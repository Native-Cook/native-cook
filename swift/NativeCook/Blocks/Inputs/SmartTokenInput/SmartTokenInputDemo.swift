import SwiftUI

struct SmartTokenInputDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xCFD4D9), height: 260) { SmartTokenInput(autoPlay: true) }
            // The stage stays put when the keyboard comes up.
            .ignoresSafeArea(.keyboard)
    }
}

#Preview {
    SmartTokenInputDemo()
}
