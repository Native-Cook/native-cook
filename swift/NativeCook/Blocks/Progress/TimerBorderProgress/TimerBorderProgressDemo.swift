import SwiftUI

struct TimerBorderProgressDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xe7e7e7), height: 320) { TimerBorderProgress(autoPlay: true) }
    }
}

#Preview {
    TimerBorderProgressDemo()
}
