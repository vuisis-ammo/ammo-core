#!/bin/bash

/opt/android/android-sdk-linux/platform-tools/aapt \
  package -m -J /opt/ammo/ammo-core/ammo-test-tool-ics/target/generated-sources/r \
  -M /opt/ammo/ammo-core/ammo-test-tool-ics/AndroidManifest.xml \
  -S /opt/ammo/ammo-core/ammo-test-tool-ics/res \
  --auto-add-overlay \
  -I /opt/android/android-sdk-linux/platforms/android-8/android.jar
