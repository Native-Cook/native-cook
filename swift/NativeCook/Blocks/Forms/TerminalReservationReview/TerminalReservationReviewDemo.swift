import SwiftUI

struct TerminalReservationReviewDemo: View {
    var body: some View {
        TerminalReservationReview(autoPlay: true, renderTime: UserDefaults.standard.object(forKey: "block-time") as? Double)
    }
}

#Preview { TerminalReservationReviewDemo() }
