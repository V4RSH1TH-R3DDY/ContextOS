# Gemini API Setup Guide

This guide walks through creating a proper Google Cloud Project for ContextOS to use the Gemini API.

## Step 1: Create a Google Cloud Project

1. Go to [Google Cloud Console](https://console.cloud.google.com/)
2. Click the project dropdown at the top
3. Click **NEW PROJECT**
4. Enter a project name (e.g., "ContextOS")
5. Click **CREATE**
6. Wait for the project to be created, then select it

## Step 2: Enable the Generative Language API

1. In the Cloud Console, go to **APIs & Services** > **Library**
2. Search for **"Generative Language API"**
3. Click on it
4. Click **ENABLE**
5. Wait for it to enable (this may take a minute)

## Step 3: Create an API Key

1. Go to **APIs & Services** > **Credentials**
2. Click **+ CREATE CREDENTIALS** > **API Key**
3. A dialog will appear with your new API key
4. **Copy the API key** (you'll use this next)
5. *(Optional)* Click **RESTRICT KEY** to limit it to the Generative Language API only

## Step 4: Update local.properties

Replace your current Gemini API key in `local.properties`:

```properties
sdk.dir=/home/varshith/Android/Sdk
MAPS_API_KEY=AIzaSyDEyJW1vIWEEBIwdQZkVIfdFxwzE89cI2Y
OPENCLAW_API_KEY=AIzaSy<YOUR_NEW_KEY_HERE>
```

## Step 5: Rebuild and Test

```bash
cd /home/varshith/ContextOS
./gradlew clean assembleDebug
adb install -r app/build/outputs/apk/debug/app-debug.apk
```

## Troubleshooting

### "API not enabled"

- Go back to **APIs & Services** > **Library**
- Search for "Generative Language API"
- Make sure it shows **MANAGE** (not **ENABLE**)

### "Invalid API key"

- Copy the key again from the Credentials page
- Make sure there are no spaces or extra characters
- Try rebuilding with `./gradlew clean`

### Rate Limiting

- Google Cloud projects have higher rate limits than AI Studio
- Default quota: 60 requests per minute per API key

### Verify It's Working

Check logcat for successful API calls:

```bash
adb logcat | grep GeminiApiClient
```

Look for: `POST https://generativelanguage.googleapis.com/v1beta/models/... (model=gemini-2.0-flash, turns=...)`

## Security Notes

⚠️ **Important**: `local.properties` is development-only and NOT committed to git (it's in `.gitignore`). Never expose your API key publicly. For production, use a secure backend proxy or environment variable injection.
