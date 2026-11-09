package com.routine.watch;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextClock;
import android.widget.TextView;
import android.widget.Toast;

import org.json.JSONObject;

import java.time.LocalDateTime;
import java.util.List;

/** The watch face of the app: clock, what is next, today's list, and a "Brief me" button. */
public class MainActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private volatile int gen = 0;                       // changes when the screen is left, so old sync loops stop
    private volatile PiClient client;
    private volatile String net = "";
    private volatile boolean netBad = false;
    private TextView status, nextTitle, nextWhen;
    private LinearLayout list;

    private final Runnable minuteTick = new Runnable() {
        @Override
        public void run() {
            refresh();
            ui.postDelayed(this, 30000);
        }
    };

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        sv.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 12);
        root.setPadding(p, p, p, p);
        sv.addView(root);

        LinearLayout clockRow = new LinearLayout(this);
        clockRow.setOrientation(LinearLayout.HORIZONTAL);
        clockRow.setGravity(Gravity.CENTER_HORIZONTAL | Gravity.BOTTOM);
        TextClock clock = new TextClock(this);
        clock.setFormat12Hour("h:mm");
        clock.setFormat24Hour("h:mm");
        clock.setTextSize(54);
        clock.setTextColor(Ui.TEXT);
        clock.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        TextClock ap = new TextClock(this);
        ap.setFormat12Hour(" a");
        ap.setFormat24Hour(" a");
        ap.setTextSize(18);
        ap.setTextColor(Ui.MUTE);
        ap.setPadding(0, 0, 0, Ui.dp(this, 10));
        clockRow.addView(clock);
        clockRow.addView(ap);
        root.addView(clockRow);
        TextClock date = new TextClock(this);
        date.setFormat12Hour("EEEE, d MMMM");
        date.setFormat24Hour("EEEE, d MMMM");
        date.setTextSize(15);
        date.setTextColor(Ui.MUTE);
        date.setGravity(Gravity.CENTER);
        root.addView(date, new LinearLayout.LayoutParams(-1, -2));

        status = Ui.label(this, "", 12, Ui.MUTE, false);
        status.setGravity(Gravity.CENTER);
        status.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 8));
        root.addView(status, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackground(Ui.round(Ui.CARD, 12, this));
        card.setPadding(p, p, p, p);
        card.addView(Ui.label(this, "NEXT", 11, Ui.MUTE, true));
        nextTitle = Ui.label(this, "", 22, Ui.TEXT, true);
        nextWhen = Ui.label(this, "", 14, Ui.ACCENT, true);
        card.addView(nextTitle);
        card.addView(nextWhen);
        root.addView(card, new LinearLayout.LayoutParams(-1, -2));

        LinearLayout buttons = new LinearLayout(this);
        buttons.setOrientation(LinearLayout.HORIZONTAL);
        buttons.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 6));
        Button brief = Ui.button(this, "Brief me", Ui.GREEN, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                briefMe();
            }
        });
        Button sync = Ui.button(this, "Sync", Ui.BTN, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                syncNow();
            }
        });
        Button setup = Ui.button(this, "Setup", Ui.BTN, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, SetupActivity.class));
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, -2, 1);
        lp.setMargins(Ui.dp(this, 3), 0, Ui.dp(this, 3), 0);
        buttons.addView(brief, lp);
        buttons.addView(sync, lp);
        buttons.addView(setup, lp);
        root.addView(buttons, new LinearLayout.LayoutParams(-1, -2));

        list = new LinearLayout(this);
        list.setOrientation(LinearLayout.VERTICAL);
        root.addView(list, new LinearLayout.LayoutParams(-1, -2));
        setContentView(sv);

        Alarms.scheduleNext(this);                      // make sure the alarms are set (also after an update)
        Alarms.scheduleSync(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refresh();
        startSync(false);
        ui.postDelayed(minuteTick, 30000);
    }

    @Override
    protected void onPause() {
        super.onPause();
        gen++;
        PiClient c = client;
        if (c != null) c.abort();
        ui.removeCallbacks(minuteTick);
    }

    // ------------------------------------------------------------------ syncing while the app is open
    private void syncNow() {
        if (!Store.isConfigured(this)) {
            startActivity(new Intent(this, SetupActivity.class));
            return;
        }
        Toast.makeText(this, "Looking for news from the Pi ...", Toast.LENGTH_SHORT).show();
        PiClient c = client;
        gen++;
        if (c != null) c.abort();
        startSync(true);
    }

    /** Keeps asking the Pi "anything new?"; the Pi answers the moment something changes (or after 20 s of nothing). */
    private void startSync(boolean quick) {
        final int my = ++gen;
        if (!Store.isConfigured(this)) {
            net = "Not connected. Tap Setup.";
            netBad = true;
            refresh();
            return;
        }
        final PiClient pc = Store.client(this);
        client = pc;
        final android.content.Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                int fail = 0;
                boolean first = true;
                while (my == gen) {
                    try {
                        SyncEngine.Result r = Sync.once(app, pc, first ? 0 : 20);
                        first = false;
                        fail = 0;
                        net = "";
                        netBad = false;
                        postRefresh(my, r.anyChange());
                    } catch (PiClient.AuthException e) {
                        net = "Wrong code. Tap Setup.";
                        netBad = true;
                        postRefresh(my, false);
                        pause(my, 30000);
                    } catch (Exception e) {
                        if (my != gen) break;
                        fail++;
                        net = "Pi not reachable - showing the saved schedule";
                        netBad = true;
                        postRefresh(my, false);
                        pause(my, Math.min(60000, 5000 * fail));
                    }
                }
            }
        }, "sync").start();
    }

    private void postRefresh(final int my, final boolean rebuild) {
        ui.post(new Runnable() {
            @Override
            public void run() {
                if (my == gen) refresh();
            }
        });
    }

    private void pause(int my, int ms) {
        for (int t = 0; t < ms && my == gen; t += 250) {
            try {
                Thread.sleep(250);
            } catch (InterruptedException e) {
                return;
            }
        }
    }

    // ------------------------------------------------------------------ the screen
    private void refresh() {
        Data d = Store.load(this);
        LocalDateTime now = LocalDateTime.now();
        // status line
        String s;
        if (netBad) {
            s = net;
        } else if (d.lastOkMs == 0) {
            s = Store.isConfigured(this) ? "Connecting ..." : "Not connected. Tap Setup.";
        } else {
            long mins = (System.currentTimeMillis() - d.lastOkMs) / 60000;
            s = mins < 1 ? "Up to date" : "Synced " + (mins < 60 ? mins + " min ago" : (mins / 60) + " h ago");
        }
        status.setText(s);
        status.setTextColor(netBad ? Ui.AMBER : Ui.MUTE);
        // next
        Schedule.Row nx = Schedule.next(d.items.values(), now);
        if (nx == null) {
            nextTitle.setText(d.items.isEmpty() ? "Nothing yet" : "Nothing planned");
            nextWhen.setText(d.items.isEmpty() ? "Tap Sync after connecting" : "");
        } else {
            nextTitle.setText(nx.item.title);
            nextWhen.setText(Schedule.fmt12(nx.when.toLocalTime()) + "   " + Schedule.when(nx.when, now));
        }
        // list
        list.removeAllViews();
        List<Schedule.Row> today = Schedule.today(d.items.values(), now);
        if (!today.isEmpty()) list.addView(header("TODAY"));
        for (Schedule.Row r : today) list.addView(row(r, now));
        List<Schedule.Row> later = Schedule.later(d.items.values(), now);
        if (!later.isEmpty()) list.addView(header("LATER"));
        for (Schedule.Row r : later) list.addView(row(r, now));
    }

    private TextView header(String s) {
        TextView t = Ui.label(this, s, 11, Ui.MUTE, true);
        t.setPadding(Ui.dp(this, 2), Ui.dp(this, 10), 0, Ui.dp(this, 4));
        return t;
    }

    private View row(Schedule.Row r, LocalDateTime now) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setBackground(Ui.round(Ui.CARD, 10, this));
        int p = Ui.dp(this, 10);
        row.setPadding(p, p, p, p);
        int color = r.status.equals("now") ? Ui.GREEN : r.status.equals("done") ? Ui.MUTE : Ui.TEXT;
        TextView time = Ui.label(this, Schedule.fmt12(r.when.toLocalTime()), 14, color, true);
        time.setMinWidth(Ui.dp(this, 70));
        TextView title = Ui.label(this, (r.status.equals("later") ? Schedule.when(r.when, now).replace(Schedule.fmt12(r.when.toLocalTime()), "").trim() + "  " : "")
                + r.item.title, 16, color, false);
        TextView tag = Ui.label(this, r.status.equals("now") ? "NOW" : r.status.equals("done") ? "DONE" : r.status.equals("next") ? "" : "", 11,
                r.status.equals("now") ? Ui.GREEN : Ui.MUTE, true);
        row.addView(time);
        row.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        row.addView(tag);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-1, -2);
        lp.bottomMargin = Ui.dp(this, 4);
        row.setLayoutParams(lp);
        return row;
    }

    // ------------------------------------------------------------------ Brief me
    private void briefMe() {
        if (!Store.isConfigured(this)) {
            startActivity(new Intent(this, SetupActivity.class));
            return;
        }
        Toast.makeText(this, "Asking the Pi ...", Toast.LENGTH_SHORT).show();
        final PiClient pc = Store.client(this);
        new Thread(new Runnable() {
            @Override
            public void run() {
                String text = null, err = null;
                boolean spoke = false;
                try {
                    JSONObject r = pc.brief();
                    text = r.optString("text", "");
                    spoke = r.optBoolean("spoke", false);
                } catch (Exception e) {
                    err = e instanceof PiClient.AuthException ? "Wrong code. Tap Setup." : "The Pi is not reachable right now.";
                }
                final String t = text, e = err;
                final boolean sp = spoke;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        if (e != null) {
                            Toast.makeText(MainActivity.this, e, Toast.LENGTH_LONG).show();
                        } else {
                            new AlertDialog.Builder(MainActivity.this).setTitle(sp ? "The Pi is speaking" : "Your day")
                                    .setMessage(t).setPositiveButton("OK", null).show();
                        }
                    }
                });
            }
        }, "brief").start();
    }
}
