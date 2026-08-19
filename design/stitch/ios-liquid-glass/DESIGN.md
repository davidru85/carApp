# Design System: carApp — iOS (Liquid Glass)
**Project ID:** _(set this to your Stitch project ID after creating the project)_

## 1. Visual Theme & Atmosphere

carApp on iOS is **luminous and layered**. The screen is built as two distinct planes: a content plane that scrolls, and a floating glass plane of controls that hovers above it. The glass is genuinely translucent — content passing underneath is visibly blurred and tinted through it, and the edge of every glass surface catches a bright highlight along its top-left, as though lit from above.

Underneath everything sits a **soft ambient wash of mint, pale sky and warm amber**, heavily diffused so no individual colour is identifiable — only a gentle gradient of light. This wash exists so the glass has something to refract. Without colour behind it, glass collapses into flat grey and the entire system stops working.

The mood is **airy, precise and calm**. Where the Android build is tactile and shape-forward, this one is optical: depth comes from transparency, blur and light rather than from fills and elevation. Content itself stays crisp, opaque and highly legible — the glassiness belongs to the chrome, never to the reading surface.

**Key Characteristics:**
- A floating translucent chrome layer with content visibly blurring beneath it
- Bright specular highlight running along the top-left edge of every glass surface
- Diffuse ambient colour wash providing the light that glass refracts
- Opaque white content cards holding all text, for uncompromised legibility
- Capsule-shaped controls and softly rounded inset groups
- Restraint — one accent colour, no decoration, nothing competing with content

## 2. Color Palette & Roles

### Primary Foundation
- **Cool Cloud Mist** (#F2F2F7) – The grouped page background sitting beneath the ambient wash. Neutral and cool so the ambient colours read as light rather than as paint.
- **Pure Canvas White** (#FFFFFF) – Every content card, list group and entry row. Fully opaque, no transparency. This is where text lives.

### Ambient Wash (background light only — never a fill for content)
- **Soft Mint Glow** (#BFF2E0) – The dominant ambient tone, tying the product to its brand teal.
- **Pale Sky Glow** (#E9F6FF) – A cool counterpoint, keeping the wash from reading as one flat tint.
- **Warm Amber Glow** (#FFE9C7) – A warm pocket low on the screen, giving the glass something with contrast to refract. Also doubles as the fill for the caution badge.

These three are always **heavily blurred, overlapping soft circles**, never visible as shapes or edges.

### Glass Materials
- **Regular Glass** – White at roughly 60% opacity with a strong background blur behind it. The default and most versatile material. Used for navigation bars, sheet toolbars, segmented control tracks and circular icon buttons.
- **Raised Glass** – White at roughly 72% opacity. Slightly more solid, used where a glass element must sit forward of other glass-adjacent content, such as the brand mark.
- **Clear Glass** – White at roughly 18% opacity with a lighter blur. Nearly transparent, used only for the lowest-priority action in a stack, where the button should barely assert itself.
- **Specular Highlight** – A hairline gradient border running from bright white at the top-left, fading to near-nothing mid-edge, returning to a soft glow at the bottom-right. This is what makes a surface read as glass rather than as a translucent rectangle.

### Accent & Interactive
- **Vivid Deep Teal** (#0A7C66) – The single accent. Every interactive label, back button, key statistic, and the prominent action button. Nothing else competes with it.
- **Pure Canvas White** (#FFFFFF) – Text and icons on top of the teal accent.

### Typography & Text Hierarchy
- **True Black Ink** (#000000) – Primary labels and values.
- **Muted Graphite** (#3C3C43 at 60%) – Secondary text: row values, subtitles, supporting descriptions.
- **Faint Graphite** (#3C3C43 at 30%) – Placeholder text, disabled chevrons, sheet grabbers.
- **Hairline Graphite** (#3C3C43 at 29%) – Row separators inside list groups, inset from the left edge to align with row text.

### Functional States (Reserved for system feedback)
- **Alert Vermilion** (#D93025) – Destructive actions and validation errors only.
- **Caution Bronze** (#B86B00) on **Warm Amber Glow** (#FFE9C7) – The partial-tank badge and other advisory notes.
- **Vivid Deep Teal** (#0A7C66) – Positive and synced states, shown as a small filled dot beside short status text.

## 3. Typography Rules

**Primary Font Family:** Inter
**Character:** Neutral, tightly-fitted grotesque with excellent small-size legibility. Chosen as the closest widely-available stand-in for the Apple system typeface; it shares the same optical proportions and slightly tightened default tracking.

### Hierarchy & Weights
- **Brand Wordmark:** Bold weight (700), slightly expanded letter-spacing, ~2.125rem size. Welcome screen only.
- **Hero Statistic:** Bold weight (700), tight letter-spacing, ~2.75rem size, in white on the teal statistic card. Large, but noticeably more restrained than the Android equivalent — iOS achieves emphasis through contrast and placement rather than sheer scale.
- **Navigation Titles:** Semi-bold weight (600), ~1.0625rem size, centred in the glass bar.
- **Row Labels & Buttons:** Regular to semi-bold (400–600), ~1.0625rem size. This is the workhorse size and most of the interface sits here.
- **Row Values:** Regular weight (400), ~1.0625rem size, right-aligned in Muted Graphite.
- **Section Headers:** Medium weight (500), ~0.75rem size, **set in capitals with expanded letter-spacing**, in Muted Graphite, indented to align with row text.
- **Footnotes & Subtitles:** Regular weight (400), ~0.8125rem size, in Muted Graphite. Explanatory text beneath a group or under a row label.

### Spacing Principles
- Slightly negative letter-spacing on body-size text, matching the platform's tight default fit
- Capitalised section headers sit close above their group with a small consistent gap
- Footnote text sits just below its group, indented to the group's inner text edge
- Comfortable, even row height — vertical padding stays consistent whether or not a row has a subtitle

## 4. Component Stylings

### Glass Navigation & Toolbars
- **Material:** Regular Glass with a strong background blur and a specular highlight along the bottom edge where it meets content.
- **Behaviour (critical):** The bar **floats above the content plane, and content scrolls underneath it**. The top of the scrolling content must be positioned so it genuinely passes behind the bar and blurs through it. Do not push content down to start below the bar — the overlap is the entire effect.
- **Layout:** Centred title, with a text back action ("‹ Atrás") on the left and a circular glass icon button on the right.

### Circular Glass Icon Buttons
- **Shape:** Perfect circle, roughly 2.375rem across.
- **Material:** Raised Glass with a specular edge and a soft shadow.
- **Icon:** Vivid Deep Teal, thin stroke, centred.

### Prominent Action Button
- **Shape:** Full capsule.
- **Fill:** Solid Vivid Deep Teal with white icon and label — a tinted glass control reads as near-solid over busy content, so it is rendered solid for legibility while keeping the specular edge.
- **Elevation:** A soft coloured shadow beneath it, tinted toward the accent rather than neutral grey.
- **Position:** **Horizontally centred**, floating clear of the bottom edge with comfortable margin. It must sit entirely within the screen — never clipped at the right edge.

### Glass Capsule Buttons (welcome screen)
- **Shape:** Full capsule, comfortable height around 3.125rem.
- **Variants:** Solid black for the Apple sign-in; Regular Glass for the Google option; solid Vivid Deep Teal for the primary sign-in; Clear Glass with teal text for the lowest-priority guest option.
- These four sit stacked inside a single large Regular Glass container with generously rounded corners.

### Content Cards & Inset Groups
- **Corner Style:** Softly rounded, roughly 1.375rem radius.
- **Fill:** Pure Canvas White, **fully opaque**. Content is never glass. Glass on glass is forbidden — it destroys both legibility and the sense of layering.
- **Shadow:** Very soft and subtle, just enough to separate the card from the ambient wash.
- **Grouped Rows:** Several rows share one rounded white container, separated by Hairline Graphite separators that are inset from the left to align with the row text, not the card edge.

### Rows & Forms
- **Structure:** Label on the left, value right-aligned in Muted Graphite. Optional subtitle beneath the label in footnote size.
- **Text Entry:** Rendered as a row, with the entered value or a Faint Graphite placeholder right-aligned. There are no boxed input fields on this platform.
- **Error State:** The row's label turns Alert Vermilion, and an Alert Vermilion footnote appears beneath the whole group explaining the problem.
- **Chevrons:** Thin Faint Graphite chevrons on rows that navigate.

### Selection Controls
- **Segmented Control:** A Regular Glass capsule track with a small internal inset. The selected segment is an opaque white capsule with a soft shadow, its label semi-bold; unselected labels sit in Muted Graphite on the bare glass.
- **Switches:** Capsule track roughly 3.1875rem wide with a large white knob and a soft shadow. On, the track fills Vivid Deep Teal; off, it is a neutral translucent grey.

### Sheets
- **Presentation:** Forms appear as a sheet covering most of the screen, with **generously rounded top corners only** and a soft upward shadow along its top edge.
- **Grabber:** A short, rounded Faint Graphite bar centred at the very top of the sheet.
- **Toolbar:** A Regular Glass bar pinned inside the top of the sheet carrying "Cancelar" on the left, the title centred, and "Guardar" in semi-bold teal on the right. Sheet content scrolls beneath it.

### Status & Caution Elements
- **Sync Status:** A small filled teal dot followed by short footnote text in Muted Graphite. No chip or container.
- **Caution Badge:** A small capsule filled Warm Amber Glow with a warning triangle and short Caution Bronze label.

## 5. Layout Principles

### Grid & Structure
- **Target Canvas:** iPhone, 402 × 874. Mobile only.
- **Edge Margin:** 1rem from both screen edges for cards and groups.
- **Layer Order (bottom to top):** ambient wash → opaque scrolling content → floating glass chrome → prominent action button.
- **System Chrome:** Status bar at the top; a centred home indicator pill near the bottom.

### Whitespace Strategy
- **Base Unit:** 0.5rem micro, 1rem standard
- **Between grouped sections:** ~1.375rem, with the capitalised section header providing additional separation
- **Inside rows:** Even vertical padding, generous enough that rows never feel cramped
- **Above the first content group:** Enough clearance that the group's top edge slides under the glass bar rather than colliding with it

### Alignment & Visual Balance
- **Label left, value right** is the dominant pattern across forms, settings and entry rows
- **Separators inset to text**, never running the full card width
- **Section headers indented** to align with the row labels beneath them
- **One accent colour only** — emphasis comes from teal, weight and right-alignment, never from additional hues
- **Depth by transparency**, not by borders or heavy shadows

### Responsive Behavior & Touch
- **Touch Targets:** Minimum 44 × 44 for every interactive element.
- **Scrolling:** Content scrolls under both the top glass bar and the floating action button.
- **Overflow Discipline:** The floating action button is centred and must remain entirely on screen. Long labels wrap or truncate rather than extending past any edge.

## 6. Design System Notes for Stitch Generation

When creating new screens for this project using Stitch, reference these specific instructions:

### Language to Use
- **Atmosphere:** "Luminous layered glass floating above crisp opaque content"
- **Glass:** "Translucent frosted glass with a bright specular highlight along its top-left edge" (not "backdrop-blur" or "bg-white/60")
- **Layering:** "Content scrolls underneath the glass bar and blurs through it"
- **Background:** "Soft diffuse ambient wash of mint, pale sky and warm amber"
- **Buttons:** "Full capsule" (not "rounded-full")
- **Cards:** "Softly rounded, fully opaque white card"
- **Separators:** "Hairline separator inset to align with the row text"

### Color References
Always use the descriptive names with hex codes:
- Accent, statistics and interactive labels: "Vivid Deep Teal (#0A7C66)"
- Content cards and groups: "Pure Canvas White (#FFFFFF)"
- Page base beneath the wash: "Cool Cloud Mist (#F2F2F7)"
- Ambient wash: "Soft Mint Glow (#BFF2E0)", "Pale Sky Glow (#E9F6FF)", "Warm Amber Glow (#FFE9C7)"
- Text: "True Black Ink (#000000)" or "Muted Graphite (#3C3C43 at 60%)"
- Caution only: "Caution Bronze (#B86B00)" on "Warm Amber Glow (#FFE9C7)"
- Destructive only: "Alert Vermilion (#D93025)"

### Component Prompts
- "Create a floating translucent glass navigation bar with a centred semi-bold title, a teal '‹ Atrás' text button on the left and a circular glass icon button on the right, with the page content scrolling underneath it and visibly blurring through the glass"
- "Design an inset grouped list as one softly rounded fully opaque white card, with label on the left and right-aligned value in Muted Graphite (#3C3C43 at 60%), separated by hairline separators inset to align with the row text"
- "Add a full-capsule prominent action button in solid Vivid Deep Teal (#0A7C66) with a plus icon and white label, horizontally centred and floating clear of the bottom edge with a soft teal-tinted shadow"
- "Show a segmented control as a translucent glass capsule track with the selected segment as an opaque white capsule carrying a soft shadow and a semi-bold label"

### Rules That Must Not Be Broken
1. **Never place glass on glass.** A glass card inside a glass container destroys the layering.
2. **Never make content cards translucent.** Text always sits on fully opaque white.
3. **Never remove the ambient wash.** Glass with nothing behind it renders as flat grey and the design fails.
4. **Never push content to start below the glass bar.** The overlap and blur-through is the effect.

### Incremental Iteration
When refining existing screens:
1. Focus on ONE component at a time (e.g., "Update the segmented control")
2. Be specific about what to change (e.g., "Make the selected segment an opaque white capsule with a softer shadow")
3. Reference this design system language consistently — say "translucent frosted glass with a specular edge", never "backdrop-blur"
4. After any regeneration, verify the four rules above still hold; they are the ones models most often quietly drop

### Content Language
All interface copy is **Spanish (Spain)**. Numbers use comma as decimal separator and period as thousands separator (`7,24` and `142.500`). Currency is euro, written after the value (`73,63 €`). Consumption is always L/100 km.
