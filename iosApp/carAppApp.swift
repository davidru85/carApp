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
                // Launch and foreground return are the two evaluation moments of D-146. onAppear
                // covers the launch one on its own, because a cold launch that is already .active
                // when the scene is installed never delivers an onChange for it. The scene-phase
                // change covers every later return. Both call the same single entry point, and the
                // shared holder collapses a duplicate call: an evaluation still in flight is
                // skipped, and a completed one has already consumed the index it published.
                .onAppear {
                    model.evaluateAnonymousReminder()
                }
                .onChange(of: scenePhase) { newPhase in
                    if newPhase == .active {
                        model.evaluateAnonymousReminder()
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
