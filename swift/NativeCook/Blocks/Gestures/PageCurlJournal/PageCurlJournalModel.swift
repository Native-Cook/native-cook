import SwiftUI

/// What SwiftUI needs from the engine: the page on top (changes when a turn lands) and, for the accessibility
/// action, a way to ask for a turn. Per-frame state stays in the engine.
@MainActor
@Observable
final class PageCurlJournalModel {
    var page: Int
    @ObservationIgnored var turn: ((Int) -> Void)?

    init(page: Int = 0) {
        self.page = page
    }
}
