import SwiftUI

/// The gift story fills the screen and plays by itself. Launch with `-previewTime <seconds>` to freeze one
/// frame of the loop.
struct ConfettiGiftOfferDemo: View {
    var body: some View {
        ConfettiGiftOffer(autoPlay: true, previewTime: Self.previewTime)
    }

    /// `-previewTime <seconds>` on the launch command line (UserDefaults reads it from the argument domain).
    private static var previewTime: Double? {
        let defaults = UserDefaults.standard
        if let value = defaults.object(forKey: "previewTime") as? Double { return value }
        if let text = defaults.string(forKey: "previewTime") { return Double(text) }
        return nil
    }
}

#Preview {
    ConfettiGiftOfferDemo()
}
