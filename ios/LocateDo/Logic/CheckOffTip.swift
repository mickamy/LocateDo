import SwiftUI
import TipKit

struct CheckOffTip: Tip {
    @Parameter static var openedFromArrival: Bool = false

    var title: Text {
        Text(.placeDetailCheckOffTipTitle)
    }

    var message: Text? {
        Text(.placeDetailCheckOffTipMessage)
    }

    var image: Image? {
        Image(systemName: "hand.tap")
    }

    var rules: [Rule] {
        #Rule(Self.$openedFromArrival) {
            $0 == true
        }
    }

    var options: [any TipOption] {
        MaxDisplayCount(1)
    }
}
