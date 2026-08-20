// carApp — wires the six light screens of a page into a navigable prototype with
// platform-native transitions, and sets the flow starting point.
//
// Requires: all six screens exist on the target page.
//
// HOW TO RUN: one page per invocation (the `setCurrentPageAsync` rule). Set RUN_PAGE and
// run twice — 'android', then 'ios'. The two calls are independent and can go in parallel.
//
// SCOPE: the light row only. The dark row from script 13 is a colour reference, not a
// second prototype; wiring both would put two competing flows in one page.
//
// Motion language per platform — this is the whole point of the script, so the numbers are
// chosen, not defaulted:
//
// Android / M3 Expressive
//   Emphasized easing, cubic-bezier(0.2, 0.0, 0.0, 1.0). M3 pairs a slow-in with a hard
//   deceleration so movement feels weighted rather than linear. 500ms entering, 400ms
//   returning — M3 makes the return trip shorter because the user already knows the target.
//   SMART_ANIMATE throughout, which is the closest Figma primitive to M3's container
//   transform: shared elements (FAB, cards, app-bar titles) morph instead of cross-fading.
//
// iOS / Liquid Glass
//   Two distinct navigations, because iOS treats them as different things:
//     - push/pop for hierarchy (home -> detail), PUSH left / right, 350ms
//     - sheet present/dismiss for modals (the three forms), MOVE_IN / MOVE_OUT from BOTTOM,
//       500ms in, 400ms out
//   Springs are attempted first (CUSTOM_SPRING), since iOS motion is spring-driven and that
//   is what makes it read as fluid rather than timed. If this build's Plugin API rejects the
//   spring shape, the script falls back to cubic-bezier(0.32, 0.72, 0, 1) — a close
//   approximation of Apple's default — and reports which one it actually used.

const RUN_PAGE = 'android'; // 'android' | 'ios'

const M3_EMPHASIZED = { type: 'CUSTOM_CUBIC_BEZIER', easingFunctionCubicBezier: { x1: 0.2, y1: 0.0, x2: 0.0, y2: 1.0 } };
// easingFunctionSpring takes exactly mass/stiffness/damping. Adding `initialVelocity`
// fails validation with `Unrecognized key(s) in object: 'initialVelocity'` — springs are
// supported, that one key is not. damping 26 against stiffness 240 is just under critical,
// so it settles with a hint of overshoot rather than ringing.
const IOS_SPRING = { type: 'CUSTOM_SPRING', easingFunctionSpring: { mass: 1, stiffness: 240, damping: 26 } };
const IOS_BEZIER = { type: 'CUSTOM_CUBIC_BEZIER', easingFunctionCubicBezier: { x1: 0.32, y1: 0.72, x2: 0.0, y2: 1.0 } };

const PAGES = {
  android: { pageId: '14:2', flowName: 'carApp — Android (M3 Expressive)' },
  ios: { pageId: '14:3', flowName: 'carApp — iOS (Liquid Glass)' }
};

const cfg = PAGES[RUN_PAGE];
if (!cfg) throw new Error('RUN_PAGE must be "android" or "ios", got: ' + RUN_PAGE);

const page = await figma.getNodeByIdAsync(cfg.pageId);
await figma.setCurrentPageAsync(page);

const DARK_SUFFIX = ' · dark';
const screen = (name) => {
  const f = page.children.find(n => n.name === name && !n.name.endsWith(DARK_SUFFIX));
  if (!f) throw new Error('screen not found: ' + name);
  return f;
};
// findOne walks in document order, so where a frame holds two same-named nodes (the two
// icon-buttons in the detail app bar, the two vehicle cards on home) this picks the first,
// which is the leading/back one in every case here.
const pick = (frame, name) => {
  const n = frame.findOne(x => x.name === name);
  if (!n) throw new Error('trigger not found: ' + name + ' in ' + frame.name);
  return n;
};

const S = {
  welcome: screen('screen-welcome'),
  home: screen('screen-home'),
  vehicleForm: screen('screen-vehicle-form'),
  detail: screen('screen-detail'),
  fuelForm: screen('screen-fuel-form'),
  settings: screen('screen-settings')
};

// [triggerNodeName, ownerScreen, destinationScreen, transitionKey]
const ANDROID_LINKS = [
  ['btn-google-outlined', S.welcome, S.home, 'forward'],
  ['btn-guest-tonal · pressed', S.welcome, S.home, 'forward'],
  ['vehicle-card · elevated', S.home, S.detail, 'forward'],
  ['fab · extended', S.home, S.vehicleForm, 'forward'],
  ['icon-button · tonal', S.home, S.settings, 'forward'],
  ['icon-button', S.vehicleForm, S.home, 'back'],
  ['button · filled-tonal', S.vehicleForm, S.home, 'back'],
  ['icon-button', S.detail, S.home, 'back'],
  ['fab · extended', S.detail, S.fuelForm, 'forward'],
  ['icon-button', S.fuelForm, S.detail, 'back'],
  ['button · filled', S.fuelForm, S.detail, 'back'],
  ['icon-button', S.settings, S.home, 'back']
];

const IOS_LINKS = [
  ['btn-apple', S.welcome, S.home, 'push'],
  ['btn-google · glass regular', S.welcome, S.home, 'push'],
  ['btn-guest · glass clear', S.welcome, S.home, 'push'],
  ['vehicle-card · content', S.home, S.detail, 'push'],
  ['button · glass prominent', S.home, S.vehicleForm, 'present'],
  ['icon-button · glass', S.home, S.settings, 'present'],
  // Script 17 replaced the text actions with circular glyph buttons; these are its names.
  ['btn-cancel', S.vehicleForm, S.home, 'dismiss'],
  ['btn-save', S.vehicleForm, S.home, 'dismiss'],
  ['back', S.detail, S.home, 'pop'],
  ['button · glass prominent', S.detail, S.fuelForm, 'present'],
  ['btn-cancel', S.fuelForm, S.detail, 'dismiss'],
  ['btn-save', S.fuelForm, S.detail, 'dismiss'],
  ['btn-close', S.settings, S.home, 'dismiss']
];

const transitions = (easing) => ({
  forward: { type: 'SMART_ANIMATE', easing, duration: 0.5 },
  back: { type: 'SMART_ANIMATE', easing, duration: 0.4 },
  push: { type: 'PUSH', direction: 'LEFT', matchLayers: false, easing, duration: 0.35 },
  pop: { type: 'PUSH', direction: 'RIGHT', matchLayers: false, easing, duration: 0.35 },
  present: { type: 'MOVE_IN', direction: 'BOTTOM', matchLayers: false, easing, duration: 0.5 },
  dismiss: { type: 'MOVE_OUT', direction: 'BOTTOM', matchLayers: false, easing, duration: 0.4 }
});

const links = RUN_PAGE === 'android' ? ANDROID_LINKS : IOS_LINKS;

async function wire(easing) {
  const T = transitions(easing);
  // Group by trigger node: a node carries one reactions array, not one per link.
  const byNode = new Map();
  for (const [triggerName, owner, dest, key] of links) {
    const node = pick(owner, triggerName);
    if (!byNode.has(node.id)) byNode.set(node.id, { node, reactions: [] });
    byNode.get(node.id).reactions.push({
      trigger: { type: 'ON_CLICK' },
      actions: [{
        type: 'NODE',
        destinationId: dest.id,
        navigation: 'NAVIGATE',
        transition: T[key],
        preserveScrollPosition: false
      }]
    });
  }
  const wired = [];
  for (const { node, reactions } of byNode.values()) {
    await node.setReactionsAsync(reactions);
    wired.push({ node: node.name, id: node.id, links: reactions.length });
  }
  return wired;
}

let easingUsed, wired;
if (RUN_PAGE === 'ios') {
  try {
    wired = await wire(IOS_SPRING);
    easingUsed = 'CUSTOM_SPRING';
  } catch (e) {
    wired = await wire(IOS_BEZIER);
    easingUsed = 'CUSTOM_CUBIC_BEZIER (spring rejected: ' + e.message + ')';
  }
} else {
  wired = await wire(M3_EMPHASIZED);
  easingUsed = 'CUSTOM_CUBIC_BEZIER (M3 emphasized)';
}

page.flowStartingPoints = [{ nodeId: S.welcome.id, name: cfg.flowName }];

return { page: cfg.pageId, easingUsed, flowStart: S.welcome.id, wired };
