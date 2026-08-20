// carApp — adds a "Dark" mode to both variable collections and fills in every value.
//
// This was impossible under the Starter plan, which caps collections at 1 mode
// (`collection.addMode` throws `Limited to 1 modes only`). The account is on Pro now,
// so this script is also the test of whether that cap is lifted. It is atomic: if
// addMode still throws, nothing is written.
//
// Adding the mode does not by itself make anything look dark. Every screen frame still
// resolves the collection's default mode. Script 13 duplicates the screens and pins the
// copies to this Dark mode.
//
// Android values are the standard M3 dark scheme for the existing teal seed (the light
// scheme's `inverse-primary` #61dbba is the dark scheme's `primary`, which is the
// consistency check that the two halves come from one tonal palette).
// iOS values follow Apple's dark system colours, with the glass materials inverted from
// white-translucent to dark-translucent.

const HEX = (s) => {
  const h = s.replace('#', '');
  const o = { r: parseInt(h.slice(0, 2), 16) / 255, g: parseInt(h.slice(2, 4), 16) / 255, b: parseInt(h.slice(4, 6), 16) / 255 };
  o.a = h.length > 6 ? parseInt(h.slice(6, 8), 16) / 255 : 1;
  return o;
};

const DARK = {
  'M3 Expressive': {
    'color/primary': '#61dbba',
    'color/on-primary': '#00382d',
    'color/primary-container': '#005141',
    'color/on-primary-container': '#7ff8d6',
    'color/secondary': '#b1ccc2',
    'color/on-secondary': '#1c352d',
    'color/secondary-container': '#334b43',
    'color/on-secondary-container': '#cde9dd',
    'color/tertiary': '#eec148',
    'color/on-tertiary': '#422c00',
    'color/tertiary-container': '#5e4100',
    'color/on-tertiary-container': '#ffdea6',
    'color/error': '#ffb4ab',
    'color/on-error': '#690005',
    'color/error-container': '#93000a',
    'color/on-error-container': '#ffdad6',
    'color/surface': '#0f1512',
    'color/on-surface': '#dee4e0',
    'color/surface-variant': '#3f4945',
    'color/on-surface-variant': '#bfc9c4',
    'color/outline': '#899390',
    'color/outline-variant': '#3f4945',
    'color/surface-container-lowest': '#0a0f0d',
    'color/surface-container-low': '#171d1a',
    'color/surface-container': '#1b211f',
    'color/surface-container-high': '#262b29',
    'color/surface-container-highest': '#313634',
    'color/inverse-surface': '#dee4e0',
    'color/inverse-on-surface': '#2b322f',
    'color/inverse-primary': '#006a57'
  },
  'Liquid Glass': {
    // Glass inverts: on a dark backdrop the material is a dark veil, not a white one,
    // and the specular edge dims so it reads as a highlight rather than a white outline.
    'material/regular/fill': '#1c1c1e99',
    'material/regular/fill-raised': '#2c2c2eb8',
    'material/clear/fill': '#ffffff1f',
    'material/clear/dim': '#0000005c',
    'material/tinted/fill': '#34d1ac38',
    'material/specular/high': '#ffffff8c',
    'material/specular/low': '#ffffff1a',
    'material/shadow': '#00000066',
    'label/primary': '#ffffff',
    'label/secondary': '#ebebf599',
    'label/tertiary': '#ebebf54d',
    'label/on-glass': '#eafaf5',
    'separator/opaque': '#38383a',
    'separator/non-opaque': '#545458a6',
    'fill/primary': '#78788052',
    'fill/secondary': '#78788047',
    'background/system': '#1c1c1e',
    'background/grouped': '#000000',
    // Ambient blobs stay chromatic but drop to low luminance, so blurred glass still has
    // colour to refract without lifting the background off black.
    'background/ambient-a': '#0d3d33',
    'background/ambient-b': '#10263a',
    'background/ambient-c': '#3a2a10',
    'accent/brand': '#34d1ac',
    'accent/on-brand': '#00201a',
    'accent/destructive': '#ff6961',
    'accent/warning': '#ffb340'
  }
};

const cols = await figma.variables.getLocalVariableCollectionsAsync();
const allVars = await figma.variables.getLocalVariablesAsync('COLOR');
const report = [];

for (const cname of Object.keys(DARK)) {
  const col = cols.find(c => c.name === cname);
  if (!col) throw new Error('collection not found: ' + cname);

  let dark = col.modes.find(m => m.name === 'Dark');
  if (!dark) {
    const id = col.addMode('Dark');
    dark = { modeId: id, name: 'Dark' };
    // The original single mode is called "Mode 1"; name it now that it has a sibling.
    if (col.modes[0].name === 'Mode 1') col.renameMode(col.modes[0].modeId, 'Light');
  }

  const mine = allVars.filter(v => v.variableCollectionId === col.id);
  const missing = [];
  let set = 0;
  for (const v of mine) {
    const hexVal = DARK[cname][v.name];
    if (!hexVal) { missing.push(v.name); continue; }
    v.setValueForMode(dark.modeId, HEX(hexVal));
    set++;
  }
  report.push({ collection: cname, darkModeId: dark.modeId, variablesSet: set, unmapped: missing });
}

return { report, modes: (await figma.variables.getLocalVariableCollectionsAsync()).map(c => ({ name: c.name, modes: c.modes.map(m => m.name) })) };
