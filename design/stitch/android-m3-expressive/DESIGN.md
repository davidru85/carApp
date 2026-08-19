# Design System: carApp — Android (Material 3 Expressive)
**Project ID:** _(set this to your Stitch project ID after creating the project)_

## 1. Visual Theme & Atmosphere

carApp on Android is a **confident, tactile utility** — a local-first fuel logbook that feels closer to a well-made instrument than to a spreadsheet. The interface is built on Material 3 Expressive, so it leans deliberately into **bold shape, generous radii and saturated containers** rather than the restrained neutrality of a typical settings app. Every surface is rounded enough to feel soft in the hand; nothing is a sharp rectangle.

The overall mood is **calm mint-and-teal clarity punctuated by warm amber accents**. The background is a pale, almost-white mint that reads as fresh rather than clinical. Against it, deep pine teal carries every primary action, and a burnished amber appears sparingly to flag anything that needs a second look. The result feels **trustworthy and quietly energetic** — a tool you reach for at a petrol station in bright daylight, so contrast and legibility outrank subtlety.

Numbers are the point of this app. Consumption figures and odometer readings are given **display-scale typographic weight**, far larger than their labels, so the answer is readable at arm's length before any of the supporting detail is.

**Key Characteristics:**
- Boldly rounded shape language, from softly rounded fields to fully pill-shaped buttons
- Filled colour containers doing the work that borders would do in a flatter system
- One large, unmissable number per screen carrying the primary insight
- Scalloped, petal-like decorative shapes used sparingly as brand moments
- Interaction states that are visibly rendered — pressed elements change both tint and shape
- Warm amber reserved exclusively for caution; never decorative

## 2. Color Palette & Roles

### Primary Foundation
- **Pale Mint Paper** (#F5FBF7) – Primary background for every screen. A barely-tinted mint that feels cleaner and less sterile than pure white.
- **Faint Mint Surface** (#EFF5F1) – Raised card and list-group background. Sits just above the page without needing a border.
- **Quiet Stone Green** (#DEE4E0) – Input field background. Slightly deeper than card surfaces so text entry areas are obvious at a glance.

### Accent & Interactive
- **Deep Pine Teal** (#006A57) – The primary action colour. Filled buttons, selected values, and every headline number. This is the only colour that should read as "tap me first".
- **Bright Mint Wash** (#7FF8D6) – Primary container. Fills the extended floating action button, leading icon circles, and the hero statistic panel. Carries brand presence at large sizes without the visual weight of full-strength teal.
- **Calm Eucalyptus** (#CDE9DD) – Secondary container. Softer, supporting actions — the save button in a form header, tonal icon buttons, and the selected segment of a segmented control.

### Typography & Text Hierarchy
- **Deep Forest Ink** (#171D1A) – Primary text. Near-black with a green undertone so it sits naturally on the mint background rather than looking pasted on.
- **Muted Slate Green** (#3F4945) – Secondary text: field labels, list subtitles, supporting captions and metadata.
- **Soft Sage Gray** (#6F7975) – Outlines on interactive elements, and the resting underline beneath input fields.
- **Whisper Sage** (#BFC9C4) – Hairline dividers inside cards and between list rows. Present but almost subliminal.

### Functional States (Reserved for system feedback)
- **Burnished Amber** (#7C5800) with **Warm Honey Wash** (#FFDEA6) container – Caution, not error. Used for the "partial tank" badge and the computed-total panel, where a value is derived rather than entered and deserves a second look.
- **Signal Crimson** (#BA1A1A) – Genuine errors only: invalid field entry, and the destructive "delete account" action.
- **Deep Pine Teal** (#006A57) – Doubles as the success/synced indicator via a small filled dot beside status text.

## 3. Typography Rules

**Primary Font Family:** Roboto Flex
**Character:** Neutral, highly legible grotesque with a wide weight range. Chosen because it holds up at very large display sizes for the statistics without feeling decorative, and stays crisp at caption size on dense forms.

### Hierarchy & Weights
- **Hero Statistic:** Bold weight (700), tight letter-spacing, roughly 3.5rem size. Reserved for the single headline number on a screen — the consumption figure. Paired with a much smaller unit label sitting on the same baseline.
- **Screen Titles (large bars):** Bold weight (700), slightly tightened letter-spacing, ~2rem size. Left-aligned, sitting low in a tall top bar with space above it.
- **Screen Titles (compact bars):** Semi-bold weight (600), ~1.375rem size. Used on form screens where vertical space is needed for fields.
- **List Item Titles:** Semi-bold weight (600), ~1.25rem size. Vehicle names and entry dates.
- **Body Text:** Regular weight (400), comfortable line-height, ~1rem size. Field values and descriptive sentences.
- **Section Headers:** Semi-bold weight (600), ~0.875rem size, set in Deep Pine Teal. Introduces a group of related settings or form fields.
- **Field Labels & Captions:** Medium weight (500), slightly expanded letter-spacing, ~0.75rem size, in Muted Slate Green. Sits above the value inside input fields.

### Spacing Principles
- Labels sit directly above their values with almost no gap — they read as one unit
- Generous vertical separation between groups (around 1.5rem) so sections are obvious without rules or boxes
- Numbers and their units share a baseline, with the unit noticeably smaller
- Supporting text under a field is indented to align with the field's inner text, not its outer edge

## 4. Component Stylings

### Buttons
- **Shape:** Fully pill-shaped by default — completely rounded ends, not merely rounded corners. This is central to the Expressive feel and should not be softened to a small radius.
- **Height:** Comfortable and generous, around 3.5rem for primary actions in a form; more compact (around 2.5rem) for actions living inside a top bar.
- **Primary (filled):** Deep Pine Teal background with white text, semi-bold label.
- **Tonal:** Calm Eucalyptus background with deep green text. The default for secondary confirmations like "Save".
- **Outlined:** Transparent with a Soft Sage Gray hairline outline and dark text. Used for third-party sign-in.
- **Pressed State (important):** A pressed button visibly **morphs from a pill into a softly rounded rectangle** while a translucent tint of its own text colour washes over it. Both changes happen together — this shape-morph is a signature of the system, not an optional flourish.

### Cards & List Groups
- **Corner Style:** Boldly rounded — roughly 1.75rem radius on cards and settings groups, noticeably rounder than a conventional card.
- **Background:** Faint Mint Surface, sitting on the Pale Mint Paper page.
- **Shadow Strategy:** A soft, close, low-opacity shadow that lifts the card just slightly off the page. Never a hard or far-cast shadow.
- **Internal Padding:** Roomy, around 1.25rem, with a hairline Whisper Sage divider separating a card's header from its statistics row.
- **Settings Groups:** Multiple rows share a single rounded container, divided by hairlines that run the full width of the card interior.

### Floating Action Button (Extended)
- **Shape:** Rounded rectangle, distinctly *less* round than the pill buttons — this contrast is intentional.
- **Fill:** Bright Mint Wash with dark green icon and label side by side.
- **Elevation:** Sits clearly above the content with a soft but noticeable shadow.
- **Position:** Anchored to the bottom-right with comfortable margin from both edges. It must never touch or overflow the screen edge.

### Inputs & Forms
- **Shape:** Rounded on the top corners only, flat along the bottom, where a coloured underline sits. Filled, never outlined.
- **Background:** Quiet Stone Green.
- **Structure:** A small caption-sized label sits above the value inside the same container.
- **Resting State:** Thin Soft Sage Gray underline, Muted Slate Green label.
- **Focused State:** Thicker Deep Pine Teal underline, and the label turns Deep Pine Teal to match.
- **Error State:** Thicker Signal Crimson underline, label turns Signal Crimson, and a short crimson message appears beneath the field, indented to the field's inner text.
- **Suffix Units:** Unit text (km, L, €/L) sits right-aligned inside the field in Muted Slate Green.

### Selection Controls
- **Segmented Control:** A single pill-shaped outlined track divided by hairlines. The selected segment fills with Calm Eucalyptus **and gains a check mark** before its label — the check is how selection is communicated, not colour alone.
- **Switches:** Generously sized. When on, the track fills Deep Pine Teal with a large white knob carrying a teal check mark. When off, the track is a hollow outlined capsule with a small, low-contrast knob. The knob visibly grows when switching on.

### Status & Caution Elements
- **Sync Chip:** A small outlined chip with a filled teal dot before short status text.
- **Caution Badge:** A compact filled badge in Warm Honey Wash with a warning triangle and short amber label.
- **Loading Indicator:** A small scalloped, petal-like shape in Deep Pine Teal rather than a plain circular spinner.

## 5. Layout Principles

### Grid & Structure
- **Target Canvas:** Android handset, 412 × 917. Design mobile-only; this product has no tablet or desktop surface.
- **Edge Margin:** 1rem from both screen edges for all cards, lists and form fields.
- **Top Bars:** Two variants — a tall bar (around 9rem) where a large title sits low with a status chip beneath it, used on primary screens; and a compact bar (around 4rem) with a back arrow, inline title and trailing action, used on forms.
- **System Chrome:** A slim status bar at the top and a centred gesture handle pill at the very bottom.

### Whitespace Strategy
- **Base Unit:** 0.5rem for micro-spacing, 1rem for standard component spacing
- **Between cards in a list:** 0.75rem — tight enough to read as one list, loose enough to keep the rounding legible
- **Between form sections:** 1.25–1.5rem, letting the teal section header do the separating work
- **Above the first content block:** Generous, letting the large title breathe

### Alignment & Visual Balance
- **Label left, value right:** The dominant row pattern. Labels in Muted Slate Green on the left, values right-aligned and emphasised.
- **Emphasis by weight and size, not by boxing:** Important values get larger, bolder and teal rather than getting a container.
- **One focal point per screen:** Either the hero statistic or the primary action, never competing.
- **Leading icon circles:** List items begin with a filled circular icon container that visually anchors the row.

### Responsive Behavior & Touch
- **Touch Targets:** No interactive element smaller than 48 × 48, including icon buttons that appear visually smaller.
- **Scrolling:** Form screens scroll freely; the top bar stays put while content moves beneath it.
- **Overflow Discipline:** Long labels must wrap or shorten. No element may extend past the screen edge — the floating action button in particular must stay fully inside the frame.

## 6. Design System Notes for Stitch Generation

When creating new screens for this project using Stitch, reference these specific instructions:

### Language to Use
- **Atmosphere:** "Calm mint-and-teal utility with bold rounded shape language"
- **Button Shapes:** "Fully pill-shaped" (not "rounded-full" or "999px")
- **Card Corners:** "Boldly rounded corners" (not "rounded-2xl" or "28px")
- **Shadows:** "Soft close-cast shadow lifting the card slightly" (not "shadow-md")
- **Fields:** "Filled field, rounded at the top only, with a coloured underline"
- **Pressed Buttons:** "Morphs from a pill into a softly rounded rectangle with a translucent tint"

### Color References
Always use the descriptive names with hex codes:
- Primary actions and headline numbers: "Deep Pine Teal (#006A57)"
- Hero panels and the floating action button: "Bright Mint Wash (#7FF8D6)"
- Page background: "Pale Mint Paper (#F5FBF7)"
- Cards and groups: "Faint Mint Surface (#EFF5F1)"
- Input fields: "Quiet Stone Green (#DEE4E0)"
- Body and secondary text: "Deep Forest Ink (#171D1A)" or "Muted Slate Green (#3F4945)"
- Caution only: "Burnished Amber (#7C5800)" on "Warm Honey Wash (#FFDEA6)"
- Errors and destructive actions only: "Signal Crimson (#BA1A1A)"

### Component Prompts
- "Create a vehicle card with boldly rounded corners on Faint Mint Surface (#EFF5F1), a Bright Mint Wash circular icon container on the left, name and manufacturer stacked beside it, a chevron on the right, a hairline divider, and an odometer row with the reading right-aligned in bold Deep Pine Teal (#006A57)"
- "Design a filled input field rounded at the top only, on Quiet Stone Green (#DEE4E0), with a small caption label above the value and a thick Deep Pine Teal (#006A57) underline showing the focused state"
- "Add an extended floating action button in Bright Mint Wash (#7FF8D6) with a plus icon and label, rounded rectangle rather than pill-shaped, lifted with a soft shadow, anchored bottom-right well inside the screen edge"
- "Show a segmented control as one pill-shaped outlined track split by hairlines, with the selected segment filled Calm Eucalyptus (#CDE9DD) and carrying a check mark before its label"

### Incremental Iteration
When refining existing screens:
1. Focus on ONE component at a time (e.g., "Update the input fields")
2. Be specific about what to change (e.g., "Make the focused field underline thicker and turn its label Deep Pine Teal")
3. Reference this design system language consistently — say "fully pill-shaped", never "rounded-full"
4. Keep the hero statistic dramatically larger than everything around it; if a regeneration shrinks it toward body size, prompt explicitly to restore display scale

### Content Language
All interface copy is **Spanish (Spain)**. Numbers use comma as decimal separator and period as thousands separator (`7,24` and `142.500`). Currency is euro, written after the value (`73,63 €`). Consumption is always L/100 km.
