// carApp — Step 02: Android M3 Expressive · screen-welcome
// STATUS: executed successfully. Created frame 16:2 at x=0 on page 14:2.
//
// Demonstrates: expressive scalloped container (createStar with high innerRadius + corner
// radius), M3 Expressive size-M buttons (56dp, full-round), and a tonal button rendered in the
// PRESSED state showing M3 Expressive's shape morph (full-round -> corner-lg) plus a 12% state
// layer. All fills are bound to M3 Expressive colour variables.
//
// The ambient shapes carry their own 60px LAYER_BLUR: hard-edged circles read as unrefined at
// full opacity. Script 03 still re-applies the same values as a fix-up for the original run;
// that fix-up is now redundant and is a no-op on a frame this script built.
//
// Idempotent: an existing light `screen-welcome` on the page is removed before rebuilding, so
// a re-run refreshes rather than stacking a second frame at x=0.

const page = await figma.getNodeByIdAsync('14:2');
await figma.setCurrentPageAsync(page);

const F = 'Roboto Flex';
for (const s of ['Regular','Medium','SemiBold','Bold']) await figma.loadFontAsync({ family: F, style: s });

const cols = await figma.variables.getLocalVariableCollectionsAsync();
const m3 = cols.find(c => c.name === 'M3 Expressive');
const vars = await figma.variables.getLocalVariablesAsync('COLOR');
const V = {};
for (const v of vars) if (v.variableCollectionId === m3.id) V[v.name] = v;
const paint = (n) => figma.variables.setBoundVariableForPaint({ type: 'SOLID', color: { r: 0, g: 0, b: 0 } }, 'color', V[n]);

const ICON = {
  car: '<path d="m21 8-2 2-1.5-3.7A2 2 0 0 0 15.646 5H8.4a2 2 0 0 0-1.903 1.257L5 10 3 8"/><path d="M7 14h.01"/><path d="M17 14h.01"/><rect width="18" height="8" x="3" y="10" rx="2"/><path d="M5 18v2"/><path d="M19 18v2"/>',
  loader: '<path d="M21 12a9 9 0 1 1-6.219-8.56"/>'
};
const GOOGLE_G = '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none"><path fill="#4285F4" d="M23.52 12.27c0-.79-.07-1.54-.2-2.27H12v4.51h6.47a5.54 5.54 0 0 1-2.4 3.63v3h3.88c2.27-2.09 3.57-5.17 3.57-8.87z"/><path fill="#34A853" d="M12 24c3.24 0 5.96-1.08 7.95-2.91l-3.88-3.01c-1.08.72-2.45 1.15-4.07 1.15-3.13 0-5.78-2.11-6.73-4.96H1.29v3.11A12 12 0 0 0 12 24z"/><path fill="#FBBC05" d="M5.27 14.27a7.2 7.2 0 0 1 0-4.54V6.62H1.29a12 12 0 0 0 0 10.76l3.98-3.11z"/><path fill="#EA4335" d="M12 4.77c1.76 0 3.35.61 4.6 1.8l3.44-3.44C17.95 1.19 15.24 0 12 0A12 12 0 0 0 1.29 6.62l3.98 3.11C6.22 6.88 8.87 4.77 12 4.77z"/></svg>';

function icon(markup, size, colorName, weight) {
  const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#000000" stroke-width="' + (weight || 2) + '" stroke-linecap="round" stroke-linejoin="round">' + markup + '</svg>';
  const n = figma.createNodeFromSvg(svg);
  n.name = 'icon'; n.clipsContent = false;
  for (const c of n.findAll(x => 'strokes' in x)) { if (c.strokes.length) c.strokes = [paint(colorName)]; }
  if (size !== 24) n.rescale(size / 24);
  return n;
}
function rawIcon(svg, size) {
  const n = figma.createNodeFromSvg(svg);
  n.name = 'icon'; n.clipsContent = false;
  if (size !== 24) n.rescale(size / 24);
  return n;
}
function T(chars, o) {
  o = o || {};
  const t = figma.createText();
  t.fontName = { family: F, style: o.style || 'Regular' };
  t.characters = chars;
  t.fontSize = o.size || 16;
  t.lineHeight = { unit: 'PIXELS', value: o.lh || Math.round((o.size || 16) * 1.45) };
  t.letterSpacing = { unit: 'PIXELS', value: o.ls || 0 };
  t.textAlignHorizontal = o.align || 'LEFT';
  t.fills = [paint(o.color || 'color/on-surface')];
  t.name = chars.slice(0, 24);
  if (o.width) { t.textAutoResize = 'HEIGHT'; t.resize(o.width, t.height); }
  return t;
}

// ---- screen shell -------------------------------------------------------
// Drop the previous light frame so a re-run refreshes rather than duplicates. The dark twin
// (`screen-welcome · dark`) is left alone; script 13 rebuilds it.
for (const n of page.children.filter(c => c.name === 'screen-welcome')) n.remove();

const S = figma.createFrame();
S.name = 'screen-welcome';
S.resize(412, 917);
S.x = 0; S.y = 0;
S.fills = [paint('color/surface')];
S.clipsContent = true;
page.appendChild(S);

// expressive ambient shapes — bold shape + colour as depth cue
const blobA = figma.createEllipse();
blobA.resize(320, 320); blobA.x = -110; blobA.y = -80;
blobA.fills = [paint('color/primary-container')]; blobA.opacity = 0.55;
blobA.effects = [{ type: 'LAYER_BLUR', radius: 60, visible: true }];
blobA.name = 'ambient-primary'; S.appendChild(blobA);

const blobB = figma.createEllipse();
blobB.resize(240, 240); blobB.x = 290; blobB.y = 150;
blobB.fills = [paint('color/tertiary-container')]; blobB.opacity = 0.6;
blobB.effects = [{ type: 'LAYER_BLUR', radius: 60, visible: true }];
blobB.name = 'ambient-tertiary'; S.appendChild(blobB);

// ---- status bar ---------------------------------------------------------
const sb = figma.createAutoLayout('HORIZONTAL', { name: 'status-bar' });
S.appendChild(sb);
sb.layoutSizingHorizontal = 'FIXED'; sb.resize(412, 32);
sb.primaryAxisAlignItems = 'SPACE_BETWEEN'; sb.counterAxisAlignItems = 'CENTER';
sb.paddingLeft = 24; sb.paddingRight = 24; sb.fills = []; sb.x = 0; sb.y = 0;
sb.appendChild(T('9:41', { size: 14, style: 'Medium', lh: 20, color: 'color/on-surface' }));
const sbr = figma.createAutoLayout('HORIZONTAL', { name: 'sys-icons', itemSpacing: 6 });
sb.appendChild(sbr); sbr.fills = []; sbr.counterAxisAlignItems = 'CENTER';
sbr.appendChild(icon('<path d="M2 20h.01"/><path d="M7 20v-4"/><path d="M12 20v-8"/><path d="M17 20V8"/>', 16, 'color/on-surface'));
sbr.appendChild(icon('<path d="M12 20h.01"/><path d="M5 12.859a10 10 0 0 1 14 0"/><path d="M8.5 16.429a5 5 0 0 1 7 0"/>', 16, 'color/on-surface'));
sbr.appendChild(icon('<rect width="16" height="10" x="2" y="7" rx="2"/><line x1="22" x2="22" y1="11" y2="13"/>', 16, 'color/on-surface'));

// ---- hero ---------------------------------------------------------------
const hero = figma.createAutoLayout('VERTICAL', { name: 'brand-hero', itemSpacing: 28 });
S.appendChild(hero);
hero.fills = []; hero.counterAxisAlignItems = 'CENTER';
hero.layoutSizingHorizontal = 'FIXED'; hero.resize(412, hero.height);
hero.x = 0; hero.y = 128;

const markWrap = figma.createFrame();
markWrap.name = 'expressive-mark'; markWrap.resize(180, 180);
markWrap.fills = []; markWrap.clipsContent = false;
hero.appendChild(markWrap);

const star = figma.createStar();
star.name = 'scalloped-container';
star.pointCount = 10; star.innerRadius = 0.9;
star.resize(180, 180); star.x = 0; star.y = 0;
star.cornerRadius = 14;
star.fills = [paint('color/primary-container')];
markWrap.appendChild(star);

const carIcon = icon(ICON.car, 84, 'color/on-primary-container', 1.9);
markWrap.appendChild(carIcon);
carIcon.x = 48; carIcon.y = 48;

const words = figma.createAutoLayout('VERTICAL', { name: 'wordmark', itemSpacing: 6 });
hero.appendChild(words); words.fills = []; words.counterAxisAlignItems = 'CENTER';
words.appendChild(T('carApp', { size: 45, style: 'Bold', lh: 52, ls: -0.5, color: 'color/on-surface', align: 'CENTER' }));
words.appendChild(T('Registro local de repostajes', { size: 16, lh: 24, color: 'color/on-surface-variant', align: 'CENTER' }));

// ---- auth card ----------------------------------------------------------
const card = figma.createAutoLayout('VERTICAL', { name: 'auth-card', itemSpacing: 12 });
S.appendChild(card);
card.fills = [paint('color/surface-container-low')];
card.cornerRadius = 28;
card.paddingTop = 24; card.paddingBottom = 20; card.paddingLeft = 20; card.paddingRight = 20;
card.layoutSizingHorizontal = 'FIXED'; card.resize(380, card.height);
card.counterAxisAlignItems = 'CENTER';

function mkButton(name, label, opts) {
  const b = figma.createAutoLayout('HORIZONTAL', { name: name, itemSpacing: 10 });
  b.counterAxisAlignItems = 'CENTER'; b.primaryAxisAlignItems = 'CENTER';
  b.paddingLeft = 24; b.paddingRight = 24;
  b.fills = opts.fill ? [paint(opts.fill)] : [];
  b.cornerRadius = opts.radius === undefined ? 999 : opts.radius;
  if (opts.stroke) { b.strokes = [paint(opts.stroke)]; b.strokeWeight = 1; }
  card.appendChild(b);
  b.layoutSizingHorizontal = 'FILL';
  b.resize(b.width, 56);
  b.primaryAxisSizingMode = 'FIXED'; b.counterAxisSizingMode = 'FIXED';
  if (opts.iconNode) b.appendChild(opts.iconNode);
  b.appendChild(T(label, { size: 16, style: 'SemiBold', lh: 24, ls: 0.1, color: opts.text }));
  return b;
}

mkButton('btn-google-outlined', 'Continuar con Google', { stroke: 'color/outline', text: 'color/on-surface', iconNode: rawIcon(GOOGLE_G, 20) });

// Tonal button rendered in PRESSED state: M3 Expressive shape-morph (full -> corner-lg) + 12% state layer
const guest = mkButton('btn-guest-tonal · pressed', 'Continuar sin cuenta', { fill: 'color/secondary-container', radius: 16, text: 'color/on-secondary-container' });
const stateLayer = figma.createRectangle();
stateLayer.name = 'state-layer · pressed 12%';
stateLayer.resize(guest.width, 56); stateLayer.cornerRadius = 16;
stateLayer.fills = [paint('color/on-secondary-container')]; stateLayer.opacity = 0.12;
guest.appendChild(stateLayer);
stateLayer.layoutPositioning = 'ABSOLUTE';
stateLayer.x = 0; stateLayer.y = 0;

const busy = figma.createAutoLayout('HORIZONTAL', { name: 'loading-row', itemSpacing: 8 });
card.appendChild(busy);
busy.fills = []; busy.counterAxisAlignItems = 'CENTER'; busy.primaryAxisAlignItems = 'CENTER';
busy.paddingTop = 4;
busy.layoutSizingHorizontal = 'FILL';
busy.appendChild(icon(ICON.loader, 16, 'color/on-surface-variant', 2.2));
busy.appendChild(T('Preparando almacenamiento local…', { size: 12, style: 'Medium', lh: 16, color: 'color/on-surface-variant' }));

card.x = 16;
card.y = 917 - 40 - card.height;

// ---- gesture bar --------------------------------------------------------
const pill = figma.createRectangle();
pill.name = 'gesture-handle';
pill.resize(108, 4); pill.cornerRadius = 999;
pill.x = 152; pill.y = 901;
pill.fills = [paint('color/on-surface-variant')]; pill.opacity = 0.6;
S.appendChild(pill);

await S.screenshot();
return { createdNodeIds: [S.id], screen: S.id, cardHeight: card.height };
