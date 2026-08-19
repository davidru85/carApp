// carApp — iOS Liquid Glass: MERGED runner for scripts 09 + 10.
// Combines 09-ios-forms.figma.js and 10-ios-settings.figma.js into ONE use_figma call,
// so finishing the redesign costs 1 Figma MCP tool call instead of 2.
// Generated from those two files — edit them, not this, then regenerate.
// Target page: "02 · iOS — Liquid Glass" (id 14:3). Requires the "Liquid Glass" collection.

const r09 = await (async () => {

const page = await figma.getNodeByIdAsync('14:3');
await figma.setCurrentPageAsync(page);
const F = 'Inter';
const ST = { r: 'Regular', m: 'Medium', sb: 'Semi Bold', b: 'Bold' };
for (const s of Object.values(ST)) await figma.loadFontAsync({ family: F, style: s });
const cols = await figma.variables.getLocalVariableCollectionsAsync();
const lg = cols.find(c => c.name === 'Liquid Glass');
const vars = await figma.variables.getLocalVariablesAsync('COLOR');
const V = {}; for (const v of vars) if (v.variableCollectionId === lg.id) V[v.name] = v;
const paint = (n) => figma.variables.setBoundVariableForPaint({ type: 'SOLID', color: { r: 0, g: 0, b: 0 } }, 'color', V[n]);
const SPECULAR = { type: 'GRADIENT_LINEAR', gradientTransform: [[0.6, 0.6, -0.1], [-0.6, 0.6, 0.4]],
  gradientStops: [{ position: 0, color: { r: 1, g: 1, b: 1, a: 0.95 } }, { position: 0.45, color: { r: 1, g: 1, b: 1, a: 0.22 } }, { position: 1, color: { r: 1, g: 1, b: 1, a: 0.7 } }] };
function glass(node, o) {
  o = o || {}; const clear = o.variant === 'clear';
  node.fills = [paint(clear ? 'material/clear/fill' : (o.raised ? 'material/regular/fill-raised' : 'material/regular/fill'))];
  const lift = o.lift === undefined ? 8 : o.lift;
  node.effects = [
    { type: 'BACKGROUND_BLUR', radius: clear ? 12 : 34, visible: true },
    { type: 'DROP_SHADOW', color: { r: 0.04, g: 0.12, b: 0.10, a: o.flat ? 0.07 : 0.16 }, offset: { x: 0, y: lift }, radius: lift * 2.4, spread: -2, visible: true, blendMode: 'NORMAL' }
  ];
  node.strokes = [SPECULAR]; node.strokeWeight = 1; node.strokeAlign = 'INSIDE';
  return node;
}
const ICON = {
  cal: '<path d="M8 2v4"/><path d="M16 2v4"/><rect width="18" height="18" x="3" y="4" rx="2"/><path d="M3 10h18"/>',
  chev: '<path d="m9 18 6-6-6-6"/>',
  signal: '<path d="M2 20h.01"/><path d="M7 20v-4"/><path d="M12 20v-8"/><path d="M17 20V8"/>',
  wifi: '<path d="M12 20h.01"/><path d="M5 12.859a10 10 0 0 1 14 0"/><path d="M8.5 16.429a5 5 0 0 1 7 0"/>',
  battery: '<rect width="16" height="10" x="2" y="7" rx="2"/><line x1="22" x2="22" y1="11" y2="13"/>'
};
function icon(markup, size, colorName, weight) {
  const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#000000" stroke-width="' + (weight === undefined ? 1.9 : weight) + '" stroke-linecap="round" stroke-linejoin="round">' + markup + '</svg>';
  const n = figma.createNodeFromSvg(svg); n.name = 'icon'; n.clipsContent = false;
  for (const c of n.findAll(x => 'strokes' in x)) { if (c.strokes.length) c.strokes = [paint(colorName)]; }
  if (size !== 24) n.rescale(size / 24);
  return n;
}
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
function sheetScreen(name, x, title, leftLabel, rightLabel) {
  const S = figma.createFrame();
  S.name = name; S.resize(402, 874); S.x = x; S.y = 0; S.fills = []; S.clipsContent = true;
  page.appendChild(S);
  const b = figma.createFrame(); b.name = 'ambient-backdrop';
  b.resize(402, 874); b.x = 0; b.y = 0; b.clipsContent = true;
  b.fills = [paint('background/grouped')]; S.appendChild(b);
  const blobs = [['background/ambient-a', 400, -120, -90, 1], ['background/ambient-b', 340, 180, 120, 1],
    ['background/ambient-c', 320, -100, 420, 1], ['background/ambient-a', 330, 170, 600, 1]];
  for (const [c, s, bx, by, op] of blobs) {
    const e = figma.createEllipse(); e.resize(s, s); e.x = bx; e.y = by;
    e.fills = [paint(c)]; e.opacity = op; e.name = 'ambient';
    e.effects = [{ type: 'LAYER_BLUR', radius: 60, visible: true }]; b.appendChild(e);
  }
  const sbTop = figma.createAutoLayout('HORIZONTAL', { name: 'status-bar' });
  S.appendChild(sbTop); sbTop.layoutSizingHorizontal = 'FIXED'; sbTop.resize(402, 44);
  sbTop.primaryAxisAlignItems = 'SPACE_BETWEEN'; sbTop.counterAxisAlignItems = 'CENTER';
  sbTop.paddingLeft = 30; sbTop.paddingRight = 26; sbTop.paddingTop = 10; sbTop.fills = []; sbTop.x = 0; sbTop.y = 0;
  sbTop.appendChild(T('9:41', { size: 15, style: ST.sb, lh: 20 }));
  const sr = figma.createAutoLayout('HORIZONTAL', { itemSpacing: 6 }); sbTop.appendChild(sr);
  sr.fills = []; sr.counterAxisAlignItems = 'CENTER';
  sr.appendChild(icon(ICON.signal, 16, 'label/primary'));
  sr.appendChild(icon(ICON.wifi, 16, 'label/primary'));
  sr.appendChild(icon(ICON.battery, 16, 'label/primary'));

  const sheet = figma.createFrame();
  sheet.name = 'sheet'; sheet.resize(402, 830); sheet.x = 0; sheet.y = 44;
  sheet.topLeftRadius = 34; sheet.topRightRadius = 34;
  sheet.bottomLeftRadius = 0; sheet.bottomRightRadius = 0;
  sheet.fills = [paint('background/grouped')]; sheet.clipsContent = true;
  sheet.effects = [{ type: 'DROP_SHADOW', color: { r: 0, g: 0, b: 0, a: 0.18 }, offset: { x: 0, y: -6 }, radius: 26, spread: 0, visible: true, blendMode: 'NORMAL' }];
  S.appendChild(sheet);

  const content = figma.createAutoLayout('VERTICAL', { name: 'scroll-content', itemSpacing: 22 });
  sheet.appendChild(content); content.fills = [];
  content.layoutSizingHorizontal = 'FIXED'; content.resize(402, content.height);
  content.paddingLeft = 16; content.paddingRight = 16; content.paddingTop = 74;
  content.x = 0; content.y = 0;

  const hdr = figma.createFrame(); hdr.name = 'toolbar · glass';
  hdr.resize(402, 62); hdr.x = 0; hdr.y = 0; hdr.clipsContent = false;
  hdr.topLeftRadius = 34; hdr.topRightRadius = 34;
  sheet.appendChild(hdr); glass(hdr, { lift: 5, flat: true });
  const grab = figma.createRectangle(); grab.name = 'grabber';
  grab.resize(36, 5); grab.cornerRadius = 999; grab.x = 183; grab.y = 8;
  grab.fills = [paint('label/tertiary')]; hdr.appendChild(grab);
  const cancel = T(leftLabel || 'Cancelar', { size: 17, lh: 22, color: 'accent/brand' });
  hdr.appendChild(cancel); cancel.x = 20; cancel.y = 28;
  const ttl = T(title, { size: 17, style: ST.sb, lh: 22, align: 'CENTER' });
  hdr.appendChild(ttl); ttl.textAutoResize = 'HEIGHT'; ttl.resize(200, ttl.height);
  ttl.x = 101; ttl.y = 28;
  if (rightLabel !== null) {
    const savet = T(rightLabel || 'Guardar', { size: 17, style: ST.sb, lh: 22, color: 'accent/brand', align: 'RIGHT' });
    hdr.appendChild(savet); savet.textAutoResize = 'HEIGHT'; savet.resize(80, savet.height);
    savet.x = 302; savet.y = 28;
  }
  const hp = figma.createRectangle(); hp.name = 'home-indicator';
  hp.resize(140, 5); hp.cornerRadius = 999; hp.x = 131; hp.y = 853;
  hp.fills = [paint('label/primary')]; hp.opacity = 0.4; S.appendChild(hp);
  return { S: S, content: content };
}
function section(parent, header) {
  const g = figma.createAutoLayout('VERTICAL', { name: 'section · ' + (header || 'plain'), itemSpacing: 7 });
  parent.appendChild(g); g.fills = []; g.layoutSizingHorizontal = 'FILL';
  if (header) {
    const h = T(header.toUpperCase(), { size: 12, style: ST.m, lh: 16, ls: 0.5, color: 'label/secondary' });
    const hw = figma.createAutoLayout('HORIZONTAL'); g.appendChild(hw);
    hw.fills = []; hw.paddingLeft = 16; hw.appendChild(h); hw.layoutSizingHorizontal = 'FILL';
  }
  const card = figma.createAutoLayout('VERTICAL', { name: 'inset-group', itemSpacing: 0 });
  g.appendChild(card); card.fills = [paint('background/system')];
  card.cornerRadius = 20; card.clipsContent = true; card.layoutSizingHorizontal = 'FILL';
  return { group: g, card: card };
}
function footer(sec, text, danger) {
  const wrap = figma.createAutoLayout('HORIZONTAL'); sec.group.appendChild(wrap);
  wrap.fills = []; wrap.paddingLeft = 16; wrap.paddingRight = 8; wrap.layoutSizingHorizontal = 'FILL';
  const f = T(text, { size: 12, lh: 16, color: danger ? 'accent/destructive' : 'label/secondary', width: 330 });
  wrap.appendChild(f); f.layoutSizingHorizontal = 'FILL';
}
function sep(sec) {
  const holder = figma.createAutoLayout('HORIZONTAL'); sec.card.appendChild(holder);
  holder.fills = []; holder.paddingLeft = 16; holder.layoutSizingHorizontal = 'FILL';
  const s = figma.createRectangle(); s.name = 'separator'; s.fills = [paint('separator/non-opaque')];
  holder.appendChild(s); s.resize(100, 1); s.layoutSizingHorizontal = 'FILL';
}
function row(sec, label, o) {
  o = o || {};
  const r = figma.createAutoLayout('HORIZONTAL', { name: 'row', itemSpacing: 12 });
  sec.card.appendChild(r); r.fills = [];
  r.paddingLeft = 16; r.paddingRight = 16; r.paddingTop = o.tall ? 12 : 14; r.paddingBottom = o.tall ? 12 : 14;
  r.counterAxisAlignItems = 'CENTER'; r.layoutSizingHorizontal = 'FILL';
  if (o.iconMarkup) r.appendChild(icon(o.iconMarkup, 20, 'accent/brand'));
  const lc = figma.createAutoLayout('VERTICAL', { itemSpacing: 1 }); r.appendChild(lc);
  lc.fills = []; lc.layoutSizingHorizontal = 'FILL';
  lc.appendChild(T(label, { size: 17, lh: 22, color: (o.danger || o.errorLabel) ? 'accent/destructive' : 'label/primary' }));
  if (o.sub) { const s2 = T(o.sub, { size: 13, lh: 17, color: 'label/secondary', width: 230 }); lc.appendChild(s2); s2.layoutSizingHorizontal = 'FILL'; }
  if (o.value !== undefined) r.appendChild(T(o.value, { size: 17, lh: 22, style: o.valueStrong ? ST.sb : ST.r, color: o.placeholder ? 'label/tertiary' : (o.valueAccent ? 'accent/brand' : 'label/secondary'), align: 'RIGHT' }));
  if (o.bubble) {
    const bb = figma.createAutoLayout('HORIZONTAL'); r.appendChild(bb);
    bb.fills = [paint('fill/secondary')]; bb.cornerRadius = 9;
    bb.paddingLeft = 11; bb.paddingRight = 11; bb.paddingTop = 6; bb.paddingBottom = 6;
    bb.appendChild(T(o.bubble, { size: 16, lh: 20 }));
  }
  if (o.chevron) r.appendChild(icon(ICON.chev, 17, 'label/tertiary', 2.4));
  if (o.toggle !== undefined) {
    const tr = figma.createFrame(); tr.name = 'switch · ' + (o.toggle ? 'on' : 'off');
    tr.resize(51, 31); tr.cornerRadius = 999; tr.clipsContent = false;
    tr.fills = [paint(o.toggle ? 'accent/brand' : 'fill/primary')]; r.appendChild(tr);
    const th = figma.createEllipse(); th.resize(27, 27); th.y = 2; th.x = o.toggle ? 22 : 2;
    th.fills = [{ type: 'SOLID', color: { r: 1, g: 1, b: 1 } }];
    th.effects = [{ type: 'DROP_SHADOW', color: { r: 0, g: 0, b: 0, a: 0.2 }, offset: { x: 0, y: 2 }, radius: 4, spread: 0, visible: true, blendMode: 'NORMAL' }];
    tr.appendChild(th);
  }
  return r;
}

// ============== VEHICLE FORM (x = 884) ==================================
const vf = sheetScreen('screen-vehicle-form', 884, 'Nuevo vehículo');
const s1 = section(vf.content, 'Información obligatoria');
row(s1, 'Nombre', { value: 'Ej. Mi coche principal', placeholder: true, errorLabel: true });
sep(s1);
row(s1, 'Odómetro inicial', { value: '124.500 km' });
footer(s1, 'Nombre es obligatorio. Rango admitido del odómetro: 0 – 2.000.000 km', true);
const s2 = section(vf.content, 'Detalles opcionales');
row(s2, 'Marca', { value: 'Ej. Toyota', placeholder: true });
sep(s2);
row(s2, 'Modelo', { value: 'Ej. Corolla', placeholder: true });

// ============== FUEL FORM (x = 1768) ====================================
const ff = sheetScreen('screen-fuel-form', 1768, 'Nueva recarga');
const f1 = section(ff.content, 'Repostaje');
row(f1, 'Fecha', { iconMarkup: ICON.cal, bubble: '15/10/2026' });
sep(f1);
row(f1, 'Odómetro actual', { value: '142.850 km' });

const modeWrap = figma.createAutoLayout('VERTICAL', { name: 'section · método', itemSpacing: 7 });
ff.content.appendChild(modeWrap); modeWrap.fills = []; modeWrap.layoutSizingHorizontal = 'FILL';
const mhw = figma.createAutoLayout('HORIZONTAL'); modeWrap.appendChild(mhw);
mhw.fills = []; mhw.paddingLeft = 16; mhw.layoutSizingHorizontal = 'FILL';
mhw.appendChild(T('MÉTODO DE ENTRADA', { size: 12, style: ST.m, lh: 16, ls: 0.5, color: 'label/secondary' }));
const segc = figma.createAutoLayout('HORIZONTAL', { name: 'segmented-control · glass', itemSpacing: 2 });
modeWrap.appendChild(segc);
segc.cornerRadius = 999; segc.paddingLeft = 3; segc.paddingRight = 3; segc.paddingTop = 3; segc.paddingBottom = 3;
glass(segc, { lift: 3, flat: true });
segc.layoutSizingHorizontal = 'FILL'; segc.resize(segc.width, 38);
segc.primaryAxisSizingMode = 'FIXED'; segc.counterAxisSizingMode = 'FIXED';
function segment(label, selected) {
  const s = figma.createAutoLayout('HORIZONTAL', { name: 'segment' + (selected ? ' · selected' : '') });
  segc.appendChild(s); s.cornerRadius = 999;
  s.primaryAxisAlignItems = 'CENTER'; s.counterAxisAlignItems = 'CENTER';
  if (selected) {
    s.fills = [{ type: 'SOLID', color: { r: 1, g: 1, b: 1 } }];
    s.effects = [{ type: 'DROP_SHADOW', color: { r: 0, g: 0, b: 0, a: 0.12 }, offset: { x: 0, y: 2 }, radius: 6, spread: 0, visible: true, blendMode: 'NORMAL' }];
  } else s.fills = [];
  s.layoutSizingHorizontal = 'FILL'; s.layoutSizingVertical = 'FILL';
  s.appendChild(T(label, { size: 13, style: selected ? ST.sb : ST.m, lh: 17, color: selected ? 'label/primary' : 'label/secondary' }));
  return s;
}
segment('L + Precio/L', true); segment('L + Total', false); segment('Precio/L + Total', false);

const f2 = section(ff.content, null);
row(f2, 'Litros', { value: '45,20 L' });
sep(f2);
row(f2, 'Precio por litro', { value: '1,629 €/L' });
sep(f2);
row(f2, 'Total calculado', { value: '73,63 €', valueStrong: true, valueAccent: true });
footer(f2, 'El total se calcula automáticamente a partir de los litros y el precio por litro.');

const f3 = section(ff.content, 'Opciones');
row(f3, 'Tanque lleno', { sub: 'Si se desmarca, este repostaje se tratará como tanque parcial.', toggle: true, tall: true });
sep(f3);
row(f3, 'Repostajes omitidos', { sub: 'Indica si no has registrado alguna carga anterior.', toggle: false, tall: true });
sep(f3);
row(f3, 'Notas', { value: 'Opcional', placeholder: true, chevron: true });

await vf.S.screenshot();
await ff.S.screenshot();
return { createdNodeIds: [vf.S.id, ff.S.id], fuelContentHeight: ff.content.height };
})();

const r10 = await (async () => {

const page = await figma.getNodeByIdAsync('14:3');
await figma.setCurrentPageAsync(page);
const F = 'Inter';
const ST = { r: 'Regular', m: 'Medium', sb: 'Semi Bold', b: 'Bold' };
for (const s of Object.values(ST)) await figma.loadFontAsync({ family: F, style: s });
const cols = await figma.variables.getLocalVariableCollectionsAsync();
const lg = cols.find(c => c.name === 'Liquid Glass');
const vars = await figma.variables.getLocalVariablesAsync('COLOR');
const V = {}; for (const v of vars) if (v.variableCollectionId === lg.id) V[v.name] = v;
const paint = (n) => figma.variables.setBoundVariableForPaint({ type: 'SOLID', color: { r: 0, g: 0, b: 0 } }, 'color', V[n]);
const SPECULAR = { type: 'GRADIENT_LINEAR', gradientTransform: [[0.6, 0.6, -0.1], [-0.6, 0.6, 0.4]],
  gradientStops: [{ position: 0, color: { r: 1, g: 1, b: 1, a: 0.95 } }, { position: 0.45, color: { r: 1, g: 1, b: 1, a: 0.22 } }, { position: 1, color: { r: 1, g: 1, b: 1, a: 0.7 } }] };
function glass(node, o) {
  o = o || {}; const clear = o.variant === 'clear';
  node.fills = [paint(clear ? 'material/clear/fill' : (o.raised ? 'material/regular/fill-raised' : 'material/regular/fill'))];
  const lift = o.lift === undefined ? 8 : o.lift;
  node.effects = [
    { type: 'BACKGROUND_BLUR', radius: clear ? 12 : 34, visible: true },
    { type: 'DROP_SHADOW', color: { r: 0.04, g: 0.12, b: 0.10, a: o.flat ? 0.07 : 0.16 }, offset: { x: 0, y: lift }, radius: lift * 2.4, spread: -2, visible: true, blendMode: 'NORMAL' }
  ];
  node.strokes = [SPECULAR]; node.strokeWeight = 1; node.strokeAlign = 'INSIDE';
  return node;
}
const ICON = {
  chev: '<path d="m9 18 6-6-6-6"/>',
  signal: '<path d="M2 20h.01"/><path d="M7 20v-4"/><path d="M12 20v-8"/><path d="M17 20V8"/>',
  wifi: '<path d="M12 20h.01"/><path d="M5 12.859a10 10 0 0 1 14 0"/><path d="M8.5 16.429a5 5 0 0 1 7 0"/>',
  battery: '<rect width="16" height="10" x="2" y="7" rx="2"/><line x1="22" x2="22" y1="11" y2="13"/>'
};
function icon(markup, size, colorName, weight) {
  const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#000000" stroke-width="' + (weight === undefined ? 1.9 : weight) + '" stroke-linecap="round" stroke-linejoin="round">' + markup + '</svg>';
  const n = figma.createNodeFromSvg(svg); n.name = 'icon'; n.clipsContent = false;
  for (const c of n.findAll(x => 'strokes' in x)) { if (c.strokes.length) c.strokes = [paint(colorName)]; }
  if (size !== 24) n.rescale(size / 24);
  return n;
}
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

// ---- screen shell (sheet presentation, "Cerrar" only) -------------------
const S = figma.createFrame();
S.name = 'screen-settings'; S.resize(402, 874); S.x = 2210; S.y = 0; S.fills = []; S.clipsContent = true;
page.appendChild(S);
const bg = figma.createFrame(); bg.name = 'ambient-backdrop';
bg.resize(402, 874); bg.x = 0; bg.y = 0; bg.clipsContent = true;
bg.fills = [paint('background/grouped')]; S.appendChild(bg);
const blobs = [['background/ambient-a', 400, -120, -90, 1], ['background/ambient-b', 340, 180, 120, 1],
  ['background/ambient-c', 320, -100, 420, 1], ['background/ambient-a', 330, 170, 600, 1]];
for (const [c, s, bx, by, op] of blobs) {
  const e = figma.createEllipse(); e.resize(s, s); e.x = bx; e.y = by;
  e.fills = [paint(c)]; e.opacity = op; e.name = 'ambient';
  e.effects = [{ type: 'LAYER_BLUR', radius: 60, visible: true }]; bg.appendChild(e);
}
const sbTop = figma.createAutoLayout('HORIZONTAL', { name: 'status-bar' });
S.appendChild(sbTop); sbTop.layoutSizingHorizontal = 'FIXED'; sbTop.resize(402, 44);
sbTop.primaryAxisAlignItems = 'SPACE_BETWEEN'; sbTop.counterAxisAlignItems = 'CENTER';
sbTop.paddingLeft = 30; sbTop.paddingRight = 26; sbTop.paddingTop = 10; sbTop.fills = []; sbTop.x = 0; sbTop.y = 0;
sbTop.appendChild(T('9:41', { size: 15, style: ST.sb, lh: 20 }));
const sr = figma.createAutoLayout('HORIZONTAL', { itemSpacing: 6 }); sbTop.appendChild(sr);
sr.fills = []; sr.counterAxisAlignItems = 'CENTER';
sr.appendChild(icon(ICON.signal, 16, 'label/primary'));
sr.appendChild(icon(ICON.wifi, 16, 'label/primary'));
sr.appendChild(icon(ICON.battery, 16, 'label/primary'));

const sheet = figma.createFrame();
sheet.name = 'sheet'; sheet.resize(402, 830); sheet.x = 0; sheet.y = 44;
sheet.topLeftRadius = 34; sheet.topRightRadius = 34;
sheet.bottomLeftRadius = 0; sheet.bottomRightRadius = 0;
sheet.fills = [paint('background/grouped')]; sheet.clipsContent = true;
sheet.effects = [{ type: 'DROP_SHADOW', color: { r: 0, g: 0, b: 0, a: 0.18 }, offset: { x: 0, y: -6 }, radius: 26, spread: 0, visible: true, blendMode: 'NORMAL' }];
S.appendChild(sheet);

const content = figma.createAutoLayout('VERTICAL', { name: 'scroll-content', itemSpacing: 22 });
sheet.appendChild(content); content.fills = [];
content.layoutSizingHorizontal = 'FIXED'; content.resize(402, content.height);
content.paddingLeft = 16; content.paddingRight = 16; content.paddingTop = 74;
content.x = 0; content.y = 0;

const hdr = figma.createFrame(); hdr.name = 'toolbar · glass';
hdr.resize(402, 62); hdr.x = 0; hdr.y = 0; hdr.clipsContent = false;
hdr.topLeftRadius = 34; hdr.topRightRadius = 34;
sheet.appendChild(hdr); glass(hdr, { lift: 5, flat: true });
const grab = figma.createRectangle(); grab.name = 'grabber';
grab.resize(36, 5); grab.cornerRadius = 999; grab.x = 183; grab.y = 8;
grab.fills = [paint('label/tertiary')]; hdr.appendChild(grab);
const closeT = T('Cerrar', { size: 17, lh: 22, color: 'accent/brand' });
hdr.appendChild(closeT); closeT.x = 20; closeT.y = 28;
const ttl = T('Ajustes', { size: 17, style: ST.sb, lh: 22, align: 'CENTER' });
hdr.appendChild(ttl); ttl.textAutoResize = 'HEIGHT'; ttl.resize(200, ttl.height);
ttl.x = 101; ttl.y = 28;

const hp = figma.createRectangle(); hp.name = 'home-indicator';
hp.resize(140, 5); hp.cornerRadius = 999; hp.x = 131; hp.y = 853;
hp.fills = [paint('label/primary')]; hp.opacity = 0.4; S.appendChild(hp);

// ---- grouped list helpers ----------------------------------------------
function section(header) {
  const g = figma.createAutoLayout('VERTICAL', { name: 'section · ' + (header || 'plain'), itemSpacing: 7 });
  content.appendChild(g); g.fills = []; g.layoutSizingHorizontal = 'FILL';
  if (header) {
    const hw = figma.createAutoLayout('HORIZONTAL'); g.appendChild(hw);
    hw.fills = []; hw.paddingLeft = 16; hw.layoutSizingHorizontal = 'FILL';
    hw.appendChild(T(header.toUpperCase(), { size: 12, style: ST.m, lh: 16, ls: 0.5, color: 'label/secondary' }));
  }
  const card = figma.createAutoLayout('VERTICAL', { name: 'inset-group', itemSpacing: 0 });
  g.appendChild(card); card.fills = [paint('background/system')];
  card.cornerRadius = 20; card.clipsContent = true; card.layoutSizingHorizontal = 'FILL';
  return { group: g, card: card };
}
function sep(sec) {
  const holder = figma.createAutoLayout('HORIZONTAL'); sec.card.appendChild(holder);
  holder.fills = []; holder.paddingLeft = 16; holder.layoutSizingHorizontal = 'FILL';
  const s = figma.createRectangle(); s.name = 'separator'; s.fills = [paint('separator/non-opaque')];
  holder.appendChild(s); s.resize(100, 1); s.layoutSizingHorizontal = 'FILL';
}
function row(sec, label, o) {
  o = o || {};
  const r = figma.createAutoLayout('HORIZONTAL', { name: 'row', itemSpacing: 12 });
  sec.card.appendChild(r); r.fills = [];
  r.paddingLeft = 16; r.paddingRight = 16; r.paddingTop = o.sub ? 12 : 14; r.paddingBottom = o.sub ? 12 : 14;
  r.counterAxisAlignItems = 'CENTER'; r.layoutSizingHorizontal = 'FILL';
  const lc = figma.createAutoLayout('VERTICAL', { itemSpacing: 1 }); r.appendChild(lc);
  lc.fills = []; lc.layoutSizingHorizontal = 'FILL';
  lc.appendChild(T(label, { size: 17, lh: 22, color: o.danger ? 'accent/destructive' : 'label/primary' }));
  if (o.sub) { const s2 = T(o.sub, { size: 13, lh: 17, color: 'label/secondary', width: 230 }); lc.appendChild(s2); s2.layoutSizingHorizontal = 'FILL'; }
  if (o.value !== undefined) r.appendChild(T(o.value, { size: 17, lh: 22, color: 'label/secondary', align: 'RIGHT' }));
  if (o.status) {
    const b = figma.createAutoLayout('HORIZONTAL', { name: 'status', itemSpacing: 6 });
    r.appendChild(b); b.fills = []; b.counterAxisAlignItems = 'CENTER';
    const d = figma.createEllipse(); d.resize(7, 7); d.fills = [paint('accent/brand')]; b.appendChild(d);
    b.appendChild(T(o.status, { size: 15, style: ST.m, lh: 20, color: 'accent/brand' }));
  }
  if (o.toggle !== undefined) {
    const tr = figma.createFrame(); tr.name = 'switch · ' + (o.toggle ? 'on' : 'off');
    tr.resize(51, 31); tr.cornerRadius = 999; tr.clipsContent = false;
    tr.fills = [paint(o.toggle ? 'accent/brand' : 'fill/primary')]; r.appendChild(tr);
    const th = figma.createEllipse(); th.resize(27, 27); th.y = 2; th.x = o.toggle ? 22 : 2;
    th.fills = [{ type: 'SOLID', color: { r: 1, g: 1, b: 1 } }];
    th.effects = [{ type: 'DROP_SHADOW', color: { r: 0, g: 0, b: 0, a: 0.2 }, offset: { x: 0, y: 2 }, radius: 4, spread: 0, visible: true, blendMode: 'NORMAL' }];
    tr.appendChild(th);
  }
  if (o.chevron) r.appendChild(icon(ICON.chev, 17, 'label/tertiary', 2.4));
  return r;
}

const g1 = section('Unidades y moneda');
row(g1, 'Moneda', { value: 'EUR €', chevron: true });
sep(g1);
row(g1, 'Unidad de distancia', { value: 'Kilómetros', chevron: true });
sep(g1);
row(g1, 'Unidad de volumen', { value: 'Litros', chevron: true });

const g2 = section('Seguridad y copias');
row(g2, 'Copia de seguridad', { sub: 'Última sincronización local guardada', status: 'Activa' });

const g3 = section('Privacidad');
row(g3, 'Compartir analíticas', { sub: 'Comparte telemetría anónima para ayudarnos a mejorar carApp.', toggle: false });

const g4 = section('Cuenta');
row(g4, 'Cerrar sesión', { chevron: true });
sep(g4);
row(g4, 'Eliminar cuenta', { chevron: true, danger: true });

const foot = figma.createAutoLayout('VERTICAL', { name: 'footer', itemSpacing: 2 });
content.appendChild(foot); foot.fills = []; foot.counterAxisAlignItems = 'CENTER';
foot.paddingTop = 10; foot.layoutSizingHorizontal = 'FILL';
foot.appendChild(T('carApp v1.0.0', { size: 13, style: ST.m, lh: 18, color: 'label/secondary', align: 'CENTER' }));
foot.appendChild(T('Local-First & Safe Tracker', { size: 13, lh: 18, color: 'label/tertiary', align: 'CENTER' }));

await S.screenshot();
return { createdNodeIds: [S.id], contentHeight: content.height };
})();

return { createdNodeIds: [...r09.createdNodeIds, ...r10.createdNodeIds], fuelContentHeight: r09.fuelContentHeight, settingsContentHeight: r10.contentHeight };
