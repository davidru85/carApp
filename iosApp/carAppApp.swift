import FirebaseCore
import FirebaseAppCheck
import GoogleSignIn
import Shared
import SwiftUI

@main
struct carAppApp: App {
    @Environment(\.scenePhase) private var scenePhase
    @StateObject private var model: WalkingSkeletonModel
    private let graph: SwiftAppGraph
    private let backgroundDuration = SceneBackgroundDuration()

    init() {
        configureAppCheck()
        configureFirebase()
        let graph = createSwiftAppGraph(isDebugBuild: isDebugBuild)
        self.graph = graph
        _model = StateObject(wrappedValue: WalkingSkeletonModel(graph: graph))
    }

    var body: some Scene {
        WindowGroup {
            ContentView(model: model, graph: graph)
                .onOpenURL { url in
                    GIDSignIn.sharedInstance.handle(url)
                }
                // Launch and foreground return are the two evaluation moments of D-146, and they are
                // also the two cases §9.8 gives the foreground trigger. onAppear emits the one process
                // cold start; the scene-phase change emits only a return for which a departure was
                // actually recorded.
                //
                // The cold-start trigger is consumed rather than assumed, because a cold launch that is
                // already .active when the scene is installed delivers an onChange for it as well.
                // Reporting the launch twice would request a second AppForeground and manufacture an
                // immediate follow-up cycle for a transition that never happened.
                .onAppear {
                    if backgroundDuration.consumeColdStart() {
                        model.onSceneActivated(backgroundMillis: nil)
                    }
                }
                .onChange(of: scenePhase) { newPhase in
                    if newPhase == .active {
                        // Only a recorded departure is a foreground return. Recording happens on every
                        // non-.active phase, including a transient .inactive from a system dialog, so a
                        // return that never reached .background is still measured from its first
                        // departure. A phase change with no recorded departure is the launch, which
                        // onAppear already reported, so it is not a trigger and the §11.3 reminder
                        // evaluation is not repeated for it.
                        guard let elapsed = backgroundDuration.consumeBackgroundMillis() else { return }
                        model.onSceneActivated(backgroundMillis: elapsed)
                    } else {
                        backgroundDuration.recordDeparture()
                    }
                }
        }
    }
}

private func configureFirebase() {
    #if DEBUG
    guard
        let configPath = Bundle.main.path(forResource: "GoogleService-Info-Debug", ofType: "plist"),
        let options = FirebaseOptions(contentsOfFile: configPath)
    else {
        fatalError("The Debug Firebase configuration is missing from the application bundle.")
    }
    FirebaseApp.configure(options: options)
    #else
    FirebaseApp.configure()
    #endif
}

private let isDebugBuild: Bool = {
    #if DEBUG
    true
    #else
    false
    #endif
}()
