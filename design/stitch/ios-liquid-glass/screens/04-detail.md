# 04 · Vehicle detail — iOS (Liquid Glass)

**Generate with:** Stitch Standard Mode, with `../DESIGN.md` loaded into the project.
**Source:** `design/figma/08-ios-home-and-detail.figma.js` → frame `screen-detail`, 402 × 874.

## Prompt

Design a mobile vehicle detail screen for carApp on a 402 × 874 iPhone canvas.

Start with an ambient backdrop: a Cool Cloud Mist (#F2F2F7) base covered by five very large, heavily blurred overlapping circles in Soft Mint Glow (#BFF2E0), Pale Sky Glow (#E9F6FF) and Warm Amber Glow (#FFE9C7), bleeding off every edge as one continuous diffuse wash.

On top of that, an opaque scrolling content layer with 1rem side margins, beginning high enough that its top passes **underneath** the navigation bar. It contains, in order:

1. A centred thin circular loading spinner in Faint Graphite — a pull-to-refresh state.

2. A hero statistic card: softly rounded, filled **solid Vivid Deep Teal (#0A7C66)**, with a soft teal-tinted shadow. Inside, in white: the label "Consumo medio histórico" in medium 13px; below it the figure "7,24" in bold 44px with the unit "L/100 km" in medium 17px sitting on the same baseline immediately to its right; and beneath, "Media de 24 repostajes registrados" in 13px.

3. An inset grouped list of three refuelling entries as one softly rounded **fully opaque Pure Canvas White (#FFFFFF)** card, its rows divided by hairline separators inset to align with the row text. Each row has a top line with the date on the left in semi-bold 16px and the consumption right-aligned in semi-bold 16px Vivid Deep Teal, and a second line with odometer and litres on the left in 13px Muted Graphite and the cost right-aligned in 13px Muted Graphite.

   Row one additionally carries, beneath its two lines, a small capsule badge filled Warm Amber Glow (#FFE9C7) with a warning triangle and the label "Tanque parcial" in Caution Bronze (#B86B00).

   1. "15 oct 2026" · "7,10 L/100 km" · "142.850 km · 45,2 L" · "73,63 €" · with the badge
   2. "01 oct 2026" · "7,32 L/100 km" · "142.100 km · 52,1 L" · "84,92 €"
   3. "18 sep 2026" · "7,26 L/100 km" · "141.400 km · 50,8 L" · "82,80 €"

Floating above the content, pinned to the top, a full-width translucent frosted-glass navigation bar with a specular highlight along its bottom edge, containing the status bar, a "‹ Atrás" text back button on the left in Vivid Deep Teal, the centred title "Toyota Corolla" in semi-bold 17px, and a circular translucent glass icon button on the right holding a teal edit icon. Content must visibly blur where it passes beneath this bar.

Floating near the bottom, horizontally centred and entirely within the screen, a full-capsule button in solid Vivid Deep Teal with a plus icon and the white label "Nueva recarga", with a soft teal-tinted shadow.

At the very bottom, a centred rounded home indicator pill. All copy is Spanish.

## Screen data

| Element | Value |
|---|---|
| Nav | ‹ Atrás · Toyota Corolla · edit |
| Hero label | Consumo medio histórico |
| Hero value | 7,24 L/100 km |
| Hero caption | Media de 24 repostajes registrados |
| Caution badge | Tanque parcial |
| Action | Nueva recarga |

## Refinement prompts

- "The three entries belong in ONE rounded white card divided by inset hairlines, not three separate cards."
- "Keep the hero figure clearly dominant over the unit beside it — roughly two and a half times its size."
- "The hero card is solid teal, not glass; only the navigation bar and the icon button are glass."
