#!/usr/bin/env bash
set -e

BT="${ANDROID_BUILD_TOOLS:-$HOME/Library/Android/sdk/build-tools/35.0.0}"
PLATFORM="${ANDROID_PLATFORM:-$HOME/Library/Android/sdk/platforms/android-35/android.jar}"

mkdir -p build/gen build/obj build/apk

"$BT/aapt2" compile --dir res -o build/res.zip
"$BT/aapt2" link -o build/unsigned.apk -I "$PLATFORM" --manifest AndroidManifest.xml --java build/gen build/res.zip
javac --release 8 -cp "$PLATFORM" -d build/obj build/gen/com/facu/budsswitcher/R.java src/com/facu/budsswitcher/MainActivity.java
"$BT/d8" --lib "$PLATFORM" --min-api 26 --output build/apk build/obj/com/facu/budsswitcher/*.class

cp build/unsigned.apk build/unaligned.apk
(cd build/apk && zip -u ../unaligned.apk classes.dex)
"$BT/zipalign" -f -p 4 build/unaligned.apk build/aligned.apk

if [ ! -f build/debug.keystore ]; then
  keytool -genkeypair -v -keystore build/debug.keystore -storepass android -alias androiddebugkey -keypass android -keyalg RSA -keysize 2048 -validity 10000 -dname "CN=Android Debug,O=Android,C=US"
fi

"$BT/apksigner" sign --ks build/debug.keystore --ks-pass pass:android --key-pass pass:android --out BudsSwitcher.apk build/aligned.apk
echo "✅ Built BudsSwitcher.apk successfully"
