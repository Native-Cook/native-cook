import SwiftUI

struct HabitRingDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x25272c), height: 450) { HabitRing(autoPlay: true) }
    }
}

#Preview {
    HabitRingDemo()
}
