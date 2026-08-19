// carApp — Step 07: iOS Liquid Glass · screen-welcome
// STATUS: executed successfully. Created frame 25:2 at x=0 on page 14:3.
//
// FONT WARNING — read before editing. The first attempt at this screen used 'SF Pro'. Figma's
// listAvailableFontsAsync() reports SF Pro as available and loadFontAsync() resolves without
// throwing, but the font is NOT installed: every text node came back hasMissingFont:true with
// width 0, producing a screen with no visible text at all. Inter is used as the SF substitute
// throughout. Do not "correct" this back to SF Pro.
// Inter's semibold style string is 'Semi Bold' (with a space), not 'SemiBold'.
//
// The two remove() calls below cleaned up that failed attempt (23:2) and the font probe (24:2)
// from the original run. They are inert on a fresh file.
//
// Liquid Glass notes: the ambient backdrop is not decoration — BACKGROUND_BLUR has nothing to
// refract without colour behind the glass, and the material collapses to flat grey.

const page = await figma.getNodeByIdAsync('14:3');
await figma.setCurrentPageAsync(page);
for (const id of ['23:2', '24:2']) { const n = await figma.getNodeByIdAsync(id); if (n) n.remove(); }

const F = 'Inter';
const ST = { r: 'Regular', m: 'Medium', sb: 'Semi Bold', b: 'Bold' };
for (const s of Object.values(ST)) await figma.loadFontAsync({ family: F, style: s });

const cols = await figma.variables.getLocalVariableCollectionsAsync();
const lg = cols.find(c => c.name === 'Liquid Glass');
const vars = await figma.variables.getLocalVariablesAsync('COLOR');
const V = {}; for (const v of vars) if (v.variableCollectionId === lg.id) V[v.name] = v;
const paint = (n) => figma.variables.setBoundVariableForPaint({ type: 'SOLID', color: { r: 0, g: 0, b: 0 } }, 'color', V[n]);

const SPECULAR = {
  type: 'GRADIENT_LINEAR',
  gradientTransform: [[0.6, 0.6, -0.1], [-0.6, 0.6, 0.4]],
  gradientStops: [
    { position: 0, color: { r: 1, g: 1, b: 1, a: 0.95 } },
    { position: 0.45, color: { r: 1, g: 1, b: 1, a: 0.22 } },
    { position: 1, color: { r: 1, g: 1, b: 1, a: 0.7 } }
  ]
};
function glass(node, o) {
  o = o || {};
  const clear = o.variant === 'clear';
  node.fills = [paint(clear ? 'material/clear/fill' : (o.raised ? 'material/regular/fill-raised' : 'material/regular/fill'))];
  const lift = o.lift === undefined ? 8 : o.lift;
  node.effects = [
    { type: 'BACKGROUND_BLUR', radius: clear ? 12 : 32, visible: true },
    { type: 'DROP_SHADOW', color: { r: 0.04, g: 0.12, b: 0.10, a: 0.16 }, offset: { x: 0, y: lift }, radius: lift * 2.4, spread: -2, visible: true, blendMode: 'NORMAL' }
  ];
  node.strokes = [SPECULAR]; node.strokeWeight = 1; node.strokeAlign = 'INSIDE';
  return node;
}
const ICON = {
  car: '<path d="m21 8-2 2-1.5-3.7A2 2 0 0 0 15.646 5H8.4a2 2 0 0 0-1.903 1.257L5 10 3 8"/><path d="M7 14h.01"/><path d="M17 14h.01"/><rect width="18" height="8" x="3" y="10" rx="2"/><path d="M5 18v2"/><path d="M19 18v2"/>',
  apple: '<path d="M12 20.94c1.5 0 2.75 1.06 4 1.06 3 0 6-8 6-12.22A4.91 4.91 0 0 0 17 5c-2.22 0-4 1.44-5 2-1-.56-2.78-2-5-2a4.9 4.9 0 0 0-5 4.78C2 14 5 22 8 22c1.25 0 2.5-1.06 4-1.06Z"/><path d="M10 2c1 .5 2 2 2 5"/>',
  loader: '<path d="M21 12a9 9 0 1 1-6.219-8.56"/>',
  signal: '<path d="M2 20h.01"/><path d="M7 20v-4"/><path d="M12 20v-8"/><path d="M17 20V8"/>',
  wifi: '<path d="M12 20h.01"/><path d="M5 12.859a10 10 0 0 1 14 0"/><path d="M8.5 16.429a5 5 0 0 1 7 0"/>',
  battery: '<rect width="16" height="10" x="2" y="7" rx="2"/><line x1="22" x2="22" y1="11" y2="13"/>'
};
const GOOGLE_G = '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none"><path fill="#4285F4" d="M23.52 12.27c0-.79-.07-1.54-.2-2.27H12v4.51h6.47a5.54 5.54 0 0 1-2.4 3.63v3h3.88c2.27-2.09 3.57-5.17 3.57-8.87z"/><path fill="#34A853" d="M12 24c3.24 0 5.96-1.08 7.95-2.91l-3.88-3.01c-1.08.72-2.45 1.15-4.07 1.15-3.13 0-5.78-2.11-6.73-4.96H1.29v3.11A12 12 0 0 0 12 24z"/><path fill="#FBBC05" d="M5.27 14.27a7.2 7.2 0 0 1 0-4.54V6.62H1.29a12 12 0 0 0 0 10.76l3.98-3.11z"/><path fill="#EA4335" d="M12 4.77c1.76 0 3.35.61 4.6 1.8l3.44-3.44C17.95 1.19 15.24 0 12 0A12 12 0 0 0 1.29 6.62l3.98 3.11C6.22 6.88 8.87 4.77 12 4.77z"/></svg>';
function icon(markup, size, colorName, weight) {
  const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#000000" stroke-width="' + (weight === undefined ? 1.9 : weight) + '" stroke-linecap="round" stroke-linejoin="round">' + markup + '</svg>';
  const n = figma.createNodeFromSvg(svg); n.name = 'icon'; n.clipsContent = false;
  for (const c of n.findAll(x => 'strokes' in x)) { if (c.strokes.length) c.strokes = [paint(colorName)]; }
  if (size !== 24) n.rescale(size / 24);
  return n;
}
function solidIcon(markup, size, colorName) {
  const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24">' + markup + '</svg>';
  const n = figma.createNodeFromSvg(svg); n.name = 'icon'; n.clipsContent = false;
  for (const c of n.findAll(x => 'fills' in x)) { if (c.type !== 'FRAME') c.fills = [paint(colorName)]; }
  if (size !== 24) n.rescale(size / 24);
  return n;
}
function rawIcon(svg, size) { const n = figma.createNodeFromSvg(svg); n.name = 'icon'; n.clipsContent = false; if (size !== 24) n.rescale(size / 24); return n; }
function T(chars, o) {
  o = o || {}; const t = figma.createText();
  t.fontName = { family: F, style: o.style || ST.r }; t.characters = chars;
  t.fontSize = o.size || 17; t.lineHeight = { unit: 'PIXELS', value: o.lh || Math.round((o.size || 17) * 1.3) };
  t.letterSpacing = { unit: 'PIXELS', value: o.ls === undefined ? -0.2 : o.ls };
  t.textAlignHorizontal = o.align || 'LEFT';
  t.fills = [paint(o.color || 'label/primary')]; t.name = chars.slice(0, 24);
  if (o.width) { t.textAutoResize = 'HEIGHT'; t.resize(o.width, t.height); }
  return t;
}

const S = figma.createFrame();
S.name = 'screen-welcome'; S.resize(402, 874); S.x = 0; S.y = 0;
S.fills = []; S.clipsContent = true; page.appendChild(S);

// ambient backdrop — glass needs colour behind it to refract
const b = figma.createFrame(); b.name = 'ambient-backdrop';
b.resize(402, 874); b.x = 0; b.y = 0; b.clipsContent = true;
b.fills = [paint('background/system')]; S.appendChild(b);
const blobs = [
  ['background/ambient-a', 400, -110, -80, 1],
  ['background/ambient-b', 360, 170, 180, 1],
  ['background/ambient-c', 330, -110, 470, 1],
  ['background/ambient-a', 320, 190, 620, 1],
  ['background/ambient-c', 240, 40, 790, 0.9]
];
for (const [c, s, x, y, op] of blobs) {
  const e = figma.createEllipse(); e.resize(s, s); e.x = x; e.y = y;
  e.fills = [paint(c)]; e.opacity = op; e.name = 'ambient';
  e.effects = [{ type: 'LAYER_BLUR', radius: 60, visible: true }];
  b.appendChild(e);
}

const sb = figma.createAutoLayout('HORIZONTAL', { name: 'status-bar' });
S.appendChild(sb); sb.layoutSizingHorizontal = 'FIXED'; sb.resize(402, 54);
sb.primaryAxisAlignItems = 'SPACE_BETWEEN'; sb.counterAxisAlignItems = 'CENTER';
sb.paddingLeft = 30; sb.paddingRight = 26; sb.paddingTop = 12; sb.fills = []; sb.x = 0; sb.y = 0;
sb.appendChild(T('9:41', { size: 15, style: ST.sb, lh: 20 }));
const sr = figma.createAutoLayout('HORIZONTAL', { itemSpacing: 6 }); sb.appendChild(sr);
sr.fills = []; sr.counterAxisAlignItems = 'CENTER';
sr.appendChild(icon(ICON.signal, 16, 'label/primary'));
sr.appendChild(icon(ICON.wifi, 16, 'label/primary'));
sr.appendChild(icon(ICON.battery, 16, 'label/primary'));

const hero = figma.createAutoLayout('VERTICAL', { name: 'brand-hero', itemSpacing: 22 });
S.appendChild(hero); hero.fills = []; hero.counterAxisAlignItems = 'CENTER';
hero.layoutSizingHorizontal = 'FIXED'; hero.resize(402, hero.height);
hero.x = 0; hero.y = 138;

const mark = figma.createAutoLayout('HORIZONTAL', { name: 'glass-mark' });
hero.appendChild(mark);
mark.primaryAxisAlignItems = 'CENTER'; mark.counterAxisAlignItems = 'CENTER';
mark.resize(128, 128); mark.primaryAxisSizingMode = 'FIXED'; mark.counterAxisSizingMode = 'FIXED';
mark.cornerRadius = 999; glass(mark, { raised: true, lift: 12 });
mark.appendChild(icon(ICON.car, 60, 'accent/brand', 1.7));

const words = figma.createAutoLayout('VERTICAL', { name: 'wordmark', itemSpacing: 6 });
hero.appendChild(words); words.fills = []; words.counterAxisAlignItems = 'CENTER';
words.appendChild(T('carApp', { size: 34, style: ST.b, lh: 41, ls: 0.4, align: 'CENTER' }));
words.appendChild(T('Local-First Fuel & Expense Tracker', { size: 15, lh: 20, color: 'label/secondary', align: 'CENTER' }));

const card = figma.createAutoLayout('VERTICAL', { name: 'auth-container · glass regular', itemSpacing: 10 });
S.appendChild(card);
card.cornerRadius = 34;
card.paddingTop = 16; card.paddingBottom = 14; card.paddingLeft = 16; card.paddingRight = 16;
card.layoutSizingHorizontal = 'FIXED'; card.resize(370, card.height);
card.counterAxisAlignItems = 'CENTER';
glass(card, { lift: 14 });

function capsule(label, o) {
  const btn = figma.createAutoLayout('HORIZONTAL', { name: o.name, itemSpacing: 8 });
  card.appendChild(btn);
  btn.cornerRadius = 999;
  btn.primaryAxisAlignItems = 'CENTER'; btn.counterAxisAlignItems = 'CENTER';
  btn.paddingLeft = 20; btn.paddingRight = 20;
  if (o.glass) glass(btn, { variant: o.glass, lift: 4 });
  else { btn.fills = [paint(o.fill)]; btn.effects = []; }
  btn.layoutSizingHorizontal = 'FILL';
  btn.resize(btn.width, 50); btn.primaryAxisSizingMode = 'FIXED'; btn.counterAxisSizingMode = 'FIXED';
  if (o.iconNode) btn.appendChild(o.iconNode);
  btn.appendChild(T(label, { size: 17, style: ST.sb, lh: 22, color: o.text }));
  return btn;
}
capsule('Continuar con Apple', { name: 'btn-apple', fill: 'label/primary', text: 'accent/on-brand', iconNode: solidIcon(ICON.apple, 19, 'accent/on-brand') });
capsule('Iniciar sesión con Google', { name: 'btn-google · glass regular', glass: 'regular', text: 'label/primary', iconNode: rawIcon(GOOGLE_G, 19) });

const divRow = figma.createAutoLayout('HORIZONTAL', { name: 'divider-row', itemSpacing: 12 });
card.appendChild(divRow); divRow.fills = []; divRow.counterAxisAlignItems = 'CENTER';
divRow.paddingTop = 2; divRow.paddingBottom = 2; divRow.layoutSizingHorizontal = 'FILL';
const l1 = figma.createRectangle(); l1.resize(10, 1); l1.fills = [paint('separator/non-opaque')];
divRow.appendChild(l1); l1.layoutSizingHorizontal = 'FILL';
divRow.appendChild(T('o', { size: 13, color: 'label/tertiary' }));
const l2 = figma.createRectangle(); l2.resize(10, 1); l2.fills = [paint('separator/non-opaque')];
divRow.appendChild(l2); l2.layoutSizingHorizontal = 'FILL';

capsule('Iniciar sesión', { name: 'btn-primary', fill: 'accent/brand', text: 'accent/on-brand' });
capsule('Continuar sin cuenta', { name: 'btn-guest · glass clear', glass: 'clear', text: 'accent/brand' });

const busy = figma.createAutoLayout('HORIZONTAL', { name: 'loading-row', itemSpacing: 7 });
card.appendChild(busy); busy.fills = []; busy.counterAxisAlignItems = 'CENTER';
busy.primaryAxisAlignItems = 'CENTER'; busy.paddingTop = 6; busy.layoutSizingHorizontal = 'FILL';
busy.appendChild(icon(ICON.loader, 14, 'label/tertiary', 2.2));
busy.appendChild(T('Preparando almacenamiento local…', { size: 13, lh: 18, color: 'label/tertiary' }));

card.x = 16; card.y = 874 - 46 - card.height;

const hp = figma.createRectangle(); hp.name = 'home-indicator';
hp.resize(140, 5); hp.cornerRadius = 999; hp.x = 131; hp.y = 853;
hp.fills = [paint('label/primary')]; hp.opacity = 0.4; S.appendChild(hp);

await S.screenshot();
return { createdNodeIds: [S.id], cardHeight: card.height };
