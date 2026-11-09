package com.routine.watch;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** After the watch restarts, the app is updated, or the clock/time zone changes: set the alarms again. */
public class BootReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context ctx, Intent intent) {
        Alarms.scheduleNext(ctx);
        Alarms.scheduleSync(ctx);
    }
}
