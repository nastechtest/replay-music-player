# APKs

No APK is committed (D-69). Builds land here locally and stay ignored.

- **Release** (what people download): `./gradlew :app:assembleRelease`, signed with the release key
  from `keystore.properties` (outside git), published as a GitHub Release with the asset name
  `replay.apk` so `releases/latest/download/replay.apk` always points at the newest one.
  See `docs/deployment.md`.
- **Debug** (the build on the development phone, `app.replay.debug`):
  `./gradlew :app:assembleDebug`, then `adb install -r` and
  `adb shell cmd package compile -m speed -f app.replay.debug` (deployment.md §3). It carries the
  debug commands and the test tones, so it's never published.
