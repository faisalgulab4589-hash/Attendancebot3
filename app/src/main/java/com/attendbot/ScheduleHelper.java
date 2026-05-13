package com.attendbot;

import android.app.AlarmManager;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Build;
import java.util.Calendar;

public class ScheduleHelper {

    public static void scheduleAlarms(Context ctx) {
        SharedPreferences prefs = ctx.getSharedPreferences("AttendBot", Context.MODE_PRIVATE);

        int inHour   = prefs.getInt("inHour", 8);
        int inMin    = prefs.getInt("inMin", 30);
        boolean inPm = prefs.getBoolean("inPm", false);
        int outHour  = prefs.getInt("outHour", 5);
        int outMin   = prefs.getInt("outMin", 0);
        boolean outPm= prefs.getBoolean("outPm", true);

        // Convert to 24h
        int inH24  = to24(inHour, inPm);
        int outH24 = to24(outHour, outPm);

        schedule(ctx, inH24,  inMin,  "com.attendbot.CHECKIN_ALARM",  100);
        schedule(ctx, outH24, outMin, "com.attendbot.CHECKOUT_ALARM", 101);
    }

    public static void cancelAlarms(Context ctx) {
        cancel(ctx, "com.attendbot.CHECKIN_ALARM",  100);
        cancel(ctx, "com.attendbot.CHECKOUT_ALARM", 101);
    }

    private static int to24(int hour, boolean pm) {
        if (!pm) {
            return (hour == 12) ? 0 : hour;
        } else {
            return (hour == 12) ? 12 : hour + 12;
        }
    }

    private static void schedule(Context ctx, int hour, int min, String action, int reqCode) {
        Calendar cal = Calendar.getInstance();
        cal.set(Calendar.HOUR_OF_DAY, hour);
        cal.set(Calendar.MINUTE, min);
        cal.set(Calendar.SECOND, 0);
        cal.set(Calendar.MILLISECOND, 0);
        if (cal.getTimeInMillis() <= System.currentTimeMillis()) {
            cal.add(Calendar.DAY_OF_YEAR, 1);
        }

        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(ctx, AlarmReceiver.class);
        intent.setAction(action);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, reqCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            try {
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
            } catch (SecurityException e) {
                am.set(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
            }
        } else {
            am.setExact(AlarmManager.RTC_WAKEUP, cal.getTimeInMillis(), pi);
        }
    }

    private static void cancel(Context ctx, String action, int reqCode) {
        AlarmManager am = (AlarmManager) ctx.getSystemService(Context.ALARM_SERVICE);
        Intent intent = new Intent(ctx, AlarmReceiver.class);
        intent.setAction(action);
        PendingIntent pi = PendingIntent.getBroadcast(ctx, reqCode, intent,
            PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);
        am.cancel(pi);
    }
}
