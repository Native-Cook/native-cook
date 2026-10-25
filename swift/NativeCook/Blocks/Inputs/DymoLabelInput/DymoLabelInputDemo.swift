import SwiftUI

/// The labelling screen alone, the label being typed and cut by itself.
struct DymoLabelInputDemo: View {
    var body: some View {
        DymoLabelInput(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    DymoLabelInputDemo()
}
