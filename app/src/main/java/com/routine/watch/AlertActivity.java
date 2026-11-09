package com.routine.watch;

import android.app.Activity;
import android.app.NotificationManager;
import android.content.Context;
import android.graphics.Color;
import android.graphics.Typeface;
import android.media.AudioManager;
import android.media.ToneGenerator;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.os.VibrationEffect;
import android.os.Vibrator;
import android.speech.tts.TextToSpeech;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import java.util.ArrayList;
import java.util.Locale;

/** "It is lunch time": wakes the screen, vibrates, three beeps, then says it aloud (if the watch has a voice). */
public class AlertActivity extends Activity {
    private final Handler h = new Handler(Looper.getMainLooper());
    private ToneGenerator tone;
    private TextToSpeech tts;
    private String spoken = "";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_SHOW_WHEN_LOCKED | WindowManager.LayoutParams.FLAG_TURN_SCREEN_ON
                | WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON | WindowManager.LayoutParams.FLAG_DISMISS_KEYGUARD);
        ArrayList<String> titles = getIntent().getStringArrayListExtra("titles");
        if (titles == null || titles.isEmpty()) {
            finish();
            return;
        }
        NotificationManager nm = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) nm.cancel(7);

        float d = getResources().getDisplayMetrics().density;
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#0e1116"));
        root.setPadding((int) (16 * d), (int) (16 * d), (int) (16 * d), (int) (16 * d));
        TextView small = text("IT IS", 14, "#8b93a3", false);
        TextView big = text(titles.get(0), 34, "#3ddc84", true);
        TextView time = text("time", 20, "#eef1f6", false);
        root.addView(small);
        root.addView(big);
        root.addView(time);
        for (int i = 1; i < titles.size(); i++) root.addView(text("and  " + titles.get(i), 18, "#eef1f6", false));
        Button ok = new Button(this);
        ok.setText("OK");
        ok.setTextSize(20);
        ok.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                finish();
            }
        });
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams((int) (160 * d), (int) (56 * d));
        lp.topMargin = (int) (16 * d);
        root.addView(ok, lp);
        setContentView(root);

        spoken = "It is " + titles.get(0) + " time";
        Vibrator v = (Vibrator) getSystemService(Context.VIBRATOR_SERVICE);
        if (v != null) v.vibrate(VibrationEffect.createWaveform(new long[]{0, 300, 200, 300, 200, 300}, -1));
        try {
            tone = new ToneGenerator(AudioManager.STREAM_ALARM, 100);
            for (int k = 0; k < 3; k++) {
                h.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        if (tone != null) tone.startTone(ToneGenerator.TONE_PROP_BEEP, 220);
                    }
                }, k * 450L);
            }
        } catch (RuntimeException ignored) {
        }
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                speak();
            }
        }, 1600);
        h.postDelayed(new Runnable() {
            @Override
            public void run() {
                finish();                                              // closes by itself after a minute
            }
        }, 60000);
    }

    private TextView text(String s, int sp, String color, boolean bold) {
        TextView t = new TextView(this);
        t.setText(s);
        t.setTextSize(sp);
        t.setTextColor(Color.parseColor(color));
        t.setGravity(Gravity.CENTER);
        if (bold) t.setTypeface(Typeface.DEFAULT_BOLD);
        return t;
    }

    private void speak() {
        try {
            tts = new TextToSpeech(getApplicationContext(), new TextToSpeech.OnInitListener() {
                @Override
                public void onInit(int status) {
                    if (status == TextToSpeech.SUCCESS && tts != null) {
                        tts.setLanguage(Locale.getDefault());
                        tts.speak(spoken, TextToSpeech.QUEUE_FLUSH, null, "alert");
                    }
                }
            });
        } catch (RuntimeException ignored) {
        }
    }

    @Override
    protected void onDestroy() {
        h.removeCallbacksAndMessages(null);
        if (tone != null) tone.release();
        if (tts != null) {
            tts.stop();
            tts.shutdown();
        }
        super.onDestroy();
    }
}
