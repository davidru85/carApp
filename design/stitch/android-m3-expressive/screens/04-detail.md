# 04 · Vehicle detail — Android (M3 Expressive)

**Generate with:** Stitch Standard Mode, with `../DESIGN.md` loaded into the project.
**Source:** `design/figma/04-android-vehicle-form-and-detail.figma.js` → frame `screen-detail`, 412 × 917.

The hero statistic is the point of this screen. If a regeneration shrinks it toward body size, prompt to restore display scale.

## Prompt

Design a mobile vehicle detail screen for carApp on a 412 × 917 Android canvas. Background Pale Mint Paper (#F5FBF7).

A slim status bar, then a tall top app bar: a back arrow at the upper left, a circular tonal icon button filled Calm Eucalyptus (#CDE9DD) with an edit icon at the upper right, and low in the bar, left-aligned, the vehicle name "Toyota Corolla" in bold 32px Deep Forest Ink (#171D1A).

Beneath the bar, centred, a small scalloped petal-shaped loading indicator in Deep Pine Teal (#006A57) — a pull-to-refresh state, not a plain circular spinner.

Below that, a hero statistic panel with 1rem side margins, boldly rounded, filled Bright Mint Wash (#7FF8D6), containing:
- The label "CONSUMO MEDIO HISTÓRICO" in small capitals with expanded letter-spacing, dark green.
- The figure "7,24" in **bold 57px** dark green, with the unit "L/100 km" in 18px sitting on the same baseline immediately to its right. The size contrast between figure and unit should be dramatic.
- Beneath, "Media de 24 repostajes registrados" in 14px dark green.
- A large scalloped petal shape in a slightly deeper green at very low opacity, bleeding off the panel's top-right corner as a subtle decorative motif, clipped by the panel edge.

Below the panel, a list of three refuelling entry rows with 1rem side margins and small gaps, each a rounded container filled Faint Mint Surface (#EFF5F1) containing:
- A top row with the date on the left in semi-bold 16px and the consumption right-aligned in semi-bold 16px Deep Pine Teal (#006A57).
- A second row with odometer and litres on the left in 13px Muted Slate Green, and the cost right-aligned in 14px Muted Slate Green.

Entry one additionally carries a small caution badge beneath its rows: a compact rounded badge filled Warm Honey Wash (#FFDEA6) with a warning triangle icon and the label "Tanque parcial" in Burnished Amber (#7C5800).

Entries:
1. "15 oct 2026" · "7,10 L/100 km" · "142.850 km · 45,2 L" · "73,63 €" · with the partial-tank badge
2. "01 oct 2026" · "7,32 L/100 km" · "142.100 km · 52,1 L" · "84,92 €"
3. "18 sep 2026" · "7,26 L/100 km" · "141.400 km · 50,8 L" · "82,80 €"

Anchored bottom-right, fully inside the frame, an extended floating action button filled Bright Mint Wash (#7FF8D6), rounded rectangle, soft shadow, with a plus icon and the label "Nueva recarga".

At the very bottom, a short centred gesture handle pill. All copy is Spanish.

## Screen data

| Element | Value |
|---|---|
| Title | Toyota Corolla |
| Hero label | CONSUMO MEDIO HISTÓRICO |
| Hero value | 7,24 L/100 km |
| Hero caption | Media de 24 repostajes registrados |
| Caution badge | Tanque parcial |
| Action | Nueva recarga |

## Refinement prompts

- "Make the 7,24 figure dramatically larger — it should dominate the panel, with the unit label roughly a third of its size."
- "The decorative scalloped shape in the hero panel is too visible; drop its opacity so it reads as a faint texture."
- "Only the first entry should carry the partial-tank badge; remove it from the others."
