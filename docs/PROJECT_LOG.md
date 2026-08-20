# Project Log - carApp

> Append-only record of what actually happened in this project: decisions taken, stories completed, problems found, direction changes. It is the fastest way for a new agent or a returning human to learn the current state without reading every normative document.
>
> This log is **history**, not rules. It never overrides `docs/SPECIFICATION.md`, `docs/CONTRACTS.md` or `docs/DECISION_BOARD.md`. If the log and a normative document disagree, the normative document wins and the discrepancy is escalated.

## How to use this log

**Read:** before starting any work, read the three most recent entries. They tell you what was just done, what is in flight and what is blocked.

**Write:** appending an entry is part of the Definition of Done (`AGENTS.md`). Add exactly one entry per completed story, per accepted or changed decision, and per significant event such as a scope change, an incident, a phase gate or a handoff to a different agent.

**Rules:**

- Newest entries at the top, immediately under this section.
- Never edit or delete a past entry. If something was wrong, append a new entry that corrects it and say which entry it corrects.
- Dates are absolute and ISO-8601 (`2026-08-17`), never relative.
- Written in technical English, like every other repository artifact.
- Keep entries short. Link to the PR, the story and the documents rather than re-explaining them.
- Do not put secrets, tokens, personal data or user data in this log.

## Entry template

```markdown
### YYYY-MM-DD — <Short title>

- **Type:** story | decision | milestone | incident | handoff | correction
- **Story / Decision:** `E0-00` / `D-13` / —
- **Author:** human name or agent identifier
- **What changed:** one or two sentences.
- **Why:** the reason, especially if a non-obvious option was rejected.
- **Documents touched:** `docs/CONTRACTS.md §9`, `docs/BACKLOG.md`, …
- **Verification:** commands run, tests added, gates passed.
- **Follow-ups / risks:** anything left open, with an owner if known.
```

---

## Entries

### 2026-08-20 — Welcome screen sign-in options fixed to the closed provider set

- **Type:** decision
- **Story / Decision:** — (no backlog story; owner-directed correction of `F-1`, affects `E2-03`)
- **Author:** Claude Opus 5, on behalf of David Ruiz
- **What changed:** `docs/SPECIFICATION.md §7 F-1` now states that the welcome screen offers the platform's sign-in providers and "Continue without account" in a single step, that there MUST NOT be an intermediate provider-selection screen or a provider-less "Sign in" control, that the screen presents exactly two actions on Android and three on iOS, and that the MVP has no sign-in method beyond anonymous, Google and Apple. `docs/BACKLOG.md` `E2-03` gained two matching acceptance criteria. `README.md` §MVP Scope and `docs/DESIGN.md` §7 were updated to repeat the same rule. The design assets were then corrected to match: `design/figma/02-android-welcome.figma.js` and `07-ios-welcome.figma.js` dropped the generic sign-in button, `15-prototype-motion.figma.js` was re-wired to the provider buttons, and `design/stitch/` was regenerated from them.
- **Why:** both welcome screens shipped a provider-less "Iniciar sesión" button (`btn-signin-filled` on Android, `btn-primary` on iOS) that no product document backs. `AuthProvider` is a closed enum of `ANONYMOUS`, `GOOGLE`, `APPLE` (`docs/CONTRACTS.md §20.3`) and the only permanent sign-in intent is `startPermanentSignIn(provider)` (`§20.10`), so a provider-less button has no intent to invoke. `F-1` step 1 previously read "Welcome screen with 'Sign in' and 'Continue without account'", which also admitted a two-step welcome → provider-picker flow; the owner chose the one-step flow, because it needs no extra screen, matches `E2-03` as a single story, and serves principle P3 "No entry barrier". Email and password, email link, phone or one-time code and other SSO providers were never in the repository and are now stated as excluded rather than merely absent.
- **Documents touched:** `docs/SPECIFICATION.md §7 F-1`, `docs/BACKLOG.md` `E2-03`, `docs/DESIGN.md` §6 and §7, `README.md`, `design/figma/**`, `design/stitch/**`, and this log. `docs/CONTRACTS.md` needed no change: the contracts were already correct and the design contradicted them.
- **Verification:** `grep -rn "Iniciar sesión" design/figma/*.figma.js` returns nothing. Every welcome button maps to a concrete intent; the prototype triggers are the provider buttons rather than the removed ones. All 19 Figma scripts pass a syntax check. Requires human review before merge: `docs/SPECIFICATION.md` is a gated path and "authentication" is a gated topic (`AGENTS.md`).
- **Follow-ups / risks:** the Figma file itself is one pass behind the scripts; re-run `02`, `07`, `13`, `16`, `15` in that order (`design/figma/README.md`). Anonymous account conversion (`F-4`) still has no designed entry point in settings; recorded in `docs/DESIGN.md` §6 and owned by `E2-04`.

### 2026-08-19 — TDD commit and push workflow made a MUST

- **Type:** decision
- **Story / Decision:** — (no backlog story; governance change, owner-directed)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added the `### TDD commit and push workflow` subsection to `docs/SPECIFICATION.md §11`, making the per-phase commit-and-push sequence (red → green → refactoring → PR) a MUST for every TDD story. Each phase is a separate commit and a separate push; phases MUST NOT be combined in a single commit; the PR MUST contain the full cycle in order; the refactoring phase is skipped if no refactoring is needed. `AGENTS.md` Technical Rules gained an explicit reference to the workflow as a MUST unless the owner exempts a story explicitly.
- **Why:** the owner directed that, from this point forward, the TDD process must produce a commit and push per phase (red, green, refactoring) and a PR only after the cycle completes, so the version history reflects the TDD intent and each phase is independently reviewable.
- **Documents touched:** `docs/SPECIFICATION.md §11`, `AGENTS.md`, and this log. No backlog story acceptance criterion changed; the rule applies to all product-code stories going forward.
- **Verification:** `grep -n 'TDD commit and push workflow' docs/SPECIFICATION.md AGENTS.md` confirms the subsection and the cross-reference. Requires human review before merge (gated paths `AGENTS.md` and `docs/SPECIFICATION.md`; gated topic "MVP scope / quality rules").
- **Follow-ups / risks:** applies from the next product-code story onward. `E0-01` was committed before this rule existed and is exempt; the handoff declares the TDD exemption for the KMP scaffold.

### 2026-08-19 — E0-01 KMP Project Bootstrap completed

- **Type:** story
- **Story / Decision:** `E0-01` (`docs/BACKLOG.md:43`)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** created the KMP project skeleton with Android and iOS targets, `:shared` framework named `Shared` (canonical SPM module name), Android host app (`:androidApp`) using Compose, and iOS host app (`iosApp/`) using SwiftUI. The `Greeting` class in `commonMain` is consumed by both hosts — Android shows `Greeting().greet("Android")`, iOS shows `Greeting().greet(platform: "iOS")` via `import Shared`. `gradle/libs.versions.toml` is the single source of dependency versions with minimal build-essential pins (Kotlin 2.0.21, KSP, SKIE 0.10.14, AGP 8.5.2, Compose BOM, coroutines, Gradle 8.9, targetSdk 35); remaining versions are `TBD` for `E0-06`. Gradle wrapper, Kotlin DSL build scripts only, root `plugins` block declaring versions once. iOS Xcode project generated via `xcodegen` with `Shared.framework` (static) embedded and linked. `AndroidManifest.xml` has `android:allowBackup="false"` and no backup/settings-sync surface; no iOS entitlements file.
- **Why:** `E0-01` is the first implementation story and blocks all others. The skeleton proves both platforms consume `commonMain`, identifiers match `docs/identifiers.md` exactly, and no platform backup/settings-sync API surface exists.
- **Documents touched:** `settings.gradle.kts`, `build.gradle.kts`, `gradle.properties`, `gradle/libs.versions.toml`, `gradle/wrapper/`, `shared/build.gradle.kts`, `shared/src/commonMain/kotlin/com/ruizurraca/carapp/Greeting.kt`, `shared/src/commonTest/kotlin/com/ruizurraca/carapp/GreetingTest.kt`, `androidApp/build.gradle.kts`, `androidApp/src/main/AndroidManifest.xml`, `androidApp/src/main/java/com/ruizurraca/carapp/MainActivity.kt`, `iosApp/project.yml`, `iosApp/Info.plist`, `iosApp/carAppApp.swift`, `iosApp/ContentView.swift`, `iosApp/carApp.xcodeproj/`, `docs/E0-01-READY-CHECK.md` (preserved per owner request, to be deleted at story close), `docs/handoff-E0-01.md`, and this log. No normative document changed.
- **Verification:** `./gradlew :shared:allTests` → BUILD SUCCESSFUL (TDD: red phase confirmed `Unresolved reference 'Greeting'` before `Greeting.kt` existed, green after). `./gradlew :androidApp:assembleDebug` → BUILD SUCCESSFUL. `xcodebuild -project iosApp/carApp.xcodeproj -scheme carApp -destination 'platform=iOS Simulator,name=iPhone 17' -configuration Debug build` → BUILD SUCCEEDED; app installs and launches on simulator (PID 56281); binary contains `Shared.Greeting` symbol (`nm` output: `_$sSo14SharedGreetingCABycfC`). All 7 ACs verified. No human review gate (E0-01 is not gated; the Phase 0 gate is E0-07).
- **Follow-ups / risks:** version revalidation by E0-06 (Kotlin 2.0.21, AGP 8.5.2 below current stable 8.7.x, Gradle 8.9, SKIE 0.10.14 warns AGP > 8.5 untested). `xcodegen` is a brew dependency; `project.yml` is the source of truth and the `.xcodeproj` is committed. iOS framework path is hardcoded to `iosSimulatorArm64/debugFramework`; E0-07 MUST switch to XCFramework. `docs/E0-01-READY-CHECK.md` must be deleted when E0-01 closes. TDD exemption declared for KMP scaffold (native UI / wiring, no behavior unit); `Greeting` was written test-first.

### 2026-08-19 — Merged Figma runner added for the two unrun iOS scripts

- **Type:** milestone
- **Story / Decision:** — (no backlog story; non-normative design tooling)
- **Author:** Claude Opus 5 (Claude Code session), on behalf of David Ruiz
- **What changed:** added `design/figma/11-ios-forms-and-settings-merged.figma.js`, a generated merge of `09-ios-forms.figma.js` and `10-ios-settings.figma.js` that creates the same three iOS frames — `screen-vehicle-form`, `screen-fuel-form` and `screen-settings` — in **one** `use_figma` call instead of two. The merge wraps each source script in an async IIFE, so their identically-named top-level declarations (`page`, `F`, `ST`, `glass`, `icon`, `T`) do not collide, and combines the two return values. `design/figma/README.md` gained the script as row `11` of the status table, an explicit warning that running the merged script *and* the originals would duplicate the three frames, a note that `11` is generated and must be regenerated rather than edited, and a `Current blocker` section recording the state as checked on 2026-08-19. Committed as `2f89c39`; this log entry is the commit that follows it.
- **Why:** the redesign is two frames short of complete and blocked purely on Figma quota. The account is Starter tier with a View seat, which caps MCP usage at 20 tool calls per month; the quota is currently exhausted and every read tool returns the rate-limit paywall error. `use_figma` is not on Figma's rate-limit exemption list — only `add_code_connect_map`, `generate_figma_design` and `whoami` are — so the remaining work cannot run until the monthly quota resets or the plan is upgraded to Pro with a Full or Dev seat. On a 20-call budget, spending one call instead of two to finish the redesign is worth a generated file.
- **Documents touched:** `design/figma/11-ios-forms-and-settings-merged.figma.js` (new), `design/figma/README.md`, and this log. No normative document touched and no rule changed; `design/` is design tooling and carries no authority (`AGENTS.md`, `docs/DESIGN.md`).
- **Verification:** the script was reviewed in full before being committed, as it was found uncommitted in the working tree rather than authored in this session. All three scripts (`09`, `10`, `11`) pass `node --check` when wrapped in an async IIFE, matching how `use_figma` wraps them. The merge was proved faithful by diff: lines 8–234 of the merged file are byte-identical to `09-ios-forms.figma.js` and lines 238–417 to `10-ios-settings.figma.js`, the only difference being the four-line header comment of each source, which the merged file replaces with its own. The script has **not** been executed — that is the blocker itself. No product code exists, so no tests, lint, coverage, architecture or contract checks apply.
- **Follow-ups / risks:** three iOS frames remain absent from the live Figma file and the redesign stays incomplete until quota resets or the plan is upgraded. Run either `11` or the pair `09` + `10`, never both: each path creates the same three frames and running both would duplicate them. `11` is generated output — edits belong in `09` and `10`, followed by a regeneration; editing `11` directly would silently diverge it from its sources. The `design/stitch/` equivalents of these three screens are complete and unaffected, so an implementer is not blocked by the Figma quota.

### 2026-08-19 — TDD made compulsory for product code

- **Type:** decision
- **Story / Decision:** — (no backlog story; governance change, owner-directed)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** made test-driven development (TDD) compulsory for product code across the MVP. Two coordinated edits: (1) `AGENTS.md` Technical Rules gained a bullet stating TDD is compulsory, per behavior unit, with the anti-paraguas clause of `docs/SPECIFICATION.md §11`, and that exemptions are limited to the list in that section and MUST be declared in the handoff. (2) `docs/SPECIFICATION.md §11` gained a `Development` row in the non-functional requirements table stating "Test-driven development (TDD) is compulsory for product code, per the rule below", followed by a full `### TDD rule` subsection defining: the behavior unit as the unit of TDD (not the line of code, not the feature); the red-then-green-then-refactor cycle with the "fails for the right reason" requirement; the anti-paraguas clause (a test MUST be specific to the behavior being introduced, not a paraguas test bundling unrelated behaviors); the orthogonality of Kover coverage thresholds (govern the result, not the order); and the closed list of exemptions (native UI, Room schemas/migrations, Firestore rules, Koin wiring/provider integration, architecture-rule fixtures) that still require tests but not written-first, and that MUST be declared in the handoff under "Decisions Made" as a SHOULD deviation.
- **Why:** the owner observed that TDD was not compulsory in the existing rules. The Definition of Done required "relevant tests pass" and CI failed on failing tests, but no rule forced the test-first order; an agent could implement first and add tests after, satisfying the letter of the DoD without the TDD cycle. The owner directed that TDD be made compulsory. The chosen design (behavior-unit TDD with anti-paraguas clause, Kover as orthogonal coverage oracle, closed exemption list with handoff declaration) follows the repo's existing pattern of a brief rule in `AGENTS.md` with the detailed development in the normative `SPECIFICATION.md`, and aligns with the existing SHOULD-deviation-in-handoff mechanism.
- **Documents touched:** `AGENTS.md`, `docs/SPECIFICATION.md §11`, and this log. No backlog story acceptance criterion changed; the rule applies to all product-code stories going forward.
- **Verification:** `grep -n 'TDD\|test-driven' AGENTS.md docs/SPECIFICATION.md` confirms the rule is present in both documents. The `### TDD rule` subsection renders as a valid Markdown heading under `## 11. Non-Functional Requirements`. Requires human review before merge (gated paths `AGENTS.md` and `docs/SPECIFICATION.md`; gated topic "MVP scope / quality rules").
- **Follow-ups / risks:** the first story that must apply the rule is `E0-01` (KMP Project Bootstrap). `E0-01` is largely scaffold/boilerplate; the agent should declare in its handoff which parts were TDD-exempt (likely most of the Gradle/Xcode scaffolding falls under the "native UI / wiring" spirit, though it is not literally in the closed exemption list — if the agent finds scaffolding that is neither product code nor on the exemption list, it MUST escalate rather than silently skip TDD). A future refinement may be needed if the exemption list proves too narrow for pure-scaffolding stories like `E0-01`/`E0-02`.

### 2026-08-19 — Language rule elevated to critical priority in `AGENTS.md`

- **Type:** correction
- **Story / Decision:** — (no backlog story; `AGENTS.md` governance change)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** elevated the Language rule priority in `AGENTS.md` via four coordinated edits. (1) Added a `> **CRITICAL — Language Rule (read before your first reply).**` callout immediately after the entry-point paragraph, summarizing the two rules (conversation in Spanish es-ES, repository artifacts in technical English) and pointing to `## Language` for the full text. (2) Moved the `## Language` section from its previous position as the fifth H2 section (between Document Authority and Owner Decisions) to the second H2 position, immediately after `## Normative Language` and before `## Document Map`, so an agent reading the document in order encounters the rule before any operational content. (3) Hardened the section opening with `**This section is CRITICAL. A violation fails review.**` and added a fourth consequence bullet: "An agent that replies in English when Spanish was required, or writes Spanish into a repository artifact, has violated a MUST and MUST self-correct before continuing." (4) Added an echo of the rule to the two operational checklists that agents actually use: Story Intake gained "the reply language (Spanish es-ES) is confirmed for this story" as a ready-check field, and Definition of Done gained "every chat reply during this story was in Spanish (es-ES); every repository artifact is in English" as a closing bullet.
- **Why:** the Language rule was well-written but structurally buried: it was the fifth H2 section, behind Document Map and Document Authority, and neither the entry-point paragraph nor the Story Intake / Definition of Done checklists mentioned it. An agent that scans the document for operational guidance (DoR, DoD, gates) could skip the rule entirely, and the owner observed this happening in practice. The four edits place the rule where it cannot be missed: first in the callout at the very top, second in section ordering, third in explicit MUST-weight language, and fourth in the two checklists an agent consults when starting and closing a story.
- **Documents touched:** `AGENTS.md`, and this log. No normative rule added or changed: the two language rules (conversation in Spanish, artifacts in English) and the three existing consequences were already present and correct; this change only repositions, strengthens and echoes them.
- **Verification:** `grep -n '^## ' AGENTS.md` confirms the new section order (Normative Language → Language → Document Map → … → Story Intake → Definition of Done). `grep '## Language' AGENTS.md` confirms a single `## Language` section remains. The diff was reviewed in full before commit. Requires human review before merge (gated path `AGENTS.md`).
- **Follow-ups / risks:** none. The change is self-contained within `AGENTS.md`. If a future agent violates the rule, the new fourth consequence bullet makes the violation a MUST breach requiring self-correction, not a stylistic lapse.

### 2026-08-19 — `docs/DESIGN.md` added as the entry point for the design assets

- **Type:** milestone
- **Story / Decision:** — (no backlog story; documentation, non-normative)
- **Author:** Claude Opus 5 (Claude Code session), on behalf of David Ruiz
- **What changed:** added `docs/DESIGN.md`, a non-normative entry point that describes the design in general terms — two design systems for one product, Material 3 Expressive on Android and Liquid Glass on iOS — and indexes every asset in `design/stitch/`: the two platform design systems and the twelve screen descriptions, screen by screen and platform by platform. It also records the constraints and known gaps of the design, and a table mapping each thing a design asset appears to decide to the normative document that actually decides it. `AGENTS.md` gained the document in its "Records and references" map plus an explicit paragraph stating that `design/` is tooling from which no rule may be derived; `README.md` gained a `Design` section and a documentation-table row; both design folder READMEs now link back to `docs/DESIGN.md`. On the owner's decision the same change also added a non-normative design pointer to the four UI stories `E1-07`, `E1-08`, `E1-09` and `E4-01`, a `docs/DESIGN.md` row to the short document map in `docs/DEFINITION.md §3`, a working rule in `docs/CONTRIBUTING.md` stating that a design asset contradicting a normative document is escalated rather than implemented, and a correction of a pre-existing drift in `AGENTS.md`: the map referred to a temporary `docs/AUDIT_GUARDRAILS.md` that no longer exists, while the present `docs/DOCUMENTATION_AUDIT.md` was absent from it. The latter is now listed as a closed, historical, fully-absorbed audit kept only so that the `AUDIT-NN` IDs cited by this log resolve.
- **Why:** the design assets committed on 2026-08-19 were reachable only by browsing `design/`, and nothing in the documentation set pointed at them. An agent assigned `E1-07`, `E1-08`, `E1-09` or `E4-01` had no way to discover that a design description of the screen existed. Placing the index in `docs/` makes it discoverable through the entry point every agent already reads, while the explicit non-normative framing keeps the design from acquiring authority it must not have over behaviour or contracts.
- **Documents touched:** `docs/DESIGN.md` (new), `AGENTS.md`, `README.md`, `docs/DEFINITION.md §3`, `docs/CONTRIBUTING.md`, `docs/BACKLOG.md` (`E1-07`, `E1-08`, `E1-09`, `E4-01`), `design/stitch/README.md`, `design/figma/README.md`, and this log. No normative rule added or changed; the backlog additions are pointers and are explicitly marked non-normative, so no acceptance criterion changed.
- **Verification:** every relative markdown link in the changed documents resolves to an existing path, checked mechanically. The screen index was generated from the actual file names and headings in `design/stitch/`. No product code exists, so no tests, lint, coverage, architecture or contract checks apply.
- **Follow-ups / risks:** touches the gated path `AGENTS.md` and requires human review before merge. Three open items were surfaced while writing the index and are recorded in `docs/DESIGN.md §6` rather than fixed here: the designs have never been audited for WCAG AA contrast or 200% font scaling, which `E4-02` owns and which may force design changes; the screens are drawn in Spanish only, while Spanish and English are both required from day one, so layouts must still be proven against the longer language; and loading, empty, error and the two-step odometer warning states are specified normatively but undrawn. `docs/DOCUMENTATION_AUDIT.md` is now fully absorbed and could be deleted; it was kept because this log cites its `AUDIT-NN` IDs, and deleting it would leave those citations unresolvable. `E3-05` backup status UI has design surface too — the sync chip on screen 02 and the backup row on screen 06 — but was left without a pointer, as the owner's decision covered the four screen-building stories.

### 2026-08-19 — UI redesign to strict platform design systems, with Figma and Stitch assets committed

- **Type:** milestone
- **Story / Decision:** — (no backlog story; design work, non-normative)
- **Author:** Claude Opus 5 (Claude Code session), on behalf of David Ruiz
- **What changed:** the six conceptual screens were redesigned to strict **Material 3 Expressive** (Android) and strict **Liquid Glass** (iOS), and the results committed as `design/` — a new non-normative folder. `design/figma/` holds eleven Figma Plugin API scripts; nine were executed against the live Figma file, producing two token collections (46 M3 variables, 35 Liquid Glass variables) and nine of twelve screens. `design/stitch/` holds the same designs translated into Google Stitch's `DESIGN.md` format plus twelve screen prompts. The original Figma concept boards were left untouched.
- **Why:** the Figma file had no design system at all — zero variables, zero components, every value hardcoded and duplicated across both platform boards, with copy diverged between them for identical functionality. A token foundation was a precondition for applying either design system "strictly". `design/` sits outside `docs/` deliberately, so it acquires no authority over behaviour or contracts.
- **Documents touched:** `design/figma/**` (new), `design/stitch/**` (new), and this log. No normative document touched.
- **Verification:** all eleven Figma scripts syntax-check clean under `node --check` when wrapped in an async IIFE, matching how `use_figma` wraps them. Nine were executed successfully and verified by screenshot. The `design/stitch/` files follow the six-section `DESIGN.md` structure published in `google-labs-code/stitch-skills`. No product code exists, so no tests, lint, coverage, architecture or contract checks apply.
- **Follow-ups / risks:** three iOS screens (`09-ios-forms`, `10-ios-settings`) remain unrun — Figma's Starter plan allows 20 MCP tool calls per month and the quota was exhausted; the scripts are complete and blocked only on quota or a plan upgrade. Free-plan variable collections are capped at one mode, so Light/Dark cannot be expressed as variable modes and no dark theme exists. The `design/stitch/` assets have not been validated against a live Stitch project. The brief's "map integration" and "real-time status updates" were not designed: they appear in no concept screen and adding them would have been the structural change the brief forbade — they need their own story if real.

### 2026-08-18 — Documentation audit AUDIT-16 applied: `AuthToken` gains `issuedAt` for freshness check

- **Type:** correction
- **Story / Decision:** `AUDIT-16` (`docs/DOCUMENTATION_AUDIT.md` §3.1, guardrail)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added an `issuedAt: Instant` field to `AuthToken` in `docs/CONTRACTS.md §20.8`, populated by `TokenProvider`. Updated `§11.5` step 1 to define the freshness check as `AppClock.now() - issuedAt <= FRESH_LOGIN_THRESHOLD_MS`, using the new `issuedAt` field. `E2-02` and `E2-05` MUST test the freshness check.
- **Why:** `§11.5` said the app MUST verify the Firebase ID token is "fresh", meaning "younger than `FRESH_LOGIN_THRESHOLD_MS`". `AuthToken` carried `expiresAt` but no `issuedAt`. "Younger than 5 minutes" is a statement about issuance age, not expiry. An agent cannot compute issuance age from `expiresAt` alone (Firebase tokens have a 1-hour validity, so `expiresAt - 5 min` approximates issuance, but the contract did not state this).
- **Documents touched:** `docs/CONTRACTS.md` (`§20.8`, `§11.5`), and this log.
- **Verification:** documentation-only change. The freshness check will be tested by `E2-02` and `E2-05`; no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md`).
- **Follow-ups / risks:** `TokenProvider` implementations MUST populate `issuedAt`; a fake that omits it will fail the freshness check. All 20 findings of `docs/DOCUMENTATION_AUDIT.md` are now applied (19 closed by direct fix, 1 closed by AUDIT-04 as a duplicate).

### 2026-08-18 — Documentation audit AUDIT-14 applied: `confirmDelete` signatures simplified

- **Type:** correction
- **Story / Decision:** `AUDIT-14` (`docs/DOCUMENTATION_AUDIT.md` §3.1, blocking)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** changed `VehicleListStateHolder.confirmDelete(vehicleId: String, confirmation: Confirmation)` to `confirmDelete(vehicleId: String)` and `FuelEntryListStateHolder.confirmDelete(entryId: String, confirmation: Confirmation)` to `confirmDelete(entryId: String)` in `docs/CONTRACTS.md §20.10`. Added a normative statement that entity deletion is a direct action, not a typed-warning confirmation; pending-sync warnings are surfaced through `UiMessage` before the destructive action, not through `Confirmation`. The `Confirmation` enum is reserved for typed warnings that require an explicit override.
- **Why:** `Confirmation` had four leaves (`OdometerInconsistent`, `DiscardPendingChanges`, `DeleteAccount`, `AdoptExistingAccount`), none of which represented "confirm vehicle deletion" or "confirm fuel-entry deletion". An agent had to either pass an unrelated confirmation or invent a new leaf.
- **Documents touched:** `docs/CONTRACTS.md` (`§20.10`), and this log.
- **Verification:** documentation-only change. The signature change will be exercised by `E1-07`/`E1-08`/`E1-09`; no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md` and gated topic "Swift-facing API surface").
- **Follow-ups / risks:** if a future story requires a typed confirmation before entity deletion (e.g. "this vehicle has N fuel entries, confirm?"), it MUST be added as a new `Confirmation` leaf and the `confirmDelete` signature MUST be revisited. The remaining 1 finding requiring owner decision (`AUDIT-16`) is still open.

### 2026-08-18 — Documentation audit AUDIT-10 applied: `cycleId` column added to `outbox` DDL

- **Type:** correction
- **Story / Decision:** `AUDIT-10` (`docs/DOCUMENTATION_AUDIT.md` §2.1, guardrail)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added a `cycleId TEXT` column to the `outbox` DDL in `docs/TECHNICAL_PLAN.md §6`, populated on every failed attempt. The sync engine reads it only for log correlation; it MUST NOT use it for retry or poison decisions (which read `lastErrorCode` only, per `§9.7`). Updated `docs/CONTRACTS.md §17` to replace the ambiguous "stored in `outbox.lastError` context" wording with "stored in the `outbox.cycleId` column". An `E3-03` migration test MUST verify the column is populated on failure and NULL on success.
- **Why:** `§17` said `cycleId` is "stored in `outbox.lastError` context", but the outbox DDL had only `lastError TEXT` and `lastErrorCode TEXT`; no `cycleId` column existed and the serialization format was undefined. `§9.7` says `lastError` is debug/UI-only and MUST NOT be read by the sync engine, so the engine cannot parse `cycleId` out of it.
- **Documents touched:** `docs/TECHNICAL_PLAN.md` (`§6`), `docs/CONTRACTS.md` (`§17`), and this log.
- **Verification:** documentation-only change. The migration test will be exercised by `E3-03`; no product code exists yet. Requires human review before merge (gated paths `docs/TECHNICAL_PLAN.md` and `docs/CONTRACTS.md`).
- **Follow-ups / risks:** the `cycleId` column is nullable (NULL on success, populated on failure); a future query that filters by `cycleId` MUST handle NULL. The remaining 2 findings requiring owner decision (`AUDIT-14`, `AUDIT-16`) are still open.

### 2026-08-18 — Documentation audit AUDIT-25 applied: `SyncController.retryFailed()` error leaves defined

- **Type:** correction
- **Story / Decision:** `AUDIT-25` (`docs/DOCUMENTATION_AUDIT.md` §5.1, guardrail)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added a normative statement in `docs/CONTRACTS.md §20.7` that `SyncController.retryFailed()` returns `Err(PersistenceError.TransactionFailed)` if the reset transaction fails; otherwise `Ok(Unit)`. It MUST NOT return `SyncError` or `RemoteError` leaves because it performs no remote work. An `E3-03` fixture MUST assert the only failure path is local-transaction failure.
- **Why:** `SyncController.retryFailed()` returns `Outcome<Unit, AppError>`, but the `SyncController` contract had no enumeration of the `AppError` leaves it may return. An agent implementing `E3-03` could return `PersistenceError.TransactionFailed`, `SyncError.RemoteUnavailable`, or nothing.
- **Documents touched:** `docs/CONTRACTS.md` (`§20.7`), and this log.
- **Verification:** documentation-only change. The fixture will be exercised by `E3-03`; no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md` and gated topic "error taxonomy").
- **Follow-ups / risks:** all automatic findings of `docs/DOCUMENTATION_AUDIT.md` have been applied. The 3 findings requiring owner decision (`AUDIT-10`, `AUDIT-14`, `AUDIT-16`) are still open.

### 2026-08-18 — Documentation audit AUDIT-24 applied: `VehicleListItemUi.deleted` documented as always-false in MVP

- **Type:** correction
- **Story / Decision:** `AUDIT-24` (`docs/DOCUMENTATION_AUDIT.md` §5.1, guardrail)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added a normative statement in `docs/CONTRACTS.md §20.10` that the MVP `VehicleListStateHolder` calls `observeVehicles(includeDeleted = false)`; `VehicleListItemUi.deleted` is present for future/debug use and is always `false` in the MVP list. A debug screen (referenced by `E3-03`) MAY call `observeVehicles(includeDeleted = true)` outside the state holder. An `E1-07` fixture MUST assert the production list never contains `deleted = true`.
- **Why:** `VehicleListItemUi` exposes `deleted: Boolean`, but `VehicleListStateHolder` had no intent that sets `includeDeleted = true` on `observeVehicles`. If `includeDeleted` is always `false`, the `deleted` flag is always `false` and the field is dead. The contract did not say whether the list ever includes deleted vehicles.
- **Documents touched:** `docs/CONTRACTS.md` (`§20.10`), and this log.
- **Verification:** documentation-only change. The fixture will be exercised by `E1-07`; no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md` and gated topic "Swift-facing API surface").
- **Follow-ups / risks:** all automatic findings of `docs/DOCUMENTATION_AUDIT.md` have been applied. The 3 findings requiring owner decision (`AUDIT-10`, `AUDIT-14`, `AUDIT-16`) are still open.

### 2026-08-18 — Documentation audit AUDIT-23 applied: `setFuelType` documented as MVP-hidden

- **Type:** correction
- **Story / Decision:** `AUDIT-23` (`docs/DOCUMENTATION_AUDIT.md` §5.1, drift)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added a normative statement in `docs/CONTRACTS.md §20.10` that `VehicleFormUiState.fuelType` is present for round-trip fidelity and defaults to `GASOLINE`; `VehicleFormStateHolder.setFuelType` exists for testability and future use, but the MVP UI MUST NOT render a `fuelType` selector (`SPECIFICATION.md §7 F-2`, `§5.1`, decision `D-4`). An `E1-07` acceptance criterion MUST assert no `fuelType` control is rendered, while the field round-trips on save.
- **Why:** `VehicleFormUiState.fuelType: FuelType` and `VehicleFormStateHolder.setFuelType(value: FuelType)` are declared in `§20.10`, but `SPECIFICATION.md §7 F-2` says "`fuelType` is not exposed in the MVP UI" and `§5.1` says it is "Metadata only". The state holder exposes a setter for a field the UI must not show. An agent could either render a selector (violating F-2) or hide the setter (violating the contract signature).
- **Documents touched:** `docs/CONTRACTS.md` (`§20.10`), and this log.
- **Verification:** documentation-only change. The acceptance criterion will be exercised by `E1-07`; no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md` and gated topic "Swift-facing API surface").
- **Follow-ups / risks:** the remaining 1 finding of `docs/DOCUMENTATION_AUDIT.md` is still open.

### 2026-08-18 — Documentation audit AUDIT-22 applied: `setUserProperties` trigger cadence defined

- **Type:** correction
- **Story / Decision:** `AUDIT-22` (`docs/DOCUMENTATION_AUDIT.md` §4.2, guardrail)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added a normative cadence rule in `docs/CONTRACTS.md §16.1`: `setUserProperties` is called once on analytics opt-in, and thereafter on every successful vehicle or fuel-entry create/delete, from the presentation layer. It MUST NOT be called from domain or data. Buckets are computed from the current list size. An `E3-09` fixture MUST assert the call cadence.
- **Why:** `AnalyticsTracker.setUserProperties` carries `vehicleCountBucket` and `entryCountBucket`, but the contract did not state when it is called. Two agents could implement "set on every write" (chatty) or "set on app foreground only" (stale buckets).
- **Documents touched:** `docs/CONTRACTS.md` (`§16.1`), and this log.
- **Verification:** documentation-only change. The fixture will be exercised by `E3-09`; no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md`).
- **Follow-ups / risks:** the remaining 2 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-21 applied: `CrashReporter.recordNonFatal` trigger policy defined

- **Type:** correction
- **Story / Decision:** `AUDIT-21` (`docs/DOCUMENTATION_AUDIT.md` §4.1, guardrail)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added a `recordNonFatal` trigger policy in `docs/CONTRACTS.md §20.3.1`: it MUST be called for every `UnexpectedError` and for every `SyncError.Poisoned` / `FAILED_POISONED` transition; it MUST NOT be called for validation warnings, expected `AuthError` leaves (`Cancelled`, `RequiresRecentLogin`, `CredentialAlreadyInUse`), or connectivity-only `RemoteError` codes. `fields` follows the same allowlist as `Logger` (`§17`). An `E3-03` / `E4-04` fixture MUST assert the call sites.
- **Why:** `CrashReporter.recordNonFatal` is the only non-fatal API, but no documented flow called it. `§17` says `Logger` is not a crash-reporting API. The boundary between "log this error" and "report this as a non-fatal crash" was unspecified. An agent could report every `UnexpectedError` as a non-fatal, or never report anything.
- **Documents touched:** `docs/CONTRACTS.md` (`§20.3.1`), and this log.
- **Verification:** documentation-only change. The fixture will be exercised by `E3-03` / `E4-04`; no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md`).
- **Follow-ups / risks:** the remaining 3 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-20 applied: `§16.1` allows failure event tracking

- **Type:** correction
- **Story / Decision:** `AUDIT-20` (`docs/DOCUMENTATION_AUDIT.md` §4.1, guardrail)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** rewrote the tracking rule in `docs/CONTRACTS.md §16.1` to: "Shared presentation or application-level orchestration may track product events after a use case returns `Ok` **or** `Err`, provided the event payload carries no user data. Success and failure events are both permitted; the closed `AnalyticsEvent` hierarchy is the sole source of allowed events." A fixture MUST assert failure events are emitted from presentation, not domain or data.
- **Why:** `§16.1` said "track product events **after successful use case results**", but `AnalyticsEvent` includes `AccountConversionFailed`, `AccountDeletionFailed` and `SyncStatusChanged` — failure/state events, not success events. The rule contradicted the existence of failure leaves. An agent could either omit failure tracking (following the rule) or emit it (following the type), and neither was provably wrong.
- **Documents touched:** `docs/CONTRACTS.md` (`§16.1`), and this log.
- **Verification:** documentation-only change. The fixture will be exercised by `E3-09`; no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md` and gated topic "logging and privacy rules").
- **Follow-ups / risks:** the remaining 4 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-18 applied: `AuthError` to analytics bucket enum mappings defined

- **Type:** correction
- **Story / Decision:** `AUDIT-18` (`docs/DOCUMENTATION_AUDIT.md` §4.1, guardrail)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added two normative mapping tables in `docs/CONTRACTS.md §20.9`: `AuthError -> ConversionFailureReason` (`Cancelled -> CANCELLED`, `CredentialAlreadyInUse -> CREDENTIAL_IN_USE`, `NetworkUnavailable -> NETWORK`, `UidWouldChange -> UID_WOULD_CHANGE`, everything else -> `UNKNOWN`) and `AuthError -> DeletionFailureReason` (`RequiresRecentLogin -> REQUIRES_RECENT_LOGIN`, `AccountDeletionRemoteFailed -> REMOTE_FAILED`, `NetworkUnavailable -> NETWORK`, everything else -> `UNKNOWN`). Unit tests MUST assert exhaustiveness of both mappings.
- **Why:** `AnalyticsEvent.AccountConversionFailed(reason: ConversionFailureReason)` and `AccountDeletionFailed(reason: DeletionFailureReason)` use analytics-specific bucket enums, but there was no defined mapping from `AuthError` to those buckets. `AuthError.PermissionDenied` had no corresponding bucket in `ConversionFailureReason`; two agents could bucket `PermissionDenied` as `UNKNOWN` or as `CREDENTIAL_IN_USE`.
- **Documents touched:** `docs/CONTRACTS.md` (`§20.9`), and this log.
- **Verification:** documentation-only change. The unit tests will be exercised by `E2-04`/`E2-05`/`E3-09`; no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md` and gated topic "error taxonomy / analytics").
- **Follow-ups / risks:** the remaining 5 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-17 applied: `SyncStatus` convergence rule for state holders

- **Type:** correction
- **Story / Decision:** `AUDIT-17` (`docs/DOCUMENTATION_AUDIT.md` §3.2, cosmetic)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added a normative statement in `docs/CONTRACTS.md §14` that every state holder exposing `SyncStatus` (`VehicleListUiState.syncStatus`, `FuelEntryListUiState.syncStatus` and `SyncUiState.status`) observes the same `SyncController.status` flow; values are eventually consistent and converge. List state holders MUST NOT independently compute `SyncStatus`; they MUST relay the single `SyncController.status` source. A unit test MUST assert that two holders fed by the same `SyncController` converge.
- **Why:** `SyncStatus` is embedded in three `UiState` classes, all derived from the same `SyncController.status` flow, but the contract did not state whether the three emissions are guaranteed to agree at any instant, or whether list state holders may snapshot a stale value while `SyncStateHolder` holds the latest.
- **Documents touched:** `docs/CONTRACTS.md` (`§14`), and this log.
- **Verification:** documentation-only change. The unit test will be exercised by the presentation stories; no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md`).
- **Follow-ups / risks:** the remaining 6 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-15 applied: `SessionStateHolder` gains F-4 conversion intents

- **Type:** correction
- **Story / Decision:** `AUDIT-15` (`docs/DOCUMENTATION_AUDIT.md` §3.1, blocking)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added `fun startAccountConversion(provider: AuthProvider)` and `fun confirmAccountConversion(confirmation: Confirmation)` to `SessionStateHolder` in `docs/CONTRACTS.md §20.10`. Added a normative statement that `startAccountConversion` calls `AuthClient.linkCredential` (not `signInWithCredential`), preserves the UID, and maps `AuthError.UidWouldChange` / `AuthError.CredentialAlreadyInUse` to the F-4 collision flow. Updated `E2-04` acceptance criteria to reference the new intents.
- **Why:** `SPECIFICATION.md §7 F-4` (Anonymous Account Conversion) is a distinct flow: from settings, the user links Google or Apple credentials to the current anonymous identity. `SessionStateHolder` had no intent for it; `startPermanentSignIn` signs in, it does not link to an existing anonymous identity. An agent implementing `E2-04` had no contract entry point.
- **Documents touched:** `docs/CONTRACTS.md` (`§20.10`), `docs/BACKLOG.md` (E2-04), and this log.
- **Verification:** documentation-only change. The intents will be exercised by `E2-04`; no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md` and gated topic "Swift-facing API surface").
- **Follow-ups / risks:** the remaining 7 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-13 applied: `SessionPhase` `LOCAL -> DELETING` semantics clarified

- **Type:** correction
- **Story / Decision:** `AUDIT-13` (`docs/DOCUMENTATION_AUDIT.md` §3.1, guardrail)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added a normative statement in `docs/CONTRACTS.md §20.10` clarifying that from `LOCAL`, `DELETING` means "clearing local data only" (no server operation, because there is no Firebase Auth account); from `ANONYMOUS` or `PERMANENT`, `DELETING` means "running the `D-23` server operation then clearing local data". The `DELETING -> UNKNOWN` transition is followed by `UNKNOWN -> SIGNED_OUT` only after the local-data clear completes. `E2-05` MUST test both paths.
- **Why:** the `SessionPhase` transition table allows `LOCAL -> DELETING`, but for a `LOCAL_OWNER` session there is no Firebase Auth account, so the `D-23` server operation cannot run. `SPECIFICATION.md §7 F-5` says the anonymous equivalent is "delete local data". The contract did not distinguish the two meanings of `DELETING`.
- **Documents touched:** `docs/CONTRACTS.md` (`§20.10`), and this log.
- **Verification:** documentation-only change. The tests will be exercised by `E2-05`; no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md`).
- **Follow-ups / risks:** the remaining 8 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-12 applied: `FuelEntryListItemUi` gains `hasMissedEntries` and `odometerInconsistent`

- **Type:** correction
- **Story / Decision:** `AUDIT-12` (`docs/DOCUMENTATION_AUDIT.md` §2.2, guardrail)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added `hasMissedEntries: Boolean` and `odometerInconsistent: Boolean` to `FuelEntryListItemUi` in `docs/CONTRACTS.md §20.10`. Updated `E1-08` and `E1-09` acceptance criteria to render the flags on every row, including partial refuels where `invalidReason = EndEntryNotFullTank`.
- **Why:** `invalidReason: ConsumptionInvalidReason?` covers `MissedEntriesInSegment` and `InconsistentOdometerInSegment` for full-tank entries, but for a partial (non-full-tank) entry `invalidReason = EndEntryNotFullTank` and the underlying `hasMissedEntries`/`odometerInconsistent` flags are lost. The UI cannot show a "missed refuels" or "inconsistent odometer" indicator on a partial row, even though those flags are user-visible per `SPECIFICATION.md §5.2` and F-3.
- **Documents touched:** `docs/CONTRACTS.md` (`§20.10`), `docs/BACKLOG.md` (E1-08, E1-09), and this log.
- **Verification:** documentation-only change. A fixture proving a partial entry with `hasMissedEntries = true` surfaces the flag on the Swift side will be exercised by `E1-08`/`E1-09`; no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md` and gated topic "Swift-facing API surface").
- **Follow-ups / risks:** the remaining 9 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-11 applied: `RemoteCursor.INITIAL` null exemption clarified

- **Type:** correction
- **Story / Decision:** `AUDIT-11` (`docs/DOCUMENTATION_AUDIT.md` §2.1, guardrail)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added a normative statement in `docs/CONTRACTS.md §20.7` that `RemoteCursor.INITIAL` is a sentinel representing "no cursor stored yet" and is never passed to `RemoteSyncSource.pullChanges`; the sync engine materialises the first page cursor as `(overlapSince, "")`. Updated `§9.4` to clarify that the `null` prohibition applies to cursors passed to `startAt`/`startAfter`; the `INITIAL` sentinel is exempt because it is translated before reaching Firestore. An `E3-03` test MUST prove `INITIAL` never reaches `RemoteSyncSource`.
- **Why:** `RemoteCursor.INITIAL` has `lastDocumentId = null`, but `§9.4` states "`null` MUST NOT be used as a cursor component." A literal reading makes `INITIAL` illegal. An agent could "fix" `INITIAL` by setting `lastDocumentId = ""`, which would then be passed to `startAfter` on a resumed cycle and produce wrong pagination.
- **Documents touched:** `docs/CONTRACTS.md` (`§20.7`, `§9.4`), and this log.
- **Verification:** documentation-only change. The test will be exercised by `E3-03`; no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md`).
- **Follow-ups / risks:** the remaining 10 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-09 applied: `quarantine` DDL `reason` CHECK constraint added

- **Type:** correction
- **Story / Decision:** `AUDIT-09` (`docs/DOCUMENTATION_AUDIT.md` §2.1, guardrail)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added `CHECK (reason IN ('UnsupportedSchemaVersion','MalformedPayload'))` to the `quarantine` DDL in `docs/TECHNICAL_PLAN.md §6`, matching the closed `QuarantineReason` enum (`docs/CONTRACTS.md §20.7`). An `E3-03` migration test MUST prove the constraint rejects an unknown reason.
- **Why:** the DDL stored `reason TEXT NOT NULL` but `QuarantineReason` is a closed enum with two leaves. Without a CHECK constraint, unlike `outbox.entityType` which has one, an agent could persist an arbitrary reason string.
- **Documents touched:** `docs/TECHNICAL_PLAN.md` (`§6`), and this log.
- **Verification:** documentation-only change. The migration test will be exercised by `E3-03`; no product code exists yet. Requires human review before merge (gated path `docs/TECHNICAL_PLAN.md`).
- **Follow-ups / risks:** if a new `QuarantineReason` leaf is added, the CHECK constraint MUST be updated in the same change. The remaining 11 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-08 applied: `sync_cursor` table DDL defined

- **Type:** correction
- **Story / Decision:** `AUDIT-08` (`docs/DOCUMENTATION_AUDIT.md` §2.1, guardrail)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added the exact DDL for the `sync_cursor` table to `docs/TECHNICAL_PLAN.md §6`: `CREATE TABLE sync_cursor (entityType TEXT NOT NULL CHECK (entityType IN ('VEHICLE','FUEL_ENTRY')), lastServerUpdatedAt INTEGER NOT NULL, lastDocumentId TEXT NOT NULL, PRIMARY KEY (entityType))`. `lastDocumentId` is `TEXT NOT NULL` because `docs/CONTRACTS.md §9.4` forbids `null` as a cursor component; the `RemoteCursor.INITIAL` sentinel is never stored as a row. An `E1-01` migration test MUST verify the constraint rejects an unknown `entityType`.
- **Why:** `§6` listed `sync_cursor` as a table and `SPECIFICATION.md §9.2` named its columns, but no DDL with column types, nullability, primary key, or `entityType` CHECK constraint was provided. An agent implementing `E1-01` would have to invent the column types.
- **Documents touched:** `docs/TECHNICAL_PLAN.md` (`§6`), and this log.
- **Verification:** documentation-only change. The migration test will be exercised by `E1-01`; no product code exists yet. Requires human review before merge (gated path `docs/TECHNICAL_PLAN.md`).
- **Follow-ups / risks:** the remaining 12 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-07 applied: `SyncStatus -> SyncStatusCategory` mapping defined

- **Type:** correction
- **Story / Decision:** `AUDIT-07` (`docs/DOCUMENTATION_AUDIT.md` §1.2, guardrail)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added a normative mapping in `docs/CONTRACTS.md §20.9` from `SyncStatus` to `SyncStatusCategory` using the same connectivity-code rule as `§9.9`: `Idle -> IDLE`, `Syncing -> SYNCING`, `Pending -> PENDING`, and `Failed -> FAILED` only when at least one counted row has `lastErrorCode` not in `CONNECTIVITY_ERROR_CODES`; otherwise `Failed -> PENDING`. A unit test MUST assert the mapping under all combinations.
- **Why:** there was no defined mapping from the sealed `SyncStatus` to the flat `SyncStatusCategory` enum used by `AnalyticsEvent.SyncStatusChanged`. An agent could map `Failed(_, _) -> FAILED` verbatim, reporting a failure event for a connectivity-only condition that `§9.9` says is `Pending`.
- **Documents touched:** `docs/CONTRACTS.md` (`§20.9`), and this log.
- **Verification:** documentation-only change. The unit test will be exercised by `E0-08` / `E3-09`; no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md` and gated topic "error taxonomy / analytics").
- **Follow-ups / risks:** the remaining 13 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-06 applied: Swift-facing `SyncStateHolder.requestSync` trigger surface restricted

- **Type:** correction
- **Story / Decision:** `AUDIT-06` (`docs/DOCUMENTATION_AUDIT.md` §1.1, guardrail)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added a normative statement in `docs/CONTRACTS.md §20.10` that `SyncStateHolder.requestSync` is intended for user-initiated sync only. The Swift-facing surface MUST pass `SyncTrigger.PullToRefresh` (and `SyncTrigger.AppForeground` if the platform emits it from a lifecycle hook). `PostWriteDebounce`, `ConnectivityRecovered` and `Periodic` are fired exclusively by `SyncTriggerAdapter` from platform wiring and MUST NOT be invoked from Swift UI code, to avoid duplicating `BGTaskScheduler`/`WorkManager` wiring and bypassing the single-`SyncController` invariant of `§9.1`. A Konsist fixture MUST ban those three leaves from any `iosMain` call site of `SyncStateHolder.requestSync`.
- **Why:** exposing system triggers to Swift invites the iOS layer to fire them manually, duplicating platform wiring and bypassing the single-`SyncController` invariant. The audit proposed a single solution.
- **Documents touched:** `docs/CONTRACTS.md` (`§20.10`), and this log.
- **Verification:** documentation-only change. The Konsist fixture will be exercised by `E3-08`; no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md` and gated topic "Swift-facing API surface").
- **Follow-ups / risks:** if the iOS layer ever needs to emit `AppForeground` from a lifecycle hook, that leaf remains permitted. The remaining 14 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-05 applied: removed `:core:sync -> :core:auth` dependency edge

- **Type:** correction
- **Story / Decision:** `AUDIT-05` (`docs/DOCUMENTATION_AUDIT.md` §1.1, guardrail)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** removed `:core:auth` from the allowed-dependency list of `:core:sync` in `docs/TECHNICAL_PLAN.md §4`, moving it to the forbidden list. Updated `docs/SPECIFICATION.md §8.3` rule 5 to state that `:core:sync` depends on `:core:model`, `:core:common` and `:core:database`, never on `:core:auth` and never on `:integration:*`, and that token handling lives entirely in `RemoteSyncSource`. Added an explanatory note in `docs/CONTRACTS.md §10` that the sync engine never calls `AuthClient` or `TokenProvider`; the `AuthExpired` state-machine transition is sync-internal and re-authentication is delegated to the session/presentation layer. Added a failing fixture to `E0-04` asserting that `:core:sync` does not depend on `:core:auth` and does not reference `AuthClient` or `TokenProvider`.
- **Why:** the `:core:sync -> :core:auth` edge was allowed by the `§4` table but no documented flow uses it: `RemoteSyncSource` (`§10`) handles token refresh on `Unauthenticated` and retries once before mapping to `RemoteError.Unauthenticated`; the sync engine only consumes the resulting `RemoteError`. The edge was dead coupling. The owner chose Option B (eliminate the edge) over Option A (justify it by documenting a `TokenProvider.getIdToken` call on the `AuthExpired` retry path), because the contracts already delegate token handling to the integration layer and aligning `:core:sync` with the "feature data MUST NOT depend on `:core:auth`" rule is cleaner.
- **Documents touched:** `docs/TECHNICAL_PLAN.md` (`§4`), `docs/SPECIFICATION.md` (`§8.3` rule 5), `docs/CONTRACTS.md` (`§10`), `docs/BACKLOG.md` (E0-04), and this log.
- **Verification:** documentation-only change. The new fixture will be exercised by `E0-04`; no product code exists yet. Requires human review before merge (gated paths `docs/TECHNICAL_PLAN.md` and `docs/SPECIFICATION.md`, gated topic "module boundaries and dependency rules").
- **Follow-ups / risks:** if a future story needs `:core:sync` to call `TokenProvider` directly (e.g. to force a refresh before a critical push without going through `RemoteSyncSource`), the edge MUST be re-added with a documented justification in `§10` or `§7`. The remaining 15 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-04 applied: `contract-check` ignored-set extended with Room-generated types

- **Type:** correction
- **Story / Decision:** `AUDIT-04` (`docs/DOCUMENTATION_AUDIT.md` §1.1, guardrail); also closes `AUDIT-19` (`docs/DOCUMENTATION_AUDIT.md` §5.2, guardrail), which is the same finding described from the `AppDatabase`/`DatabaseFactory` angle.
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** extended the `contract-check` assertion 1 ignored-identifier set in `docs/CONTRACTS.md §18` with an explicit "Room-generated types owned by `:core:database`" category covering `AppDatabase`, Room `Dao` supertypes, and `@Entity`-generated row classes. The extension states that these types are allowed only in `:core:database` signatures and in `DatabaseFactory` (`§20.3.2`); any appearance in `:core:common`, `:core:sync`, feature `domain` or the `:shared` public API remains a violation. Added a matching failing fixture to the `E0-04` acceptance criteria: the check does not flag `AppDatabase` in a `:core:database` or `DatabaseFactory` signature, but does flag it elsewhere.
- **Why:** the `DatabaseFactory` move in `AUDIT-03` introduced `AppDatabase` (a Room-generated type not declared in `§20`) into a `§20.3.2` code block; assertion 1 would flag it as undeclared without this extension. The owner accepted the audit's single proposed solution verbatim. `AUDIT-19` is the same issue viewed from the `DatabaseFactory.create(): AppDatabase` signature in `§20.3`, so it is closed by the same change.
- **Documents touched:** `docs/CONTRACTS.md` (`§18`), `docs/BACKLOG.md` (E0-04), and this log.
- **Verification:** documentation-only change. The fixture will be exercised by `contract-check` (implemented by `E0-05`); no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md` and gated topic "Swift-facing API surface / module boundaries").
- **Follow-ups / risks:** if Room-generated types ever need to appear outside `:core:database` and `DatabaseFactory`, this rule MUST be revisited. The remaining 16 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-03 applied: `DatabaseFactory` moved to `:core:database`

- **Type:** correction
- **Story / Decision:** `AUDIT-03` (`docs/DOCUMENTATION_AUDIT.md` §1.1, blocking)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** moved `interface DatabaseFactory { fun create(): AppDatabase }` out of `§20.3` (`:core:common`) into a new `§20.3.2 Database types — :core:database`, and declared `AppDatabase` as the Room-generated type owned by `:core:database`. Added a note in `§11.6` that `AppGraphDependencies.databaseFactory` imports `DatabaseFactory` from `:core:database`. Added a failing fixture to `E0-04` asserting that `:core:common` references neither `AppDatabase` nor `DatabaseFactory`, and that both types may appear only in `:core:database`, `:core:testing` fakes and the `AppGraphDependencies` field of `:shared`.
- **Why:** `DatabaseFactory`'s return type `AppDatabase` is a Room-generated type owned by `:core:database`, but the interface lived in `:core:common`, which is forbidden from depending on Room (`docs/TECHNICAL_PLAN.md §4`). The dependency-rule table row added by `AUDIT-01` now allows `:core:testing` to depend on `:core:database`, so the fake can be provided by `:core:testing`. The audit proposed a single solution; the owner accepted it verbatim.
- **Documents touched:** `docs/CONTRACTS.md` (`§20.3`, new `§20.3.2`, `§11.6`), `docs/BACKLOG.md` (E0-04), and this log.
- **Verification:** documentation-only change. The `contract-check` ignored-set extension for Room-generated types (audit findings 4 and 19) is still pending and will be applied when those findings are processed; until then the new `§20.3.2` code block references `AppDatabase`, which `contract-check` assertion 1 would currently flag. Requires human review before merge (gated path `docs/CONTRACTS.md` and gated topic "module boundaries").
- **Follow-ups / risks:** `AUDIT-04` and `AUDIT-19` MUST extend the `contract-check` ignored-identifier set with "Room-generated types owned by `:core:database`" so `AppDatabase` and `DatabaseFactory` signatures do not trip assertion 1. The remaining 17 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-02 applied: `AuthProvider` moved to `:core:common`

- **Type:** correction
- **Story / Decision:** `AUDIT-02` (`docs/DOCUMENTATION_AUDIT.md` §1.1, blocking)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** moved the `AuthProvider` enum (`ANONYMOUS, GOOGLE, APPLE`) from `§20.8` (`:core:auth`, Phase 2) to `§20.3` (`:core:common`, Phase 0). `:core:auth` now imports it from `:core:common` instead of declaring it. Added `contract-check` assertion 18 asserting that `AuthProvider` is declared in `§20.3` before any reference in `§20.8` (`:core:auth`) or `§20.9` (`:core:analytics`). No backlog or phase change: `E0-08` (Phase 0) and `E2-01` (Phase 2) keep their assignments.
- **Why:** `AnalyticsEvent.PermanentSignInSelected(val provider: AuthProvider)` in `§20.9` is required by `E0-08` (Phase 0), but `AuthProvider` lived in `:core:auth`, created only in Phase 2 (`E2-01`). A Phase 0 module cannot compile against a Phase 2 module. `AuthProvider` is a pure enum referenced by two modules, so it belongs in `:core:common` alongside `SyncTrigger` and `LogLevel`. The owner chose Option A (move to `:core:common`) over moving it to `:core:model` (mixes identity with business vocabulary) or moving `:core:analytics` to Phase 2 (reorders the whole plan for one enum).
- **Documents touched:** `docs/CONTRACTS.md` (`§20.3`, `§20.8`, `§18`), and this log.
- **Verification:** documentation-only change. The new assertion 18 will be exercised by `contract-check` (implemented by `E0-05`); no product code exists yet. Requires human review before merge (gated path `docs/CONTRACTS.md` and gated topic "Swift-facing API surface / module boundaries").
- **Follow-ups / risks:** if `AuthProvider` gains non-enum semantics later it MUST stay a pure enum on the `:core:common` surface, since both `:core:analytics` and `:core:auth` depend on it. The remaining 18 findings of `docs/DOCUMENTATION_AUDIT.md` are still open.

### 2026-08-18 — Documentation audit AUDIT-01 applied: dependency-rule rows for `:core:auth`, `:core:analytics`, `:core:testing`

- **Type:** correction
- **Story / Decision:** `AUDIT-01` (`docs/DOCUMENTATION_AUDIT.md` §1.1, blocking)
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** added three explicit rows to the `docs/TECHNICAL_PLAN.md §4` dependency-rule table for `:core:auth`, `:core:analytics` and `:core:testing`, which previously had no enforceable rule even though they appear in the canonical module inventory (`docs/CONTRACTS.md §1.1`). Added an explanatory paragraph pinning the `:core:testing` platform-API permission to `expect`/`actual` test doubles only (`docs/CONTRACTS.md §15.1`), keeping its `commonMain` public surface Kotlin-pure. Added matching failing fixtures to the `E0-04` acceptance criteria (one per new row). The architecture check generated from that table can now enforce the boundaries of all three modules.
- **Why:** the architecture check is generated from the `§4` table, so the three modules were previously unenforceable; an agent could make `:core:auth` depend on `:feature:*` or `:integration:*` and the check would not fire. The owner accepted the audit's single proposed solution verbatim and the agent's interpretation that `:core:testing` forbids platform APIs only in its `commonMain` public surface (permitted in `expect`/`actual` test doubles).
- **Documents touched:** `docs/TECHNICAL_PLAN.md §4`, `docs/BACKLOG.md` (E0-04), and this log.
- **Verification:** documentation-only change. The new architecture rules and fixtures will be exercised by `E0-04`; no product code exists yet. Requires human review before merge (gated path `docs/TECHNICAL_PLAN.md` and gated topic "module boundaries and dependency rules").
- **Follow-ups / risks:** the `:core:testing` platform-API carve-out relies on `§15.1` boundaries; if a future change loosens `§15.1`, this row's fixture wording MUST be re-checked. The remaining 19 findings of `docs/DOCUMENTATION_AUDIT.md` are still open and will be processed one by one with their own project-log entries.

### 2026-08-18 — Second documentation audit performed

- **Type:** milestone
- **Story / Decision:** `docs/DOCUMENTATION_AUDIT.md`
- **Author:** opencode agent (glm-5.2:cloud), on behalf of David Ruiz
- **What changed:** a fresh Senior KMP Architecture audit of the current documentation state (after the prior 56-finding audit was folded into the normative docs). The audit document was rewritten with 20 new findings: 5 blocking, 13 guardrails, 1 drift, 1 cosmetic. Top blockers: missing dependency-rule rows for `:core:auth`/`:core:analytics`/`:core:testing`; `:core:analytics` (Phase 0) referencing `AuthProvider` from `:core:auth` (Phase 2); `DatabaseFactory` in `:core:common` returning a `:core:database` type; `SessionStateHolder` missing an F-4 conversion intent; `Confirmation` enum missing deletion leaves for `confirmDelete`.
- **Why:** the prior audit closure left residual gaps that would still let two competent agents produce incompatible implementations; this pass targets those.
- **Documents touched:** `docs/DOCUMENTATION_AUDIT.md`, and this log.
- **Verification:** documentation-only change; cross-referenced findings against the current `AGENTS.md`, `SPECIFICATION.md`, `CONTRACTS.md`, `TECHNICAL_PLAN.md`, `DECISION_BOARD.md`, `BACKLOG.md`, `SECURITY.md`, `identifiers.md` and `versions-matrix.md`.
- **Follow-ups / risks:** the audit is non-normative; each finding must be accepted by the owner and folded into the normative docs in a separate change with a project-log entry. Blocking findings should be resolved before the dependent backlog stories start.

### 2026-08-18 — Documentation audit verification hooks tightened

- **Type:** decision
- **Story / Decision:** `docs/DOCUMENTATION_AUDIT.md`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** remaining recommended audit guardrails were folded into executable story criteria: `Instant` stays an `E0-06` blocker until the version matrix pins its package, `outbox.lastError` is debug/UI-only, Swift scale suffixes require `shared/README.md`, and supported currencies require platform minor-unit verification with `EUR` fallback.
- **Why:** the owner authorised using the recommended option while away, and these entries make already-accepted prose rules testable by the stories that will implement them.
- **Documents touched:** `docs/CONTRACTS.md`, `docs/BACKLOG.md`, and this log.
- **Verification:** documentation-only change; `git diff --check`.
- **Follow-ups / risks:** changes touch gated documentation and still require normal human review before merge.

### 2026-08-18 — Documentation audit review decisions recorded

- **Type:** decision
- **Story / Decision:** `docs/DOCUMENTATION_AUDIT.md`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the owner reviewed the first documentation-audit corrections and accepted the recommended choices for `:core:database` dependency rules, dependency injection over duplicate `expect`/`actual` paths, consumption type ownership in `:core:model`, closed platform API vocabulary, `:core:crash` ownership, and a non-duplicated `SPECIFICATION.md §8.2` module inventory reference. The owner also instructed Codex to use the recommended option for remaining documentation-audit decisions while they are away.
- **Why:** the review keeps decision ownership explicit while allowing the audit cleanup to continue without blocking on every low-risk guardrail choice.
- **Documents touched:** `docs/SPECIFICATION.md`, `docs/CONTRACTS.md`, `docs/TECHNICAL_PLAN.md`, `docs/BACKLOG.md`, `docs/identifiers.md`, and this log.
- **Verification:** manual review in conversation, followed by `git diff --check`.
- **Follow-ups / risks:** every subsequent assumed recommendation in this review should be summarised for owner review after the remaining audit items are processed.

### 2026-08-17 — Documentation audit guardrails folded into project docs

- **Type:** milestone
- **Story / Decision:** `docs/DOCUMENTATION_AUDIT.md`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** processed the documentation audit in order across architecture, data modelling, sync, auth, error handling, Swift-facing ABI and cross-document consistency. The docs now include a canonical module inventory, explicit `:core:database` dependency rules, tighter `expect`/`actual` boundaries, exact local DDL constraints, outbox coalescing SQL, Firestore `validPayload()` shape, local-owner adoption and account-deletion ordering details, logging redaction rules, `CalculateConsumption` signature cleanup, Swift ABI lifecycle rules, Phase 0 module-set constraints and expanded backlog fixtures.
- **Why:** the audit identified places where two agents could implement different behaviours while still claiming to follow the same docs. The corrections turn those areas into precise contract language and story acceptance criteria.
- **Documents touched:** `docs/SPECIFICATION.md`, `docs/CONTRACTS.md`, `docs/TECHNICAL_PLAN.md`, `docs/BACKLOG.md`, `docs/identifiers.md`, and this log.
- **Verification:** manual documentation review with targeted searches, diff review and code-fence counts for the edited Markdown files. No product code or CI exists yet.
- **Follow-ups / risks:** changes touch gated documentation paths and require human review before merge. `contract-check`, `architecture-check`, Firestore emulator tests and generated Objective-C header checks are specified but not yet implemented.

### 2026-08-17 — MVP single-device constraint recorded

- **Type:** decision
- **Story / Decision:** `D-0`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the MVP now explicitly supports one active device per account. Simultaneous multi-device use, active synchronization and moving the source of truth from Room to the remote database are recorded as future scope.
- **Why:** the owner clarified that the remote database is backup-only for the MVP, while future multi-device use will require real synchronization and a remote source of truth.
- **Documents touched:** `AGENTS.md`, `docs/SPECIFICATION.md`, `docs/CONTRACTS.md §10`, `docs/TECHNICAL_PLAN.md`, `docs/BACKLOG.md`, `docs/DEFINITION.md`, `README.md`, `docs/adr/0001-backend-cloud-firestore.md`, `docs/adr/0014-firestore-location-europe-west1.md`, and this log.
- **Verification:** manual documentation update only; no product code exists for this behaviour.
- **Follow-ups / risks:** remaining internal names such as `SyncController`, `RemoteSyncSource` and `syncState` are implementation terms unless a future contract-renaming story changes them. The change touches gated scope, backend and sync topics and requires human review before merge.

### 2026-08-17 — Remote database purpose clarified as backup and recovery

- **Type:** decision
- **Story / Decision:** `D-0`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the remote database purpose is now documented as backup and recovery only, so users can retrieve backed-up data on a new device. Active multi-device collaboration is not an MVP goal.
- **Why:** the owner clarified that the remote database exists solely as a backup, not as the product source of truth or a real-time cross-device data layer.
- **Documents touched:** `docs/SPECIFICATION.md`, `docs/CONTRACTS.md §9` and `§10`, `docs/DECISION_BOARD.md`, `docs/SECURITY.md`, `docs/TECHNICAL_PLAN.md`, `docs/BACKLOG.md`, `docs/DEFINITION.md`, `README.md`, `docs/adr/0001-backend-cloud-firestore.md`, `docs/adr/0015-firebase-project-topology.md`, and this log.
- **Verification:** manual documentation update only; no product code exists for this behaviour.
- **Follow-ups / risks:** remaining `sync` type and module names are technical implementation names unless a future contract-renaming story changes them. The change touches gated scope, backend and sync topics and requires human review before merge.

### 2026-08-17 — Post-MVP OCR and Cloud Functions security recorded

- **Type:** milestone
- **Story / Decision:** —
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** post-MVP roadmap notes now explicitly reserve receipt and odometer image capture with local AI text recognition, targeting receipt total amount, receipt price per liter and odometer reading, plus Cloud Functions-mediated remote read/write validation beyond the `D-23` account deletion server operation.
- **Why:** the owner asked to keep these capabilities visible for the future without expanding MVP scope or authorizing implementation dependencies, models, receipt or odometer image storage, server-mediated product reads, App Check enforcement or broader privileged server-side writes.
- **Documents touched:** `docs/SPECIFICATION.md §3`, `docs/SECURITY.md`, `docs/TECHNICAL_PLAN.md §13`, `README.md`, and this log.
- **Verification:** manual documentation update only; no product code exists for these features.
- **Follow-ups / risks:** both future areas require a dedicated story or ADR before implementation. Changes touch gated documentation and security topics and require human review before merge.

### 2026-08-17 — Temporary audit guardrails file deleted

- **Type:** decision
- **Story / Decision:** audit closure
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** deleted `docs/AUDIT_GUARDRAILS.md` after all `F-01` through `F-19` follow-up findings were absorbed into the normative and derived documentation.
- **Why:** the file was explicitly temporary and no longer carried active work after owner review.
- **Documents touched:** `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual search for active audit findings and `git diff --check`.
- **Follow-ups / risks:** none.

### 2026-08-17 — Partial refuel consumption explanation defined

- **Type:** decision
- **Story / Decision:** `F-15`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** `EndEntryNotFullTank` is kept as the list-projection reason for partial refuels. Partial rows show no own consumption, do not produce `SegmentResult`, and still contribute litres to the next full-to-full segment when they fall inside it.
- **Why:** the owner chose to keep a clear UI explanation for non-full refuels while preserving full-to-full consumption as the only calculation model.
- **Documents touched:** `docs/SPECIFICATION.md §6`, `docs/CONTRACTS.md §4`, `§13`, `§20.4` and `§20.6`, `docs/BACKLOG.md` `E1-05`, `E1-06`, `E1-08` and `E1-09`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `F-15`, `EndEntryNotFullTank`, `FuelEntryListItem`, `SegmentResult`, `ConsumptionReport` and partial refuels; `git diff --check`.
- **Follow-ups / risks:** no active audit findings remain. `docs/AUDIT_GUARDRAILS.md` is ready for owner review and later deletion.

### 2026-08-17 — Firestore remote schemas closed

- **Type:** decision
- **Story / Decision:** `F-12`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** remote `Vehicle` and `FuelEntry` documents now have exact closed key sets in `docs/CONTRACTS.md §16`. Unknown collections, missing keys, extra keys, local-only metadata and inconsistent `deleted` / `deletedAt` pairs are rejected by the Firestore contract.
- **Why:** the owner chose the strict schema option to make remote payload validation predictable and prevent malformed or locally-owned fields from entering Firestore.
- **Documents touched:** `AGENTS.md`, `docs/SPECIFICATION.md §10`, `docs/CONTRACTS.md §16`, `docs/BACKLOG.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `F-12`, `validPayload`, `closed remote schema`, forbidden local-only keys, `schemaVersion` and `deletedAt`; `git diff --check`.
- **Follow-ups / risks:** remaining audit finding is `F-15`.

### 2026-08-17 — Account deletion server operation accepted

- **Type:** decision
- **Story / Decision:** `F-11` / `D-23`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** account deletion now uses a Firebase Admin server operation. The app re-authenticates if needed, calls the authenticated server operation, the server deletes `fuelEntries`, then `vehicles`, then the Firebase Auth user, and only after success does the app clear local data.
- **Why:** the owner chose server/Admin deletion so store-required account deletion can physically purge remote data while client Firestore rules continue to reject hard deletes.
- **Documents touched:** `AGENTS.md`, `docs/SPECIFICATION.md §7` and `§12`, `docs/CONTRACTS.md §6`, `§11.1`, `§11.5` and `§16`, `docs/DECISION_BOARD.md`, `docs/TECHNICAL_PLAN.md`, `docs/adr/README.md`, `docs/adr/0024-account-deletion-server-admin.md`, `docs/BACKLOG.md`, `docs/SECURITY.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `D-23`, `F-11`, `E3-10`, `AuthError.AccountDeletionRemoteFailed`, account deletion and `allow delete`; `git diff --check`.
- **Follow-ups / risks:** remaining audit findings are `F-12` and `F-15`. `E3-10` must implement and test the server operation before release.

### 2026-08-17 — Malformed remote payload quarantine decided

- **Type:** decision
- **Story / Decision:** `F-16`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** quarantine now covers both unsupported future schema versions and malformed supported-version payloads. `QuarantineReason.MalformedPayload` and `QuarantineRecord` are canonical sync types.
- **Why:** the owner chose to keep malformed remote documents out of product tables without blocking cursor progress, provided the quarantine row is committed successfully.
- **Documents touched:** `AGENTS.md`, `docs/CONTRACTS.md §5`, `§9.5` and `§20.7`, `docs/SPECIFICATION.md`, `docs/TECHNICAL_PLAN.md §8` and `§9`, `docs/BACKLOG.md`, `docs/DEFINITION.md`, `docs/SECURITY.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `quarantine`, `MalformedPayload`, `QuarantineReason`, `schemaVersion`, `malformed` and `18 sync tests`; `git diff --check`.
- **Follow-ups / risks:** remaining audit findings are `F-11`, `F-12` and `F-15`.

### 2026-08-17 — Odometer recompute set defined

- **Type:** decision
- **Story / Decision:** `F-14`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** `odometerInconsistent` recomputation now has an exact minimal recompute set for create, update, delete and vehicle cascade delete. `currentOdometerKm` remains recomputed for the whole vehicle in the same transaction.
- **Why:** the owner chose the explicit minimal-set option to handle edits that move an entry in chronological order without recomputing the whole vehicle unnecessarily.
- **Documents touched:** `docs/CONTRACTS.md §3.1` and fuel repository side effects, `docs/SPECIFICATION.md §5` / R-1, `docs/BACKLOG.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `odometerInconsistent`, `currentOdometerKm`, `successor`, `recompute` and `F-14`; `git diff --check`.
- **Follow-ups / risks:** `F-16` remains the next sync/database guardrail.

### 2026-08-17 — Pull overlap cursor anchor fixed

- **Type:** decision
- **Story / Decision:** `F-13`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** pull pagination now uses `startAt(overlapSince, "")` for the first page of an overlapped cycle and `startAfter(pageCursor.lastServerUpdatedAt, pageCursor.lastDocumentId)` for later pages.
- **Why:** the owner chose the concrete-anchor option to avoid an invalid `null` document-id cursor while preserving the 30-second overlap window.
- **Documents touched:** `docs/CONTRACTS.md §9.4` and `§16`, `docs/SPECIFICATION.md §9.3`, `docs/TECHNICAL_PLAN.md §8`, `docs/BACKLOG.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `startAt`, `startAfter`, `overlapSince`, `RemoteCursor` and `null`; `git diff --check`.
- **Follow-ups / risks:** `F-14` and `F-16` remain the next sync/database guardrails.

### 2026-08-17 — Local owner adoption ordering decided

- **Type:** decision
- **Story / Decision:** `F-10`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** first launch now attempts Firebase anonymous authentication automatically; `LOCAL_OWNER` is the offline/Auth-unavailable fallback. Local synchronized rows carry `localMutationSeq`, and adoption builds the initial outbox in dependency-group order and then by `localMutationSeq ASC, id ASC`.
- **Why:** the owner chose deterministic local mutation ordering without creating a staging outbox for `LOCAL_OWNER`, preserving the rule that the outbox stays empty until a real UID exists.
- **Documents touched:** `docs/CONTRACTS.md §3`, `§8`, `§11.2`, `§11.4` and repository rules, `docs/SPECIFICATION.md`, `docs/TECHNICAL_PLAN.md §6`, `docs/BACKLOG.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `localMutationSeq`, `local_sequence`, `LOCAL_OWNER`, anonymous authentication and adoption; `git diff --check`.
- **Follow-ups / risks:** `F-13`, `F-14` and `F-16` remain the next sync/database guardrails.

### 2026-08-17 — Sync state storage decided

- **Type:** decision
- **Story / Decision:** `F-09`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** `syncState` is now defined as a stored local control column. The outbox influences it but does not fully define it, and `LOCAL_OWNER + PENDING + no outbox` is an explicit legal state before adoption.
- **Why:** the owner chose the stored-state option to resolve the contradiction between first-launch offline writes and the outbox suppression rule.
- **Documents touched:** `docs/CONTRACTS.md §7`, `docs/BACKLOG.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `syncState`, `LOCAL_OWNER`, `PENDING` and `outbox`; `git diff --check`.
- **Follow-ups / risks:** `F-10` remains open and must define how local-owner mutations preserve causal ordering during adoption.

### 2026-08-17 — MVP settings reset semantics decided

- **Type:** decision
- **Story / Decision:** `F-17`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** MVP settings now reset during destructive local-data flows. Sign-out, anonymous "delete local data" and account deletion delete `user_settings`; defaults are recreated from locale with `analyticsEnabled = false`.
- **Why:** the owner confirmed that settings should not survive in the MVP, while settings sync through Google Play services / Android backup / iCloud belongs to future roadmap scope.
- **Documents touched:** `AGENTS.md`, README, `docs/SPECIFICATION.md §3`, `§5.3` and `§7 F-5`, `docs/CONTRACTS.md §3 UserSettings` and `§11.5`, `docs/TECHNICAL_PLAN.md`, `docs/BACKLOG.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `settings`, `user_settings`, `Google Play`, `Android backup`, `iCloud`, sign-out, local-data deletion and account deletion; `git diff --check`.
- **Follow-ups / risks:** future platform settings sync requires a new story or ADR before adding platform APIs, entitlements, manifest keys or dependencies.

### 2026-08-17 — Electric and hybrid fuel types deferred

- **Type:** decision
- **Story / Decision:** `D-4` / `F-08`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the MVP `FuelType` enum now excludes `ELECTRIC` and `HYBRID`; the canonical values are `GASOLINE`, `DIESEL`, `LPG`, `CNG` and `OTHER`. Electric and hybrid support is recorded as future roadmap scope requiring a dedicated energy model.
- **Why:** the owner confirmed that electric and hybrid vehicles are not included in the MVP, and supporting them correctly requires kWh input, mixed energy units, validation, Firestore rules and migration work.
- **Documents touched:** `AGENTS.md`, `docs/CONTRACTS.md §3`, `§5` and `§20.4`, `docs/SPECIFICATION.md §3` and `§12`, `docs/DECISION_BOARD.md`, `docs/TECHNICAL_PLAN.md §2`, `docs/adr/0005-vehicle-fuel-type-from-day-one.md`, `docs/adr/README.md`, `docs/BACKLOG.md`, `README.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `FuelType`, `ELECTRIC`, `HYBRID`, `energy model` and `D-4`; `git diff --check`.
- **Follow-ups / risks:** future electric/hybrid support requires a new story or ADR before enum/schema expansion.

### 2026-08-17 — Monetary and name data guardrails tightened

- **Type:** correction
- **Story / Decision:** `F-05`, `F-06`, `F-07`; `F-08` remains owner decision
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** `MoneyInput` now selects the derived field only during validation, while persistence stores only the canonical monetary triple. `SUPPORTED_CURRENCY_CODES` is now the exact MVP currency allowlist, and vehicle name normalization / `nameFold` are defined as KMP-pure operations.
- **Why:** the previous contract implied an authoritative supplied monetary pair without storing it, left currency support dependent on an unspecified ISO lookup, and described `nameFold` differently from the normalization rules.
- **Documents touched:** `docs/CONTRACTS.md §2`, `§3`, `§5`, `§20.0.1` and `§20.3`, `docs/SPECIFICATION.md §5.3`, `docs/BACKLOG.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `MoneyInput`, `moneyInputKind`, `SUPPORTED_CURRENCY_CODES`, `MinorUnits`, `canonicalVehicleName`, `nameFold`, `FuelType`, `ELECTRIC` and `HYBRID`; `git diff --check`.
- **Follow-ups / risks:** `F-08` still requires owner choice: reject `ELECTRIC` / `HYBRID` in MVP validation or remove them until an energy-model story exists.

### 2026-08-17 — Swift-facing graph contract made explicit

- **Type:** correction
- **Story / Decision:** `D-2` / `F-02`, `F-03`, `F-04`, `F-18`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the Swift-facing ABI is now an explicit allowlist with `createSwiftAppGraph(isDebugBuild)`, `SwiftAppGraph`, concrete state holders, declared `UiState` classes and `UiMessage`; Kotlin-facing graph construction remains available through `createAppGraph(AppGraphDependencies)` but is hidden from the Objective-C header.
- **Why:** the previous contract mixed Kotlin construction APIs with Swift-exported APIs, exposed implementation-facing abstractions such as `SyncController`, and referenced state-holder / `UiState` types that were not declared.
- **Documents touched:** `docs/CONTRACTS.md §11.6`, `§14`, `§15.3`, `§18`, `§20.7` and `§20.10`, `docs/SPECIFICATION.md §8.4` and `§8.5`, `docs/TECHNICAL_PLAN.md §5`, `docs/BACKLOG.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `SwiftAppGraph`, `AppGraphDependencies`, `createAppGraph`, `AppGraph`, `SyncController`, `CoroutineScope`, `Outcome` and `AppError`; `git diff --check`.
- **Follow-ups / risks:** changes touch gated contract and specification documents and require human review before merge. Next open audit block is `F-05`, `F-06`, `F-07` and `F-08`.

### 2026-08-17 — Crash reporting module ownership aligned

- **Type:** correction
- **Story / Decision:** `D-21` / `F-01`, `F-19`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** `CrashReporter` is now owned by `:core:crash`, included in `AppGraphDependencies` from Phase 0, and represented consistently in the module lists. Firebase Crashlytics remains a Phase 4 integration behind that abstraction.
- **Why:** the previous wording split ownership between `:core:common`, `:core:crash` and Phase 4 wiring, which made graph construction and module bootstrap ambiguous.
- **Documents touched:** `docs/CONTRACTS.md §11.6` and `§20.3.1`, `docs/SPECIFICATION.md §8.2` and `§8.3`, `docs/TECHNICAL_PLAN.md §4`, `docs/BACKLOG.md`, `README.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `CrashReporter`, `:core:crash` and `:integration:firebase-crashlytics`.
- **Follow-ups / risks:** changes touch gated contract and specification documents and require human review before merge. `F-02`, `F-03`, `F-04` and `F-18` remain open around the Swift-facing graph surface.

### 2026-08-17 — Empty owner-decision queue made explicit

- **Type:** correction
- **Story / Decision:** `E0-00`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the "Decisions Awaiting Owner Confirmation" section now has a clear no-open-decisions state instead of an empty table with a sentence where a row would normally be.
- **Why:** the future `contract-check` should not have to parse a malformed table to determine that no `Proposed` or `Pending` decisions exist.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/CONTRACTS.md §18`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual review of the decision-board empty state and the corresponding `contract-check` assertion.
- **Follow-ups / risks:** if a future `Proposed` or `Pending` decision is introduced, this section must become a real table with a `Needed by` story or phase.

### 2026-08-17 — D-14 future production project clarified

- **Type:** decision
- **Story / Decision:** `D-14`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** documentation now states that development uses only `carapp-dev` for now, but public release requires a separate production Firebase project.
- **Why:** the owner clarified that the future topology is two Firebase projects, development and production, while production project creation and its project ID remain deferred until release preparation.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/identifiers.md`, `docs/BACKLOG.md`, `docs/adr/0015-firebase-project-topology.md`, `docs/adr/README.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for production Firebase topology, production project ID and `D-14` wording.
- **Follow-ups / risks:** the production Firebase project ID remains an owner decision before `E4-04`; agents MUST NOT invent it.

### 2026-08-17 — Firebase identifier wording aligned with D-14

- **Type:** correction
- **Story / Decision:** `E0-00` / `D-14`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** `E0-00` and the document map now require only the development Firebase project ID during development, while production Firebase topology and project IDs remain explicitly deferred by `D-14`.
- **Why:** the previous wording said "Firebase project IDs" in plural and could lead an agent to invent a production project ID to satisfy an already-closed owner-decision story.
- **Documents touched:** `AGENTS.md`, `docs/BACKLOG.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for Firebase project ID wording and production-project references.
- **Follow-ups / risks:** production Firebase topology remains an owner decision before `E4-04`.

### 2026-08-17 — E0-00 closure reflected as completed

- **Type:** story
- **Story / Decision:** `E0-00`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** orientation documents now state that owner decision closure is complete and that implementation starts at `E0-01`.
- **Why:** after `D-13` through `D-22` were accepted and pushed, keeping `E0-00` as the next implementation step would send future agents back through already-closed decisions.
- **Documents touched:** `README.md`, `docs/DEFINITION.md`, `docs/BACKLOG.md`, `docs/AUDIT_GUARDRAILS.md`, and this log.
- **Verification:** manual cross-document search for `E0-00`, `Proposed`, `Pending`, and owner-decision closure wording.
- **Follow-ups / risks:** `docs/AUDIT_GUARDRAILS.md` remains temporary until the owner finishes reviewing every audit-remediation theme.

### 2026-08-17 — D-21 crash reporting accepted

- **Type:** decision
- **Story / Decision:** `D-21`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** Firebase Crashlytics was accepted for Phase 4 behind a new `CrashReporter` abstraction.
- **Why:** the owner selected Crashlytics for release hardening while keeping crash reporting separate from Kermit logging and Firebase Analytics.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §8` and `§12`, `docs/CONTRACTS.md §11.6` and `§20.3`, `docs/TECHNICAL_PLAN.md §2` and `§3`, `docs/BACKLOG.md`, `docs/versions-matrix.md`, `docs/SECURITY.md`, `docs/adr/0016-logging-kermit.md`, `docs/adr/0023-firebase-crashlytics.md`, `docs/adr/README.md`, and this log.
- **Verification:** manual cross-document search for `D-21`, Crashlytics and `CrashReporter` references.
- **Follow-ups / risks:** production Firebase topology remains deferred by `D-14` until `E4-04`.

### 2026-08-17 — D-20 localization implementation accepted

- **Type:** decision
- **Story / Decision:** `D-20`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** native Android and iOS resources were accepted for localization, with `UiState` carrying typed values only and no user-facing text.
- **Why:** the owner selected the existing recommendation; the UI is native, so native resource catalogues keep localization idiomatic without adding a shared resource dependency.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/adr/0020-localization-native-resources.md`, `docs/adr/README.md`, and this log.
- **Verification:** manual cross-document search for `D-20`, localization, native resources and `UiState` status references.
- **Follow-ups / risks:** `D-21` remains unresolved and is needed before `E4-04`.

### 2026-08-17 — D-18 coverage measurement accepted

- **Type:** decision
- **Story / Decision:** `D-18`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** Kover was accepted as the coverage measurement tool with the existing thresholds: `:core:model` and `:core:common` at 90%, feature `domain` at 85%, and `:core:sync` at 80%.
- **Why:** the owner selected the existing recommendation so coverage targets become CI-enforced pass/fail criteria rather than review judgement.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/BACKLOG.md`, `docs/adr/0022-coverage-kover.md`, `docs/adr/README.md`, and this log.
- **Verification:** manual cross-document search for `D-18`, Kover and coverage status references.
- **Follow-ups / risks:** Kover version compatibility remains validated in `E0-06`; `D-20` and `D-21` remain unresolved.

### 2026-08-17 — D-17 Flow testing helper accepted

- **Type:** decision
- **Story / Decision:** `D-17`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** Turbine was accepted as the Flow testing helper, and the previous combined D-17/D-18 ADR was split into separate ADRs so each decision can carry its own status.
- **Why:** the owner selected Turbine; `D-18` remains unresolved, so sharing one ADR made status verification ambiguous.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/BACKLOG.md`, `docs/adr/0019-flow-testing-turbine.md`, `docs/adr/0022-coverage-kover.md`, `docs/adr/README.md`, and this log.
- **Verification:** manual cross-document search for `D-17`, `D-18`, Turbine, Kover and ADR status references.
- **Follow-ups / risks:** Turbine compatibility remains validated in `E0-06`; `E0-05` remains blocked by `D-18`.

### 2026-08-17 — D-19 result channel accepted

- **Type:** decision
- **Story / Decision:** `D-19`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the owner accepted the custom `Outcome<T, E>` result channel in `:core:common`.
- **Why:** `kotlin.Result` cannot represent a typed error channel, and Arrow would add a dependency for one core type; the custom type matches the contracts exactly.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/BACKLOG.md`, `docs/adr/0018-outcome-result-type.md`, `docs/adr/README.md`, and this log.
- **Verification:** manual cross-document search for `D-19`, `Outcome<T, E>` and result type status references.
- **Follow-ups / risks:** `D-17`, `D-18`, `D-20` and `D-21` remain unresolved; `E0-05` is still blocked by `D-17` and `D-18`.

### 2026-08-17 — D-16 architecture checks accepted

- **Type:** decision
- **Story / Decision:** `D-16`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** Konsist was accepted for package-level architecture rules, with a custom Gradle configuration check for module-level rules.
- **Why:** the owner selected the existing recommendation; package rules are intra-module source rules that Gradle cannot see, while module rules remain cheaper to enforce from the Gradle graph.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/BACKLOG.md`, `docs/adr/0017-architecture-checks-konsist.md`, `docs/adr/README.md`, and this log.
- **Verification:** manual cross-document search for `D-16`, Konsist and architecture check status references.
- **Follow-ups / risks:** Konsist version pinning remains part of `E0-06`; `D-17`, `D-18`, `D-19`, `D-20` and `D-21` remain unresolved.

### 2026-08-17 — D-15 logging telemetry boundary clarified

- **Type:** decision
- **Story / Decision:** `D-15`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** documentation now states that Kermit is the logging implementation only and does not replace `AnalyticsTracker`, Firebase Analytics, `CrashReporter` or Firebase Crashlytics.
- **Why:** the owner said Kermit should be used for Firebase Analytics and Crashlytics; the clarification preserves the intent that Firebase integrations may use Kermit-backed logging for diagnostics while keeping analytics events and crash reports on separate contracts.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/CONTRACTS.md §17`, `docs/adr/0016-logging-kermit.md`, and this log.
- **Verification:** manual cross-document search for Kermit, Analytics, Crashlytics, `AnalyticsTracker` and `CrashReporter`.
- **Follow-ups / risks:** `D-21` still decides whether Firebase Crashlytics is part of the MVP hardening phase.

### 2026-08-17 — D-15 logging implementation accepted

- **Type:** decision
- **Story / Decision:** `D-15`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** Kermit was accepted as the logging implementation behind the common `Logger` abstraction.
- **Why:** the owner selected the existing recommendation; Kermit provides KMP platform sinks while remaining hidden behind the repository-owned logging contract.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/BACKLOG.md`, `docs/adr/0016-logging-kermit.md`, `docs/adr/README.md`, and this log.
- **Verification:** manual cross-document search for `D-15`, Kermit and logging status references.
- **Follow-ups / risks:** Kermit version pinning remains part of `E0-06`; `D-16`, `D-17`, `D-18`, `D-19`, `D-20` and `D-21` remain unresolved.

### 2026-08-17 — D-22 application identifiers accepted

- **Type:** decision
- **Story / Decision:** `D-22`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the owner accepted `carApp` as the product name, `com.ruizurraca.carapp` as the Android application ID, Android namespace, iOS bundle identifier and shared package root, `Shared` as the iOS framework name, and `carapp-dev` as the development Firebase project ID.
- **Why:** these identifiers are required before `E0-01` creates platform projects and Firebase app registrations.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/BACKLOG.md`, `docs/identifiers.md`, `docs/adr/0021-application-identifiers.md`, `docs/adr/README.md`, and this log.
- **Verification:** manual cross-document search for `D-22`, `carApp`, `carapp-dev` and `com.ruizurraca.carapp`.
- **Follow-ups / risks:** production Firebase topology and production project identifiers remain deferred by `D-14` until `E4-04`.

### 2026-08-17 — D-22 application package prefix changed

- **Type:** decision
- **Story / Decision:** `D-22`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** the proposed Android `applicationId`, Android namespace, iOS bundle identifier and shared package root changed from `com.davidru85.carapp` to `com.ruizurraca.carapp`.
- **Why:** the owner selected the `ruizurraca` package prefix.
- **Documents touched:** `docs/identifiers.md`, `docs/adr/0021-application-identifiers.md`, and this log.
- **Verification:** manual cross-document search for `com.davidru85.carapp` and `com.ruizurraca.carapp`.
- **Follow-ups / risks:** `D-22` still requires owner confirmation for the remaining identifier values, including the product name and development Firebase project ID.

### 2026-08-17 — D-14 Firebase project topology accepted for development

- **Type:** decision
- **Story / Decision:** `D-14`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** Firebase topology changed from proposed `dev` plus `prod` projects to one development Firebase project plus the local emulator; production Firebase topology is deferred until release preparation.
- **Why:** the owner chose a simpler development setup because Firestore is only a backup and synchronization replica, while Room remains the source of truth.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §12`, `docs/TECHNICAL_PLAN.md §2`, `docs/BACKLOG.md`, `docs/identifiers.md`, `docs/adr/0015-firebase-project-topology.md`, `docs/adr/README.md`, `docs/SECURITY.md`, and this log.
- **Verification:** manual cross-document search for `D-14`, Firebase topology, project ID, emulator and production references.
- **Follow-ups / risks:** development Firebase project ID remains governed by `D-22`. Production topology and production project IDs must be decided before `E4-04`.

### 2026-08-17 — D-13 Firestore location accepted

- **Type:** decision
- **Story / Decision:** `D-13`
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** Firestore location changed from the proposed `eur3` multi-region to the accepted `europe-west1` single region, and the documentation now states that Firestore is a backup and synchronization replica while Room remains the source of truth.
- **Why:** the owner chose `europe-west1` after reviewing the cost and availability tradeoff, because Firestore is not the primary product database.
- **Documents touched:** `docs/DECISION_BOARD.md`, `docs/SPECIFICATION.md §9` and `§12`, `docs/CONTRACTS.md §10`, `docs/TECHNICAL_PLAN.md §2`, `docs/identifiers.md`, `docs/adr/0014-firestore-location-europe-west1.md`, `docs/adr/README.md`, `docs/BACKLOG.md`, and this log.
- **Verification:** manual cross-document search for `D-13`, `eur3` and Firestore location references.
- **Follow-ups / risks:** `D-14`, `D-15`, `D-16`, `D-17`, `D-18`, `D-19`, `D-20`, `D-21` and `D-22` remain unresolved.

### 2026-08-17 — Agent handoff and contract-check guardrails tightened

- **Type:** milestone
- **Story / Decision:** —
- **Author:** Codex, on behalf of David Ruiz
- **What changed:** added an explicit story intake protocol, required acceptance evidence in handoffs, and expanded `contract-check` to verify decision statuses, ADR status alignment, unresolved decision tracking and PR-template/handoff coverage.
- **Why:** the documentation already defined strong product contracts, but future agents still needed a more mechanical way to prove a story was Ready before implementation and Done after implementation.
- **Documents touched:** `AGENTS.md`, `docs/SPECIFICATION.md §12`, `docs/CONTRACTS.md §18`, `docs/DECISION_BOARD.md`, `docs/TECHNICAL_PLAN.md §2`, `docs/BACKLOG.md`, `docs/adr/README.md`, `docs/templates/agent-handoff.md`, `.github/pull_request_template.md`, and this log.
- **Verification:** manual documentation review with `rg` and targeted file reads. No product code or CI exists yet.
- **Follow-ups / risks:** changes touch gated documentation paths and require human review before merge.

### 2026-08-17 — Audit findings folded back into the documentation

- **Type:** milestone
- **Story / Decision:** —
- **Author:** Claude (Cowork session), on behalf of David Ruiz
- **What changed:** the 99 findings of the specification audit were applied across the whole documentation set. The main structural changes: document authority is now split into a behaviour axis (`docs/SPECIFICATION.md`) and a representation axis (`docs/CONTRACTS.md`) instead of a single linear ranking; `docs/CONTRACTS.md` gained `§20` with the complete canonical type definitions that were previously referenced but never declared; the field vocabulary was unified on the `…Km` / `…Scaled` / `…Minor` names; the monetary formulas were rewritten as exact integer arithmetic with golden test values; the pull query gained a `startAfter` anchor; LWW arbitration now compares `serverUpdatedAt` instead of the local clock; first launch was made offline-capable through the `LOCAL_OWNER` sentinel with a new adoption story; and human review gates, reading order and normative language were consolidated into `AGENTS.md` as the single entry point.
- **Why:** the definition package was complete in intent but not machine-decidable. Roughly twenty types appeared in normative signatures without being defined anywhere, two documents used incompatible field names, and several rules were mutually contradictory — enough that two competent agents would have produced two incompatible implementations, with no CI check able to catch it.
- **Documents touched:** `AGENTS.md`, `docs/SPECIFICATION.md`, `docs/CONTRACTS.md`, `docs/DECISION_BOARD.md`, `docs/TECHNICAL_PLAN.md`, `docs/BACKLOG.md`, `docs/DEFINITION.md`, `README.md`, `docs/CONTRIBUTING.md`, `docs/SECURITY.md`, `docs/adr/*`, `docs/identifiers.md`, `docs/versions-matrix.md`, `docs/templates/agent-handoff.md`, `.github/pull_request_template.md`, and this log.
- **Verification:** manual cross-document review. The automated `contract-check` that would enforce these invariants does not exist yet; it is specified in `docs/CONTRACTS.md §18` and implemented by `E0-05`.
- **Follow-ups / risks:** ten decisions are `Proposed` or `Pending` and require owner confirmation before Phase 0 starts — see `E0-00` and the "Decisions Awaiting Owner Confirmation" table in `docs/DECISION_BOARD.md`. Five new stories were added (`E0-00`, `E1-10`, `E2-06`, `E3-07`, `E3-08`) and are not yet estimated against real capacity. `docs/AUDIT_GUARDRAILS.md` remains in the repository as a temporary resolution log and should be deleted once the owner has reviewed the changes.

### 2026-08-17 — Specification audit performed

- **Type:** milestone
- **Story / Decision:** —
- **Author:** Claude (Cowork session), on behalf of David Ruiz
- **What changed:** a full architectural audit of the definition package produced `docs/AUDIT_GUARDRAILS.md`: 99 findings across governance, KMP architecture, data modeling, workflows and state, error handling, API contracts, security and verifiability, of which 16 were classified as blocking. Committed as `f8b70cb`.
- **Why:** the project is intended to be implemented mostly by AI agents, so ambiguity in the specification translates directly into divergent implementations rather than into questions.
- **Documents touched:** `docs/AUDIT_GUARDRAILS.md` (new, temporary).
- **Verification:** every finding cites a literal section of the documents as they stood on 2026-08-16.
- **Follow-ups / risks:** the audit file is temporary and is deleted once its findings are absorbed and reviewed.

### 2026-08-16 — Definition package completed

- **Type:** milestone
- **Story / Decision:** `D-0` to `D-11`
- **Author:** David Ruiz
- **What changed:** the initial definition package was written: specification, contracts, decision board, technical plan, backlog, agent operating guide, and thirteen ADRs covering the closed technical decisions. The repository remains greenfield, with no product code.
- **Why:** to make the project implementable by AI agents with predictable boundaries, before writing any code.
- **Documents touched:** all initial documents.
- **Verification:** —
- **Follow-ups / risks:** version pinning and real CI command validation deferred to Phase 0.
