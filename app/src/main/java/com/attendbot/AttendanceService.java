package com.attendbot;

import android.app.*;
import android.content.*;
import android.content.pm.ServiceInfo;
import android.net.*;
import android.net.wifi.WifiManager;
import android.os.*;
import android.provider.Settings;
import android.util.Log;
import androidx.core.app.NotificationCompat;
import java.text.SimpleDateFormat;
import java.util.*;

public class AttendanceService extends Service {
    static final String TAG = "AttendBot";
    static final String CHANNEL = "attendbot_ch";
    Handler handler;
    SharedPreferences prefs;
    String mode = "checkin";
    int retryCount = 0;
    static final int MAX_RETRY = 3;

    @Override public void onCreate() {
        super.onCreate();
        prefs = getSharedPreferences("AttendBot", MODE_PRIVATE);
        handler = new Handler(Looper.getMainLooper());
        createChannel();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) mode = intent.getStringExtra("mode") != null ? intent.getStringExtra("mode") : "checkin";
        retryCount = 0;

        String label = mode.equals("checkin") ? "CHECK IN" : "CHECK OUT";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(1, buildNotif("AttendBot چل رہا ہے", label + " کی تیاری..."),
                ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC);
        } else {
            startForeground(1, buildNotif("AttendBot چل رہا ہے", label + " کی تیاری..."));
        }

        handler.postDelayed(this::step1_Network, 500);
        return START_NOT_STICKY;
    }

    // ── Step 1: Fix network ─────────────────────────────────────────────────
    void step1_Network() {
        updateNotif("نیٹ ورک چیک ہو رہا ہے...");

        // First: disable flight mode if on
        if (isFlightModeOn()) {
            updateNotif("Flight mode بند ہو رہا ہے...");
            disableFlightMode();
            handler.postDelayed(this::step1b_EnableData, 3000);
            return;
        }
        step1b_EnableData();
    }

    void step1b_EnableData() {
        if (!isInternetAvailable()) {
            updateNotif("انٹرنیٹ آن ہو رہا ہے...");
            enableWifi();
            // Wait up to 10s for internet
            waitForInternet(0);
        } else {
            step2_Launch();
        }
    }

    void waitForInternet(int attempts) {
        if (attempts > 10) {
            // No internet after 10s — still try
            step2_Launch();
            return;
        }
        if (isInternetAvailable()) {
            step2_Launch();
        } else {
            handler.postDelayed(() -> waitForInternet(attempts + 1), 1000);
        }
    }

    // ── Step 2: Launch WebView activity ────────────────────────────────────
    void step2_Launch() {
        updateNotif(mode.equals("checkin") ? "CHECK IN ہو رہا ہے..." : "CHECK OUT ہو رہا ہے...");

        boolean hasMacro = !prefs.getString(
            mode.equals("checkin") ? "macro_checkin" : "macro_checkout", "").isEmpty();

        Intent intent = new Intent(this, AttendanceWebActivity.class);
        intent.putExtra("mode", mode);
        intent.putExtra("hasMacro", hasMacro);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TOP);
        startActivity(intent);

        // Service done — activity handles the rest
        handler.postDelayed(this::stopSelf, 2000);
    }

    // ── Network helpers ─────────────────────────────────────────────────────
    boolean isFlightModeOn() {
        return Settings.Global.getInt(getContentResolver(),
            Settings.Global.AIRPLANE_MODE_ON, 0) != 0;
    }

    void disableFlightMode() {
        try {
            Settings.Global.putInt(getContentResolver(),
                Settings.Global.AIRPLANE_MODE_ON, 0);
            Intent i = new Intent(Intent.ACTION_AIRPLANE_MODE_CHANGED);
            i.putExtra("state", false);
            sendBroadcast(i);
        } catch (Exception e) {
            Log.e(TAG, "Flight mode: " + e.getMessage());
            // Android 6+ needs WRITE_SECURE_SETTINGS — show settings panel
            Intent panel = new Intent(Settings.ACTION_AIRPLANE_MODE_SETTINGS);
            panel.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
            startActivity(panel);
        }
    }

    void enableWifi() {
        try {
            WifiManager wm = (WifiManager) getApplicationContext().getSystemService(WIFI_SERVICE);
            if (wm != null && !wm.isWifiEnabled()) {
                if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
                    wm.setWifiEnabled(true);
                } else {
                    // Android 10+ — open panel
                    Intent panel = new Intent(Settings.Panel.ACTION_WIFI);
                    panel.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                    startActivity(panel);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "WiFi: " + e.getMessage());
        }
    }

    boolean isInternetAvailable() {
        ConnectivityManager cm = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        if (cm == null) return false;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            Network net = cm.getActiveNetwork();
            if (net == null) return false;
            NetworkCapabilities nc = cm.getNetworkCapabilities(net);
            return nc != null && (nc.hasTransport(NetworkCapabilities.TRANSPORT_WIFI)
                || nc.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR)
                || nc.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET));
        } else {
            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();
        }
    }

    // ── Notifications ───────────────────────────────────────────────────────
    void createChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CHANNEL, "AttendBot", NotificationManager.IMPORTANCE_LOW);
            getSystemService(NotificationManager.class).createNotificationChannel(ch);
        }
    }

    Notification buildNotif(String title, String text) {
        return new NotificationCompat.Builder(this, CHANNEL)
            .setContentTitle(title).setContentText(text)
            .setSmallIcon(android.R.drawable.ic_menu_today)
            .setPriority(NotificationCompat.PRIORITY_LOW).build();
    }

    void updateNotif(String text) {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        nm.notify(1, buildNotif("AttendBot", text));
    }

    @Override public IBinder onBind(Intent i) { return null; }
}
