package com.routine.watch;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.io.IOException;
import java.util.TreeMap;

/**
 * The sync rules.
 *  - First time (version 0): the Pi sends everything ("full").
 *  - After that the watch says which version it has; the Pi sends only the items that changed and the ids that were deleted.
 *  - After every answer the watch checks "do I now hold exactly the items the Pi says it has?" (count + fingerprint).
 *    If not, it throws the answer away and asks for a full copy. So the watch can never stay wrong.
 */
public final class SyncEngine {
    public interface Net {
        JSONObject sync(int since, String epoch, int n, String digest, int wait) throws IOException, JSONException;
    }

    public static final class Result {
        public boolean full;        // the Pi sent everything
        public int changed;         // items added or changed
        public int deleted;         // items removed
        public boolean healed;      // the first answer did not add up, so a full copy was taken
        public boolean ok;

        public boolean anyChange() {
            return full || changed > 0 || deleted > 0;
        }
    }

    /** Ask the Pi for news (wait = seconds the Pi may hold the answer until something changes) and apply it to d. */
    public static Result sync(Data d, Net net, int wait) throws IOException, JSONException {
        Result r = apply(d, net.sync(d.rev, d.epoch, d.items.size(), d.digest(), wait));
        if (!r.ok) {                                         // did not add up: start over with a full copy
            r = apply(d, net.sync(0, "", 0, "", 0));
            r.healed = true;
            if (!r.ok) throw new IOException("The Pi's answer did not add up");
        }
        return r;
    }

    static Result apply(Data d, JSONObject a) throws JSONException {
        Result r = new Result();
        r.full = a.getBoolean("full");
        TreeMap<String, Item> next = r.full ? new TreeMap<String, Item>() : new TreeMap<>(d.items);
        JSONArray gone = a.getJSONArray("deleted");
        for (int k = 0; k < gone.length(); k++) {            // deletions first, then additions (so "deleted and added again" survives)
            if (next.remove(gone.getString(k)) != null) r.deleted++;
        }
        JSONArray items = a.getJSONArray("items");
        for (int k = 0; k < items.length(); k++) {
            Item i = Item.fromJson(items.getJSONObject(k));
            Item old = next.put(i.id, i);
            if (old == null || !old.hash.equals(i.hash)) r.changed++;
        }
        r.ok = next.size() == a.getInt("total") && Data.digestOf(next).equals(a.getString("digest"));
        if (r.ok) {                                          // only now is the watch's copy replaced
            d.items = next;
            d.epoch = a.getString("epoch");
            d.rev = a.getInt("rev");
        }
        return r;
    }
}
