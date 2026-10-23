import SwiftUI

/// One row of the gallery. The list is in `BlockRegistry.swift`, written from `catalog/`.
struct Block: Identifiable {
    let slug: String
    let name: String
    let category: String
    let spec: String
    let demo: () -> AnyView

    var id: String { slug }
}
