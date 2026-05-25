# BloodLink 🩸

**AI-powered pre-screening companion for blood donation drives in the Philippines**

![Android](https://img.shields.io/badge/Android-26%2B-green?logo=android)
![Kotlin](https://img.shields.io/badge/Kotlin-2.0-purple?logo=kotlin)
![TFLite](https://img.shields.io/badge/TensorFlow%20Lite-ML-orange?logo=tensorflow)
![Firebase](https://img.shields.io/badge/Firebase-Backend-yellow?logo=firebase)

---

## Overview

BloodLink is a mobile pre-screening tool designed for Philippine blood donation events. It uses smartphone camera-based image analysis to detect visual signs of **pallor (anemia risk)** and **jaundice** from the eye's conjunctiva and sclera — helping NGO staff quickly assess donor eligibility before laboratory testing.

> ⚠️ **Disclaimer:** BloodLink is a pre-screening aid and does not constitute a medical diagnosis. All results must be verified by qualified medical personnel.

---

## Screenshots

| Consent Screen | Screening Module | Result View | Staff Dashboard |
|:-:|:-:|:-:|:-:|
| ![Consent](docs/screenshots/consent.png) | ![Screening](docs/screenshots/screening.png) | ![Result](docs/screenshots/result.png) | ![Dashboard](docs/screenshots/dashboard.png) |

---

## Architecture

BloodLink follows a **5-layer architecture**:

```
┌─────────────────────────────────────────────┐
│  UI Layer          Jetpack Compose screens   │
├─────────────────────────────────────────────┤
│  State Layer       ViewModels + StateFlow    │
├─────────────────────────────────────────────┤
│  Domain Layer      Repositories + Engines    │
├─────────────────────────────────────────────┤
│  ML Layer          TFLite + ML Kit Face ROI  │
├─────────────────────────────────────────────┤
│  Data Layer        Firebase (Auth/Firestore) │
└─────────────────────────────────────────────┘
```

For detailed architecture documentation, see [`docs/architecture.md`](docs/architecture.md).

---

## Tech Stack

| Component | Technology |
|---|---|
| Language | Kotlin 2.0 |
| UI Framework | Jetpack Compose + Material 3 |
| ML Inference | TensorFlow Lite (MobileNetV2) |
| Face Detection | Google ML Kit Face Detection |
| Backend | Firebase Authentication + Cloud Firestore |
| Notifications | Firebase Cloud Messaging |
| Min SDK | Android 8.0 (API 26) |
| Target SDK | Android 15 (API 35) |

---

## Key Features

- **Camera-based screening** — Captures eye photos and runs on-device ML inference to detect pallor and jaundice
- **Image quality gate** — Rejects blurry, too dark, overexposed, or featureless images before inference runs
- **ROI extraction** — ML Kit detects the face and crops the lower eyelid (pallor) or sclera (jaundice) automatically
- **Informed consent** — Full Data Privacy Act (RA 10173) compliant consent screen at sign-up and event join
- **Session persistence** — Event session survives app kill / phone restart via SharedPreferences
- **Real-time event management** — Staff can create events, generate QR codes, and monitor applicant screenings
- **Health questionnaire** — Rule-based eligibility engine with automatic deferral logic

---

## Setup & Installation

### Prerequisites
- Android Studio Ladybug (2024.2.1) or newer
- JDK 17+
- A Firebase project with Authentication and Firestore enabled

### Steps

1. **Clone the repository**
   ```bash
   git clone https://github.com/YOUR_USERNAME/BloodLink.git
   cd BloodLink
   ```

2. **Add Firebase configuration**
   - Go to [Firebase Console](https://console.firebase.google.com/)
   - Download your `google-services.json`
   - Place it in `app/` directory
   - ⚠️ This file is excluded from version control via `.gitignore`

3. **Open in Android Studio**
   - File → Open → select the project root
   - Wait for Gradle sync to complete

4. **Run on device**
   - Connect your Android phone (USB debugging enabled)
   - Click **Run ▶** or `Shift + F10`

---

## ML Pipeline

The ML models are trained using Google Colab with MobileNetV2 transfer learning. See the [`ml/`](ml/) folder for:

- Training notebooks (pallor + jaundice)
- Dataset sources and preprocessing steps
- Instructions for retraining with new data

For details, see [`ml/README.md`](ml/README.md).

---

## Data Privacy

BloodLink complies with the **Data Privacy Act of 2012 (Republic Act No. 10173)**:

- No biometric images are stored or transmitted — only numerical scores
- Users provide explicit informed consent before account creation and event participation
- Data subject rights (access, correction, erasure) are documented and supported
- Full compliance details: [`docs/data-privacy.md`](docs/data-privacy.md)

---

## Project Structure

```
BloodLink/
├── app/src/main/
│   ├── java/                    # Kotlin source files
│   │   ├── Screens.kt           # All Compose UI screens
│   │   ├── ViewModels.kt        # State management
│   │   ├── Models.kt            # Data classes
│   │   ├── ImagePipeline.kt     # TFLite inference + quality gate
│   │   ├── PhysicalTestEngine.kt # Screening test modules
│   │   ├── MlKitFaceRoi.kt      # Face detection + ROI extraction
│   │   ├── ScreeningRepository.kt # Firestore CRUD for screenings
│   │   └── EventRepository.kt   # Firestore CRUD for events
│   ├── assets/                  # TFLite model files
│   └── res/                     # Android resources
├── ml/                          # Training notebooks + dataset docs
├── docs/                        # Architecture + privacy docs
└── README.md
```

---

## Contributors

| Name | Role |
|---|---|
| [Your Name] | Lead Developer, ML Pipeline |
| [Teammate 2] | [Role] |
| [Teammate 3] | [Role] |

---

## License

This project is developed as an undergraduate thesis at [Your University]. All rights reserved.

---

## Acknowledgments

- Dataset: [EYES-DEFY-ANEMIA](https://www.kaggle.com/datasets/harshwardhanfartale/eyes-defy-anemia) (Kaggle)
- ML Kit Face Detection by Google
- TensorFlow Lite by Google
