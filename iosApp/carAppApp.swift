import FirebaseCore
import FirebaseAppCheck
import Shared
import SwiftUI

@main
struct carAppApp: App {
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
