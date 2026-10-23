import SwiftUI

struct LiquidUploadButtonDemo: View {
    var body: some View {
        ComponentStage(background: Color(hex: 0xe1e9f7), height: 250) {
            // The reference shows the four variants together, starting one after another.
            VStack(spacing: 22) {
                HStack(spacing: 24) {
                    LiquidUploadButton(autoPlay: true)
                    LiquidUploadButton(shape: .round, tone: .dark, autoPlay: true, autoPlayDelay: 0.9)
                }
                HStack(spacing: 24) {
                    LiquidUploadButton(shape: .round, tone: .light, autoPlay: true, autoPlayDelay: 1.8)
                    LiquidUploadButton(tone: .dark, autoPlay: true, autoPlayDelay: 2.7)
                }
            }
        }
    }
}

#Preview {
    LiquidUploadButtonDemo()
}
