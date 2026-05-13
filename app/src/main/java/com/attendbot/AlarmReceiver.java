package com.attendbot;

import android.content.*;
import android.os.Build;

public class AlarmReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        String action = intent.getAction();
        if (action == null) return;

        if (action.equals(Intent.ACTION_BOOT_COMPLETED)) {
            SharedPreferences prefs = context.getSharedPreferences("AttendBot", Context.MODE_PRIVATE);
            if (prefs.getBoolean("alarmEnabled", false)) {
                ScheduleHelper.scheduleAlarms(context);
            }
            return;
        }

        String mode = action.equals("com.attendbot.CHECKOUT_ALARM") ? "checkout" : "checkin";

        Intent si = new Intent(context, AttendanceService.class);
        si.putExtra("mode", mode);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            context.startForegroundService(si);
        } else {
            context.startService(si);
        }
    }
}
