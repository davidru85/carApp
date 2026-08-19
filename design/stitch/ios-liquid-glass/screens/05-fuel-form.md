# 05 · Refuelling form — iOS (Liquid Glass)

**Generate with:** Stitch Standard Mode, with `../DESIGN.md` loaded into the project.
**Source:** `design/figma/09-ios-forms.figma.js` → frame `screen-fuel-form`, 402 × 874.

Carries the glass segmented control and both switch states. Note the segment labels are Spanish here — the original concept board shipped them untranslated.

## Prompt

Design a mobile "new refuelling entry" form, presented as a sheet, on a 402 × 874 iPhone canvas.

Start with an ambient backdrop: a Cool Cloud Mist (#F2F2F7) base covered by four very large, heavily blurred overlapping circles in Soft Mint Glow (#BFF2E0), Pale Sky Glow (#E9F6FF) and Warm Amber Glow (#FFE9C7), reading as one diffuse wash. A status bar sits at the very top of the screen.

Covering most of the screen, a sheet with generously rounded top corners only, filled Cool Cloud Mist, with a soft upward shadow along its top edge. Pinned inside its top, a translucent frosted-glass toolbar with a specular edge containing a centred Faint Graphite grabber bar, "Cancelar" on the left in Vivid Deep Teal (#0A7C66), the centred title "Nueva recarga" in semi-bold 17px, and "Guardar" on the right in semi-bold teal. Content scrolls beneath the toolbar.

Below, with 1rem side margins:

**Section "REPOSTAJE"** (capitalised 12px Muted Graphite header, indented) above a softly rounded **fully opaque white** card with two rows separated by an inset hairline:
- "Fecha" with a small teal calendar icon before the label, and on the right a small rounded translucent grey bubble containing "15/10/2026".
- "Odómetro actual" with "142.850 km" right-aligned in Muted Graphite.

**Section "MÉTODO DE ENTRADA"** above a segmented control: a full-capsule **translucent frosted-glass** track with a small inner inset, split into three equal segments. The first is selected — an opaque white capsule with a soft shadow and a semi-bold label. Segments read "L + Precio/L", "L + Total", "Precio/L + Total" in 13px, unselected labels in Muted Graphite.

**An unlabelled group** — a softly rounded opaque white card with three rows separated by inset hairlines:
- "Litros" → "45,20 L"
- "Precio por litro" → "1,629 €/L"
- "Total calculado" → "73,63 €" right-aligned in **semi-bold Vivid Deep Teal (#0A7C66)**

Directly beneath, a 12px Muted Graphite footnote indented to the card's inner text: "El total se calcula automáticamente a partir de los litros y el precio por litro."

**Section "OPCIONES"** above a softly rounded opaque white card with three rows separated by inset hairlines:
- "Tanque lleno" with the subtitle "Si se desmarca, este repostaje se tratará como tanque parcial." beneath it, and on the right a switch in the **on** state — capsule track filled Vivid Deep Teal with a large white knob at the right carrying a soft shadow.
- "Repostajes omitidos" with the subtitle "Indica si no has registrado alguna carga anterior.", and on the right a switch in the **off** state — neutral translucent grey track with the white knob at the left.
- "Notas" with "Opcional" right-aligned in Faint Graphite and a thin chevron after it.

At the very bottom of the screen, a centred rounded home indicator pill. All copy is Spanish.

## Screen data

| Element | Value |
|---|---|
| Toolbar | Cancelar · Nueva recarga · Guardar |
| Date | 15/10/2026 |
| Odometer | Odómetro actual · 142.850 km |
| Entry mode | L + Precio/L *(selected)* · L + Total · Precio/L + Total |
| Volume / price | 45,20 L · 1,629 €/L |
| Computed total | Total calculado · 73,63 € |
| Footnote | El total se calcula automáticamente a partir de los litros y el precio por litro. |
| Switch on | Tanque lleno |
| Switch off | Repostajes omitidos |
| Notes | Notas · Opcional |

## Refinement prompts

- "The segmented control track is translucent glass; only the selected segment is an opaque white capsule with a soft shadow."
- "Segment labels must be in Spanish — 'L + Precio/L', not 'L + Price/L'."
- "Only the 'Total calculado' value is teal and semi-bold; the other two values stay regular Muted Graphite."
