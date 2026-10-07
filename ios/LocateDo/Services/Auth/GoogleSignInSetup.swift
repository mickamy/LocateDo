import Foundation
import GoogleSignIn

enum GoogleSignInSetup {
    static func configure(bundle: Bundle = .main) {
        guard let configuration = configuration(info: bundle.infoDictionary ?? [:]) else {
            return
        }
        GIDSignIn.sharedInstance.configuration = configuration
    }

    static func configuration(info: [String: Any]) -> GIDConfiguration? {
        guard let clientID = info["LocateDoGoogleClientID"] as? String, !clientID.isEmpty,
              let serverClientID = info["LocateDoGoogleServerClientID"] as? String, !serverClientID.isEmpty else {
            return nil
        }
        return GIDConfiguration(clientID: clientID, serverClientID: serverClientID)
    }

    static var isConfigured: Bool {
        GIDSignIn.sharedInstance.configuration != nil
    }
}
