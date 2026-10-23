import SwiftUI

struct MoodShapeMixerDemo: View {
    var body: some View {
        MoodShapeMixer(autoPlay: true, renderTime: UserDefaults.standard.object(forKey: "block-time") as? Double)
    }
}

#Preview {
    MoodShapeMixerDemo()
}
