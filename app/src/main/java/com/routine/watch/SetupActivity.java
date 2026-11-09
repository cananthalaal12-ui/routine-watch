package com.routine.watch;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.util.List;

/** Connect to the Pi: "Find Pi" (or type its address), type the 6-digit code from the Pi's home screen, Connect. */
public class SetupActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private TextView addrBox, codeBox, msg;
    private LinearLayout foundBox;
    private final StringBuilder addr = new StringBuilder(), code = new StringBuilder();
    private int port = PiClient.DEFAULT_PORT;
    private boolean editingCode = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        if (Store.isConfigured(this)) {
            addr.append(Store.host(this));
            port = Store.port(this);
        }
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 12);
        root.setPadding(p, p, p, p);
        sv.addView(root);

        root.addView(Ui.label(this, "Connect to the Pi", 22, Ui.TEXT, true));
        TextView how = Ui.label(this, "1. Be on the home Wi-Fi.   2. Tap Find Pi.   3. Type the code shown on the Pi's home screen.   4. Connect.", 12, Ui.MUTE, false);
        how.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 8));
        root.addView(how);

        root.addView(Ui.button(this, "Find Pi", Ui.ACCENT, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                find();
            }
        }), new LinearLayout.LayoutParams(-1, -2));
        foundBox = new LinearLayout(this);
        foundBox.setOrientation(LinearLayout.VERTICAL);
        root.addView(foundBox, new LinearLayout.LayoutParams(-1, -2));

        root.addView(field("Pi address", false));
        root.addView(field("Code", true));

        String[][] keys = {{"1", "2", "3"}, {"4", "5", "6"}, {"7", "8", "9"}, {".", "0", "<"}};
        for (String[] rowKeys : keys) {
            LinearLayout r = new LinearLayout(this);
            r.setOrientation(LinearLayout.HORIZONTAL);
            for (final String k : rowKeys) {
                Button kb = Ui.button(this, k.equals("<") ? "Del" : k, Ui.BTN, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        press(k);
                    }
                });
                kb.setTextSize(20);
                LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, Ui.dp(this, 52), 1);
                lp.setMargins(Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2));
                r.addView(kb, lp);
            }
            root.addView(r, new LinearLayout.LayoutParams(-1, -2));
        }
        Button connect = Ui.button(this, "Connect", Ui.GREEN, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                connect();
            }
        });
        LinearLayout.LayoutParams cl = new LinearLayout.LayoutParams(-1, -2);
        cl.topMargin = Ui.dp(this, 8);
        root.addView(connect, cl);
        msg = Ui.label(this, "", 14, Ui.AMBER, true);
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 16));
        root.addView(msg);
        setContentView(sv);
        show();
    }

    private View field(String name, final boolean isCode) {
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.VERTICAL);
        row.setPadding(0, Ui.dp(this, 8), 0, 0);
        row.addView(Ui.label(this, name, 12, Ui.MUTE, true));
        TextView box = Ui.label(this, "", 22, Ui.TEXT, true);
        box.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        box.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                editingCode = isCode;
                show();
            }
        });
        if (isCode) codeBox = box;
        else addrBox = box;
        row.addView(box, new LinearLayout.LayoutParams(-1, -2));
        return row;
    }

    private void show() {
        addrBox.setText(addr.length() == 0 ? "tap Find Pi, or type" : addr + (port != PiClient.DEFAULT_PORT ? ":" + port : ""));
        StringBuilder dots = new StringBuilder(code);
        while (dots.length() < 6) dots.append('_');
        codeBox.setText(dots.toString().replace("", " ").trim());
        addrBox.setBackground(Ui.round(editingCode ? Ui.CARD : Ui.BTN, 8, this));
        codeBox.setBackground(Ui.round(editingCode ? Ui.BTN : Ui.CARD, 8, this));
    }

    private void press(String k) {
        StringBuilder sb = editingCode ? code : addr;
        if (k.equals("<")) {
            if (sb.length() > 0) sb.setLength(sb.length() - 1);
        } else if (editingCode) {
            if (!k.equals(".") && sb.length() < 6) sb.append(k);
            if (sb.length() == 6) msg.setText("");
        } else if (sb.length() < 40) {
            sb.append(k);
        }
        show();
    }

    private void find() {
        msg.setTextColor(Ui.MUTE);
        msg.setText("Looking for the Pi on the Wi-Fi ...");
        foundBox.removeAllViews();
        new Thread(new Runnable() {
            @Override
            public void run() {
                final List<PiClient.Found> found = PiClient.discover(3500);
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        if (found.isEmpty()) {
                            msg.setTextColor(Ui.AMBER);
                            msg.setText("No Pi found. Is the watch on the same Wi-Fi? You can type the Pi's address instead.");
                            return;
                        }
                        msg.setText("");
                        for (final PiClient.Found f : found) {
                            foundBox.addView(Ui.button(SetupActivity.this, f.name + "   " + f.ip, Ui.BTN, new View.OnClickListener() {
                                @Override
                                public void onClick(View v) {
                                    addr.setLength(0);
                                    addr.append(f.ip);
                                    port = f.port;
                                    editingCode = true;
                                    show();
                                }
                            }), new LinearLayout.LayoutParams(-1, -2));
                        }
                        if (found.size() == 1) {                    // only one Pi: choose it for the person
                            addr.setLength(0);
                            addr.append(found.get(0).ip);
                            port = found.get(0).port;
                            editingCode = true;
                            show();
                            msg.setText("Found it. Now type the code.");
                        }
                    }
                });
            }
        }, "find").start();
    }

    private void connect() {
        final String host = addr.toString().trim();
        final String c = code.toString();
        if (!host.matches("[0-9A-Za-z.\\-]+")) {
            msg.setTextColor(Ui.AMBER);
            msg.setText("Type the Pi's address first (tap Find Pi).");
            return;
        }
        if (c.length() != 6) {
            msg.setTextColor(Ui.AMBER);
            msg.setText("The code has 6 digits. It is on the Pi's home screen (or run: python3 watch_api.py --info).");
            return;
        }
        msg.setTextColor(Ui.MUTE);
        msg.setText("Connecting ...");
        final Context app = getApplicationContext();
        final int prt = port;
        new Thread(new Runnable() {
            @Override
            public void run() {
                String err = null;
                int n = 0;
                try {
                    Data d = new Data();                                // a brand new copy: the Pi sends everything
                    SyncEngine.sync(d, new PiClient(host, prt, c), 0);
                    d.lastOkMs = System.currentTimeMillis();
                    Store.setPi(app, host, prt, c);
                    Store.save(app, d);
                    n = d.items.size();
                    Alarms.scheduleNext(app);
                    Alarms.scheduleSync(app);
                } catch (PiClient.AuthException e) {
                    err = "Wrong code. Look at the code on the Pi's home screen.";
                } catch (Exception e) {
                    err = "Could not reach the Pi. Same Wi-Fi? Is the Pi app running?";
                }
                final String e2 = err;
                final int count = n;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        if (isFinishing()) return;
                        if (e2 != null) {
                            msg.setTextColor(Ui.RED);
                            msg.setText(e2);
                        } else {
                            msg.setTextColor(Ui.GREEN);
                            msg.setText("Connected! Got " + count + " items.");
                            ui.postDelayed(new Runnable() {
                                @Override
                                public void run() {
                                    finish();
                                }
                            }, 1200);
                        }
                    }
                });
            }
        }, "connect").start();
    }
}
