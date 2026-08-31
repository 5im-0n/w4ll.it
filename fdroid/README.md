# F-Droid submission metadata

`it.w4ll.yml` is a ready-to-complete build recipe for the [F-Droid data repository](https://gitlab.com/fdroid/fdroiddata). It deliberately contains the `RELEASE_COMMIT` placeholder: F-Droid recipes must build an immutable full commit hash, so it must be replaced with the commit hash of the signed and tagged release being submitted.

## Submission steps

1. Commit the release sources, then create and push a matching version tag (currently `v1.0.2`).
2. Copy `it.w4ll.yml` to a fork of `fdroiddata` at `metadata/it.w4ll.yml`.
3. Set `Builds[0].commit` to the new release tag's full commit hash. Keep the version name and code equal to `app/build.gradle.kts`.
4. Build and validate the recipe using F-Droid's tools, then open a merge request against `fdroiddata`.

The bot previously scanned `v1.0.0`, which predated the wrapper integrity configuration. The original `v1.0.1` tag was created with verification metadata generated on Windows only, so GitHub Actions could not verify a few Linux-selected and repository metadata artifacts. Submit the new release instead. Its Gradle wrapper has a distribution SHA-256, and `gradle/verification-metadata.xml` pins all executable Gradle artifacts used by Windows and Linux builds. Android Studio separately resolves dependency source archives in a detached configuration for code navigation; those archives are explicitly trusted rather than individually hashed because they are neither executed nor included in the APK. OpenCensus is pulled by Android Gradle Plugin build tooling, not included in the app's runtime dependency graph or APK.

The app has no proprietary SDKs, ads, analytics, trackers, accounts, or embedded credentials. It does, however, depend on Wallhaven for its main function. Leave the `NonFreeNet` anti-feature in the submitted metadata so F-Droid users receive an accurate disclosure.
