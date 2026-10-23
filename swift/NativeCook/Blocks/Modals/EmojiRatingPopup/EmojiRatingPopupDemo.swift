import SwiftUI

/// The block fills the screen and keeps the status bar, so it is shown without a stage.
struct EmojiRatingPopupDemo: View {
    var body: some View {
        EmojiRatingPopup(autoPlay: true)
    }
}

#Preview {
    EmojiRatingPopupDemo()
}
