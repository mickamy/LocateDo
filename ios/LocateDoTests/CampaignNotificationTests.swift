import Foundation
import Testing

@testable import LocateDo

struct CampaignNotificationTests {
    @Test func readsTheCampaignAndItsLink() throws {
        let campaign = try #require(CampaignNotification(userInfo: [
            "campaign_id": "0199bd00-0000-7000-8000-000000000001",
            "url": "https://locatedo.com/news"
        ]))

        #expect(campaign.id == "0199bd00-0000-7000-8000-000000000001")
        #expect(campaign.url == URL(string: "https://locatedo.com/news"))
    }

    @Test func theLinkIsOptional() throws {
        let campaign = try #require(CampaignNotification(userInfo: ["campaign_id": "c"]))

        #expect(campaign.url == nil)
    }

    @Test func onlyHTTPSLinksAreOpened() throws {
        let campaign = try #require(CampaignNotification(userInfo: [
            "campaign_id": "c",
            "url": "http://locatedo.com/news"
        ]))

        #expect(campaign.url == nil)
    }

    @Test func otherNotificationsAreNotCampaigns() {
        #expect(CampaignNotification(userInfo: ["placeID": UUID().uuidString]) == nil)
        #expect(CampaignNotification(userInfo: ["campaign_id": ""]) == nil)
    }
}
