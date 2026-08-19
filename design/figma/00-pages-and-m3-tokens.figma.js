// carApp — Step 00: create both redesign pages + the Material 3 Expressive token collection
// STATUS: executed successfully.
// Created pages: "01 · Android — M3 Expressive" (14:2), "02 · iOS — Liquid Glass" (14:3)
// Created collection: "M3 Expressive" (VariableCollectionId:14:4), 46 variables, mode "14:0"
//
// NOTE: Figma free/Starter plans cap variable collections at 1 mode, so Light/Dark cannot be
// expressed as variable modes here. An earlier version of this script called
// collection.addMode('Dark') and failed with "in addMode: Limited to 1 modes only".

const p1 = figma.createPage(); p1.name = '01 · Android — M3 Expressive';
const p2 = figma.createPage(); p2.name = '02 · iOS — Liquid Glass';

const hex = (h) => ({ r: parseInt(h.slice(1,3),16)/255, g: parseInt(h.slice(3,5),16)/255, b: parseInt(h.slice(5,7),16)/255 });

const col = figma.variables.createVariableCollection('M3 Expressive');
const mode = col.modes[0].modeId;
const made = [];

const COLOR_SCOPES = ['FRAME_FILL','SHAPE_FILL','TEXT_FILL','STROKE_COLOR'];
const colors = {
  'color/primary': '#006A57',
  'color/on-primary': '#FFFFFF',
  'color/primary-container': '#7FF8D6',
  'color/on-primary-container': '#00201A',
  'color/secondary': '#4A635B',
  'color/on-secondary': '#FFFFFF',
  'color/secondary-container': '#CDE9DD',
  'color/on-secondary-container': '#06201A',
  'color/tertiary': '#7C5800',
  'color/on-tertiary': '#FFFFFF',
  'color/tertiary-container': '#FFDEA6',
  'color/on-tertiary-container': '#271900',
  'color/error': '#BA1A1A',
  'color/on-error': '#FFFFFF',
  'color/error-container': '#FFDAD6',
  'color/on-error-container': '#410002',
  'color/surface': '#F5FBF7',
  'color/on-surface': '#171D1A',
  'color/surface-variant': '#DBE5DF',
  'color/on-surface-variant': '#3F4945',
  'color/outline': '#6F7975',
  'color/outline-variant': '#BFC9C4',
  'color/surface-container-lowest': '#FFFFFF',
  'color/surface-container-low': '#EFF5F1',
  'color/surface-container': '#E9EFEC',
  'color/surface-container-high': '#E4EAE6',
  'color/surface-container-highest': '#DEE4E0',
  'color/inverse-surface': '#2B322F',
  'color/inverse-on-surface': '#ECF2EE',
  'color/inverse-primary': '#61DBBA'
};
for (const [name, h] of Object.entries(colors)) {
  const v = figma.variables.createVariable(name, col, 'COLOR');
  v.scopes = COLOR_SCOPES;
  v.setValueForMode(mode, hex(h));
  made.push(name);
}

// Material 3 Expressive shape scale
const shape = {
  'shape/corner-none': 0,
  'shape/corner-xs': 4,
  'shape/corner-sm': 8,
  'shape/corner-md': 12,
  'shape/corner-lg': 16,
  'shape/corner-lg-increased': 20,
  'shape/corner-xl': 28,
  'shape/corner-xl-increased': 32,
  'shape/corner-xxl': 48,
  'shape/corner-full': 999
};
for (const [name, n] of Object.entries(shape)) {
  const v = figma.variables.createVariable(name, col, 'FLOAT');
  v.scopes = ['CORNER_RADIUS'];
  v.setValueForMode(mode, n);
  made.push(name);
}

const space = { 'space/4': 4, 'space/8': 8, 'space/12': 12, 'space/16': 16, 'space/24': 24, 'space/32': 32 };
for (const [name, n] of Object.entries(space)) {
  const v = figma.variables.createVariable(name, col, 'FLOAT');
  v.scopes = ['GAP','WIDTH_HEIGHT'];
  v.setValueForMode(mode, n);
  made.push(name);
}

return { createdNodeIds: [p1.id, p2.id], pages: { android: p1.id, ios: p2.id }, collectionId: col.id, modeId: mode, variableCount: made.length };
