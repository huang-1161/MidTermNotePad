package com.example.android.notepad;

import android.content.Context;
import android.database.Cursor;
import android.text.TextUtils;
import android.view.View;
import android.view.ViewGroup;
import android.widget.SimpleCursorAdapter;
import android.widget.TextView;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

/**
 * 列表适配器：
 * - 第一行：标题
 * - 第二行：[分类] 2026-10-31 06:49（绝对时间戳，恢复了）
 * - 整行背景按分类/ID 上色（美化 UI）
 */
public class NoteListAdapter extends SimpleCursorAdapter {

    private final SimpleDateFormat mDateFormat =
            new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault());

    public NoteListAdapter(Context context, Cursor cursor) {
        super(context,
                R.layout.noteslist_item,
                cursor,
                new String[] {
                        NotePad.Notes.COLUMN_NAME_TITLE,
                        NotePad.Notes.COLUMN_NAME_MODIFICATION_DATE
                },
                new int[] { R.id.title, R.id.subtitle },
                0);
    }

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
        int idIndex = cursor.getColumnIndex(NotePad.Notes._ID);

        String title = (titleIndex >= 0) ? cursor.getString(titleIndex) : "";
        String category = (categoryIndex >= 0) ? cursor.getString(categoryIndex) : null;
        if (TextUtils.isEmpty(category)) {
            category = NotePad.Notes.CATEGORY_DEFAULT;
        }
        long modified = (dateIndex >= 0) ? cursor.getLong(dateIndex) : 0L;
        long noteId = (idIndex >= 0) ? cursor.getLong(idIndex) : 0L;

        TextView titleView = (TextView) view.findViewById(R.id.title);
        TextView subtitleView = (TextView) view.findViewById(R.id.subtitle);
        titleView.setText(title);
        // 恢复绝对时间戳
        subtitleView.setText("[" + category + "] "
                + mDateFormat.format(new Date(modified)));

        view.setBackgroundColor(NoteColors.row(view.getContext(), noteId, category));
        return view;
    }
}