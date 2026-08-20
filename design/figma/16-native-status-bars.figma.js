// carApp — rebuilds every status bar with platform-native content.
//
// HOW TO RUN: one page per invocation (the `setCurrentPageAsync` rule). Set RUN_PAGE and run
// twice — 'android', then 'ios'. Idempotent: it replaces status-bar frames wholesale, so
// re-running is safe.
//
// Applies to BOTH rows. The dark twins are real frames pinned to the Dark mode, so rebuilding
// in place is correct — new children inherit the pinned mode and resolve dark automatically.
// Do NOT instead re-run script 13 to refresh them: the light row now carries prototype
// reactions, and clone() would copy them, giving the dark screens links into the light flow.
//
// WHAT WAS WRONG
// Both platforms shipped the *same three* lucide-style stroke icons — 4 stroke bars, arcs, and
// a stroked rounded rect. None of them is what either OS actually draws, and using one set for
// both defeats the whole point of a platform-native pass. The earlier note claiming Android had
// its own status bar was true only of geometry (32 vs 54 tall, different padding), never of the
// glyphs. Real status bars also use filled glyphs, not strokes.
//
// WHAT CHANGES
// Android (Pixel / Material):
//   - clock left at 16dp, Roboto Flex Medium 14 (was 24dp)
//   - filled Material glyphs: triangular cellular, three-arc wifi, VERTICAL battery with the
//     nub on top
//   - order wifi -> signal -> battery, which is stock Pixel order
// iOS (iPhone 16 Pro — 402x874 is exactly this device):
//   - Dynamic Island: a 125x37 black pill at y=11, centred. It was missing entirely.
//   - clock centred in the left "ear" beside the island rather than hard against the edge
//   - signal as four discrete rounded bars of increasing height
//   - battery as a horizontal outline + fill level + terminal nub, the way iOS draws it
//   - order signal -> wifi -> battery
//
// Frame heights are deliberately unchanged (Android 32, iOS 54). Changing them would reflow
// every screen, and the brief is to fix the status bars, not relayout the app.

const RUN_PAGE = 'android'; // 'android' | 'ios'

const PAGES = {
  android: {
    pageId: '14:2', font: 'Roboto Flex', clockStyle: 'Medium', clockSize: 14,
    ink: 'color/on-surface', width: 412, height: 32
  },
  ios: {
    pageId: '14:3', font: 'Inter', clockStyle: 'Semi Bold', clockSize: 15,
    ink: 'label/primary', width: 402, height: 54
  }
};

const cfg = PAGES[RUN_PAGE];
if (!cfg) throw new Error('RUN_PAGE must be "android" or "ios", got: ' + RUN_PAGE);

const page = await figma.getNodeByIdAsync(cfg.pageId);
await figma.setCurrentPageAsync(page);
await figma.loadFontAsync({ family: cfg.font, style: cfg.clockStyle });

const cols = await figma.variables.getLocalVariableCollectionsAsync();
const col = cols.find(c => c.name === (RUN_PAGE === 'android' ? 'M3 Expressive' : 'Liquid Glass'));
if (!col) throw new Error('collection not found');
const allVars = await figma.variables.getLocalVariablesAsync('COLOR');
const V = {};
for (const v of allVars) if (v.variableCollectionId === col.id) V[v.name] = v;
const paint = (n) => {
  if (!V[n]) throw new Error('variable not found: ' + n);
  return figma.variables.setBoundVariableForPaint({ type: 'SOLID', color: { r: 0, g: 0, b: 0 } }, 'color', V[n]);
};

// Filled glyph from a 24x24 path. The existing icon() helper strokes; status bars fill.
function glyph(d, size, colorName, opacity) {
  const svg = '<svg xmlns="http://www.w3.org/2000/svg" width="24" height="24" viewBox="0 0 24 24" fill="#000000">' + d + '</svg>';
  const n = figma.createNodeFromSvg(svg);
  n.name = 'icon'; n.clipsContent = false;
  for (const c of n.findAll(x => 'fills' in x)) {
    if (c.fills && c.fills.length) c.fills = [paint(colorName)];
  }
  if (size !== 24) n.rescale(size / 24);
  if (opacity !== undefined) n.opacity = opacity;
  return n;
}
function rect(w, h, r, colorName, opacity) {
  const n = figma.createRectangle();
  n.resize(w, h); n.cornerRadius = r; n.fills = [paint(colorName)];
  if (opacity !== undefined) n.opacity = opacity;
  return n;
}
function clock(width, align) {
  const t = figma.createText();
  t.fontName = { family: cfg.font, style: cfg.clockStyle };
  t.characters = '9:41';
  t.fontSize = cfg.clockSize;
  t.lineHeight = { unit: 'PIXELS', value: 20 };
  t.letterSpacing = { unit: 'PIXELS', value: 0 };
  t.fills = [paint(cfg.ink)];
  t.name = '9:41';
  if (width) { t.textAutoResize = 'HEIGHT'; t.resize(width, t.height); t.textAlignHorizontal = align || 'LEFT'; }
  return t;
}

// Material filled glyphs.
const M_SIGNAL = '<path d="M2 22h20V2L2 22z"/>';
const M_WIFI = '<path d="M1 9l2 2c4.97-4.97 13.03-4.97 18 0l2-2C16.93 2.93 7.08 2.93 1 9zm8 8l3 3 3-3c-1.65-1.66-4.34-1.66-6 0zm-4-4l2 2c2.76-2.76 7.24-2.76 10 0l2-2C15.14 9.14 8.87 9.14 5 13z"/>';
const M_BATTERY = '<path d="M15.67 4H14V2h-4v2H8.33C7.6 4 7 4.6 7 5.33v15.34C7 21.4 7.6 22 8.33 22h7.34c.73 0 1.33-.6 1.33-1.33V5.33C17 4.6 16.4 4 15.67 4z"/>';
const IOS_WIFI = M_WIFI; // both systems draw wifi as a filled three-arc fan; this one is honest

function buildAndroid() {
  const sb = figma.createFrame();
  sb.name = 'status-bar'; sb.resize(cfg.width, cfg.height);
  sb.fills = []; sb.clipsContent = false;

  const t = clock();
  sb.appendChild(t); t.x = 16; t.y = 6;

  // Stock Pixel order, left to right: wifi, cellular, battery.
  const cluster = [M_WIFI, M_SIGNAL, M_BATTERY];
  const size = 16, gap = 6;
  let x = cfg.width - 16 - (cluster.length * size + (cluster.length - 1) * gap);
  for (const d of cluster) {
    const g = glyph(d, size, cfg.ink);
    sb.appendChild(g); g.x = x; g.y = 8;
    x += size + gap;
  }
  return sb;
}

function buildIos() {
  const sb = figma.createFrame();
  sb.name = 'status-bar'; sb.resize(cfg.width, cfg.height);
  sb.fills = []; sb.clipsContent = false;

  // Dynamic Island. Deliberately a literal black, not a token: it is a physical cutout, so
  // it stays black in both light and dark.
  const island = figma.createRectangle();
  island.name = 'dynamic-island';
  island.resize(125, 37); island.cornerRadius = 999;
  island.fills = [{ type: 'SOLID', color: { r: 0, g: 0, b: 0 } }];
  sb.appendChild(island);
  island.x = (cfg.width - 125) / 2; island.y = 11;

  // Content centre line, matching the island's centre (11 + 37/2).
  const CY = 29.5;

  // Clock centred in the left ear (0 .. island left edge).
  const earW = island.x;
  const t = clock(earW - 20, 'CENTER');
  sb.appendChild(t); t.x = 10; t.y = CY - 10;

  const right = cfg.width - 30;

  // Battery: outline + fill level + terminal nub.
  const batW = 25, batH = 13;
  const batX = right - batW - 2;
  const shell = figma.createRectangle();
  shell.name = 'battery-shell';
  shell.resize(batW, batH); shell.cornerRadius = 4.3;
  shell.fills = []; shell.strokes = [paint(cfg.ink)]; shell.strokeWeight = 1; shell.strokeAlign = 'INSIDE';
  shell.opacity = 0.38;
  sb.appendChild(shell); shell.x = batX; shell.y = CY - batH / 2;

  const level = rect(batW - 4, batH - 4, 2.5, cfg.ink);
  level.name = 'battery-level';
  sb.appendChild(level); level.x = batX + 2; level.y = CY - (batH - 4) / 2;

  const nub = rect(1.5, 4.5, 0.75, cfg.ink, 0.4);
  nub.name = 'battery-nub';
  sb.appendChild(nub); nub.x = batX + batW + 1.2; nub.y = CY - 2.25;

  // Wifi sits left of the battery.
  const wifi = glyph(IOS_WIFI, 17, cfg.ink);
  sb.appendChild(wifi); wifi.x = batX - 6 - 17; wifi.y = CY - 8.5;

  // Signal: four discrete rounded bars, increasing height, bottom-aligned.
  const bars = figma.createFrame();
  bars.name = 'signal'; bars.resize(18, 12); bars.fills = []; bars.clipsContent = false;
  sb.appendChild(bars);
  bars.x = wifi.x - 6 - 18; bars.y = CY - 6;
  const heights = [4, 6.5, 9, 11.5];
  for (let i = 0; i < heights.length; i++) {
    const b = rect(3, heights[i], 1, cfg.ink);
    b.name = 'bar'; bars.appendChild(b);
    b.x = i * 5; b.y = 12 - heights[i];
  }
  return sb;
}

const build = RUN_PAGE === 'android' ? buildAndroid : buildIos;

const targets = page.findAll(n => n.type === 'FRAME' && n.name === 'status-bar');
const replaced = [];
for (const old of targets) {
  const parent = old.parent;
  const index = parent.children.indexOf(old);
  const x = old.x, y = old.y;
  const fresh = build();
  parent.insertChild(index, fresh);
  // Only meaningful when the parent lays out absolutely; every parent here does.
  if (!parent.layoutMode || parent.layoutMode === 'NONE') { fresh.x = x; fresh.y = y; }
  old.remove();
  replaced.push({ id: fresh.id, parent: parent.name });
}

await figma.currentPage.screenshot();
return { page: cfg.pageId, platform: RUN_PAGE, replacedCount: replaced.length, replaced };
