package com.routine.watch;

import org.json.JSONException;
import org.json.JSONObject;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.TreeMap;

/** Everything the watch remembers about the Pi's schedule: the version it has, and the items. */
public final class Data {
    public String epoch = "";                       // which "life" of the Pi's data this belongs to
    public int rev = 0;                             // the version of the Pi's schedule the watch has
    public TreeMap<String, Item> items = new TreeMap<>();
    public long lastOkMs = 0;                       // when the watch last heard from the Pi

    /** Short fingerprint of all items. The Pi computes the same; if they differ the watch asks for a full copy. */
    public static String digestOf(Map<String, Item> m) {
        StringBuilder sb = new StringBuilder();
        for (Map.Entry<String, Item> e : new TreeMap<>(m).entrySet()) {
            if (sb.length() > 0) sb.append('\n');
            sb.append(e.getKey()).append(':').append(e.getValue().hash);
        }
        try {
            byte[] h = MessageDigest.getInstance("SHA-1").digest(sb.toString().getBytes(StandardCharsets.UTF_8));
            StringBuilder hex = new StringBuilder();
            for (byte b : h) hex.append(String.format("%02x", b & 0xff));
            return hex.toString();
        } catch (java.security.NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String digest() {
        return digestOf(items);
    }

    public String toJson() {
        try {
            JSONObject o = new JSONObject();
            o.put("epoch", epoch).put("rev", rev).put("lastOk", lastOkMs);
            org.json.JSONArray a = new org.json.JSONArray();
            for (Item i : items.values()) a.put(i.toJson());
            o.put("items", a);
            return o.toString();
        } catch (JSONException e) {
            throw new IllegalStateException(e);
        }
    }

    public static Data fromJson(String s) {
        Data d = new Data();
        try {
            JSONObject o = new JSONObject(s);
            d.epoch = o.optString("epoch", "");
            d.rev = o.optInt("rev", 0);
            d.lastOkMs = o.optLong("lastOk", 0);
            org.json.JSONArray a = o.optJSONArray("items");
            if (a != null) {
                for (int k = 0; k < a.length(); k++) {
                    Item i = Item.fromJson(a.getJSONObject(k));
                    d.items.put(i.id, i);
                }
            }
        } catch (JSONException e) {
            return new Data();                     // damaged file: start fresh, the next sync fills it again
        }
        return d;
    }
}
