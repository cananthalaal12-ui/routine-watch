package com.routine.watch;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;

/** What is due when - for the screen and for the alarms. Pure logic, no Android. */
public final class Schedule {
    public static final class Row {
        public final Item item;
        public final LocalDateTime when;
        public final String status;       // "done" | "now" | "next" | "later"

        Row(Item item, LocalDateTime when, String status) {
            this.item = item;
            this.when = when;
            this.status = status;
        }
    }

    private static LocalDate recDate(Item it) {
        try {
            return LocalDate.parse(it.date);
        } catch (RuntimeException e) {
            return null;
        }
    }

    /** The next moment (strictly after now) this item is due, or null. Routine items repeat every day. */
    public static LocalDateTime nextOccurrence(Item it, LocalDateTime now) {
        LocalTime t = it.localTime();
        if (it.isRecording()) {
            LocalDate d = recDate(it);
            if (d == null || it.played) return null;
            LocalDateTime w = LocalDateTime.of(d, t);
            return w.isAfter(now) ? w : null;
        }
        LocalDateTime w = LocalDateTime.of(now.toLocalDate(), t);
        return w.isAfter(now) ? w : w.plusDays(1);
    }

    public static LocalDateTime nextAlarm(Collection<Item> items, LocalDateTime now) {
        LocalDateTime best = null;
        for (Item it : items) {
            LocalDateTime w = nextOccurrence(it, now);
            if (w != null && (best == null || w.isBefore(best))) best = w;
        }
        return best;
    }

    /** Everything due at that minute. */
    public static List<Item> dueAt(Collection<Item> items, LocalDateTime moment) {
        LocalDateTime m = moment.truncatedTo(ChronoUnit.MINUTES);
        List<Item> out = new ArrayList<>();
        for (Item it : items) {
            if (it.isRecording()) {
                LocalDate d = recDate(it);
                if (d != null && !it.played && LocalDateTime.of(d, it.localTime()).equals(m)) out.add(it);
            } else if (it.localTime().equals(m.toLocalTime())) {
                out.add(it);
            }
        }
        return out;
    }

    private static final Comparator<Row> BY_TIME = new Comparator<Row>() {
        @Override
        public int compare(Row a, Row b) {
            int c = a.when.compareTo(b.when);
            return c != 0 ? c : a.item.title.compareToIgnoreCase(b.item.title);
        }
    };

    /** Today's list: every routine item and every recording dated today, with DONE / NOW / NEXT. */
    public static List<Row> today(Collection<Item> items, LocalDateTime now) {
        LocalDate today = now.toLocalDate();
        LocalDateTime nowMin = now.truncatedTo(ChronoUnit.MINUTES);
        List<Row> rows = new ArrayList<>();
        for (Item it : items) {
            LocalDateTime w;
            if (it.isRecording()) {
                if (!today.equals(recDate(it))) continue;
                w = LocalDateTime.of(today, it.localTime());
            } else {
                w = LocalDateTime.of(today, it.localTime());
            }
            String st = w.equals(nowMin) ? "now" : (w.isBefore(nowMin) || it.played) ? "done" : "next";
            rows.add(new Row(it, w, st));
        }
        Collections.sort(rows, BY_TIME);
        return rows;
    }

    /** Dated recordings after today. */
    public static List<Row> later(Collection<Item> items, LocalDateTime now) {
        List<Row> rows = new ArrayList<>();
        for (Item it : items) {
            LocalDate d = it.isRecording() ? recDate(it) : null;
            if (d != null && d.isAfter(now.toLocalDate()) && !it.played) {
                rows.add(new Row(it, LocalDateTime.of(d, it.localTime()), "later"));
            }
        }
        Collections.sort(rows, BY_TIME);
        return rows;
    }

    /** Every recording (dated first, by date and time; then the ones without a date, oldest first). */
    public static List<Item> recordings(Collection<Item> items) {
        List<Item> out = new ArrayList<>();
        for (Item it : items) if (it.isRecording()) out.add(it);
        Collections.sort(out, new Comparator<Item>() {
            @Override
            public int compare(Item a, Item b) {
                boolean da = recDate(a) != null, db = recDate(b) != null;
                if (da != db) return da ? -1 : 1;
                if (da) {
                    int c = LocalDateTime.of(recDate(a), a.localTime()).compareTo(LocalDateTime.of(recDate(b), b.localTime()));
                    if (c != 0) return c;
                } else {
                    int c = a.created.compareTo(b.created);
                    if (c != 0) return c;
                }
                return a.title.compareToIgnoreCase(b.title);
            }
        });
        return out;
    }

    /** "Today", "Tomorrow", "Mon 9 Nov". */
    public static String fmtDay(LocalDate d, LocalDate today) {
        if (d.equals(today)) return "Today";
        if (d.equals(today.plusDays(1))) return "Tomorrow";
        return d.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH) + " " + d.getDayOfMonth() + " "
                + d.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH);
    }

    /** One line for a recording: "Tomorrow - 5:30 PM" or "No date". */
    public static String recWhen(Item it, LocalDate today) {
        LocalDate d = recDate(it);
        return d == null ? "No date" : fmtDay(d, today) + " - " + fmt12(it.localTime());
    }

    /** The one item that comes next (tomorrow's first routine item if today is finished). */
    public static Row next(Collection<Item> items, LocalDateTime now) {
        Row best = null;
        for (Item it : items) {
            LocalDateTime w = nextOccurrence(it, now);
            if (w != null && (best == null || w.isBefore(best.when))) best = new Row(it, w, "next");
        }
        return best;
    }

    public static String fmt12(LocalTime t) {
        int h = t.getHour() % 12 == 0 ? 12 : t.getHour() % 12;
        return String.format(Locale.US, "%d:%02d %s", h, t.getMinute(), t.getHour() < 12 ? "AM" : "PM");
    }

    /** "in 25 min", "in 2 h 5 min", "tomorrow 7:00 AM", "Mon 9 Nov 5:30 PM". */
    public static String when(LocalDateTime w, LocalDateTime now) {
        long mins = ChronoUnit.MINUTES.between(now.truncatedTo(ChronoUnit.MINUTES), w);
        if (mins < 1) return "now";
        if (mins < 60) return "in " + mins + " min";
        if (w.toLocalDate().equals(now.toLocalDate())) {
            return "in " + (mins / 60) + " h" + (mins % 60 == 0 ? "" : " " + (mins % 60) + " min");
        }
        String day = w.toLocalDate().equals(now.toLocalDate().plusDays(1)) ? "tomorrow"
                : w.getDayOfWeek().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH) + " " + w.getDayOfMonth()
                + " " + w.getMonth().getDisplayName(java.time.format.TextStyle.SHORT, Locale.ENGLISH);
        return day + " " + fmt12(w.toLocalTime());
    }
}
