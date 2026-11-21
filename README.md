# ReelGuard (Android)
1. Install Android Studio (Ladybug or newer). File > Open > select this ReelGuard folder. Let Gradle sync.
2. Plug in an Android phone (USB debugging on) or start an emulator. Press Run.
3. On the phone: Settings > Accessibility > Installed apps > ReelGuard > turn on.
   Android 13+: if the toggle is greyed out, open App info > ⋮ > Allow restricted settings, then retry.
4. Open Instagram Reels / YouTube Shorts / TikTok and swipe. The bar appears at the top and counts automatically.

If a reel is not being counted in Instagram or YouTube after an app update, the app renamed its internal view ids.
Edit the keyword lists in the `apps` map in ReelAccessibilityService.kt.

## Get an APK without Android Studio
1. Create a free GitHub account and a new empty repository.
2. Upload every file in this folder (keep the .github folder) and commit.
3. Open the Actions tab > "Build APK" > wait for the green tick > download the ReelGuard-apk artifact.
4. Unzip it, send app-debug.apk to your phone, and install (allow "install unknown apps").
The main screen shows "Last swipe seen" with the scroll values the app receives. Send me that text after testing.
