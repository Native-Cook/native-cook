import SwiftUI

/// The block has the whole screen: the help centre of an invented bank app, Alder. One question is
/// open at a time; in the 9 s film a ghost finger opens the third, the fifth and the first again.
/// Tap any question to take over (the film returns 2 s after the last touch).
struct SpringDisclosureFaqDemo: View {
    var body: some View {
        SpringDisclosureFaq(autoPlay: true)
    }
}

#Preview {
    SpringDisclosureFaqDemo()
}
