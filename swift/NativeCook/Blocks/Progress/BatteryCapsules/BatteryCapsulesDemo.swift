import SwiftUI

struct BatteryCapsulesDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0x6c8ab3), height: 250) {
            HStack(spacing: 18) {
                BatteryCapsules(autoPlay: true)
                BatteryCapsules(autoPlay: true, variant: .dark)
            }
        }
    }
}

#Preview {
    BatteryCapsulesDemo()
}
