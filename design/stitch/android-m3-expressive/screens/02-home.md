# 02 · Home / Vehicle list — Android (M3 Expressive)

**Generate with:** Stitch Standard Mode, with `../DESIGN.md` loaded into the project.
**Source:** `design/figma/03-android-home.figma.js` → frame `screen-home`, 412 × 917.

## Prompt

Design a mobile vehicle-list home screen for carApp, a local-first fuel-tracking app, on a 412 × 917 Android canvas. Background Pale Mint Paper (#F5FBF7).

A slim status bar at the top showing "9:41" and signal, wifi and battery icons.

Below it, a tall top app bar. In its upper right, a circular tonal icon button filled Calm Eucalyptus (#CDE9DD) containing a settings icon in dark green. Low in the bar and left-aligned with a 1.5rem margin, the screen title "Mis vehículos" in bold 32px Deep Forest Ink (#171D1A). Directly beneath the title, a small outlined chip with a filled Deep Pine Teal (#006A57) dot followed by "Sincronizado localmente" in 12px Muted Slate Green (#3F4945).

Below the bar, a list of two vehicle cards with 1rem side margins and 0.75rem between them. Each card is boldly rounded, filled Faint Mint Surface (#EFF5F1), lifted by a soft close-cast shadow, with roomy internal padding, and laid out as:
- A header row: a circular container filled Bright Mint Wash (#7FF8D6) holding an outlined car icon in dark green; beside it the vehicle name in semi-bold 20px and the manufacturer beneath it in 14px Muted Slate Green (#3F4945); a chevron on the far right.
- A full-width hairline divider in Whisper Sage (#BFC9C4).
- A statistics row with the label "Último odómetro" on the left in small capitals-weight Muted Slate Green, and the reading right-aligned in semi-bold 18px Deep Pine Teal (#006A57).

Card one: "Toyota Corolla" / "Toyota" / "142.500 km".
Card two: "Volkswagen Golf" / "Volkswagen" / "42.105 km".

Anchored bottom-right with comfortable margin from both edges — and entirely inside the screen, never clipped — an extended floating action button filled Bright Mint Wash (#7FF8D6), a rounded rectangle rather than a pill, lifted by a noticeable soft shadow, containing a plus icon and the label "Añadir vehículo" in dark green semi-bold.

At the very bottom, a short centred gesture handle pill.

All copy is Spanish.

## Screen data

| Element | Value |
|---|---|
| Title | Mis vehículos |
| Sync chip | Sincronizado localmente |
| Vehicle 1 | Toyota Corolla · Toyota · 142.500 km |
| Vehicle 2 | Volkswagen Golf · Volkswagen · 42.105 km |
| Row label | Último odómetro |
| Action | Añadir vehículo |

## Refinement prompts

- "Increase the spacing between the title and the sync chip, and let the title sit lower in the bar."
- "The floating action button must sit fully inside the frame with at least 1rem clearance from the right edge — shorten the label before allowing any overflow."
- "Make the odometer readings noticeably larger and bolder than the label beside them."
