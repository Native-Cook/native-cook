import SwiftUI

/// The block has the whole screen: the Adjust page of an invented camera app. In the 9 s film a
/// ghost finger scrubs Exposure, flicks Warmth, scrubs Vignette and double taps the photo to roll
/// the values back. Drag any value sideways to take over (the film returns 2 s after the last touch).
struct ScrubNumberFieldDemo: View {
    var body: some View {
        ScrubNumberField(autoPlay: true)
    }
}

#Preview {
    ScrubNumberFieldDemo()
}
