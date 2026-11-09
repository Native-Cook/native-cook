import SwiftUI

/// The complete Farewind trip loader with its eight-second autoplay loop.
struct TripLineLoaderDemo: View {
    var body: some View {
        TripLineLoader(autoPlay: true)
    }
}

#Preview {
    TripLineLoaderDemo()
}
