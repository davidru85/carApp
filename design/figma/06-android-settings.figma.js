// carApp — Step 06: Android M3 Expressive · screen-settings
// STATUS: executed successfully. Created frame 22:2 at x=2260.
//
// Demonstrates: grouped surface-container cards at corner-xl (28) with section headers in the
// primary role, M3 list rows with dividers, primary-container status chip, M3 switch (off),
// destructive row using the error colour role, version footer.
//
// Copy here is the unified Spanish string set used across BOTH platforms — the original boards
// had diverged wording for identical functionality.

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
  back: '<path d="m12 19-7-7 7-7"/><path d="M19 12H5"/>',
  chev: '<path d="m9 18 6-6-6-6"/>',
  logout: '<path d="M9 21H5a2 2 0 0 1-2-2V5a2 2 0 0 1 2-2h4"/><path d="m16 17 5-5-5-5"/><path d="M21 12H9"/>',
  trash: '<path d="M3 6h18"/><path d="M19 6v14a2 2 0 0 1-2 2H7a2 2 0 0 1-2-2V6"/><path d="M8 6V4a2 2 0 0 1 2-2h4a2 2 0 0 1 2 2v2"/><path d="M10 11v6"/><path d="M14 11v6"/>',
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

const S = figma.createFrame();
S.name = 'screen-settings'; S.resize(412, 917); S.x = 2260; S.y = 0;
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
const back = figma.createAutoLayout('HORIZONTAL', { name: 'icon-button' });
bar.appendChild(back); back.fills = []; back.cornerRadius = 999;
back.primaryAxisAlignItems = 'CENTER'; back.counterAxisAlignItems = 'CENTER';
back.resize(48, 48); back.primaryAxisSizingMode = 'FIXED'; back.counterAxisSizingMode = 'FIXED';
back.x = 8; back.y = 8; back.appendChild(icon(ICON.back, 24, 'color/on-surface'));
const ttl = T('Ajustes', { size: 22, style: 'SemiBold', lh: 28 });
bar.appendChild(ttl); ttl.x = 64; ttl.y = 18;

const list = figma.createAutoLayout('VERTICAL', { name: 'settings-list', itemSpacing: 24 });
S.appendChild(list); list.fills = [];
list.layoutSizingHorizontal = 'FIXED'; list.resize(412, list.height);
list.paddingLeft = 16; list.paddingRight = 16; list.x = 0; list.y = 108;

function group(header) {
  const g = figma.createAutoLayout('VERTICAL', { name: 'group · ' + header, itemSpacing: 10 });
  list.appendChild(g); g.fills = []; g.layoutSizingHorizontal = 'FILL';
  const h = T(header, { size: 14, style: 'SemiBold', lh: 20, ls: 0.1, color: 'color/primary' });
  g.appendChild(h); h.x = 4;
  const card = figma.createAutoLayout('VERTICAL', { name: 'card', itemSpacing: 0 });
  g.appendChild(card);
  card.fills = [paint('color/surface-container-low')]; card.cornerRadius = 28;
  card.layoutSizingHorizontal = 'FILL'; card.clipsContent = true;
  return card;
}
function divider(card) {
  const d = figma.createRectangle(); d.name = 'divider';
  d.fills = [paint('color/outline-variant')]; card.appendChild(d);
  d.resize(100, 1); d.layoutSizingHorizontal = 'FILL';
}
function row(card, title, desc, opts) {
  opts = opts || {};
  const r = figma.createAutoLayout('HORIZONTAL', { name: 'list-item', itemSpacing: 16 });
  card.appendChild(r); r.fills = [];
  r.paddingLeft = 20; r.paddingRight = 18; r.paddingTop = desc ? 16 : 18; r.paddingBottom = desc ? 16 : 18;
  r.counterAxisAlignItems = 'CENTER'; r.layoutSizingHorizontal = 'FILL';
  const c = figma.createAutoLayout('VERTICAL', { itemSpacing: 2 }); r.appendChild(c);
  c.fills = []; c.layoutSizingHorizontal = 'FILL';
  c.appendChild(T(title, { size: 16, style: 'Medium', lh: 22, color: opts.danger ? 'color/error' : 'color/on-surface' }));
  if (desc) { const d = T(desc, { size: 12, lh: 16, color: 'color/on-surface-variant', width: 240 }); c.appendChild(d); d.layoutSizingHorizontal = 'FILL'; }
  if (opts.value) {
    const vr = figma.createAutoLayout('HORIZONTAL', { itemSpacing: 4 }); r.appendChild(vr);
    vr.fills = []; vr.counterAxisAlignItems = 'CENTER';
    vr.appendChild(T(opts.value, { size: 15, style: 'Medium', lh: 20, color: 'color/on-surface-variant' }));
    vr.appendChild(icon(ICON.chev, 20, 'color/on-surface-variant'));
  }
  if (opts.chip) {
    const ch = figma.createAutoLayout('HORIZONTAL', { name: 'status-chip', itemSpacing: 7 });
    r.appendChild(ch); ch.fills = [paint('color/primary-container')]; ch.cornerRadius = 8;
    ch.paddingLeft = 10; ch.paddingRight = 12; ch.counterAxisAlignItems = 'CENTER';
    ch.resize(ch.width, 28); ch.counterAxisSizingMode = 'FIXED';
    const dt = figma.createEllipse(); dt.resize(7, 7); dt.fills = [paint('color/primary')]; ch.appendChild(dt);
    ch.appendChild(T(opts.chip, { size: 12, style: 'Medium', lh: 16, color: 'color/on-primary-container' }));
  }
  if (opts.switchOff) {
    const tr = figma.createFrame(); tr.name = 'switch · off';
    tr.resize(52, 32); tr.cornerRadius = 999; tr.clipsContent = false;
    tr.fills = [paint('color/surface-container-highest')];
    tr.strokes = [paint('color/outline')]; tr.strokeWeight = 2; r.appendChild(tr);
    const th = figma.createEllipse(); th.resize(16, 16); th.x = 8; th.y = 8;
    th.fills = [paint('color/outline')]; tr.appendChild(th);
  }
  if (opts.icon) r.appendChild(icon(opts.icon, 22, opts.danger ? 'color/error' : 'color/on-surface-variant'));
  return r;
}

const g1 = group('Unidades y moneda');
row(g1, 'Moneda', 'Divisa predeterminada de los repostajes', { value: 'EUR €' });
divider(g1);
row(g1, 'Unidad de distancia', null, { value: 'Kilómetros' });
divider(g1);
row(g1, 'Unidad de volumen', null, { value: 'Litros' });

const g2 = group('Seguridad y copias');
row(g2, 'Copia de seguridad', 'Última sincronización local guardada', { chip: 'Activa' });

const g3 = group('Privacidad');
row(g3, 'Compartir analíticas', 'Comparte telemetría anónima para ayudarnos a mejorar carApp.', { switchOff: true });

const g4 = group('Cuenta');
row(g4, 'Cerrar sesión', null, { icon: ICON.logout });
divider(g4);
row(g4, 'Eliminar cuenta', null, { icon: ICON.trash, danger: true });

const foot = figma.createAutoLayout('VERTICAL', { name: 'footer', itemSpacing: 2 });
S.appendChild(foot); foot.fills = []; foot.counterAxisAlignItems = 'CENTER';
foot.layoutSizingHorizontal = 'FIXED'; foot.resize(412, foot.height);
foot.appendChild(T('carApp v1.0.0', { size: 12, style: 'Medium', lh: 16, color: 'color/on-surface-variant', align: 'CENTER' }));
foot.appendChild(T('Local-First & Safe Tracker', { size: 12, lh: 16, color: 'color/on-surface-variant', align: 'CENTER' }));
foot.x = 0; foot.y = list.y + list.height + 28;

const pill = figma.createRectangle();
pill.name = 'gesture-handle'; pill.resize(108, 4); pill.cornerRadius = 999;
pill.x = 152; pill.y = 901; pill.fills = [paint('color/on-surface-variant')]; pill.opacity = 0.6;
S.appendChild(pill);

await S.screenshot();
return { createdNodeIds: [S.id], listBottom: list.y + list.height, footY: foot.y };
