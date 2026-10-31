// ============================================================================
//  mvn-proxy.js —— 本地 HTTP 转发代理
//
//  背景：本机 HTTPS 已损坏（schannel SEC_E_NO_CREDENTIALS），但明文 HTTP 正常。
//  Gradle 9 的 Kotlin DSL 又不再支持 allowInsecureProtocol。
//  于是这里起一个本地明文 HTTP 服务，把请求转发到上游 Maven 镜像（HTTPS 由
//  Node 发起，不经过 Windows schannel），Gradle 只需访问 http://127.0.0.1:PORT。
//
//  用法：  node mvn-proxy.js 18080
//  然后 settings.gradle.kts 里挂 maven { url = uri("http://127.0.0.1:18080/") }
//
//  注意：这是课程实验环境下的权宜之计，仅在本地回环地址监听。
// ============================================================================
const http = require('http');
const https = require('https');

const PORT = Number(process.argv[2] || 18080);

// 一个最小合法 ZIP（空档），用于合成插件标记 jar
const EMPTY_JAR = Buffer.from(
  'UEsFBgAAAAAAAAAAAAAAAAAAAAAAAA==',
  'base64'
);

const UPSTREAMS = [
  { host: 'repo.huaweicloud.com', pathPrefix: '/repository/maven' },
  { host: 'maven.aliyun.com', pathPrefix: '/repository/public' }
];

function handler(req, res) {
  // 依次尝试上游，第一个返回非 404/5xx 的响应即采用
  let idx = 0;
  console.log('[REQ] ' + req.method + ' ' + req.url);

  // Gradle 的 plugins{} DSL 会为 *.gradle.plugin 这类“插件标记”构件同时请求
  // .pom 和 .jar。而 Maven 上的插件标记 **只有 pom，没有 jar**，于是 Gradle 会
  // 因 404 判定插件不存在。这里对这类标记 jar 合成一个合法的空 jar，让 Gradle
  // 只通过 pom 里的 dependency 去拿真正的插件 jar。
  if (req.url.endsWith('.gradle.plugin-2.2.10.jar')) {
    console.log('[SYNTH] empty marker jar for ' + req.url);
    res.writeHead(200, {
      'Content-Type': 'application/java-archive',
      'Content-Length': String(EMPTY_JAR.length)
    });
    res.end(req.method === 'HEAD' ? undefined : EMPTY_JAR);
    return;
  }

  const attempt = () => {
    if (idx >= UPSTREAMS.length) {
      console.log('[FAIL] ' + req.url + ' (all upstreams)');
      res.writeHead(502, { 'Content-Type': 'text/plain' });
      res.end('all upstreams failed\n');
      return;
    }
    const up = UPSTREAMS[idx++];
    const options = {
      host: up.host,
      port: 443,
      method: req.method,
      path: up.pathPrefix + req.url,
      headers: Object.assign({}, req.headers, { host: up.host })
    };

    const upReq = https.request(options, (upRes) => {
      const code = upRes.statusCode || 502;
      console.log('[RES] ' + code + ' ' + up.host + options.path);
      // 3xx 跟随一次（镜像常用 302 跳转）
      if (code >= 300 && code < 400 && upRes.headers.location) {
        upRes.resume();
        let loc = upRes.headers.location;
        if (loc.startsWith('http://')) {
          loc = 'https://' + loc.slice('http://'.length);
        }
        try {
          const u = new URL(loc);
          const r2 = https.request({
            host: u.hostname, port: 443, method: req.method,
            path: u.pathname + u.search,
            headers: Object.assign({}, req.headers, { host: u.hostname })
          }, (r2res) => {
            res.writeHead(r2res.statusCode || 502, r2res.headers);
            r2res.pipe(res);
          });
          r2.on('error', () => fallback(res, attempt));
          r2.end();
          return;
        } catch (e) {
          fallback(res, attempt);
          return;
        }
      }
      if (code === 404) {
        upRes.resume();
        fallback(res, attempt);
        return;
      }
      res.writeHead(code, upRes.headers);
      upRes.pipe(res);
    });

    upReq.on('error', () => fallback(res, attempt));
    upReq.setTimeout(60000, () => { upReq.destroy(); fallback(res, attempt); });

    if (req.method === 'PUT' || req.method === 'POST') {
      req.pipe(upReq);
    } else {
      upReq.end();
    }
  };

  attempt();
}

function fallback(res, next) {
  if (res.headersSent) { res.end(); return; }
  next();
}

const server = http.createServer(handler);
server.listen(PORT, '127.0.0.1', () => {
  console.log('mvn-proxy listening on http://127.0.0.1:' + PORT);
  console.log('upstreams: ' + UPSTREAMS.map(u => 'https://' + u.host + u.pathPrefix).join(', '));
});
