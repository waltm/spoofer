<div align="center">
  <img src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.webp" alt="Spoofer Logo" width="120" height="120" />
  <h1>Spoofer: Advanced Android Location Simulation Engine</h1>
  
  <p>
    <strong>A robust, open-source mock location provider built natively for Android using Kotlin, Jetpack Compose, and modern architecture principles.</strong>
  </p>

  <p>
    <a href="https://developer.android.com/"><img src="https://img.shields.io/badge/Android-3DDC84?style=for-the-badge&logo=android&logoColor=white" alt="Android" /></a>
    <a href="https://kotlinlang.org/"><img src="https://img.shields.io/badge/Kotlin-0095D5?style=for-the-badge&logo=kotlin&logoColor=white" alt="Kotlin" /></a>
    <a href="https://developer.android.com/jetpack/compose"><img src="https://img.shields.io/badge/Jetpack_Compose-4285F4?style=for-the-badge&logo=jetpackcompose&logoColor=white" alt="Jetpack Compose" /></a>
    <a href="https://opensource.org/licenses/MIT"><img src="https://img.shields.io/badge/License-MIT-yellow.svg?style=for-the-badge" alt="License" /></a>
  </p>
</div>

---

## 📖 Table of Contents
1. [Project Overview](#-project-overview)
2. [Deep Dive into Features](#-deep-dive-into-features)
3. [Software Architecture & Tech Stack](#-software-architecture--tech-stack)
4. [The Rubber-Banding Conundrum (Technical Challenge)](#-the-rubber-banding-conundrum-technical-challenge)
5. [Installation & Setup Guide](#-installation--setup-guide)
6. [Known Bugs & Limitations](#-known-bugs--limitations)
7. [Contributing & Community Help](#-contributing--community-help)
8. [License](#-license)

---

## 🌍 Project Overview

**Spoofer** is a native Android application built with Kotlin and Jetpack Compose that allows users to override their device's GPS coordinates for testing and privacy purposes, featuring interactive map integration, joystick-controlled movement, and simulated multi-point route navigation. The project's quality and stability are maintained through a professional QA architecture, featuring comprehensive manual test case documentation, structured bug reporting templates, and an automated end-to-end testing suite built with Python, Pytest, and Appium.

### 🎯 Key Accomplishments & QA Implementations
* Built a native Android application using Kotlin and Jetpack Compose, actively participating in release testing to maintain overall product quality.
* Designed and executed functional, regression, smoke, and sanity testing across different simulated devices and complex user flows.
* Analyzed product requirements to author meaningful test scenarios and test cases, expanding overall test coverage and improving QA processes.
* Developed an automated end-to-end (E2E) testing framework using Python, Pytest, and Appium to validate critical customer journeys.
* Established standardized documentation templates to identify, reproduce, and track bugs with clear steps, ensuring validated fixes do not reoccur.

---

## ✨ Deep Dive into Features

Spoofer is divided into three distinct location injection modalities, supported by a powerful search infrastructure.

### 1. Static Teleportation (Static Mode)
The simplest form of spoofing. By entering an address or dropping a pin on the map, Spoofer immediately teleports the device's system coordinates to that exact location. 
* **Implementation Details:** The engine injects coordinates with a randomized micro-jitter (changing by 0.000018 degrees) to simulate the natural satellite drift of real hardware, preventing anti-cheat systems from detecting a perfectly static, "impossible" GPS lock.

### 2. Manual Directional Control (Joystick Mode)
An on-screen, floating joystick overlay allows the user to manually "walk", "drive", or "fly" in any 360-degree direction.
* **Physics Simulation:** The joystick calculates vector magnitude and bearing. These vectors are passed through a Haversine physics algorithm (`METERS_PER_DEGREE_LAT`) to advance the coordinates mathematically based on the user's selected speed profile (e.g., walking at 5 km/h vs driving at 120 km/h).

### 3. Automated Route Navigation (Directions Mode)
The hallmark feature of Spoofer. Users can select an Origin and a Destination. The app queries the OpenRouteService (OSRM) API to generate a realistic road-geometry polyline.
* **Dynamic Interpolation:** The `SpeedSimulationUseCase` engine iterates over the polyline segments. It calculates the exact distance between nodes and mathematically interpolates your position along the curve of the road at a highly specific update rate (5Hz). It dynamically updates the device's `bearing` (compass heading) and `speed` metrics so that apps like Google Maps behave identically to being in a real moving vehicle.

### 4. PC Receiver Mode
A fourth location source, toggled on under **Settings → Location Source → PC Receiver Mode**. When enabled, it replaces the three on-device modes above with a single socket listener: a PC-side companion sends newline-delimited JSON location updates, and the app feeds them straight into the same mock-location pipeline used by Static mode (including jitter).
* **Transport:** `adb forward tcp:8765 tcp:8765` tunnels a PC-local port straight to the app's listener, which binds to `127.0.0.1` only — it is never exposed on the phone's own network interface.
* **Message format:** `{"Action":"SendPosition","data":{"Lat":"<decimal string>","Lng":"<decimal string>","Type":"<string>"}}`, one JSON object per line.
* **Status:** the bottom sheet shows "Not listening" / "Waiting for PC…" / "PC connected" depending on whether spoofing is active and a client is connected.
* There is currently no PC-side companion app — the port and message format above are meant for a throwaway script or a future dedicated client to send to.

### 5. Multi-Waypoint Routing & Return Modes
Directions mode supports intermediate stops (Add Stop chip) between origin and destination, plus a **Return Mode** choice for the trip back to the start: **None** (end at the destination), **Loop** (a new direct leg from destination back to origin), or **Backtrack** (retrace the same stops in reverse).

### 6. GPX Import/Export
Routes can be exported to a `.gpx` file (via the system file picker) and re-imported later, preserving elevation data per point when present. An imported GPX route's own elevation is used automatically; it does not require the Elevation Simulation setting.

### 7. Road Speed-Limit Clamping & Elevation Simulation
Directions mode reads OSRM's own per-segment speed annotation and caps the simulated speed to it, so movement never exceeds what's plausible for the current road. **Elevation Simulation** (off by default, under Settings) adds altitude that changes gradually along a route, sourced from an imported GPX file when available or looked up from Open-Elevation otherwise.

### 8. Advanced Geocoding Infrastructure
Instead of relying on the restrictive and often rate-limited Android `Geocoder`, Spoofer utilizes the **Photon API** (backed by OpenStreetMap). This allows for rich Point of Interest (POI) searches. We apply a location-bias algorithm, meaning if your map is currently looking at New York, searching for "Starbucks" will prioritize results in New York rather than returning a Starbucks in London.

---

## 🏗 Software Architecture & Tech Stack

The codebase is structured around modern Android development standards to ensure maintainability, testability, and reactive UI updates.

- **UI Layer (Jetpack Compose):** 100% declarative UI. We utilize Material 3 components, Bottom Sheets, and custom composables (`LocationInputField`). State is strictly hoisted and collected from ViewModels.
- **Presentation Layer (MVVM):** `MapViewModel` manages the monolithic state of the map, location engines, and search results using Kotlin `StateFlow` and `viewModelScope` coroutines.
- **Dependency Injection (Hilt/Dagger):** All repositories, use cases, and location providers are scoped as singletons and injected via Hilt to ensure lifecycle safety across Foreground Services and Activities.
- **Data Layer (Room & Datastore):** Favorite locations are persisted using a Room SQLite database. User preferences (like dark mode and default speeds) are saved using Preferences DataStore.
- **Service Layer (Foreground Services):** Location injection happens inside a highly privileged Foreground Service (`MockLocationService`). This prevents the Android OS from killing the location loop when the app is minimized or the screen is turned off.

---

## 🚨 The Rubber-Banding Conundrum (Technical Challenge)

If you are an Android framework engineer, this section is for you. 

**Rubber-Banding** is the most notorious problem in location spoofing. Modern Android devices do not just use satellite GPS; they use the **Fused Location Provider (FLP)**. FLP aggressively scans local Wi-Fi MAC addresses, Bluetooth beacons, and cellular towers to triangulate your position. 

When you spoof the GPS, FLP detects a massive discrepancy between your mocked satellite location and the physical Wi-Fi routers around you. FLP often decides the Mock Location is "wrong" and forcefully snaps the device's location back to your real physical location for a split second, causing the marker to bounce rapidly back and forth (Rubber-Banding).

### Our 4-Pronged Mitigation Strategy
To suppress FLP and force it to accept our mock coordinates on strict operating systems like Android 14 and 15, we have implemented an extreme override loop in `MockLocationProvider.kt`:

1. **Mocking the Undocumented `fused` Provider:**
   By default, developers only mock `LocationManager.GPS_PROVIDER`. We explicitly inject mock locations into the hidden `"fused"` test provider as well. This injects our fake coordinates directly into the pipeline Google Play Services uses.
2. **Hyper-Aggressive 5Hz Tick Spamming:**
   Real hardware GPS chips update at 1Hz (1 update per second). Our coroutine loop fires at **5Hz (every 200ms)**. By flooding the `LocationManager` with 5 times the volume of data, we mathematically drown out the real hardware updates.
3. **Artificial Perfect Accuracy:**
   FLP evaluates incoming location data and trusts the source with the lowest error margin. We hardcode our mock location accuracy to `1.0f` (1 meter). Because real GPS is usually 3m-10m accurate, the FLP algorithm looks at our fake signal, deems it "mathematically superior," and prioritizes it.
4. **API 31+ Anti-Tamper Bypasses:**
   Android 15 requires strict flags. We utilize Java Reflection to invoke the hidden `Location.makeComplete()` method and explicitly set the `isMock = true` boolean to prevent the OS from discarding our packets as malformed.

### 🆘 We Need Your Help!
Despite this incredibly aggressive mitigation architecture, **micro-jumps still occasionally occur**. If the device detects a massive influx of strong Wi-Fi signals, FLP will still momentarily overpower our 5Hz spam. 

**We are actively seeking pull requests and architectural advice from Android security or framework experts to help us achieve a 100% airtight, zero-jump spoofing pipeline without requiring Magisk/Root.**

---

## ⚙️ Installation & Setup Guide

Since this app interfaces with system-level Developer Options, installation requires a few extra steps.

### Prerequisites
- A physical Android device or Emulator running Android 8.0 (API 26) or higher.
- Android Studio Ladybug (or newer).
- JDK 17.

### Build Instructions
1. **Clone the Source:**
   ```bash
   git clone https://github.com/yourusername/spoofer.git
   cd spoofer
   ```
2. **Get a Google Maps API key (required for the map background):**
   The map tiles are rendered by the Google Maps SDK for Android, which requires
   an API key that is **not** included in this repository (it's a secret tied to
   your own Google account and billing).
   1. In [Google Cloud Console](https://console.cloud.google.com/), create or select a project.
   2. Enable **"Maps SDK for Android"** for that project.
   3. Create an API key, and restrict it to this app's package name
      (`com.spoofer`) and your signing certificate's SHA-1 fingerprint (your
      debug keystore's fingerprint is enough for local development — get it
      with `./gradlew signingReport`).
   4. Create (or edit) `local.properties` in the project root — it's already
      gitignored, so your key never gets committed — and add:
      ```properties
      MAPS_API_KEY=your_key_here
      ```
   Without this step the app still builds and runs, but the map background
   will be blank (the Maps SDK silently refuses to render tiles with a missing key).

   > **Don't use Google's ["demo" API key](https://developers.google.com/maps/demo-key).**
   > It's a shared key on a Google-owned project, scoped to web APIs only (Maps
   > JavaScript API / web services) for prototyping — it has no Android SDK
   > support and can't be restricted to your app's package name and SHA-1, so
   > it fails with an "Authorization failure" in logcat. You need a real key
   > created under your own Google Cloud project (billing enabled, but normal
   > dev/testing usage stays within the recurring free monthly credit),
   > restricted to `com.spoofer` + your signing certificate's SHA-1 as
   > described above.
3. **Open in Android Studio:**
   Allow Gradle to sync the dependencies.
4. **Compile the APK:**
   Click the **Run** button, or build via terminal:
   ```bash
   ./gradlew assembleDebug
   ```
5. **Install to Device:**
   Ensure your device is connected via ADB and install the generated APK.

### Enabling Mock Locations (Crucial Step)
The app will not work unless you grant it Mock Location authority at the OS level.
1. Open your phone's **Settings**.
2. Scroll to **About Phone** -> **Software Information**.
3. Tap **Build Number** rapidly 7 times to unlock Developer Options.
4. Go back to the main Settings menu and open **Developer Options**.
5. Scroll down to the **Debugging** section.
6. Tap **Select mock location app** and choose **Spoofer** from the list.
7. *(Optional but Highly Recommended)*: Go to Settings -> Location -> Location Services and turn **OFF "Google Location Accuracy"** (Wi-Fi/Bluetooth scanning). This severely cripples Rubber-Banding.

### Using PC Receiver Mode
1. In the app, go to **Settings → Location Source** and enable **PC Receiver Mode**. This hides the Static/Directions/Joystick tabs and replaces them with a connection-status panel.
2. With the device connected over ADB, forward the listener port:
   ```bash
   adb forward tcp:8765 tcp:8765
   ```
3. Tap **Start spoofing** in the app — the panel should read "Waiting for PC…".
4. Send newline-delimited JSON to `127.0.0.1:8765` on the PC:
   ```json
   {"Action":"SendPosition","data":{"Lat":"37.775000","Lng":"-122.419000","Type":"android"}}
   ```
   The app applies the same jitter as Static mode and updates the mock location on each message.

---

## 🐛 Known Bugs & Limitations

- **Rubber-Banding:** As detailed above, micro-jumps back to the real hardware location still occasionally occur on devices with aggressive Wi-Fi scanning enabled.
- **Altitude/Elevation Simulation:** Optional and off by default (see Settings). An imported GPX file's own elevation data is used automatically when present; otherwise, enabling the "Elevation Simulation" setting looks it up from Open-Elevation, a free third-party service whose uptime isn't guaranteed. Static and Joystick modes still report altitude `0.0`.
- **Directions Route Limitations:** If an excessively long route (e.g., cross-country) is selected, the OSRM polyline response may be too large to parse efficiently on the main thread, causing temporary UI freezes.

---

## 🤝 Contributing & Community Help

We believe in the power of open-source collaboration. We are actively looking for contributors to help build new features, refine the Material 3 UI, and conquer the rubber-banding issue.

Please read our [**CONTRIBUTING.md**](CONTRIBUTING.md) for full details on how you can get involved, our code of conduct, and our pull request pipeline.

1. **Fork** the repository.
2. **Clone** your fork locally.
3. **Create a branch** for your feature or bugfix (`git checkout -b feature/advanced-physics`).
4. **Commit** your changes (`git commit -m 'Implement advanced physics model'`).
5. **Push** to your fork (`git push origin feature/advanced-physics`).
6. Open a **Pull Request** on this repository.

---

## 📜 License

This project is licensed under the **MIT License**. See the [LICENSE](LICENSE) file for complete details. You are free to modify, distribute, and use this code commercially and privately, provided proper attribution is given.
