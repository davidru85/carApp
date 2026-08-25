import FirebaseCore
import Shared
import SwiftUI

@main
struct carAppApp: App {
    @StateObject private var model: WalkingSkeletonModel

    init() {
        FirebaseApp.configure()
        let graph = createSwiftAppGraph(isDebugBuild: isDebugBuild)
        _model = StateObject(wrappedValue: WalkingSkeletonModel(graph: graph))
    }

    var body: some Scene {
        WindowGroup {
            ContentView(model: model)
        }
    }
}

private let isDebugBuild: Bool = {
    #if DEBUG
    true
    #else
    false
    #endif
}()
