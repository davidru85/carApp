# Design - carApp

**Entry point for the visual design of the product.** This document states what carApp looks
like in general terms and indexes every design asset in [`design/stitch/`](../design/stitch/).
It exists so that an agent or a human implementing a screen can find the design description of
that screen in one hop, without reading the design folders end to end.

> **Non-normative.** This document and everything it indexes are **design references**. They
> create no rules. They have no authority over behaviour (`docs/SPECIFICATION.md`), over
> representation (`docs/CONTRACTS.md`) or over allowed technologies (`docs/DECISION_BOARD.md`).
> Where a design asset and a normative document disagree, the normative document wins and the
> discrepancy MUST be escalated rather than resolved in the UI. Document authority is defined
> canonically in [AGENTS.md](../AGENTS.md); this document does not restate it.

## 1. What the design is

carApp ships **two design systems for one product**. The platforms share the information
architecture, the flows, the data on screen and the interface copy, and share almost nothing of
their visual language.

| | Android | iOS |
|---|---|---|
| Design system | Material 3 Expressive | Liquid Glass |
| Source of depth | Filled tonal containers, elevation, bold shape | Translucency, background blur, specular light |
| Accent | Deep Pine Teal `#006A57` | Vivid Deep Teal `#0A7C66` |
| Page base | Pale Mint Paper `#F5FBF7` | Cool Cloud Mist `#F2F2F7` under an ambient colour wash |
| Content surfaces | Faint Mint Surface `#EFF5F1`, boldly rounded | Pure Canvas White `#FFFFFF`, fully opaque |
| Typeface | Roboto Flex | Inter, standing in for the Apple system face |
| Chrome | Top app bars, extended FAB | Floating glass navigation bars and toolbars |
| Caution / error | Burnished Amber `#7C5800` / Signal Crimson `#BA1A1A` | Caution Bronze `#B86B00` / Alert Vermilion `#D93025` |

Three properties are common to both platforms and are the product-level design intent:

- **The number is the product.** Consumption figures and odometer readings carry display-scale
  typographic weight, far larger than their labels, because the answer must be readable at arm's
  length at a petrol station in daylight.
- **Warm accents mean "look again", never decoration.** Amber and bronze are reserved for values
  that are derived rather than entered, and for partial tanks. Red is reserved for genuine errors
  and destructive actions.
- **Legibility outranks effect.** On iOS this is explicit: glass is chrome only, and text always
  sits on a fully opaque surface.

The two systems are deliberately kept as **separate design projects**. Merging them would average
away the platform-native distinction the redesign exists to express.

## 2. Where the design lives

```text
design/
├── figma/     Figma Plugin API build scripts — pixel-exact, machine-executable
└── stitch/    DESIGN.md design systems + screen prompts — agent-readable, the index below
```

[`design/stitch/`](../design/stitch/) is the **primary reference for implementation**. It is plain
markdown, so any coding agent can read it directly and generate matching UI, with or without
[Google Stitch](https://stitch.withgoogle.com) in the loop. Its `DESIGN.md` files follow Stitch's
own six-section interchange format for design systems.

[`design/figma/`](../design/figma/) is the **pixel-exact source of truth**. It holds imperative
scripts that build the screens inside a live Figma file, resolving every colour through named
variable collections. Use it when an exact value, geometry or node id is needed. It cannot be
mechanically derived from the Stitch files, and vice versa; the relationship between the two
folders is explained in [`design/stitch/README.md`](../design/stitch/README.md).

Neither folder is inside `docs/` deliberately: design assets acquire no authority over behaviour
or contracts by being referenced from here.

## 3. Design system index

Read the platform design system before reading any screen file. Each is self-contained and
covers visual theme, colour palette with roles, typography, component stylings, layout principles
and generation guidance.

Note the name collision: **this** document is `docs/DESIGN.md`, the project-level entry point.
The two files below are also called `DESIGN.md` because that filename is required by Stitch's
interchange format. They are platform design systems, not entry points.

| Platform | Document | Covers |
|---|---|---|
| Android | [design/stitch/android-m3-expressive/DESIGN.md](../design/stitch/android-m3-expressive/DESIGN.md) | Material 3 Expressive: mint-and-teal palette, Roboto Flex type scale, bold shape language, filled containers, rendered interaction states. |
| iOS | [design/stitch/ios-liquid-glass/DESIGN.md](../design/stitch/ios-liquid-glass/DESIGN.md) | Liquid Glass: ambient wash, the three glass materials, specular edge treatment, Inter type scale, opaque content plane, and four rules that must not be broken. |

Folder-level orientation, the mapping back to the Figma scripts, and the caveats of the
translation are in [design/stitch/README.md](../design/stitch/README.md) and
[design/figma/README.md](../design/figma/README.md).

## 4. Screen index

Six screens per platform, one file each. Every screen file contains a **Prompt** (a full
descriptive rendering of the screen), a **Screen data** table with the exact strings and values
so content stays identical across regenerations and across platforms, and **Refinement prompts**
targeting what a regeneration usually gets wrong.

| # | Screen | Android | iOS | Related flow / story |
|---|---|---|---|---|
| 01 | Welcome | [01-welcome.md](../design/stitch/android-m3-expressive/screens/01-welcome.md) | [01-welcome.md](../design/stitch/ios-liquid-glass/screens/01-welcome.md) | `F-1` first launch and authentication |
| 02 | Home / vehicle list | [02-home.md](../design/stitch/android-m3-expressive/screens/02-home.md) | [02-home.md](../design/stitch/ios-liquid-glass/screens/02-home.md) | `E1-07`, `E1-09` |
| 03 | Vehicle form | [03-vehicle-form.md](../design/stitch/android-m3-expressive/screens/03-vehicle-form.md) | [03-vehicle-form.md](../design/stitch/ios-liquid-glass/screens/03-vehicle-form.md) | `F-2` first vehicle creation |
| 04 | Vehicle detail | [04-detail.md](../design/stitch/android-m3-expressive/screens/04-detail.md) | [04-detail.md](../design/stitch/ios-liquid-glass/screens/04-detail.md) | `R-3` consumption display, `E1-08` |
| 05 | Refuelling form | [05-fuel-form.md](../design/stitch/android-m3-expressive/screens/05-fuel-form.md) | [05-fuel-form.md](../design/stitch/ios-liquid-glass/screens/05-fuel-form.md) | `F-3` fuel logging, `R-1`, `R-2` |
| 06 | Settings | [06-settings.md](../design/stitch/android-m3-expressive/screens/06-settings.md) | [06-settings.md](../design/stitch/ios-liquid-glass/screens/06-settings.md) | `E4-01` settings UI |

Canvas geometry is **412 × 917** on Android and **402 × 874** on iOS.

The flow and story column is orientation, not a contract. The authoritative definition of what a
screen must do is the flow in `docs/SPECIFICATION.md §7` and the acceptance criteria of the
backlog story, never the design file.

## 5. How to use this when implementing a screen

1. Read [AGENTS.md](../AGENTS.md) and the assigned story in `docs/BACKLOG.md`.
2. Read the governing behaviour in `docs/SPECIFICATION.md` and the governing types, states and
   error mappings in `docs/CONTRACTS.md`. These decide what the screen does.
3. Read the platform design system in §3 above. It decides how it looks.
4. Read the screen file in §4 above for that screen's composition, copy and data.
5. Implement. Where the design shows something the specification does not define, or defines
   differently, the specification wins — see §7.

## 6. Constraints and known gaps

- **No dark theme exists.** Free and Starter Figma plans cap variable collections at one mode, so
  Light/Dark could not be expressed as variable modes. Both token sets are single-mode. A dark
  theme is undesigned work, not an omission from the files.
- **Screen copy is Spanish sample content, not the localization source.** The screens are drawn in
  Spanish (Spain) with `7,24` / `142.500` number formatting and euro after the value. English is
  required from day one (`docs/SPECIFICATION.md §11`), and localized strings live in resource
  files with English keys, never in these design assets. Layouts must tolerate the longer of the
  two languages.
- **Accessibility has not been verified against the design.** `docs/SPECIFICATION.md §11` requires
  WCAG AA contrast, content labels, and usability at 200% system font size. The designs were drawn
  for legibility but no contrast audit or 200%-scale reflow test has been run on them. `E4-02` owns
  this, and may require design changes.
- **Three iOS screens have never been rendered in Figma.** `09-ios-forms.figma.js` and
  `10-ios-settings.figma.js` are complete but unrun; Figma's Starter plan allows 20 MCP tool calls
  per month and the quota was exhausted. Their Stitch equivalents exist and are complete.
- **The Stitch assets have not been validated against a live Stitch project.** The `Project ID`
  field in both `DESIGN.md` files is still a placeholder.
- **Not every state is designed.** The screens show the happy path plus the interaction states the
  Figma build rendered explicitly. Loading, empty and error states, the two-step odometer warning
  dialog and the no-consumption explanations are specified normatively but not drawn; derive them
  from the design system rather than inventing a new visual language.
- **Map integration and real-time status updates are not designed.** They appear in no screen and
  are out of MVP scope (`docs/SPECIFICATION.md §3.2`).

## 7. Relationship to the normative documents

When a design asset appears to decide something, check this table first. In every row the
normative document wins.

| The design shows | Who actually decides |
|---|---|
| A field, its label and its position | `docs/SPECIFICATION.md §5` domain model, `docs/CONTRACTS.md §20` types |
| A number's format, rounding or units | `docs/CONTRACTS.md §2` monetary and scaled-value arithmetic |
| A warning, badge or error message | `docs/SPECIFICATION.md §6` business rules, `docs/CONTRACTS.md` error taxonomy |
| A sync or backup status indicator | `docs/SPECIFICATION.md §9`, `docs/CONTRACTS.md` sync state machine |
| Which rows exist in settings | `docs/SPECIFICATION.md §3.1` |
| Any interface string | Localized resources; `UiState` carries no user-facing text |

A design asset that contradicts one of these is a defect in the design asset. Fix the design or
escalate; do not implement the contradiction.

## 8. Changing the design

- Markdown has no variable bindings. Changing a colour means editing the platform `DESIGN.md` and
  regenerating the screens; the screen files will not follow automatically.
- A change that alters what a screen *does*, rather than how it looks, is a specification change
  first. Escalate it before touching the design files.
- Design changes are not gated by `AGENTS.md`, because `design/**` is not a gated path. Adding a
  rule to this document, however, would be — this document is a reference and MUST NOT introduce
  rules.
- Record significant design work in `docs/PROJECT_LOG.md`, as the redesign itself was.
