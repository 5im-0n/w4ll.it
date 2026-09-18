# F-Droid submission metadata

`it.w4ll.yml` is the build recipe template for the [F-Droid data repository](https://gitlab.com/fdroid/fdroiddata). The GitHub release workflow derives the release version from a `vMAJOR.MINOR.PATCH` tag, increments the Android version code, and updates the recipe's build and current-version fields. Its `commit` field uses the release tag, which F-Droid resolves to the immutable tagged revision.

## Submission steps

1. Create and push a matching version tag from the current tip of the default branch, for example `v1.2.0`.
2. Wait for the GitHub release workflow to commit the generated version metadata and finish the release.
3. Copy `it.w4ll.yml` to a fork of `fdroiddata` at `metadata/it.w4ll.yml`.
4. Build and validate the recipe using F-Droid's tools, then open a merge request against `fdroiddata`.

The bot previously scanned `v1.0.0`, which predated the wrapper integrity configuration. The original `v1.0.1` tag was created with verification metadata generated on Windows only, so GitHub Actions could not verify a few Linux-selected and repository metadata artifacts. Submit a newer release instead. Its Gradle wrapper has a distribution SHA-256, and `gradle/verification-metadata.xml` pins all executable Gradle artifacts used by Windows and Linux builds. Android Studio separately resolves dependency source archives in a detached configuration for code navigation; those archives are explicitly trusted rather than individually hashed because they are neither executed nor included in the APK. OpenCensus is pulled by Android Gradle Plugin build tooling, not included in the app's runtime dependency graph or APK.

The app has no proprietary SDKs, ads, analytics, trackers, accounts, or embedded credentials. It does, however, depend on Wallhaven for its main function. Leave the `NonFreeNet` anti-feature in the submitted metadata so F-Droid users receive an accurate disclosure.
