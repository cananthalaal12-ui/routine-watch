package com.routine.watch;

import android.content.Context;

import org.json.JSONException;

import java.io.IOException;

/** One round of syncing on the watch: load what we have, ask the Pi, keep the answer, set the alarms. */
public final class Sync {
    public static SyncEngine.Result once(Context c, PiClient pi, int wait) throws IOException, JSONException {
        Context app = c.getApplicationContext();
        if (Pending.count(app) > 0) Pending.flush(app, pi);     // recordings made while the Pi was away go over first
        Data d = Store.load(app);
        SyncEngine.Result r = SyncEngine.sync(d, pi, wait);
        long now = System.currentTimeMillis();
        if (r.anyChange() || now - d.lastOkMs > 60000) {      // do not rewrite the file on every empty answer
            d.lastOkMs = now;
            Store.save(app, d);
        }
        if (r.anyChange()) Alarms.scheduleNext(app);           // new times -> new alarm
        return r;
    }
}
