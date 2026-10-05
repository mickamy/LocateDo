import Foundation

enum LegalLinks {
    static var privacyPolicy: URL {
        if Bundle.main.preferredLocalizations.first == "ja" {
            return URL(string: "https://locatedo.com/privacy-ja")!
        }
        return URL(string: "https://locatedo.com/privacy")!
    }

    static let termsOfUse = URL(string: "https://www.apple.com/legal/internet-services/itunes/dev/stdeula/")!
}
