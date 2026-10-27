package com.ruizurraca.carapp

import com.google.firebase.appcheck.AppCheckProviderFactory
import com.google.firebase.appcheck.playintegrity.PlayIntegrityAppCheckProviderFactory

/**
 * `D-259`: staging is release code, so it uses the release App Check provider. The debug provider lives
 * in the `debug` source set and is deliberately unreachable here: a staging build must never be able to
 * pass App Check with a debug token.
 */
internal fun appCheckProviderFactory(): AppCheckProviderFactory = PlayIntegrityAppCheckProviderFactory.getInstance()
