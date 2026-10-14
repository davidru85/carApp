# ADR-0203 - Use the published sync aggregate for analytics

## Status

Accepted

## Context

The old §20.9 prose asked analytics to downgrade a Failed aggregate if all counted row errors were
connectivity-related. SyncStatus exposes only counts, while §9.9 already classifies connectivity in
the engine. Non-connectivity pull, adoption or unexpected cycle failures can publish Failed without
any failed row, using a representative retryable count of at least one. The old prose therefore
could not describe the existing state machine or an implementable consumer fixture.

## Options Considered

- 2A: align §20.9 with §9.9 and keep the direct, total category mapping.
- 2B: expose row errors through SyncStatus and reclassify in analytics.
- 2C: change the engine and every aggregate consumer to fit the old prose.

## Decision

The owner selected **2A** on 2026-10-14. `:core:sync` alone derives the aggregate. Analytics maps
Idle, Syncing, Pending and Failed directly to IDLE, SYNCING, PENDING and FAILED. No sync engine,
SyncStatus API or connectivity observation changes.

## Consequences and Constraints

A connectivity-only cycle with pending work can publish Pending; with an empty outbox it can
publish Idle. A non-connectivity cycle failure may publish Failed even when no row failed. Consumers
must not inspect row errors or derive a second aggregate. The mapper KDoc and contract must explain
these cases consistently with §9.9, D-192 and D-193.

## Verification

Run `SyncStatusCategoryTest` and the existing `DefaultSyncControllerThirteenthReviewTest`, which
covers empty/pending connectivity failures and a non-connectivity pull failure with zero failed
rows. No new product behavior is introduced; this is a documentation alignment, exempt from RED.
