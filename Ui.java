package com.routine.watch;

import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

/** Small helpers so the screens are built in code (no layout files). Same colours as the Pi app. */
final class Ui {
    static final int BG = Color.parseColor("#0e1116"), CARD = Color.parseColor("#181c24"), TEXT = Color.parseColor("#eef1f6"),
            MUTE = Color.parseColor("#8b93a3"), ACCENT = Color.parseColor("#4f8cff"), GREEN = Color.parseColor("#3ddc84"),
            RED = Color.parseColor("#ff5252"), AMBER = Color.parseColor("#ffb300"), BTN = Color.parseColor("#343a48");

    static int dp(Context c, float v) {
        return (int) (v * c.getResources().getDisplayMetrics().density + 0.5f);
    }

    static TextView label(Context c, String s, int sp, int color, boolean bold) {
        TextView t = new TextView(c);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(color);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    static GradientDrawable round(int color, float radiusDp, Context c) {
        GradientDrawable g = new GradientDrawable();
        g.setColor(color);
        g.setCornerRadius(dp(c, radiusDp));
        return g;
    }

    static Button button(Context c, String text, int color, View.OnClickListener l) {
        Button b = new Button(c);
        b.setText(text);
        b.setTextSize(15);
        b.setAllCaps(false);
        b.setTextColor(color == GREEN ? Color.BLACK : Color.WHITE);
        b.setTypeface(Typeface.DEFAULT_BOLD);
        b.setBackground(round(color, 10, c));
        b.setMinHeight(dp(c, 48));
        b.setMinimumHeight(dp(c, 48));
        b.setGravity(Gravity.CENTER);
        b.setOnClickListener(l);
        return b;
    }
}
