# How to publish a new release

- Update `versionCode` and `versionName` in [libs.versions.toml](/gradle/libs.versions.toml)
- Create a changelog in: `\fastlane\metadata\android\en-US`
- Create a **semvar** tag in the form of "v1.0.0" on `master` and push it, this will trigger a release.

### Automated Publishing

We use GitHub Actions to automatically build and publish new releases to Google Play when a tag with the
format `v*` (e.g., `v1.0.0`) is pushed. See the [GitHub Actions workflow documentation](.github/workflows/README.md) for
details on how this works and the required setup.

The project includes a pre-configured [FastLane](https://fastlane.tools/) setup for automating the deployment process.
See the [FastLane documentation](fastlane/README.md) for details on how to use it for manual deployments or to customize
the metadata.