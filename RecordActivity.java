package com.routine.watch;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.media.AudioManager;
import android.media.MediaPlayer;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

/** Record a voice note on the watch, then name it, give it a day/time (or none) and send it to the Pi. */
public class RecordActivity extends Activity {
    private static final int ASK_MIC = 7;
    private final Handler ui = new Handler(Looper.getMainLooper());
    private File wav;
    private WavRecorder recorder;
    private MediaPlayer player;
    private boolean recording = false, reviewing = false, busy = false, ring = true, backOnce = false;

    private LinearLayout stage1, stage2;
    private TextView timer, hint, msg, msg2;
    private ProgressBar level;
    private Button bigBtn, playBtn, ringBtn, send;
    private EditText name;
    private Pickers.DateField dateField;
    private Pickers.TimeField timeField;
    private View timeView;

    private final Runnable tick = new Runnable() {
        @Override
        public void run() {
            WavRecorder r = recorder;
            if (recording && r != null) {
                timer.setText(mmss(r.seconds()));
                level.setProgress((int) Math.min(100, 100 * Math.sqrt(r.peak / 32768.0)));
                if (!r.isRunning()) {                       // it stopped by itself (5 minute limit, or a problem)
                    finishRecording();
                    return;
                }
                ui.postDelayed(this, 200);
            }
        }
    };

    private static String mmss(double s) {
        int t = (int) s;
        return String.format(Locale.US, "%d:%02d", t / 60, t % 60);
    }

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        wav = new File(getCacheDir(), "new_recording.wav");
        wav.delete();
        ScrollView sv = new ScrollView(this);
        sv.setBackgroundColor(Ui.BG);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        int p = Ui.dp(this, 12);
        root.setPadding(p, p, p, p);
        sv.addView(root);
        root.addView(Ui.label(this, "Record", 22, Ui.TEXT, true));

        // ---- stage 1: record
        stage1 = new LinearLayout(this);
        stage1.setOrientation(LinearLayout.VERTICAL);
        hint = Ui.label(this, "Tap the red button and speak. Keep the watch screen on while you record.", 13, Ui.MUTE, false);
        hint.setPadding(0, Ui.dp(this, 4), 0, Ui.dp(this, 8));
        stage1.addView(hint);
        timer = Ui.label(this, "0:00", 44, Ui.TEXT, true);
        timer.setGravity(Gravity.CENTER);
        stage1.addView(timer, new LinearLayout.LayoutParams(-1, -2));
        level = new ProgressBar(this, null, android.R.attr.progressBarStyleHorizontal);
        level.setMax(100);
        LinearLayout.LayoutParams ll = new LinearLayout.LayoutParams(-1, Ui.dp(this, 10));
        ll.setMargins(0, Ui.dp(this, 6), 0, Ui.dp(this, 10));
        stage1.addView(level, ll);
        bigBtn = Ui.button(this, "●  Start recording", Ui.RED, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if (recording) finishRecording();
                else beginRecording();
            }
        });
        bigBtn.setTextSize(18);
        stage1.addView(bigBtn, new LinearLayout.LayoutParams(-1, Ui.dp(this, 64)));
        msg = Ui.label(this, "", 14, Ui.RED, true);
        msg.setGravity(Gravity.CENTER);
        msg.setPadding(0, Ui.dp(this, 8), 0, 0);
        stage1.addView(msg);
        root.addView(stage1, new LinearLayout.LayoutParams(-1, -2));

        // ---- stage 2: name it, day / time, send
        stage2 = new LinearLayout(this);
        stage2.setOrientation(LinearLayout.VERTICAL);
        stage2.setVisibility(View.GONE);
        playBtn = Ui.button(this, "▶  Listen", Ui.BTN, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                togglePreview();
            }
        });
        stage2.addView(playBtn, new LinearLayout.LayoutParams(-1, -2));
        name = new EditText(this);
        name.setSingleLine(true);
        name.setTextSize(20);
        name.setTextColor(Ui.TEXT);
        name.setHintTextColor(Ui.MUTE);
        name.setHint("name of the recording");
        name.setBackground(Ui.round(Ui.CARD, 8, this));
        name.setPadding(Ui.dp(this, 10), Ui.dp(this, 8), Ui.dp(this, 10), Ui.dp(this, 8));
        name.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_CAP_SENTENCES);
        name.setImeOptions(EditorInfo.IME_ACTION_DONE);
        LinearLayout.LayoutParams nl = new LinearLayout.LayoutParams(-1, -2);
        nl.topMargin = Ui.dp(this, 10);
        stage2.addView(name, nl);
        TextView dl = Ui.label(this, "Which day?  (No date = it only plays when you tap it)", 12, Ui.MUTE, true);
        dl.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 2));
        stage2.addView(dl);
        timeField = new Pickers.TimeField(LocalTime.now().plusMinutes(10));
        dateField = new Pickers.DateField(null, new Runnable() {
            @Override
            public void run() {
                if (timeView != null) timeView.setVisibility(dateField.date == null ? View.GONE : View.VISIBLE);
            }
        });
        stage2.addView(dateField.build(this));
        TextView tl = Ui.label(this, "At what time?", 12, Ui.MUTE, true);
        tl.setPadding(0, Ui.dp(this, 10), 0, Ui.dp(this, 2));
        stage2.addView(tl);
        timeView = timeField.build(this);
        stage2.addView(timeView);
        timeView.setVisibility(View.GONE);
        ringBtn = Ui.button(this, "", Ui.BTN, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                ring = !ring;
                showRing();
            }
        });
        LinearLayout.LayoutParams rl = new LinearLayout.LayoutParams(-1, -2);
        rl.topMargin = Ui.dp(this, 10);
        stage2.addView(ringBtn, rl);
        showRing();
        msg2 = Ui.label(this, "", 14, Ui.RED, true);
        msg2.setGravity(Gravity.CENTER);
        msg2.setPadding(0, Ui.dp(this, 8), 0, Ui.dp(this, 4));
        stage2.addView(msg2);
        send = Ui.button(this, "Save on the Pi", Ui.GREEN, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                upload();
            }
        });
        stage2.addView(send, new LinearLayout.LayoutParams(-1, -2));
        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        Button again = Ui.button(this, "Record again", Ui.BTN, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                recordAgain();
            }
        });
        LinearLayout.LayoutParams h1 = new LinearLayout.LayoutParams(0, -2, 1);
        h1.setMargins(0, Ui.dp(this, 6), Ui.dp(this, 3), 0);
        row.addView(again, h1);
        Button throwAway = Ui.button(this, "Throw away", Ui.BTN, new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        LinearLayout.LayoutParams h2 = new LinearLayout.LayoutParams(0, -2, 1);
        h2.setMargins(Ui.dp(this, 3), Ui.dp(this, 6), 0, 0);
        row.addView(throwAway, h2);
        stage2.addView(row, new LinearLayout.LayoutParams(-1, -2));
        root.addView(stage2, new LinearLayout.LayoutParams(-1, -2));
        setContentView(sv);
        getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE | WindowManager.LayoutParams.SOFT_INPUT_STATE_HIDDEN);
    }

    private void showRing() {
        ringBtn.setText(ring ? "Alarm beep before it plays: ON" : "Alarm beep before it plays: OFF");
        ringBtn.setBackground(Ui.round(ring ? Ui.ACCENT : Ui.BTN, 10, this));
    }

    // ------------------------------------------------------------------ recording
    private void beginRecording() {
        if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO}, ASK_MIC);      // the watch asks "Allow Routine to record audio?"
            return;
        }
        msg.setText("");
        try {
            recorder = new WavRecorder(wav);
            recorder.start();
        } catch (Exception e) {
            recorder = null;
            msg.setText(e.getMessage() == null ? "Could not start the microphone." : e.getMessage());
            return;
        }
        recording = true;
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        hint.setText("Recording ... speak now. Tap the button to stop. (Longest: 5 minutes.)");
        bigBtn.setText("■  Stop");
        timer.setTextColor(Ui.RED);
        ui.post(tick);
    }

    @Override
    public void onRequestPermissionsResult(int code, String[] perms, int[] results) {
        super.onRequestPermissionsResult(code, perms, results);
        if (code != ASK_MIC) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) {
            beginRecording();
        } else {
            msg.setText("The microphone is not allowed. On the watch: Settings > Apps > Routine > Permissions > Microphone.");
        }
    }

    private void finishRecording() {
        if (!recording) return;
        recording = false;
        ui.removeCallbacks(tick);
        getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        WavRecorder r = recorder;
        if (r != null) r.stop();
        if (r == null || r.bytes < WavRecorder.RATE) {      // less than half a second
            bigBtn.setText("●  Start recording");
            timer.setTextColor(Ui.TEXT);
            hint.setText("Tap the red button and speak. Keep the watch screen on while you record.");
            msg.setText(r != null && r.failed != null ? r.failed : "That was too short. Try again.");
            return;
        }
        reviewing = true;
        stage1.setVisibility(View.GONE);
        stage2.setVisibility(View.VISIBLE);
        name.setText("Recording " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM h:mm a", Locale.US)));
        String note = mmss(r.seconds()) + " recorded.";
        if (r.capped) note += " (That is the 5 minute limit.)";
        if (r.failed != null) note += " " + r.failed;
        if (r.loudest < 300) note += " I could hear almost nothing - check the microphone.";
        msg2.setTextColor(r.loudest < 300 || r.failed != null ? Ui.AMBER : Ui.MUTE);
        msg2.setText(note);
    }

    private void recordAgain() {
        if (busy) return;
        stopPreview();
        reviewing = false;
        wav.delete();
        stage2.setVisibility(View.GONE);
        stage1.setVisibility(View.VISIBLE);
        timer.setText("0:00");
        timer.setTextColor(Ui.TEXT);
        level.setProgress(0);
        bigBtn.setText("●  Start recording");
        hint.setText("Tap the red button and speak. Keep the watch screen on while you record.");
        msg.setText("");
    }

    // ------------------------------------------------------------------ listen to it before sending
    private void togglePreview() {
        if (player != null) {
            stopPreview();
            return;
        }
        try {
            MediaPlayer m = new MediaPlayer();
            player = m;
            m.setAudioStreamType(AudioManager.STREAM_MUSIC);
            m.setDataSource(wav.getPath());
            m.setOnCompletionListener(new MediaPlayer.OnCompletionListener() {
                @Override
                public void onCompletion(MediaPlayer mp) {
                    stopPreview();
                }
            });
            m.prepare();
            m.start();
            playBtn.setText("■  Stop");
        } catch (Exception e) {
            stopPreview();
            Toast.makeText(this, "The watch could not play it.", Toast.LENGTH_SHORT).show();
        }
    }

    private void stopPreview() {
        MediaPlayer m = player;
        player = null;
        if (m != null) {
            try {
                m.stop();
            } catch (Exception ignored) {
            }
            m.release();
        }
        if (playBtn != null) playBtn.setText("▶  Listen");
    }

    // ------------------------------------------------------------------ send to the Pi
    private void upload() {
        if (busy) return;
        if (!Store.isConfigured(this)) {
            msg2.setTextColor(Ui.RED);
            msg2.setText("Not connected to the Pi. Use Setup first.");
            return;
        }
        stopPreview();
        final String title = name.getText().toString().trim();
        final String date = dateField.date == null ? null : dateField.date.toString();
        final String time = dateField.date == null ? null : Edits.hm(timeField.time);
        busy = true;
        send.setText("Sending ...");
        msg2.setTextColor(Ui.MUTE);
        msg2.setText("Sending " + (wav.length() / 1024) + " KB to the Pi ...");
        final Context app = getApplicationContext();
        final File f = wav;
        final boolean rg = ring;
        new Thread(new Runnable() {
            @Override
            public void run() {
                String err = null;
                try {
                    PiClient pc = Store.client(app);
                    pc.uploadRecording(f, title, date, time, rg);
                    Sync.once(app, pc, 0);
                } catch (PiClient.RejectedException e) {
                    err = e.getMessage();
                } catch (PiClient.AuthException e) {
                    err = "Wrong code. Use Setup.";
                } catch (Exception e) {
                    err = "The Pi is not reachable. Your recording is kept here - try again on the home Wi-Fi.";
                }
                final String e2 = err;
                ui.post(new Runnable() {
                    @Override
                    public void run() {
                        busy = false;
                        if (isFinishing()) return;
                        send.setText("Save on the Pi");
                        if (e2 != null) {
                            msg2.setTextColor(Ui.RED);
                            msg2.setText(e2);
                        } else {
                            Toast.makeText(RecordActivity.this, "Saved on the Pi", Toast.LENGTH_SHORT).show();
                            setResult(RESULT_OK);
                            finish();
                        }
                    }
                });
            }
        }, "upload").start();
    }

    // ------------------------------------------------------------------ leaving
    @Override
    protected void onPause() {
        super.onPause();
        if (recording) finishRecording();                     // screen off / another app: keep what we have
        stopPreview();
    }

    @Override
    public void onBackPressed() {
        if (recording) {
            finishRecording();
        } else if (reviewing && !busy && !backOnce) {
            backOnce = true;
            Toast.makeText(this, "Press Back again to throw this recording away", Toast.LENGTH_SHORT).show();
            ui.postDelayed(new Runnable() {
                @Override
                public void run() {
                    backOnce = false;
                }
            }, 3000);
        } else {
            super.onBackPressed();
        }
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        ui.removeCallbacksAndMessages(null);
        WavRecorder r = recorder;
        if (r != null && r.isRunning()) r.stop();
        stopPreview();
        if (isFinishing() && !busy) wav.delete();
    }
}
