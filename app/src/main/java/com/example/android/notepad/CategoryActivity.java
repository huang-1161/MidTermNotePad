package com.example.android.notepad;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.BaseAdapter;
import android.widget.GridView;
import android.widget.ImageView;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.List;

/**
 * 笔记分类页面：2 列卡片（全部 / 默认 / 工作 / 学习 / 生活），点击进入对应分类的列表。
 */
public class CategoryActivity extends Activity {

    private GridView mGrid;
    private CategoryAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        setContentView(R.layout.activity_category);
        setTitle(R.string.title_categories);

        mGrid = (GridView) findViewById(R.id.grid);
        mAdapter = new CategoryAdapter();
        mGrid.setAdapter(mAdapter);

        mGrid.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                CategoryItem item = mAdapter.getItem(position);
                Intent intent = new Intent(CategoryActivity.this, NotesList.class);
                intent.setData(NotePad.Notes.CONTENT_URI);
                if (!item.all) {
                    intent.putExtra(NotesList.EXTRA_CATEGORY, item.name);
                }
                startActivity(intent);
            }
        });
    }

    @Override
    protected void onResume() {
        super.onResume();
        // 回到本页时刷新各分类的数量
        mAdapter.reload();
    }

    /** 一个分类卡片的数据 */
    private static class CategoryItem {
        final String name;
        final int count;
        final int color;
        final boolean all;

        CategoryItem(String name, int count, int color, boolean all) {
            this.name = name;
            this.count = count;
            this.color = color;
            this.all = all;
        }
    }

    private class CategoryAdapter extends BaseAdapter {

        private final List<CategoryItem> mItems = new ArrayList<CategoryItem>();

        CategoryAdapter() {
            reload();
        }

        void reload() {
            mItems.clear();

            int total = 0;
            int[] counts = new int[NotePad.Notes.CATEGORIES.length];

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

            mItems.add(new CategoryItem(getString(R.string.category_all), total,
                    NoteColors.get(CategoryActivity.this, null, true), true));

            for (int i = 0; i < NotePad.Notes.CATEGORIES.length; i++) {
                String name = NotePad.Notes.CATEGORIES[i];
                mItems.add(new CategoryItem(name, counts[i],
                        NoteColors.get(CategoryActivity.this, name, false), false));
            }

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

            TextView name = (TextView) view.findViewById(R.id.cat_name);
            TextView count = (TextView) view.findViewById(R.id.cat_count);
            ImageView icon = (ImageView) view.findViewById(R.id.cat_icon);

            name.setText(item.name);
            count.setText(getString(R.string.category_count, item.count));
            icon.setImageResource(iconFor(item));
            view.setBackground(rounded(item.color));

            return view;
        }

        private int iconFor(CategoryItem item) {
            if (item.all) {
                return android.R.drawable.ic_menu_agenda;
            }
            if ("工作".equals(item.name)) {
                return android.R.drawable.ic_menu_manage;
            }
            if ("学习".equals(item.name)) {
                return android.R.drawable.ic_menu_edit;
            }
            if ("生活".equals(item.name)) {
                return android.R.drawable.ic_menu_myplaces;
            }
            return android.R.drawable.ic_menu_agenda;
        }

        private GradientDrawable rounded(int color) {
            float density = getResources().getDisplayMetrics().density;
            GradientDrawable drawable = new GradientDrawable();
            drawable.setColor(color);
            drawable.setCornerRadius(12 * density);
            drawable.setStroke((int) density, 0x22000000);
            return drawable;
        }
    }
}