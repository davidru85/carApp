// carApp — Step 03: soften the welcome ambient shapes + Android M3 Expressive · screen-home
// STATUS: executed successfully. Created frame 19:2 at x=452; mutated 16:3, 16:4.
//
// Contains a FIX-UP for script 02: applies a 60px LAYER_BLUR to the two ambient ellipses so
// they read as ambient wash rather than hard-edged circles. The node ids '16:2' / names
// 'ambient-primary' / 'ambient-tertiary' come from that run — on a fresh file, re-point them.
//
// Demonstrates: M3 large top app bar, tonal icon button, assist chip, elevated cards at
// elevation level 1, extended FAB at level 3. The FAB right edge lands at 396 of 412, fixing
// the overflow present on the original iOS board.

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
const shadow = (a, y, r, sp) => ({ type: 'DROP_SHADOW', color: { r: 0, g: 0, b: 0, a: a }, offset: { x: 0, y: y }, radius: r, spread: sp, visible: true, blendMode: 'NORMAL' });
const ELEV1 = [shadow(0.30, 1, 2, 0), shadow(0.15, 1, 3, 1)];
const ELEV3 = [shadow(0.30, 1, 3, 0), shadow(0.15, 4, 8, 3)];

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
  if (o.width) { t.textAutoResize = 'HEIGHT'; t.resize(o.width, t.height); }
  return t;
}
const ICON = {
  car: '<path d="m21 8-2 2-1.5-3.7A2 2 0 0 0 15.646 5H8.4a2 2 0 0 0-1.903 1.257L5 10 3 8"/><path d="M7 14h.01"/><path d="M17 14h.01"/><rect width="18" height="8" x="3" y="10" rx="2"/><path d="M5 18v2"/><path d="M19 18v2"/>',
  settings: '<path d="M20 7h-9"/><path d="M14 17H5"/><circle cx="17" cy="17" r="3"/><circle cx="7" cy="7" r="3"/>',
  chevron: '<path d="m9 18 6-6-6-6"/>',
  plus: '<path d="M5 12h14"/><path d="M12 5v14"/>',
  signal: '<path d="M2 20h.01"/><path d="M7 20v-4"/><path d="M12 20v-8"/><path d="M17 20V8"/>',
  wifi: '<path d="M12 20h.01"/><path d="M5 12.859a10 10 0 0 1 14 0"/><path d="M8.5 16.429a5 5 0 0 1 7 0"/>',
  battery: '<rect width="16" height="10" x="2" y="7" rx="2"/><line x1="22" x2="22" y1="11" y2="13"/>'
};
function statusBar(parent) {
  const sb = figma.createAutoLayout('HORIZONTAL', { name: 'status-bar' });
  parent.appendChild(sb);
  sb.layoutSizingHorizontal = 'FIXED'; sb.resize(412, 32);
  sb.primaryAxisAlignItems = 'SPACE_BETWEEN'; sb.counterAxisAlignItems = 'CENTER';
  sb.paddingLeft = 24; sb.paddingRight = 24; sb.fills = []; sb.x = 0; sb.y = 0;
  sb.appendChild(T('9:41', { size: 14, style: 'Medium', lh: 20 }));
  const r = figma.createAutoLayout('HORIZONTAL', { name: 'sys-icons', itemSpacing: 6 });
  sb.appendChild(r); r.fills = []; r.counterAxisAlignItems = 'CENTER';
  r.appendChild(icon(ICON.signal, 16, 'color/on-surface'));
  r.appendChild(icon(ICON.wifi, 16, 'color/on-surface'));
  r.appendChild(icon(ICON.battery, 16, 'color/on-surface'));
  return sb;
}

// ---- FIX-UP for script 02: soften welcome ambient shapes ----------------
const welcome = await figma.getNodeByIdAsync('16:2');
const blurred = [];
for (const nm of ['ambient-primary', 'ambient-tertiary']) {
  const n = welcome.findOne(x => x.name === nm);
  if (n) { n.effects = [{ type: 'LAYER_BLUR', radius: 60, visible: true }]; n.opacity = nm === 'ambient-primary' ? 0.55 : 0.6; blurred.push(n.id); }
}

// ---- home screen --------------------------------------------------------
const S = figma.createFrame();
S.name = 'screen-home'; S.resize(412, 917); S.x = 452; S.y = 0;
S.fills = [paint('color/surface')]; S.clipsContent = true;
page.appendChild(S);
statusBar(S);

// large top app bar
const bar = figma.createFrame();
bar.name = 'top-app-bar · large'; bar.resize(412, 148); bar.x = 0; bar.y = 32;
bar.fills = []; bar.clipsContent = false; S.appendChild(bar);

const iconBtn = figma.createAutoLayout('HORIZONTAL', { name: 'icon-button · tonal' });
bar.appendChild(iconBtn);
iconBtn.fills = [paint('color/secondary-container')]; iconBtn.cornerRadius = 999;
iconBtn.primaryAxisAlignItems = 'CENTER'; iconBtn.counterAxisAlignItems = 'CENTER';
iconBtn.resize(48, 48); iconBtn.primaryAxisSizingMode = 'FIXED'; iconBtn.counterAxisSizingMode = 'FIXED';
iconBtn.x = 344; iconBtn.y = 4;
iconBtn.appendChild(icon(ICON.settings, 24, 'color/on-secondary-container'));

const title = T('Mis vehículos', { size: 32, style: 'Bold', lh: 40, ls: -0.4 });
bar.appendChild(title); title.x = 24; title.y = 66;

const chip = figma.createAutoLayout('HORIZONTAL', { name: 'assist-chip · sync', itemSpacing: 8 });
bar.appendChild(chip);
chip.fills = []; chip.strokes = [paint('color/outline-variant')]; chip.strokeWeight = 1;
chip.cornerRadius = 8; chip.paddingLeft = 12; chip.paddingRight = 14;
chip.counterAxisAlignItems = 'CENTER'; chip.resize(chip.width, 32);
chip.counterAxisSizingMode = 'FIXED';
chip.x = 24; chip.y = 114;
const dot = figma.createEllipse(); dot.resize(8, 8); dot.fills = [paint('color/primary')];
chip.appendChild(dot);
chip.appendChild(T('Sincronizado localmente', { size: 12, style: 'Medium', lh: 16, color: 'color/on-surface-variant' }));

// vehicle list
const list = figma.createAutoLayout('VERTICAL', { name: 'vehicle-list', itemSpacing: 12 });
S.appendChild(list);
list.fills = []; list.layoutSizingHorizontal = 'FIXED'; list.resize(412, list.height);
list.paddingLeft = 16; list.paddingRight = 16;
list.x = 0; list.y = 196;

function vehicleCard(name, brand, odo) {
  const c = figma.createAutoLayout('VERTICAL', { name: 'vehicle-card · elevated', itemSpacing: 16 });
  list.appendChild(c);
  c.fills = [paint('color/surface-container-low')];
  c.cornerRadius = 28; c.effects = ELEV1;
  c.paddingTop = 20; c.paddingBottom = 20; c.paddingLeft = 20; c.paddingRight = 20;
  c.layoutSizingHorizontal = 'FILL';

  const head = figma.createAutoLayout('HORIZONTAL', { name: 'header', itemSpacing: 16 });
  c.appendChild(head); head.fills = []; head.counterAxisAlignItems = 'CENTER';
  head.layoutSizingHorizontal = 'FILL';

  const av = figma.createAutoLayout('HORIZONTAL', { name: 'avatar' });
  head.appendChild(av);
  av.fills = [paint('color/primary-container')]; av.cornerRadius = 999;
  av.primaryAxisAlignItems = 'CENTER'; av.counterAxisAlignItems = 'CENTER';
  av.resize(52, 52); av.primaryAxisSizingMode = 'FIXED'; av.counterAxisSizingMode = 'FIXED';
  av.appendChild(icon(ICON.car, 26, 'color/on-primary-container'));

  const tx = figma.createAutoLayout('VERTICAL', { name: 'titles', itemSpacing: 2 });
  head.appendChild(tx); tx.fills = [];
  tx.appendChild(T(name, { size: 20, style: 'SemiBold', lh: 26 }));
  tx.appendChild(T(brand, { size: 14, lh: 20, color: 'color/on-surface-variant' }));
  tx.layoutSizingHorizontal = 'FILL';

  head.appendChild(icon(ICON.chevron, 22, 'color/on-surface-variant'));

  const div = figma.createRectangle();
  div.name = 'divider'; div.resize(100, 1);
  div.fills = [paint('color/outline-variant')];
  c.appendChild(div); div.layoutSizingHorizontal = 'FILL';

  const stats = figma.createAutoLayout('HORIZONTAL', { name: 'stats' });
  c.appendChild(stats); stats.fills = []; stats.primaryAxisAlignItems = 'SPACE_BETWEEN';
  stats.counterAxisAlignItems = 'CENTER'; stats.layoutSizingHorizontal = 'FILL';
  stats.appendChild(T('Último odómetro', { size: 12, style: 'Medium', lh: 16, ls: 0.4, color: 'color/on-surface-variant' }));
  stats.appendChild(T(odo, { size: 18, style: 'SemiBold', lh: 24, color: 'color/primary' }));
  return c;
}
vehicleCard('Toyota Corolla', 'Toyota', '142.500 km');
vehicleCard('Volkswagen Golf', 'Volkswagen', '42.105 km');

// extended FAB
const fab = figma.createAutoLayout('HORIZONTAL', { name: 'fab · extended', itemSpacing: 12 });
S.appendChild(fab);
fab.fills = [paint('color/primary-container')]; fab.cornerRadius = 16; fab.effects = ELEV3;
fab.paddingLeft = 20; fab.paddingRight = 24;
fab.counterAxisAlignItems = 'CENTER'; fab.primaryAxisAlignItems = 'CENTER';
fab.resize(fab.width, 56); fab.counterAxisSizingMode = 'FIXED';
fab.appendChild(icon(ICON.plus, 24, 'color/on-primary-container'));
fab.appendChild(T('Añadir vehículo', { size: 16, style: 'SemiBold', lh: 24, ls: 0.1, color: 'color/on-primary-container' }));
fab.x = 412 - 16 - fab.width;
fab.y = 917 - 40 - 56;

const pill = figma.createRectangle();
pill.name = 'gesture-handle'; pill.resize(108, 4); pill.cornerRadius = 999;
pill.x = 152; pill.y = 901; pill.fills = [paint('color/on-surface-variant')]; pill.opacity = 0.6;
S.appendChild(pill);

await S.screenshot();
return { createdNodeIds: [S.id], mutatedNodeIds: blurred, fabWidth: fab.width, fabRight: fab.x + fab.width };
