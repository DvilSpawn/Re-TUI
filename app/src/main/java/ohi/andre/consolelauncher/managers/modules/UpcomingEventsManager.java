package ohi.andre.consolelauncher.managers.modules;

import android.Manifest;
import android.content.ContentUris;
import android.content.Context;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.provider.CalendarContract;
import android.text.TextUtils;

import androidx.core.content.ContextCompat;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.HashSet;
import java.util.Locale;
import java.util.Set;

import ohi.andre.consolelauncher.managers.settings.LauncherSettings;
import ohi.andre.consolelauncher.managers.xml.options.Behavior;

public final class UpcomingEventsManager {

    private static final int MAX_EVENTS = 20;
    public static final int MAX_LOOKAHEAD_DAYS = 366;

    private static final String[] PROJECTION = {
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.EVENT_LOCATION
    };

    private UpcomingEventsManager() {}

    public static boolean hasCalendarPermission(Context context) {
        return ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static String formatUpcoming(Context context) {
        int lookaheadDays = getLookaheadDays();
        String heading = formatHeading(lookaheadDays);
        if (!hasCalendarPermission(context)) {
            return heading + "\nCalendar access is required.\nRun: events -access";
        }

        long now = System.currentTimeMillis();
        long end = endOfLookaheadDay(now, lookaheadDays);

        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, now);
        ContentUris.appendId(builder, end);

        StringBuilder out = new StringBuilder(heading);
        Set<String> seen = new HashSet<>();
        int count = 0;

        try (Cursor cursor = context.getContentResolver().query(
                builder.build(),
                PROJECTION,
                null,
                null,
                CalendarContract.Instances.BEGIN + " ASC")) {
            if (cursor != null) {
                while (cursor.moveToNext() && count < MAX_EVENTS) {
                    String title = cursor.getString(0);
                    long begin = cursor.getLong(1);
                    boolean allDay = cursor.getInt(2) == 1;
                    String location = cursor.getString(3);
                    String safeTitle = TextUtils.isEmpty(title) ? "Untitled event" : title.trim();
                    String key = begin + "|" + allDay + "|" + safeTitle.toLowerCase(Locale.US);
                    if (!seen.add(key)) {
                        continue;
                    }

                    out.append('\n')
                            .append(formatWhen(begin, allDay))
                            .append(" - ")
                            .append(safeTitle);
                    if (!TextUtils.isEmpty(location)) {
                        out.append(" @ ").append(location);
                    }
                    count++;
                }
            }
        } catch (Exception e) {
            return heading + "\nUnable to read Android Calendar.";
        }

        if (count == 0) {
            out.append('\n').append(formatEmptyMessage(lookaheadDays));
        }
        return out.toString();
    }

    public static String formatModulePayload(Context context) {
        long now = System.currentTimeMillis();
        StringBuilder out = new StringBuilder();
        out.append("::title Events\n");
        for (String line : formatUpcoming(context).split("\\r?\\n", -1)) {
            out.append("::body ").append(line).append('\n');
        }
        out.append("::suggest refresh | command | module -refresh events\n");
        out.append("::suggest access | command | events -access\n");
        out.append("::suggest open | command | intent -view content://com.android.calendar/time/")
                .append(now)
                .append('\n');
        return out.toString().trim();
    }

    public static String formatUpcomingTsv(Context context) {
        if (!hasCalendarPermission(context)) {
            return "";
        }

        long now = System.currentTimeMillis();
        long end = endOfCurrentMonth(now);

        Uri.Builder builder = CalendarContract.Instances.CONTENT_URI.buildUpon();
        ContentUris.appendId(builder, now);
        ContentUris.appendId(builder, end);

        StringBuilder out = new StringBuilder();
        Set<String> seen = new HashSet<>();
        int count = 0;

        try (Cursor cursor = context.getContentResolver().query(
                builder.build(),
                PROJECTION,
                null,
                null,
                CalendarContract.Instances.BEGIN + " ASC")) {
            if (cursor != null) {
                while (cursor.moveToNext() && count < MAX_EVENTS) {
                    String title = cursor.getString(0);
                    long begin = cursor.getLong(1);
                    boolean allDay = cursor.getInt(2) == 1;
                    String location = cursor.getString(3);
                    String safeTitle = TextUtils.isEmpty(title) ? "Untitled event" : title.trim();
                    String key = begin + "|" + allDay + "|" + safeTitle.toLowerCase(Locale.US);
                    if (!seen.add(key)) {
                        continue;
                    }

                    if (out.length() > 0) {
                        out.append('\n');
                    }
                    out.append(formatDate(begin))
                            .append('\t')
                            .append(allDay ? "" : formatTime(begin))
                            .append('\t')
                            .append(safeTsv(safeTitle))
                            .append('\t')
                            .append(safeTsv(location));
                    count++;
                }
            }
        } catch (Exception ignored) {
            return "";
        }
        return out.toString();
    }

    public static int getLookaheadDays() {
        return sanitizeLookaheadDays(LauncherSettings.getInt(Behavior.events_lookahead_days));
    }

    public static int sanitizeLookaheadDays(int days) {
        if (days < 0) {
            return 0;
        }
        return Math.min(days, MAX_LOOKAHEAD_DAYS);
    }

    private static long endOfLookaheadDay(long now, int lookaheadDays) {
        Calendar end = Calendar.getInstance();
        end.setTimeInMillis(now);
        end.add(Calendar.DAY_OF_YEAR, sanitizeLookaheadDays(lookaheadDays));
        end.set(Calendar.HOUR_OF_DAY, 23);
        end.set(Calendar.MINUTE, 59);
        end.set(Calendar.SECOND, 59);
        end.set(Calendar.MILLISECOND, 999);
        return end.getTimeInMillis();
    }

    private static long endOfCurrentMonth(long now) {
        Calendar end = Calendar.getInstance();
        end.setTimeInMillis(now);
        end.set(Calendar.DAY_OF_MONTH, end.getActualMaximum(Calendar.DAY_OF_MONTH));
        end.set(Calendar.HOUR_OF_DAY, 23);
        end.set(Calendar.MINUTE, 59);
        end.set(Calendar.SECOND, 59);
        end.set(Calendar.MILLISECOND, 999);
        return end.getTimeInMillis();
    }

    private static String formatHeading(int lookaheadDays) {
        if (lookaheadDays <= 0) {
            return "[Upcoming events today]";
        }
        if (lookaheadDays == 1) {
            return "[Upcoming events today + 1 day]";
        }
        return "[Upcoming events today + " + lookaheadDays + " days]";
    }

    private static String formatEmptyMessage(int lookaheadDays) {
        if (lookaheadDays <= 0) {
            return "No upcoming events today.";
        }
        if (lookaheadDays == 1) {
            return "No upcoming events today or tomorrow.";
        }
        return "No upcoming events through today + " + lookaheadDays + " days.";
    }

    private static String formatWhen(long millis, boolean allDay) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        String pattern = allDay ? " MMM yyyy" : " MMM yyyy hh:mma";
        SimpleDateFormat suffix = new SimpleDateFormat(pattern, Locale.US);
        return day + ordinal(day) + suffix.format(calendar.getTime());
    }

    private static String formatDate(long millis) {
        Calendar calendar = Calendar.getInstance();
        calendar.setTimeInMillis(millis);
        int day = calendar.get(Calendar.DAY_OF_MONTH);
        SimpleDateFormat suffix = new SimpleDateFormat(" MMM yyyy", Locale.US);
        return day + ordinal(day) + suffix.format(calendar.getTime());
    }

    private static String formatTime(long millis) {
        return new SimpleDateFormat("hh:mma", Locale.US).format(millis);
    }

    private static String safeTsv(String value) {
        return value == null ? "" : value.replace('\t', ' ').replace('\n', ' ').trim();
    }

    private static String ordinal(int day) {
        if (day >= 11 && day <= 13) {
            return "th";
        }
        switch (day % 10) {
            case 1:
                return "st";
            case 2:
                return "nd";
            case 3:
                return "rd";
            default:
                return "th";
        }
    }
}
