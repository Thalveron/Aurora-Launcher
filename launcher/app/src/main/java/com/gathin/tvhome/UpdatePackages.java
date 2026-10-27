package com.gathin.tvhome;
final class UpdatePackages {static long code(android.content.pm.PackageInfo info){return android.os.Build.VERSION.SDK_INT>=28?info.getLongVersionCode():info.versionCode;}}
