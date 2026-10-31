/*
 * Copyright (C) 2007 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.notepad;

import com.example.android.notepad.NotePad;

import android.app.AlertDialog;
import android.app.ListActivity;
import android.content.ClipboardManager;
import android.content.ClipData;
import android.content.ComponentName;
import android.content.ContentUris;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.text.TextUtils;
import android.util.Log;
import android.view.ContextMenu;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ContextMenu.ContextMenuInfo;
import android.widget.AdapterView;
import android.widget.ListView;
import android.widget.SearchView;

import java.util.ArrayList;
import java.util.List;

/**
 * 笔记列表：彩色行 + 时间戳 + 搜索 + 排序 + 背景 + 分类入口。
 */
public class NotesList extends ListActivity {

    /** 从 CategoryActivity 传进来的分类过滤（不传 = 全部） */
    public static final String EXTRA_CATEGORY = "category";

    private static final String TAG = "NotesList";

    private static final String PREFS = "notepad_prefs";
    private static final String KEY_SORT = "sort_order";

    // 查询列：ID / 标题 / 修改时间 / 分类
    private static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID,
            NotePad.Notes.COLUMN_NAME_TITLE,
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE,
            NotePad.Notes.COLUMN_NAME_CATEGORY
    };

    private static final int COLUMN_INDEX_TITLE = 1;

    // 排序方式
    private static final String[] SORT_NAMES = {
            "修改时间（最新在前）", "创建时间（最新在前）", "标题（A→Z）"
    };
    private static final String[] SORT_ORDERS = {
            NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE + " DESC",
            NotePad.Notes.COLUMN_NAME_CREATE_DATE + " DESC",
            NotePad.Notes.COLUMN_NAME_TITLE + " ASC"
    };

    private NoteListAdapter mAdapter;
    private String mSortOrder;
    private String mKeyword = "";
    private String mCategoryFilter;   // null = 全部

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        NoteStyle.applyBackground(this);

        setDefaultKeyMode(DEFAULT_KEYS_SHORTCUT);

        Intent intent = getIntent();
        if (intent.getData() == null) {
            intent.setData(NotePad.Notes.CONTENT_URI);
        }

        mCategoryFilter = getIntent().getStringExtra(EXTRA_CATEGORY);
        if (!TextUtils.isEmpty(mCategoryFilter)) {
            setTitle("分类：" + mCategoryFilter);
        }

        mSortOrder = getSharedPreferences(PREFS, MODE_PRIVATE)
                .getString(KEY_SORT, SORT_ORDERS[0]);

        getListView().setOnCreateContextMenuListener(this);

        mAdapter = new NoteListAdapter(this, queryNotes());
        setListAdapter(mAdapter);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 从编辑页回来后刷新（分类/时间可能变了）
        reloadNotes();
    }

    /** 组合 关键字 + 分类 的查询 */
    private Cursor queryNotes() {
        StringBuilder selection = new StringBuilder();
        List<String> args = new ArrayList<String>();

        if (!TextUtils.isEmpty(mKeyword)) {
            selection.append("(")
                    .append(NotePad.Notes.COLUMN_NAME_TITLE).append(" LIKE ? OR ")
                    .append(NotePad.Notes.COLUMN_NAME_NOTE).append(" LIKE ?)");
            args.add("%" + mKeyword + "%");
            args.add("%" + mKeyword + "%");
        }

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
        }
    }

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
                        reloadNotes();
                    }
                })
                .show();
    }

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

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        super.onPrepareOptionsMenu(menu);

        ClipboardManager clipboard = (ClipboardManager)
                getSystemService(Context.CLIPBOARD_SERVICE);
        MenuItem mPasteItem = menu.findItem(R.id.menu_paste);

        if (clipboard.hasPrimaryClip()) {
            mPasteItem.setEnabled(true);
        } else {
            mPasteItem.setEnabled(false);
        }

        final boolean haveItems = getListAdapter() != null && getListAdapter().getCount() > 0;

        if (haveItems) {
            Uri uri = ContentUris.withAppendedId(getIntent().getData(), getSelectedItemId());

            Intent[] specifics = new Intent[1];
            specifics[0] = new Intent(Intent.ACTION_EDIT, uri);

            MenuItem[] items = new MenuItem[1];
            Intent intent = new Intent(null, uri);
            intent.addCategory(Intent.CATEGORY_ALTERNATIVE);

            menu.addIntentOptions(
                    Menu.CATEGORY_ALTERNATIVE,
                    Menu.NONE,
                    Menu.NONE,
                    null,
                    specifics,
                    intent,
                    Menu.NONE,
                    items
            );

            if (items[0] != null) {
                items[0].setShortcut('1', 'e');
            }
        } else {
            menu.removeGroup(Menu.CATEGORY_ALTERNATIVE);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        if (id == R.id.menu_add) {
            startActivity(new Intent(Intent.ACTION_INSERT, getIntent().getData()));
            return true;
        } else if (id == R.id.menu_paste) {
            startActivity(new Intent(Intent.ACTION_PASTE, getIntent().getData()));
            return true;
        } else if (id == R.id.menu_categories) {
            startActivity(new Intent(this, CategoryActivity.class));
            return true;
        } else if (id == R.id.menu_sort) {
            showSortDialog();
            return true;
        } else if (id == R.id.menu_background) {
            NoteStyle.showPicker(this, new Runnable() {
                @Override
                public void run() {
                    recreate();
                }
            });
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onCreateContextMenu(ContextMenu menu, View view, ContextMenuInfo menuInfo) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) menuInfo;
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return;
        }

        Cursor cursor = (Cursor) getListAdapter().getItem(info.position);
        if (cursor == null) {
            return;
        }

        MenuInflater inflater = getMenuInflater();
        inflater.inflate(R.menu.list_context_menu, menu);

        menu.setHeaderTitle(cursor.getString(COLUMN_INDEX_TITLE));

        Intent intent = new Intent(null, Uri.withAppendedPath(getIntent().getData(),
                Integer.toString((int) info.id) ));
        intent.addCategory(Intent.CATEGORY_ALTERNATIVE);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        menu.addIntentOptions(Menu.CATEGORY_ALTERNATIVE, 0, 0,
                new ComponentName(this, NotesList.class), null, intent, 0, null);
    }

    @Override
    public boolean onContextItemSelected(MenuItem item) {
        AdapterView.AdapterContextMenuInfo info;
        try {
            info = (AdapterView.AdapterContextMenuInfo) item.getMenuInfo();
        } catch (ClassCastException e) {
            Log.e(TAG, "bad menuInfo", e);
            return false;
        }

        Uri noteUri = ContentUris.withAppendedId(getIntent().getData(), info.id);
        int id = item.getItemId();

        if (id == R.id.context_open) {
            startActivity(new Intent(Intent.ACTION_EDIT, noteUri));
            return true;
        } else if (id == R.id.context_copy) {
            ClipboardManager clipboard = (ClipboardManager)
                    getSystemService(Context.CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newUri(getContentResolver(), "Note", noteUri));
            return true;
        } else if (id == R.id.context_delete) {
            getContentResolver().delete(noteUri, null, null);
            reloadNotes();
            return true;
        }
        return super.onContextItemSelected(item);
    }

    @Override
    protected void onListItemClick(ListView l, View v, int position, long id) {
        Uri uri = ContentUris.withAppendedId(getIntent().getData(), id);
        String action = getIntent().getAction();

        if (Intent.ACTION_PICK.equals(action) || Intent.ACTION_GET_CONTENT.equals(action)) {
            setResult(RESULT_OK, new Intent().setData(uri));
        } else {
            startActivity(new Intent(Intent.ACTION_EDIT, uri));
        }
    }
}