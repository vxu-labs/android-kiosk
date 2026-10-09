# Verification — 2026-10-09

## Version 1.1.0

The update adds persisted Hebrew/English resources and RTL/LTR layout, and moves lock status out of the public display into PIN-protected administration. Hebrew uses Android's legacy `values-iw` resource qualifier for compatibility.

- The 1.0.0 APK was installed on an Android 11 emulator and seeded with a PIN, website, custom content and settings. Installing 1.1.0 over it with the same signing key preserved those values and did not grant an admin session.
- Core checks cover 34 security assertions plus matching English/Hebrew resource keys and references.
- 18 language/footer instrumentation assertions passed on Android 11: first-run language selection, English/Hebrew screens, LTR/RTL, persistence, retained custom content and PIN, no footer on home or website, and real status confined to administration.
- The final 1.1.0 APK also passed all 19 Android smoke checks and all 18 Device Owner checks described below. ADB was restored after the owner test and its saved success report retrieved.

## Version 1.0.0 baseline

## Passed

- Built a release APK using JDK 17, Android SDK 35, AAPT2, javac and D8. Minimum Android API 28; no native ABI-specific code.
- 34 JVM security checks: PIN validation, salted PBKDF2 verification, malformed credentials, attempt backoff, HTTPS validation and host restriction bypass cases.
- APK signature verified with Android apksigner (v3, supported on all declared Android versions), plus zipalign validation.
- Installed the signed production APK and a separately signed test APK on a disposable Android 11 / API 30 x86_64 emulator, 1200 × 800 tablet display, WHPX acceleration.
- 19 Android smoke checks: first-run setup, PIN creation, home rendering, settings hidden by default, Back cannot finish the kiosk, wrong/correct PIN, admin session revocation on pause, website mode and persistence, file/content access disabled, mixed content blocked, restart behavior, cooldown and stored credential format.
- 18 Device Owner checks on that emulator: ownership, actual `LOCK_TASK_MODE_LOCKED`, one-package allowlist, `LOCK_TASK_FEATURE_NONE`, uninstall restriction, debugging/safe boot/factory reset/overlay/add-user restrictions, persistent Home assignment, PIN access while fully locked, settings keeping Android locked, and release of lock/restrictions for maintenance.
- The Device Owner test deliberately disabled ADB as production does; its cleanup restored emulator ADB, and its saved report was retrieved afterward: `PASS: 18 Device Owner checks`.
- The first extended Owner test tried to click a dialog in the same main-thread callback that opened it, before Android dispatched OnShow. Splitting those test actions with an idle wait fixed that test timing issue; the production authentication implementation was unchanged.
- Visually inspected screenshots of the internal home and the scrollable Hebrew administration screen. Screenshots are in `build/screenshots/`; instrumentation temporarily clears FLAG_SECURE only while capturing test screenshots. The distributed application always applies FLAG_SECURE.

## Physical-device checks still required

No Samsung Galaxy Tab S6 Lite was connected. Samsung One UI behavior, S Pen / Air Command / side-key features, actual cold boot, prolonged operation, the user's website and its sign-in flow, Wi-Fi reconnection, Android 12–15 behavior, and hardware-key interactions have not been verified on that tablet. Follow the checklist in `docs/INSTALL-HE.html` after installation before public deployment.

The app is not capable of physically powering on a fully powered-off device. Device Owner provisioning is required for full lock. From 1.1.0, accurate lock status appears in PIN-protected administration; no status footer is shown over public content. Selecting the app as Home is not the same as full lock task mode.

## Reproduction

```powershell
.\scripts\build.ps1
.\scripts\build-android-tests.ps1
# Use a disposable emulator ONLY: these tests clear the app's test data.
.\scripts\test-emulator.ps1
```

The smoke tests run before Device Owner is provisioned. After assigning ownership, the owner test verifies and then releases kiosk policies. APKs in `build/` are test-only and are never included in the installation bundle. Never install or run the test APK on a production tablet.

The release signing key and password live only in ignored `.signing/`. Back up that folder privately for future APK updates. Downloaded toolchains, local activity history, signing material and build outputs are excluded from the public source snapshot.
