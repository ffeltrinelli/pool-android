# Android releases

The Android app is distributed as a standalone signed APK (Android application package), through GitHub Releases or Settings → **Share app**. Sharing copies the installed package: it preserves the signature and current version, excludes preferences/statistics, and requires no download server. The receiving phone opens the APK and approves installation from its receiving app. Device security policy can still restrict installation.

Share app is Android-only. Debug copies have `-debug` in their filenames. Split-package installations are not supported. Preparation happens off the graphics thread; Settings remains open and the match remains paused after returning. Sharing uses temporary read access to cache files retained for up to seven days between sharing requests, so returning from the chooser does not interrupt transfers.

## Create and protect the signing identity

With JDK 21 selected, run once:

```sh
python3 tools/setup_android_signing.py
```

This generates an RSA 3072-bit key, valid for 100 years, with alias `pool-release`. The keystore and password configuration are stored with owner-only permissions in `~/.android/pool-signing/`. The script refuses to overwrite an existing identity. It prints no password. Back up **both files** in encrypted storage and save the password in your password manager; losing the key prevents normal updates to distributed installations. Neither file belongs in Git or PR attachments.

Gradle reads `signing.properties` from that directory, or the path in `POOL_SIGNING_PROPERTIES`. Its properties are `storeFile` (an absolute keystore path), `storePassword`, `keyAlias`, and `keyPassword`.

Local debug and release builds use this identity when configured. Without it, CI and other machines use their normal debug certificate; their APKs cannot update your locally signed installation. Release builds require signing credentials. Do not upload release credentials to ordinary CI jobs.

Existing installs signed with the former debug key cannot be updated using the new identity. Switching requires uninstalling, which normally erases local preferences and statistics. Obtain the owner's explicit permission first. Thereafter, local debug/release switching preserves app identity and data, subject to Android's version checks.

## Version and publish

A release is explicitly requested or agreed upon; ordinary PRs and local edits remain unreleased. Every distributed release updates both values in `android/build.gradle`:

- `versionName`: `MAJOR.MINOR.PATCH`. Incompatible changes increase MAJOR, compatible features increase MINOR, and compatible fixes/polish increase PATCH. The same rules apply below 1.0.0. Reset lower components after increasing a higher one.
- `versionCode`: increase the integer by one for every distributed release. Android uses it to reject downgrades.

Compatibility covers preferences, statistics, and documented external formats. A migration that preserves these contracts remains compatible. Assess all changes since the previous release, not just the last PR. Record release notes in `docs/release-notes.md`.

Run the repository checks and leave the PR open for the user’s merge decision. Never merge or enable auto-merge unless the user explicitly instructs you to merge that PR; release authorization and passing CI are not merge authorization. After the user merges it, or explicitly authorizes its merge, build from the clean merged commit:

```sh
./gradlew :core:test :android:lintDebug :android:assembleDebug :android:assembleRelease
./gradlew :lwjgl3:visualTest :lwjgl3:audioTest
"$ANDROID_HOME/build-tools/35.0.0/apksigner" verify --print-certs android/build/outputs/apk/release/android-release.apk
adb devices -l
adb -s SERIAL install -r android/build/outputs/apk/release/android-release.apk
adb -s SERIAL shell am start -n io.github.ffeltrinelli.pool/.android.AndroidLauncher
```

The release uses code shrinking; Bullet’s native-to-Java callbacks require the committed keep rule. Debug-only launch checks do not validate the minified release. Compare debug/release certificates, verify the APK package/version, and validate changed behavior and background/return handling on the intended authorized physical device. If multiple targets exist, resolve the intended serial first. Record results or unavailable-device limitations in `docs/device-validation.md`; absent hardware does not block an otherwise verified release. Signature conflicts must never trigger an automatic uninstall.

After verification, copy the release APK to `build/releases/Feltri-8-Ball-VERSION.apk`. Tag the exact source commit as `vVERSION`, push that tag, and create a **public** GitHub Release with the APK attached and the version's release notes. Release authorization includes this publication; do not create a draft or ask again. Never replace an existing release tag. The tagged source and attached APK must match. Android Developer Console registration is separate from APK signing and publication.
