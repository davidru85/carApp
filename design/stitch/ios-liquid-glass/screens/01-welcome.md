# 01 · Welcome — iOS (Liquid Glass)

**Generate with:** Stitch Standard Mode, with `../DESIGN.md` loaded into the project.
**Source:** `design/figma/07-ios-welcome.figma.js` → frame `screen-welcome`, 402 × 874.

## Prompt

Design a mobile welcome and sign-in screen for carApp, a local-first vehicle fuel-tracking app, on a 402 × 874 iPhone canvas.

Start with an ambient backdrop: a Cool Cloud Mist (#F2F2F7) base covered by five very large, heavily blurred overlapping soft circles that bleed off every edge — Soft Mint Glow (#BFF2E0) top-left, Pale Sky Glow (#E9F6FF) upper-right, Warm Amber Glow (#FFE9C7) lower-left, mint again lower-right, amber at the very bottom. They must read as a continuous diffuse wash of light with no identifiable edges. This wash exists so the glass has something to refract.

At the top, a status bar with "9:41" on the left and signal, wifi and battery icons on the right.

Centred in the upper third, a perfectly circular translucent frosted-glass container about 128 across, with a bright specular highlight along its top-left edge and a soft shadow beneath. Inside it, a thin-stroke outlined car icon in Vivid Deep Teal (#0A7C66). Below, the wordmark "carApp" in bold 34px True Black Ink (#000000), and beneath it "Local-First Fuel & Expense Tracker" in 15px Muted Graphite. Both centred.

Anchored near the bottom with a 1rem margin, a large translucent frosted-glass container with generously rounded corners and a specular top-left edge, holding a vertical stack:
1. "Continuar con Apple" — full capsule, solid black, with a white Apple logo before the white label.
2. "Iniciar sesión con Google" — full capsule, translucent frosted glass with a specular edge, dark label, multicolour Google G logo before it.
3. A divider row: a hairline rule, the lowercase word "o" in Faint Graphite, another hairline rule.
4. "Iniciar sesión" — full capsule, solid Vivid Deep Teal (#0A7C66) with white label.
5. "Continuar sin cuenta" — full capsule in barely-there clear glass, with a Vivid Deep Teal label.

Beneath the stack, a small centred row with a thin circular spinner and "Preparando almacenamiento local…" in 13px Faint Graphite.

At the very bottom, a centred rounded home indicator pill.

All copy is Spanish.

## Screen data

| Element | Value |
|---|---|
| Wordmark | carApp |
| Tagline | Local-First Fuel & Expense Tracker |
| Buttons | Continuar con Apple · Iniciar sesión con Google · Iniciar sesión · Continuar sin cuenta |
| Divider | o |
| Loading caption | Preparando almacenamiento local… |

## Refinement prompts

- "Blur the ambient background circles far more — they should be an indistinct wash of light, not five visible circles."
- "The glass container needs a brighter specular highlight along its top-left edge; right now it reads as a flat translucent panel rather than glass."
- "Make the guest button noticeably more transparent than the Google button — it is the lowest-priority action in the stack."
