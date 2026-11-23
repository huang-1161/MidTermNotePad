# 移动软件开发期中实验 —— NotePad 记事本应用

基于 AOSP 早期示例 **NotePad**（ContentProvider + SQLite 教学工程）完成的期中实验。
在保留原有功能（笔记列表、编辑、修改标题、长按菜单、复制粘贴）的基础上，完成了实验要求的
**两项基本功能** 和 **三项扩展功能**，并对界面做了整体美化。

> 本文档的写法：**每个功能先给效果截图，再说明实现思路，最后贴出该功能涉及的全部代码（含所有调用处）**
> 

---

## 一、功能总览

| 类别 | 功能 | 代码章节 |
|---|---|---|
| 基本功能 ① | 笔记列表显示**时间戳** | 2.1 |
| 基本功能 ② | 笔记**查询**（标题或内容） | 2.2 |
| 扩展功能 ① | **UI 美化**（配色、卡片、背景、空状态） | 2.3 |
| 扩展功能 ② | **笔记分类**（12 分类、分类页、按类过滤） | 2.4 |
| 扩展功能 ③ | **笔记排序**（修改时间 / 创建时间 / 标题） | 2.5 |

---

## 二、功能实现详解（每个功能：截图 + 实现思路 + 全部代码）

### 2.1 笔记列表显示时间戳（基本功能 ①）

![笔记列表](docs/images/01-notes-list.png)

![QQ_1795425722352](C:\Users\27659\AppData\Local\Temp\QQ_1795425722352.png)

#### 实现思路

1. **数据从哪来**：`notes` 表本来就有 `created` / `modified` 两列（毫秒时间戳）。
   新建笔记时 `NotePadProvider.insert()` 写入当前时间；每次保存时 `NoteEditor.updateNote()`
   把 `modified` 刷新为 `System.currentTimeMillis()`。
2. **怎么读**：`NotesList` 把 `COLUMN_NAME_MODIFICATION_DATE` 加进查询投影 `PROJECTION`。
3. **怎么显示**：`NoteListAdapter.getView()` 用 `SimpleDateFormat("yyyy-MM-dd HH:mm")`
   把毫秒格式化成 `2026-11-23 14:49`，写到列表第二行，并拼上分类前缀。
4. **怎么保持最新**：Provider 每次增删改都 `notifyChange()`，Cursor 自动刷新；
   `NotesList.onResume()` 也会主动重新查询。

#### 涉及的文件与调用处

| 文件 | 作用 | 调用处 |
|---|---|---|
| `NotePad.java` | 定义 `created` / `modified` 列名 | `NotePadProvider`、`NotesList` 引用 |
| `NotePadProvider.java` | `insert()` 写初始时间、`update()` 落库 | `NoteEditor` 保存时触发 |
| `NoteEditor.java` | `updateNote()` 每次保存刷新 `modified` | 被 `onPause()`、菜单保存调用 |
| `NotesList.java` | `PROJECTION` 带上 `modified` 并查询 | `onCreate()`、`queryNotes()` |
| `NoteListAdapter.java` | 格式化并显示时间戳 | `getView()` |
| `res/layout/noteslist_item.xml` | 列表第二行 `subtitle` | `NoteListAdapter` 绑定 |

#### 代码：列定义 —— `NotePad.java`

```java
        /** 创建时间（毫秒） */
        public static final String COLUMN_NAME_CREATE_DATE = "created";

        /** 修改时间（毫秒） */
        public static final String COLUMN_NAME_MODIFICATION_DATE = "modified";
```

#### 代码：写入时间 —— `NotePadProvider.java`

```java
    @Override
    public Uri insert(Uri uri, ContentValues initialValues) {

        if (sUriMatcher.match(uri) != NOTES) {
            throw new IllegalArgumentException("Unknown URI " + uri);
        }

        ContentValues values;
        if (initialValues != null) {
            values = new ContentValues(initialValues);
        } else {
            values = new ContentValues();
        }

        Long now = Long.valueOf(System.currentTimeMillis());

        if (values.containsKey(NotePad.Notes.COLUMN_NAME_CREATE_DATE) == false) {
            values.put(NotePad.Notes.COLUMN_NAME_CREATE_DATE, now);
        }

        if (values.containsKey(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE) == false) {
            values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, now);
        }

        SQLiteDatabase db = mOpenHelper.getWritableDatabase();

        long rowId = db.insert(
                NotePad.Notes.TABLE_NAME,
                NotePad.Notes.COLUMN_NAME_NOTE,
                values
        );

        if (rowId > 0) {
            Uri noteUri = ContentUris.withAppendedId(NotePad.Notes.CONTENT_ID_URI_BASE, rowId);
            getContext().getContentResolver().notifyChange(noteUri, null);
            return noteUri;
        }

        throw new SQLException("Failed to insert row into " + uri);
    }
```

#### 代码：保存时刷新时间 —— `NoteEditor.java`

`onPause()` 自动保存（调用处 1）：

```java
    @Override
    protected void onPause() {
        super.onPause();

        if (mCursor != null) {

            String text = mText.getText().toString();
            int length = text.length();

            if (isFinishing() && (length == 0)) {
                setResult(RESULT_CANCELED);
                deleteNote();

            } else if (mState == STATE_EDIT) {
                updateNote(text, null);          // ← 调用处 1：自动保存
            } else if (mState == STATE_INSERT) {
                updateNote(text, text);
                mState = STATE_EDIT;
            }
        }
    }
```

菜单「保存」（调用处 2）：

```java
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_save) {
            String text = mText.getText().toString();
            updateNote(text, null);              // ← 调用处 2：菜单保存
            finish();
        } else if (id == R.id.menu_delete) {
            deleteNote();
            finish();
        } else if (id == R.id.menu_revert) {
            cancelNote();
        } else if (id == R.id.menu_category) {
            showCategoryPicker();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
```

真正刷新时间的 `updateNote()`：

```java
    private final void updateNote(String text, String title) {

        ContentValues values = new ContentValues();
        // 时间戳来源：每次保存都写入当前时间
        values.put(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE, System.currentTimeMillis());

        if (mState == STATE_INSERT) {

            if (title == null) {

                int length = text.length();

                title = text.substring(0, Math.min(30, length));

                if (length > 30) {
                    int lastSpace = title.lastIndexOf(' ');
                    if (lastSpace > 0) {
                        title = title.substring(0, lastSpace);
                    }
                }
            }
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        } else if (title != null) {
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, title);
        }

        values.put(NotePad.Notes.COLUMN_NAME_NOTE, text);

        getContentResolver().update(
                mUri,
                values,
                null,
                null
        );
    }
```

#### 代码：查询列 —— `NotesList.java`

```java
    // 查询列：ID / 标题 / 修改时间 / 分类 / 正文（正文用于列表预览）
    private static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_TITLE,
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
            NotePad.Notes.COLUMN_NAME_CATEGORY,
            NotePad.Notes.COLUMN_NAME_NOTE
    };
```

```java
        mAdapter = new NoteListAdapter(this, queryNotes());
        setListAdapter(mAdapter);

        setupEmptyView();
        updateSubtitle();
```

#### 代码：格式化显示 —— `NoteListAdapter.java`

```java
    private final SimpleDateFormat mDateFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());
```

```java
        long modified = (dateIndex >= 0) ? cursor.getLong(dateIndex) : 0L;

        titleView.setText(TextUtils.isEmpty(title) ? "<无标题>" : title);
        // 第二行：[分类] 2026-11-23 14:49 —— 时间戳在这里显示
        subtitleView.setText("[" + category + "] " + mDateFormat.format(new Date(modified)));
```

#### 代码：列表项布局 —— `res/layout/noteslist_item.xml`

```xml
        <TextView
            android:id="@+id/subtitle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:singleLine="true"
            android:textColor="#5F5F5F"
            android:textSize="12sp" />
```

---

### 2.2 笔记搜索（基本功能 ②）

![搜索](docs/images/02-search.png)



![QQ_1795425784935](C:\Users\27659\AppData\Local\Temp\QQ_1795425784935.png)

#### 实现思路

1. **入口**：`list_options_menu.xml` 里加一个 `actionViewClass="android.widget.SearchView"` 的菜单项，
   点开后标题栏变成搜索框。
2. **监听输入**：`NotesList.onCreateOptionsMenu()` 通过 `menu.findItem(R.id.menu_search).getActionView()`
   拿到 SearchView，注册 `OnQueryTextListener`；边输入（`onQueryTextChange`）和回车
   （`onQueryTextSubmit`）都把关键字存进 `mKeyword`，再调 `reloadNotes()`。
3. **查询**：`queryNotes()` 拼 `title LIKE ? OR note LIKE ?`，**标题或正文命中都算**。
4. **与分类组合**：若当前是某个分类的列表，追加 `AND category = ?`，即"在该分类内搜索"。
5. **刷新**：`reloadNotes()` 用 `changeCursor()` 换游标，`updateSubtitle()` 更新条数提示。
6. **清空**：关键字为空时 `selection` 为空，自动恢复全部。

#### 涉及的文件与调用处

| 文件 | 作用 | 调用处 |
|---|---|---|
| `res/menu/list_options_menu.xml` | 搜索菜单项 | `NotesList.onCreateOptionsMenu()` |
| `NotesList.java` | 监听、拼条件、刷新 | `onCreateOptionsMenu()`、`onQueryTextChange()`、`onQueryTextSubmit()`、`queryNotes()`、`reloadNotes()` |

#### 代码：菜单项 —— `res/menu/list_options_menu.xml`

```xml
    <!-- 搜索 -->
    <item android:id="@+id/menu_search"
          android:icon="@android:drawable/ic_menu_search"
          android:showAsAction="ifRoom|collapseActionView"
          android:actionViewClass="android.widget.SearchView"
          android:queryHint="@string/search_hint"
          android:title="@string/menu_search" />
```

#### 代码：监听输入 —— `NotesList.java`

```java
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_options_menu, menu);

        MenuItem searchItem = menu.findItem(R.id.menu_search);
        SearchView searchView = (SearchView) searchItem.getActionView();
        if (searchView != null) {
            searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
                @Override
                public boolean onQueryTextSubmit(String query) {
                    mKeyword = (query == null) ? "" : query.trim();
                    reloadNotes();
                    return true;
                }

                @Override
                public boolean onQueryTextChange(String newText) {
                    mKeyword = (newText == null) ? "" : newText.trim();
                    reloadNotes();
                    return true;
                }
            });
        }

        Intent intent = new Intent(null, getIntent().getData());
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);

        return super.onCreateOptionsMenu(menu);
    }
```

#### 代码：拼查询条件 + 刷新 —— `NotesList.java`

```java
    /** 组合 关键字 + 分类 的查询 */
    private Cursor queryNotes() {
        StringBuilder selection = new StringBuilder();
        List<String> args = new ArrayList<String>();

        // ① 关键字：标题或正文命中
        if (!TextUtils.isEmpty(mKeyword)) {
            selection.append("(")
                    .append(NotePad.Notes.COLUMN_NAME_TITLE).append(" LIKE ? OR ")
                    .append(NotePad.Notes.COLUMN_NAME_NOTE).append(" LIKE ?)");
            args.add("%" + mKeyword + "%");
            args.add("%" + mKeyword + "%");
        }

        // ② 分类过滤：与关键字是 AND 关系
        if (!TextUtils.isEmpty(mCategoryFilter)) {
            if (selection.length() > 0) {
                selection.append(" AND ");
            }
            selection.append(NotePad.Notes.COLUMN_NAME_CATEGORY).append(" = ?");
            args.add(mCategoryFilter);
        }

        String[] argsArray = args.isEmpty() ? null : args.toArray(new String[args.size()]);
        return getContentResolver().query(
                getIntent().getData(),
                PROJECTION,
                selection.length() == 0 ? null : selection.toString(),
                argsArray,
                mSortOrder);
    }

    private void reloadNotes() {
        if (mAdapter != null) {
            mAdapter.changeCursor(queryNotes());
            updateSubtitle();
        }
    }

    /** 标题栏副标题：共 N 条笔记（搜索/分类时同步变化） */
    private void updateSubtitle() {
        int count = (mAdapter == null) ? 0 : mAdapter.getCount();
        String subtitle;
        if (TextUtils.isEmpty(mCategoryFilter)) {
            subtitle = "共 " + count + " 条笔记";
        } else {
            subtitle = mCategoryFilter + " · " + count + " 条";
        }
        if (getActionBar() != null) {
            getActionBar().setSubtitle(subtitle);
        }
        if (mEmptyView != null) {
            mEmptyView.setText(TextUtils.isEmpty(mKeyword)
                    ? "还没有笔记，点右上角新建"
                    : "没有匹配的笔记");
        }
    }
```

---

### 2.3 UI 美化（扩展功能 ①）

![笔记列表配色](docs/images/01-notes-list.png)

![分类页卡片](docs/images/03-categories.png)

![编辑页](docs/images/04-note-editor.png)

![更换背景](docs/images/07-background-dialog.png)

#### 实现思路

1. **配色集中管理**：所有分类颜色写在 `colors.xml`，`NoteColors` 统一做"分类名 → 颜色/头像/首字"映射。
2. **列表行重构**：`noteslist_item.xml` 改为「圆形头像 + 标题 + 时间 + 正文预览」；
   `NoteListAdapter.getView()` 每次绑定：
   - 头像显示分类首字，底为半透明白圆（`GradientDrawable` 运行时绘制，不需要图片）；
   - 整行背景 `NoteColors.row()`：分类固定色，"默认"分类按笔记 ID 轮换 6 种浅色。
3. **列表页观感**：副标题显示条数；空列表/无搜索结果时显示居中提示（`ListView.setEmptyView`）。
4. **背景更换**：`NoteStyle` 把颜色存 `SharedPreferences`，`applyBackground()` 设置窗口背景，
   列表页和编辑页 `onCreate()` 都调用；菜单「更换背景」选完 `recreate()` 立即生效。
5. **分类页卡片**：`GradientDrawable` 画 16dp 圆角卡片 + 圆形首字头像。

#### 涉及的文件与调用处

| 文件 | 作用 | 调用处 |
|---|---|---|
| `res/values/colors.xml` | 12 分类色 + 6 轮换色 + 全部卡片色 | `NoteColors` |
| `NoteColors.java` | 分类色 / 行背景 / 头像 / 首字 | `NoteListAdapter`、`CategoryActivity` |
| `res/layout/noteslist_item.xml` | 四段式列表行 | `NoteListAdapter` |
| `NoteListAdapter.java` | 绑定头像、颜色、预览 | `NotesList.setListAdapter` |
| `NotesList.java` | 副标题、空状态、背景菜单 | `onCreate()`、`updateSubtitle()`、`setupEmptyView()`、`onOptionsItemSelected()` |
| `NoteStyle.java` | 背景持久化 + 应用 + 选色框 | `NotesList`、`NoteEditor` |
| `NoteEditor.java` | 编辑页应用背景 | `onCreate()` |

#### 代码：配色表 —— `res/values/colors.xml`

```xml
<resources>
    <!-- 12 个分类的固定配色 -->
    <color name="cat_default">#F2F3F5</color>
    <color name="cat_work">#FFD6E4</color>
    <color name="cat_study">#D3E3FF</color>
    <color name="cat_life">#D7F3D2</color>
    <color name="cat_important">#FFDFC4</color>
    <color name="cat_idea">#E6D9FF</color>
    <color name="cat_todo">#FFF3BF</color>
    <color name="cat_shopping">#CFEFE8</color>
    <color name="cat_travel">#CDE7F0</color>
    <color name="cat_health">#FFE1E1</color>
    <color name="cat_read">#EFE3D0</color>
    <color name="cat_movie">#E0E0F5</color>

    <!-- “全部笔记”卡片 -->
    <color name="cat_all">#FFF1C1</color>

    <!-- “默认”分类行的轮换浅色 -->
    <color name="row_pink">#FFE3EC</color>
    <color name="row_blue">#E3EFFF</color>
    <color name="row_green">#E6F5DA</color>
    <color name="row_yellow">#FFF6D6</color>
    <color name="row_purple">#EFE4FF</color>
    <color name="row_teal">#DDF3EE</color>
</resources>
```

#### 代码：颜色/头像工具 —— `NoteColors.java`

```java
public final class NoteColors {

    private static final int[] PASTELS = {
            R.color.row_pink,
            R.color.row_blue,
            R.color.row_green,
            R.color.row_yellow,
            R.color.row_purple,
            R.color.row_teal
    };

    private NoteColors() {
    }

    /** 分类固定色 */
    public static int of(Context context, String category) {
        if ("工作".equals(category)) {
            return context.getColor(R.color.cat_work);
        }
        if ("学习".equals(category)) {
            return context.getColor(R.color.cat_study);
        }
        if ("生活".equals(category)) {
            return context.getColor(R.color.cat_life);
        }
        if ("重要".equals(category)) {
            return context.getColor(R.color.cat_important);
        }
        if ("灵感".equals(category)) {
            return context.getColor(R.color.cat_idea);
        }
        if ("待办".equals(category)) {
            return context.getColor(R.color.cat_todo);
        }
        if ("购物".equals(category)) {
            return context.getColor(R.color.cat_shopping);
        }
        if ("旅行".equals(category)) {
            return context.getColor(R.color.cat_travel);
        }
        if ("健康".equals(category)) {
            return context.getColor(R.color.cat_health);
        }
        if ("阅读".equals(category)) {
            return context.getColor(R.color.cat_read);
        }
        if ("电影".equals(category)) {
            return context.getColor(R.color.cat_movie);
        }
        return context.getColor(R.color.cat_default);
    }

    /** 列表行背景：默认分类按 ID 轮换，其余按分类固定色 */
    public static int row(Context context, long noteId, String category) {
        if (category == null || NotePad.Notes.CATEGORY_DEFAULT.equals(category)) {
            int index = (int) (Math.abs(noteId) % PASTELS.length);
            return context.getColor(PASTELS[index]);
        }
        return of(context, category);
    }

    /** 分类卡片背景色 */
    public static int get(Context context, String category, boolean all) {
        if (all) {
            return context.getColor(R.color.cat_all);
        }
        return of(context, category);
    }

    /** 行内圆形头像的底（半透明白） */
    public static GradientDrawable avatar() {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(0x99FFFFFF);
        return drawable;
    }

    /** 分类首字 */
    public static String initial(String category) {
        if (category == null || category.length() == 0) {
            return "?";
        }
        return category.substring(0, 1);
    }
}
```

#### 代码：列表行布局 —— `res/layout/noteslist_item.xml`

```xml
<LinearLayout xmlns:android="http://schemas.android.com/apk/res/android"
    android:layout_width="match_parent"
    android:layout_height="wrap_content"
    android:gravity="center_vertical"
    android:orientation="horizontal"
    android:paddingLeft="14dp"
    android:paddingRight="14dp"
    android:paddingTop="12dp"
    android:paddingBottom="12dp">

    <TextView
        android:id="@+id/avatar"
        android:layout_width="40dp"
        android:layout_height="40dp"
        android:gravity="center"
        android:textColor="#3A3A3A"
        android:textSize="16sp"
        android:textStyle="bold" />

    <LinearLayout
        android:layout_width="0dp"
        android:layout_height="wrap_content"
        android:layout_marginStart="12dp"
        android:layout_weight="1"
        android:orientation="vertical">

        <TextView
            android:id="@+id/title"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:singleLine="true"
            android:textColor="#1F1F1F"
            android:textSize="16sp"
            android:textStyle="bold" />

        <TextView
            android:id="@+id/subtitle"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:singleLine="true"
            android:textColor="#5F5F5F"
            android:textSize="12sp" />

        <TextView
            android:id="@+id/preview"
            android:layout_width="match_parent"
            android:layout_height="wrap_content"
            android:layout_marginTop="2dp"
            android:singleLine="true"
            android:textColor="#8A8A8A"
            android:textSize="12sp" />
    </LinearLayout>
</LinearLayout>
```

#### 代码：适配器绑定 —— `NoteListAdapter.java`

```java
    @Override
    public View getView(int position, View convertView, ViewGroup parent) {
        View view = super.getView(position, convertView, parent);
        Cursor cursor = getCursor();
        if (cursor == null) {
            return view;
        }
        cursor.moveToPosition(position);

        int titleIndex = cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
        int dateIndex = cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE);
        int categoryIndex = cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_CATEGORY);
        int noteIndex = cursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_NOTE);
        int idIndex = cursor.getColumnIndex(NotePad.Notes._ID);

        String title = (titleIndex >= 0) ? cursor.getString(titleIndex) : "";
        String category = (categoryIndex >= 0) ? cursor.getString(categoryIndex) : null;
        if (TextUtils.isEmpty(category)) {
            category = NotePad.Notes.CATEGORY_DEFAULT;
        }
        long modified = (dateIndex >= 0) ? cursor.getLong(dateIndex) : 0L;
        long noteId = (idIndex >= 0) ? cursor.getLong(idIndex) : 0L;
        String note = (noteIndex >= 0) ? cursor.getString(noteIndex) : "";

        TextView avatar = (TextView) view.findViewById(R.id.avatar);
        TextView titleView = (TextView) view.findViewById(R.id.title);
        TextView subtitleView = (TextView) view.findViewById(R.id.subtitle);
        TextView previewView = (TextView) view.findViewById(R.id.preview);

        avatar.setText(NoteColors.initial(category));
        avatar.setBackground(NoteColors.avatar());

        titleView.setText(TextUtils.isEmpty(title) ? "<无标题>" : title);
        subtitleView.setText("[" + category + "] " + mDateFormat.format(new Date(modified)));

        String preview = (note == null) ? "" : note.replace('\n', ' ').trim();
        if (preview.length() > MAX_PREVIEW) {
            preview = preview.substring(0, MAX_PREVIEW) + "…";
        }
        if (preview.isEmpty()) {
            previewView.setVisibility(View.GONE);
        } else {
            previewView.setVisibility(View.VISIBLE);
            previewView.setText(preview);
        }

        view.setBackgroundColor(NoteColors.row(view.getContext(), noteId, category));
        return view;
    }
```

#### 代码：空状态 + 统计 —— `NotesList.java`

```java
    /** 空列表提示文字（搜索无结果时换成另一句） */
    private void setupEmptyView() {
        mEmptyView = new TextView(this);
        mEmptyView.setGravity(Gravity.CENTER);
        mEmptyView.setTextSize(14);
        mEmptyView.setTextColor(0xFF8A8A8A);
        int pad = (int) (getResources().getDisplayMetrics().density * 80);
        mEmptyView.setPadding(pad, pad, pad, 0);

        ViewGroup parent = (ViewGroup) getListView().getParent();
        parent.addView(mEmptyView, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT,
                ViewGroup.LayoutParams.MATCH_PARENT));
        getListView().setEmptyView(mEmptyView);
    }
```

#### 代码：背景持久化 —— `NoteStyle.java`

```java
public final class NoteStyle {

    private static final String PREFS = "notepad_prefs";
    private static final String KEY_BG = "bg_color";

    public static final String[] BG_NAMES = {"默认白", "米黄", "淡绿", "淡蓝", "淡粉"};
    public static final int[] BG_COLORS = {
            Color.WHITE,
            0xFFFDF6E3,
            0xFFE8F5E9,
            0xFFE3F2FD,
            0xFFFCE4EC
    };

    private NoteStyle() {
    }

    public static int getBackgroundColor(Context context) {
        SharedPreferences sp = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        return sp.getInt(KEY_BG, Color.WHITE);
    }

    /** 应用到 Activity 窗口：内容里的控件是透明背景，所以能透出来 */
    public static void applyBackground(Activity activity) {
        if (activity == null) {
            return;
        }
        activity.getWindow().setBackgroundDrawable(
                new ColorDrawable(getBackgroundColor(activity)));
    }

    /** 选色对话框；选完回调 onChanged（列表页传 recreate） */
    public static void showPicker(final Activity activity, final Runnable onChanged) {
        SharedPreferences sp = activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE);
        int current = sp.getInt(KEY_BG, Color.WHITE);

        int checked = 0;
        for (int i = 0; i < BG_COLORS.length; i++) {
            if (BG_COLORS[i] == current) {
                checked = i;
                break;
            }
        }

        new AlertDialog.Builder(activity)
                .setTitle("选择背景颜色")
                .setSingleChoiceItems(BG_NAMES, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
                                .edit()
                                .putInt(KEY_BG, BG_COLORS[which])
                                .apply();
                        dialog.dismiss();
                        if (onChanged != null) {
                            onChanged.run();
                        }
                    }
                })
                .show();
    }
}
```

#### 代码：背景的三个调用处

`NotesList.onCreate()`（列表页）：

```java
        NoteStyle.applyBackground(this);
```

`NotesList.onOptionsItemSelected()`（更换背景菜单）：

```java
        } else if (id == R.id.menu_background) {
            NoteStyle.showPicker(this, new Runnable() {
                @Override
                public void run() {
                    recreate();          // 立即重绘列表
                }
            });
            return true;
        }
```

`NoteEditor.onCreate()`（编辑页）：

```java
        // 应用背景色（附加功能：更换背景）
        NoteStyle.applyBackground(this);
```

---

### 2.4 笔记分类（扩展功能 ②）

![分类总览](docs/images/03-categories.png)

![编辑页设置分类](docs/images/05-editor-category-dialog.png)

![QQ_1795425845970](C:\Users\27659\AppData\Local\Temp\QQ_1795425845970.png)

#### 实现思路

1. **数据层加列**：`notes` 表增加 `category TEXT DEFAULT '默认'`；数据库版本 2 → **3**，
   `onUpgrade()` 用 `ALTER TABLE ... ADD COLUMN` 就地加列，**不删旧数据**。
2. **Provider 放行**：`sNotesProjectionMap` 登记 `category`；`insert()` 未指定分类时填「默认」。
3. **分类常量统一**：12 个分类写在 `NotePad.Notes.CATEGORIES`，编辑页与分类页共用。
4. **编辑页设置**：`NoteEditor` 的 `PROJECTION` 加 `category`；菜单「分类」弹单选对话框，
   选中后 `ContentResolver.update()` 写回，再 `requery()` + `refreshTitle()` 立即刷新。
5. **列表按分类过滤**：`CategoryActivity` 通过 Intent 传 `EXTRA_CATEGORY`，
   `NotesList` 追加 `category = ?`，标题显示 `分类：工作`。
6. **列表按分类着色**：`NoteListAdapter` 把 `category` 交给 `NoteColors.row()`。
7. **分类页统计**：`CategoryActivity` 查一次 `category` 列在内存里计数，得到每类条数与总数。

#### 涉及的文件与调用处

| 文件 | 作用 | 调用处 |
|---|---|---|
| `NotePad.java` | 分类列名 / 默认分类 / 12 个分类 | 全工程引用 |
| `NotePadProvider.java` | 建表加列、`onUpgrade`、投影表、insert 默认值 | 建库/升级、insert/query |
| `NoteEditor.java` | 读分类、设分类、标题显示分类 | `onCreate()`、`onOptionsItemSelected()`、`showCategoryPicker()`、`refreshTitle()` |
| `res/menu/editor_options_menu.xml` | 「分类」菜单项 | `NoteEditor.onCreateOptionsMenu()` |
| `NotesList.java` | 按分类过滤 | `onCreate()`、`queryNotes()` |
| `NoteListAdapter.java` | 分类标签与配色 | `getView()` |
| `CategoryActivity.java` | 分类总览页 | `onCreate()`、`onItemClick`、`openCategory()` |
| `AndroidManifest.xml` | 注册分类页 | 系统启动 |

#### 代码：分类常量 —— `NotePad.java`

```java
        // ================= 附加功能：笔记分类 =================
        /** 分类列（TEXT） */
        public static final String COLUMN_NAME_CATEGORY = "category";

        /** 默认分类 */
        public static final String CATEGORY_DEFAULT = "默认";

        /** 可选分类（编辑页弹窗 / 分类页用） */
        public static final String[] CATEGORIES = {
                "默认", "工作", "学习", "生活",
                "重要", "灵感", "待办", "购物",
                "旅行", "健康", "阅读", "电影"
        };
```

#### 代码：建表 / 升级 / 投影 / insert —— `NotePadProvider.java`

```java
    /**
     * 数据库版本
     * 1 = 原始版本
     * 2 = 历史版本（本项目已用过）
     * 3 = 新增 category（分类）列
     */
    private static final int DATABASE_VERSION = 3;
```

```java
        @Override
        public void onCreate(SQLiteDatabase db) {
            // 建表：新增 category 列，默认值“默认”
            db.execSQL("CREATE TABLE " + NotePad.Notes.TABLE_NAME + " ("
                    + NotePad.Notes._ID + " INTEGER PRIMARY KEY,"
                    + NotePad.Notes.COLUMN_NAME_TITLE + " TEXT,"
                    + NotePad.Notes.COLUMN_NAME_NOTE + " TEXT,"
                    + NotePad.Notes.COLUMN_NAME_CREATE_DATE + " INTEGER,"
                    + NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE + " INTEGER,"
                    + NotePad.Notes.COLUMN_NAME_CATEGORY + " TEXT DEFAULT '"
                    + NotePad.Notes.CATEGORY_DEFAULT + "'"
                    + ");");
        }

        /**
         * 升级：就地加列，不删旧数据（原版是 DROP TABLE，会清空所有笔记）
         */
        @Override
        public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
            Log.w(TAG, "Upgrading database from version " + oldVersion
                    + " to " + newVersion);

            if (oldVersion < 3) {
                db.execSQL("ALTER TABLE " + NotePad.Notes.TABLE_NAME + " ADD COLUMN "
                        + NotePad.Notes.COLUMN_NAME_CATEGORY + " TEXT DEFAULT '"
                        + NotePad.Notes.CATEGORY_DEFAULT + "'");
            }
        }
```

```java
        // 静态块中把 category 登记进投影映射表（query 合法性校验）
        sNotesProjectionMap.put(NotePad.Notes.COLUMN_NAME_CATEGORY,
                NotePad.Notes.COLUMN_NAME_CATEGORY);
```

```java
        // insert：没指定分类就用默认分类
        if (values.containsKey(NotePad.Notes.COLUMN_NAME_CATEGORY) == false) {
            values.put(NotePad.Notes.COLUMN_NAME_CATEGORY, NotePad.Notes.CATEGORY_DEFAULT);
        }
```

#### 代码：编辑页 —— `NoteEditor.java`

查询列带分类：

```java
    // 查询列：ID / 标题 / 正文 / 分类
    private static final String[] PROJECTION =
            new String[] {
                    NotePad.Notes._ID,
                    NotePad.Notes.COLUMN_NAME_TITLE,
                    NotePad.Notes.COLUMN_NAME_NOTE,
                    NotePad.Notes.COLUMN_NAME_CATEGORY
            };
```

菜单入口：

```java
        } else if (id == R.id.menu_category) {
            showCategoryPicker();
            return true;
        }
```

标题栏显示分类：

```java
    /**
     * 标题栏显示 "Edit: 标题  ·  分类"，改完分类立刻能看到
     */
    private void refreshTitle() {
        if (mCursor == null || mCursor.isClosed()) {
            return;
        }
        int colTitleIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_TITLE);
        int colCategoryIndex = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_CATEGORY);

        String title = mCursor.getString(colTitleIndex);
        String category = (colCategoryIndex >= 0) ? mCursor.getString(colCategoryIndex) : null;
        if (category == null || category.length() == 0) {
            category = NotePad.Notes.CATEGORY_DEFAULT;
        }

        String text = String.format(getResources().getString(R.string.title_edit), title);
        setTitle(text + "  ·  " + category);
    }
```

分类单选对话框：

```java
    /**
     * 选择笔记分类（12 个分类的单选对话框）
     */
    private void showCategoryPicker() {
        String current = NotePad.Notes.CATEGORY_DEFAULT;
        if (mCursor != null && !mCursor.isClosed()) {
            int idx = mCursor.getColumnIndex(NotePad.Notes.COLUMN_NAME_CATEGORY);
            if (idx >= 0 && mCursor.getString(idx) != null) {
                current = mCursor.getString(idx);
            }
        }

        int checked = 0;
        for (int i = 0; i < NotePad.Notes.CATEGORIES.length; i++) {
            if (NotePad.Notes.CATEGORIES[i].equals(current)) {
                checked = i;
                break;
            }
        }

        final Uri uri = mUri;
        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_category)
                .setSingleChoiceItems(NotePad.Notes.CATEGORIES, checked,
                        new DialogInterface.OnClickListener() {
                            @Override
                            public void onClick(DialogInterface dialog, int which) {
                                ContentValues values = new ContentValues();
                                values.put(NotePad.Notes.COLUMN_NAME_CATEGORY,
                                        NotePad.Notes.CATEGORIES[which]);
                                getContentResolver().update(uri, values, null, null);
                                dialog.dismiss();
                                // 刷新 Cursor 和标题，避免再次打开弹窗还显示旧分类
                                if (mCursor != null && !mCursor.isClosed()) {
                                    mCursor.requery();
                                    mCursor.moveToFirst();
                                    refreshTitle();
                                }
                            }
                        })
                .show();
    }
```

#### 代码：编辑页菜单项 —— `res/menu/editor_options_menu.xml`

```xml
    <!-- 分类（附加功能） -->
    <item android:id="@+id/menu_category"
        android:title="@string/menu_category" />
```

#### 代码：列表按分类过滤 —— `NotesList.java`

```java
    /** 从 CategoryActivity 传进来的分类过滤（不传 = 全部） */
    public static final String EXTRA_CATEGORY = "category";
```

```java
        mCategoryFilter = getIntent().getStringExtra(EXTRA_CATEGORY);
        if (!TextUtils.isEmpty(mCategoryFilter)) {
            setTitle("分类：" + mCategoryFilter);
        }
```

```java
        if (!TextUtils.isEmpty(mCategoryFilter)) {
            if (selection.length() > 0) {
                selection.append(" AND ");
            }
            selection.append(NotePad.Notes.COLUMN_NAME_CATEGORY).append(" = ?");
            args.add(mCategoryFilter);
        }
```

#### 代码：分类总览页 —— `CategoryActivity.java`

```java
public class CategoryActivity extends Activity {

    private TextView mSummary;
    private TextView mAllCount;
    private TextView mAllAvatar;
    private View mAllCard;
    private GridView mGrid;
    private CategoryAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_category);
        setTitle(R.string.title_categories);

        mSummary = (TextView) findViewById(R.id.summary);
        mAllCount = (TextView) findViewById(R.id.all_count);
        mAllAvatar = (TextView) findViewById(R.id.all_avatar);
        mAllCard = findViewById(R.id.all_card);
        mGrid = (GridView) findViewById(R.id.grid);

        mAllAvatar.setText("全");
        mAllAvatar.setBackground(NoteColors.avatar());
        mAllCard.setBackground(roundedCard(getColor(R.color.cat_all)));
        mAllCard.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                openCategory(null);        // “全部笔记”
            }
        });

        mAdapter = new CategoryAdapter();
        mGrid.setAdapter(mAdapter);
        mGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                openCategory(mAdapter.getItem(position).name);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        mAdapter.reload();                 // 每次回到本页重新统计
    }

    private void openCategory(String category) {
        Intent intent = new Intent(this, NotesList.class);
        intent.setData(NotePad.Notes.CONTENT_URI);
        if (category != null) {
            intent.putExtra(NotesList.EXTRA_CATEGORY, category);
        }
        startActivity(intent);
    }

    /** 圆角卡片背景 */
    private GradientDrawable roundedCard(int color) {
        float density = getResources().getDisplayMetrics().density;
        GradientDrawable drawable = new GradientDrawable();
        drawable.setColor(color);
        drawable.setCornerRadius(16 * density);
        drawable.setStroke((int) density, 0x1A000000);
        return drawable;
    }

    /** 一个分类卡片的数据 */
    private static class CategoryItem {
        final String name;
        final int count;
        final int color;

        CategoryItem(String name, int count, int color) {
            this.name = name;
            this.count = count;
            this.color = color;
        }
    }

    private class CategoryAdapter extends BaseAdapter {

        private final List<CategoryItem> mItems = new ArrayList<CategoryItem>();

        void reload() {
            mItems.clear();

            int total = 0;
            int used = 0;
            int[] counts = new int[NotePad.Notes.CATEGORIES.length];

            // 只查分类列，在内存里按分类计数
            Cursor cursor = getContentResolver().query(
                    NotePad.Notes.CONTENT_URI,
                    new String[] { NotePad.Notes.COLUMN_NAME_CATEGORY },
                    null, null, null);
            if (cursor != null) {
                while (cursor.moveToNext()) {
                    String category = cursor.getString(0);
                    if (category == null) {
                        category = NotePad.Notes.CATEGORY_DEFAULT;
                    }
                    for (int i = 0; i < NotePad.Notes.CATEGORIES.length; i++) {
                        if (NotePad.Notes.CATEGORIES[i].equals(category)) {
                            counts[i]++;
                        }
                    }
                    total++;
                }
                cursor.close();
            }

            for (int i = 0; i < NotePad.Notes.CATEGORIES.length; i++) {
                String name = NotePad.Notes.CATEGORIES[i];
                if (counts[i] > 0) {
                    used++;
                }
                mItems.add(new CategoryItem(name, counts[i],
                        NoteColors.of(CategoryActivity.this, name)));
            }

            mSummary.setText("共 " + total + " 条笔记 · " + used + " 个分类在用");
            mAllCount.setText(total + " 条笔记");

            notifyDataSetChanged();
        }

        @Override
        public int getCount() {
            return mItems.size();
        }

        @Override
        public CategoryItem getItem(int position) {
            return mItems.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            View view = convertView;
            if (view == null) {
                view = LayoutInflater.from(CategoryActivity.this)
                        .inflate(R.layout.item_category, parent, false);
            }

            CategoryItem item = getItem(position);

            TextView avatar = (TextView) view.findViewById(R.id.cat_avatar);
            TextView name = (TextView) view.findViewById(R.id.cat_name);
            TextView count = (TextView) view.findViewById(R.id.cat_count);

            avatar.setText(NoteColors.initial(item.name));
            avatar.setBackground(NoteColors.avatar());
            name.setText(item.name);
            count.setText(item.count + " 条");
            view.setBackground(roundedCard(item.color));

            return view;
        }
    }
}
```

#### 代码：注册分类页 —— `AndroidManifest.xml`

```xml
        <!-- 笔记分类页（附加功能） -->
        <activity android:name="CategoryActivity"
            android:label="@string/title_categories" />
```

---

### 2.5 笔记排序（扩展功能 ③）

![排序](docs/images/06-sort-dialog.png)

![QQ_1795425881538](C:\Users\27659\AppData\Local\Temp\QQ_1795425881538.png)

#### 实现思路

1. 定义三组「显示名 + SQL 排序串」：修改时间倒序 / 创建时间倒序 / 标题升序。
2. 当前排序存 `SharedPreferences`（`notepad_prefs` / `sort_order`），启动时读取，退出重进仍生效。
3. 菜单「排序」弹单选对话框，选完写回偏好并 `reloadNotes()` 立即重排。
4. `queryNotes()` 把 `mSortOrder` 作为 `ContentResolver.query()` 的 `sortOrder` 参数，
   最终由 `NotePadProvider.query()` 交给 `SQLiteQueryBuilder` 执行。

#### 涉及的文件与调用处

| 文件 | 作用 | 调用处 |
|---|---|---|
| `NotesList.java` | 排序常量、读写偏好、对话框、查询参数 | `onCreate()`、`showSortDialog()`、`onOptionsItemSelected()`、`queryNotes()` |
| `res/menu/list_options_menu.xml` | 「排序」菜单项 | `onOptionsItemSelected()` |

#### 代码 —— `NotesList.java`

```java
    private static final String PREFS = "notepad_prefs";
    private static final String KEY_SORT = "sort_order";

    // 排序方式
    private static final String[] SORT_NAMES = {
            "修改时间（最新在前）", "创建时间（最新在前）", "标题（A→Z）"
    };
    private static final String[] SORT_ORDERS = {
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE + " DESC",
            NotePad.Notes.COLUMN_NAME_CREATE_DATE + " DESC",
            NotePad.Notes.COLUMN_NAME_TITLE + " ASC"
    };
```

```java
        // onCreate 中读取上次选择的排序
        mSortOrder = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_SORT, SORT_ORDERS[0]);
```

```java
    /** 排序对话框 */
    private void showSortDialog() {
        int checked = 0;
        for (int i = 0; i < SORT_ORDERS.length; i++) {
            if (SORT_ORDERS[i].equals(mSortOrder)) {
                checked = i;
                break;
            }
        }

        new AlertDialog.Builder(this)
                .setTitle(R.string.menu_sort)
                .setSingleChoiceItems(SORT_NAMES, checked, new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        mSortOrder = SORT_ORDERS[which];
                        getSharedPreferences(PREFS, MODE_PRIVATE)
                                .edit().putString(KEY_SORT, mSortOrder).apply();
                        dialog.dismiss();
                        reloadNotes();          // 立即按新顺序刷新
                    }
                })
                .show();
    }
```

```java
        } else if (id == R.id.menu_sort) {
            showSortDialog();
            return true;
        }
```

```java
        // queryNotes 中作为最后一个参数（排序）传入 provider
        return getContentResolver().query(
                getIntent().getData(),
                PROJECTION,
                selection.length() == 0 ? null : selection.toString(),
                argsArray,
                mSortOrder);
```

---

## 三、数据层设计

| 内容 | 值 |
|---|---|
| Authority | `com.google.provider.NotePad` |
| 列表 URI | `content://com.google.provider.NotePad/notes` |
| 单条 URI | `content://com.google.provider.NotePad/notes/<id>` |
| MIME（目录） | `vnd.android.cursor.dir/vnd.google.note` |
| MIME（单条） | `vnd.android.cursor.item/vnd.google.note` |
| 默认排序 | `modified DESC` |
| 数据库版本 | 3（新增 `category` 列，`ALTER TABLE` 增量升级） |

```sql
CREATE TABLE notes (
    _id      INTEGER PRIMARY KEY,
    title    TEXT,
    note     TEXT,
    created  INTEGER,               -- 创建时间（毫秒）
    modified INTEGER,               -- 修改时间（毫秒）
    category TEXT DEFAULT '默认'     -- 分类
);
```

---

## 四、项目结构

```
app/src/main/
├── AndroidManifest.xml                 4 个 Activity + ContentProvider 声明
├── java/com/example/android/notepad/
│   ├── NotePad.java                    契约类：表名/列名/URI/12 个分类常量
│   ├── NotePadProvider.java            ContentProvider：增删改查、DB 升级
│   ├── NotesList.java                  列表页：时间戳、搜索、分类过滤、排序、背景、空状态
│   ├── NoteListAdapter.java            列表适配器：头像/标题/时间/预览/行配色
│   ├── NoteColors.java                 分类配色、圆形头像、分类首字
│   ├── NoteStyle.java                  背景色持久化与应用
│   ├── NoteEditor.java                 编辑页：自动保存、分类、标题显示分类
│   ├── TitleEditor.java                修改标题对话框（原示例保留）
│   ├── CategoryActivity.java           分类总览页
│   └── NotesLiveFolder.java            原示例 Live Folder（历史遗留，未启用）
└── res/
    ├── layout/  noteslist_item.xml、activity_category.xml、item_category.xml
    │            note_editor.xml、title_editor.xml
    ├── menu/    list_options_menu.xml、editor_options_menu.xml、list_context_menu.xml
    └── values/  colors.xml、strings.xml
```

---

## 五、运行环境

| 项 | 值 |
|---|---|
| IDE | Android Studio（2026.1） |
| Android Gradle Plugin | 9.3.0 |
| Gradle | 9.7.1 |
| JDK | 21（Android Studio 自带 JBR） |
| compileSdk | 36 |
| targetSdk | 34 |
| minSdk | 24 |
| 第三方依赖 | 无（全部使用 Android 框架 API） |

> `targetSdk` 用 34 而不是 36：targetSdk ≥ 35 会强制 edge-to-edge，
> 这个示例的编辑器布局没有做 inset 处理，正文会被状态栏/标题栏遮挡。

**本机环境补充**：本机访问 Google Maven 的 HTTPS 不通，构建依赖通过本地 HTTP 转发代理：

```bash
node tools/mvn-proxy.js 18080
```

工程 `build.gradle` 已把该代理放在仓库列表第一位，并清除了全局 `init.d` 注入的阿里云 HTTPS 仓库。

---

## 六、常见问题

**1. 列表时间和电脑时间差 8 小时**

模拟器时区是 GMT，本机是 CST（UTC+8）。时间数据本身正确，只是显示时区不同：

```bash
adb root
adb shell settings put global auto_time_zone 0
adb shell setprop persist.sys.timezone Asia/Shanghai
```

改完**杀掉 App 重新打开**（`SimpleDateFormat` 的时区在进程启动时缓存）。

**2. 模拟器不弹软键盘**

该模拟器镜像自带的 AOSP 输入法渲染异常（键盘窗口空白），用**电脑键盘**输入即可，
或改用真机运行。

**3. 分类页卡片显示不全**

分类较多时列表可上下拖动查看；本项目把卡片尺寸控制在常见分辨率下一屏显示 12 个分类。

---

## 七、已知限制与可扩展方向

- 时间戳记录的是**最后一次保存时间**（原示例在 `onPause` 时无条件更新 `modified`）。
  若要求"只有内容真的改了才更新时间"，需要在 `updateNote()` 前比对原文本；
- 分类为固定 12 项（常量数组），如需自定义分类，可把分类单独建表；
- 未实现的扩展方向：导出笔记、云备份、待办清单、偏好设置、桌面便签小部件、
  多类型笔记（图片/语音）、OCR 扫描等。

---

## 八、参考资料

- AOSP NotePad 示例（实验提供源码）
- [NotePad 源码解析（CSDN）](https://blog.csdn.net/llfjfz/article/details/67638499)
- [Android 数据存储](https://developer.android.google.cn/guide/topics/data/data-storage)
- [ContentProvider 基础](https://developer.android.google.cn/guide/topics/providers/content-provider-basics)
