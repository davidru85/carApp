// carApp — Step 19: Android launcher icon (adaptive) on page 14:2
//
// Builds a `launcher-icon` SECTION below the dark row, holding the three adaptive-icon source
// layers, the safe-zone diagram, the four launcher masks, the themed-icon pair, a density
// legibility ladder and the Play Store render. Every fill resolves through the M3 Expressive
// collection, so the board follows the same tokens as the six screens.
//
// WHY A SECTION, NOT A FRAME. Script 13 rebuilds the dark row from
// `page.children.filter(n => n.type === 'FRAME' && !n.name.endsWith(' · dark'))`. A top-level
// FRAME here would be cloned into the dark row on the next run of 13. A SECTION is skipped by
// that filter, so the two scripts stay independent.
//
// GEOMETRY. The adaptive icon canvas is 108dp; the launcher only ever shows the central 72dp
// (the mask viewport) and only guarantees the central 66dp (the safe zone) — the 18dp ring on
// each side is parallax headroom. Masters here are drawn at 4x (432 px = 108dp @xxxhdpi) so a
// 1x export is the largest density and the rest fall on clean quarters.
//
// THE MARK. Identical vocabulary to `screen-welcome`: the Expressive scalloped container
// (10-point star, innerRadius 0.9, corner radius 7.8% of the diameter) in `primary-container`
// carrying the lucide car glyph in `on-primary-container`, over a flat `primary` background.
//
// THE CONTAINER IS 52dp, NOT 66dp, AND THAT IS THE WHOLE POINT. 66dp is the safe zone — the
// limit past which a mask may cut, not a target size. A first pass drew the star at the full 66
// and every mask ruined it: the circle sheared the scallop points flat, and `primary` survived
// only as a sliver in the corners, so the icon read as a mint blob with no ground. At 52dp
// (72% of the visible 72dp viewport) the scalloped silhouette clears every mask intact and the
// deep-teal ground reads as a deliberate field around it. Verify any change to this number
// against the circle mask first — it is the tightest of the four.
//
// MONOCHROME. Android 13+ themed icons tint a single-colour drawable with the wallpaper
// palette, so the container cannot survive — it would flatten into the glyph. The monochrome
// layer is the car alone at 72% of the safe zone with a heavier stroke, which is what stays
// legible once the system recolours it.
//
// Idempotent: an existing `launcher-icon` section on the page is removed before rebuilding.

const page = await figma.getNodeByIdAsync('14:2');
await figma.setCurrentPageAsync(page);

const F = 'Roboto Flex';
for (const s of ['Regular', 'Medium', 'SemiBold', 'Bold']) await figma.loadFontAsync({ family: F, style: s });

const cols = await figma.variables.getLocalVariableCollectionsAsync();
const m3 = cols.find(c => c.name === 'M3 Expressive');
if (!m3) throw new Error('collection "M3 Expressive" not found — run script 00 first');
const darkMode = m3.modes.find(m => m.name === 'Dark');
if (!darkMode) throw new Error('no Dark mode on M3 Expressive — run script 12 first');

const vars = await figma.variables.getLocalVariablesAsync('COLOR');
const V = {};
for (const v of vars) if (v.variableCollectionId === m3.id) V[v.name] = v;
const paint = (n) => figma.variables.setBoundVariableForPaint({ type: 'SOLID', color: { r: 0, g: 0, b: 0 } }, 'color', V[n]);
const hexPaint = (h, a) => ({ type: 'SOLID', color: { r: parseInt(h.slice(1, 3), 16) / 255, g: parseInt(h.slice(3, 5), 16) / 255, b: parseInt(h.slice(5, 7), 16) / 255 }, opacity: a === undefined ? 1 : a });
const P = (n, a) => (n.charAt(0) === '#' ? hexPaint(n, a) : paint(n));

const ICON = {
  car: '<path d="m21 8-2 2-1.5-3.7A2 2 0 0 0 15.646 5H8.4a2 2 0 0 0-1.903 1.257L5 10 3 8"/><path d="M7 14h.01"/><path d="M17 14h.01"/><rect width="18" height="8" x="3" y="10" rx="2"/><path d="M5 18v2"/><path d="M19 18v2"/>'
};

function icon(markup, size, colorName, weight) {
  const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#000000" stroke-width="' + (weight || 2) + '" stroke-linecap="round" stroke-linejoin="round">' + markup + '</svg>';
  const n = figma.createNodeFromSvg(svg);
  n.name = 'glyph'; n.clipsContent = false;
  for (const c of n.findAll(x => 'strokes' in x)) { if (c.strokes.length) c.strokes = [P(colorName)]; }
  if (size !== 24) n.rescale(size / 24);
  return n;
}

function T(chars, o) {
  o = o || {};
  const t = figma.createText();
  t.fontName = { family: F, style: o.style || 'Regular' };
  t.characters = chars;
  t.fontSize = o.size || 14;
  t.lineHeight = { unit: 'PIXELS', value: o.lh || Math.round((o.size || 14) * 1.45) };
  t.letterSpacing = { unit: 'PIXELS', value: o.ls || 0 };
  t.textAlignHorizontal = o.align || 'LEFT';
  t.fills = [P(o.color || 'color/on-surface')];
  t.name = chars.slice(0, 24);
  if (o.width) { t.textAutoResize = 'HEIGHT'; t.resize(o.width, t.height); }
  return t;
}

// ---- adaptive-icon geometry --------------------------------------------------------------
const S = 4;                       // master scale — 108dp drawn at 432 px (@xxxhdpi)
const CANVAS = 108 * S;            // 432 — the full adaptive canvas
const VIEW = 72 * S;               // 288 — what any mask can reveal
const SAFE = 66 * S;               // 264 — what every mask is guaranteed to reveal
const BLEED = (CANVAS - VIEW) / 2; // 72  — parallax headroom per side
const CONTAINER = 52 * S;          // 208 — the scalloped mark; see the header note on 52 vs 66
const GLYPH = Math.round(CONTAINER * 0.5);

function bgLayer() {
  const f = figma.createFrame();
  f.name = 'ic_launcher_background';
  f.resize(CANVAS, CANVAS);
  f.fills = [paint('color/primary')];
  f.clipsContent = true;
  return f;
}

function fgLayer() {
  const f = figma.createFrame();
  f.name = 'ic_launcher_foreground';
  f.resize(CANVAS, CANVAS);
  f.fills = []; f.clipsContent = false;

  const star = figma.createStar();
  star.name = 'scalloped-container';
  star.pointCount = 10; star.innerRadius = 0.9;
  star.resize(CONTAINER, CONTAINER);
  star.cornerRadius = Math.round(CONTAINER * 0.078);
  star.fills = [paint('color/primary-container')];
  f.appendChild(star);
  star.x = (CANVAS - CONTAINER) / 2; star.y = (CANVAS - CONTAINER) / 2;

  const g = icon(ICON.car, GLYPH, 'color/on-primary-container', 2.0);
  f.appendChild(g);
  g.x = (CANVAS - GLYPH) / 2; g.y = (CANVAS - GLYPH) / 2;
  return f;
}

function monoLayer(colorName) {
  const f = figma.createFrame();
  f.name = 'ic_launcher_monochrome';
  f.resize(CANVAS, CANVAS);
  f.fills = []; f.clipsContent = false;
  // Deliberately larger than the colour icon's 26dp glyph: the themed variant has no container
  // to sit in, so the car has to hold the whole 72dp viewport on its own.
  const size = Math.round(SAFE * 0.72);
  const g = icon(ICON.car, size, colorName || 'color/on-surface', 2.2);
  f.appendChild(g);
  g.x = (CANVAS - size) / 2; g.y = (CANVAS - size) / 2;
  return f;
}

// The composite as the launcher assembles it, at an arbitrary rendered size. `size` is the
// rendered edge of the MASK, so the 108dp canvas is scaled by size/72dp and the 18dp bleed
// falls outside the clip on every side — exactly the crop a real launcher applies.
function composite(size, o) {
  o = o || {};
  const f = figma.createFrame();
  f.name = o.name || ('mask · ' + size);
  f.resize(size, size);
  f.clipsContent = true;
  f.fills = [];
  if (o.shape === 'circle') f.cornerRadius = size;
  else if (o.shape === 'squircle') { f.cornerRadius = size * 0.28; f.cornerSmoothing = 0.6; }
  else if (o.shape === 'rounded') f.cornerRadius = size * 0.18;
  else f.cornerRadius = 0;

  const k = size / (o.full ? CANVAS : VIEW);
  const off = o.full ? 0 : -BLEED * k;
  for (const layer of [bgLayer(), fgLayer()]) {
    f.appendChild(layer);
    layer.rescale(k);
    layer.x = off; layer.y = off;
  }
  return f;
}

// ---- board scaffolding -------------------------------------------------------------------
for (const n of page.children.filter(c => c.name === 'launcher-icon')) n.remove();

const sec = figma.createSection();
sec.name = 'launcher-icon';
page.appendChild(sec);
sec.x = 0; sec.y = 2300;
sec.resizeWithoutConstraints(2400, 2400);
sec.fills = [paint('color/surface-container')];

const board = figma.createAutoLayout('VERTICAL', { name: 'android-launcher-icon', itemSpacing: 44 });
sec.appendChild(board);
board.paddingTop = 44; board.paddingBottom = 44; board.paddingLeft = 44; board.paddingRight = 44;
board.fills = [paint('color/surface')];
board.cornerRadius = 28;

function tile(art, label, sub) {
  const t = figma.createAutoLayout('VERTICAL', { name: 'tile', itemSpacing: 10 });
  t.fills = []; t.counterAxisAlignItems = 'CENTER';
  t.appendChild(art);
  const cap = figma.createAutoLayout('VERTICAL', { name: 'caption', itemSpacing: 2 });
  cap.fills = []; cap.counterAxisAlignItems = 'CENTER';
  cap.appendChild(T(label, { size: 13, style: 'Medium', align: 'CENTER' }));
  if (sub) cap.appendChild(T(sub, { size: 11, color: 'color/on-surface-variant', align: 'CENTER' }));
  t.appendChild(cap);
  return t;
}

function row(title, note) {
  const r = figma.createAutoLayout('VERTICAL', { name: title, itemSpacing: 18 });
  r.fills = [];
  const h = figma.createAutoLayout('VERTICAL', { name: 'heading', itemSpacing: 2 });
  h.fills = [];
  h.appendChild(T(title, { size: 20, style: 'SemiBold', lh: 26 }));
  if (note) h.appendChild(T(note, { size: 13, color: 'color/on-surface-variant', lh: 18 }));
  r.appendChild(h);
  const items = figma.createAutoLayout('HORIZONTAL', { name: 'items', itemSpacing: 28 });
  items.fills = []; items.counterAxisAlignItems = 'MAX';
  r.appendChild(items);
  board.appendChild(r);
  return items;
}

const title = figma.createAutoLayout('VERTICAL', { name: 'title', itemSpacing: 4 });
title.fills = [];
title.appendChild(T('Launcher icon — Android', { size: 34, style: 'Bold', lh: 40, ls: -0.5 }));
title.appendChild(T('Adaptive icon · 108dp canvas drawn at 4x (432 px) · minSdk 26, so vector layers only', { size: 14, color: 'color/on-surface-variant' }));
board.appendChild(title);

// ---- 1 · source layers -------------------------------------------------------------------
const r1 = row('Source layers', 'Three drawables referenced by res/mipmap-anydpi-v26/ic_launcher.xml. Each is the full 108dp canvas.');
r1.appendChild(tile(bgLayer(), 'ic_launcher_background', 'flat color/primary #006A57'));
r1.appendChild(tile(fgLayer(), 'ic_launcher_foreground', 'scalloped container + car glyph'));
r1.appendChild(tile(monoLayer(), 'ic_launcher_monochrome', 'themed icon, Android 13+'));

const guides = figma.createFrame();
guides.name = 'safe-zones';
guides.resize(CANVAS, CANVAS);
guides.fills = [paint('color/surface-container-high')];
guides.clipsContent = true;
for (const layer of [bgLayer(), fgLayer()]) {
  guides.appendChild(layer);
  layer.x = 0; layer.y = 0; layer.opacity = 0.4;
}
const viewport = figma.createEllipse();
viewport.name = 'viewport · 72dp';
viewport.resize(VIEW, VIEW);
viewport.fills = [];
viewport.strokes = [paint('color/on-surface')];
viewport.strokeWeight = S;
guides.appendChild(viewport);
viewport.x = BLEED; viewport.y = BLEED;
const safe = figma.createEllipse();
safe.name = 'safe zone · 66dp';
safe.resize(SAFE, SAFE);
safe.fills = [];
safe.strokes = [paint('color/error')];
safe.strokeWeight = S;
safe.dashPattern = [4 * S, 3 * S];
guides.appendChild(safe);
safe.x = (CANVAS - SAFE) / 2; safe.y = (CANVAS - SAFE) / 2;
r1.appendChild(tile(guides, 'safe zones', '108 canvas · 72 viewport · 66 safe · 52 mark'));

// ---- 2 · masks ---------------------------------------------------------------------------
const r2 = row('Launcher masks · rendered at 192 px (xxxhdpi)', 'The OEM picks the mask. Every one of them crops to the 72dp viewport, so nothing outside the 66dp safe zone can be relied on.');
r2.appendChild(tile(composite(192, { shape: 'circle', name: 'mask · circle' }), 'circle', 'Pixel launcher'));
r2.appendChild(tile(composite(192, { shape: 'squircle', name: 'mask · squircle' }), 'squircle', 'One UI / MIUI'));
r2.appendChild(tile(composite(192, { shape: 'rounded', name: 'mask · rounded-square' }), 'rounded square', 'stock AOSP'));
r2.appendChild(tile(composite(192, { shape: 'square', name: 'mask · square' }), 'square', 'worst case'));

// ---- 3 · themed icon ---------------------------------------------------------------------
const r3 = row('Themed icon · Android 13+', 'The system replaces both colour layers with the wallpaper palette and tints the monochrome drawable. Only the glyph survives, so it carries the whole mark.');
function themedTile(dark) {
  const f = figma.createFrame();
  f.name = 'themed · ' + (dark ? 'dark' : 'light');
  f.resize(192, 192);
  f.cornerRadius = 192;
  f.clipsContent = true;
  // Same token both ways — the Dark pin below is what changes how it resolves, which is the
  // point: a themed icon is the wallpaper palette, not a second drawing.
  f.fills = [paint('color/primary-container')];
  const k = 192 / VIEW;
  const mono = monoLayer('color/on-primary-container');
  f.appendChild(mono);
  mono.rescale(k);
  mono.x = -BLEED * k; mono.y = -BLEED * k;
  if (dark) f.setExplicitVariableModeForCollection(m3, darkMode.modeId);
  return f;
}
r3.appendChild(tile(themedTile(false), 'light wallpaper palette', 'primary-container / on-primary-container'));
r3.appendChild(tile(themedTile(true), 'dark wallpaper palette', 'same tokens, Dark mode pinned'));

// ---- 4 · density ladder ------------------------------------------------------------------
const r4 = row('Density ladder · legibility check', 'Not an export list — minSdk 26 ships vectors. These are the pixel sizes the launcher actually rasterises to, smallest first.');
for (const [px, bucket] of [[48, 'mdpi'], [72, 'hdpi'], [96, 'xhdpi'], [144, 'xxhdpi'], [192, 'xxxhdpi']]) {
  r4.appendChild(tile(composite(px, { shape: 'circle', name: 'ic_launcher · ' + bucket }), px + ' px', bucket));
}

// ---- 5 · store + spec --------------------------------------------------------------------
const r5 = row('Play Store listing', 'Full 108dp canvas, square, no mask and no transparency — Play applies its own rounding.');
r5.appendChild(tile(composite(512, { shape: 'square', full: true, name: 'playstore-icon' }), 'playstore-icon.png', '512 × 512 · 32-bit PNG'));

const spec = figma.createAutoLayout('VERTICAL', { name: 'export-spec', itemSpacing: 12 });
spec.fills = [paint('color/surface-container-low')];
spec.cornerRadius = 20;
spec.paddingTop = 24; spec.paddingBottom = 24; spec.paddingLeft = 24; spec.paddingRight = 24;
spec.appendChild(T('Export', { size: 16, style: 'SemiBold' }));
spec.appendChild(T(
  'res/drawable/ic_launcher_foreground.xml — export the foreground layer as SVG, convert to a Vector Drawable.\n' +
  'res/drawable/ic_launcher_monochrome.xml — same route for the themed layer.\n' +
  'res/values/ic_launcher_background.xml — the background is a flat colour, so it ships as <color>, not a drawable.\n' +
  'res/mipmap-anydpi-v26/ic_launcher.xml + ic_launcher_round.xml — <adaptive-icon> with <background>, <foreground>, <monochrome>.\n' +
  '\n' +
  'PNG fallback, only if a raster set is ever needed (108dp canvas): mdpi 108 · hdpi 162 · xhdpi 216 · xxhdpi 324 · xxxhdpi 432 px.\n' +
  'Masters here are 432 px, so those are export scales 0.25x / 0.375x / 0.5x / 0.75x / 1x.\n' +
  '\n' +
  'Play Store: 512 x 512 PNG, 32-bit, square, no alpha.',
  { size: 13, lh: 21, color: 'color/on-surface-variant', width: 520 }
));
r5.appendChild(spec);

// ---- fit the section ---------------------------------------------------------------------
board.x = 40; board.y = 40;
sec.resizeWithoutConstraints(board.width + 80, board.height + 80);

await sec.screenshot();

return {
  createdNodeIds: [sec.id, board.id],
  section: sec.id,
  board: board.id,
  size: { width: sec.width, height: sec.height },
  geometry: { canvas: CANVAS, viewport: VIEW, safe: SAFE, glyph: GLYPH }
};
