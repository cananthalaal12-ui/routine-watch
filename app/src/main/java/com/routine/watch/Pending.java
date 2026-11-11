package com.routine.watch;

import android.content.Context;
import android.content.SharedPreferences;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;

/**
 * Recordings made on the watch that the Pi does not have yet. They are kept ON THE WATCH (sound file + details),
 * can be played, edited and deleted here, and are sent to the Pi by themselves the next time the Pi can be reached.
 */
final class Pending {
    static final class Entry {
        String lid, title, date = "", time = "", file = "", created = "", error = "";
        boolean ring = true;
    }

    /** What happened when we tried to send. */
    static final class Result {
        int sent = 0;                  // arrived on the Pi (and were removed from the watch's waiting list)
        int left = 0;                  // still waiting
        boolean unreachable = false;   // the Pi could not be reached
        boolean wrongCode = false;
        String refused = "";           // the Pi said no to one of them (in its own words)
    }

    private static final java.util.Map<String, Long> refusedAt = new java.util.HashMap<>();   // the Pi said no: do not nag it every few seconds
    private static final Set<String> sending = new HashSet<>();      // being sent right now: cannot be edited for a moment

    private static SharedPreferences sp(Context c) {
        return c.getApplicationContext().getSharedPreferences("pending", Context.MODE_PRIVATE);
    }

    static File dir(Context c) {
        File d = new File(c.getApplicationContext().getFilesDir(), "pending");
        if (!d.isDirectory()) d.mkdirs();
        return d;
    }

    static File file(Context c, String lid) {
        return new File(dir(c), lid + ".wav");
    }

    private static List<Entry> read(Context c) {
        List<Entry> out = new ArrayList<>();
        String s = sp(c).getString("list", "[]");
        try {
            JSONArray a = new JSONArray(s);
            for (int i = 0; i < a.length(); i++) {
                JSONObject o = a.getJSONObject(i);
                Entry e = new Entry();
                e.lid = o.getString("lid");
                e.title = o.optString("title", "");
                e.date = o.optString("date", "");
                e.time = o.optString("time", "");
                e.created = o.optString("created", "");
                e.error = o.optString("error", "");
                e.ring = o.optBoolean("ring", true);
                if (file(c, e.lid).isFile()) out.add(e);                // a missing sound file = nothing to send
            }
        } catch (JSONException ignored) {
        }
        return out;
    }

    private static void write(Context c, List<Entry> list) {
        JSONArray a = new JSONArray();
        try {
            for (Entry e : list) {
                a.put(new JSONObject().put("lid", e.lid).put("title", e.title).put("date", e.date).put("time", e.time)
                        .put("created", e.created).put("error", e.error).put("ring", e.ring));
            }
        } catch (JSONException ignored) {
        }
        sp(c).edit().putString("list", a.toString()).commit();
    }

    static synchronized List<Entry> list(Context c) {
        return read(c);
    }

    static synchronized int count(Context c) {
        return read(c).size();
    }

    static synchronized Entry get(Context c, String lid) {
        for (Entry e : read(c)) if (e.lid.equals(lid)) return e;
        return null;
    }

    /** Changes whenever the waiting list changes (so the screen knows when to redraw). */
    static synchronized String sig(Context c) {
        return sp(c).getString("list", "[]").hashCode() + "/" + sending.size();
    }

    static synchronized boolean isSending(String lid) {
        return sending.contains(lid);
    }

    private static String defaultName() {
        return "Recording " + LocalDateTime.now().format(DateTimeFormatter.ofPattern("d MMM h:mm a", Locale.US));
    }

    /** Keep a new recording on the watch. [wav] is moved into the watch's own folder. date == null = no date. */
    static synchronized Entry add(Context c, File wav, String title, String date, String time, boolean ring) throws IOException {
        Entry e = new Entry();
        e.lid = UUID.randomUUID().toString().replace("-", "").substring(0, 12);
        e.title = title == null || title.trim().isEmpty() ? defaultName() : title.trim();
        e.date = date == null ? "" : date;
        e.time = date == null || time == null ? "" : time;
        e.ring = ring;
        e.created = LocalDateTime.now().withNano(0).toString();
        File to = file(c, e.lid);
        if (!wav.renameTo(to)) {                                         // different storage area: copy instead
            FileInputStream in = new FileInputStream(wav);
            FileOutputStream out = new FileOutputStream(to);
            try {
                byte[] buf = new byte[16384];
                int n;
                while ((n = in.read(buf)) > 0) out.write(buf, 0, n);
            } finally {
                in.close();
                out.close();
            }
            wav.delete();
        }
        List<Entry> l = read(c);
        l.add(e);
        write(c, l);
        return e;
    }

    /** Change the details of a waiting recording. False if it is being sent right now (try again in a moment). */
    static synchronized boolean update(Context c, String lid, String title, String date, String time, boolean ring) {
        if (sending.contains(lid)) return false;
        List<Entry> l = read(c);
        for (Entry e : l) {
            if (e.lid.equals(lid)) {
                e.title = title == null || title.trim().isEmpty() ? defaultName() : title.trim();
                e.date = date == null ? "" : date;
                e.time = date == null || time == null ? "" : time;
                e.ring = ring;
                e.error = "";
                refusedAt.remove(lid);
                write(c, l);
                return true;
            }
        }
        return true;
    }

    static synchronized boolean delete(Context c, String lid) {
        if (sending.contains(lid)) return false;
        removeNow(c, lid);
        return true;
    }

    private static void removeNow(Context c, String lid) {
        List<Entry> l = read(c);
        List<Entry> keep = new ArrayList<>();
        for (Entry e : l) if (!e.lid.equals(lid)) keep.add(e);
        write(c, keep);
        file(c, lid).delete();
    }

    private static synchronized void setError(Context c, String lid, String msg) {
        List<Entry> l = read(c);
        for (Entry e : l) if (e.lid.equals(lid)) e.error = msg;
        write(c, l);
    }

    /** The waiting recordings as schedule items (id "p..."), so the list, "next" and the alarms treat them like the others. */
    static List<Item> asItems(Context c) {
        List<Item> out = new ArrayList<>();
        for (Entry e : list(c)) {
            out.add(new Item("p" + e.lid, "c", e.title, e.time.isEmpty() ? "00:00" : e.time, e.date, "", e.ring, false, e.created));
        }
        return out;
    }

    /** Send everything that is waiting. Stops at the first "cannot reach the Pi". Safe to call from several places at once. */
    static Result flush(Context c, PiClient pi) {
        Result r = new Result();
        for (Entry e : list(c)) {
            synchronized (Pending.class) {
                Long rt = refusedAt.get(e.lid);
                if (rt != null && !e.error.isEmpty() && System.currentTimeMillis() - rt < 10 * 60 * 1000L) continue;   // refused lately
                if (sending.contains(e.lid) || get(c, e.lid) == null) continue;        // someone else is sending it / it is gone
                sending.add(e.lid);
            }
            boolean done = false;
            try {
                pi.uploadRecording(file(c, e.lid), e.title, e.date.isEmpty() ? null : e.date, e.time, e.ring, e.lid);
                done = true;
            } catch (PiClient.RejectedException x) {
                r.refused = x.getMessage();
                synchronized (Pending.class) {
                    refusedAt.put(e.lid, System.currentTimeMillis());
                }
                setError(c, e.lid, x.getMessage());
            } catch (PiClient.AuthException x) {
                r.wrongCode = true;
            } catch (Exception x) {
                r.unreachable = true;
            } finally {
                synchronized (Pending.class) {
                    sending.remove(e.lid);
                    if (done) removeNow(c, e.lid);
                }
            }
            if (done) r.sent++;
            if (r.unreachable || r.wrongCode) break;
        }
        r.left = count(c);
        if (r.sent > 0) Alarms.scheduleNext(c);
        return r;
    }
}
