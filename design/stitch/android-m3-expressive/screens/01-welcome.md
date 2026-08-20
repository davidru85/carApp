# 01 · Welcome — Android (M3 Expressive)

**Generate with:** Stitch Standard Mode, with `../DESIGN.md` loaded into the project.
**Source:** `design/figma/02-android-welcome.figma.js` → frame `screen-welcome`, 412 × 917.

## Prompt

Design a mobile welcome and sign-in screen for carApp, a local-first vehicle fuel-tracking app, on a 412 × 917 Android canvas.

The background is Pale Mint Paper (#F5FBF7). Behind everything, place two very large, heavily blurred soft circles bleeding off the edges — one in Bright Mint Wash (#7FF8D6) off the top-left corner, one in Warm Honey Wash (#FFDEA6) off the right edge at about a third of the way down. They should read as an ambient glow with no visible edge, not as shapes.

At the top, a slim status bar showing "9:41" on the left and small signal, wifi and battery icons on the right in Deep Forest Ink (#171D1A).

Centred in the upper half, a large scalloped petal-shaped container about 180 across, filled Bright Mint Wash (#7FF8D6), with a simple outlined car icon centred inside it in dark green. Below it, the wordmark "carApp" in bold 45px Deep Forest Ink (#171D1A), and beneath that the tagline "Registro local de repostajes" in 16px Muted Slate Green (#3F4945). Both centred.

Anchored near the bottom with a 1rem margin, a boldly rounded card on Faint Mint Surface (#EFF5F1) containing a vertical stack of two full-width buttons with comfortable spacing:
1. "Continuar con Google" — fully pill-shaped, transparent with a Soft Sage Gray (#6F7975) hairline outline, dark text, with the multicolour Google G logo to the left of the label.
2. "Continuar sin cuenta" — shown in its **pressed state**: filled Calm Eucalyptus (#CDE9DD), morphed from a pill into a softly rounded rectangle, with a translucent dark-green tint washing over the whole button.

Beneath the buttons, a small centred row with a circular loading spinner and the caption "Preparando almacenamiento local…" in 12px Muted Slate Green (#3F4945).

At the very bottom, a short centred rounded gesture handle pill.

All copy is Spanish.

## Screen data

| Element | Value |
|---|---|
| Wordmark | carApp |
| Tagline | Registro local de repostajes |
| Buttons | Continuar con Google · Continuar sin cuenta |
| Loading caption | Preparando almacenamiento local… |

## Auth constraint

Not part of the prompt. Derived from `design/figma/02-android-welcome.figma.js`, which in turn
implements `docs/SPECIFICATION.md §7 F-1`. It is here so a regeneration does not reintroduce a
control the product cannot honour.

`§7 F-1` step 1: the welcome screen offers the platform's sign-in providers and "Continue without
account" in a single step. There is no intermediate provider-selection screen, and no
provider-less "Iniciar sesión" control — every sign-in affordance names the provider it uses.

`§7 F-1` step 3: Android offers Google. The screen therefore has **exactly two** actions.

| Button | Figma node | Intent (`docs/CONTRACTS.md §20.10`) |
|---|---|---|
| Continuar con Google | `btn-google-outlined` | `startPermanentSignIn(AuthProvider.GOOGLE)` |
| Continuar sin cuenta | `btn-guest-tonal · pressed` | `startAnonymousSignIn()` |

`§7 F-1` step 4: the MVP has no other sign-in method — no email and password, no email link, no
phone or one-time code, no third-party SSO beyond Google and Apple. `AuthProvider` and
`NativeAuthCredential` are closed contracts; widening either is a gated change under `AGENTS.md`.

Android has no Apple button: Apple sign-in is iOS only.

Account linking (anonymous → permanent, `§7 F-4`) starts from settings, never from this screen.

## Refinement prompts

- "Make the scalloped brand shape larger and increase the number of petals slightly so it reads as softer."
- "The pressed button should be more obviously a rounded rectangle — reduce its corner radius further and deepen the tint."
- "Blur the two ambient background circles much more heavily; they should be an indistinct glow, not identifiable circles."
