package com.routine.watch;

import org.json.JSONException;
import org.json.JSONObject;

import java.time.LocalDate;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/** The edit messages the watch sends to the Pi. */
public final class Edits {
    private static final DateTimeFormatter HM = DateTimeFormatter.ofPattern("HH:mm");

    public static String hm(LocalTime t) {
        return t.format(HM);
    }

    /** Add a routine item (old = null) or change one (old = its current time): new name and/or new time. */
    public static JSONObject routineSet(String oldTime, LocalTime time, String title) throws JSONException {
        return new JSONObject().put("op", "routine_set").put("old", oldTime == null ? "" : oldTime)
                .put("time", hm(time)).put("title", title.trim());
    }

    public static JSONObject routineDelete(String time) throws JSONException {
        return new JSONObject().put("op", "routine_delete").put("time", time);
    }

    /** Change a recording. date == null means "no date" (then time is empty too). */
    public static JSONObject recSet(String id, String title, LocalDate date, LocalTime time, boolean ring) throws JSONException {
        return new JSONObject().put("op", "rec_set").put("id", id).put("title", title.trim())
                .put("date", date == null ? "" : date.toString()).put("time", date == null || time == null ? "" : hm(time))
                .put("ring", ring);
    }

    public static JSONObject recDelete(String id) throws JSONException {
        return new JSONObject().put("op", "rec_delete").put("id", id);
    }
}
