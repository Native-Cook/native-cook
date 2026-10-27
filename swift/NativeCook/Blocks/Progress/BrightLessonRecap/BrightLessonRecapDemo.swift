import SwiftUI

/// The lesson recap on the whole screen, playing by itself. The launch argument `-previewTime <seconds>`
/// freezes any frame of the loop.
struct BrightLessonRecapDemo: View {
    private let previewTime: Double? = {
        let value = UserDefaults.standard.object(forKey: "previewTime")
        if let number = value as? Double { return number }
        if let text = value as? String { return Double(text) }
        return nil
    }()

    var body: some View {
        BrightLessonRecap(autoPlay: true, previewTime: previewTime)
    }
}

#Preview {
    BrightLessonRecapDemo()
}
