@file:OptIn(kotlin.experimental.ExperimentalObjCRefinement::class)

package com.ruizurraca.carapp.core.model

import kotlin.native.HiddenFromObjC

/**
 * The canonical vehicle-name form: leading and trailing whitespace removed, every internal run of
 * whitespace collapsed to one space, and no other character changed. The stored `nameFold` is this
 * value lowercased.
 *
 * This lives in `:core:model` because both `:feature:vehicle` (local create/update and duplicate-name
 * detection) and `:core:sync` (deriving `nameFold` for a remotely applied vehicle) must produce the
 * identical value. `docs/CONTRACTS.md §3` duplicate-name detection depends on it, so a divergence
 * between the two call sites would silently break that rule; a single shared function removes the
 * possibility rather than guarding it.
 */
@HiddenFromObjC
fun canonicalVehicleName(input: String): String =
    buildString {
        var whitespacePending = false
        input.forEach { character ->
            if (character.isWhitespace()) {
                whitespacePending = isNotEmpty()
            } else {
                if (whitespacePending) append(' ')
                append(character)
                whitespacePending = false
            }
        }
    }
