# 01 · Welcome — iOS (Liquid Glass)

**Generate with:** Stitch Standard Mode, with `../DESIGN.md` loaded into the project.
**Source:** `design/figma/07-ios-welcome.figma.js` → frame `screen-welcome`, 402 × 874.

## Prompt

Design a mobile welcome and sign-in screen for carApp, a local-first vehicle fuel-tracking app, on a 402 × 874 iPhone canvas.

Start with an ambient backdrop: a Cool Cloud Mist (#F2F2F7) base covered by five very large, heavily blurred overlapping soft circles that bleed off every edge — Soft Mint Glow (#BFF2E0) top-left, Pale Sky Glow (#E9F6FF) upper-right, Warm Amber Glow (#FFE9C7) lower-left, mint again lower-right, amber at the very bottom. They must read as a continuous diffuse wash of light with no identifiable edges. This wash exists so the glass has something to refract.

At the top, a status bar with "9:41" on the left and signal, wifi and battery icons on the right.

Centred in the upper third, a perfectly circular translucent frosted-glass container about 128 across, with a bright specular highlight along its top-left edge and a soft shadow beneath. Inside it, a thin-stroke outlined car icon in Vivid Deep Teal (#0A7C66). Below, the wordmark "carApp" in bold 34px True Black Ink (#000000), and beneath it the tagline "Registro local de repostajes" in 15px Muted Graphite. Both centred.

Anchored near the bottom with a 1rem margin, a large translucent frosted-glass container with generously rounded corners and a specular top-left edge, holding a vertical stack of three capsules with no dividers between them:
1. "Continuar con Apple" — full capsule, solid black, with a white Apple logo before the white label. Apple comes first: it is the platform-native provider.
2. "Continuar con Google" — full capsule, translucent frosted glass with a specular edge, dark label, multicolour Google G logo before it.
3. "Continuar sin cuenta" — full capsule in barely-there clear glass, with a Vivid Deep Teal label.

Beneath the stack, a small centred row with a thin circular spinner and "Preparando almacenamiento local…" in 13px Faint Graphite.

At the very bottom, a centred rounded home indicator pill.

All copy is Spanish.

## Screen data

| Element | Value |
|---|---|
| Wordmark | carApp |
| Tagline | Registro local de repostajes |
| Buttons | Continuar con Apple · Continuar con Google · Continuar sin cuenta |
| Loading caption | Preparando almacenamiento local… |

## Auth constraint

Not part of the prompt. Derived from `design/figma/07-ios-welcome.figma.js`, which in turn
implements `docs/SPECIFICATION.md §7 F-1`. It is here so a regeneration does not reintroduce a
control the product cannot honour.

`§7 F-1` step 1: the welcome screen offers the platform's sign-in providers and "Continue without
account" in a single step. There is no intermediate provider-selection screen, and no
provider-less "Iniciar sesión" control — every sign-in affordance names the provider it uses.

`§7 F-1` step 3: iOS offers Google and Apple, and MUST offer Apple whenever it offers Google. The
screen therefore has **exactly three** actions.

| Button | Figma node | Intent (`docs/CONTRACTS.md §20.10`) |
|---|---|---|
| Continuar con Apple | `btn-apple` | `startPermanentSignIn(AuthProvider.APPLE)` |
| Continuar con Google | `btn-google · glass regular` | `startPermanentSignIn(AuthProvider.GOOGLE)` |
| Continuar sin cuenta | `btn-guest · glass clear` | `startAnonymousSignIn()` |

`§7 F-1` step 4: the MVP has no other sign-in method — no email and password, no email link, no
phone or one-time code, no third-party SSO beyond Google and Apple. `AuthProvider` and
`NativeAuthCredential` are closed contracts; widening either is a gated change under `AGENTS.md`.

Apple leads the stack because it is the platform-native provider. The specification fixes the set
and the count, not the order; the ordering is the design call.

Account linking (anonymous → permanent, `§7 F-4`) starts from settings, never from this screen.

## Refinement prompts

- "Blur the ambient background circles far more — they should be an indistinct wash of light, not five visible circles."
- "The glass container needs a brighter specular highlight along its top-left edge; right now it reads as a flat translucent panel rather than glass."
- "Make the guest button noticeably more transparent than the Google button — it is the lowest-priority action in the stack."
