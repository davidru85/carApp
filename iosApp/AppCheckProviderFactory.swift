import FirebaseAppCheck
import FirebaseCore

private final class CarAppAppCheckProviderFactory: NSObject, AppCheckProviderFactory {
    func createProvider(with app: FirebaseApp) -> AppCheckProvider? {
        AppAttestProvider(app: app)
    }
}

func configureAppCheck() {
    #if DEBUG && targetEnvironment(simulator)
    AppCheck.setAppCheckProviderFactory(AppCheckDebugProviderFactory())
    #else
    AppCheck.setAppCheckProviderFactory(CarAppAppCheckProviderFactory())
    #endif
}
