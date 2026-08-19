# Google Stitch design assets

Design-system and screen-generation assets for [Google Stitch](https://stitch.withgoogle.com),
adapted from the Figma build scripts in [`../figma/`](../figma/).

**Non-normative.** This folder is design tooling. It is not part of the documentation set
governed by `AGENTS.md` and carries no authority over behaviour or contracts.

## Why this is not a copy of `../figma/`

The two folders describe the same designs in fundamentally different ways, and neither can be
mechanically converted into the other.

`../figma/` holds **imperative programs**. They call `figma.createFrame()`, resolve colours
through named Figma variable collections, and hardcode node ids like `14:2`. They only mean
anything inside a Figma execution context, and Stitch cannot run them.

This folder holds **declarative design descriptions**. Stitch reads design rules from a
`DESIGN.md` file and generates screens from natural-language prompts. Its guiding principle is
that design intent should be expressed in descriptive language rather than technical values —
"subtly rounded corners", not `border-radius: 8px`. So the adaptation is a genuine translation:
token tables became named colours with described roles, and layout code became prose that
describes what a screen contains and how it should feel.

## Format provenance

`DESIGN.md` is Stitch's own interchange format for design systems, described by Google as "an
agent-friendly markdown file" for importing and exporting design rules across tools. The format
is open source; both files here follow the six-section structure from the official example in
[`google-labs-code/stitch-skills`](https://github.com/google-labs-code/stitch-skills/tree/main/plugins/stitch-utilities/skills/design-md).

Because it is plain markdown, these files are also readable by coding agents directly — Claude
Code, Gemini CLI, Cursor and Antigravity can all consume a `DESIGN.md` to generate matching UI
without Stitch in the loop.

## Structure

```
design/stitch/
├── android-m3-expressive/
│   ├── DESIGN.md              Material 3 Expressive design system
│   └── screens/               one paste-ready prompt per screen
│       ├── 01-welcome.md
│       ├── 02-home.md
│       ├── 03-vehicle-form.md
│       ├── 04-detail.md
│       ├── 05-fuel-form.md
│       └── 06-settings.md
└── ios-liquid-glass/
    ├── DESIGN.md              Liquid Glass design system
    └── screens/               (same six screens)
```

Two design systems, because they genuinely are two — the platforms share information
architecture and copy but almost no visual language. Keep them as **separate Stitch projects**;
merging them would average away the distinction the whole redesign exists to express.

## How to use

1. Create a Stitch project per platform at [stitch.withgoogle.com](https://stitch.withgoogle.com).
2. Import the platform's `DESIGN.md` as the project's design system.
3. Fill in the `Project ID` placeholder at the top of that `DESIGN.md` so the file round-trips
   cleanly on future exports.
4. For each screen, paste the **Prompt** section of its file into Stitch Standard Mode.
   Standard Mode is the one that supports Figma export; Experimental Mode takes image and
   wireframe input instead.
5. Iterate using the **Refinement prompts** at the bottom of each screen file. These target the
   specific things a regeneration tends to get wrong — usually the glass layering on iOS and the
   hero statistic scale on Android.

Each screen file also carries a **Screen data** table with the exact strings and values, so
content stays identical across regenerations and across platforms.

## Mapping from the Figma scripts

| Figma script | Stitch equivalent |
|---|---|
| `00-pages-and-m3-tokens.figma.js` | `android-m3-expressive/DESIGN.md` |
| `01-liquid-glass-tokens.figma.js` | `ios-liquid-glass/DESIGN.md` |
| `02-android-welcome.figma.js` | `android-m3-expressive/screens/01-welcome.md` |
| `03-android-home.figma.js` | `android-m3-expressive/screens/02-home.md` |
| `04-android-vehicle-form-and-detail.figma.js` | `android-m3-expressive/screens/03-vehicle-form.md`, `04-detail.md` |
| `05-android-fuel-form.figma.js` | `android-m3-expressive/screens/05-fuel-form.md` |
| `06-android-settings.figma.js` | `android-m3-expressive/screens/06-settings.md` |
| `07-ios-welcome.figma.js` | `ios-liquid-glass/screens/01-welcome.md` |
| `08-ios-home-and-detail.figma.js` | `ios-liquid-glass/screens/02-home.md`, `04-detail.md` |
| `09-ios-forms.figma.js` | `ios-liquid-glass/screens/03-vehicle-form.md`, `05-fuel-form.md` |
| `10-ios-settings.figma.js` | `ios-liquid-glass/screens/06-settings.md` |

Two scripts built two screens each, which is why the counts differ: 11 scripts become 2 design
systems plus 12 screen prompts.

## What survived the translation, and what did not

**Carried over faithfully:** every colour with its exact hex value, the type scale and weights,
component shape rules, layout geometry and canvas sizes, all interface copy and data values,
and the interaction states the Figma build deliberately rendered (Android's pressed shape-morph
and three field states; iOS's on/off switches and selected segment).

**Deliberately reframed:** numeric tokens became descriptive language, per the format's core
convention. `corner-xl: 28` became "boldly rounded corners"; a `BACKGROUND_BLUR` effect at
radius 34 became "translucent frosted glass with a bright specular highlight along its top-left
edge". The hex values are retained alongside the descriptive names, as the format prescribes.

**Cannot carry over:** Figma variable *bindings*. In Figma every fill points at a live variable,
so changing one token updates every screen. Markdown has no such mechanism — if you change a
colour, update `DESIGN.md` and regenerate, rather than expecting screens to follow.

**Not attempted:** vector icon geometry. The Figma scripts embed real SVG path data; the prompts
describe icons by name and let Stitch supply them.

## Caveats

- The `Project ID` field in both `DESIGN.md` files is a placeholder. Set it after creating the
  Stitch projects.
- No Stitch MCP server was connected when these were written, so nothing here has been validated
  against a live Stitch project. Stitch does publish an MCP server and SDK; connecting it would
  allow generating and verifying the screens directly.
- Stitch is a generative tool. It will not reproduce these screens pixel-for-pixel, and it is not
  meant to — `DESIGN.md` constrains the visual language so that *new* screens match the existing
  ones. For pixel-exact reproduction, the Figma scripts remain the source of truth.
