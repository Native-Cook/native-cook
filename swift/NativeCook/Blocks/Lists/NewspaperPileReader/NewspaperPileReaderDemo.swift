import SwiftUI

/// The morning pile on the whole screen, picking a paper and putting it back by itself.
struct NewspaperPileReaderDemo: View {
    var body: some View {
        NewspaperPileReader(autoPlay: true)
    }
}

#Preview {
    NewspaperPileReaderDemo()
}
