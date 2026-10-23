import SwiftUI

struct DonationFanCarouselDemo: View {
    var body: some View {
        DonationFanCarousel(autoPlay: true, freezeAt: freezeAt)
    }

    private var freezeAt: Double? {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if let index = args.firstIndex(of: "-freezeAt"), args.indices.contains(index + 1),
           let time = Double(args[index + 1]), time.isFinite, time >= 0 {
            return time
        }
        #endif
        return nil
    }
}

#Preview {
    DonationFanCarouselDemo()
}
