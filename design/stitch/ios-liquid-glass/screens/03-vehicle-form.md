# 03 · Vehicle form — iOS (Liquid Glass)

**Generate with:** Stitch Standard Mode, with `../DESIGN.md` loaded into the project.
**Source:** `design/figma/09-ios-forms.figma.js` → frame `screen-vehicle-form`, 402 × 874.

Note the platform divergence from Android: iOS has **no boxed input fields**. Text entry is a list row with the value right-aligned, and validation errors surface as a footnote beneath the group rather than under an individual field.

## Prompt

Design a mobile "add vehicle" form, presented as a sheet, on a 402 × 874 iPhone canvas.

Start with an ambient backdrop: a Cool Cloud Mist (#F2F2F7) base covered by four very large, heavily blurred overlapping circles in Soft Mint Glow (#BFF2E0), Pale Sky Glow (#E9F6FF) and Warm Amber Glow (#FFE9C7), bleeding off the edges as one diffuse wash of light. A status bar sits at the very top of the screen showing "9:41" and system icons.

Covering most of the screen from just below the status bar, a sheet with **generously rounded top corners only**, filled Cool Cloud Mist (#F2F2F7), with a soft shadow cast upward along its top edge.

Pinned inside the top of the sheet, a translucent frosted-glass toolbar with a specular edge, containing: a short rounded Faint Graphite grabber bar centred at the very top; "Cancelar" on the left in Vivid Deep Teal (#0A7C66); the title "Nuevo vehículo" centred in semi-bold 17px; and "Guardar" on the right in semi-bold Vivid Deep Teal. Sheet content scrolls beneath this toolbar.

Below, two inset grouped sections with 1rem side margins:

**Section header "INFORMACIÓN OBLIGATORIA"** in capitals, 12px Muted Graphite with expanded letter-spacing, indented to align with the row text. Beneath it, one softly rounded **fully opaque Pure Canvas White (#FFFFFF)** card containing two rows separated by a hairline separator inset from the left:
- "Nombre" — its label in **Alert Vermilion (#D93025)** to indicate a validation error, with the placeholder "Ej. Mi coche principal" right-aligned in Faint Graphite.
- "Odómetro inicial" — label in True Black Ink, value "124.500 km" right-aligned in Muted Graphite.

Directly beneath the card, a footnote in 12px **Alert Vermilion**, indented to the card's inner text: "Nombre es obligatorio. Rango admitido del odómetro: 0 – 2.000.000 km".

**Section header "DETALLES OPCIONALES"** in the same capitalised style. Beneath it, another opaque white rounded card with two rows separated by an inset hairline:
- "Marca" with placeholder "Ej. Toyota" right-aligned in Faint Graphite.
- "Modelo" with placeholder "Ej. Corolla" right-aligned in Faint Graphite.

At the very bottom of the screen, a centred rounded home indicator pill. All copy is Spanish.

## Screen data

| Element | Value |
|---|---|
| Toolbar | Cancelar · Nuevo vehículo · Guardar |
| Section 1 | INFORMACIÓN OBLIGATORIA |
| Row (error) | Nombre · Ej. Mi coche principal |
| Row | Odómetro inicial · 124.500 km |
| Error footnote | Nombre es obligatorio. Rango admitido del odómetro: 0 – 2.000.000 km |
| Section 2 | DETALLES OPCIONALES |
| Rows | Marca · Ej. Toyota / Modelo · Ej. Corolla |

## Refinement prompts

- "Do not draw boxed input fields — each field is a list row with the value right-aligned inside the grouped card."
- "The error is shown by turning the 'Nombre' label vermilion plus a vermilion footnote beneath the whole group, not by outlining the row."
- "Separators must be inset from the left to align with the row labels, not run the full card width."
