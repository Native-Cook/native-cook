import SwiftUI

struct SeasonTierMedalsClaim: View {
    let image: UIImage
    var action: () -> Void
    var body: some View {
        Button(action: action) { Image(uiImage: image).resizable() }
            .buttonStyle(Style())
            .accessibilityLabel("Claim 7% back on clothing and shoes")
    }
    private struct Style: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label.scaleEffect(configuration.isPressed ? 0.97 : 1)
                .opacity(configuration.isPressed ? 0.9 : 1)
        }
    }
}
