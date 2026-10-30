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
        // `E4-03` (`D-251`): must start before the first scene becomes active.
        LaunchMeasurement.shared.begin()
        configureAppCheck()
        configureFirebase()
        let graph = createSwiftAppGraph(isDebugBuild: isDebugBuild)
        self.graph = graph
        _model = StateObject(wrappedValue: WalkingSkeletonModel(graph: graph))
    }

    var body: some Scene {
        WindowGroup {
            ContentView(model: model, graph: graph)
                .benchmarkSeedingIfRequested(graph: graph)
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
    // `D-259`: the plist is selected by configuration, not by `#if DEBUG`, because Staging is release
    // code with the development project and `DEBUG` cannot tell the two apart. Debug and Staging each
    // bundle their own development plist (`D-261`); Release bundles the production one that `E4-10` adds,
    // and the build script of `D-238` refuses to package a configuration without its plist.
    let resourceName = firebaseConfigurationResourceName(configuration: currentConfiguration)

    guard
        let configPath = Bundle.main.path(forResource: resourceName, ofType: "plist"),
        let options = FirebaseOptions(contentsOfFile: configPath)
    else {
        fatalError("The \(currentConfiguration) Firebase configuration is missing from the application bundle.")
    }
    FirebaseApp.configure(options: options)
}

/// The configuration name Xcode compiled, so the plist choice follows the build settings.
///
/// `project.yml` injects `CARAPP_CONFIGURATION = $(CONFIGURATION)` into `Info.plist`, because Xcode does
/// not copy `CONFIGURATION` there by itself. The fallback keeps a unit-test bundle usable.
private let currentConfiguration: String =
    (Bundle.main.infoDictionary?["CARAPP_CONFIGURATION"] as? String) ?? "Debug"

/// The plist each configuration bundles. Kept as a free function so it is testable without a bundle.
func firebaseConfigurationResourceName(configuration: String) -> String {
    switch configuration {
    case "Debug":
        "GoogleService-Info-Debug"
    // `E4-03` (`D-253`): Benchmark is the measured derivative of Staging, so it loads the Staging plist.
    case "Staging", "Benchmark":
        "GoogleService-Info-Staging"
    default:
        "GoogleService-Info"
    }
}

private let isDebugBuild: Bool = {
    #if DEBUG
    true
    #else
    false
    #endif
}()
