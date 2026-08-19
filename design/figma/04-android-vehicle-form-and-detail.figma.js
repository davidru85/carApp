// carApp — Step 04: Android M3 Expressive · screen-vehicle-form + screen-detail
// STATUS: executed successfully. Created frames 20:2 (x=904) and 20:61 (x=1356).
//
// Demonstrates: M3 filled text fields rendered in three interaction states (error / focused /
// enabled) with correct active-indicator weights and colours; M3 Expressive display-scale KPI
// (57px) in a primary-container hero; the M3 Expressive morphing loading indicator; tertiary-
// container warning chip.
//
// KNOWN DEFECT (fixed in script 05): filledField() indents the whole field wrapper by 16 when
// supporting text is present, so fields with supporting text sit narrower than those without.
// Script 05 re-parents the supporting text into its own padded row and resets paddingLeft.

const page = await figma.getNodeByIdAsync('14:2');
await figma.setCurrentPageAsync(page);
const F = 'Roboto Flex';
for (const s of ['Regular','Medium','SemiBold','Bold']) await figma.loadFontAsync({ family: F, style: s });
const cols = await figma.variables.getLocalVariableCollectionsAsync();
const m3 = cols.find(c => c.name === 'M3 Expressive');
const vars = await figma.variables.getLocalVariablesAsync('COLOR');
const V = {}; for (const v of vars) if (v.variableCollectionId === m3.id) V[v.name] = v;
const paint = (n) => figma.variables.setBoundVariableForPaint({ type: 'SOLID', color: { r: 0, g: 0, b: 0 } }, 'color', V[n]);
const shadow = (a, y, r, sp) => ({ type: 'DROP_SHADOW', color: { r: 0, g: 0, b: 0, a: a }, offset: { x: 0, y: y }, radius: r, spread: sp, visible: true, blendMode: 'NORMAL' });
const ELEV1 = [shadow(0.30, 1, 2, 0), shadow(0.15, 1, 3, 1)];
const ELEV3 = [shadow(0.30, 1, 3, 0), shadow(0.15, 4, 8, 3)];
const ICON = {
  chevron: '<path d="m9 18 6-6-6-6"/>', plus: '<path d="M5 12h14"/><path d="M12 5v14"/>',
  back: '<path d="m12 19-7-7 7-7"/><path d="M19 12H5"/>',
  edit: '<path d="M12 3H5a2 2 0 0 0-2 2v14a2 2 0 0 0 2 2h14a2 2 0 0 0 2-2v-7"/><path d="M18.4 2.6a2 2 0 0 1 3 3L12 15l-4 1 1-4Z"/>',
  alert: '<path d="m21.73 18-8-14a2 2 0 0 0-3.48 0l-8 14A2 2 0 0 0 4 21h16a2 2 0 0 0 1.73-3"/><path d="M12 9v4"/><path d="M12 17h.01"/>',
  signal: '<path d="M2 20h.01"/><path d="M7 20v-4"/><path d="M12 20v-8"/><path d="M17 20V8"/>',
  wifi: '<path d="M12 20h.01"/><path d="M5 12.859a10 10 0 0 1 14 0"/><path d="M8.5 16.429a5 5 0 0 1 7 0"/>',
  battery: '<rect width="16" height="10" x="2" y="7" rx="2"/><line x1="22" x2="22" y1="11" y2="13"/>'
};
function icon(markup, size, colorName, weight) {
  const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#000000" stroke-width="' + (weight || 2) + '" stroke-linecap="round" stroke-linejoin="round">' + markup + '</svg>';
  const n = figma.createNodeFromSvg(svg); n.name = 'icon'; n.clipsContent = false;
  for (const c of n.findAll(x => 'strokes' in x)) { if (c.strokes.length) c.strokes = [paint(colorName)]; }
  if (size !== 24) n.rescale(size / 24);
  return n;
}
function T(chars, o) {
  o = o || {}; const t = figma.createText();
  t.fontName = { family: F, style: o.style || 'Regular' }; t.characters = chars;
  t.fontSize = o.size || 16; t.lineHeight = { unit: 'PIXELS', value: o.lh || Math.round((o.size || 16) * 1.45) };
  t.letterSpacing = { unit: 'PIXELS', value: o.ls || 0 };
  t.fills = [paint(o.color || 'color/on-surface')]; t.name = chars.slice(0, 24);
  if (o.width) { t.textAutoResize = 'HEIGHT'; t.resize(o.width, t.height); }
  return t;
}
function screenShell(name, x) {
  const S = figma.createFrame();
  S.name = name; S.resize(412, 917); S.x = x; S.y = 0;
  S.fills = [paint('color/surface')]; S.clipsContent = true; page.appendChild(S);
  const sb = figma.createAutoLayout('HORIZONTAL', { name: 'status-bar' });
  S.appendChild(sb); sb.layoutSizingHorizontal = 'FIXED'; sb.resize(412, 32);
  sb.primaryAxisAlignItems = 'SPACE_BETWEEN'; sb.counterAxisAlignItems = 'CENTER';
  sb.paddingLeft = 24; sb.paddingRight = 24; sb.fills = []; sb.x = 0; sb.y = 0;
  sb.appendChild(T('9:41', { size: 14, style: 'Medium', lh: 20 }));
  const r = figma.createAutoLayout('HORIZONTAL', { name: 'sys-icons', itemSpacing: 6 });
  sb.appendChild(r); r.fills = []; r.counterAxisAlignItems = 'CENTER';
  r.appendChild(icon(ICON.signal, 16, 'color/on-surface'));
  r.appendChild(icon(ICON.wifi, 16, 'color/on-surface'));
  r.appendChild(icon(ICON.battery, 16, 'color/on-surface'));
  const pill = figma.createRectangle();
  pill.name = 'gesture-handle'; pill.resize(108, 4); pill.cornerRadius = 999;
  pill.x = 152; pill.y = 901; pill.fills = [paint('color/on-surface-variant')]; pill.opacity = 0.6;
  S.appendChild(pill);
  return S;
}
function iconButton(parent, markup, colorName, fillName) {
  const b = figma.createAutoLayout('HORIZONTAL', { name: 'icon-button' });
  parent.appendChild(b);
  b.fills = fillName ? [paint(fillName)] : []; b.cornerRadius = 999;
  b.primaryAxisAlignItems = 'CENTER'; b.counterAxisAlignItems = 'CENTER';
  b.resize(48, 48); b.primaryAxisSizingMode = 'FIXED'; b.counterAxisSizingMode = 'FIXED';
  b.appendChild(icon(markup, 24, colorName));
  return b;
}

// ============ SCREEN: vehicle form =======================================
const A = screenShell('screen-vehicle-form', 904);

const bar = figma.createFrame();
bar.name = 'top-app-bar · small'; bar.resize(412, 64); bar.x = 0; bar.y = 32;
bar.fills = []; A.appendChild(bar);
const back = iconButton(bar, ICON.back, 'color/on-surface'); back.x = 8; back.y = 8;
const ttl = T('Nuevo vehículo', { size: 22, style: 'SemiBold', lh: 28 });
bar.appendChild(ttl); ttl.x = 64; ttl.y = 18;
const save = figma.createAutoLayout('HORIZONTAL', { name: 'button · filled-tonal' });
bar.appendChild(save);
save.fills = [paint('color/secondary-container')]; save.cornerRadius = 999;
save.paddingLeft = 20; save.paddingRight = 20;
save.primaryAxisAlignItems = 'CENTER'; save.counterAxisAlignItems = 'CENTER';
save.resize(save.width, 40); save.counterAxisSizingMode = 'FIXED';
save.appendChild(T('Guardar', { size: 14, style: 'SemiBold', lh: 20, ls: 0.1, color: 'color/on-secondary-container' }));
save.x = 412 - 16 - save.width; save.y = 12;

const form = figma.createAutoLayout('VERTICAL', { name: 'form', itemSpacing: 20 });
A.appendChild(form);
form.fills = []; form.layoutSizingHorizontal = 'FIXED'; form.resize(412, form.height);
form.paddingLeft = 16; form.paddingRight = 16; form.x = 0; form.y = 112;

function sectionLabel(parent, txt) {
  const l = T(txt, { size: 14, style: 'SemiBold', lh: 20, ls: 0.1, color: 'color/primary' });
  parent.appendChild(l); return l;
}
// M3 filled text field with explicit interaction states
function filledField(parent, o) {
  const wrap = figma.createAutoLayout('VERTICAL', { name: 'text-field · filled · ' + o.state, itemSpacing: 6 });
  parent.appendChild(wrap); wrap.fills = []; wrap.layoutSizingHorizontal = 'FILL';

  const box = figma.createAutoLayout('VERTICAL', { name: 'container', itemSpacing: 2 });
  wrap.appendChild(box);
  box.fills = [paint('color/surface-container-highest')];
  box.topLeftRadius = 8; box.topRightRadius = 8; box.bottomLeftRadius = 0; box.bottomRightRadius = 0;
  box.paddingLeft = 16; box.paddingRight = 16; box.paddingTop = 8; box.paddingBottom = 10;
  box.layoutSizingHorizontal = 'FILL'; box.resize(box.width, 60); box.primaryAxisSizingMode = 'FIXED';
  box.primaryAxisAlignItems = 'CENTER';

  const labelColor = o.state === 'error' ? 'color/error' : (o.state === 'focused' ? 'color/primary' : 'color/on-surface-variant');
  box.appendChild(T(o.label, { size: 12, style: 'Medium', lh: 16, ls: 0.4, color: labelColor }));

  const row = figma.createAutoLayout('HORIZONTAL', { name: 'value-row' });
  box.appendChild(row); row.fills = []; row.primaryAxisAlignItems = 'SPACE_BETWEEN';
  row.counterAxisAlignItems = 'CENTER'; row.layoutSizingHorizontal = 'FILL';
  row.appendChild(T(o.value || o.placeholder, { size: 16, lh: 24, color: o.value ? 'color/on-surface' : 'color/on-surface-variant' }));
  if (o.suffix) row.appendChild(T(o.suffix, { size: 16, lh: 24, color: 'color/on-surface-variant' }));

  const line = figma.createRectangle();
  line.name = 'active-indicator';
  const lw = o.state === 'enabled' ? 1 : 2;
  line.resize(380 - 32, lw);
  line.fills = [paint(o.state === 'error' ? 'color/error' : (o.state === 'focused' ? 'color/primary' : 'color/on-surface-variant'))];
  box.appendChild(line); line.layoutPositioning = 'ABSOLUTE';
  line.resize(box.width, lw); line.x = 0; line.y = 60 - lw;

  if (o.support) {
    const s = T(o.support, { size: 12, lh: 16, ls: 0.4, color: o.state === 'error' ? 'color/error' : 'color/on-surface-variant' });
    wrap.appendChild(s); s.x = 0;
    const pad = figma.createFrame();
    pad.name = 'support-indent'; pad.resize(16, 1); pad.fills = [];
    s.parent.insertChild(s.parent.children.indexOf(s), pad); pad.remove();
    wrap.paddingLeft = 16; // <-- the defect fixed in script 05
  }
  return wrap;
}
const g1 = figma.createAutoLayout('VERTICAL', { name: 'section · obligatoria', itemSpacing: 12 });
form.appendChild(g1); g1.fills = []; g1.layoutSizingHorizontal = 'FILL';
sectionLabel(g1, 'Información obligatoria');
filledField(g1, { label: 'Nombre', placeholder: 'Ej. Mi coche principal', state: 'error', support: 'Este campo es obligatorio' });
filledField(g1, { label: 'Odómetro inicial', value: '124.500', suffix: 'km', state: 'focused', support: 'Rango admitido: 0 – 2.000.000 km' });

const g2 = figma.createAutoLayout('VERTICAL', { name: 'section · opcional', itemSpacing: 12 });
form.appendChild(g2); g2.fills = []; g2.layoutSizingHorizontal = 'FILL';
sectionLabel(g2, 'Detalles opcionales');
filledField(g2, { label: 'Marca', placeholder: 'Ej. Toyota', state: 'enabled' });
filledField(g2, { label: 'Modelo', placeholder: 'Ej. Corolla', state: 'enabled' });

// ============ SCREEN: detail =============================================
const B = screenShell('screen-detail', 1356);

const bar2 = figma.createFrame();
bar2.name = 'top-app-bar · large'; bar2.resize(412, 132); bar2.x = 0; bar2.y = 32;
bar2.fills = []; B.appendChild(bar2);
const back2 = iconButton(bar2, ICON.back, 'color/on-surface'); back2.x = 8; back2.y = 4;
const editB = iconButton(bar2, ICON.edit, 'color/on-secondary-container', 'color/secondary-container');
editB.x = 356; editB.y = 4;
const vt = T('Toyota Corolla', { size: 32, style: 'Bold', lh: 40, ls: -0.4 });
bar2.appendChild(vt); vt.x = 24; vt.y = 68;

// M3 Expressive loading indicator — morphing polygon
const loadWrap = figma.createFrame();
loadWrap.name = 'loading-indicator · expressive'; loadWrap.resize(412, 48);
loadWrap.fills = []; loadWrap.x = 0; loadWrap.y = 164; B.appendChild(loadWrap);
const poly = figma.createStar();
poly.name = 'morph-shape'; poly.pointCount = 7; poly.innerRadius = 0.8; poly.cornerRadius = 4;
poly.resize(32, 32); poly.x = 190; poly.y = 8; poly.fills = [paint('color/primary')];
loadWrap.appendChild(poly);

// expressive KPI hero
const kpi = figma.createAutoLayout('VERTICAL', { name: 'kpi-card · expressive', itemSpacing: 4 });
B.appendChild(kpi);
kpi.fills = [paint('color/primary-container')]; kpi.cornerRadius = 32;
kpi.paddingLeft = 24; kpi.paddingRight = 24; kpi.paddingTop = 22; kpi.paddingBottom = 22;
kpi.layoutSizingHorizontal = 'FIXED'; kpi.resize(380, kpi.height);
kpi.x = 16; kpi.y = 220; kpi.clipsContent = true;
kpi.appendChild(T('CONSUMO MEDIO HISTÓRICO', { size: 12, style: 'SemiBold', lh: 16, ls: 1, color: 'color/on-primary-container' }));
const vrow = figma.createAutoLayout('HORIZONTAL', { name: 'value-row', itemSpacing: 8 });
kpi.appendChild(vrow); vrow.fills = []; vrow.counterAxisAlignItems = 'BASELINE';
vrow.appendChild(T('7,24', { size: 57, style: 'Bold', lh: 64, ls: -1, color: 'color/on-primary-container' }));
vrow.appendChild(T('L/100 km', { size: 18, style: 'Medium', lh: 24, color: 'color/on-primary-container' }));
kpi.appendChild(T('Media de 24 repostajes registrados', { size: 14, lh: 20, color: 'color/on-primary-container' }));
const deco = figma.createStar();
deco.pointCount = 12; deco.innerRadius = 0.88; deco.cornerRadius = 8; deco.resize(180, 180);
deco.fills = [paint('color/primary')]; deco.opacity = 0.14; deco.name = 'expressive-deco';
kpi.appendChild(deco); deco.layoutPositioning = 'ABSOLUTE'; deco.x = 268; deco.y = -40;

// entries
const entries = figma.createAutoLayout('VERTICAL', { name: 'entry-list', itemSpacing: 8 });
B.appendChild(entries);
entries.fills = []; entries.layoutSizingHorizontal = 'FIXED'; entries.resize(412, entries.height);
entries.paddingLeft = 16; entries.paddingRight = 16; entries.x = 0; entries.y = 400;

function entryRow(date, cons, meta, cost, partial) {
  const r = figma.createAutoLayout('VERTICAL', { name: 'entry-row', itemSpacing: 6 });
  entries.appendChild(r);
  r.fills = [paint('color/surface-container-low')]; r.cornerRadius = 20;
  r.paddingLeft = 18; r.paddingRight = 18; r.paddingTop = 14; r.paddingBottom = 14;
  r.layoutSizingHorizontal = 'FILL';
  const t1 = figma.createAutoLayout('HORIZONTAL'); r.appendChild(t1);
  t1.fills = []; t1.primaryAxisAlignItems = 'SPACE_BETWEEN'; t1.layoutSizingHorizontal = 'FILL';
  t1.appendChild(T(date, { size: 16, style: 'SemiBold', lh: 22 }));
  t1.appendChild(T(cons, { size: 16, style: 'SemiBold', lh: 22, color: 'color/primary' }));
  const t2 = figma.createAutoLayout('HORIZONTAL'); r.appendChild(t2);
  t2.fills = []; t2.primaryAxisAlignItems = 'SPACE_BETWEEN'; t2.layoutSizingHorizontal = 'FILL';
  t2.appendChild(T(meta, { size: 13, lh: 18, color: 'color/on-surface-variant' }));
  t2.appendChild(T(cost, { size: 14, style: 'Medium', lh: 18, color: 'color/on-surface-variant' }));
  if (partial) {
    const chip = figma.createAutoLayout('HORIZONTAL', { name: 'chip · tanque parcial', itemSpacing: 6 });
    r.appendChild(chip);
    chip.fills = [paint('color/tertiary-container')]; chip.cornerRadius = 8;
    chip.paddingLeft = 10; chip.paddingRight = 12;
    chip.counterAxisAlignItems = 'CENTER'; chip.resize(chip.width, 30); chip.counterAxisSizingMode = 'FIXED';
    chip.appendChild(icon(ICON.alert, 16, 'color/on-tertiary-container'));
    chip.appendChild(T('Tanque parcial', { size: 12, style: 'Medium', lh: 16, color: 'color/on-tertiary-container' }));
  }
  return r;
}
entryRow('15 oct 2026', '7,10 L/100 km', '142.850 km · 45,2 L', '73,63 €', true);
entryRow('01 oct 2026', '7,32 L/100 km', '142.100 km · 52,1 L', '84,92 €', false);
entryRow('18 sep 2026', '7,26 L/100 km', '141.400 km · 50,8 L', '82,80 €', false);

const fab2 = figma.createAutoLayout('HORIZONTAL', { name: 'fab · extended', itemSpacing: 12 });
B.appendChild(fab2);
fab2.fills = [paint('color/primary-container')]; fab2.cornerRadius = 16; fab2.effects = ELEV3;
fab2.paddingLeft = 20; fab2.paddingRight = 24;
fab2.counterAxisAlignItems = 'CENTER'; fab2.primaryAxisAlignItems = 'CENTER';
fab2.resize(fab2.width, 56); fab2.counterAxisSizingMode = 'FIXED';
fab2.appendChild(icon(ICON.plus, 24, 'color/on-primary-container'));
fab2.appendChild(T('Nueva recarga', { size: 16, style: 'SemiBold', lh: 24, ls: 0.1, color: 'color/on-primary-container' }));
fab2.x = 412 - 16 - fab2.width; fab2.y = 917 - 40 - 56;

await A.screenshot();
await B.screenshot();
return { createdNodeIds: [A.id, B.id], fabRight: fab2.x + fab2.width };
