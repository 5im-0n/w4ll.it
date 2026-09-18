# w4ll.it — Wallhaven Wallpapers for Android

<p align="center">
  <img src="design/w4ll-it-icon.svg" width="300px" />
</p>

`w4ll.it` is a free and open-source Android wallpaper app powered by the public [Wallhaven](https://wallhaven.cc/) catalogue. It fetches recent wallpapers that match your chosen tags, displays them in a two-column grid, and lets you apply a wallpaper to the home screen, lock screen, or both.

<p align="center">
  <img src="design/Screenshot1.png" width="300px" />
  <img src="design/Screenshot2.png" width="300px" />
</p>

## Features

- Fetches the most recently added wallpapers from Wallhaven.
- Uses only Wallhaven's public results; no account, API key, or credential is required.
- Displays cached wallpapers in a two-column grid for quick browsing.
- Applies a selected image to the home screen, lock screen, or both.
- Starts fetching automatically when the app opens with an empty wallpaper cache.
- Lets you configure:
  - the number of wallpapers to fetch (1–100; default: 50);
  - comma-separated wallpaper tags (default: `nature, abstract, landscape, city`);
  - comma-separated tags to exclude from all results;
  - a periodic home-screen wallpaper change interval (default: every 6 hours; `0` disables it).
- Refreshes the cached Wallhaven results asynchronously once every 24 hours when a network connection is available.

## Privacy and network services

- The app contains **no advertising, analytics, trackers, accounts, or API keys**.
- Settings and the cached wallpaper list are stored locally on the device.
- To perform its core function, the app contacts Wallhaven and the image hosts returned by Wallhaven. Those services receive normal network-request data, including the device IP address and the requested tags or images.
- Wallhaven is a third-party, non-libre network service. An F-Droid listing must therefore disclose the `NonFreeNet` anti-feature. The app does not contain a proprietary SDK or depend on Google Play services.

## License

w4ll.it source code and the project-owned artwork are released under the [MIT License](LICENSE). Wallpapers fetched from Wallhaven are not part of this source distribution and remain subject to their creators’ rights and Wallhaven’s terms.

## Requirements

- A current stable release of **Android Studio**.
- Android Studio's bundled **JDK 17** (Embedded JDK), or another JDK 17 installation.
- Android SDK Platform 35 (Android 15).
- A physical device or emulator running Android 7.0 / API 24 or later.
- Internet access on the device or emulator to contact Wallhaven and download wallpaper images.

> The project uses Android Gradle Plugin 9.3.2, Kotlin 2.2.10, `compileSdk` 35, `targetSdk` 35, and `minSdk` 24.

## Run from Android Studio

1. Install [Android Studio](https://developer.android.com/studio).
2. Open this repository's root directory—the folder containing `settings.gradle.kts` and `app`.
3. Let Android Studio complete Gradle sync.
   - Install any requested SDK components, including Android SDK Platform 35.
   - If asked for a Gradle JDK, choose **Embedded JDK**.
4. Connect a USB-debugging-enabled device, or create and start an Android Virtual Device with API 24 or newer.
5. Select the device in the Android Studio device selector and press **Run** (`▶`).

## Build a debug APK from the command line

The repository includes the Gradle wrapper. On Windows, use Android Studio's bundled Java runtime:

```bat
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "PATH=%JAVA_HOME%\bin;%PATH%"
call gradlew.bat assembleDebug --stacktrace
```

The generated debug APK is located at:

```text
app\build\outputs\apk\debug\app-debug.apk
```

### Install on a connected device

Ensure `adb devices` shows your device or emulator, then run:

```bat
adb install -r app\build\outputs\apk\debug\app-debug.apk
adb shell am start -n it.w4ll/.MainActivity
```

Alternatively, build and install in one step:

```bat
set "JAVA_HOME=C:\Program Files\Android\Android Studio\jbr"
set "PATH=%JAVA_HOME%\bin;%PATH%"
call gradlew.bat installDebug
```

## GitHub release signing

Tag pushes run `.github/workflows/build-apk-on-tag.yml`, which builds a signed release APK and attaches it to a GitHub Release as `w4ll.apk`. Configure these GitHub Actions repository secrets before pushing a tag:

- `ANDROID_KEYSTORE_BASE64`: the release keystore encoded as a single Base64 string;
- `ANDROID_KEYSTORE_PASSWORD`: the keystore password;
- `ANDROID_KEY_ALIAS`: the signing-key alias;
- `ANDROID_KEY_PASSWORD`: the signing-key password.

Keep the original keystore and its passwords in a secure backup. Every release must use this same key; losing or replacing it prevents Android from installing the new APK as an update.

Create each release tag from the current tip of the default branch using the format `vMAJOR.MINOR.PATCH` (for example, `v1.2.0`). The workflow derives `versionName` from the tag, increments `versionCode`, updates both `app/build.gradle.kts` and `fdroid/it.w4ll.yml`, commits those changes to the default branch, and moves the tag to that generated commit before building. Because this requires force-updating the newly pushed tag, do not protect release tags against updates. If branch protection applies to the default branch, allow GitHub Actions to push to it (or add the Actions bot to the bypass list).

If you do not yet have a keystore, create one from Windows Command Prompt (replace the alias if desired, then securely record the passwords you enter):

```bat
"%JAVA_HOME%\bin\keytool.exe" -genkeypair -v -keystore w4ll-release-key.jks -alias w4ll-upload -keyalg RSA -keysize 2048 -validity 10000
```

Encode the keystore as one Base64 line:

```bat
powershell -NoProfile -Command "[Convert]::ToBase64String([IO.File]::ReadAllBytes('w4ll-release-key.jks'))" > keystore-base64.txt
```

Copy the Base64 value from `keystore-base64.txt` into `ANDROID_KEYSTORE_BASE64`. Add all four secrets under **Repository settings → Secrets and variables → Actions → New repository secret**. Never commit the keystore, `keystore.properties`, passwords, or the generated Base64 file.

## Using the app

1. Open the app. If there are no locally cached wallpapers, it automatically fetches a new set from Wallhaven.
2. Browse the wallpaper grid and tap **Home**, **Lock**, or **Both** on an item to apply it.
3. Tap the cog in the top-right corner to open **Settings**.
4. Set the desired number of images, comma-separated included and excluded tags, and automatic-change interval.
5. Tap **Save settings** to save the values and fetch a new matching set.

Included tags are alternatives: a wallpaper only needs to match **at least one** entered tag. For example, `nature, mountains` fetches wallpapers tagged `nature` or `mountains`. Excluded tags apply to every search and remove matching wallpapers. Multi-word tags can be entered normally, such as `abstract art, city`. Results from all included tags are merged, deduplicated, and kept in most-recent-first order.

## F-Droid

The project includes upstream F-Droid store metadata in `fastlane/metadata/android/en-US/` and a submission recipe template at [`fdroid/it.w4ll.yml`](fdroid/it.w4ll.yml). The release workflow automatically keeps its version fields and source tag aligned with the Android build.

The app is suitable for F-Droid review because it is openly licensed, builds with Gradle from public source dependencies, and contains no proprietary SDK, advertising, analytics, or tracking. Its reliance on Wallhaven must remain transparently marked as the `NonFreeNet` anti-feature.

Before opening the F-Droid `fdroiddata` merge request, push the release tag and wait for the GitHub release workflow to finish. Then copy the updated recipe to `fdroiddata`, test it with F-Droid’s build tools, and open the merge request. Store artwork and phone screenshots are included in the Fastlane metadata directory.

The `v1.0.0` bot scan reported OpenCensus because the Android Gradle Plugin's **build-time** dependency graph contains it; it is not in the application's runtime dependency graph or APK. That tag also predated the Gradle wrapper integrity configuration. The next release includes Gradle distribution checksum verification and `gradle/verification-metadata.xml` with SHA-256 verification for every resolved Gradle artifact.

## Notes and troubleshooting

- Wallhaven may rate-limit requests or temporarily be unavailable. If a fetch fails, wait briefly and try again.
- Results are sorted by most recently added on Wallhaven.
- The automatic wallpaper-change task applies a random cached wallpaper to the **home screen**. Android and device manufacturers can differ in their lock-screen wallpaper behavior.
- Background work is managed by Android's WorkManager, so its exact execution time can vary because of battery-saving and system scheduling policies.
- If no results are returned, try broader or different tags.
- Wallpaper images remain subject to their respective creators' rights and Wallhaven's terms.
- If Gradle sync or builds fail, confirm that Android Studio is using JDK 17 and that SDK Platform 35 is installed.
