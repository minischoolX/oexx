package org.edx.mobile.view.custom.cache.utils;

import android.util.Log;

public class LogUtils {

    private static final String TAG = "FastWebView";
    public static boolean DEBUG = true;

    public static void d(String message) {
        if (DEBUG) {
            Log.d(TAG, message);
        }
    }

    public static void e(String message) {
        if (DEBUG) {
            Log.e(TAG, message);
        }
    }
}
