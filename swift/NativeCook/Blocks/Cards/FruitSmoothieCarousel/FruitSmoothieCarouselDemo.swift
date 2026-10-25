import SwiftUI

struct FruitSmoothieCarouselDemo: View {
    private var snapshot: Double? {
        let args = ProcessInfo.processInfo.arguments
        guard let index = args.firstIndex(of: "-snapshot-time"), args.indices.contains(index+1) else { return nil }
        return Double(args[index+1])
    }
    var body: some View { FruitSmoothieCarousel(autoPlay: true, previewTime: snapshot) }
}
