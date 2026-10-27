import SwiftUI

/// Ember Day Review on the whole screen, playing its 10 s loop by itself.
///
/// `-previewTime <seconds>` on the command line holds the loop at that time, for freezing a frame.
struct EmberDayReviewDemo: View {
    var body: some View {
        EmberDayReview(autoPlay: true, previewTime: Self.previewTime)
    }

    private static var previewTime: Double? {
        let value = UserDefaults.standard.object(forKey: "previewTime")
        if let number = value as? Double { return number }
        // Launch arguments arrive as strings.
        if let text = value as? String { return Double(text) }
        return nil
    }
}

#Preview {
    EmberDayReviewDemo()
}
