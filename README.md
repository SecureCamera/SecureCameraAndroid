# Snap Safe
*The camera that minds its own business.*

_Available on:_

[![Codacy Badge](https://api.codacy.com/project/badge/Grade/9f4cfddc69f64f199777062499319714)](https://app.codacy.com/gh/SecureCamera/SecureCameraAndroid?utm_source=github.com&utm_medium=referral&utm_content=SecureCamera/SecureCameraAndroid&utm_campaign=Badge_Grade)
[![Google Play](https://img.shields.io/endpoint?color=green&logo=google-play&logoColor=green&url=https%3A%2F%2Fplay.cuzi.workers.dev%2Fplay%3Fi%3Dcom.darkrockstudios.app.securecamera%26l%3DGoogle%2520Play%26m%3D%24version)](https://play.google.com/store/apps/details?id=com.darkrockstudios.app.securecamera)
[![F-Droid](https://img.shields.io/f-droid/v/com.darkrockstudios.app.securecamera?logo=FDROID)](https://f-droid.org/en/packages/com.darkrockstudios.app.securecamera/)
[![GitHub](https://img.shields.io/github/v/release/SecureCamera/SecureCameraAndroid?include_prereleases&logo=github)](https://github.com/SecureCamera/SecureCameraAndroid/releases/latest)

[snapsafe.org](https://snapsafe.org/)

[![featureGraphic.png](fastlane/metadata/android/en-US/images/featureGraphic.png)](http://www.snapsafe.org)

[![codebeat badge](https://codebeat.co/badges/1d47f0fa-2155-4e63-85ba-aafd01812d8c)](https://codebeat.co/projects/github-com-securecamera-securecameraandroid-main)

----

## Why Snap Safe?

**SnapSafe** is a camera app that has been engineered from the ground up to protect your photos.

Attacks come in many forms, from accidental swipes, to intrusive surveillance, and even malicious code.
**SnapSafe** can protect your photos from all angles.

### Key Features

* 🔒 **Zero‑Leak Design** – The app has no internet access; android backups are prevented..
* 🛡️ **Fully Encrypted** – Shots are written to encrypted, app‑private storage.
* 🔢 **PIN‑Locked Gallery** – A separate PIN stands between curious thumbs and your photos.
* 📤 **Secure Sharing** – Metadata is scrubbed and filenames are randomized when you share.
* 😶‍🌫️ **Auto-Face Blur** – Obfuscate faces automatically with our secure blur algorithm.
* 🗺️ **Granular Location** – Add coarse, fine, or zero location data—your call.
* ☠️ **Poison Pill** – Set a special PIN, that when entered, appears to work normally but actually deletes your existing
  photos.
* 🎭 **Decoy Photos** – Select innocuous decoy photos, these will be preserved when your Poison Pill is activated.
* 👀 **100 % Open Source** – Auditable code in plain sight.

### On the Roadmap

* Encrypted video recording. _Maybe._
* Improved photo-taking experience

## Read our papers on SnapSafe

- [Security Design](docs/SnapSafe%20Security%20on%20Android.md)
- [Attack Vectors](docs/SnapSafe%20Attack%20Vectors.md)
- [Related Incidents](docs/SnapSafe%20Related%20Incidents.md)

---

## Contributing

Pull requests are happily accepted.

Start with an issue or draft PR and we can talk it through.

### Automated Publishing

The project uses GitHub Actions to automatically build and publish new releases to Google Play when a tag with the
format `v*` (e.g., `v1.0.0`) is pushed. See the [GitHub Actions workflow documentation](.github/workflows/README.md) for
details on how this works and the required setup.

The project includes a pre-configured [FastLane](https://fastlane.tools/) setup for automating the deployment process.
See the [FastLane documentation](fastlane/README.md) for details on how to use it for manual deployments or to customize
the metadata.

---

## License

SnapSafe is released under the [MIT License](LICENSE). Use it, fork it, improve it—just keep it open.

---

## Privacy

Our full, ultra‑brief Privacy Policy lives in [PRIVACY.md](PRIVACY.md). Spoiler: we collect nothing.
