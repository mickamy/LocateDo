//
//  Item.swift
//  LocateDo
//
//  Created by tetsuro.mikami on 2026/10/03.
//

import Foundation
import SwiftData

@Model
final class Item {
    var timestamp: Date
    
    init(timestamp: Date) {
        self.timestamp = timestamp
    }
}
