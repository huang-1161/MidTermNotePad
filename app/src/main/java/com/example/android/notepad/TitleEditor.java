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

import android.app.Activity;
import android.content.ContentValues;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.EditText;

/**
 * 修改笔记标题的对话框式 Activity。
 */
public class TitleEditor extends Activity {

    /**
     * 自定义 action：编辑标题
     */
    public static final String EDIT_TITLE_ACTION = "com.android.notepad.action.EDIT_TITLE";

    // 查询列：ID、标题
    private static final String[] PROJECTION = new String[] {
            NotePad.Notes._ID,               // 0
            NotePad.Notes.COLUMN_NAME_TITLE, // 1
    };

    // 标题所在列的索引
    private static final int COLUMN_INDEX_TITLE = 1;

    private Cursor mCursor;
    private EditText mText;
    private Uri mUri;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.title_editor);

        mUri = getIntent().getData();

        mCursor = managedQuery(
                mUri,        // 要查询的笔记 URI
                PROJECTION,  // 列
                null,        // 无 where 条件
                null,        // 无 where 参数
                null         // 无排序
        );

        mText = (EditText) this.findViewById(R.id.title);
    }

    @Override
    protected void onResume() {
        super.onResume();

        if (mCursor != null) {
            mCursor.moveToFirst();
            mText.setText(mCursor.getString(COLUMN_INDEX_TITLE));
        }
    }

    @Override
    protected void onPause() {
        super.onPause();

        // 离开时把输入框里的标题写回数据库
        if (mCursor != null) {

            ContentValues values = new ContentValues();
            values.put(NotePad.Notes.COLUMN_NAME_TITLE, mText.getText().toString());

            getContentResolver().update(
                    mUri,
                    values,
                    null,
                    null
            );
        }
    }

    public void onClickOk(View v) {
        finish();
    }
}