# WiFi Signal Mapper

## Overview
This is an Android application developed in Kotlin for the "Mobile Computing" course, Assignment 3. The app logs the received signal strength (RSS) of WiFi Access Points (APs) at different locations, storing 100 samples per location in a matrix format. It identifies at least three distinct locations and displays the RSS range across them, as per the assignment requirements. The app uses Room for local data storage and Google’s FusedLocationProvider for location services. This README documents the implementation details and how the assignment questions have been addressed.

## GitHub Repository
- **Link**: [https://github.com/sameer-singh-godara/WiFi-Signal-Mapper](https://github.com/sameer-singh-godara/WiFi-Signal-Mapper.git)
- The repository is private; please ensure access is granted to the instructor via GitHub for evaluation.

## Implementation Details

### How I Solved the Assignment

#### Question: Write an App that Logs WiFi Signal Strength
The assignment required an app that logs RSS of WiFi APs as a matrix of 100 elements per location, identifies at least three locations, and shows the RSS range differences. The following criteria were addressed:

- **Creation of App Interface (10 marks)**:
  - Designed `activity_main.xml` with a `ConstraintLayout` containing a `TextView` for status updates, a `RecyclerView` for real-time AP display, and buttons (`startStopButton`, `viewResultsButton`). The `activity_results.xml` uses a `RecyclerView` to show logged data per location with a `clearDatabaseButton`. The UI is intuitive, with `CardView` layouts (`item_ap_card.xml`, `item_result_card.xml`) for APs and results, ensuring readability and responsiveness.
- **Ability to Log the Data (15 marks)**:
  - Implemented data logging in `MainActivity.kt` using a Room database (`AppDatabase.kt`, `WiFiDao.kt`, `WiFiData.kt`). The app collects 100 RSS samples per location during scanning, storing each sample with BSSID, SSID, RSSI, timestamp, location (latitude/longitude), and user-defined location name. Data is saved asynchronously using Kotlin Coroutines, with error handling for WiFi and location service issues.
- **Showing the Data in the Demo from Three Locations (15 marks)**:
  - The `ResultsActivity.kt` displays data for distinct locations using a `RecyclerView`. Each location’s card shows the location name, coordinates, total APs, average RSSI, and RSSI range (min to max). Detailed AP data (BSSID, average RSSI, range, sample count) is shown per location. The app prompts users to name locations, ensuring at least three unique locations can be logged and compared, with metrics calculated dynamically.

### How to Run
1. **Enable WiFi and Location**: Turn on WiFi and location services on your Android device.
2. **Enter Location Name**: Launch the app and, when prompted, enter a name for the current location (or it defaults to "Unknown Location").
3. **Start Scanning**: Press the "Start Scanning" button to collect 100 RSS samples for the current location. The app displays real-time AP data during scanning.
4. **Save Results**: After scanning, results are saved under the specified location name in the database.
5. **Revisit Location**: If you return to a previously scanned location (based on coordinates), the app recognizes it and displays the stored location name.
6. **Append Scans**: Scanning again at the same location appends new samples to the existing data for that location.
7. **View Results**: Press the "View Results" button to see logged data for all locations, including RSSI metrics and ranges.

### Files
- **Kotlin**:
  - `MainActivity.kt`: Manages the scanning UI, handles permissions, collects RSS samples, and updates the database.
  - `ResultsActivity.kt`: Displays logged data for multiple locations with RSS metrics.
  - `WiFiDao.kt`: Defines Room DAO for database operations.
  - `WiFiData.kt`: Defines the Room entity for WiFi data.
  - `AppDatabase.kt`: Configures the Room database with migrations.
- **XML**:
  - `activity_main.xml`: Main UI layout for scanning and AP display.
  - `activity_results.xml`: Layout for displaying logged results.
  - `item_ap_card.xml`: Card layout for real-time AP data.
  - `item_result_card.xml`: Card layout for location-based results.
  - `item_ap_detail.xml`: Layout for detailed AP data in results.
- **Gradle**:
  - Configures dependencies for Room, Coroutines, and Google Location Services.

### Limitations
- Nearby locations may not be distinguished, as coordinates are rounded to the third decimal place for matching.
- Maximum of 100 samples per location at a time, as per assignment requirements.
- Requires WiFi and location services to be enabled, with appropriate permissions granted.
- Scanning may be affected by device-specific WiFi scan throttling.

### Future Work
- Add offline caching for scan results to handle network disruptions.
- Implement a map view to visualize location-based data.
- Add unit tests for database operations and UI components.

### Submission
- **Files Uploaded**: Kotlin sources (`MainActivity.kt`, `ResultsActivity.kt`, `WiFiDao.kt`, `WiFiData.kt`, `AppDatabase.kt`), XML layouts (`activity_main.xml`, `activity_results.xml`, `item_ap_card.xml`, `item_ap_detail.xml`, `item_result_card.xml`), and this README are committed to the GitHub repository.
- **Submission Method**: Uploaded to both the Google Classroom and the private GitHub repository [https://github.com/sameer-singh-godara/WiFi-Signal-Mapper](https://github.com/sameer-singh-godara/WiFi-Signal-Mapper.git).