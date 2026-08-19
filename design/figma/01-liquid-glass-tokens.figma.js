// carApp — Step 01: create the Liquid Glass token collection
// STATUS: executed successfully.
// Created collection: "Liquid Glass" (VariableCollectionId:14:51), 35 variables, mode "14:1"
//
// Covers the two Apple material variants (Regular = adaptive default, Clear = permanently
// transparent), the specular edge pair, iOS semantic label/background colours, concentric
// radii and blur radii.

const rgba = (h, a) => ({ r: parseInt(h.slice(1,3),16)/255, g: parseInt(h.slice(3,5),16)/255, b: parseInt(h.slice(5,7),16)/255, a: a === undefined ? 1 : a });

const col = figma.variables.createVariableCollection('Liquid Glass');
const mode = col.modes[0].modeId;
const made = [];
const COLOR_SCOPES = ['FRAME_FILL','SHAPE_FILL','TEXT_FILL','STROKE_COLOR'];

const colors = {
  // Material — Regular glass is the adaptive default; Clear is the permanently-transparent variant
  'material/regular/fill':        ['#FFFFFF', 0.60],
  'material/regular/fill-raised': ['#FFFFFF', 0.72],
  'material/clear/fill':          ['#FFFFFF', 0.18],
  'material/clear/dim':           ['#000000', 0.18],
  'material/tinted/fill':         ['#0A7C66', 0.22],
  // Specular edge — bright top-left catch, dim bottom-right falloff
  'material/specular/high':       ['#FFFFFF', 0.90],
  'material/specular/low':        ['#FFFFFF', 0.20],
  'material/shadow':              ['#0B1F1A', 0.18],
  // iOS semantic content colors
  'label/primary':                ['#000000', 1],
  'label/secondary':              ['#3C3C43', 0.60],
  'label/tertiary':               ['#3C3C43', 0.30],
  'label/on-glass':               ['#0B1F1A', 1],
  'separator/opaque':             ['#C6C6C8', 1],
  'separator/non-opaque':         ['#3C3C43', 0.29],
  'fill/primary':                 ['#787880', 0.20],
  'fill/secondary':               ['#787880', 0.16],
  'background/system':            ['#FFFFFF', 1],
  'background/grouped':           ['#F2F2F7', 1],
  'background/ambient-a':         ['#BFF2E0', 1],
  'background/ambient-b':         ['#E9F6FF', 1],
  'background/ambient-c':         ['#FFE9C7', 1],
  'accent/brand':                 ['#0A7C66', 1],
  'accent/on-brand':              ['#FFFFFF', 1],
  'accent/destructive':           ['#D93025', 1],
  'accent/warning':               ['#B86B00', 1]
};
for (const [name, [h, a]] of Object.entries(colors)) {
  const v = figma.variables.createVariable(name, col, 'COLOR');
  v.scopes = COLOR_SCOPES;
  v.setValueForMode(mode, rgba(h, a));
  made.push(name);
}

// Concentric radii — inner control radius = outer radius minus the padding between them
const radii = {
  'radius/capsule': 999,
  'radius/control': 12,
  'radius/field': 14,
  'radius/card': 20,
  'radius/group': 26,
  'radius/sheet': 34,
  'radius/device': 54
};
for (const [name, n] of Object.entries(radii)) {
  const v = figma.variables.createVariable(name, col, 'FLOAT');
  v.scopes = ['CORNER_RADIUS'];
  v.setValueForMode(mode, n);
  made.push(name);
}

const blur = { 'blur/regular': 32, 'blur/clear': 12, 'blur/chrome': 40 };
for (const [name, n] of Object.entries(blur)) {
  const v = figma.variables.createVariable(name, col, 'FLOAT');
  v.scopes = ['WIDTH_HEIGHT'];
  v.setValueForMode(mode, n);
  made.push(name);
}

return { collectionId: col.id, modeId: mode, variableCount: made.length, names: made.length };
