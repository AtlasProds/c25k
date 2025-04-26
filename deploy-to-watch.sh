#!/bin/bash

# Build the app
./gradlew :wear:assembleRelease

# Find connected devices
DEVICE=$(adb devices | grep -v "List" | grep "device" | head -1 | cut -f 1)

if [ -z "$DEVICE" ]; then
  echo "No device connected. Make sure your watch is connected via ADB."
  exit 1
fi

echo "Deploying to device: $DEVICE"

# Uninstall existing app (optional)
echo "Uninstalling existing app..."
adb -s $DEVICE uninstall com.example.c25kbuddy

# Install the new APK
echo "Installing release APK..."
adb -s $DEVICE install -r wear/build/outputs/apk/release/wear-release.apk

echo "Deployment complete!" 