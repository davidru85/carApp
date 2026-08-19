# Figma design scripts

Figma Plugin API scripts that build the carApp UI redesign — strict **Material 3 Expressive**
for Android and strict **Liquid Glass** for iOS.

**Non-normative.** This folder is design tooling. It is indexed by
[`docs/DESIGN.md`](../../docs/DESIGN.md), the entry point for the design, but it is not part
of the normative documentation set governed by `AGENTS.md` and carries no authority over
behaviour or contracts.

## Status

Scripts `00`–`08` were executed successfully against the live file and their output is in
Figma today. Scripts `09`–`10` are complete but **have never run**: Figma's Starter plan allows
only **20 MCP tool calls per month** and the quota was exhausted mid-build. They failed on
quota, not on code.

| # | Script | Creates | Ran? |
|---|---|---|---|
| 00 | `00-pages-and-m3-tokens.figma.js` | Both pages + `M3 Expressive` collection (46 vars) | ✅ |
| 01 | `01-liquid-glass-tokens.figma.js` | `Liquid Glass` collection (35 vars) | ✅ |
| 02 | `02-android-welcome.figma.js` | Android `screen-welcome` | ✅ |
| 03 | `03-android-home.figma.js` | Android `screen-home` + blur fix for 02 | ✅ |
| 04 | `04-android-vehicle-form-and-detail.figma.js` | Android `screen-vehicle-form`, `screen-detail` | ✅ |
| 05 | `05-android-fuel-form.figma.js` | Android `screen-fuel-form` + indent fix for 04 | ✅ |
| 06 | `06-android-settings.figma.js` | Android `screen-settings` | ✅ |
| 07 | `07-ios-welcome.figma.js` | iOS `screen-welcome` | ✅ |
| 08 | `08-ios-home-and-detail.figma.js` | iOS `screen-home`, `screen-detail` | ✅ |
| 09 | `09-ios-forms.figma.js` | iOS `screen-vehicle-form`, `screen-fuel-form` | ⏳ pending |
| 10 | `10-ios-settings.figma.js` | iOS `screen-settings` | ⏳ pending |

To finish the redesign, run `09` then `10`. Nothing else is outstanding.

## Target file

| | |
|---|---|
| File | `carApp Figma` |
| File key | `OB4zAjwSmnNfgxfuIxnD34` |
| URL | https://www.figma.com/design/OB4zAjwSmnNfgxfuIxnD34/carApp-Figma |
| Android page | `01 · Android — M3 Expressive` (node `14:2`) |
| iOS page | `02 · iOS — Liquid Glass` (node `14:3`) |

The original concept boards (`android` `2:9`, `ios` `2:445` on `Page 1`) are untouched.

## How to run

Through an agent with Figma MCP access:

```
use_figma {
  fileKey: "OB4zAjwSmnNfgxfuIxnD34",
  code: <contents of the script>,
  description: "...",
  skillNames: "resource:figma-use"
}
```

Each script is one atomic call — if it throws, nothing is written and it is safe to fix and
retry. Each ends with `await screenshot()` so the result returns visually, and returns the
created node ids.

### Prerequisites

1. **A seat that can write.** Starter with a View seat cannot. A Pro plan with a Full or Dev
   seat gives 200 calls/day.
2. **The variable collections must exist** before any screen script runs. Screens resolve every
   colour through them and will throw if missing. Run `00` and `01` first on a fresh file.
3. **Inter must be available.** See the font warning below.

### Replaying on a fresh file

Page node ids (`14:2`, `14:3`) are hardcoded at the top of every screen script, and scripts
`03` and `05` additionally reference frame ids from the original run (`16:2`, `20:2`) to apply
their fix-ups. On a fresh file, run `00` first and re-point all of these to the ids it returns.

## Canvas geometry

Android screens are **412×917**; iOS screens are **402×874**. The original concept board drew
Android at iPhone dimensions — that is corrected here.

Frame x-positions preserve flow order on each page:

| | welcome | home | vehicle-form | detail | fuel-form | settings |
|---|---|---|---|---|---|---|
| Android | 0 | 452 | 904 | 1356 | 1808 | 2260 |
| iOS | 0 | 442 | 884 | 1326 | 1768 | 2210 |

## Three traps worth knowing before you edit

**1. `SF Pro` is a phantom font.** Figma's `listAvailableFontsAsync()` lists it and
`loadFontAsync()` resolves without throwing, but it is not installed — text nodes come back
`hasMissingFont: true` with width 0. The first iOS screen rendered completely textless because
of this. **Inter** is used as the SF substitute throughout; do not "correct" it back. Inter's
semibold style string is `Semi Bold` (with a space), not `SemiBold`. Roboto Flex, used on
Android, spells it `SemiBold` (no space).

**2. Custom properties cannot be attached to Figma nodes.** `node.__foo = x` throws
`TypeError: no such property`. Helper state is carried in plain JS objects instead.

**3. Free/Starter plans cap variable collections at 1 mode.** `collection.addMode('Dark')`
fails with `Limited to 1 modes only`, so Light/Dark cannot be expressed as variable modes. Both
token sets are single-mode; a dark theme would need a parallel collection or a plan upgrade.

## Design system notes

### Android — Material 3 Expressive
- Full M3 colour-role set derived from the existing teal seed, plus the Expressive shape scale
  (`corner-xs` 4 → `corner-xxl` 48, `corner-full` 999) and spacing tokens.
- Interaction states are **rendered, not implied**: the tonal welcome button shows the
  Expressive shape morph (full-round → `corner-lg`) under a 12% state layer, and the vehicle
  form shows text fields in error / focused / enabled with correct indicator weights.
- Elevation levels 1 and 3 as paired drop shadows; extended FABs; the Expressive morphing
  loading indicator (a rounded 7-point star).

### iOS — Liquid Glass
- **Glass is chrome only.** Nav bars, toolbars, segmented controls and floating actions get the
  treatment. Vehicle cards, KPI cards and entry rows stay opaque. Apple's guidance forbids
  glass on glass and reserves the material for the functional layer above content.
- **Glass needs something behind it.** Every screen builds an ambient backdrop of blurred colour
  ellipses; without it `BACKGROUND_BLUR` has nothing to refract and the material reads as flat
  translucent grey.
- **The recipe** is the `glass()` helper: semi-transparent fill + `BACKGROUND_BLUR` + a
  linear-gradient specular edge stroke (bright top-left, dim mid, medium bottom-right) + a lift
  shadow. Regular blurs at 32–34, Clear at 12.
- **Content deliberately runs under the glass.** Scroll containers sit at y=84 while nav bars
  occupy 0–104, so content genuinely passes behind the bar and refracts. That overlap is the
  effect, not a layout bug.
- Concentric radii: sheet 34, card/group 20–22, control 12–14, capsule 999.

## Fixes carried over from the audit of the original boards

- iOS floating action no longer overflows the frame (was clipped to "Añadir vehícu…").
- Segmented-control labels are Spanish on both platforms (iOS previously shipped English).
- The notes field exists on both platforms (was Android-only).
- Copy is unified — the original boards had diverged wording for identical functionality
  (sync status, backup subtitle, analytics row, skipped-refuel toggle, full-tank hint).
- The iOS KPI regained its typographic hierarchy (was body-sized).
- Android uses Android status-bar geometry rather than reusing the iOS status bar icons.

## Not covered

The brief's product context mentions **map integration** and **real-time status updates**.
Neither exists in the concept designs, and adding them would have been the structural change
the brief explicitly forbade, so the redesign preserves the 6-screen flow. If those features
are real they need new screens — a separate piece of work from this aesthetic pass.
