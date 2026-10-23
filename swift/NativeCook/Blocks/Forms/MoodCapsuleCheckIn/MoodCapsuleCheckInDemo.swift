import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct MoodCapsuleCheckInDemo: View {
    var body: some View {
        MoodCapsuleCheckIn(autoPlay: true)
    }
}

#Preview {
    MoodCapsuleCheckInDemo()
}
