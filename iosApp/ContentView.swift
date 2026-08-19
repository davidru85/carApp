import SwiftUI
import Shared

struct ContentView: View {
    let greeting = Greeting().greet(platform: "iOS")

    var body: some View {
        VStack {
            Image(systemName: "car.fill")
                .imageScale(.large)
                .foregroundStyle(.tint)
            Text(greeting)
                .font(.title)
                .padding()
        }
        .padding()
    }
}

#Preview {
    ContentView()
}