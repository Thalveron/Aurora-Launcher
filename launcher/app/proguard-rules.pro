# Shrink and optimise, but keep class and method names: the size win comes from dropping unused
# AndroidX code, not from renaming, and readable stack traces matter more in a sideloaded app
# where crash reports arrive as screenshots.
-dontobfuscate

# Launched by class name from the authorized local ADB helper.
-keep public class com.gathin.tvhome.NetworkTrafficProbe { public static void main(java.lang.String[]); }
