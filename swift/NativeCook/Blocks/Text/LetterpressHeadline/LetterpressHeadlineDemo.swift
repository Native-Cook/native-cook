import SwiftUI

/// The edition page on the whole screen, the headline printing itself.
struct LetterpressHeadlineDemo: View {
    var body: some View {
        LetterpressHeadline(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    LetterpressHeadlineDemo()
}
