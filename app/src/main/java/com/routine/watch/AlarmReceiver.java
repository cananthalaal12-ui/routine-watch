package com.routine.watch;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;

/** Rings when an item is due, and does the quiet background sync. */
public class AlarmReceiver extends BroadcastReceiver {
    static final String CHANNEL = "alerts";

    @Override
    public void onReceive(final Context ctx, Intent intent) {
        String action = intent.getAction();
        if (Alarms.ACTION_ALARM.equals(action)) {
            long when = intent.getLongExtra("when", 0);
            long late = System.currentTimeMillis() - when;
            if (when > 0 && late < 15 * 60 * 1000L) {                 // not for alarms missed while the watch was off
                LocalDateTime moment = LocalDateTime.ofInstant(Instant.ofEpochMilli(when), ZoneId.systemDefault());
                List<Item> due = Schedule.dueAt(Store.load(ctx).items.values(), moment);
                if (!due.isEmpty()) alert(ctx, due);
            }
            Alarms.scheduleNext(ctx);                                  // and set the one after it
        } else if (Alarms.ACTION_SYNC.equals(action)) {
            final PendingResult pr = goAsync();
            new Thread(new Runnable() {
                @Override
                public void run() {
                    try {
                        if (Store.isConfigured(ctx)) Sync.once(ctx, Store.client(ctx), 0);
                    } catch (Exception ignored) {
                        // Pi not reachable (watch away from home): the copy on the watch is used
                    } finally {
                        Alarms.scheduleSync(ctx);
                        pr.finish();
                    }
                }
            }, "bg-sync").start();
        }
    }

    private void alert(Context ctx, List<Item> due) {
        ArrayList<String> titles = new ArrayList<>();
        for (Item i : due) titles.add(i.title);
        Intent show = new Intent(ctx, AlertActivity.class).putStringArrayListExtra("titles", titles)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        PendingIntent pi = PendingIntent.getActivity(ctx, 4, show, PendingIntent.FLAG_UPDATE_CURRENT);
        NotificationManager nm = (NotificationManager) ctx.getSystemService(Context.NOTIFICATION_SERVICE);
        if (nm != null) {                                              // a notification too, in case the screen cannot be opened
            nm.createNotificationChannel(new NotificationChannel(CHANNEL, "Reminders", NotificationManager.IMPORTANCE_HIGH));
            Notification n = new Notification.Builder(ctx, CHANNEL)
                    .setSmallIcon(android.R.drawable.ic_lock_idle_alarm)
                    .setContentTitle("It is " + titles.get(0) + " time")
                    .setContentIntent(pi).setFullScreenIntent(pi, true).setAutoCancel(true).build();
            nm.notify(7, n);
        }
        try {
            ctx.startActivity(show);
        } catch (RuntimeException ignored) {
        }
    }
}
