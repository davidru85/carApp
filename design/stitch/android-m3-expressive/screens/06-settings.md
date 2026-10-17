# 06 · Settings — Android (M3 Expressive)

**Generate with:** Stitch Standard Mode, with `../DESIGN.md` loaded into the project.
**Source:** `design/figma/06-android-settings.figma.js` → frame `screen-settings`, 412 × 917.

## Prompt

Design a mobile settings screen for carApp on a 412 × 917 Android canvas. Background Pale Mint Paper (#F5FBF7).

A slim status bar, then a compact top app bar with a back arrow on the left and the title "Ajustes" in semi-bold 22px beside it.

Below, four grouped sections with 1rem side margins and generous 1.5rem spacing between them. Each section has a small semi-bold 14px header in Deep Pine Teal (#006A57) sitting above a boldly rounded card filled Faint Mint Surface (#EFF5F1), whose rows are separated by full-width hairline dividers in Whisper Sage (#BFC9C4).

**Section "Unidades y moneda"** — three rows:
- "Moneda" with the subtitle "Divisa predeterminada de los repostajes" beneath it, and on the right the value "EUR €" in medium Muted Slate Green followed by a chevron.
- "Unidad de distancia" with the value "Kilómetros" and a chevron.
- "Unidad de volumen" with the value "Litros" and a chevron.

**Section "Seguridad y copias"** — one row:
- "Copia de seguridad" with the subtitle "Última sincronización local guardada", and on the right a small status chip filled Bright Mint Wash (#7FF8D6) containing a filled Deep Pine Teal dot and the label "Activa".

**Section "Privacidad"** — one row:
- "Compartir analíticas" with the subtitle "Comparte telemetría anónima para ayudarnos a mejorar carApp." and, on the right, a switch in the **off** state: a hollow outlined capsule track with a small low-contrast knob at the left.

**Section "Cuenta"** — two rows:
- "Cerrar sesión" with a sign-out icon on the right in Muted Slate Green.
- "Eliminar cuenta" with its label in Signal Crimson (#BA1A1A) and a trash icon on the right, also in Signal Crimson.

Beneath the last section, centred, a two-line footer: "carApp v1.0.0" in medium 12px and "Local-First & Safe Tracker" in regular 12px, both in Muted Slate Green (#3F4945).

At the very bottom, a short centred gesture handle pill. All copy is Spanish.

## Screen data

| Section | Rows |
|---|---|
| Unidades y moneda | Moneda → EUR € · Unidad de distancia → Kilómetros · Unidad de volumen → Litros |
| Seguridad y copias | Copia de seguridad → chip "Activa" |
| Privacidad | Compartir analíticas → switch off |
| Cuenta | Cerrar sesión · **Eliminar cuenta** (destructive) |
| Footer | carApp v1.0.0 / Local-First & Safe Tracker |

## Anonymous account conversion (E4-05)

Approved by the owner on 2026-10-16. Shown only while the session is anonymous; local, permanent
and every other session phase show none of it (`D-224`). The copy below is Spanish sample
content, like the rest of this screen.

- **Placement:** the first row of the "Cuenta" card, above the local-data deletion row.
- **Row:** the title "Vincular una cuenta" in semi-bold Deep Forest Ink (#171D1A), exposed as a
  heading to screen readers; the subtitle "Vincula una cuenta de Google para poder recuperar tus
  vehículos y repostajes en otro dispositivo. Seguirán también en este dispositivo." in Muted Slate
  Green (#3F4945); below it a full-width, fully pill-shaped outlined button "Continuar con Google"
  with a Soft Sage Gray (#6F7975) hairline outline and dark text — the outlined style this system
  reserves for third-party sign-in.
- **Busy:** the button is disabled and the loading indicator in Deep Pine Teal (#006A57) appears
  beneath it; its accessible label is "Vinculando la cuenta…".
- **Credential collision:** a Material 3 alert dialog titled "Esta cuenta ya tiene datos", with the
  body "Esto sustituye permanentemente todos los vehículos y repostajes de la cuenta existente por
  los datos de este dispositivo. No se puede deshacer.", a "Cancelar" text button and a
  "Sustituir datos" text button in Signal Crimson (#BA1A1A). Tapping outside the dialog or pressing
  back cancels.
- **Success:** the row disappears and the permanent-account rows "Cerrar sesión" and
  "Eliminar cuenta" take its place.

| Section | Rows |
|---|---|
| Cuenta (anonymous session only) | Vincular una cuenta → Continuar con Google · local-data deletion row |

## Refinement prompts

- "Only 'Eliminar cuenta' should be crimson — every other row label stays dark."
- "Section headers should sit outside the card, above it, in teal — not inside the card as a first row."
- "Dividers should run the full interior width of the card, edge to edge."
