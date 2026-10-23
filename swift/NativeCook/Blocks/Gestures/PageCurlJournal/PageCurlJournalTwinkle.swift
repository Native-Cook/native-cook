import SwiftUI

/// 0 → 1 while the title's sparkles twinkle; 1 is the resting state. Only the sparkles read it.
@MainActor
@Observable
final class PageCurlJournalTwinkle {
    var value = 1.0
}

/// A sparkle that twinkles once when `value` runs 0 → 1: it swells and turns (JournalPages.tsx Twinkle).
/// `delay` staggers it, as a fraction of the run.
struct PageCurlJournalTwinkleView<Content: View>: View {
    let twinkle: PageCurlJournalTwinkle
    let delay: Double
    @ViewBuilder let content: Content

    var body: some View {
        let motion = PageCurlJournalMotion.Twinkle.self
        let u = max(0, min(1, (twinkle.value - delay) / (1 - delay)))
        let swell = sin(Double.pi * u)
        content
            .scaleEffect(1 + (motion.scale - 1) * swell)
            .rotationEffect(.degrees(motion.turnDeg * u))
    }
}
