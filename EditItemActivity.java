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
import android.widget.HorizontalScrollView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.time.LocalTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

/** Add or change a routine item (what + time), or delete it. The change is sent to the Pi, which saves it. */
public class EditItemActivity extends Activity {
    private final Handler ui = new Handler(Looper.getMainLooper());
    private String oldTime = "";
    private EditText name;
    private Pickers.TimeField timeField;
    private TextView msg;
    private Button save, delete;
    private boolean busy = false, sure = false;

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        oldTime = getIntent().getStringExtra("old") == null ? "" : getIntent().getStringExtra("old");
        String title = getIntent().getStringExtra("title") == null ? "" : getIntent().getStringExtra("title");
        boolean editing = !oldTime.isEmpty();
        LocalTime start = LocalTime.now().plusMinutes(5).withSecond(0).withNano(0);
        if (editing) {
            try {
                start = LocalTime.parse(oldTime);
            } catch (RuntimeException ignored) {
            }
        }

        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 12);
        root.setPadding(p, p, p, p);
        sv.addView(root);

        root.addView(Ui.label(this, editing ? "Edit item" : "New item", 22, Ui.TEXT, true));
        TextView hint = Ui.label(this, "What is it? The Pi will say \"It is ... time\".", 12, Ui.MUTE, false);
        hint.setPadding(0, Ui.dp(this, 2), 0, Ui.dp(this, 4));
        root.addView(hint);

        name = new EditText(this);
        name.setText(title);
        name.setSelectAllOnFocus(true);
        name.setSingleLine(true);
        name.setTextSize(20);
        name.setTextColor(Ui.TEXT);
        name.setHintTextColor(Ui.MUTE);
        name.setHint("for example: eating");
        name.setBackground(Ui.round(Ui.CARD, 8, this));
        name.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setImeOptions(EditorInfo.IME_ACTION_DONE);
        root.addView(name, new LinearLayout.LayoutParams(-1, -2));

        // quick names: tap one instead of typing
        TextView q = Ui.label(this, "Tap a name (or type above):", 11, Ui.MUTE, false);
        q.setPadding(0, Ui.dp(this, 6), 0, 0);
        root.addView(q);
        HorizontalScrollView hs = new HorizontalScrollView(this);
        LinearLayout chips = new LinearLayout(this);
        chips.setOrientation(LinearLayout.HORIZONTAL);
        hs.addView(chips);
        for (final String t : quickNames()) {
            Button chip = Ui.button(this, t, Ui.BTN, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    name.setText(t);
                    name.setSelection(t.length());
                }
            });
            chip.setTextSize(13);
            chip.setMinHeight(Ui.dp(this, 40));
            chip.setMinimumHeight(Ui.dp(this, 40));
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(-2, Ui.dp(this, 40));
            lp.setMargins(Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2), Ui.dp(this, 2));
            chips.addView(chip, lp);
        }
        root.addView(hs);

        TextView tl = Ui.label(this, "At what time?", 12, Ui.MUTE, true);
        tl.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 2));
        root.addView(tl);
        timeField = new Pickers.TimeField(start);
        root.addView(timeField.build(this));

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
        if (editing) {
            delete = Ui.button(this, "Delete", Ui.BTN, new View.OnClickListener() {
                @Override
                public void onClick(View v) {
                    delete();
                }
            });
            LinearLayout.LayoutParams half2 = new LinearLayout.LayoutParams(0, -2, 1);
            half2.setMargins(Ui.dp(this, 3), Ui.dp(this, 6), 0, 0);
            row.addView(delete, half2);
        }
        root.addView(row, new LinearLayout.LayoutParams(-1, -2));
        setContentView(sv);
    }

    private List<String> quickNames() {
        Set<String> s = new LinkedHashSet<>();
        for (Item i : Store.load(this).items.values()) if (!i.isRecording()) s.add(i.title);
        s.addAll(Arrays.asList("wake up", "bath", "eating", "school", "study", "homework", "play", "tea break", "dinner", "sleep"));
        return new ArrayList<>(s).subList(0, Math.min(14, s.size()));
    }

    private void save() {
        if (busy) return;
        final String t = name.getText().toString().trim();
        if (t.isEmpty()) {
            msg.setText("Type what it is, or tap a name.");
            return;
        }
        try {
            send(Edits.routineSet(oldTime.isEmpty() ? null : oldTime, timeField.time, t), "Saved");
        } catch (org.json.JSONException e) {
            msg.setText("Could not save.");
        }
    }

    private void delete() {
        if (busy) return;
        if (!sure) {                                   // first tap asks, second tap deletes
            sure = true;
            delete.setText("Sure?");
            delete.setBackground(Ui.round(Ui.RED, 10, this));
            ui.postDelayed(new Runnable() {
                @Override
                public void run() {
                    sure = false;
                    if (delete != null) {
                        delete.setText("Delete");
                        delete.setBackground(Ui.round(Ui.BTN, 10, EditItemActivity.this));
                    }
                }
            }, 4000);
            return;
        }
        try {
            send(Edits.routineDelete(oldTime), "Deleted");
        } catch (org.json.JSONException e) {
            msg.setText("Could not delete.");
        }
    }

    /** Send to the Pi, take the change back with a normal sync, then close. */
    private void send(final org.json.JSONObject edit, final String done) {
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
                    Sync.once(app, pc, 0);               // the Pi sends back exactly this change
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
