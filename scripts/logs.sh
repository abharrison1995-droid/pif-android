#!/bin/bash
# Shows the engine log (logcat) and the compatibility script log from the device.
ADB="${ADB:-$HOME/Android/Sdk/platform-tools/adb}"

echo "== pif-android.log"
"$ADB" shell cat /storage/emulated/0/InfiniteFusion/pif-android.log 2>/dev/null
echo "== logcat (mkxp, SDL, crashes)"
"$ADB" logcat -d -s mkxp:* mkxp-z[Activity]:* mkxp-z[Controller]:* SDL:* SDL/APP:* AndroidRuntime:E DEBUG:* libc:F
