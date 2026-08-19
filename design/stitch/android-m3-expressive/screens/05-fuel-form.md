# 05 · Refuelling form — Android (M3 Expressive)

**Generate with:** Stitch Standard Mode, with `../DESIGN.md` loaded into the project.
**Source:** `design/figma/05-android-fuel-form.figma.js` → frame `screen-fuel-form`, 412 × 917.

The densest screen in the app. It carries the segmented control, the paired numeric inputs, both switch states and the collapsible group.

## Prompt

Design a mobile "new refuelling entry" form screen for carApp on a 412 × 917 Android canvas. Background Pale Mint Paper (#F5FBF7).

A slim status bar, then a compact top app bar with a close (×) icon on the left, the title "Nueva recarga" in semi-bold 22px beside it, and on the right a compact pill-shaped button filled Deep Pine Teal (#006A57) labelled "Guardar" in white.

Below, a scrolling form with 1rem side margins and consistent 1rem gaps between blocks:

1. A filled input field — rounded at the top only, filled Quiet Stone Green (#DEE4E0), thin underline — with a calendar icon on the left, the caption label "Fecha" above the value "15/10/2026".

2. A second filled input field with the label "Odómetro actual", the value "142.850", and the unit "km" right-aligned inside the field.

3. The section header "Método de entrada" in semi-bold 14px Deep Pine Teal (#006A57), followed by a segmented control: one fully pill-shaped outlined track split into three equal segments by hairlines. The first segment is selected — filled Calm Eucalyptus (#CDE9DD) with a check mark before its label. Segments read "L + Precio/L", "L + Total", "Precio/L + Total", all in 12px.

4. A row of two half-width filled input fields side by side: "Litros" with value "45,20" and unit "L"; "Precio por litro" with value "1,629" and unit "€/L".

5. A computed-total panel: a rounded container filled Warm Honey Wash (#FFDEA6), containing the caption "Total calculado" above the value "73,63 €" in bold 24px Burnished Amber (#7C5800), with a calculator icon on the far right.

6. A switch row: a rounded container filled Faint Mint Surface (#EFF5F1) with "Tanque lleno" in medium 16px and beneath it "Si se desmarca, este repostaje se tratará como tanque parcial." in 12px Muted Slate Green. On the right, a switch in the **on** state — track filled Deep Pine Teal (#006A57) with a large white knob carrying a teal check mark.

7. A collapsible group: a rounded container filled slightly deeper than the cards, with a header row reading "Más opciones" in semi-bold 16px and an upward chevron on the right, indicating it is expanded. Inside it:
   - A switch row with "Repostajes omitidos" and beneath it "Indica si no has registrado alguna carga anterior.", with a switch in the **off** state — a hollow outlined capsule track with a small low-contrast knob at the left.
   - A filled input field labelled "Notas (opcional)" with the placeholder "Ej. Gasolinera de la autovía A-6".

At the very bottom, a short centred gesture handle pill. All copy is Spanish.

## Screen data

| Element | Value |
|---|---|
| Title / action | Nueva recarga · Guardar |
| Date | 15/10/2026 |
| Odometer | Odómetro actual · 142.850 km |
| Entry mode | L + Precio/L *(selected)* · L + Total · Precio/L + Total |
| Volume / price | 45,20 L · 1,629 €/L |
| Computed total | Total calculado · 73,63 € |
| Switch on | Tanque lleno |
| Switch off | Repostajes omitidos |
| Notes | Ej. Gasolinera de la autovía A-6 |

## Refinement prompts

- "The selected segment needs a check mark before its label — selection must not be communicated by colour alone."
- "Make the two switches clearly different: the on switch has a large white knob with a teal check on a filled teal track; the off switch has a small knob on a hollow outlined track."
- "Keep the two numeric fields exactly equal in width with a small gap between them."
