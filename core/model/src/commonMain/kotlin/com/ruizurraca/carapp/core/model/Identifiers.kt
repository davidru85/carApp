package com.ruizurraca.carapp.core.model

import kotlin.jvm.JvmInline

/**
 * Canonical identifier, money and scaled-value types of `docs/CONTRACTS.md §20.0`.
 *
 * **Construction never validates.** None of these types has an `init` block, none throws and none
 * rejects anything: wrapping a malformed UUID or an unsupported currency code is legal at the type
 * level. This is deliberate, not an oversight. `§5` requires that a pull transaction MUST NOT fail
 * because of a domain constraint or a malformed remote payload; a throwing constructor would turn
 * one bad remote document into an exception inside the pull transaction and stall the cursor
 * permanently. Validation lives in the use cases of `§5` and returns typed errors.
 *
 * **Property naming is canonical.** Identifier types expose `value`; scaled quantity types expose
 * `scaled`. Using `raw`, `id`, `amount` or a unit-specific name is a contract violation.
 */

/** Lowercase canonical UUID v4, generated on the client. */
@JvmInline
value class EntityId(val value: String)

/** Firebase UID, or the [LOCAL_OWNER] sentinel before an anonymous UID exists (`§11.4`). */
@JvmInline
value class OwnerId(val value: String)

/** ISO-4217 uppercase code. The MVP default fallback is `EUR`. */
@JvmInline
value class CurrencyCode(val value: String)

/**
 * Owner sentinel used before an anonymous Firebase UID exists (`§11.2`, `§11.4`).
 *
 * It lives here with [OwnerId] rather than with the other named constants in `:core:common`,
 * because `:core:common` depends on `:core:model` and never the reverse.
 */
val LOCAL_OWNER: OwnerId = OwnerId("LOCAL_OWNER")
