// carApp — replaces the iOS sheet toolbar text actions with compact circular glyph buttons.
//
// Targets page 14:3 only. Applies to both rows (light and dark twins). Idempotent: it looks
// for either the original text labels or its own buttons and rebuilds from scratch.
//
// WHY
// The three sheets carried "Cancelar" / "Guardar" / "Cerrar" as 17pt text. That is the older
// iOS bar-button idiom. Current Liquid Glass sheets use a compact circular control instead —
// a grey translucent circle with a glyph — which is both more minimal and more current.
//
// Cancel and Close are the same gesture and get the same ✕. Save is the only affirmative
// action, so it is the only one that gets colour: a brand-filled circle with a ✓. Settings has
// no affirmative action, so it gets the ✕ alone.
//
// NOT glass. The toolbar is already a glass surface and the design notes forbid glass on
// glass, so the buttons use the flat translucent `fill/primary` that iOS uses for its own
// circular close buttons. They still adapt across modes because that is a token.
//
// AFTER RUNNING THIS, RE-RUN SCRIPT 15 for iOS. The prototype wires these actions by layer
// name, and the names change from 'Cancelar'/'Guardar'/'Cerrar' to
// 'btn-cancel'/'btn-save'/'btn-close'. Script 15's IOS_LINKS table is already updated to match.

const CLOSE_D = '<path d="M18 6 6 18"/><path d="m6 6 12 12"/>';
const CHECK_D = '<path d="M20 6 9 17l-5-5"/>';

const BTN = 30;            // circular control diameter
const TOOLBAR_CENTRE = 39; // title sits at y=28 with a 22px line box, so its centre is 39
const INSET = 16;

const page = await figma.getNodeByIdAsync('14:3');
await figma.setCurrentPageAsync(page);
for (const s of ['Regular', 'Medium', 'Semi Bold', 'Bold']) {
  await figma.loadFontAsync({ family: 'Inter', style: s });
}

const cols = await figma.variables.getLocalVariableCollectionsAsync();
const col = cols.find(c => c.name === 'Liquid Glass');
if (!col) throw new Error('Liquid Glass collection not found');
const allVars = await figma.variables.getLocalVariablesAsync('COLOR');
const V = {};
for (const v of allVars) if (v.variableCollectionId === col.id) V[v.name] = v;
const paint = (n) => {
  if (!V[n]) throw new Error('variable not found: ' + n);
  return figma.variables.setBoundVariableForPaint({ type: 'SOLID', color: { r: 0, g: 0, b: 0 } }, 'color', V[n]);
};

function strokeGlyph(d, size, colorName, weight) {
  const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="none" stroke="#000000" stroke-width="' + weight + '" stroke-linecap="round" stroke-linejoin="round">' + d + '</svg>';
  const n = figma.createNodeFromSvg(svg);
  n.name = 'icon'; n.clipsContent = false;
  for (const c of n.findAll(x => 'strokes' in x)) {
    if (c.strokes.length) c.strokes = [paint(colorName)];
  }
  if (size !== 24) n.rescale(size / 24);
  return n;
}

function circleButton(name, d, bgToken, inkToken, glyphSize, weight) {
  const b = figma.createFrame();
  b.name = name;
  b.resize(BTN, BTN);
  b.cornerRadius = 999;
  b.clipsContent = false;
  b.fills = [paint(bgToken)];
  const g = strokeGlyph(d, glyphSize, inkToken, weight);
  b.appendChild(g);
  g.x = (BTN - glyphSize) / 2;
  g.y = (BTN - glyphSize) / 2;
  return b;
}

const OLD_NAMES = ['Cancelar', 'Guardar', 'Cerrar', 'btn-cancel', 'btn-save', 'btn-close'];

const toolbars = page.findAll(n => n.type === 'FRAME' && n.name === 'toolbar · glass');
const touched = [];

for (const tb of toolbars) {
  const olds = tb.children.filter(c => OLD_NAMES.indexOf(c.name) !== -1);
  // Decide before removing: only the two forms have an affirmative action.
  const hasSave = olds.some(c => c.name === 'Guardar' || c.name === 'btn-save');
  // 'Cerrar' means this is Settings — the dismiss is a close, not a cancel.
  const isClose = olds.some(c => c.name === 'Cerrar' || c.name === 'btn-close');
  if (!olds.length) continue;
  for (const o of olds) o.remove();

  const leading = circleButton(
    isClose ? 'btn-close' : 'btn-cancel',
    CLOSE_D, 'fill/primary', 'label/secondary', 13, 2.6
  );
  tb.appendChild(leading);
  leading.x = INSET;
  leading.y = TOOLBAR_CENTRE - BTN / 2;

  const made = [leading.name];
  if (hasSave) {
    const save = circleButton('btn-save', CHECK_D, 'accent/brand', 'accent/on-brand', 14, 2.8);
    tb.appendChild(save);
    save.x = tb.width - INSET - BTN;
    save.y = TOOLBAR_CENTRE - BTN / 2;
    made.push(save.name);
  }
  touched.push({ toolbar: tb.id, screen: tb.parent && tb.parent.parent ? tb.parent.parent.name : '?', buttons: made });
}

await figma.currentPage.screenshot();
return { toolbarsTouched: touched.length, touched };
