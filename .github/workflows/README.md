# GitHub Actions Workflows for App Publishing

This directory contains GitHub Actions workflow configurations for automating the build and
deployment process to both Google Play and GitHub Releases.

## Workflow: Publish Releases

The `publish-release.yml` workflow automatically builds and publishes the app when a new tag with
the format `v*` (e.g., `v1.0.0`) is pushed to the repository. It contains two jobs:

1. Build and publish to Play Store
2. Build and publish GitHub release

### Required Secrets

#### For Play Store Publishing

The following secrets must be configured in your GitHub repository settings:

1. **ENCODED_KEYSTORE**: Base64-encoded Android keystore file
   ```bash
   # Generate using:
   base64 -w 0 keystore.jks > keystore_base64.txt
   ```

2. **KEYSTORE_PASSWORD**: Password for the keystore

3. **KEY_ALIAS**: Alias of the key in the keystore

4. **KEY_PASSWORD**: Password for the key

5. **PLAY_STORE_CONFIG_JSON**: Google Play service account JSON key file content
    - This is used by Fastlane to authenticate with Google Play
    - You need to create a service account in the Google Play Console with the appropriate permissions

#### For GitHub Release Publishing

The following secrets must be configured in your GitHub repository settings:

1. **GITHUB_RELEASE_ENCODED_KEYSTORE**: Base64-encoded Android keystore file (separate from Play Store keystore)
   ```bash
   # Generate using:
   base64 -w 0 github_release_keystore.jks > github_release_keystore_base64.txt
   ```

2. **GITHUB_RELEASE_KEYSTORE_PASSWORD**: Password for the GitHub release keystore

3. **GITHUB_RELEASE_KEY_ALIAS**: Alias of the key in the GitHub release keystore

4. **GITHUB_RELEASE_KEY_PASSWORD**: Password for the key in the GitHub release keystore
