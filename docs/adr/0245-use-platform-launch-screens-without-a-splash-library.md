# ADR-0245 / D-244 - Use Platform Launch Screens without a Splash Library

## Status

Accepted

The owner selected option B on 2026-10-24 and supplied the concrete launch values on 2026-10-27
(O11, recorded here rather than under a new decision).

## Context

Neither host had an icon or a branded launch screen: Android used the platform
`Theme.Material.Light.NoActionBar` with no `android:icon`, and iOS declared an empty `UILaunchScreen`.
Android 12 (API 31) and later always show a system splash built from the theme and the launcher icon;
API 26–30 show the window background. The app is light-only: it has no dark theme, so a `values-night`
splash colour would describe a screen the app never draws.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. `androidx.core:core-splashscreen` | One API for every Android level. | A new library and decision; adds an exit animation the MVP does not need. |
| B. Platform mechanisms only | No dependency: `windowSplashScreen*` theme attributes on API 31+, a themed window background on API 26–30, iOS `UILaunchScreen` with an image. | Two Android resource paths to maintain. |

## Decision

The selected option is **B**, with these values (O11, 2026-10-27):

- **Android.** The activity starts on `Theme.CarApp.Launch`.
  - API 31+ (`values-v31`): `android:windowSplashScreenBackground` = `#FEF7FF`, the default Material 3
    light surface the app renders today, and `android:windowSplashScreenAnimatedIcon` = the adaptive
    launcher icon.
  - API 26–30: `android:windowBackground` = `@drawable/launch_background`, a layer list of `#FEF7FF`
    plus `@drawable/launch_logo` centred at 160 dp, the size of the Android 12+ splash icon circle.
  - There is **no** `values-night` splash colour: the app is light-only.
  - `MainActivity` switches to `Theme.CarApp` before `super.onCreate`. The adaptive launcher icon has
    background, foreground and monochrome layers.
- **iOS.** `UILaunchScreen` names `UIImageName` = `LaunchLogo` and sets **no** `UIColorName`, so the
  system default `systemBackground` applies (white in light, black in dark). There is no
  `LaunchBackground` colour set.
- **Artwork provenance.** The owner exported the final artwork from the Figma file
  `OB4zAjwSmnNfgxfuIxnD34` on 2026-10-27: Android background node `141:12`, foreground `141:17`,
  monochrome `141:30`; iOS `AppIcon-Any` `143:12`, `AppIcon-Dark` `143:29`, `AppIcon-Tinted` `143:46`
  (1024 × 1024, RGB, no alpha, the tinted one grayscale by design); the launch logo is derived from the
  launcher's 72 dp viewport as a disc. The 432-unit SVGs are the 108 dp adaptive canvas at 4×, and the
  container sits inside the 66 dp safe zone, checked under the circle and squircle masks.
- **Icon appearances.** `AppIcon.appiconset` carries the three 1024 × 1024 appearances Apple requires:
  universal (Any), Dark and Tinted.

## Consequences

### Positive

- No dependency; the launch screen matches the first screen the app actually draws.
- The icon and launch logo are the owner's real artwork, not placeholders.

### Negative

- Two Android resource paths to maintain: the API 31+ theme attributes and the API 26–30 window
  background.
- A dark appearance of the launch logo would need its own asset; the owner may adjust the logo size
  after the on-device review.

### Constraints Introduced

- No splash library may be added without a superseding decision; `ReleaseAssetsContractTest` asserts its
  absence.
- The launch background stays `#FEF7FF` and light-only until the app gains a dark theme.

## Verification

- `ReleaseAssetsContractTest`: the adaptive icon and its three layers, the manifest icon and theme, the
  two launch theme resources, the `MainActivity` theme switch, the iOS icon appearances and the
  `UILaunchScreen` dictionary.
- `sips -g hasAlpha` reports `no` for each app-icon PNG.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-244`); `design/figma/19-android-launcher-icon.figma.js`;
  `design/figma/20-ios-launcher-icon.figma.js`
- The Figma file `OB4zAjwSmnNfgxfuIxnD34`, exported 2026-10-27
