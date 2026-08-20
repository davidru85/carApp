// carApp — rebuilds the Android settings screen in the M3 Expressive idiom.
//
// Targets page 14:2. Rebuilds BOTH `screen-settings` and `screen-settings · dark` in place;
// the dark twin is pinned to the Dark mode, so its new children resolve dark automatically.
// Idempotent — it removes what it previously built before rebuilding.
//
// Preserves `status-bar` (script 16 owns that) and `gesture-handle`.
//
// WHY
// Settings was the flattest of the six Android screens: one `surface-container` card per group
// with 1px dividers, and a `small` top app bar. That is correct M3 and unremarkable Expressive.
// Every other screen carries the Expressive vocabulary — the scalloped mark, shape morphs, the
// morphing loading indicator, extended FABs — and settings carried none of it.
//
// WHAT CHANGES (styling only; every string and every row is preserved)
//  1. Large top app bar, matching home. Expressive leans on big flexible bars, not `small`.
//  2. **Connected item groups.** This is the signature Expressive list: items separated by a
//     4px gap instead of hairline dividers, with asymmetric radii so a group reads as one
//     carved object — first item 28 outside / 6 inside, middles 6, last mirrors the first,
//     lone items 28 all round. Dividers disappear entirely.
//  3. Leading tonal circles. Every row gets an icon in a 40px `secondary-container` disc.
//  4. Tonal differentiation instead of uniform grey: the backup row is `primary-container`
//     because it reports a healthy state, and Eliminar cuenta is `error-container` rather than
//     grey-with-red-text. Colour carries the meaning instead of decorating it.
//  5. Section headers promoted to the primary role at title-small.
//  6. A real M3 switch for the analytics row — track, outline, and a thumb that is small and
//     outline-coloured when off, which is the actual M3 spec, not a grey pill.
//  7. The scalloped mark returns in the footer as a small Expressive callback to welcome.
//
// AFTER RUNNING: re-run script 15 for android. The back button keeps the name `icon-button`,
// so the existing wiring still resolves, but re-running is the cheap way to be certain.

const PAGE_ID = '14:2';
const BUILT = ['top-app-bar · small', 'top-app-bar · large', 'settings-list', 'footer'];

const LIST_Y = 152;    // status bar 32 + app bar 112, plus an 8px breath
const GESTURE_Y = 901; // the gesture handle; nothing may reach it

// Disc tones. The first pass used `secondary-container` (#cde9dd) on rows filled with
// `surface-container-high` (#e4eae6) — two values so close the discs nearly vanished. Varying
// the tone per group fixes the contrast and does the Expressive job of colour-coding sections.
const DISC_PRIMARY = { discTone: 'color/primary-container', discInk: 'color/on-primary-container' };
const DISC_TERTIARY = { discTone: 'color/tertiary-container', discInk: 'color/on-tertiary-container' };

const R_OUT = 28; // group outer radius — corner-xl
const R_IN = 6;   // the tight inner radius that makes a group read as one carved object
const GAP = 4;

const page = await figma.getNodeByIdAsync(PAGE_ID);
await figma.setCurrentPageAsync(page);
const F = 'Roboto Flex';
for (const s of ['Regular', 'Medium', 'SemiBold', 'Bold']) await figma.loadFontAsync({ family: F, style: s });

const cols = await figma.variables.getLocalVariableCollectionsAsync();
const m3 = cols.find(c => c.name === 'M3 Expressive');
if (!m3) throw new Error('M3 Expressive collection not found');
const vars = await figma.variables.getLocalVariablesAsync('COLOR');
const V = {};
for (const v of vars) if (v.variableCollectionId === m3.id) V[v.name] = v;
const paint = (n) => {
  if (!V[n]) throw new Error('variable not found: ' + n);
  return figma.variables.setBoundVariableForPaint({ type: 'SOLID', color: { r: 0, g: 0, b: 0 } }, 'color', V[n]);
};

const ICON = {
  back: '<path d="m12 19-7-7 7-7"/><path d="M19 12H5"/>',
  chev: '<path d="m9 18 6-6-6-6"/>',
  euro: '<path d="M4 10h12"/><path d="M4 14h9"/><path d="M19 6a7.7 7.7 0 0 0-5.2-2A7.9 7.9 0 0 0 6 12c0 4.4 3.5 8 7.8 8 2 0 3.8-.8 5.2-2"/>',
  ruler: '<path d="M21.3 8.7 8.7 21.3a1 1 0 0 1-1.4 0l-4.6-4.6a1 1 0 0 1 0-1.4L15.3 2.7a1 1 0 0 1 1.4 0l4.6 4.6a1 1 0 0 1 0 1.4Z"/><path d="m7.5 10.5 2 2"/><path d="m10.5 7.5 2 2"/><path d="m13.5 4.5 2 2"/><path d="m4.5 13.5 2 2"/>',
  drop: '<path d="M12 22a7 7 0 0 0 7-7c0-2-1-3.9-3-5.5s-3.5-4-4-6.5c-.5 2.5-2 4.9-4 6.5C6 11.1 5 13 5 15a7 7 0 0 0 7 7z"/>',
  cloud: '<path d="M20 17.58A5 5 0 0 0 18 8h-1.26A8 8 0 1 0 4 16.25"/><path d="m8 16 4-4 4 4"/><path d="M12 12v9"/>',
  chart: '<path d="M3 3v18h18"/><path d="m19 9-5 5-4-4-3 3"/>',
  logout: '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="m16 17 5-5-5-5"/><path d="M21 12H9"/>',
  trash: '<path d="M3 6h18"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/><path d="M10 11v6"/><path d="M14 11v6"/>',
  check: '<path d="M20 6 9 17l-5-5"/>'
};

function icon(markup, size, colorName, weight) {
  const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#000000" stroke-width="' + (weight || 2) + '" stroke-linecap="round" stroke-linejoin="round">' + markup + '</svg>';
  const n = figma.createNodeFromSvg(svg);
  n.name = 'icon'; n.clipsContent = false;
  for (const c of n.findAll(x => 'strokes' in x)) { if (c.strokes.length) c.strokes = [paint(colorName)]; }
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
  t.fills = [paint(o.color || 'color/on-surface')];
  t.name = chars.slice(0, 24);
  return t;
}

// ---- the Expressive connected-group radius rule -------------------------------
function radii(node, index, count) {
  const first = index === 0, last = index === count - 1;
  node.topLeftRadius = first ? R_OUT : R_IN;
  node.topRightRadius = first ? R_OUT : R_IN;
  node.bottomLeftRadius = last ? R_OUT : R_IN;
  node.bottomRightRadius = last ? R_OUT : R_IN;
}

function buildBar(parent) {
  const bar = figma.createFrame();
  bar.name = 'top-app-bar · large';
  bar.resize(412, 112);
  bar.fills = [];
  bar.clipsContent = false;
  parent.appendChild(bar);
  bar.x = 0; bar.y = 32;

  const btn = figma.createFrame();
  btn.name = 'icon-button';           // kept so the existing prototype link still resolves
  btn.resize(48, 48); btn.cornerRadius = 999; btn.fills = [];
  bar.appendChild(btn); btn.x = 8; btn.y = 4;
  const bi = icon(ICON.back, 24, 'color/on-surface');
  btn.appendChild(bi); bi.x = 12; bi.y = 12;

  const title = T('Ajustes', { size: 32, style: 'Bold', lh: 40, ls: -0.4 });
  bar.appendChild(title); title.x = 24; title.y = 62;
  return bar;
}

// A single row. `tone` picks the container role; `trailing` is a node or null.
function buildRow(group, opts) {
  const r = figma.createAutoLayout('HORIZONTAL', { name: 'list-item', itemSpacing: 16 });
  group.appendChild(r);
  r.fills = [paint(opts.tone || 'color/surface-container-high')];
  r.paddingLeft = 16; r.paddingRight = 20;
  r.paddingTop = 10; r.paddingBottom = 10;
  r.counterAxisAlignItems = 'CENTER';
  r.layoutSizingHorizontal = 'FILL';

  const disc = figma.createFrame();
  disc.name = 'leading-disc';
  disc.resize(40, 40); disc.cornerRadius = 999;
  disc.fills = [paint(opts.discTone || 'color/secondary-container')];
  r.appendChild(disc);
  const gi = icon(opts.icon, 22, opts.discInk || 'color/on-secondary-container');
  disc.appendChild(gi); gi.x = 9; gi.y = 9;

  const colf = figma.createAutoLayout('VERTICAL', { itemSpacing: 2 });
  r.appendChild(colf); colf.fills = []; colf.layoutSizingHorizontal = 'FILL';
  colf.appendChild(T(opts.label, { size: 16, style: 'Medium', lh: 22, color: opts.ink || 'color/on-surface' }));
  if (opts.sub) {
    const s2 = T(opts.sub, { size: 13, lh: 17, color: opts.subInk || 'color/on-surface-variant' });
    colf.appendChild(s2);
    // Order matters: HEIGHT before FILL, and both only after the node is parented.
    s2.textAutoResize = 'HEIGHT';
    s2.layoutSizingHorizontal = 'FILL';
  }

  if (opts.value) {
    r.appendChild(T(opts.value, { size: 14, style: 'Medium', lh: 20, color: opts.ink || 'color/on-surface-variant' }));
  }
  if (opts.chip) {
    const c = figma.createAutoLayout('HORIZONTAL', { name: 'status-chip', itemSpacing: 6 });
    r.appendChild(c); c.fills = [paint('color/primary')]; c.cornerRadius = 999;
    c.paddingLeft = 10; c.paddingRight = 12; c.paddingTop = 5; c.paddingBottom = 5;
    c.counterAxisAlignItems = 'CENTER';
    const ci = icon(ICON.check, 14, 'color/on-primary', 2.6);
    c.appendChild(ci);
    c.appendChild(T(opts.chip, { size: 13, style: 'Medium', lh: 16, color: 'color/on-primary' }));
  }
  if (opts.toggle !== undefined) {
    const tr = figma.createFrame();
    tr.name = 'switch · ' + (opts.toggle ? 'on' : 'off');
    tr.resize(52, 32); tr.cornerRadius = 999; tr.clipsContent = false;
    tr.fills = [paint(opts.toggle ? 'color/primary' : 'color/surface-variant')];
    if (!opts.toggle) { tr.strokes = [paint('color/outline')]; tr.strokeWeight = 2; tr.strokeAlign = 'INSIDE'; }
    r.appendChild(tr);
    const th = figma.createEllipse();
    // M3 spec: the off thumb is small and outline-coloured; it only grows when selected.
    const d = opts.toggle ? 24 : 16;
    th.resize(d, d);
    th.x = opts.toggle ? 24 : 8;
    th.y = (32 - d) / 2;
    th.fills = [paint(opts.toggle ? 'color/on-primary' : 'color/outline')];
    tr.appendChild(th);
  }
  if (opts.chevron) r.appendChild(icon(ICON.chev, 20, opts.ink || 'color/on-surface-variant', 2.2));
  return r;
}

function buildGroup(list, header, rows) {
  const g = figma.createAutoLayout('VERTICAL', { name: 'group · ' + header, itemSpacing: 8 });
  list.appendChild(g); g.fills = []; g.layoutSizingHorizontal = 'FILL';

  const hw = figma.createAutoLayout('HORIZONTAL');
  g.appendChild(hw); hw.fills = []; hw.paddingLeft = 12; hw.layoutSizingHorizontal = 'FILL';
  hw.appendChild(T(header, { size: 14, style: 'SemiBold', lh: 20, ls: 0.1, color: 'color/primary' }));

  const stack = figma.createAutoLayout('VERTICAL', { name: 'connected-group', itemSpacing: GAP });
  g.appendChild(stack); stack.fills = []; stack.layoutSizingHorizontal = 'FILL';

  rows.forEach((o, i) => radii(buildRow(stack, o), i, rows.length));
  return g;
}

function buildFooter(parent) {
  const f = figma.createAutoLayout('VERTICAL', { name: 'footer', itemSpacing: 4 });
  parent.appendChild(f);
  f.fills = []; f.counterAxisAlignItems = 'CENTER';
  f.layoutSizingHorizontal = 'FIXED'; f.resize(412, f.height);

  const mark = figma.createStar();
  mark.name = 'expressive-mark';
  mark.pointCount = 8; mark.innerRadius = 0.86; mark.cornerRadius = 3;
  mark.resize(26, 26);
  mark.fills = [paint('color/primary-container')];
  f.appendChild(mark);

  f.appendChild(T('carApp v1.0.0', { size: 13, style: 'Medium', lh: 18, color: 'color/on-surface-variant' }));
  f.appendChild(T('Local-First & Safe Tracker', { size: 13, lh: 18, color: 'color/outline' }));
  return f;
}

function rebuild(S) {
  for (const c of S.children.filter(c => BUILT.indexOf(c.name) !== -1)) c.remove();

  buildBar(S);

  const list = figma.createAutoLayout('VERTICAL', { name: 'settings-list', itemSpacing: 14 });
  S.appendChild(list);
  list.fills = [];
  list.paddingLeft = 16; list.paddingRight = 16;
  list.layoutSizingHorizontal = 'FIXED'; list.resize(412, list.height);
  list.x = 0; list.y = LIST_Y;

  buildGroup(list, 'Unidades y moneda', [
    { label: 'Moneda', sub: 'Divisa predeterminada de los repostajes', icon: ICON.euro, value: 'EUR €', chevron: true, ...DISC_PRIMARY },
    { label: 'Unidad de distancia', icon: ICON.ruler, value: 'Kilómetros', chevron: true, ...DISC_PRIMARY },
    { label: 'Unidad de volumen', icon: ICON.drop, value: 'Litros', chevron: true, ...DISC_PRIMARY }
  ]);

  buildGroup(list, 'Seguridad y copias', [
    {
      label: 'Copia de seguridad', sub: 'Última sincronización local guardada',
      icon: ICON.cloud, chip: 'Activa',
      tone: 'color/primary-container', ink: 'color/on-primary-container',
      subInk: 'color/on-primary-container',
      discTone: 'color/primary', discInk: 'color/on-primary'
    }
  ]);

  buildGroup(list, 'Privacidad', [
    {
      label: 'Compartir analíticas',
      sub: 'Comparte telemetría anónima para ayudarnos a mejorar carApp.',
      icon: ICON.chart, toggle: false, ...DISC_TERTIARY
    }
  ]);

  buildGroup(list, 'Cuenta', [
    { label: 'Cerrar sesión', icon: ICON.logout, chevron: true },
    {
      label: 'Eliminar cuenta', icon: ICON.trash, chevron: true,
      tone: 'color/error-container', ink: 'color/on-error-container',
      discTone: 'color/error', discInk: 'color/on-error'
    }
  ]);

  const f = buildFooter(S);
  f.x = 0; f.y = LIST_Y + list.height + 18;
  // Self-check. The first pass overflowed (footer bottom 936 against a 917 frame, clipping the
  // version text and colliding with the gesture handle). Report it rather than eyeballing.
  const bottom = f.y + f.height;
  return { listHeight: list.height, footerBottom: bottom, overflows: bottom > GESTURE_Y };
}

const targets = page.children.filter(n => n.type === 'FRAME' && n.name.indexOf('screen-settings') === 0);
if (!targets.length) throw new Error('no screen-settings frames found');
const out = targets.map(S => ({ screen: S.name, id: S.id, metrics: rebuild(S) }));

for (const S of targets) await S.screenshot();
return { rebuilt: out };
