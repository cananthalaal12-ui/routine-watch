package com.routine.watch;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;

import java.time.LocalDateTime;
import java.time.ZoneId;

/**
 * Alarms run from the copy on the watch, so the reminders still ring when the Pi is off or the watch is away from home.
 * Only the next alarm is set at a time; when it rings, the one after it is set.
 */
public final class Alarms {
    public static final String ACTION_ALARM = "com.routine.watch.ALARM";
    public static final String ACTION_SYNC = "com.routine.watch.SYNC";
    public static final long SYNC_EVERY_MS = 15 * 60 * 1000L;       // while the screen is off, look for news every 15 minutes

    private static PendingIntent pending(Context c, String action, long when) {
        Intent i = new Intent(c, AlarmReceiver.class).setAction(action).putExtra("when", when);
        return PendingIntent.getBroadcast(c, action.equals(ACTION_ALARM) ? 1 : 2, i, PendingIntent.FLAG_UPDATE_CURRENT);
    }

    /** Set the alarm for the next item on the schedule. */
    public static void scheduleNext(Context c) {
        Context app = c.getApplicationContext();
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (am == null) return;
        Data d = Store.load(app);
        java.util.List<Item> all = new java.util.ArrayList<>(d.items.values());
        all.addAll(Pending.asItems(app));                        // recordings still waiting for the Pi ring too
        LocalDateTime next = Schedule.nextAlarm(all, LocalDateTime.now());
        if (next == null) {
            am.cancel(pending(app, ACTION_ALARM, 0));
            return;
        }
        long ms = next.atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        PendingIntent show = PendingIntent.getActivity(app, 3, new Intent(app, MainActivity.class), PendingIntent.FLAG_UPDATE_CURRENT);
        // setAlarmClock is the one Android keeps exact even when the watch is dozing
        am.setAlarmClock(new AlarmManager.AlarmClockInfo(ms, show), pending(app, ACTION_ALARM, ms));
    }

    /** Set the next background "look for news". */
    public static void scheduleSync(Context c) {
        Context app = c.getApplicationContext();
        AlarmManager am = (AlarmManager) app.getSystemService(Context.ALARM_SERVICE);
        if (am == null || !Store.isConfigured(app)) return;
        long at = System.currentTimeMillis() + SYNC_EVERY_MS;
        am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, at, pending(app, ACTION_SYNC, at));
    }
}
