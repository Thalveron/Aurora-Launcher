package org.cleanyourtv.homebridge;
import android.content.*;
import java.io.File;

/** Compatibility receiver: old launchers may send previews; never save them. */
public final class PreviewReceiver extends BroadcastReceiver {
    public void onReceive(Context context, Intent intent) {
        Context storage = context.createDeviceProtectedStorageContext();
        new android.util.AtomicFile(new File(storage.getFilesDir(), "startup-preview.jpg")).delete();
    }
}
