// carApp — iOS depth pass. Fixes two defects found by auditing the built screens against
// the Liquid Glass brief. Targets page 14:3 only. Idempotent.
//
// Requires: scripts 12 and 13 have run (Dark mode exists; dark twins exist).
//
// DEFECT 1 — the sheet screens read flat.
// `sheet` is filled with an opaque `background/grouped` and spans y=44..874, so it covers
// the `ambient-backdrop` sibling almost entirely. On screen-vehicle-form, screen-fuel-form
// and screen-settings the glass toolbar therefore has nothing to refract, which is the one
// thing the design notes say glass must never lack. In Dark mode it is worse: grouped
// resolves to #000000, so the sheet is pure black.
// Fix: drop the sheet paint to SHEET_OPACITY so the blurred ambient blobs read through the
// gaps between the opaque inset-group cards. Card fills stay at full opacity, so text
// contrast is untouched — only the negative space gains colour.
// Note the tension: real iOS sheets are opaque. This trades a little platform literalism
// for the layered depth the brief explicitly asks for, and it is a one-line revert.
//
// DEFECT 2 — the selected segment is hardcoded white.
// `segment · selected` was given a literal {r:1,g:1,b:1} fill instead of a token, so it
// ignores the mode and renders as a bright white pill on the dark screens. iOS dark uses a
// grey selection, not white. Fix: introduce `control/segment-selected` (white / #636366)
// and bind the fill to it.

const SHEET_OPACITY = 0.8;

const page = await figma.getNodeByIdAsync('14:3');
await figma.setCurrentPageAsync(page);

const cols = await figma.variables.getLocalVariableCollectionsAsync();
const col = cols.find(c => c.name === 'Liquid Glass');
if (!col) throw new Error('Liquid Glass collection not found');
const light = col.modes.find(m => m.name === 'Light');
const dark = col.modes.find(m => m.name === 'Dark');
if (!light || !dark) throw new Error('expected Light and Dark modes — run script 12 first');

// ---- token for the segmented-control selection -------------------------------
const existing = await figma.variables.getLocalVariablesAsync('COLOR');
let segVar = existing.find(v => v.variableCollectionId === col.id && v.name === 'control/segment-selected');
if (!segVar) {
  segVar = figma.variables.createVariable('control/segment-selected', col, 'COLOR');
  // Scoped deliberately: this is a container fill, never a text or stroke colour.
  segVar.scopes = ['FRAME_FILL', 'SHAPE_FILL'];
}
segVar.setValueForMode(light.modeId, { r: 1, g: 1, b: 1, a: 1 });
segVar.setValueForMode(dark.modeId, { r: 0x63 / 255, g: 0x63 / 255, b: 0x66 / 255, a: 1 });

// ---- apply -------------------------------------------------------------------
// NOTE: do not use page.query() for these. The layer names contain "·" (U+00B7) and the
// selector parser rejects non-ASCII: `Invalid selector: unexpected character (0xc2)`.
// findAll with a predicate has no such restriction.
const sheets = [];
for (const n of page.findAll(n => n.type === 'FRAME' && n.name === 'sheet')) {
  const f = n.fills[0];
  if (!f) continue;
  if (f.opacity !== SHEET_OPACITY) {
    n.fills = [Object.assign({}, f, { opacity: SHEET_OPACITY })];
    sheets.push(n.id);
  }
}

const segments = [];
for (const n of page.findAll(n => n.type === 'FRAME' && n.name.indexOf('segment') === 0 && n.name.indexOf('selected') !== -1)) {
  n.fills = [figma.variables.setBoundVariableForPaint({ type: 'SOLID', color: { r: 0, g: 0, b: 0 } }, 'color', segVar)];
  segments.push(n.id);
}

await figma.currentPage.screenshot();
return {
  segmentVariableId: segVar.id,
  sheetsRetinted: sheets,
  segmentsRebound: segments,
  sheetOpacity: SHEET_OPACITY
};
