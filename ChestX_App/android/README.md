# Chest X-Ray Classification Android App

Android application for chest X-ray disease classification using Jetpack Compose and Material 3.

## Features

- Upload X-ray images from gallery or camera
- CNN-powered disease classification
- Real-time analytics dashboard
- Scan history tracking
- Modern Material 3 design
- Heatmap visualization
- PDF report generation
- App Lock
- Per Class AUC Performance of Model
- More Details Option of Per Finding
- Update Notification System

## Tech Stack

- **Language:** Kotlin
- **UI Framework:** Jetpack Compose
- **Design System:** Material 3
- **Architecture:** MVVM with ViewModels
- **Image Loading:** Coil
- **Navigation:** Jetpack Navigation Compose
- **Async:** Kotlin Coroutines + Flow

## Prerequisites

- Android Studio Hedgehog (2023.1.1) or newer
- JDK 17 or higher
- Android SDK 34
- Minimum Android SDK 26 (Android 8.0)
- Backend API running (see backend README)

## Setup Instructions

### 1. Open Project

1. Open Android Studio
2. Select "Open an Existing Project"
3. Navigate to the `android` folder
4. Click "OK"


### 2. Sync Gradle

- Click "Sync Now" when prompted
- Wait for dependencies to download

### 3. Run the App

1. Connect an Android device or start an emulator
2. Click "Run" (▶️) in Android Studio
3. Select your device
4. Wait for the app to build and install

## Project Structure

```
android/app/src/main/java/com/medical/chestxray/
├── data/
│   ├── api/
│   │   ├── ApiService.kt          # API endpoints
│   │   └── RetrofitClient.kt      # Retrofit configuration
│   └── model/
│       └── Models.kt               # Data models
├── ui/
│   ├── navigation/
│   │   └── AppNavigation.kt       # Navigation graph
│   ├── screens/
│   │   ├── HomeScreen.kt          # Home/Upload screen
│   │   ├── AnalysisResultScreen.kt # Results display
│   │   ├── DashboardScreen.kt     # Statistics dashboard
│   │   ├── HistoryScreen.kt       # Scan history
│   │   ├── AnalyticsScreen.kt     # Analytics
│   │   └── SettingsScreen.kt      # Settings
│   ├── theme/
│   │   ├── Color.kt               # Color palette
│   │   ├── Theme.kt               # Material theme
│   │   └── Type.kt                # Typography
│   └── viewmodel/
│       ├── AnalysisViewModel.kt   # Analysis logic
│       └── DashboardViewModel.kt  # Dashboard logic
└── MainActivity.kt
```

## Key Components

### Screens

1. **Home Screen**: Upload X-ray images from camera or gallery
2. **Analysis Result Screen**: Display disease predictions with confidence scores
3. **Dashboard Screen**: Show statistics and scan frequency charts
4. **History Screen**: List previous scans
5. **Analytics Screen**: Detailed analytics (placeholder)
6. **Settings Screen**: App settings (placeholder)

### ViewModels

- **AnalysisViewModel**: Manages image analysis and API communication
- **DashboardViewModel**: Loads and manages dashboard statistics


## Permissions

The app requires the following permissions:

- `INTERNET`: Network communication with backend
- `CAMERA`: Take photos of X-rays
- `READ_MEDIA_IMAGES`: Access images from gallery (Android 13+)
- `READ_EXTERNAL_STORAGE`: Access images from gallery (Android 12 and below)

## Build Variants

### Debug Build

```bash
./gradlew assembleDebug
```

APK location: `app/build/outputs/apk/debug/app-debug.apk`

### Release Build

1. Create keystore:

```bash
keytool -genkey -v -keystore chest-xray.keystore -alias chest-xray -keyalg RSA -keysize 2048 -validity 10000
```

2. Configure signing in `app/build.gradle.kts`:

```kotlin
android {
    signingConfigs {
        create("release") {
            storeFile = file("chest-xray.keystore")
            storePassword = "your_password"
            keyAlias = "chest-xray"
            keyPassword = "your_password"
        }
    }
    buildTypes {
        release {
            signingConfig = signingConfigs.getByName("release")
            isMinifyEnabled = true
            proguardFiles(...)
        }
    }
}
```

3. Build release APK:

```bash
./gradlew assembleRelease
```

## Testing

### Run Unit Tests

```bash
./gradlew test
```

### Run Instrumented Tests

```bash
./gradlew connectedAndroidTest
```

## Customization

### Change Theme Colors

Edit `ui/theme/Color.kt`:

```kotlin
val OrangePrimary = Color(0xFFFF6B35)
val OrangeSecondary = Color(0xFFFF8C42)
```

### Add New Screens

1. Create screen composable in `ui/screens/`
2. Add route to `AppNavigation.kt`
3. Update navigation graph

### Modify Disease Categories

Update the API response models in `data/model/Models.kt` to match your backend.

## Troubleshooting

### Issue: Image upload fails

**Solution:**
- Check file size limits
- Verify permissions are granted
- Check backend logs for errors
- Ensure image format is supported (JPEG, PNG)

### Issue: Build fails

**Solution:**
- Clean and rebuild: `Build > Clean Project` then `Build > Rebuild Project`
- Invalidate caches: `File > Invalidate Caches > Invalidate and Restart`
- Check JDK version (must be 17+)
- Update Gradle: `./gradlew wrapper --gradle-version=8.2`

### Issue: App crashes on startup

**Solution:**
- Check Logcat for error messages
- Verify minimum SDK version
- Ensure all dependencies are synced
- Check for ProGuard issues in release builds

## Performance Tips

- Use release builds for better performance
- Enable R8 code shrinking
- Optimize image loading with Coil
- Use LazyColumn for large lists
- Implement pagination for history

## Accessibility

The app follows Material Design accessibility guidelines:

- Adequate color contrast ratios
- Touch target sizes (48dp minimum)
- Content descriptions for icons
- Support for screen readers
- Scalable text

## Future Enhancements

- [ ] Offline mode with local database
- [ ] Multi-language support
- [ ] Pre-analysis image sanity check
- [ ] TTA-based confidence agreement
- [ ] Live Framing Guide
- [ ] Haptic Feedback
- [ ] Dynamic Colour
- [ ] Local-only diagnostic log viewer

## Contributing

1. Fork the repository
2. Create feature branch: `git checkout -b feature/amazing-feature`
3. Commit changes: `git commit -m 'Add amazing feature'`
4. Push to branch: `git push origin feature/amazing-feature`
5. Open Pull Request

## License

MIT License

## Author

- Arghadeep Pakhira: `Sister Nivedita University`
- Dr. Bidyut Saha: `Sister Nivedita University`
