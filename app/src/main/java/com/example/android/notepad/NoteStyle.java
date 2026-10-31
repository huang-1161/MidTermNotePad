package com.example.android.notepad;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Context;
import android.content.DialogInterface;
import android.content.SharedPreferences;
import android.graphics.Color;
import android.graphics.drawable.ColorDrawable;

/**
 * 主题/背景工具类：SharedPreferences 记住背景色，列表页和编辑页启动时应用。
 */
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

    public static void applyBackground(Activity activity) {
        if (activity == null) {
            return;
        }
        activity.getWindow().setBackgroundDrawable(
                new ColorDrawable(getBackgroundColor(activity)));
    }

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