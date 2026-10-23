import SwiftUI

/// The block on the whole screen: the reference's cool gray page, dark status
/// bar, and the notes laid out inside the safe area.
struct FlareNotificationsDemo: View {
  var body: some View {
    FlareNotes()
      .background(FlareTone.page.color.ignoresSafeArea())
      .preferredColorScheme(.light)
  }
}

#Preview {
  FlareNotificationsDemo()
}
