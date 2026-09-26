#!/bin/bash
set -e
SDK=/home/claude/android-sdk
BT=$SDK/build-tools/35.0.0
JAR=$SDK/platforms/android-34/android.jar
rm -rf build && mkdir -p build/classes build/dex
$BT/aapt2 link -o build/base.apk --manifest AndroidManifest.xml -I $JAR --min-sdk-version 24 --target-sdk-version 34
javac --release 8 -Xlint:-options -classpath $JAR -d build/classes $(find src -name '*.java')
$BT/d8 --min-api 24 --lib $JAR --output build/dex $(find build/classes -name '*.class')
cp build/base.apk build/unaligned.apk
(cd build/dex && zip -q ../unaligned.apk classes.dex)
$BT/zipalign -f 4 build/unaligned.apk build/aligned.apk
[ -f debug.keystore ] || keytool -genkeypair -keystore debug.keystore -storepass android -keypass android -alias key -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Behind Alert, O=HackGT" >/dev/null 2>&1
$BT/apksigner sign --ks debug.keystore --ks-pass pass:android --key-pass pass:android --out build/BehindAlert.apk build/aligned.apk
$BT/apksigner verify --verbose build/BehindAlert.apk | head -4
