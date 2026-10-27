package com.gathin.tvhome;
public final class LauncherApplication extends android.app.Application {
 @Override public void onCreate(){super.onCreate();BundledDefaults.start(this);}
}
