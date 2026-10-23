# ADR-0227 / D-226 - Combine Agent Evidence with an Owner Screen-Reader Walkthrough

## Status

Accepted

The owner selected option C on 2026-10-16.

## Context

`E4-02` requires TalkBack and VoiceOver to work on F-1 to F-3, Settings, the departure screens and
the `E4-05` conversion surface (`D-225`). The automated checks of `D-216` and `D-217` find missing
labels, insufficient contrast and small hit regions, but they do not prove that a person can
complete the flows with a screen reader. The agent can run emulators and simulators and record their
evidence; it does not operate a physical device, and a screen-reader walkthrough on a real device is
a human task, like the owner-run manual provider acceptance of `E2-03`.

## Options Considered

| Option | Benefits | Costs / Risks |
|--------|----------|---------------|
| A. The owner performs the walkthrough on a device from a checklist the agent prepares; that is the only walkthrough evidence | Real devices and a real screen-reader pass. | No recorded emulator or simulator evidence to compare later runs with. |
| B. The owner accepts the agent's emulator and simulator evidence only | No owner time. | Without a real screen-reader pass, use with a screen reader is not demonstrated. Rejected. |
| C. Both: the agent provides emulator and simulator evidence (automated checks and captures) and prepares the checklist, and the owner runs the walkthrough on a device | Regression evidence plus a human pass. | Owner time with one Android and one iOS device. |

## Decision

The selected option is **C**. The agent records in `docs/handoffs/E4-02.md` the emulator and
simulator evidence of every audited screen and a step-by-step TalkBack and VoiceOver checklist of
F-1 to F-3, Settings, the departure screens and the conversion surface. The owner runs the checklist
on a device; the handoff records the result, the date and the devices, or states that the
walkthrough is still open when the pull request is opened.

## Consequences

### Positive

- The acceptance rests on automated evidence and on a real screen-reader pass.

### Negative

- The device walkthrough depends on the owner's time and can remain open after the pull request is
  opened.

### Constraints Introduced

- The agent never reports the walkthrough as done before the owner reports it.

## Verification

- `docs/handoffs/E4-02.md` contains the evidence, the checklist and the walkthrough result or its
  open status.

## References

- `docs/DECISION_BOARD.md` (decision ID `D-226`)
- `docs/SPECIFICATION.md §11` (Accessibility)
- `D-216` (ADR-0217), `D-217` (ADR-0218), `D-225` (ADR-0226)
- `docs/handoffs/E2-03.md` (owner-run manual acceptance)
- `docs/BACKLOG.md` (`E4-02`)
