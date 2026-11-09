package com.routine.watch;

import android.app.Activity;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalDate;
import java.time.LocalTime;

/** Change a recording's name, date, time and alarm - or delete it. (The sound itself stays on the Pi.) */
public class EditRecActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private String id = "";
    private EditText name;
    private Pickers.DateField dateField;
    private Pickers.TimeField timeField;
    private View timeView;
    private boolean ring = true, busy = false, sure = false;
    private Button ringBtn, save, delete;
    private TextView msg;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        id = getIntent().getStringExtra("id") == null ? "" : getIntent().getStringExtra("id");
        Item it = Store.load(this).items.get("c" + id);
        if (it == null) {                                  // it was deleted on the Pi meanwhile
            finish();
            return;
        }
        ring = it.ring;
        LocalDate d = null;
        LocalTime t = LocalTime.now().plusMinutes(10).withSecond(0).withNano(0);
        try {
            if (!it.date.isEmpty()) {
                d = LocalDate.parse(it.date);
                t = LocalTime.parse(it.time);
            }
        } catch (RuntimeException ignored) {
            d = null;
        }

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 12);
        root.setPadding(p, p, p, p);
        sv.addView(root);

        root.addView(Ui.label(this, "Edit recording", 22, Ui.TEXT, true));
        TextView hint = Ui.label(this, "On its date and time the Pi plays it. No date = only plays when you press Play on the Pi.", 12, Ui.MUTE, false);
        hint.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 4));
        root.addView(hint);

        name = new EditText(this);
        name.setText(it.title);
        name.setSingleLine(true);
        name.setTextSize(20);
        name.setTextColor(Ui.TEXT);
        name.setHintTextColor(Ui.MUTE);
        name.setHint("name of the recording");
        name.setBackground(Ui.round(Ui.CARD, 8, this));
        name.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setImeOptions(EditorInfo.IME_ACTION_DONE);
        root.addView(name, new LinearLayout.LayoutParams(-1, -2));

        TextView dl = Ui.label(this, "Which day?", 12, Ui.MUTE, true);
        dl.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 2));
        root.addView(dl);
        timeField = new Pickers.TimeField(t);
        dateField = new Pickers.DateField(d, new Runnable() {
            @Override
            public void run() {
                if (timeView != null) timeView.setVisibility(dateField.date == null ? View.GONE : View.VISIBLE);
            }
        });
        root.addView(dateField.build(this));
        TextView tl = Ui.label(this, "At what time?", 12, Ui.MUTE, true);
        tl.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 2));
        root.addView(tl);
        timeView = timeField.build(this);
        root.addView(timeView);
        timeView.setVisibility(dateField.date == null ? View.GONE : View.VISIBLE);
        tl.setVisibility(View.VISIBLE);

        ringBtn = Ui.button(this, "", Ui.BTN, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ring = !ring;
                showRing();
            }
        });
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.topMargin = Ui.dp(this, 10);
        root.addView(ringBtn, rl);
        showRing();

        msg = Ui.label(this, "", 14, Ui.RED, true);
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        root.addView(msg, new LinearLayout.LayoutParams(-1, -2));

        save = Ui.button(this, "Save", Ui.GREEN, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                save();
            }
        });
        root.addView(save, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button cancel = Ui.button(this, "Cancel", Ui.BTN, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        LinearLayout.LayoutParams half = new LinearLayout.LayoutParams(0, -2, 1);
        half.setMargins(0, Ui.dp(this, 6), Ui.dp(this, 3), 0);
        row.addView(cancel, half);
        delete = Ui.button(this, "Delete", Ui.BTN, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                delete();
            }
        });
        LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, -2, 1);
        half2.setMargins(Ui.dp(this, 3), Ui.dp(this, 6), 0, 0);
        row.addView(delete, half2);
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));
        setContentView(sv);
    }

    private void showRing() {
        ringBtn.setText(ring ? "Alarm beep before it plays: ON" : "Alarm beep before it plays: OFF");
        ringBtn.setBackground(Ui.round(ring ? Ui.ACCENT : Ui.BTN, 10, this));
    }

    private void save() {
        if (busy) return;
        String t = name.getText().toString().trim();
        if (t.isEmpty()) {
            msg.setText("Type a name for the recording.");
            return;
        }
        try {
            send(Edits.recSet(id, t, dateField.date, timeField.time, ring));
        } catch (org.json.JSONException e) {
            msg.setText("Could not save.");
        }
    }

    private void delete() {
        if (busy) return;
        if (!sure) {
            sure = true;
            delete.setText("Sure?");
            delete.setBackground(Ui.round(Ui.RED, 10, this));
            ui.postDelayed(new Runnable() {
                @Override
                public void run() {
                    sure = false;
                    if (delete != null) {
                        delete.setText("Delete");
                        delete.setBackground(Ui.round(Ui.BTN, 10, EditRecActivity.this));
                    }
                }
            }, 4000);
            return;
        }
        try {
            send(Edits.recDelete(id));
        } catch (org.json.JSONException e) {
            msg.setText("Could not delete.");
        }
    }

    private void send(final org.json.JSONObject edit) {
        if (!Store.isConfigured(this)) {
            msg.setText("Not connected to the Pi. Use Setup first.");
            return;
        }
        busy = true;
        msg.setTextColor(Ui.MUTE);
        msg.setText("Saving on the Pi ...");
        final Context app = getApplicationContext();
        new Thread(new Runnable() {
            @Override
            public void run() {
                String err = null;
                try {
                    PiClient pc = Store.client(app);
                    pc.edit(edit);
                    Sync.once(app, pc, 0);
                } catch (PiClient.RejectedException e) {
                    err = e.getMessage();
                } catch (PiClient.AuthException e) {
                    err = "Wrong code. Use Setup.";
                } catch (Exception e) {
                    err = "The Pi is not reachable. Try again when you are on the home Wi-Fi.";
                }
                final String e2 = err;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        busy = false;
                        if (isFinishing()) return;
                        if (e2 != null) {
                            msg.setTextColor(Ui.RED);
                            msg.setText(e2);
                        } else {
                            setResult(RESULT_OK);
                            finish();
                        }
                    }
                });
            }
        }, "edit").start();
    }
}
