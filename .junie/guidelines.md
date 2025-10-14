# Project Guidelines

We're creating an Android app that is a privacy focused camera.

We want to take pictures, strip all metadata, store them in app-private encrypted storage
and require a PIN separate from the device's PIN to view them.

With all of that privacy in mind, we do want to be able to use the normal Android "share"
system for getting the images out of our secure storage and into other apps.

As a stretch goal, I want to provide further security features such as automatic face
blurring.

* The project consists of a single `app` module which is the whole application
* We're using Kotlin
* Prefer KMP libraries over Kotlin/JVM when possible
* We're using Compose for the UI
* We use tabs, no spaces
* We should write unit tests when possible for new functions or features
* We use Mockk for our test mocking
* Comment code only sparingly. Obvious comments should be left out, only comment on code that is slight weird or complex

## Data Architecture

We use a simple, layered architecture:

- **DataSource**
    - Stateless
    - Lowest-level components that talk to the outside world (Android framework, storage, camera, crypto, file
      provider).
    - Responsibilities: raw I/O only, no business rules. Example: read/write bytes via EncryptedFile, capture image
      bytes via CameraX, access secure app-private directories, provide streams for sharing via a FileProvider.
    - Should not expose Android-specific types to upper layers when avoidable; return plain data (bytes, DTOs) or flows.

- **Repository**
    - Stateful
    - Single source of truth for a data domain. Orchestrates one or more DataSources and enforces privacy rules.
    - Responsibilities: enforce stripping of EXIF/metadata before persisting; ensure data at rest is encrypted and only
      stored in app-private storage; map raw data to domain models; cache as needed; expose cold/suspend functions and
      Flow/StateFlow for observation.
    - Example: PhotoRepository saves a captured image by invoking Camera DataSource, strips metadata, writes encrypted
      bytes, indexes result for the gallery, and provides a shareable URI when requested.

- **UseCase**
    - Stateless
    - Focused application actions that compose repository operations into a single intention.
    - Responsibilities: encapsulate business logic and validation, thread policy (suspend/invoke pattern), and error
      mapping. Keep them platform-agnostic where possible.
    - Examples: CapturePhotoUseCase, GetGalleryItemsUseCase, DeletePhotoUseCase, SharePhotoUseCase,
      SetOrVerifyPinUseCase.

- **ViewModel**
    - Stateful
    - UI-facing state holder for Compose screens/tabs. Consumes UseCases and exposes UI state/events (StateFlow,
      immutable state objects).
    - Responsibilities: transform domain results to UI models, handle one-off effects (navigation/events), survive
      config changes, and never leak sensitive data beyond what the UI needs.
    - Example: GalleryViewModel collects gallery flow, requests SharePhotoUseCase when the user shares, and gates access
      behind the PIN.

### Conventions:

- Keep DataSources minimal and framework-bound; keep Repositories the place for privacy enforcement; keep UseCases small
  and composable; keep ViewModels UI-only.
- Prefer KMP-friendly abstractions where sensible (domain models, use cases), while Android-specific details live in
  DataSources and DI wiring.
- Write unit tests for UseCases and Repositories; mock DataSources with Mockk, or implement a dummy of the interface.
  Write UI/state tests around ViewModels when logic is non-trivial.
- Entities in a given layer should not access siblings of the same layer when ever possible, they should only access
  entities in the layer below them.