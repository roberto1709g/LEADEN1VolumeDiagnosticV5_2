package com.leaden1.volumediagnostic;

import android.service.notification.NotificationListenerService;

public class MediaNotificationListener extends NotificationListenerService {
    private static MediaNotificationListener instance;

    @Override public void onListenerConnected() {
        super.onListenerConnected();
        instance = this;
    }

    @Override public void onListenerDisconnected() {
        instance = null;
        super.onListenerDisconnected();
    }

    public static boolean isConnected() { return instance != null; }
}
