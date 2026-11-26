#!/usr/bin/env bash
# بناء الـ APK بدون Android Studio (يحتاج: aapt, dalvik-exchange, apksigner, zipalign, JDK).
# على أوبونتو: sudo apt install aapt apksigner dalvik-exchange zipalign android-sdk-platform-23
# الأسهل: افتح المشروع بـ Android Studio واضغط Build > Build APK.
#
# المتغيرات:
#   ANDROID_JAR    ملف فيه موارد وكلاسات أندرويد 34، مثل android.jar من SDK 34
#                  أو org.robolectric:android-all:14-robolectric-10818077 من Maven Central
#   JAVA_LANG_JAR  (اختياري) إذا ANDROID_JAR ما بيه java.lang، مثل platforms/android-23/android.jar
set -euo pipefail
cd "$(dirname "$0")/.."

ANDROID_JAR=${ANDROID_JAR:?set ANDROID_JAR to an API 34 android.jar}
CLASSPATH_JAR=${CLASSPATH_JAR:-$ANDROID_JAR}
OUT=build
APP=app/src/main

rm -rf "$OUT" && mkdir -p "$OUT/gen" "$OUT/classes" "$OUT/dex"

# 1) الموارد + R.java
aapt package -f -m -J "$OUT/gen" -M "$APP/AndroidManifest.xml" -S "$APP/res" -I "$ANDROID_JAR" \
    -F "$OUT/app.unsigned.apk"

# 2) ترجمة الجافا
javac -nowarn -source 8 -target 8 -encoding UTF-8 -bootclasspath "$CLASSPATH_JAR:${JAVA_LANG_JAR:-$ANDROID_JAR}" \
    -d "$OUT/classes" $(find "$APP/java" "$OUT/gen" -name '*.java') 2>&1 | grep -vE 'Picked up|warning: \[options\]|^1 warning|^3 warnings' || true

# 3) تحويل لـ dex وإضافته للـ APK
dalvik-exchange --dex --min-sdk-version=26 --output="$OUT/dex/classes.dex" "$OUT/classes"
(cd "$OUT/dex" && aapt add ../app.unsigned.apk classes.dex >/dev/null)

# 4) محاذاة وتوقيع
zipalign -f -p 4 "$OUT/app.unsigned.apk" "$OUT/app.aligned.apk"
apksigner sign --ks keystore/adhan.jks --ks-pass env:ADHAN_KS_PASS --key-pass env:ADHAN_KS_PASS \
    --out "$OUT/MosulAdhan.apk" "$OUT/app.aligned.apk"
apksigner verify "$OUT/MosulAdhan.apk"
echo "تم: $OUT/MosulAdhan.apk"
