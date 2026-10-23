import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct FallingWordStickersDemo: View {
    var body: some View {
        FallingWordStickers(autoPlay: true)
    }
}

#Preview {
    FallingWordStickersDemo()
}
