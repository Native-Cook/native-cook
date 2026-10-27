import SwiftUI

/// The Silver Screen welcome on the whole screen, drifting by itself. Launching the app with
/// `-previewTime <seconds>` freezes it at that moment of the loop.
struct SilverScreenCarouselDemo: View {
    var body: some View {
        SilverScreenCarousel(autoPlay: true, previewTime: Self.previewTime)
    }

    /// The `-previewTime` launch argument, if any (UserDefaults reads it from the argument domain,
    /// where a number may arrive as text).
    private static var previewTime: Double? {
        let value = UserDefaults.standard.object(forKey: "previewTime")
        if let number = value as? Double { return number }
        if let text = value as? String { return Double(text) }
        return nil
    }
}

#Preview {
    SilverScreenCarouselDemo()
}
