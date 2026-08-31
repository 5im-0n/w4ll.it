# F-Droid submission metadata

`it.w4ll.yml` is a ready-to-complete build recipe for the [F-Droid data repository](https://gitlab.com/fdroid/fdroiddata). It deliberately contains the `RELEASE_COMMIT` placeholder: F-Droid recipes must build an immutable full commit hash, so it must be replaced with the commit hash of the signed and tagged release being submitted.

## Submission steps

1. Commit the release sources, then create and push a matching version tag (currently `v1.0.1`).
2. Copy `it.w4ll.yml` to a fork of `fdroiddata` at `metadata/it.w4ll.yml`.
3. Set `Builds[0].commit` to the new release tag's full commit hash. Keep the version name and code equal to `app/build.gradle.kts`.
4. Build and validate the recipe using F-Droid's tools, then open a merge request against `fdroiddata`.

The bot previously scanned `v1.0.0`, which predated the wrapper integrity configuration. That release cannot be retroactively changed, so submit the new release instead. Its Gradle wrapper has a distribution SHA-256, and `gradle/verification-metadata.xml` pins all downloaded Gradle artifacts. OpenCensus is pulled by Android Gradle Plugin build tooling, not included in the app's runtime dependency graph or APK.

The app has no proprietary SDKs, ads, analytics, trackers, accounts, or embedded credentials. It does, however, depend on Wallhaven for its main function. Leave the `NonFreeNet` anti-feature in the submitted metadata so F-Droid users receive an accurate disclosure.
