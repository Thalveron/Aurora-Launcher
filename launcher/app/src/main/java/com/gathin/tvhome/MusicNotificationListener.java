package com.gathin.tvhome;

import android.content.Intent;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;

/** Notification access enables media-session discovery. Only Chora triggers a refresh; no notification content is read or stored. */
public final class MusicNotificationListener extends NotificationListenerService {
    static final String CHANGED="com.gathin.tvhome.MUSIC_SESSION_CHANGED";
    private void changed(){sendBroadcast(new Intent(CHANGED).setPackage(getPackageName()));}
    @Override public void onListenerConnected(){changed();}
    @Override public void onNotificationPosted(StatusBarNotification n){if(("com.craftworks.music".equals(n.getPackageName())||"com.craftworks.music.tvcustom".equals(n.getPackageName())))changed();}
    @Override public void onNotificationRemoved(StatusBarNotification n){if(("com.craftworks.music".equals(n.getPackageName())||"com.craftworks.music.tvcustom".equals(n.getPackageName())))changed();}
}
