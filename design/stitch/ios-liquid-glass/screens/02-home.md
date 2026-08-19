# 02 · Home / Vehicle list — iOS (Liquid Glass)

**Generate with:** Stitch Standard Mode, with `../DESIGN.md` loaded into the project.
**Source:** `design/figma/08-ios-home-and-detail.figma.js` → frame `screen-home`, 402 × 874.

This is the clearest demonstration of the layering rule. The vehicle cards must physically pass **behind** the glass bar and blur through it.

## Prompt

Design a mobile vehicle-list home screen for carApp on a 402 × 874 iPhone canvas.

Start with an ambient backdrop: a Cool Cloud Mist (#F2F2F7) base covered by five very large, heavily blurred overlapping circles in Soft Mint Glow (#BFF2E0), Pale Sky Glow (#E9F6FF) and Warm Amber Glow (#FFE9C7), bleeding off every edge and reading as one continuous diffuse wash of light.

On top of that, an opaque scrolling content layer with 1rem side margins, whose top begins high enough that its first element passes **underneath** the navigation bar. It contains:
- A small status row: a filled Vivid Deep Teal (#0A7C66) dot followed by "Sincronizado localmente" in 13px Muted Graphite.
- Two vehicle cards, each a softly rounded **fully opaque Pure Canvas White (#FFFFFF)** card with a very soft shadow, containing: a circular container with a faint teal tint holding a thin car icon in Vivid Deep Teal; beside it the vehicle name in semi-bold 17px and the manufacturer beneath in 15px Muted Graphite; a thin chevron on the right; a hairline separator inset to align with the text; and a bottom row with "Último odómetro" in 13px Muted Graphite on the left and the reading right-aligned in semi-bold 17px Vivid Deep Teal.

Card one: "Toyota Corolla" / "Toyota" / "142.500 km".
Card two: "Volkswagen Golf" / "Volkswagen" / "42.105 km".

Floating above that content, pinned to the top of the screen, a full-width translucent frosted-glass navigation bar with a specular highlight along its bottom edge. It contains the status bar ("9:41" and system icons), the centred title "Mis vehículos" in semi-bold 17px, and on the right a circular translucent glass icon button holding a settings icon in Vivid Deep Teal. **The top of the first vehicle card must be visibly blurred where it passes beneath this bar** — that overlap is the entire point of the design.

Floating above the content near the bottom, **horizontally centred** and entirely inside the screen, a full-capsule button in solid Vivid Deep Teal (#0A7C66) with a plus icon and the white label "Añadir vehículo", carrying a soft teal-tinted shadow.

At the very bottom, a centred rounded home indicator pill. All copy is Spanish.

## Screen data

| Element | Value |
|---|---|
| Title | Mis vehículos |
| Sync status | Sincronizado localmente |
| Vehicle 1 | Toyota Corolla · Toyota · 142.500 km |
| Vehicle 2 | Volkswagen Golf · Volkswagen · 42.105 km |
| Row label | Último odómetro |
| Action | Añadir vehículo |

## Refinement prompts

- "Move the content up so the first card clearly slides under the glass navigation bar and blurs through it — do not start the content below the bar."
- "The vehicle cards must be fully opaque white. They are content, not chrome — never glass."
- "Centre the action button horizontally and keep it fully within the frame."
