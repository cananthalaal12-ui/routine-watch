package com.routine.watch;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalTime;

/** One thing on the schedule: a daily routine item ("r") or a dated recording ("c"). Same fields the Pi sends. */
public final class Item {
    public final String id, kind, title, time, date, hash, created;
    public final boolean ring, played;

    public Item(String id, String kind, String title, String time, String date, String hash, boolean ring, boolean played,
                String created) {
        this.id = id;
        this.kind = kind;
        this.title = title;
        this.time = time;
        this.date = date;
        this.hash = hash;
        this.ring = ring;
        this.played = played;
        this.created = created;
    }

    public static Item fromJson(JSONObject o) throws JSONException {
        return new Item(o.getString("id"), o.optString("k", "r"), o.optString("t", ""), o.optString("tm", "00:00"),
                o.optString("d", ""), o.optString("h", ""), o.optBoolean("ring", false), o.optBoolean("p", false),
                o.optString("cr", ""));
    }

    public JSONObject toJson() throws JSONException {
        JSONObject o = new JSONObject();
        o.put("id", id).put("k", kind).put("t", title).put("tm", time).put("d", date).put("h", hash)
                .put("ring", ring).put("p", played).put("cr", created);
        return o;
    }

    public boolean isRecording() {
        return "c".equals(kind);
    }

    public LocalTime localTime() {
        try {
            return LocalTime.parse(time);
        } catch (RuntimeException e) {
            return LocalTime.MIDNIGHT;
        }
    }
}
