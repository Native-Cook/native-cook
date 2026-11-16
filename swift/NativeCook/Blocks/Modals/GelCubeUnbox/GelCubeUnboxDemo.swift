import SwiftUI

struct GelCubeUnboxDemo: View {
    static let loop = 7.0
    @Environment(\.dismiss) private var dismiss

    var body: some View {
        GelCubeUnbox(autoPlay: true, onClose: { dismiss() })
    }
}

#Preview {
    GelCubeUnboxDemo()
}
