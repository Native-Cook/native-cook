import SwiftUI

struct MenuProductCarouselDemo: View {
    var body: some View {
        MenuProductCarousel(autoPlay: freezeAt == nil, freezeAt: freezeAt)
    }

    private var freezeAt: Double? {
        #if DEBUG
        let arguments = ProcessInfo.processInfo.arguments
        if let index = arguments.firstIndex(of: "-freezeAt"), arguments.indices.contains(index + 1),
           let time = Double(arguments[index + 1]), time.isFinite, time >= 0 {
            return time
        }
        #endif
        return nil
    }
}

#Preview {
    MenuProductCarouselDemo()
}
