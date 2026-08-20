// carApp — duplicates every screen on a page into a second row pinned to the Dark mode
// created by script 12.
//
// Requires: script 12 has run (both collections must already have a "Dark" mode).
//
// HOW TO RUN: this script targets ONE page per invocation, because `use_figma` allows
// `setCurrentPageAsync` at most once per call. Set RUN_PAGE below and run it twice — once
// as 'android', once as 'ios'. The two calls are independent and can be issued in parallel.
//
// Idempotent: frames whose name already ends in DARK_SUFFIX are skipped, and any existing
// dark twin of a frame is removed and rebuilt, so re-running after editing a light screen
// refreshes its dark counterpart rather than piling up duplicates.
//
// Why a duplicated row rather than flipping the whole page to Dark: the light designs are
// the delivered artefact and must stay visible. Figma resolves a collection mode per node
// subtree, so a clone pinned with `setExplicitVariableModeForCollection` renders dark while
// its original renders light, side by side in the same file.

const RUN_PAGE = 'android'; // 'android' | 'ios'

const PAGES = {
  android: {
    pageId: '14:2',
    collection: 'M3 Expressive',
    font: 'Roboto Flex',
    styles: ['Regular', 'Medium', 'SemiBold', 'Bold']
  },
  ios: {
    pageId: '14:3',
    collection: 'Liquid Glass',
    font: 'Inter',
    // Inter spells semibold with a space. Roboto Flex does not. Do not "fix" either.
    styles: ['Regular', 'Medium', 'Semi Bold', 'Bold']
  }
};

const DARK_SUFFIX = ' · dark';
const DARK_ROW_Y = 1100; // clears the tallest light frame (Android, 917) with margin

const cfg = PAGES[RUN_PAGE];
if (!cfg) throw new Error('RUN_PAGE must be "android" or "ios", got: ' + RUN_PAGE);

const page = await figma.getNodeByIdAsync(cfg.pageId);
await figma.setCurrentPageAsync(page);

// Fonts must be loaded before cloning or setting variable modes on subtrees containing
// text, otherwise the write throws "Cannot write to node with unloaded font".
for (const s of cfg.styles) await figma.loadFontAsync({ family: cfg.font, style: s });

const cols = await figma.variables.getLocalVariableCollectionsAsync();
const col = cols.find(c => c.name === cfg.collection);
if (!col) throw new Error('collection not found: ' + cfg.collection);
const darkMode = col.modes.find(m => m.name === 'Dark');
if (!darkMode) throw new Error('no Dark mode on ' + cfg.collection + ' — run script 12 first');

const light = page.children.filter(n => n.type === 'FRAME' && !n.name.endsWith(DARK_SUFFIX));

// Drop stale twins so a re-run refreshes rather than duplicates.
const removed = [];
for (const n of page.children.filter(n => n.name.endsWith(DARK_SUFFIX))) {
  removed.push(n.id);
  n.remove();
}

const created = [];
for (const src of light) {
  const dup = src.clone();
  dup.name = src.name + DARK_SUFFIX;
  page.appendChild(dup);
  dup.x = src.x;
  dup.y = DARK_ROW_Y;
  dup.setExplicitVariableModeForCollection(col, darkMode.modeId);
  created.push({ name: dup.name, id: dup.id, x: dup.x });
}

await figma.currentPage.screenshot();
return { page: cfg.pageId, collection: cfg.collection, darkModeId: darkMode.modeId, removed, created };
