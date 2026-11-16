import SwiftUI

struct NeonContourMoodDemo: View {
    static let loop = NeonContourMood.loop
    var body: some View {
        NeonContourMood(autoPlay: true)
    }
}

#Preview {
    NeonContourMoodDemo()
}
