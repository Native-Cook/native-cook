import SwiftUI

struct DayNightToggleDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xe0edf6)) { DayNightToggle(autoPlay: true, backdrop: true) }
    }
}

#Preview {
    DayNightToggleDemo()
}
