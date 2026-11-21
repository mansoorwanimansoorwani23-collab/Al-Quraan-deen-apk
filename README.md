<div align="center">

# 📖 Al-Quraan Deen APK

**A clean, simple Android app for reading the Holy Quran**

![Platform](https://img.shields.io/badge/Platform-Android-3DDC84?logo=android&logoColor=white)
![Language](https://img.shields.io/badge/Kotlin-Gradle%20KTS-7F52FF?logo=kotlin&logoColor=white)
![Build](https://img.shields.io/badge/Build-GitHub%20Actions-2088FF?logo=githubactions&logoColor=white)
![Status](https://img.shields.io/badge/Status-In%20Development-orange)

*بسم الله الرحمن الرحيم*

</div>

---

## ✨ About

**Al-Quraan Deen** is an Android application built to make the Quran easy to read and access on your phone. It focuses on a simple, distraction-free experience so you can concentrate on what matters most — the words of Allah.

## 🌟 Features

- 📚 Read the Holy Quran Surah by Surah
- 🔍 Quick search for Surahs and verses
- 🔖 Bookmark your favourite verses
- 🌙 Light / Dark mode
- 🔠 Adjustable font size for comfortable reading
- 📱 Lightweight and easy to use

> 🛠️ *More features are being added. Contributions and suggestions are welcome!*

## 📸 Screenshots

| Home | Reading | Settings |
|:----:|:-------:|:--------:|
| *coming soon* | *coming soon* | *coming soon* |

> Add your screenshots to `docs/screenshots/` and link them here.

## 🧰 Tech Stack

| Category | Details |
|----------|---------|
| Platform | Android |
| Build System | Gradle (Kotlin DSL) |
| CI/CD | GitHub Actions |
| Config | `.env` for API keys / secrets |

## 📂 Project Structure

```
Al-Quraan-deen-apk/
├── .github/workflows/   # CI workflow for building the APK
├── app/                 # Main Android application module
├── gradle/              # Gradle wrapper & version catalog
├── public/assets/       # Static assets
├── .env.example         # Example environment variables
├── build.gradle.kts     # Root build config
├── settings.gradle.kts  # Gradle settings
└── metadata.json        # Project metadata
```

## 🚀 Getting Started

### Prerequisites

- [Android Studio](https://developer.android.com/studio) (latest stable)
- JDK 17 or higher
- Android SDK installed via Android Studio

### Installation

1. **Clone the repository**
   ```bash
   git clone https://github.com/mansoorwanimansoorwani23-collab/Al-Quraan-deen-apk.git
   cd Al-Quraan-deen-apk
   ```

2. **Set up environment variables**
   ```bash
   cp .env.example .env
   ```
   Open `.env` and fill in the required values.

3. **Open in Android Studio**
   `File → Open` → select the project folder → wait for Gradle sync to finish.

4. **Run the app**
   Connect a device or start an emulator, then click ▶️ **Run**.

### Build APK from the command line

```bash
# Debug APK
./gradlew assembleDebug

# Release APK
./gradlew assembleRelease
```

The generated APK will be at:

```
app/build/outputs/apk/
```

## ⚙️ GitHub Actions

Every push can automatically build the APK through the workflow in
`.github/workflows/`. After a successful run, download the APK from the
**Actions → latest run → Artifacts** section.

## 📥 Download

Get the latest APK from the [**Releases**](../../releases) page.

> ⚠️ To install, allow *"Install from unknown sources"* on your device.

## 🤝 Contributing

Contributions are welcome!

1. Fork the repository
2. Create a new branch: `git checkout -b feature/your-feature`
3. Commit your changes: `git commit -m "Add your feature"`
4. Push to the branch: `git push origin feature/your-feature`
5. Open a Pull Request

Found a bug or have an idea? Please open an [Issue](../../issues).

## 🔒 Privacy

This app respects your privacy. No unnecessary personal data is collected.

## 📜 License

Add a license of your choice (e.g. [MIT](https://choosealicense.com/licenses/mit/)) by creating a `LICENSE` file in the repository root.

## 🙏 Acknowledgements

- The Quran text and translations belong to their respective sources — please give proper credit to the data providers you use.
- Thanks to everyone who contributes and shares this project.

---

<div align="center">

**Made with ❤️ by [mansoorwanimansoorwani23-collab](https://github.com/mansoorwanimansoorwani23-collab)**

*جزاك الله خيراً — If you find this useful, please ⭐ the repo!*

</div>
