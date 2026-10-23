import SwiftUI

struct SaveToCollectionDemo: View {
    var body: some View {
        SaveToCollection(autoPlay: true)
            .preferredColorScheme(.dark)
    }
}

#Preview {
    SaveToCollectionDemo()
}
