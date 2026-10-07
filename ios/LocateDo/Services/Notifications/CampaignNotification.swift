import Foundation

nonisolated struct CampaignNotification: Equatable {
    static let idKey = "campaign_id"
    static let urlKey = "url"

    let id: String
    let url: URL?

    init?(userInfo: [AnyHashable: Any]) {
        guard let id = userInfo[Self.idKey] as? String, !id.isEmpty else {
            return nil
        }
        self.id = id
        var url: URL?
        if let raw = userInfo[Self.urlKey] as? String, let parsed = URL(string: raw), parsed.scheme == "https" {
            url = parsed
        }
        self.url = url
    }
}
