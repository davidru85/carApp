package com.ruizurraca.carapp

import android.app.Application
import com.google.firebase.FirebaseApp
import com.google.firebase.appcheck.FirebaseAppCheck

/**
 * Process-scoped host for the single application graph.
 *
 * The graph lives here rather than in the Activity because `docs/CONTRACTS.md §9.1` requires every
 * platform trigger to route through *the same in-process* `SyncController`: a WorkManager worker runs
 * without an Activity, so a graph owned by one could not be reached by a background cycle at all, and
 * a second graph built inside the worker would break both the single-controller rule and the
 * single-`DatabaseHandle` rule of `D-89`.
 *
 * Owning it here also matches the iOS host, where the graph already lives for the app's lifetime. The
 * graph is released when the process dies; the Activity consumes it without closing it.
 */
class CarAppApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        FirebaseApp.initializeApp(this)
        FirebaseAppCheck
            .getInstance()
            .installAppCheckProviderFactory(appCheckProviderFactory())
        AndroidAppGraph.install(this)
    }
}
