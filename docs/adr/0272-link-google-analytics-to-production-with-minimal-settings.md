# ADR-0272 / D-271 - Link Google Analytics to Production with Minimal Settings

## Status

Accepted

The owner selected option A on 2026-11-09, at the `E4-10` intake. The owner first chose option B on the
agent's recommendation, then chose A after the agent corrected that recommendation: it had not checked
what the opt-in copy and the privacy-policy draft promise.

## Context

`E3-09` emits the closed `docs/CONTRACTS.md §16.1` events through Firebase Analytics, without an
advertising identifier (`D-203`), and only after the Settings opt-in (`D-241`). The development project
has no Google Analytics link, so no report receives those events.

The Settings opt-in offers "pseudonymous usage measurement", and the privacy-policy draft in
`docs/handoffs/E4-04.md` says that the opted-in data goes to Google Analytics and Firebase Crashlytics.
`docs/BACKLOG.md` §`E4-10` leaves the production link open.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. Link a GA4 property with minimal settings | The opt-in copy and the privacy draft stay true without a text change | One more owner console step; opted-in usage data reaches a Google Analytics property |
| B. No link, and correct the texts | Nothing to configure | The opt-in offers measurement that no report receives; `E4-09` must remove Google Analytics from the privacy draft |
| C. Defer the question to `E4-09` | No work now | The production project starts with the same mismatch as option B |

## Decision

The selected option is **A**. The production project `davidruiz-carapp` is linked to a new Google
Analytics 4 property, with these settings:

- event data retention: 2 months;
- Google signals: off;
- ads personalization: off for every region;
- every account data-sharing setting: off;
- no link to Google Ads or to any other Google product.

The link is made before the client configuration files are downloaded
(`docs/runbooks/production-firebase-release.md` P10). The development project stays unlinked.

## Consequences

### Positive

- The usage measurement that the opt-in offers reaches a report in production.
- No user-facing text or privacy draft changes.

### Negative

- Opted-in usage events reach Google Analytics, so the privacy policy that `E4-09` publishes must keep
  naming it.
- Production and development differ: an Analytics check made against development proves nothing about
  the production property.

### Constraints Introduced

- A change to any setting above needs a new decision.
- The client keeps the `D-203` posture: no advertising identifier, collection only after the opt-in.

## Verification

- The owner's read-back after P10 states each setting above.
- `docs/handoffs/E4-10.md` records the read-back.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-271`); `D-11` (ADR-0011); `D-203` (ADR-0204); `D-241`
  (ADR-0242); `D-258` (ADR-0259)
- `docs/CONTRACTS.md §16.1`; `docs/handoffs/E4-04.md` §Privacy policy draft
