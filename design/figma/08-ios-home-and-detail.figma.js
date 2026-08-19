// carApp — Step 08: iOS Liquid Glass · screen-home + screen-detail
// STATUS: executed successfully. Created frames 26:2 (x=442) and 26:80 (x=1326).
//
// The two rules that shape this file:
//
//   1. Glass is the CHROME layer only. Nav bars, toolbars and floating controls get the glass
//      treatment; vehicle cards, the KPI card and entry rows stay opaque. Apple's guidance
//      forbids glass stacked on glass, and using it for the content layer destroys legibility.
//
//   2. Content must pass UNDER the glass. Both scroll-content containers are positioned at
//      y=84 while the nav bar occupies 0–104, so the top of the content genuinely sits behind
//      the bar and BACKGROUND_BLUR has something to refract. That overlap is deliberate — do
//      not "fix" it by pushing content below the bar, or the material stops reading as glass.
//
// The floating action is centred (x=107, right edge 296 of 402), fixing the overflow that
// clipped "Añadir vehícu…" / "Nueva recar…" on the original iOS board.

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
    { type: 'DROP_SHADOW', color: { r: 0.04, g: 0.12, b: 0.10, a: o.flat ? 0.08 : 0.16 }, offset: { x: 0, y: lift }, radius: lift * 2.4, spread: -2, visible: true, blendMode: 'NORMAL' }
  ];
  node.strokes = [SPECULAR]; node.strokeWeight = 1; node.strokeAlign = 'INSIDE';
  return node;
}
const ICON = {
  car: '<path d="m21 8-2 2-1.5-3.7A2 2 0 0 0 15.646 5H8.4a2 2 0 0 0-1.903 1.257L5 10 3 8"/><path d="M7 14h.01"/><path d="M17 14h.01"/><rect width="18" height="8" x="3" y="10" rx="2"/><path d="M5 18v2"/><path d="M19 18v2"/>',
  settings: '<path d="M20 7h-9"/><path d="M14 17H5"/><circle cx="17" cy="17" r="3"/><circle cx="7" cy="7" r="3"/>',
  chev: '<path d="m9 18 6-6-6-6"/>', back: '<path d="m15 18-6-6 6-6"/>',
  plus: '<path d="M5 12h14"/><path d="M12 5v14"/>',
  edit: '<path d="M12 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.4 2.6a2 2 0 0 1 3 3L12 15l-4 1 1-4Z"/>',
  alert: '<path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3"/><path d="M12 9v4"/><path d="M12 17h.01"/>',
  loader: '<path d="M21 12a9 9 0 1 1-6.219-8.56"/>',
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
function shell(name, x) {
  const S = figma.createFrame();
  S.name = name; S.resize(402, 874); S.x = x; S.y = 0; S.fills = []; S.clipsContent = true;
  page.appendChild(S);
  const b = figma.createFrame(); b.name = 'ambient-backdrop';
  b.resize(402, 874); b.x = 0; b.y = 0; b.clipsContent = true;
  b.fills = [paint('background/grouped')]; S.appendChild(b);
  const blobs = [['background/ambient-a', 400, -120, -90, 1], ['background/ambient-b', 340, 180, 150, 1],
    ['background/ambient-c', 320, -100, 430, 1], ['background/ambient-a', 330, 170, 600, 1], ['background/ambient-c', 250, 20, 780, 0.9]];
  for (const [c, s, bx, by, op] of blobs) {
    const e = figma.createEllipse(); e.resize(s, s); e.x = bx; e.y = by;
    e.fills = [paint(c)]; e.opacity = op; e.name = 'ambient';
    e.effects = [{ type: 'LAYER_BLUR', radius: 60, visible: true }]; b.appendChild(e);
  }
  const hp = figma.createRectangle(); hp.name = 'home-indicator';
  hp.resize(140, 5); hp.cornerRadius = 999; hp.x = 131; hp.y = 853;
  hp.fills = [paint('label/primary')]; hp.opacity = 0.4; S.appendChild(hp);
  return S;
}
function glassNav(S, opts) {
  const nav = figma.createFrame(); nav.name = 'nav-bar · glass';
  nav.resize(402, opts.h || 104); nav.x = 0; nav.y = 0; nav.clipsContent = false;
  S.appendChild(nav); glass(nav, { lift: 6, flat: true }); nav.strokeAlign = 'INSIDE';
  const sb = figma.createAutoLayout('HORIZONTAL', { name: 'status-bar' });
  nav.appendChild(sb); sb.layoutSizingHorizontal = 'FIXED'; sb.resize(402, 54);
  sb.primaryAxisAlignItems = 'SPACE_BETWEEN'; sb.counterAxisAlignItems = 'CENTER';
  sb.paddingLeft = 30; sb.paddingRight = 26; sb.paddingTop = 12; sb.fills = []; sb.x = 0; sb.y = 0;
  sb.appendChild(T('9:41', { size: 15, style: ST.sb, lh: 20 }));
  const sr = figma.createAutoLayout('HORIZONTAL', { itemSpacing: 6 }); sb.appendChild(sr);
  sr.fills = []; sr.counterAxisAlignItems = 'CENTER';
  sr.appendChild(icon(ICON.signal, 16, 'label/primary'));
  sr.appendChild(icon(ICON.wifi, 16, 'label/primary'));
  sr.appendChild(icon(ICON.battery, 16, 'label/primary'));
  return nav;
}
function glassCircle(parent, markup, size) {
  const c = figma.createAutoLayout('HORIZONTAL', { name: 'icon-button · glass' });
  parent.appendChild(c);
  c.primaryAxisAlignItems = 'CENTER'; c.counterAxisAlignItems = 'CENTER';
  c.resize(size, size); c.primaryAxisSizingMode = 'FIXED'; c.counterAxisSizingMode = 'FIXED';
  c.cornerRadius = 999; glass(c, { raised: true, lift: 4 });
  c.appendChild(icon(markup, size * 0.46, 'accent/brand'));
  return c;
}
function prominent(S, label, markup) {
  const btn = figma.createAutoLayout('HORIZONTAL', { name: 'button · glass prominent', itemSpacing: 8 });
  S.appendChild(btn);
  btn.fills = [paint('accent/brand')]; btn.cornerRadius = 999;
  btn.strokes = [SPECULAR]; btn.strokeWeight = 1; btn.strokeAlign = 'INSIDE';
  btn.effects = [{ type: 'DROP_SHADOW', color: { r: 0.04, g: 0.25, b: 0.20, a: 0.30 }, offset: { x: 0, y: 10 }, radius: 24, spread: -4, visible: true, blendMode: 'NORMAL' }];
  btn.paddingLeft = 20; btn.paddingRight = 24;
  btn.primaryAxisAlignItems = 'CENTER'; btn.counterAxisAlignItems = 'CENTER';
  btn.resize(btn.width, 52); btn.counterAxisSizingMode = 'FIXED';
  btn.appendChild(icon(markup, 20, 'accent/on-brand', 2.2));
  btn.appendChild(T(label, { size: 17, style: ST.sb, lh: 22, color: 'accent/on-brand' }));
  btn.x = Math.round((402 - btn.width) / 2);
  btn.y = 874 - 46 - 52 - 16;
  return btn;
}

// ================= HOME =================================================
const H = shell('screen-home', 442);
const hcontent = figma.createAutoLayout('VERTICAL', { name: 'scroll-content', itemSpacing: 14 });
H.appendChild(hcontent); hcontent.fills = [];
hcontent.layoutSizingHorizontal = 'FIXED'; hcontent.resize(402, hcontent.height);
hcontent.paddingLeft = 16; hcontent.paddingRight = 16;
hcontent.x = 0; hcontent.y = 84; // deliberately runs under the glass nav bar

const syncRow = figma.createAutoLayout('HORIZONTAL', { name: 'sync-status', itemSpacing: 7 });
hcontent.appendChild(syncRow); syncRow.fills = []; syncRow.counterAxisAlignItems = 'CENTER';
syncRow.paddingLeft = 6; syncRow.paddingTop = 34;
const d = figma.createEllipse(); d.resize(7, 7); d.fills = [paint('accent/brand')]; syncRow.appendChild(d);
syncRow.appendChild(T('Sincronizado localmente', { size: 13, style: ST.m, lh: 18, color: 'label/secondary' }));

function vehicleCard(parent, name, brand, odo) {
  const c = figma.createAutoLayout('VERTICAL', { name: 'vehicle-card · content', itemSpacing: 14 });
  parent.appendChild(c);
  c.fills = [paint('background/system')]; c.cornerRadius = 22;
  c.paddingLeft = 16; c.paddingRight = 16; c.paddingTop = 16; c.paddingBottom = 16;
  c.effects = [{ type: 'DROP_SHADOW', color: { r: 0.05, g: 0.12, b: 0.1, a: 0.08 }, offset: { x: 0, y: 4 }, radius: 14, spread: -2, visible: true, blendMode: 'NORMAL' }];
  c.layoutSizingHorizontal = 'FILL';
  const head = figma.createAutoLayout('HORIZONTAL', { itemSpacing: 14 }); c.appendChild(head);
  head.fills = []; head.counterAxisAlignItems = 'CENTER'; head.layoutSizingHorizontal = 'FILL';
  const av = figma.createAutoLayout('HORIZONTAL'); head.appendChild(av);
  av.fills = [paint('material/tinted/fill')]; av.cornerRadius = 999;
  av.primaryAxisAlignItems = 'CENTER'; av.counterAxisAlignItems = 'CENTER';
  av.resize(44, 44); av.primaryAxisSizingMode = 'FIXED'; av.counterAxisSizingMode = 'FIXED';
  av.appendChild(icon(ICON.car, 22, 'accent/brand'));
  const tx = figma.createAutoLayout('VERTICAL', { itemSpacing: 1 }); head.appendChild(tx);
  tx.fills = [];
  tx.appendChild(T(name, { size: 17, style: ST.sb, lh: 22 }));
  tx.appendChild(T(brand, { size: 15, lh: 20, color: 'label/secondary' }));
  tx.layoutSizingHorizontal = 'FILL';
  head.appendChild(icon(ICON.chev, 18, 'label/tertiary', 2.4));
  const sepr = figma.createRectangle(); sepr.name = 'separator';
  sepr.fills = [paint('separator/non-opaque')]; c.appendChild(sepr);
  sepr.resize(100, 1); sepr.layoutSizingHorizontal = 'FILL';
  const st = figma.createAutoLayout('HORIZONTAL'); c.appendChild(st);
  st.fills = []; st.primaryAxisAlignItems = 'SPACE_BETWEEN'; st.counterAxisAlignItems = 'CENTER';
  st.layoutSizingHorizontal = 'FILL';
  st.appendChild(T('Último odómetro', { size: 13, lh: 18, color: 'label/secondary' }));
  st.appendChild(T(odo, { size: 17, style: ST.sb, lh: 22, color: 'accent/brand' }));
  return c;
}
vehicleCard(hcontent, 'Toyota Corolla', 'Toyota', '142.500 km');
vehicleCard(hcontent, 'Volkswagen Golf', 'Volkswagen', '42.105 km');

const hnav = glassNav(H, { h: 104 });
const htitle = T('Mis vehículos', { size: 17, style: ST.sb, lh: 22, align: 'CENTER' });
hnav.appendChild(htitle); htitle.textAutoResize = 'HEIGHT'; htitle.resize(200, htitle.height);
htitle.x = 101; htitle.y = 68;
const hset = glassCircle(hnav, ICON.settings, 38); hset.x = 348; hset.y = 60;
prominent(H, 'Añadir vehículo', ICON.plus);

// ================= DETAIL ===============================================
const D = shell('screen-detail', 1326);
const dcontent = figma.createAutoLayout('VERTICAL', { name: 'scroll-content', itemSpacing: 12 });
D.appendChild(dcontent); dcontent.fills = [];
dcontent.layoutSizingHorizontal = 'FIXED'; dcontent.resize(402, dcontent.height);
dcontent.paddingLeft = 16; dcontent.paddingRight = 16;
dcontent.x = 0; dcontent.y = 84;

const refresh = figma.createAutoLayout('HORIZONTAL', { name: 'pull-to-refresh' });
dcontent.appendChild(refresh); refresh.fills = []; refresh.primaryAxisAlignItems = 'CENTER';
refresh.paddingTop = 40; refresh.layoutSizingHorizontal = 'FILL';
refresh.appendChild(icon(ICON.loader, 20, 'label/tertiary', 2.2));

const kpi = figma.createAutoLayout('VERTICAL', { name: 'kpi-card', itemSpacing: 4 });
dcontent.appendChild(kpi);
kpi.fills = [paint('accent/brand')]; kpi.cornerRadius = 26;
kpi.paddingLeft = 22; kpi.paddingRight = 22; kpi.paddingTop = 20; kpi.paddingBottom = 20;
kpi.effects = [{ type: 'DROP_SHADOW', color: { r: 0.04, g: 0.25, b: 0.2, a: 0.24 }, offset: { x: 0, y: 10 }, radius: 24, spread: -6, visible: true, blendMode: 'NORMAL' }];
kpi.layoutSizingHorizontal = 'FILL';
kpi.appendChild(T('Consumo medio histórico', { size: 13, style: ST.m, lh: 18, color: 'accent/on-brand' }));
const vr = figma.createAutoLayout('HORIZONTAL', { itemSpacing: 7 }); kpi.appendChild(vr);
vr.fills = []; vr.counterAxisAlignItems = 'BASELINE';
vr.appendChild(T('7,24', { size: 44, style: ST.b, lh: 52, ls: -1, color: 'accent/on-brand' }));
vr.appendChild(T('L/100 km', { size: 17, style: ST.m, lh: 22, color: 'accent/on-brand' }));
kpi.appendChild(T('Media de 24 repostajes registrados', { size: 13, lh: 18, color: 'accent/on-brand' }));

const entries = figma.createAutoLayout('VERTICAL', { name: 'entries · inset grouped', itemSpacing: 0 });
dcontent.appendChild(entries);
entries.fills = [paint('background/system')]; entries.cornerRadius = 22; entries.clipsContent = true;
entries.layoutSizingHorizontal = 'FILL';
function entryRow(date, cons, meta, cost, partial, last) {
  const r = figma.createAutoLayout('VERTICAL', { name: 'entry-row', itemSpacing: 5 });
  entries.appendChild(r); r.fills = [];
  r.paddingLeft = 16; r.paddingRight = 16; r.paddingTop = 14; r.paddingBottom = 14;
  r.layoutSizingHorizontal = 'FILL';
  const t1 = figma.createAutoLayout('HORIZONTAL'); r.appendChild(t1);
  t1.fills = []; t1.primaryAxisAlignItems = 'SPACE_BETWEEN'; t1.layoutSizingHorizontal = 'FILL';
  t1.appendChild(T(date, { size: 16, style: ST.sb, lh: 21 }));
  t1.appendChild(T(cons, { size: 16, style: ST.sb, lh: 21, color: 'accent/brand' }));
  const t2 = figma.createAutoLayout('HORIZONTAL'); r.appendChild(t2);
  t2.fills = []; t2.primaryAxisAlignItems = 'SPACE_BETWEEN'; t2.layoutSizingHorizontal = 'FILL';
  t2.appendChild(T(meta, { size: 13, lh: 18, color: 'label/secondary' }));
  t2.appendChild(T(cost, { size: 13, style: ST.m, lh: 18, color: 'label/secondary' }));
  if (partial) {
    const ch = figma.createAutoLayout('HORIZONTAL', { name: 'badge · tanque parcial', itemSpacing: 5 });
    r.appendChild(ch);
    ch.fills = [paint('background/ambient-c')]; ch.cornerRadius = 999;
    ch.paddingLeft = 9; ch.paddingRight = 11; ch.counterAxisAlignItems = 'CENTER';
    ch.resize(ch.width, 26); ch.counterAxisSizingMode = 'FIXED';
    ch.appendChild(icon(ICON.alert, 14, 'accent/warning', 2.2));
    ch.appendChild(T('Tanque parcial', { size: 12, style: ST.m, lh: 16, color: 'accent/warning' }));
  }
  if (!last) {
    const sepr = figma.createRectangle(); sepr.name = 'separator';
    sepr.fills = [paint('separator/non-opaque')]; entries.appendChild(sepr);
    sepr.resize(100, 1); sepr.layoutSizingHorizontal = 'FILL';
  }
  return r;
}
entryRow('15 oct 2026', '7,10 L/100 km', '142.850 km · 45,2 L', '73,63 €', true, false);
entryRow('01 oct 2026', '7,32 L/100 km', '142.100 km · 52,1 L', '84,92 €', false, false);
entryRow('18 sep 2026', '7,26 L/100 km', '141.400 km · 50,8 L', '82,80 €', false, true);

const dnav = glassNav(D, { h: 104 });
const dback = figma.createAutoLayout('HORIZONTAL', { name: 'back', itemSpacing: 2 });
dnav.appendChild(dback); dback.fills = []; dback.counterAxisAlignItems = 'CENTER';
dback.x = 14; dback.y = 66;
dback.appendChild(icon(ICON.back, 20, 'accent/brand', 2.4));
dback.appendChild(T('Atrás', { size: 17, lh: 22, color: 'accent/brand' }));
const dtitle = T('Toyota Corolla', { size: 17, style: ST.sb, lh: 22, align: 'CENTER' });
dnav.appendChild(dtitle); dtitle.textAutoResize = 'HEIGHT'; dtitle.resize(180, dtitle.height);
dtitle.x = 111; dtitle.y = 68;
const dedit = glassCircle(dnav, ICON.edit, 38); dedit.x = 348; dedit.y = 60;
const dfab = prominent(D, 'Nueva recarga', ICON.plus);

await H.screenshot();
await D.screenshot();
return { createdNodeIds: [H.id, D.id], detailFabX: dfab.x, detailFabRight: dfab.x + dfab.width, contentBottom: dcontent.y + dcontent.height };
