package com.routine.watch;

import android.content.Context;
import android.content.SharedPreferences;

/** What the watch keeps on itself: the Pi's address + code, and the schedule copy. */
public final class Store {
    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("routine", Context.MODE_PRIVATE);
    }

    public static synchronized Data load(Context c) {
        String s = sp(c).getString("data", null);
        return s == null ? new Data() : Data.fromJson(s);
    }

    public static synchronized void save(Context c, Data d) {
        sp(c).edit().putString("data", d.toJson()).apply();
    }

    public static synchronized void clearData(Context c) {
        sp(c).edit().remove("data").apply();
    }

    public static boolean isConfigured(Context c) {
        return !host(c).isEmpty() && !code(c).isEmpty();
    }

    public static String host(Context c) {
        return sp(c).getString("host", "");
    }

    public static int port(Context c) {
        return sp(c).getInt("port", PiClient.DEFAULT_PORT);
    }

    public static String code(Context c) {
        return sp(c).getString("code", "");
    }

    public static void setPi(Context c, String host, int port, String code) {
        sp(c).edit().putString("host", host).putInt("port", port).putString("code", code).apply();
    }

    public static PiClient client(Context c) {
        return new PiClient(host(c), port(c), code(c));
    }
}
