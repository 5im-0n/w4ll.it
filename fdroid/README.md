# F-Droid submission metadata

`it.w4ll.yml` is a ready-to-complete build recipe for the [F-Droid data repository](https://gitlab.com/fdroid/fdroiddata). It deliberately contains the `RELEASE_COMMIT` placeholder: F-Droid recipes must build an immutable full commit hash, so it must be replaced with the commit hash of the signed and tagged release being submitted.

## Submission steps

1. Commit the release sources and create a matching `v1.0` tag (or the applicable version tag).
2. Copy `it.w4ll.yml` to a fork of `fdroiddata` at `metadata/it.w4ll.yml`.
3. Set `Builds[0].commit` to the release tag's full commit hash. Keep the version name and code equal to `app/build.gradle.kts`.
4. Build and validate the recipe using F-Droid's tools, then open a merge request against `fdroiddata`.

The app has no proprietary SDKs, ads, analytics, trackers, accounts, or embedded credentials. It does, however, depend on Wallhaven for its main function. Leave the `NonFreeNet` anti-feature in the submitted metadata so F-Droid users receive an accurate disclosure.
