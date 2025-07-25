<div align="center">
  <img src="/assets/logo.png" alt="Terravue Logo" width="300">
</div>
<p align="center">Terravue is an environmental metrics visualizer for Android applications and services.</p>

---

## Features
- Environmental impact tracking for installed Android apps
- Real-time carbon footprint calculations
- Eco-friendly app recommendations
- Offline-first data storage with GitHub integration

## Development
1. Clone the repository:
   ```bash
   git clone https://github.com/TerraVueDev/android.git
   cd android
   ```
2. Open in Android Studio:
   - Launch Android Studio
   - Select "Open an existing project"
   - Navigate to the cloned directory

3. Set up API key in `gradle.properties`:
   ```properties
   GOOGLE_AI_API_KEY=your_google_ai_api_key_here
   ```

4. Build and run:
   ```bash
   ./gradlew build
   ./gradlew installDebug
   ```

5. The app requires Android 7.0 (API 24) or higher.

## Contributing
Contributions are welcome! Please open issues or submit pull requests.