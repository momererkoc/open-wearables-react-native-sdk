# Open Wearables React Native SDK

The official React Native SDK for the [Open Wearables](https://github.com/the-momentum/open-wearables) project.

Built with the [Expo Module API](https://docs.expo.dev/modules/module-api/). Works in both Expo and bare React Native projects.

**This fork contains a fully self-contained implementation** — no external private native SDKs required. Android uses Health Connect directly; iOS uses HealthKit directly.

## Platform support

| Platform | Status | Implementation |
| -------- | ------ | -------------- |
| iOS      | ✅ Implemented | HealthKit (native, no external SDK) |
| Android  | ✅ Implemented | Health Connect (native, no external SDK) |

## Requirements

| Platform | Minimum version |
| -------- | --------------- |
| iOS      | 15.1+ |
| Android  | API 28+ (Android 9+) with Health Connect |
| React Native | 0.73+ |
| Expo     | SDK 51+ |

## Installation

### From this repo (local)

```sh
# In your project root:
npm install /path/to/open-wearables-react-native-sdk
```

### Expo

```sh
npx expo run:ios
npx expo run:android
```

### React Native CLI

Ensure you have [Expo modules configured](https://docs.expo.dev/bare/installing-expo-modules/) in your project, then:

```sh
# iOS
cd ios && pod install && cd ..
npx react-native run-ios

# Android
npx react-native run-android
```

### Config Plugin (recommended)

Add to your `app.json` / `app.config.js`:

```json
{
  "expo": {
    "plugins": [
      ["open-wearables", {
        "healthShareUsage": "Allow access to your health data.",
        "healthUpdateUsage": "Allow updates to your health data.",
        "backgroundDelivery": true
      }]
    ]
  }
}
```

## Android: Health Connect Setup

Add the Health Connect permission queries to your `AndroidManifest.xml`:

```xml
<queries>
  <package android:name="com.google.android.apps.healthdata" />
</queries>
```

The config plugin automatically adds the required `activity-alias` entries for Health Connect permission UI.

## Quick Start

```typescript
import OpenWearables, { HealthDataType } from 'open-wearables';

// 1. Configure
OpenWearables.configure('https://your-open-wearables-server.com');

// 2. Sign in with SDK token
await OpenWearables.signIn(userId, accessToken, refreshToken, null);
// OR with API key:
await OpenWearables.signIn(userId, null, null, 'your-api-key');

// 3. Request permissions
const granted = await OpenWearables.requestAuthorization([
  HealthDataType.Steps,
  HealthDataType.HeartRate,
  HealthDataType.Sleep,
  HealthDataType.ActiveEnergy,
]);

// 4. Start background sync
await OpenWearables.startBackgroundSync(30); // sync last 30 days on first run

// 5. Or sync immediately
await OpenWearables.syncNow();
```

## Events

```typescript
import OpenWearables from 'open-wearables';
import { EventSubscription } from 'expo-modules-core';

// Listen for log messages
const logSub: EventSubscription = OpenWearables.addListener('onLog', ({ message }) => {
  console.log('[OW]', message);
});

// Listen for auth errors
const errSub: EventSubscription = OpenWearables.addListener('onAuthError', ({ statusCode, message }) => {
  console.error('Auth error', statusCode, message);
  // Re-authenticate here
});

// Cleanup
logSub.remove();
errSub.remove();
```

## API Reference

### `configure(host, customSyncURL?)`
Set the Open Wearables server host. Call before anything else.

### `signIn(userId, accessToken, refreshToken, apiKey)`
Authenticate. Use either token-based auth (`accessToken` + `refreshToken`) or API key auth (`apiKey`).

### `signOut()`
Clear stored credentials and stop sync.

### `requestAuthorization(types: HealthDataType[])`
Request HealthKit (iOS) or Health Connect (Android) permissions. Returns `true` if granted.

### `startBackgroundSync(syncDaysBack?)`
Start periodic background sync. On first run, syncs `syncDaysBack` days of history. Returns `true` if started.

### `stopBackgroundSync()`
Stop background sync.

### `syncNow()`
Trigger an immediate sync.

### `setSyncInterval(minutes)`
Set background sync interval (minimum 15 minutes on Android due to WorkManager limits).

### `isSyncActive(): boolean`
Check if sync is currently running.

### `getSyncStatus(): Record<string, any>`
Get current sync state including last sync time and status per data type.

### `resumeSync()`
Resume sync after token refresh or re-authentication.

### `resetAnchors()`
Clear all sync anchors — next sync will re-read all historical data.

### `getStoredCredentials(): Record<string, any>`
Get stored auth credentials (tokens are masked).

### `getAvailableProviders()` (Android only)
Returns list of available health data providers (Health Connect, Samsung Health).

### `setProvider(providerId)` (Android only)
Select the active health data provider.

## Supported Health Data Types

```typescript
import { HealthDataType } from 'open-wearables';

HealthDataType.Steps
HealthDataType.HeartRate
HealthDataType.RestingHeartRate
HealthDataType.HeartRateVariabilitySDNN
HealthDataType.OxygenSaturation
HealthDataType.ActiveEnergy
HealthDataType.BasalEnergy
HealthDataType.DistanceWalkingRunning
HealthDataType.DistanceCycling
HealthDataType.FlightsClimbed
HealthDataType.BodyMass
HealthDataType.Height
HealthDataType.BodyFatPercentage
HealthDataType.Bmi
HealthDataType.BloodPressureSystolic
HealthDataType.BloodPressureDiastolic
HealthDataType.BloodGlucose
HealthDataType.RespiratoryRate
HealthDataType.BodyTemperature
HealthDataType.Vo2Max
HealthDataType.Sleep
HealthDataType.Workout
// ... and more
```

## Architecture

```
React Native / TypeScript
        │
        ▼
Expo Module API (JSI bridge)
   ┌────┴────┐
   │         │
Android     iOS
  │           │
Health      HealthKit
Connect       │
  │           │
  └─────┬─────┘
        │
  Open Wearables API
  POST /api/v1/sdk/users/{id}/sync
```

## Contributing

PRs welcome. See [CONTRIBUTING.md](CONTRIBUTING.md).

## License

MIT
