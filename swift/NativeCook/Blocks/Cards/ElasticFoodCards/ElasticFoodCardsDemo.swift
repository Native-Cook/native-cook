import SwiftUI

struct ElasticFoodCardsDemo: View {
    private var previewTime: Double? {
        let args = ProcessInfo.processInfo.arguments
        guard let index = args.firstIndex(of: "-snapshot-time"), index + 1 < args.count,
              let time = Double(args[index + 1]), time.isFinite else { return nil }
        return max(0, time)
    }
    var body: some View {
        ElasticFoodCards(autoPlay: true, previewTime: previewTime)
    }
}
