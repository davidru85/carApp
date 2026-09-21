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
                // also the two cases §9.8 gives the foreground trigger. onAppear covers the launch
                // one, because a cold launch that is already .active when the scene is installed
                // never delivers an onChange for it; the scene-phase change covers every later
                // return. Both call the same single entry point.
                //
                // The launch call passes a literal nil rather than the tracker's reading: at launch
                // "there was no departure" and "nothing was recorded" are the same fact, and nil is
                // the cold start §9.8 names as a trigger in its own right.
                .onAppear {
                    model.onSceneActivated(backgroundMillis: nil)
                }
                .onChange(of: scenePhase) { newPhase in
                    // Every non-.active phase records the departure, including a transient
                    // .inactive from a system dialog: the tracker keeps the earliest moment, so an
                    // .inactive that is followed by .background measures from the first departure
                    // rather than from the later one. Recording on .inactive alone would also drop
                    // the reminder evaluation of a return that never reached .background, which is
                    // what this host did before the duration existed.
                    //
                    // Recording is not a trigger. The §9.8 threshold is applied inside
                    // `SyncStateHolder`, so returning from a dialog reports a stay far below five
                    // minutes and requests no cycle; only a real background stay does.
                    if newPhase == .active {
                        // The entry point is called unconditionally, exactly as the Android host calls
                        // it on every ON_START. `consumeBackgroundMillis()` returning nil means "no
                        // recorded departure", which is the cold-start case §9.8 names as a trigger in
                        // its own right, and the §11.3 reminder evaluation MUST NOT be conditional on
                        // a value that only the sync trigger reads.
                        model.onSceneActivated(backgroundMillis: backgroundDuration.consumeBackgroundMillis())
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
