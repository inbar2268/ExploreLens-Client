# ExploreLens:

## Overview

ExploreLens is a smart, AR-powered mobile app that transforms your phone into an interactive tour guide.
Just point your camera to discover nearby landmarks and cultural sites, highlighted with clickable AR icons. Tap any icon to instantly access detailed information—including historical background, ratings, and real user reviews.

Simply explore your surroundings with ease:

🏛️ Discover monuments, buildings, and hidden gems with contextual AR overlays

🍽️ Explore nearby hotels, restaurants, bars, and attractions directly through the camera view

🤖 Chat with an AI-powered guide for personalized recommendations, insider tips, and detailed explanations

Whether you're on a planned city tour or a spontaneous walk, ExploreLens lets you uncover hidden stories, gain local insights, and connect with the world around you — all through your camera.

## Prerequisites

- Android device with ARCore support (check [ARCore supported devices](https://developers.google.com/ar/devices))
- Google Cloud Platform account
- OpenAI account
- Android development environment

## Setup Guide

### 1. Create OAuth 2.0 Client ID

This step is crucial for authenticating with Google services, such as Google Sign-In.

1. Navigate to the [Google Cloud Console](https://console.cloud.google.com/)
2. Select your existing project or create a new one
3. Go to **APIs & Services > Credentials**
4. Click **"+ CREATE CREDENTIALS"** and choose **"OAuth client ID"**
5. Select **Android** as the application type
6. Provide your app's package name (e.g., `com.example.explorelens`) and your SHA-1 certificate fingerprint

You can obtain your SHA-1 fingerprint by running the following command in your terminal from your project's root directory:

```bash
./gradlew signingReport
```
In the output, look for a section similar to this, and copy the SHA1 value:
```bash
Variant: debug
Config: debug
Store: /home/username/.android/debug.keystore
Alias: AndroidDebugKey
SHA1: A1:B2:C3:D4:E5:F6:78:90:AB:CD:EF:12:34:56:78:90:12:34:56:78
```

7. Once created, Google will provide you with a Client ID string

### 2. Create and Configure Google API Key

An API key is essential for your app to access various Google services like Maps and Places.

1. Go to the [Google Cloud Console](https://console.cloud.google.com/)
2. Select your project
3. Navigate to **APIs & Services > Credentials**
4. Click **"+ CREATE CREDENTIALS"** and select **"API Key"**
5. After generation, click **"Restrict Key"** to enhance security

#### Application Restrictions
- Under **"Application restrictions"**, select **"Android apps"**
- Click **"+ Add package name and fingerprint"**
- Enter your app's package name and SHA-1 certificate fingerprint

#### API Restrictions
- Under **"API restrictions"**, select **"Restrict key"**
- Select the following APIs from the dropdown:
    - Places API (New)
    - Places API
    - Maps SDK for Android
    - ARCore API

6. Click **"OK"** then **"Save"** to apply changes

### 3. Enable Required Google APIs

Before your app can use Google services, enable the necessary APIs:

1. Visit the [Google Cloud Console API Library](https://console.cloud.google.com/apis/library)
2. Confirm your project is selected
3. Search for and enable the following APIs:
    - **Places API (New)**
    - **Places API**
    - **Maps SDK for Android**
    - **ARCore API**
    - **Maps Elevation API**

### 4. Create OpenAI API Key

To leverage ExploreLens's AI-powered features:

1. Go to the [OpenAI API Keys page](https://platform.openai.com/api-keys)
2. Sign in to your OpenAI account (create one if needed)
3. Click **"+ Create new secret key"**
4. Optionally, give your key a descriptive name
5. Click **"Create secret key"**

> **⚠️ CRITICAL:** Copy your secret key immediately. This is your only opportunity to view and copy it.

### 5. Configure local.properties

Add the following configuration values to your Android project's `local.properties` file (located in the root of your Android project):

```properties
# Google OAuth Client ID for Android authentication
WEB_CLIENT_ID="your_google_web_client_id_here"

# Google Maps, Places, and AR API Key
GOOGLE_API_KEY="your_google_api_key_here"

# Base URL for the ExploreLens backend server
BASE_URL=https://explorelensserver.cs.colman.ac.il

# OpenAI API Key for AI services
openai.api.key="your_openai_api_key_here"
```

Replace the placeholder values with your actual credentials:
- `your_google_web_client_id_here` → Client ID from Step 1
- `your_google_api_key_here` → API Key from Step 2
- `your_openai_api_key_here` → OpenAI API key from Step 4

### 6. Configure gradle.properties

You can use a flag to switch between live and mock data for development or testing. Add this line to your `gradle.properties` file:

```properties
# Set to 'true' to use mock data for development/testing
USE_MOCK_DATA=true
```

Set to `true` for mock data or `false` for real-time data from the backend server.

### 7. Google Play Services for AR (ARCore) Setup

ExploreLens leverages ARCore for augmented reality functionalities.

#### Check Device Compatibility
Not all Android devices support ARCore. Verify compatibility by checking the [official ARCore supported devices list](https://developers.google.com/ar/devices).

#### Install Google Play Services for AR
- Usually installed automatically when launching an ARCore-enabled app
- Can be manually installed from the [Google Play Store](https://play.google.com/store/apps/details?id=com.google.ar.core) if needed

#### Geospatial API Support
The Geospatial API enables advanced AR experiences using real-world geographic coordinates. Note that not all Android phones support this API.

If a device doesn't support the Geospatial API, the application will display: **"Geospatial API not supported."**

## Getting Started

1. Clone the repository
2. Follow the setup guide above to configure all API keys and credentials
3. Ensure your Android device supports ARCore
4. Grant camera and location permissions when prompted
5. Build and run the application