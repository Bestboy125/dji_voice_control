package com.dji.sdk.voice_control.internal.djidemo.utils;

import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;

import com.dji.sdk.voice_control.internal.controller.DJISampleApplication;

public class ClipboardUtil {

    private static ClipboardManager cm;

    public static void init() {
        if (cm == null) {
            Context context = DJISampleApplication.getContext();
            cm = (ClipboardManager) context.getSystemService(context.CLIPBOARD_SERVICE);
        }
    }

    public static boolean copy(String text) {
        if (cm != null) {
            ClipData data = ClipData.newPlainText("bot", text);
            cm.setPrimaryClip(data);
            return true;
        }
        return false;
    }
}
