package com.routine.watch;

import android.content.Context;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.LocalTime;
import java.util.Locale;

/** Time and date pickers made of big + / - buttons (typing numbers on a watch is hard). */
final class Pickers {

    private static Button small(Context c, String text, final Runnable action) {
        Button b = Ui.button(c, text, Ui.BTN, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                action.run();
            }
        });
        b.setTextSize(15);
        b.setPadding(0, 0, 0, 0);
        return b;
    }

    private static LinearLayout row(Context c) {
        LinearLayout r = new LinearLayout(c);
        r.setOrientation(LinearLayout.HORIZONTAL);
        r.setGravity(Gravity.CENTER_VERTICAL);
        return r;
    }

    private static void addBtn(Context c, LinearLayout r, Button b) {
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(c, 48), 1);
        lp.setMargins(Ui.dp(c, 2), Ui.dp(c, 2), Ui.dp(c, 2), Ui.dp(c, 2));
        r.addView(b, lp);
    }

    // ------------------------------------------------------------------ time
    static final class TimeField {
        LocalTime time;
        private TextView shown;

        TimeField(LocalTime start) {
            time = start.withSecond(0).withNano(0);
        }

        private void show() {
            int h = time.getHour() % 12 == 0 ? 12 : time.getHour() % 12;
            shown.setText(String.format(Locale.US, "%d:%02d %s", h, time.getMinute(), time.getHour() < 12 ? "AM" : "PM"));
        }

        View build(Context c) {
            LinearLayout box = new LinearLayout(c);
            box.setOrientation(LinearLayout.VERTICAL);
            shown = Ui.label(c, "", 34, Ui.GREEN, true);
            shown.setGravity(Gravity.CENTER);
            box.addView(shown, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout h = row(c);
            addBtn(c, h, small(c, "Hour -", new Runnable() {
                public void run() {
                    time = time.minusHours(1);
                    show();
                }
            }));
            addBtn(c, h, small(c, "Hour +", new Runnable() {
                public void run() {
                    time = time.plusHours(1);
                    show();
                }
            }));
            addBtn(c, h, small(c, "AM/PM", new Runnable() {
                public void run() {
                    time = time.plusHours(12);
                    show();
                }
            }));
            box.addView(h, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout m = row(c);
            final int[] steps = {-5, -1, 1, 5};
            for (final int s : steps) {
                addBtn(c, m, small(c, s > 0 ? "+" + s : String.valueOf(s), new Runnable() {
                    public void run() {
                        time = time.plusMinutes(s);
                        show();
                    }
                }));
            }
            box.addView(Ui.label(c, "minutes", 11, Ui.MUTE, false));
            box.addView(m, new LinearLayout.LayoutParams(-1, -2));
            show();
            return box;
        }
    }

    // ------------------------------------------------------------------ date (can be "no date")
    static final class DateField {
        LocalDate date;                      // null = no date
        private TextView shown;
        private final Runnable changed;

        DateField(LocalDate start, Runnable changed) {
            date = start;
            this.changed = changed;
        }

        private void show() {
            shown.setText(date == null ? "No date" : Schedule.fmtDay(date, LocalDate.now()) + "  "
                    + date.getDayOfMonth() + "/" + date.getMonthValue() + "/" + date.getYear());
            shown.setTextColor(date == null ? Ui.MUTE : Ui.GREEN);
            if (changed != null) changed.run();
        }

        private void move(int days) {
            date = (date == null ? LocalDate.now() : date).plusDays(days);
            show();
        }

        View build(final Context c) {
            LinearLayout box = new LinearLayout(c);
            box.setOrientation(LinearLayout.VERTICAL);
            shown = Ui.label(c, "", 22, Ui.GREEN, true);
            shown.setGravity(Gravity.CENTER);
            box.addView(shown, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout r1 = row(c);
            final int[] steps = {-7, -1, 1, 7};
            for (final int s : steps) {
                addBtn(c, r1, small(c, s > 0 ? "+" + s + "d" : s + "d", new Runnable() {
                    public void run() {
                        move(s);
                    }
                }));
            }
            box.addView(r1, new LinearLayout.LayoutParams(-1, -2));
            LinearLayout r2 = row(c);
            addBtn(c, r2, small(c, "Today", new Runnable() {
                public void run() {
                    date = LocalDate.now();
                    show();
                }
            }));
            addBtn(c, r2, small(c, "Tomorrow", new Runnable() {
                public void run() {
                    date = LocalDate.now().plusDays(1);
                    show();
                }
            }));
            addBtn(c, r2, small(c, "No date", new Runnable() {
                public void run() {
                    date = null;
                    show();
                }
            }));
            box.addView(r2, new LinearLayout.LayoutParams(-1, -2));
            show();
            return box;
        }
    }
}
