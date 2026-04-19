# SaveFrom-Android 🎞️⬇️

[![Build Status](https://img.shields.io/badge/Build-Passing-brightgreen.svg)](https://github.com/mvxGREEN/SaveFrom-Android/actions)
[![License: WTFPL](https://img.shields.io/badge/License-WTFPL-brightgreen.svg)](http://www.wtfpl.net/about/)
[![Language: Kotlin](https://img.shields.io/badge/Language-Kotlin-purple.svg)](https://kotlinlang.org/)
[![Platform: Android](https://img.shields.io/badge/Platform-Android-3DDC84.svg)](https://developer.android.com/)

Universal URL-to-MP4 video downloader app for Android.  

Powered by an embedded `yt-dlp` Python package.

## ✨ Features
* **Universal Downloader**: Capable of downloading videos from a vast array of supported platforms (YouTube, TikTok, and hundreds more) directly to your Android device.
* **Powered by `yt-dlp`**: Utilizes the robust and constantly updated `yt-dlp` library to handle complex extractions and varying video platform architectures.
* **Chaquopy Integration**: Seamlessly runs Python scripts natively within the Android app environment using the Chaquopy SDK.
* **Kotlin-Centric**: The Android UI and core app architecture are built primarily in Kotlin for a modern, safe, and efficient mobile experience.

## 🛠 Tech Stack
* **Language**: Kotlin (App Logic & UI), Python (Extraction Engine), Java
* **Platform**: Android SDK
* **Python SDK**: [chaquopy](https://chaquo.com/chaquopy/)
* **Core Library**: [yt-dlp](https://github.com/yt-dlp/yt-dlp)

## 🚀 Getting Started

To get a local copy up and running, follow these simple steps.

### Prerequisites
* **Android Studio**: Make sure you have the latest version of [Android Studio](https://developer.android.com/studio) installed.
* **NDK & CMake**: Because Chaquopy builds native Python components, ensure you have the Android NDK and CMake installed via the Android Studio SDK Manager.

### Installation & Build

1. **Clone the repository**
    `git clone https://github.com/mvxGREEN/SaveFrom-Android.git`

2. **Open the project in Android Studio**
   * Launch Android Studio.
   * Select **Open an existing Android Studio project**.
   * Navigate to the cloned `SaveFrom-Android` directory and click **OK**.

3. **Sync Gradle**
   * Wait for Android Studio to index the files and sync the Gradle dependencies. 
   * *Note: Chaquopy might require an initial download of Python binaries for Android, so this sync might take a little longer than usual on the first run.*

4. **Run the App**
   * Connect an Android device via USB (with USB Debugging enabled) or start an Android Emulator.
   * Click the **Run** button (green play icon) in the Android Studio toolbar.

## 💡 Usage

1. Open the SaveFrom app on your Android device.
2. Find a video on a supported platform (like YouTube, TikTok, etc.) and copy its URL.
3. Paste the URL into the app's input field.
4. Tap the download button. The app will use `yt-dlp` in the background to fetch the best available MP4 and save it directly to your device storage.

## 🤝 Contributing
Contributions, issues, and feature requests are welcome! 
Feel free to check the [issues page](https://github.com/mvxGREEN/SaveFrom-Android/issues) if you want to contribute. 

1. Fork the Project
2. Create your Feature Branch (`git checkout -b feature/AmazingFeature`)
3. Commit your Changes (`git commit -m 'Add some AmazingFeature'`)
4. Push to the Branch (`git push origin feature/AmazingFeature`)
5. Open a Pull Request

## 📄 License
This project is licensed under the **WTFPL** (Do What The F*ck You Want To Public License) - see the [LICENSE.txt](LICENSE.txt) file for details.
