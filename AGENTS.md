# AGENTS.md

Android app "Usahaki POS & Dapur" (POS/cashier + kitchen display), Kotlin + Jetpack Compose, single module `:app`. Generated from Google AI Studio; backend is Firebase (Firestore + Auth + Storage), no local server. UI text and code comments are Indonesian.

## Build

- **No Gradle wrapper in this repo.** Use system `gradle`, not `./gradlew`. CI reference (in stray export dir): Gradle 9.6.0 + JDK 21. Toolchain: AGP 9.1.1, Kotlin 2.2.10, KSP 2.3.5, compileSdk 36 / minSdk 24.
- Debug: `gradle :app:assembleDebug`. **Always use debug builds for normal testing.**
- Release signing requires env vars `KEYSTORE_PATH` (falls back to `<rootDir>/my-upload-key.jks`), `STORE_PASSWORD`, `KEY_PASSWORD`; without them the release build fails. There is intentionally no custom debug signing config.
- `googleServices.missing.passthrough=true`, so builds pass even without `app/google-services.json`.
- Secrets come from the Secrets Gradle plugin (`propertiesFileName = ".env"`, fallback `.env.example`), but **no `.env` or `.env.example` exists in this repo** (the only one is in the stray export dir, unused). To package `GEMINI_API_KEY`, create `.env` at repo root or `app/` and uncomment the key — no source file reads any secret today.

## Architecture / gotchas

- Entry: `MainActivity.kt` → `ui/MainAppScreen.kt` (bottom-nav tabs). Each feature ViewModel under `ui/{pos,cashier,kitchen,auth}/` extends `AndroidViewModel` and creates its own `UsahakiRepository(application)`.
- `data/repository/UsahakiRepository.kt` is the whole backend. It uses a **named Firestore database**: `FirebaseFirestore.getInstance(firebaseApp, NAMED_DATABASE_ID)` with `NAMED_DATABASE_ID = "ai-studio-b778b7d5-6121-4ebb-b57f-d9f58c40eac9"` (UsahakiRepository.kt:40,64). It also hardcodes `FirebaseOptions` (API key, project, bucket) — the build compiles without `google-services.json` because of this.
- Firestore collections in use: `users`, `businesses`, `branches`, `products`, `categories`, `tables`, `tableorders`, `orders`, `transactions`. Unknown/renamed collections here will break the app silently.
- Namespace is `com.example` but `applicationId` is `com.aistudio.usahakipos.app`; `google-services.json` matches the latter. Keep both in sync if changed.
- Bluetooth thermal printer logic lives in `util/printer/ThermalPrinterManager.kt`; barcode scanning uses CameraX + ML Kit in `ui/components/BarcodeScannerDialog.kt`.

## Tests

- Unit tests are Robolectric-based and run on the host: `gradle :app:testDebugUnitTest`. Run one test with `--tests "com.example.FooTest"`.
- Roborazzi screenshot test `GreetingScreenshotTest` writes PNGs into the **tracked** dir `app/src/test/screenshots/`. Roborazzi defaults to *compare* mode, so first runs or intentional screenshot changes need `gradle :app:testDebugUnitTest -PrecordRoborazzi=true` to overwrite the PNGs.
- Known failing/stale test: `ExampleRobolectricTest` asserts `app_name == "My Application"` but `strings.xml` says `Usahaki POS`. Don't chase this as a regression.

## Dead code to ignore

`firebase-ai`, `retrofit`/`okhttp`/`converter-moshi`, `firebase-appcheck`, Room, and the KSP codegen for Room/Moshi are declared in `gradle/libs.versions.toml` and `app/build.gradle.kts` but **unused in source**. build.gradle.kts deliberately keeps some commented-out instead of removing them.

`usahaki-pos-&-dapur (3)/` at repo root is a leftover partial AI Studio export (icon + CI workflow only), not part of the build — don't edit or build it. The project README is AI Studio boilerplate; its step about removing a `debugConfig` signing line is already done.
