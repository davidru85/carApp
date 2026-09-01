import Shared
import SwiftUI

struct ContentView: View {
    @ObservedObject var model: WalkingSkeletonModel
    let graph: SwiftAppGraph

    var body: some View {
        VehicleListView(graph: graph, skeletonModel: model)
    }
}
