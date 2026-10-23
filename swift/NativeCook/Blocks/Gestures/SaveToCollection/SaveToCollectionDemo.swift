import SwiftUI

struct SaveToCollectionDemo: View {
    var body: some View {
        SaveToCollection(autoPlay: true)
            .id(SaveToCollectionTokens.qaHoldAt)
            .preferredColorScheme(.dark)
    }
}

#Preview {
    SaveToCollectionDemo()
}
