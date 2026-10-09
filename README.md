# Android Kiosk

A small, offline-first Android kiosk by **VXU LABS**, with a built-in home screen or a fullscreen HTTPS website, PIN-protected administration, and Android Enterprise dedicated-device locking.

[עברית](README.he.md) · [Download APK](https://github.com/vxu-labs/android-kiosk/releases/latest) · [License](LICENSE)

## Features

- Dark by default, with a saved Light option in PIN-protected settings. Rounded cards, clearly outlined PIN fields and a two-action tap menu. Appearance applies to the kiosk interface; websites keep their own design.
- Hebrew and English UI, including RTL/LTR layout and localized dates. Choose a language on first setup or in PIN-protected settings, then save.
- A basic home screen with your title, welcome message, clock and date, or a fullscreen HTTPS website.
- A clean public display: no preview or management-status footer over the home screen or website. Accurate lock status remains in administration.
- Personal 6–12 digit PIN, salted PBKDF2 storage, persistent attempt throttling and expiring admin sessions. No default or recovery PIN.
- Device Owner lock task, persistent Home assignment, boot launch, restricted system UI and supervised maintenance access.
- No ads, analytics SDKs, remote administration service or third-party app libraries.

Android 9+ (API 28), architecture-independent Java. Originally built for a Samsung Galaxy Tab S6 Lite; see [verification](VERIFICATION.md) for tested environments and limits.

## Install or update

Download `Kiosk-1.2.0.apk` from Releases and install it. For an update, install **over the existing app**, using the same signing key. Do not uninstall: the PIN, website, text and settings are kept.

Installing the APK alone does **not** enable full Android locking. Selecting Kiosk as the Home app only makes the Home button return to it. Full lock requires Device Owner provisioning.

1. Back up the tablet. Provisioning requires a device without conflicting accounts, secondary users or another device manager. Android may require a factory reset; this project never resets the device automatically.
2. Enable USB debugging, connect one tablet to a computer with official Android Platform Tools and authorize the computer on the tablet.
3. Install and provision:

```sh
adb install -r Kiosk-1.2.0.apk
adb shell dpm set-device-owner il.co.kiosk/.KioskAdminReceiver
adb shell am start -n il.co.kiosk/.MainActivity
```

4. Create your PIN, select the content and language, then save. When Device Owner is available, confirm **Save and enable kiosk**.
5. Tap **⋮ in the top-right corner**, choose **Kiosk settings**, then enter the PIN to manage the kiosk. **Refresh homepage** is available without a PIN and returns to the saved home screen or website URL. Maintenance/Android exit requires another PIN check. Returning to Kiosk or rebooting reapplies the lock.

Full locking disables USB debugging. Before updating through ADB, enter supervised maintenance and enable debugging again if needed. Keep the tablet with you during maintenance. The admin screen also offers permanent removal of device management before uninstalling.

[Detailed Hebrew installation guide](docs/INSTALL-HE.html)

## Website behavior

HTTPS only. Navigation stays on the configured hostname unless the administrator allows other HTTPS hosts, for example for sign-in. Invalid certificates, external-app links, downloads, file uploads, pop-ups, location, camera and microphone access are blocked. Third-party cookies are disabled. Check your intended website and sign-in flow before unattended use.

Changing the app language does not translate or overwrite your website or custom title/message. Those remain exactly as you entered them.

## Build

Requirements for the direct Windows build: PowerShell, Node.js, and the downloaded JDK/Android SDK archives.

```powershell
.\scripts\bootstrap.ps1
.\scripts\build.ps1
.\scripts\verify-release.ps1
```

The bootstrap script downloads official JDK/SDK archives and checks their published hashes. The build runs core/resource checks, compiles the app, signs the APK and verifies its signature/alignment. Release verification also packages and validates a local Windows setup ZIP. Third-party Android tools in that generated ZIP retain their own notices and licenses; the MIT license covers this project's authored source.

Version name/code live in `version.properties`. Android Studio project files are also provided (AGP 8.7.3, Gradle 8.9, JDK 17, SDK 35); the direct PowerShell build is the validated release path.

The first local build creates a private signing key in `.signing/`. **Back it up privately.** It is excluded from source control and distribution. A locally generated new key cannot update a published APK signed with another key. Official releases must reuse the original release key.

## Tests and limitations

See [VERIFICATION.md](VERIFICATION.md). Instrumentation tests clear app data and are for disposable emulators only; never install the test APK on a production tablet.

The app can open automatically after boot. It cannot physically power on a switched-off tablet or prevent hardware recovery/forced reboot. Device-specific Samsung features, actual cold boot, prolonged use and your website still need acceptance testing on your device. If the PIN is lost, there is no bypass; recovery may require erasing the tablet.

## License

MIT © 2026 VXU LABS. See [LICENSE](LICENSE).
