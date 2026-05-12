# OPNsense Admin

Android application for remotely managing OPNsense routers through the official OPNsense API.

## Current Scope

The app currently includes:

- Dashboard with live system metrics and historical graphs
- Firewall automation rules view and enable/disable actions
- Interfaces overview and interface reload actions
- Services overview with start/stop/restart actions
- Firmware update status, update check, and update trigger
- System status notifications with dismiss support

## Tech Stack

- Kotlin
- Jetpack Compose
- Material 3
- OPNsense HTTP API using API key + API secret over Basic Auth

## Requirements

- Android Studio or a recent Android-capable Gradle toolchain
- JDK 11
- Android SDK with API 36
- An OPNsense user with API key/secret and the required privileges

## Local Build

Debug APK:

```bash
./gradlew assembleDebug
```

Release APK:

```bash
./gradlew assembleRelease
```

The unsigned release APK is generated under:

```text
app/build/outputs/apk/release/
```

## Authentication

The app uses:

- API key as the Basic Auth username
- API secret as the Basic Auth password

For routers with self-signed certificates, the app can optionally ignore invalid SSL certificates. This is intended for controlled environments only.

## GitHub Actions

This repository includes a GitHub Actions workflow at `.github/workflows/release-apk.yml` that builds the release APK when a version tag is pushed and also attaches the APK to a published GitHub release.

Expected tag format:

```text
v1.0.0
v1.2.3
v2.0.0-rc1
```

The workflow uploads the generated APK as a GitHub Actions artifact, and when a GitHub release is published for that tag it also uploads the APK into the release assets.

## Notes

- The release build is currently unsigned unless signing is added to the Gradle configuration.
- Some OPNsense UI areas do not have official API support. In those cases the app only implements the supported API-backed functionality.
