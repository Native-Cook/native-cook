import SwiftUI
struct ArrowRankCardsDemo: View {
    static let loop=9.0
    var body: some View {
        #if DEBUG
        ArrowRankCards(autoPlay: !UserDefaults.standard.bool(forKey:"arrowRankCardsManual"))
        #else
        ArrowRankCards(autoPlay:true)
        #endif
    }
}
#Preview { ArrowRankCardsDemo() }
