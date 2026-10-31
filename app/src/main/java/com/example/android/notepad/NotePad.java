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

import android.net.Uri;
import android.provider.BaseColumns;

/**
 * NotePad 的 ContentProvider 契约类：定义列名、URI、MIME 类型。
 */
public final class NotePad {

    public static final String AUTHORITY = "com.google.provider.NotePad";

    // This class cannot be instantiated
    private NotePad() {
    }

    public static final class Notes implements BaseColumns {

        // This class cannot be instantiated
        private Notes() {}

        /** 表名 */
        public static final String TABLE_NAME = "notes";

        /*
         * URI 定义
         */
        private static final String SCHEME = "content://";
        private static final String PATH_NOTES = "/notes";
        private static final String PATH_NOTE_ID = "/notes/";
        private static final String PATH_LIVE_FOLDER = "/live_folders/notes";

        /** 0-relative position of a note ID segment in the path part of a note ID URI */
        public static final int NOTE_ID_PATH_POSITION = 1;

        public static final Uri CONTENT_URI =
                Uri.parse(SCHEME + AUTHORITY + PATH_NOTES);

        public static final Uri CONTENT_ID_URI_BASE =
                Uri.parse(SCHEME + AUTHORITY + PATH_NOTE_ID);

        public static final Uri CONTENT_ID_URI_PATTERN =
                Uri.parse(SCHEME + AUTHORITY + PATH_NOTE_ID + "/#");

        public static final Uri LIVE_FOLDER_URI =
                Uri.parse(SCHEME + AUTHORITY + PATH_LIVE_FOLDER);

        /*
         * MIME 类型
         */
        public static final String CONTENT_TYPE =
                "vnd.android.cursor.dir/vnd.google.note";

        public static final String CONTENT_ITEM_TYPE =
                "vnd.android.cursor.item/vnd.google.note";

        /** 默认排序：修改时间倒序 */
        public static final String DEFAULT_SORT_ORDER = "modified DESC";

        /*
         * 列定义
         */
        /** 标题 */
        public static final String COLUMN_NAME_TITLE = "title";

        /** 正文 */
        public static final String COLUMN_NAME_NOTE = "note";

        /** 创建时间（毫秒） */
        public static final String COLUMN_NAME_CREATE_DATE = "created";

        /** 修改时间（毫秒） */
        public static final String COLUMN_NAME_MODIFICATION_DATE = "modified";

        // ================= 附加功能：笔记分类 =================
        /** 分类列（TEXT） */
        public static final String COLUMN_NAME_CATEGORY = "category";

        /** 默认分类 */
        public static final String CATEGORY_DEFAULT = "默认";

        /** 可选分类（编辑页弹窗用） */
        public static final String[] CATEGORIES = {"默认", "工作", "学习", "生活"};
        // =====================================================
    }
}