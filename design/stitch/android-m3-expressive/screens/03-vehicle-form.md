# 03 · Vehicle form — Android (M3 Expressive)

**Generate with:** Stitch Standard Mode, with `../DESIGN.md` loaded into the project.
**Source:** `design/figma/04-android-vehicle-form-and-detail.figma.js` → frame `screen-vehicle-form`, 412 × 917.

This screen exists to demonstrate the **three field interaction states side by side** — error, focused and resting. Keep all three visible in any regeneration.

## Prompt

Design a mobile "add vehicle" form screen for carApp on a 412 × 917 Android canvas. Background Pale Mint Paper (#F5FBF7).

A slim status bar at the top, then a compact top app bar containing a back arrow on the left, the title "Nuevo vehículo" in semi-bold 22px beside it, and on the right a compact pill-shaped tonal button filled Calm Eucalyptus (#CDE9DD) labelled "Guardar" in dark green.

Below, a form with 1rem side margins, organised into two groups separated by generous vertical space.

**Group one**, introduced by the section header "Información obligatoria" in semi-bold 14px Deep Pine Teal (#006A57), containing two filled input fields. Each field is filled Quiet Stone Green (#DEE4E0), rounded at the top corners only and flat along the bottom, with a small caption label above the value inside the same container, and a coloured underline along the bottom edge.

- First field — **error state**: label "Nombre" in Signal Crimson (#BA1A1A), placeholder text "Ej. Mi coche principal" in muted grey, a thick Signal Crimson underline, and beneath the field a short crimson message "Este campo es obligatorio", indented to align with the field's inner text.
- Second field — **focused state**: label "Odómetro inicial" in Deep Pine Teal (#006A57), value "124.500" in dark text, the unit "km" right-aligned inside the field in Muted Slate Green, a thick Deep Pine Teal underline, and beneath it the supporting text "Rango admitido: 0 – 2.000.000 km" in Muted Slate Green, indented to the same inner alignment.

**Group two**, introduced by the section header "Detalles opcionales" in semi-bold 14px Deep Pine Teal, containing two fields in the **resting state** — thin Soft Sage Gray (#6F7975) underline, Muted Slate Green labels, no supporting text:
- "Marca" with placeholder "Ej. Toyota"
- "Modelo" with placeholder "Ej. Corolla"

All four fields are full width within the margins and identically sized. At the very bottom, a short centred gesture handle pill.

All copy is Spanish.

## Screen data

| Element | Value |
|---|---|
| Title / action | Nuevo vehículo · Guardar |
| Section 1 | Información obligatoria |
| Field 1 (error) | Nombre · Ej. Mi coche principal · Este campo es obligatorio |
| Field 2 (focused) | Odómetro inicial · 124.500 · km · Rango admitido: 0 – 2.000.000 km |
| Section 2 | Detalles opcionales |
| Fields 3–4 (resting) | Marca · Ej. Toyota / Modelo · Ej. Corolla |

## Refinement prompts

- "All four fields must be exactly the same width — the two with supporting text underneath are currently narrower; the supporting text should be indented on its own without shrinking the field."
- "Make the difference between the three field states more obvious: thin grey underline at rest, thick teal when focused, thick crimson on error."
- "Round only the top corners of the fields; the bottom edge should be flat where the underline sits."
