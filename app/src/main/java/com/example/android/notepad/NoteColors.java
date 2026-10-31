package com.example.android.notepad;

import android.content.Context;

/**
 * 颜色工具：
 * - row()：列表每一行的背景色（分类固定色；"默认"按笔记 ID 轮换浅色）
 * - get()：分类卡片背景色
 */
public final class NoteColors {

    private static final int[] PASTELS = {
            R.color.row_pink,
            R.color.row_blue,
            R.color.row_green,
            R.color.row_yellow
    };

    private NoteColors() {
    }

    /** 列表行背景色 */
    public static int row(Context context, long noteId, String category) {
        if ("工作".equals(category)) {
            return context.getColor(R.color.cat_work);
        }
        if ("学习".equals(category)) {
            return context.getColor(R.color.cat_study);
        }
        if ("生活".equals(category)) {
            return context.getColor(R.color.cat_life);
        }
        // “默认”分类：按 id 轮换，保证列表一定有颜色
        int index = (int) (Math.abs(noteId) % PASTELS.length);
        return context.getColor(PASTELS[index]);
    }

    /** 分类卡片背景色 */
    public static int get(Context context, String category, boolean all) {
        if (all) {
            return context.getColor(R.color.cat_all);
        }
        if (category == null) {
            return context.getColor(R.color.cat_default);
        }
        if ("工作".equals(category)) {
            return context.getColor(R.color.cat_work);
        }
        if ("学习".equals(category)) {
            return context.getColor(R.color.cat_study);
        }
        if ("生活".equals(category)) {
            return context.getColor(R.color.cat_life);
        }
        return context.getColor(R.color.cat_default);
    }
}