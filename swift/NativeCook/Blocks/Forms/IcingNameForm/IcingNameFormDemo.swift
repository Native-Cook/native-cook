import SwiftUI

/// The order screen alone, the message being written by itself.
struct IcingNameFormDemo: View {
    var body: some View {
        IcingNameForm(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    IcingNameFormDemo()
}
