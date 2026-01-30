# HHSampleAppAI

An Android application demonstrating the integration of Zebra RFID and Scanner SDKs. This sample app allows users to connect to Zebra RFID readers (like the RFD40) via USB or Bluetooth, perform RFID tag inventory, and scan barcodes.

## Features

- **RFID Inventory**: Connect to a Zebra RFID reader and perform real-time inventory of EPC tags.
- **Barcode Scanning**: Trigger and receive barcode data from the integrated or connected Zebra scanners.
- **Background Processing**: SDK operations are offloaded to background threads to ensure a smooth UI experience.
- **Dynamic Connection**: Supports automatic and manual connection toggling for RFID readers.
- **Unique Tag Tracking**: Tracks and displays the count of unique tags discovered during a session.
- **Permission Handling**: Fully compatible with Android 12+ Bluetooth permission requirements.

## Prerequisites

- Android Studio Arctic Fox or newer.
- Zebra RFID SDK for Android.
- Zebra Scanner Control SDK for Android.
- A compatible Zebra RFID Reader (e.g., RFD40, RFD8500).

## Getting Started

1. **Clone the repository**:
   ```sh
   git clone https://github.com/GelatoCookie/AI_Java_SDKSample.git
   ```
2. **Open in Android Studio**: Import the project as a Gradle project.
3. **Build and Run**: Connect your Android device and run the `app` module.

## Usage

1. **Connect**: Tap the status text at the top to toggle the connection to your Zebra reader.
2. **Inventory**: Use the **Start** and **Stop** buttons to control RFID tag reading.
3. **Barcode**: Use the **Scan** button to trigger the barcode scanner.
4. **Settings**: Use the overflow menu to access antenna settings or reset to defaults.

## Project Structure

- `MainActivity.java`: Handles the UI logic and user interactions.
- `RFIDHandler.java`: Encapsulates the Zebra RFID API and Scanner SDK logic.
- `ScannerHandler.java`: Delegate for handling Scanner SDK events.

## License

This project is for demonstration purposes. Refer to Zebra's SDK license agreements for production use.
