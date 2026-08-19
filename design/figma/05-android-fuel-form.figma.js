// carApp — Step 05: fix vehicle-form supporting-text indent + Android M3 Expressive · screen-fuel-form
// STATUS: executed successfully. Created frame 21:4 (x=1808); mutated 20:30, 20:38.
//
// Contains a FIX-UP for script 04: filledField() set paddingLeft=16 on the whole field wrapper
// whenever supporting text was present, indenting the field box itself. This resets
// paddingLeft to 0 and re-parents the supporting text into its own padded row. The lookup keys
// off node id '20:2' and the name prefix 'text-field · filled' — re-point on a fresh file.
//
// Demonstrates: M3 segmented button (capsule, selected segment carries a check icon), paired
// numeric fields, computed-total card on tertiary-container, M3 switches on/off (52x32 track,
// 24 thumb checked / 16 unchecked), collapsible section. Content bottom lands at 802 of 917.

const page = await figma.getNodeByIdAsync('14:2');
await figma.setCurrentPageAsync(page);
const F = 'Roboto Flex';
for (const s of ['Regular','Medium','SemiBold','Bold']) await figma.loadFontAsync({ family: F, style: s });
const cols = await figma.variables.getLocalVariableCollectionsAsync();
const m3 = cols.find(c => c.name === 'M3 Expressive');
const vars = await figma.variables.getLocalVariablesAsync('COLOR');
const V = {}; for (const v of vars) if (v.variableCollectionId === m3.id) V[v.name] = v;
const paint = (n) => figma.variables.setBoundVariableForPaint({ type: 'SOLID', color: { r: 0, g: 0, b: 0 } }, 'color', V[n]);
const ICON = {
  x: '<path d="M18 6 6 18"/><path d="m6 6 12 12"/>',
  cal: '<path d="M8 2v4"/><path d="M16 2v4"/><rect width="18" height="18" x="3" y="4" rx="2"/><path d="M3 10h18"/>',
  calc: '<rect width="16" height="20" x="4" y="2" rx="2"/><path d="M8 6h8"/><path d="M8 10h.01"/><path d="M12 10h.01"/><path d="M16 10h.01"/><path d="M8 14h.01"/><path d="M12 14h.01"/><path d="M16 14v4"/><path d="M8 18h.01"/><path d="M12 18h.01"/>',
  check: '<path d="M20 6 9 17l-5-5"/>',
  chevUp: '<path d="m18 15-6-6-6 6"/>',
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

// ---- FIX-UP for script 04 ----------------------------------------------
const vf = await figma.getNodeByIdAsync('20:2');
const fixed = [];
for (const w of vf.findAll(n => n.name && n.name.indexOf('text-field · filled') === 0)) {
  if (w.paddingLeft === 16) {
    w.paddingLeft = 0;
    const sup = w.children[w.children.length - 1];
    if (sup.type === 'TEXT') {
      const holder = figma.createAutoLayout('HORIZONTAL', { name: 'support-row' });
      w.appendChild(holder); holder.fills = []; holder.paddingLeft = 16;
      holder.appendChild(sup);
      holder.layoutSizingHorizontal = 'FILL';
    }
    fixed.push(w.id);
  }
}

// ---- SCREEN: fuel form --------------------------------------------------
const S = figma.createFrame();
S.name = 'screen-fuel-form'; S.resize(412, 917); S.x = 1808; S.y = 0;
S.fills = [paint('color/surface')]; S.clipsContent = true; page.appendChild(S);
const sb = figma.createAutoLayout('HORIZONTAL', { name: 'status-bar' });
S.appendChild(sb); sb.layoutSizingHorizontal = 'FIXED'; sb.resize(412, 32);
sb.primaryAxisAlignItems = 'SPACE_BETWEEN'; sb.counterAxisAlignItems = 'CENTER';
sb.paddingLeft = 24; sb.paddingRight = 24; sb.fills = []; sb.x = 0; sb.y = 0;
sb.appendChild(T('9:41', { size: 14, style: 'Medium', lh: 20 }));
const sr = figma.createAutoLayout('HORIZONTAL', { itemSpacing: 6 }); sb.appendChild(sr);
sr.fills = []; sr.counterAxisAlignItems = 'CENTER';
sr.appendChild(icon(ICON.signal, 16, 'color/on-surface'));
sr.appendChild(icon(ICON.wifi, 16, 'color/on-surface'));
sr.appendChild(icon(ICON.battery, 16, 'color/on-surface'));

const bar = figma.createFrame(); bar.name = 'top-app-bar · small';
bar.resize(412, 64); bar.x = 0; bar.y = 32; bar.fills = []; S.appendChild(bar);
const close = figma.createAutoLayout('HORIZONTAL', { name: 'icon-button' });
bar.appendChild(close); close.fills = []; close.cornerRadius = 999;
close.primaryAxisAlignItems = 'CENTER'; close.counterAxisAlignItems = 'CENTER';
close.resize(48, 48); close.primaryAxisSizingMode = 'FIXED'; close.counterAxisSizingMode = 'FIXED';
close.x = 8; close.y = 8; close.appendChild(icon(ICON.x, 24, 'color/on-surface'));
const ttl = T('Nueva recarga', { size: 22, style: 'SemiBold', lh: 28 });
bar.appendChild(ttl); ttl.x = 64; ttl.y = 18;
const save = figma.createAutoLayout('HORIZONTAL', { name: 'button · filled' });
bar.appendChild(save); save.fills = [paint('color/primary')]; save.cornerRadius = 999;
save.paddingLeft = 20; save.paddingRight = 20;
save.primaryAxisAlignItems = 'CENTER'; save.counterAxisAlignItems = 'CENTER';
save.resize(save.width, 40); save.counterAxisSizingMode = 'FIXED';
save.appendChild(T('Guardar', { size: 14, style: 'SemiBold', lh: 20, ls: 0.1, color: 'color/on-primary' }));
save.x = 412 - 16 - save.width; save.y = 12;

const form = figma.createAutoLayout('VERTICAL', { name: 'form', itemSpacing: 16 });
S.appendChild(form); form.fills = [];
form.layoutSizingHorizontal = 'FIXED'; form.resize(412, form.height);
form.paddingLeft = 16; form.paddingRight = 16; form.x = 0; form.y = 104;

function field(parent, o) {
  const box = figma.createAutoLayout('HORIZONTAL', { name: 'text-field · filled', itemSpacing: 14 });
  parent.appendChild(box);
  box.fills = [paint('color/surface-container-highest')];
  box.topLeftRadius = 8; box.topRightRadius = 8; box.bottomLeftRadius = 0; box.bottomRightRadius = 0;
  box.paddingLeft = 16; box.paddingRight = 16;
  box.counterAxisAlignItems = 'CENTER';
  box.layoutSizingHorizontal = o.fixedWidth ? 'FIXED' : 'FILL';
  if (o.fixedWidth) box.resize(o.fixedWidth, 60); else box.resize(box.width, 60);
  box.primaryAxisSizingMode = 'FIXED'; box.counterAxisSizingMode = 'FIXED';
  if (o.icon) box.appendChild(icon(o.icon, 22, 'color/on-surface-variant'));
  const c = figma.createAutoLayout('VERTICAL', { itemSpacing: 1 }); box.appendChild(c);
  c.fills = []; c.layoutSizingHorizontal = 'FILL';
  c.appendChild(T(o.label, { size: 12, style: 'Medium', lh: 16, ls: 0.4, color: 'color/on-surface-variant' }));
  c.appendChild(T(o.value, { size: 16, lh: 22 }));
  if (o.suffix) box.appendChild(T(o.suffix, { size: 15, lh: 20, color: 'color/on-surface-variant' }));
  const line = figma.createRectangle(); line.name = 'active-indicator';
  line.fills = [paint('color/on-surface-variant')];
  box.appendChild(line); line.layoutPositioning = 'ABSOLUTE';
  line.resize(box.width, 1); line.x = 0; line.y = 59;
  return box;
}
field(form, { label: 'Fecha', value: '15/10/2026', icon: ICON.cal });
field(form, { label: 'Odómetro actual', value: '142.850', suffix: 'km' });

// segmented button
const segWrap = figma.createAutoLayout('VERTICAL', { name: 'entry-mode', itemSpacing: 8 });
form.appendChild(segWrap); segWrap.fills = []; segWrap.layoutSizingHorizontal = 'FILL';
segWrap.appendChild(T('Método de entrada', { size: 14, style: 'SemiBold', lh: 20, ls: 0.1, color: 'color/primary' }));
const seg = figma.createAutoLayout('HORIZONTAL', { name: 'segmented-button', itemSpacing: 0 });
segWrap.appendChild(seg);
seg.fills = []; seg.strokes = [paint('color/outline')]; seg.strokeWeight = 1;
seg.cornerRadius = 999; seg.clipsContent = true;
seg.layoutSizingHorizontal = 'FILL'; seg.resize(seg.width, 44);
seg.counterAxisSizingMode = 'FIXED'; seg.primaryAxisSizingMode = 'FIXED';
function segment(label, selected, last) {
  const s = figma.createAutoLayout('HORIZONTAL', { name: 'segment' + (selected ? ' · selected' : ''), itemSpacing: 6 });
  seg.appendChild(s);
  s.fills = selected ? [paint('color/secondary-container')] : [];
  s.primaryAxisAlignItems = 'CENTER'; s.counterAxisAlignItems = 'CENTER';
  s.layoutSizingHorizontal = 'FILL'; s.layoutSizingVertical = 'FILL';
  if (selected) s.appendChild(icon(ICON.check, 17, 'color/on-secondary-container', 2.4));
  s.appendChild(T(label, { size: 12, style: 'Medium', lh: 16, color: selected ? 'color/on-secondary-container' : 'color/on-surface' }));
  if (!last) {
    const d = figma.createRectangle(); d.name = 'divider';
    d.fills = [paint('color/outline')]; seg.appendChild(d);
    d.resize(1, 44); d.layoutSizingVertical = 'FILL';
  }
  return s;
}
segment('L + Precio/L', true, false);
segment('L + Total', false, false);
segment('Precio/L + Total', false, true);

// paired numeric fields
const pair = figma.createAutoLayout('HORIZONTAL', { name: 'numeric-pair', itemSpacing: 12 });
form.appendChild(pair); pair.fills = []; pair.layoutSizingHorizontal = 'FILL';
const p1 = field(pair, { label: 'Litros', value: '45,20', suffix: 'L' });
const p2 = field(pair, { label: 'Precio por litro', value: '1,629', suffix: '€/L' });
p1.layoutSizingHorizontal = 'FILL'; p2.layoutSizingHorizontal = 'FILL';
p1.children[p1.children.length - 1].resize(p1.width, 1);
p2.children[p2.children.length - 1].resize(p2.width, 1);

// computed total
const total = figma.createAutoLayout('HORIZONTAL', { name: 'total-card · computed', itemSpacing: 14 });
form.appendChild(total);
total.fills = [paint('color/tertiary-container')]; total.cornerRadius = 20;
total.paddingLeft = 20; total.paddingRight = 20; total.paddingTop = 16; total.paddingBottom = 16;
total.counterAxisAlignItems = 'CENTER'; total.layoutSizingHorizontal = 'FILL';
const tcol = figma.createAutoLayout('VERTICAL', { itemSpacing: 2 }); total.appendChild(tcol);
tcol.fills = []; tcol.layoutSizingHorizontal = 'FILL';
tcol.appendChild(T('Total calculado', { size: 12, style: 'Medium', lh: 16, ls: 0.4, color: 'color/on-tertiary-container' }));
tcol.appendChild(T('73,63 €', { size: 24, style: 'Bold', lh: 30, color: 'color/on-tertiary-container' }));
total.appendChild(icon(ICON.calc, 24, 'color/on-tertiary-container'));

function m3Switch(parent, on) {
  const tr = figma.createFrame(); tr.name = 'switch · ' + (on ? 'on' : 'off');
  tr.resize(52, 32); tr.cornerRadius = 999; tr.clipsContent = false;
  tr.fills = [paint(on ? 'color/primary' : 'color/surface-container-highest')];
  if (!on) { tr.strokes = [paint('color/outline')]; tr.strokeWeight = 2; }
  parent.appendChild(tr);
  const th = figma.createEllipse(); th.name = 'thumb';
  if (on) { th.resize(24, 24); th.x = 24; th.y = 4; th.fills = [paint('color/on-primary')]; }
  else { th.resize(16, 16); th.x = 8; th.y = 8; th.fills = [paint('color/outline')]; }
  tr.appendChild(th);
  if (on) { const ck = icon(ICON.check, 16, 'color/primary', 3); tr.appendChild(ck); ck.x = 28; ck.y = 8; }
  return tr;
}
function switchRow(parent, title, desc, on) {
  const r = figma.createAutoLayout('HORIZONTAL', { name: 'switch-row', itemSpacing: 16 });
  parent.appendChild(r);
  r.fills = [paint('color/surface-container-low')]; r.cornerRadius = 20;
  r.paddingLeft = 20; r.paddingRight = 16; r.paddingTop = 16; r.paddingBottom = 16;
  r.counterAxisAlignItems = 'CENTER'; r.layoutSizingHorizontal = 'FILL';
  const c = figma.createAutoLayout('VERTICAL', { itemSpacing: 2 }); r.appendChild(c);
  c.fills = []; c.layoutSizingHorizontal = 'FILL';
  c.appendChild(T(title, { size: 16, style: 'Medium', lh: 22 }));
  const d = T(desc, { size: 12, lh: 16, color: 'color/on-surface-variant', width: 240 });
  c.appendChild(d); d.layoutSizingHorizontal = 'FILL';
  m3Switch(r, on);
  return r;
}
switchRow(form, 'Tanque lleno', 'Si se desmarca, este repostaje se tratará como tanque parcial.', true);

// collapsible
const coll = figma.createAutoLayout('VERTICAL', { name: 'collapsible · expanded', itemSpacing: 14 });
form.appendChild(coll);
coll.fills = [paint('color/surface-container')]; coll.cornerRadius = 20;
coll.paddingLeft = 20; coll.paddingRight = 20; coll.paddingTop = 16; coll.paddingBottom = 18;
coll.layoutSizingHorizontal = 'FILL';
const ch = figma.createAutoLayout('HORIZONTAL', { name: 'header' }); coll.appendChild(ch);
ch.fills = []; ch.primaryAxisAlignItems = 'SPACE_BETWEEN'; ch.counterAxisAlignItems = 'CENTER';
ch.layoutSizingHorizontal = 'FILL';
ch.appendChild(T('Más opciones', { size: 16, style: 'SemiBold', lh: 22 }));
ch.appendChild(icon(ICON.chevUp, 22, 'color/on-surface-variant'));
const inner = figma.createAutoLayout('HORIZONTAL', { name: 'switch-row', itemSpacing: 16 });
coll.appendChild(inner); inner.fills = []; inner.counterAxisAlignItems = 'CENTER';
inner.layoutSizingHorizontal = 'FILL';
const ic = figma.createAutoLayout('VERTICAL', { itemSpacing: 2 }); inner.appendChild(ic);
ic.fills = []; ic.layoutSizingHorizontal = 'FILL';
ic.appendChild(T('Repostajes omitidos', { size: 15, style: 'Medium', lh: 20 }));
const id2 = T('Indica si no has registrado alguna carga anterior.', { size: 12, lh: 16, color: 'color/on-surface-variant', width: 230 });
ic.appendChild(id2); id2.layoutSizingHorizontal = 'FILL';
m3Switch(inner, false);
const notes = field(coll, { label: 'Notas (opcional)', value: 'Ej. Gasolinera de la autovía A-6' });
notes.children[notes.children.length - 1].resize(notes.width, 1);

const pill = figma.createRectangle();
pill.name = 'gesture-handle'; pill.resize(108, 4); pill.cornerRadius = 999;
pill.x = 152; pill.y = 901; pill.fills = [paint('color/on-surface-variant')]; pill.opacity = 0.6;
S.appendChild(pill);

await S.screenshot();
return { createdNodeIds: [S.id], mutatedNodeIds: fixed, formHeight: form.height, formBottom: form.y + form.height };
