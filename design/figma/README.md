# Figma design scripts

Figma Plugin API scripts that build the carApp UI redesign — strict **Material 3 Expressive**
for Android and strict **Liquid Glass** for iOS.

**Non-normative.** This folder is design tooling. It is indexed by
[`docs/DESIGN.md`](../../docs/DESIGN.md), the entry point for the design, but it is not part
of the normative documentation set governed by `AGENTS.md` and carries no authority over
behaviour or contracts.

## Status

**The redesign is complete, but the live file is one pass behind.** Every script had been
executed against the live file: both pages hold all six screens in light, a second row of the
same six pinned to a Dark mode, and a wired prototype with platform-native transitions. Scripts
`09` and `10` were never run individually — `11`, their merged equivalent, ran instead and
created the same three frames in one call.

Since then the **welcome pass** (see [Authentication surface](#authentication-surface)) edited
`02`, `07` and `15`. Those three are ⏳ below and must be re-run, in the order given there,
before the file matches the scripts again.

| # | Script | Creates | Ran? |
|---|---|---|---|
| 00 | `00-pages-and-m3-tokens.figma.js` | Both pages + `M3 Expressive` collection (46 vars) | ✅ |
| 01 | `01-liquid-glass-tokens.figma.js` | `Liquid Glass` collection (35 vars) | ✅ |
| 02 | `02-android-welcome.figma.js` | Android `screen-welcome` | ⏳ edited, re-run |
| 03 | `03-android-home.figma.js` | Android `screen-home` + blur fix for 02 | ✅ |
| 04 | `04-android-vehicle-form-and-detail.figma.js` | Android `screen-vehicle-form`, `screen-detail` | ✅ |
| 05 | `05-android-fuel-form.figma.js` | Android `screen-fuel-form` + indent fix for 04 | ✅ |
| 06 | `06-android-settings.figma.js` | Android `screen-settings` | ✅ |
| 07 | `07-ios-welcome.figma.js` | iOS `screen-welcome` | ⏳ edited, re-run |
| 08 | `08-ios-home-and-detail.figma.js` | iOS `screen-home`, `screen-detail` | ✅ |
| 09 | `09-ios-forms.figma.js` | iOS `screen-vehicle-form`, `screen-fuel-form` | ➖ superseded by `11` |
| 10 | `10-ios-settings.figma.js` | iOS `screen-settings` | ➖ superseded by `11` |
| 11 | `11-ios-forms-and-settings-merged.figma.js` | `09` + `10` in one call | ✅ |
| 12 | `12-dark-mode-tokens.figma.js` | `Dark` mode on both collections, 55 values | ✅ |
| 13 | `13-dark-screen-row.figma.js` | Dark twin of all 12 screens, at y=1100 | ✅ ×2 |
| 14 | `14-ios-depth-pass.figma.js` | Translucent iOS sheets + `control/segment-selected` | ✅ |
| 15 | `15-prototype-motion.figma.js` | Prototype links, transitions, flow start | ⏳ edited, re-run ×2 |
| 16 | `16-native-status-bars.figma.js` | Platform-native status bars, all 24 frames | ✅ ×2 |
| 17 | `17-minimal-sheet-actions.figma.js` | Circular ✕ / ✓ actions on the three iOS sheets | ✅ |
| 18 | `18-android-settings-expressive.figma.js` | Android settings rebuilt in the Expressive idiom | ✅ |
| 19 | `19-android-launcher-icon.figma.js` | Android `launcher-icon` section (adaptive icon) | ✅ ×2 |
| 20 | `20-ios-launcher-icon.figma.js` | iOS `launcher-icon` section (app icon) | ✅ ×2 |

`11` is a generated merge of `09` and `10` that does the same work in **one** MCP call instead
of two. That mattered acutely on the old 20-call/month budget, and it is the script that actually
ran. Running `09` then `10` separately would have been equivalent. Do not run both the merged
script and the originals; either path creates the same three frames. On this file `11` has
already run, so running `09`/`10` now would duplicate the iOS forms and settings.

`11` is generated, not authored: it wraps each source script in an async IIFE (so their
identically-named `const`s do not collide) and merges the return values. Edit `09` and `10`,
then regenerate `11` — do not edit `11` directly.

Scripts `13` and `15` are marked ✅ ×2 because each targets one page per run and was executed
twice, once per platform. Both are idempotent: `13` rebuilds stale dark twins rather than piling
up duplicates, and `setReactionsAsync` replaces rather than appends.

**`13` strips reactions from its clones.** `clone()` copies prototype reactions along with
everything else, so once `15` has run, re-cloning a light screen hands the dark twin live links
into the **light** flow — clicking a dark button would jump the viewer back to the light
prototype. This did not bite on the first build only because `13` happened to run before `15`
ever did; the moment the two scripts ran in the other order it became real. On the F-1 re-run it
stripped 10 reactions per page. The dark row is a colour reference, not a second prototype, so
the clones must come out inert.

**`15` must run last.** It wires the prototype by layer name, and two later scripts move the
ground under it:

- `17` renames the iOS sheet actions from `Cancelar`/`Guardar`/`Cerrar` to
  `btn-cancel`/`btn-save`/`btn-close`. Its `IOS_LINKS` table already carries the new names.
- `18` rebuilds the Android settings screen, which destroys and recreates its back button. The
  name `icon-button` is preserved so the lookup still resolves, but the **node id changes**, so
  the old reaction is left pointing at a dead node.

Reactions live on node ids, not names, so anything that recreates a trigger node silently
breaks its link. `15` was re-run for both platforms after `17` and `18` for exactly this
reason. If you rebuild a screen, re-run `15` for that platform.

`12`–`17` post-date the original build and were driven by an audit of the delivered screens
against the two style briefs. `14` in particular fixes two defects baked into `09`/`10`/`11`: an
opaque sheet that hid the ambient backdrop, and a selected segment filled with literal white
instead of a token. Those source scripts were **not** retrofitted — `14` is a post-pass. Replaying
the build from scratch therefore means running `00`–`15` in order, not `00`–`11`.

The welcome pass is the only outstanding work; see [Authentication surface](#authentication-surface).

`19` and `20` are marked ✅ ×2 because each ran twice: once as built, then again after the first
render exposed a defect in each (the Android mark was too large for the circle mask, the iOS
plate went white behind a white disc). Both are idempotent, so the second run replaced the first
rather than stacking. See [Launcher icons](#launcher-icons).

### Access history (resolved 2026-08-20)

Worth keeping, because the failure mode was mistaken for a quota problem twice.

Seat type governs two independent things, and only one is about volume:

- *Rate.* View/Collab seats get 20 tool calls/month on Starter, 6/month elsewhere. Dev and Full
  seats get 200/day on Pro. See `file://figma/docs/rate-limits-access.md`.
- *Write access.* **Only a Full seat may call the editing tools.** A Dev seat carries the full Pro
  rate limit and reads the file fine, but `use_figma` refuses outright:
  *"To use MCP tools that make edits, you'll need a Full seat."*

The build stalled twice, for different reasons. First on Starter with a View seat, where the
20-call monthly quota ran out mid-build — that is the failure earlier revisions of this file
recorded. By 2026-08-20 the account had moved to Pro, reads worked again, and the remaining work
was attempted; it was refused on the *second* reason, the Dev seat's lack of write access.
Raising the seat to Full cleared it, and `11` ran on the first attempt with no code changes.

`use_figma` is atomic, so neither refusal wrote anything and no cleanup was needed.

If a future script is refused, run `whoami` first: it reports tier and seat, which separates a
rate-limit problem from a write-permission problem immediately.

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

1. **A Full seat on a paid plan.** A Dev seat is not enough — it can read but not write. See
   [Access history](#access-history-resolved-2026-08-20).
2. **The variable collections must exist** before any screen script runs. Screens resolve every
   colour through them and will throw if missing. Run `00` and `01` first on a fresh file.
3. **Inter must be available.** See the font warning below.

### Replaying on a fresh file

Page node ids (`14:2`, `14:3`) are hardcoded at the top of every screen script, and scripts
`03` and `05` additionally reference frame ids from the original run (`16:2`, `20:2`) to apply
their fix-ups. On a fresh file, run `00` first and re-point all of these to the ids it returns.

`03` is the mild case: it now resolves the welcome frame by name and falls back to `16:2`, so it
degrades to a no-op instead of throwing. `05` still depends on `20:2` outright.

## Canvas geometry

Android screens are **412×917**; iOS screens are **402×874**. The original concept board drew
Android at iPhone dimensions — that is corrected here.

Frame x-positions preserve flow order on each page:

| | welcome | home | vehicle-form | detail | fuel-form | settings |
|---|---|---|---|---|---|---|
| Android | 0 | 452 | 904 | 1356 | 1808 | 2260 |
| iOS | 0 | 442 | 884 | 1326 | 1768 | 2210 |

Two rows per page. Light frames sit at **y=0**; their dark twins, named `<screen> · dark`, sit at
**y=1100** at the same x. The offset clears the tallest light frame (Android, 917) with margin.

Below both rows, at **y=2300**, each page carries a `launcher-icon` **section** — not a frame.
See [Launcher icons](#launcher-icons) for why that distinction is load-bearing.

## Six traps worth knowing before you edit

**1. `SF Pro` is a phantom font.** Figma's `listAvailableFontsAsync()` lists it and
`loadFontAsync()` resolves without throwing, but it is not installed — text nodes come back
`hasMissingFont: true` with width 0. The first iOS screen rendered completely textless because
of this. **Inter** is used as the SF substitute throughout; do not "correct" it back. Inter's
semibold style string is `Semi Bold` (with a space), not `SemiBold`. Roboto Flex, used on
Android, spells it `SemiBold` (no space).

**2. Custom properties cannot be attached to Figma nodes.** `node.__foo = x` throws
`TypeError: no such property`. Helper state is carried in plain JS objects instead.

**3. Free/Starter plans cap variable collections at 1 mode**, and Pro lifts it. Under Starter,
`collection.addMode('Dark')` fails with `Limited to 1 modes only`, which is why both token sets
were originally built single-mode. Script `12` confirmed on 2026-08-20 that a Pro plan removes
the cap: both collections now carry `Light` and `Dark`. If you replay this on a free file, `12`
is the script that will fail.

**4. `node.query()` rejects non-ASCII selectors.** Layer names here are full of `·` (U+00B7), and
`page.query('FRAME[name^=segment · selected]')` throws
`Invalid selector: unexpected character (0xc2)`. The selector parser is ASCII-only; the middle dot
is two UTF-8 bytes and it chokes on the first. Use `findAll` with a predicate for any name
containing punctuation beyond ASCII.

**5. Screen scripts are not automatically idempotent.** Most call `createFrame()` outright, so a
second run stacks a duplicate frame at the same coordinates rather than refreshing. `02` and `07`
now remove the existing light frame by name first; `13` and `16` already did the equivalent.
Anything else you re-run, check before assuming a clean refresh. The related trap is cross-script
fix-ups pinned to node ids from the original run — see the `03` case in
[Authentication surface](#authentication-surface).

**6. Prototype springs exist but reject `initialVelocity`.** `easingFunctionSpring` takes exactly
`mass`, `stiffness` and `damping`. Passing the fourth field that the CSS/UIKit spring model uses
fails validation with `Unrecognized key(s) in object: 'initialVelocity'`. The error reads like
springs are unsupported; they are not.

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
- **Sheets are translucent at 0.8**, not opaque (script `14`). An opaque sheet covers y=44–874
  and hides the ambient backdrop, which left the three form screens flat and gave the toolbar
  glass nothing to refract. Only the negative space between the inset groups picks up ambient
  colour; the group cards stay fully opaque, so text contrast is unchanged. Real iOS sheets are
  opaque — this trades a little platform literalism for the depth the brief asks for, and it is
  one constant to revert.

## Dark mode

Both collections carry `Light` and `Dark` (script `12`). Android uses the standard M3 dark scheme
for the existing teal seed; the check that the two halves come from one tonal palette is that the
light scheme's `inverse-primary` (#61dbba) is the dark scheme's `primary`. iOS follows Apple's
dark system colours, with the glass materials inverted from white-translucent to dark-translucent
and the specular edge dimmed so it reads as a highlight rather than a white outline. The ambient
blobs stay chromatic but drop to low luminance, so blurred glass still has colour to refract
without lifting the background off black.

Nothing is themed by toggling the page. Figma resolves a collection mode per subtree, so script
`13` clones each screen and pins the clone with `setExplicitVariableModeForCollection`. Light and
dark render side by side in the same file, and the light row stays the delivered artefact.

**Known limit: a few fills are literals, not tokens, so they do not respond to the mode.** Script
`14` fixed the worst offender (the selected segment, previously literal white — glaring on dark).
The remainder are deliberate: switch thumbs stay white because iOS switches are white in both
modes, and the specular gradient stroke is a white-alpha ramp by construction. Anything else that
looks mode-deaf is a literal fill that should become a variable.

## Motion

Script `15` wires the six light screens of each page into a prototype — 12 links on Android, 13
on iOS (welcome carries one trigger per sign-in provider, and iOS has two), `ON_CLICK`, with the
flow starting at `screen-welcome`. The dark row is a colour reference and is
deliberately **not** wired; two flows in one page would compete.

The two motion languages are the point, so the numbers are chosen rather than defaulted:

| | Android — M3 Expressive | iOS — Liquid Glass |
|---|---|---|
| Easing | `CUSTOM_CUBIC_BEZIER` (0.2, 0, 0, 1) — M3 emphasized | `CUSTOM_SPRING` mass 1, stiffness 240, damping 26 |
| Hierarchy | `SMART_ANIMATE` 500 ms in / 400 ms back | `PUSH` left 350 ms / right 350 ms |
| Modals | same as hierarchy | `MOVE_IN` bottom 500 ms / `MOVE_OUT` bottom 400 ms |

Android returns faster than it advances, which is M3's rule: the user already knows the target.
`SMART_ANIMATE` is the closest Figma primitive to M3's container transform, so the FAB, the
vehicle cards and the app-bar titles morph instead of cross-fading. iOS separates push/pop for
hierarchy from sheet present/dismiss for the three forms, because iOS treats them as different
gestures. Damping 26 against stiffness 240 sits just under critical: it settles with a hint of
overshoot rather than ringing.

## Status bars

Both platforms originally shipped the **same three lucide-style stroke icons** — four stroke
bars, arcs, and a stroked rounded rect. Neither OS draws any of those, and sharing one set
across both defeats a platform-native pass. The older note claiming Android had its own status
bar was true only of geometry (32 vs 54 tall, different padding), never of the glyphs. Script
`16` rebuilds all 24 status-bar frames, light and dark, with filled glyphs:

| | Android — Pixel / Material | iOS — iPhone 16 Pro |
|---|---|---|
| Clock | left at 16dp, Roboto Flex Medium 14 | centred in the left ear beside the island |
| Cellular | filled triangle | four discrete rounded bars, increasing |
| Battery | **vertical**, nub on top | **horizontal**, outline + fill level + terminal nub |
| Order | wifi → cellular → battery (stock Pixel) | cellular → wifi → battery |
| Cutout | none | **Dynamic Island**, 125×37 at y=11 |

402×874 is exactly an iPhone 16 Pro, so the island belongs there; it was missing entirely. It is
a deliberate literal black rather than a token, because it is a physical cutout and stays black
in both modes. Wifi is the one glyph that is genuinely near-identical on both systems — a filled
three-arc fan — so both use it, honestly rather than by oversight.

Frame heights are unchanged (Android 32, iOS 54). Changing them would reflow every screen.

## iOS sheet actions

The three sheets carried `Cancelar` / `Guardar` / `Cerrar` as 17pt text, which is the older iOS
bar-button idiom. Script `17` replaces them with compact circular glyph buttons:

- **✕ leading on all three sheets.** Cancel and Close are the same gesture, so they share the
  glyph; only the layer name differs (`btn-cancel` vs `btn-close`), which keeps the prototype
  wiring legible.
- **✓ trailing on the two forms only.** Save is the sole affirmative action, so it is the only
  one that earns colour: `accent/brand` fill with an `accent/on-brand` glyph. Settings has no
  affirmative action and gets the ✕ alone.

The buttons are **not** glass. The toolbar is already a glass surface and glass on glass is
forbidden above, so they use the flat translucent `fill/primary` that iOS uses for its own
circular close buttons — still a token, so still mode-aware.

## Android settings — the Expressive rebuild

Settings was the flattest of the six Android screens: one `surface-container` card per group
with 1px dividers and a `small` top app bar. Correct M3, unremarkable Expressive — every other
screen carried the vocabulary (the scalloped mark, the shape morph, the morphing loading
indicator, extended FABs) and this one carried none of it. Script `18` rebuilds it. Every string
and every row is preserved; only the styling changes.

- **Connected item groups.** The signature Expressive list. Items are separated by a 4px gap
  instead of hairline dividers, with asymmetric radii — first item 28 outside / 6 inside,
  middles 6 all round, last mirrors the first, lone items 28 everywhere — so a group reads as
  one carved object rather than a stack of rules. Dividers are gone entirely.
- **Large top app bar** with the title at 32/Bold, replacing `small`.
- **Leading tonal discs**, 40px, one per row.
- **Tonal differentiation carries meaning**, not decoration: the backup row is
  `primary-container` because it reports a healthy state, and Eliminar cuenta is
  `error-container` rather than grey-with-red-text.
- **A spec-correct M3 switch** — track, outline, and a thumb that is small and outline-coloured
  when off, growing only when selected. It was previously a grey pill.
- **The scalloped mark returns in the footer**, a small callback to welcome.

Two defects were caught by iterating rather than by eye, and both are worth recording:

1. **The first pass overflowed.** The footer landed at y=936 in a 917 frame, clipping the version
   text into the gesture handle. `rebuild()` now returns `footerBottom` and an `overflows` flag
   checked against `GESTURE_Y`, so the script reports it instead of leaving it to a screenshot.
2. **The leading discs nearly vanished.** They defaulted to `secondary-container` (#cde9dd) on
   rows filled with `surface-container-high` (#e4eae6) — two values too close to separate. Discs
   now vary per group (`primary-container` for units, `tertiary-container` for privacy), which
   fixes the contrast and does the Expressive job of colour-coding sections at the same time.

## Authentication surface

Governed by `docs/SPECIFICATION.md §7 F-1` and `docs/CONTRACTS.md §20.3`. This section records how
these scripts satisfy that specification; it creates no rules of its own.

The welcome screen is the only auth surface in the six-screen flow, and it was the one place the
designs invented product behaviour. Both platforms shipped a **generic "Iniciar sesión" button**
alongside the provider buttons — `btn-signin-filled` on Android (filled `color/primary`, so the
most prominent control on the screen) and `btn-primary` on iOS (filled `accent/brand`, below an
"o" divider). Nothing in the product backs it, and the prototype made the inversion literal: `15`
wired those two phantom buttons to `screen-home` while Google and Apple led nowhere.

### What the product actually supports

Extracted from the normative documents; none of it is a design decision.

| Source | Statement |
|---|---|
| `docs/SPECIFICATION.md` §3.1 | Anonymous login; Google sign-in on Android and iOS; Apple sign-in on iOS. |
| `docs/SPECIFICATION.md` §7 F-1 step 1 | "The welcome screen offers the platform's sign-in providers and 'Continue without account' in a single step. There MUST NOT be an intermediate provider-selection screen, and there MUST NOT be a provider-less 'Sign in' control: every sign-in affordance names the provider it uses." |
| `docs/SPECIFICATION.md` §7 F-1 step 3 | "Android offers Google; iOS offers Google and Apple. iOS MUST offer Apple whenever it offers Google… exactly two actions on Android and exactly three on iOS, counting 'Continue without account'." |
| `docs/SPECIFICATION.md` §7 F-1 step 4 | "The MVP has no other sign-in method. Email and password, email link, phone or one-time code, and any third-party identity provider other than Google and Apple are not part of the MVP." |
| `docs/SPECIFICATION.md` §4 | "Authenticated user \| Uses Google **or** Apple." |
| `docs/CONTRACTS.md` §20.3 | `enum class AuthProvider { ANONYMOUS, GOOGLE, APPLE }` — closed. |
| `docs/CONTRACTS.md` §20.8 | `sealed interface NativeAuthCredential { Google, Apple }` — no email, phone or OIDC leaf. |
| `docs/CONTRACTS.md` §20.10 | `SessionStateHolder.startAnonymousSignIn()` and `startPermanentSignIn(provider: AuthProvider)`. |
| `docs/CONTRACTS.md` §20.2 | `AuthError` has no `InvalidEmail`, `WrongPassword` or `WeakPassword` leaf. |
| `docs/adr/0007-firebase-auth-gitlive.md` | Firebase Auth via GitLive 2.6.x behind `AuthClient`; native UI obtains **Google and Apple** credentials. A custom auth backend was considered and rejected as out of scope. |
| `docs/BACKLOG.md` `E2-03` | "iOS offers Apple whenever Google is offered."; "exactly two actions on Android … exactly three on iOS"; "No control on the welcome screen starts a sign-in without naming its provider." |

Email/password, magic link, OTP and SSO appear **nowhere** in the repository, and since
2026-08-20 `§7 F-1` step 4 says so outright rather than leaving it to inference. Adding one would
mean widening two closed contracts, which `AGENTS.md` makes a human review gate.

The decisive argument for the design is mechanical, not stylistic: the only way the UI can start a
permanent sign-in is `startPermanentSignIn(provider)`. A provider-less button has no provider to
pass, so it has no intent to invoke.

### Decision — one-step welcome (owner, 2026-08-20)

`docs/SPECIFICATION.md` §7 F-1 step 1 used to read *"Welcome screen with 'Sign in' and 'Continue
without account'"*, and step 3 said sign-in *offers* providers. Read together that admitted a
two-step flow (welcome → provider picker), which would have justified a generic button — but only
on a screen showing no providers of its own. The delivered composition matched neither reading.

The owner chose the **one-step** flow: the welcome screen shows the platform providers directly.
It costs no extra screen, matches `E2-03` as a single story, and serves P3 "No entry barrier".

**That decision is now normative.** `§7 F-1` was rewritten on 2026-08-20 to state the one-step
rule, the exact action count per platform and the closed provider set; `docs/BACKLOG.md` `E2-03`
gained two matching acceptance criteria; `README.md` and `docs/DESIGN.md` repeat the rule; the
change is logged in `docs/PROJECT_LOG.md`. This folder implements that specification — it does
not decide it. If a future change to these scripts contradicts `§7 F-1`, the specification wins
and the scripts are the defect.

### The resulting stacks

Every button maps to one real intent. Nothing on the screen is decorative.

| | Android (`02`) | iOS (`07`) |
|---|---|---|
| 1 | `btn-google-outlined` — Continuar con Google | `btn-apple` — Continuar con Apple |
| 2 | `btn-guest-tonal · pressed` — Continuar sin cuenta | `btn-google · glass regular` — Continuar con Google |
| 3 | — | `btn-guest · glass clear` — Continuar sin cuenta |

| Button | Intent |
|---|---|
| Continuar con Google | `startPermanentSignIn(AuthProvider.GOOGLE)` |
| Continuar con Apple | `startPermanentSignIn(AuthProvider.APPLE)` — iOS only |
| Continuar sin cuenta | `startAnonymousSignIn()` |

Apple leads on iOS because it is the platform-native provider, and it is not optional: `§7 F-1`
step 3 and `E2-03` both forbid offering Google without it. Android has no Apple button — `§7 F-1`
step 3 gives Android Google only.

Ordering within the stack is the one thing here the specification does not fix; it constrains the
set and the count, not the sequence. Apple first on iOS and provider-before-guest on both are
design calls, made because the platform-native provider leads on its own platform and the guest
path is the lowest-priority action in the stack.

Compliance check, straight against `§7 F-1`: two actions on Android and three on iOS ✓; every
sign-in affordance names its provider ✓; no intermediate provider-selection screen ✓; no provider
outside `AuthProvider` ✓.

Three further copy defects were fixed in the same pass, all of them iOS drifting from Android for
identical functionality:

- The Google label read `Iniciar sesión con Google` on iOS and `Continuar con Google` on Android.
  Both now say **Continuar con Google** — one verb across the whole stack, and no wording that
  implies a pre-existing account to recover.
- The tagline was `Local-First Fuel & Expense Tracker`, English, on a screen whose own brief ends
  "All copy is Spanish". It now matches Android: **Registro local de repostajes**.
- The `divider-row` ("o") existed only to separate the phantom button from the providers. Gone;
  the three capsules are a single uninterrupted stack.

### Re-run order

`15` wires by layer name but stores reactions on **node ids**, so it must go last:

1. `02-android-welcome.figma.js` — rebuilds Android `screen-welcome`.
2. `07-ios-welcome.figma.js` — rebuilds iOS `screen-welcome`.
3. `13-dark-screen-row.figma.js` — once per page; rebuilds the stale dark twins.
4. `16-native-status-bars.figma.js` — once per page; the rebuilt frames need their status bars back.
5. `15-prototype-motion.figma.js` — once per page; re-wires the prototype.

`14`, `17` and `18` are untouched by this pass: none of them targets a welcome frame.

Executed in that order on 2026-08-21. Results: `02` → `91:2`, `07` → `92:2`; `13` rebuilt six
twins per page and stripped 10 inherited reactions on each; `16` replaced 12 status bars per
page; `15` wired 12 links on Android and 13 on iOS. Verified from the scripts' own return
values that `btn-signin-filled`, `btn-primary` and `divider-row` are all absent from the rebuilt
welcome frames.

`02` and `07` were **not re-runnable** before this pass — each called `createFrame()`
unconditionally, so a second run stacked a duplicate `screen-welcome` at x=0 on top of the first.
Both now drop the existing light frame by name before rebuilding. The dark twin is left alone;
`13` rebuilds it.

The blur fix-up in `03` was the other trap. It resolved the welcome frame through the hardcoded
node id `16:2` from the original run, which dies the instant `02` is re-run, and then called
`.findOne` on `null`. `02` now applies the 60px `LAYER_BLUR` and the 0.55 / 0.6 opacities to its
own ambient ellipses, so the fix-up is redundant; `03` resolves by name with the id as fallback
and no-ops when the frame or the shapes are absent.

### What is still missing, and it is not on this screen

`docs/SPECIFICATION.md` §7 F-4 puts anonymous → permanent conversion **in settings**, and
`docs/CONTRACTS.md` §20.10 exposes `startAccountConversion(provider)` and
`confirmAccountConversion(confirmation)` for it. The settings screens (`06`, `18` for Android;
`11` for iOS) show only `Cerrar sesión` and `Eliminar cuenta` under "Cuenta". Undesigned:

- A conversion row offering Google (and Apple on iOS), visible only while the session is
  `SessionPhase.ANONYMOUS`.
- The F-4 credential-collision dialog: the MVP does not merge accounts, so the user either enters
  the existing account and discards local anonymous data after a destructive confirmation that
  reports how much data is lost, or cancels and keeps everything.
- Sign-out is offered only to a permanently authenticated user; an anonymous session gets
  "delete local data" instead, with the same two-step confirmation.

That is real missing auth UI. It needs a decision and a script of its own — it is not part of this
pass.

## Launcher icons

Scripts `19` and `20` add the platform launcher icons, one section per page. They post-date the
six-screen flow and are independent of it: nothing else references them, and neither script
touches an existing frame.

### They are sections, and that is deliberate

Script `13` rebuilds the dark row from
`page.children.filter(n => n.type === 'FRAME' && !n.name.endsWith(' · dark'))`. A top-level
**frame** named `launcher-icon` would therefore be cloned into the dark row at y=1100 on 13's
next run, landing on top of `screen-welcome · dark`. A **section** is skipped by that type
filter, so the two scripts stay independent without either having to know about the other.

A section's children carry coordinates **relative to the section origin** — verified rather than
assumed, because the board is positioned from inside it.

### One mark, two executions

Both icons reuse the welcome screen's vocabulary rather than inventing a third mark, so the
launcher and the first screen the user sees are recognisably the same product:

| | Android | iOS |
|---|---|---|
| Ground | flat `color/primary` #006A57 | ambient plate on `background/ambient-a` |
| Container | Expressive scalloped star, `primary-container` | raised glass disc, `material/regular/fill-raised` |
| Glyph | lucide car, `on-primary-container` | lucide car, `accent/brand` |

### Android — the container is 52dp, not 66dp

108dp canvas, 72dp mask viewport, 66dp safe zone. The first pass drew the scalloped container at
the full 66dp on the reasoning that the safe zone is what every mask guarantees. That is true and
it still looked wrong: at 66dp the circle mask **sheared the scallop points flat**, and `primary`
survived only as slivers in the corners, so the icon read as a mint blob with no ground. 66dp is
a ceiling, not a target. At **52dp** — 72% of the visible viewport — the silhouette clears every
mask intact and the deep teal reads as a deliberate field. Check any change to that number
against the circle mask first; it is the tightest of the four.

The monochrome layer runs the opposite way, at 44dp. Themed icons drop both colour layers, so the
container cannot survive the recolour and the car has to hold the viewport alone.

Masters are drawn at 4x (432 px = 108dp @xxxhdpi), so the density set falls on clean quarters.
`minSdk` is 26, so the delivery is vector drawables and no PNG mipmap set is required.

### iOS — the plate cannot be white

The screens build their ambient backdrop on `background/system`, which is white. Copied verbatim
into a 1024 icon it failed: the top-right corner resolved to near-white and the white glass disc
dissolved into it. The plate is based on `background/ambient-a` instead, so every corner stays
tinted and the disc separates all the way round. It carries into Dark for free — the same token
is a deep teal there rather than near-black `#1c1c1e`.

Three appearance masters ship: **Any**, **Dark** (a clone pinned to the Dark mode with
`setExplicitVariableModeForCollection`, the same trick as `13`), and **Tinted**.

**Tinted is the one deliberate exception to the token rule in this whole folder.** Apple's tinted
appearance takes a *grayscale* asset and maps its luminance onto the user's chosen tint, so a
token-coloured icon would be wrong by construction. That master is built from literal greys. Do
not "fix" it to variables.

The masters are full-bleed squares with no rounded corners and no alpha: iOS applies the
superellipse itself, and baking it in produces a double-masked icon with dark fringing.

### What these boards are not

They are design sources, not shipped assets. Neither
`androidApp/src/main/res/mipmap-anydpi-v26/` nor `iosApp/Assets.xcassets/` exists in the
repository yet; creating them is `docs/BACKLOG.md` **E4-04** ("App icons and splash are
present"). Each board carries its own export spec — file names, sizes and formats — so that
story is a transcription job rather than a design one.

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
