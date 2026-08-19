# 06 · Settings — iOS (Liquid Glass)

**Generate with:** Stitch Standard Mode, with `../DESIGN.md` loaded into the project.
**Source:** `design/figma/10-ios-settings.figma.js` → frame `screen-settings`, 402 × 874.

Presented as a sheet with a single "Cerrar" action — there is no save button, since settings apply immediately.

## Prompt

Design a mobile settings screen, presented as a sheet, on a 402 × 874 iPhone canvas.

Start with an ambient backdrop: a Cool Cloud Mist (#F2F2F7) base covered by four very large, heavily blurred overlapping circles in Soft Mint Glow (#BFF2E0), Pale Sky Glow (#E9F6FF) and Warm Amber Glow (#FFE9C7), reading as one diffuse wash of light. A status bar sits at the very top of the screen.

Covering most of the screen, a sheet with generously rounded top corners only, filled Cool Cloud Mist, with a soft upward shadow along its top edge. Pinned inside its top, a translucent frosted-glass toolbar with a specular edge containing a centred Faint Graphite grabber bar, "Cerrar" on the left in Vivid Deep Teal (#0A7C66), and the centred title "Ajustes" in semi-bold 17px. **There is no right-hand action.** Content scrolls beneath the toolbar.

Below, four inset grouped sections with 1rem side margins. Each has a capitalised 12px Muted Graphite header with expanded letter-spacing, indented to align with the row text, above a softly rounded **fully opaque Pure Canvas White (#FFFFFF)** card whose rows are separated by hairline separators inset from the left.

**"UNIDADES Y MONEDA"** — three rows, each with the value right-aligned in Muted Graphite followed by a thin chevron:
- "Moneda" → "EUR €"
- "Unidad de distancia" → "Kilómetros"
- "Unidad de volumen" → "Litros"

**"SEGURIDAD Y COPIAS"** — one row:
- "Copia de seguridad" with the subtitle "Última sincronización local guardada" beneath it, and on the right a small filled Vivid Deep Teal dot followed by "Activa" in medium 15px teal.

**"PRIVACIDAD"** — one row:
- "Compartir analíticas" with the subtitle "Comparte telemetría anónima para ayudarnos a mejorar carApp.", and on the right a switch in the **off** state — a neutral translucent grey capsule track with the white knob at the left.

**"CUENTA"** — two rows, each with a thin chevron on the right:
- "Cerrar sesión" in True Black Ink.
- "Eliminar cuenta" in **Alert Vermilion (#D93025)**.

Beneath the last section, centred, a two-line footer: "carApp v1.0.0" in medium 13px Muted Graphite and "Local-First & Safe Tracker" in 13px Faint Graphite.

At the very bottom of the screen, a centred rounded home indicator pill. All copy is Spanish.

## Screen data

| Section | Rows |
|---|---|
| UNIDADES Y MONEDA | Moneda → EUR € · Unidad de distancia → Kilómetros · Unidad de volumen → Litros |
| SEGURIDAD Y COPIAS | Copia de seguridad → teal dot + "Activa" |
| PRIVACIDAD | Compartir analíticas → switch off |
| CUENTA | Cerrar sesión · **Eliminar cuenta** (destructive) |
| Footer | carApp v1.0.0 / Local-First & Safe Tracker |

## Refinement prompts

- "The toolbar has only 'Cerrar' on the left and the centred title — no button on the right."
- "Only 'Eliminar cuenta' is vermilion; every other row label stays black."
- "Section headers sit above their card in small capitals, indented to align with the row labels."
