// carApp — Step 20: iOS app icon (Liquid Glass) on page 14:3
//
// Builds a `launcher-icon` SECTION below the dark row, holding the three 1024 appearance
// masters Xcode asks for (Any / Dark / Tinted), the two layers an Icon Composer `.icon` needs,
// the superellipse size ladder and the export spec. Every fill resolves through the Liquid
// Glass collection except the Tinted master — see the note on that below.
//
// WHY A SECTION, NOT A FRAME. Script 13 rebuilds the dark row from
// `page.children.filter(n => n.type === 'FRAME' && !n.name.endsWith(' · dark'))`. A top-level
// FRAME here would be cloned into the dark row on 13's next run. A SECTION is skipped by that
// filter. (Verified: a section's children carry coordinates relative to the section origin.)
//
// SIZE. Masters are drawn at the real 1024 x 1024, because that is the asset — Xcode's
// single-size app icon slot takes one 1024 PNG per appearance and derives everything else. The
// ladder below it is a legibility check, not an export list.
//
// THE MARK. Identical vocabulary to `screen-welcome`: the raised glass disc carrying the lucide
// car glyph in `accent/brand`, over the ambient backdrop that gives BACKGROUND_BLUR something
// to refract. Ratios follow the welcome hero (disc 62.5% of the canvas, glyph 47% of the disc).
// Without the ambient blobs the material collapses to flat grey — the same trap the screens hit.
//
// THE PLATE IS BASED ON `ambient-a`, NOT `background/system`. On the screens the backdrop is
// white with pastel blobs, which is right behind content. As an icon it failed: the top-right
// corner resolved to near-white and the white glass disc dissolved into it, leaving a pale
// square with a floating car. Basing the plate on the mint ambient token keeps every corner
// tinted, so the disc separates all the way round — and it carries into Dark for free, where
// the same token is a deep teal instead of near-black #1c1c1e.
//
// DARK. A clone pinned to the collection's Dark mode with setExplicitVariableModeForCollection,
// exactly as script 13 does for the screens. The glass inverts to a dark veil and the ambient
// blobs drop to low luminance because the tokens say so, not because anything is redrawn.
//
// TINTED IS THE ONE DELIBERATE EXCEPTION TO THE TOKEN RULE. Apple's tinted appearance takes a
// GRAYSCALE asset and maps its luminance onto the user's chosen tint. A token-coloured icon
// would be wrong by construction there, so that master is built from literal greys. Do not
// "fix" it to variables.
//
// NO ROUNDED CORNERS, NO ALPHA. The masters are full-bleed squares. iOS applies the superellipse
// itself; baking it in produces a double-masked icon with dark fringing.
//
// Idempotent: an existing `launcher-icon` section on the page is removed before rebuilding.

const page = await figma.getNodeByIdAsync('14:3');
await figma.setCurrentPageAsync(page);

const F = 'Inter';
// Inter spells semibold with a space. SF Pro is a phantom font in this file — see script 07.
const ST = { r: 'Regular', m: 'Medium', sb: 'Semi Bold', b: 'Bold' };
for (const s of Object.values(ST)) await figma.loadFontAsync({ family: F, style: s });

const cols = await figma.variables.getLocalVariableCollectionsAsync();
const lg = cols.find(c => c.name === 'Liquid Glass');
if (!lg) throw new Error('collection "Liquid Glass" not found — run script 01 first');
const darkMode = lg.modes.find(m => m.name === 'Dark');
if (!darkMode) throw new Error('no Dark mode on Liquid Glass — run script 12 first');

const vars = await figma.variables.getLocalVariablesAsync('COLOR');
const V = {};
for (const v of vars) if (v.variableCollectionId === lg.id) V[v.name] = v;
const paint = (n) => figma.variables.setBoundVariableForPaint({ type: 'SOLID', color: { r: 0, g: 0, b: 0 } }, 'color', V[n]);
const hexPaint = (h, a) => ({ type: 'SOLID', color: { r: parseInt(h.slice(1, 3), 16) / 255, g: parseInt(h.slice(3, 5), 16) / 255, b: parseInt(h.slice(5, 7), 16) / 255 }, opacity: a === undefined ? 1 : a });
const P = (n, a) => (n.charAt(0) === '#' ? hexPaint(n, a) : paint(n));

const SPECULAR = {
  type: 'GRADIENT_LINEAR',
  gradientTransform: [[0.6, 0.6, -0.1], [-0.6, 0.6, 0.4]],
  gradientStops: [
    { position: 0, color: { r: 1, g: 1, b: 1, a: 0.95 } },
    { position: 0.45, color: { r: 1, g: 1, b: 1, a: 0.22 } },
    { position: 1, color: { r: 1, g: 1, b: 1, a: 0.7 } }
  ]
};

const ICON = {
  car: '<path d="m21 8-2 2-1.5-3.7A2 2 0 0 0 15.646 5H8.4a2 2 0 0 0-1.903 1.257L5 10 3 8"/><path d="M7 14h.01"/><path d="M17 14h.01"/><rect width="18" height="8" x="3" y="10" rx="2"/><path d="M5 18v2"/><path d="M19 18v2"/>'
};

function icon(markup, size, colorName, weight) {
  const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#000000" stroke-width="' + (weight === undefined ? 1.9 : weight) + '" stroke-linecap="round" stroke-linejoin="round">' + markup + '</svg>';
  const n = figma.createNodeFromSvg(svg);
  n.name = 'glyph'; n.clipsContent = false;
  for (const c of n.findAll(x => 'strokes' in x)) { if (c.strokes.length) c.strokes = [P(colorName)]; }
  if (size !== 24) n.rescale(size / 24);
  return n;
}

function T(chars, o) {
  o = o || {};
  const t = figma.createText();
  t.fontName = { family: F, style: o.style || ST.r };
  t.characters = chars;
  t.fontSize = o.size || 15;
  t.lineHeight = { unit: 'PIXELS', value: o.lh || Math.round((o.size || 15) * 1.4) };
  t.letterSpacing = { unit: 'PIXELS', value: o.ls === undefined ? -0.2 : o.ls };
  t.textAlignHorizontal = o.align || 'LEFT';
  t.fills = [P(o.color || 'label/primary')];
  t.name = chars.slice(0, 24);
  if (o.width) { t.textAutoResize = 'HEIGHT'; t.resize(o.width, t.height); }
  return t;
}

// ---- icon geometry -----------------------------------------------------------------------
const M = 1024;                      // the delivered master edge
const DISC = Math.round(M * 0.625);  // 640 — welcome's mark-to-screen presence, scaled up
const GLYPH = Math.round(DISC * 0.47);
const RADIUS = 0.2237;               // iOS superellipse corner radius, as a fraction of the edge

// Ambient blobs, in master units: [token, diameter, x, y, opacity]
const BLOBS = [
  ['background/ambient-a', 900, -260, -240, 1],
  ['background/ambient-b', 820, 380, -180, 0.9],
  ['background/ambient-c', 760, -200, 560, 0.85],
  ['background/ambient-a', 700, 460, 620, 0.9]
];

function backdrop(name) {
  const f = figma.createFrame();
  f.name = name || 'layer · background';
  f.resize(M, M);
  f.clipsContent = true;
  f.fills = [paint('background/ambient-a')];
  for (const [c, s, x, y, op] of BLOBS) {
    const e = figma.createEllipse();
    e.name = 'ambient';
    e.resize(s, s);
    e.fills = [paint(c)];
    e.opacity = op;
    e.effects = [{ type: 'LAYER_BLUR', radius: 150, visible: true }];
    f.appendChild(e);
    e.x = x; e.y = y;
  }
  return f;
}

function glassMark(parent) {
  const d = figma.createAutoLayout('HORIZONTAL', { name: 'glass-mark' });
  parent.appendChild(d);
  d.primaryAxisAlignItems = 'CENTER';
  d.counterAxisAlignItems = 'CENTER';
  d.resize(DISC, DISC);
  d.primaryAxisSizingMode = 'FIXED';
  d.counterAxisSizingMode = 'FIXED';
  d.cornerRadius = 999;
  d.fills = [paint('material/regular/fill-raised')];
  // Blur and lift scale with the master; the screen values (32 / 8) would vanish at 1024.
  d.effects = [
    { type: 'BACKGROUND_BLUR', radius: 130, visible: true },
    { type: 'DROP_SHADOW', color: { r: 0.04, g: 0.12, b: 0.10, a: 0.18 }, offset: { x: 0, y: 36 }, radius: 96, spread: -8, visible: true, blendMode: 'NORMAL' }
  ];
  d.strokes = [SPECULAR];
  d.strokeWeight = 8;
  d.strokeAlign = 'INSIDE';
  d.appendChild(icon(ICON.car, GLYPH, 'accent/brand', 2.0));
  d.x = (M - DISC) / 2;
  d.y = (M - DISC) / 2;
  return d;
}

function master(name) {
  const f = backdrop(name);
  glassMark(f);
  return f;
}

// Grayscale by Apple's rule, not by oversight — see the header note.
function tintedMaster() {
  const f = figma.createFrame();
  f.name = 'AppIcon · Tinted';
  f.resize(M, M);
  f.clipsContent = true;
  f.fills = [{
    type: 'GRADIENT_LINEAR',
    gradientTransform: [[0.7, 0.7, -0.2], [-0.7, 0.7, 0.5]],
    gradientStops: [
      { position: 0, color: { r: 0.24, g: 0.24, b: 0.25, a: 1 } },
      { position: 1, color: { r: 0.09, g: 0.09, b: 0.10, a: 1 } }
    ]
  }];
  const d = figma.createAutoLayout('HORIZONTAL', { name: 'glass-mark' });
  f.appendChild(d);
  d.primaryAxisAlignItems = 'CENTER';
  d.counterAxisAlignItems = 'CENTER';
  d.resize(DISC, DISC);
  d.primaryAxisSizingMode = 'FIXED';
  d.counterAxisSizingMode = 'FIXED';
  d.cornerRadius = 999;
  d.fills = [hexPaint('#FFFFFF', 0.16)];
  d.strokes = [hexPaint('#FFFFFF', 0.28)];
  d.strokeWeight = 6;
  d.strokeAlign = 'INSIDE';
  d.appendChild(icon(ICON.car, GLYPH, '#F2F2F2', 2.0));
  d.x = (M - DISC) / 2;
  d.y = (M - DISC) / 2;
  return f;
}

// A master rendered at `size` under the iOS superellipse, the way the home screen shows it.
function masked(src, size, name) {
  const f = figma.createFrame();
  f.name = name || ('icon · ' + size);
  f.resize(size, size);
  f.clipsContent = true;
  f.fills = [];
  f.cornerRadius = size * RADIUS;
  f.cornerSmoothing = 0.6;
  const c = src.clone();
  f.appendChild(c);
  c.rescale(size / M);
  c.x = 0; c.y = 0;
  return f;
}

// ---- board scaffolding -------------------------------------------------------------------
for (const n of page.children.filter(c => c.name === 'launcher-icon')) n.remove();

const sec = figma.createSection();
sec.name = 'launcher-icon';
page.appendChild(sec);
sec.x = 0; sec.y = 2300;
sec.resizeWithoutConstraints(2400, 2400);
sec.fills = [paint('background/grouped')];

const board = figma.createAutoLayout('VERTICAL', { name: 'ios-app-icon', itemSpacing: 48 });
sec.appendChild(board);
board.paddingTop = 48; board.paddingBottom = 48; board.paddingLeft = 48; board.paddingRight = 48;
board.fills = [paint('background/system')];
board.cornerRadius = 34;

function tile(art, label, sub) {
  const t = figma.createAutoLayout('VERTICAL', { name: 'tile', itemSpacing: 12 });
  t.fills = []; t.counterAxisAlignItems = 'CENTER';
  t.appendChild(art);
  const cap = figma.createAutoLayout('VERTICAL', { name: 'caption', itemSpacing: 2 });
  cap.fills = []; cap.counterAxisAlignItems = 'CENTER';
  cap.appendChild(T(label, { size: 14, style: ST.m, align: 'CENTER' }));
  if (sub) cap.appendChild(T(sub, { size: 12, color: 'label/secondary', align: 'CENTER' }));
  t.appendChild(cap);
  return t;
}

function row(title, note) {
  const r = figma.createAutoLayout('VERTICAL', { name: title, itemSpacing: 20 });
  r.fills = [];
  const h = figma.createAutoLayout('VERTICAL', { name: 'heading', itemSpacing: 2 });
  h.fills = [];
  h.appendChild(T(title, { size: 22, style: ST.sb, lh: 28 }));
  if (note) h.appendChild(T(note, { size: 14, color: 'label/secondary', lh: 20, width: 900 }));
  r.appendChild(h);
  const items = figma.createAutoLayout('HORIZONTAL', { name: 'items', itemSpacing: 32 });
  items.fills = []; items.counterAxisAlignItems = 'MAX';
  r.appendChild(items);
  board.appendChild(r);
  return items;
}

const title = figma.createAutoLayout('VERTICAL', { name: 'title', itemSpacing: 4 });
title.fills = [];
title.appendChild(T('App icon — iOS', { size: 40, style: ST.b, lh: 48, ls: 0.4 }));
title.appendChild(T('AppIcon.appiconset · 1024 x 1024 masters, one per appearance · deployment target iOS 16', { size: 15, color: 'label/secondary' }));
board.appendChild(title);

// ---- 1 · appearance masters --------------------------------------------------------------
const r1 = row('Appearance masters · 1024 x 1024', 'The three slots Xcode exposes. Any is the asset every iOS version uses; Dark and Tinted are read from iOS 18 up and ignored below it, so shipping all three is safe on a 16.0 target.');

const anyMaster = master('AppIcon · Any');
r1.appendChild(tile(anyMaster, 'AppIcon · Any (Light)', 'Liquid Glass tokens, Light mode'));

const darkMaster = anyMaster.clone();
darkMaster.name = 'AppIcon · Dark';
darkMaster.setExplicitVariableModeForCollection(lg, darkMode.modeId);
r1.appendChild(tile(darkMaster, 'AppIcon · Dark', 'same tokens, Dark mode pinned'));

r1.appendChild(tile(tintedMaster(), 'AppIcon · Tinted', 'grayscale — iOS supplies the tint'));

// ---- 2 · Icon Composer layers ------------------------------------------------------------
const r2 = row('Layers · Icon Composer (iOS 26)', 'The same drawing split the way a layered .icon wants it: the ambient plate stays put while the mark parallaxes and takes the specular pass. Export both at 1024, no alpha on the background, alpha preserved on the foreground.');
r2.appendChild(tile(backdrop('layer · background'), 'layer · background', 'ambient plate, opaque'));

const fgLayer = figma.createFrame();
fgLayer.name = 'layer · foreground';
fgLayer.resize(M, M);
fgLayer.fills = [];
fgLayer.clipsContent = false;
glassMark(fgLayer);
r2.appendChild(tile(fgLayer, 'layer · foreground', 'glass disc + glyph, transparent'));

// ---- 3 · size ladder ---------------------------------------------------------------------
const r3 = row('Size ladder · superellipse mask, real pixels', 'Rendered from the Any master. Xcode derives these from the 1024 automatically — the row is here to prove the glyph survives at 40 px, not to be exported slot by slot.');
const LADDER = [
  [180, 'iPhone app · 60pt @3x'],
  [167, 'iPad Pro app · 83.5pt @2x'],
  [152, 'iPad app · 76pt @2x'],
  [120, 'iPhone app @2x / Spotlight @3x'],
  [87, 'Settings · 29pt @3x'],
  [80, 'Spotlight · 40pt @2x'],
  [60, 'Notification · 20pt @3x'],
  [58, 'Settings · 29pt @2x'],
  [40, 'Notification · 20pt @2x']
];
for (const [px, slot] of LADDER) {
  r3.appendChild(tile(masked(anyMaster, px, 'icon · ' + px), px + ' px', slot));
}

// ---- 4 · spec ----------------------------------------------------------------------------
const specRow = row('Export', null);
const spec = figma.createAutoLayout('VERTICAL', { name: 'export-spec', itemSpacing: 12 });
spec.fills = [paint('background/grouped')];
spec.cornerRadius = 26;
spec.paddingTop = 26; spec.paddingBottom = 26; spec.paddingLeft = 26; spec.paddingRight = 26;
spec.appendChild(T('iosApp/Assets.xcassets/AppIcon.appiconset', { size: 16, style: ST.sb }));
spec.appendChild(T(
  'Single-size app icon: one 1024 x 1024 PNG per appearance — Any, Dark, Tinted. Xcode derives every\n' +
  'other slot at build time, so no other size needs to ship.\n' +
  '\n' +
  'PNG, sRGB, 32-bit, NO alpha channel on Any and Dark, NO rounded corners, no baked drop shadow.\n' +
  'The Tinted asset is grayscale on purpose: iOS maps its luminance onto the user tint.\n' +
  '\n' +
  'The asset catalog does not exist in the repo yet — it is created by story E4-04, together with\n' +
  'ASSETCATALOG_COMPILER_APPICON_NAME = AppIcon, which the Xcode project already sets.\n' +
  '\n' +
  'iOS 26 layered icon: export layer · background and layer · foreground at 1024 and compose them\n' +
  'in Icon Composer instead of shipping the flattened master.',
  { size: 13, lh: 21, color: 'label/secondary', width: 760 }
));
specRow.appendChild(spec);

// ---- fit the section ---------------------------------------------------------------------
board.x = 40; board.y = 40;
sec.resizeWithoutConstraints(board.width + 80, board.height + 80);

await sec.screenshot();

return {
  createdNodeIds: [sec.id, board.id],
  section: sec.id,
  board: board.id,
  masters: { any: anyMaster.id, dark: darkMaster.id },
  size: { width: sec.width, height: sec.height },
  geometry: { master: M, disc: DISC, glyph: GLYPH }
};
