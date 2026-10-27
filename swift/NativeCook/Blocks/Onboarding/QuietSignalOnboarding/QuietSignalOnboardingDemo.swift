import SwiftUI

/// The tour playing by itself on the whole screen. The launch argument `-previewTime <seconds>` freezes it at
/// that time of the loop, for inspecting a frame.
struct QuietSignalOnboardingDemo: View {
    var body: some View {
        QuietSignalOnboarding(autoPlay: true, previewTime: Self.launchPreviewTime)
    }

    /// `UserDefaults.standard.object(forKey: "previewTime") as? Double`; a launch argument arrives as text, so
    /// that is read too.
    private static var launchPreviewTime: Double? {
        let value = UserDefaults.standard.object(forKey: "previewTime")
        if let number = value as? Double { return number }
        if let text = value as? String { return Double(text) }
        return nil
    }
}

#Preview {
    QuietSignalOnboardingDemo()
}
