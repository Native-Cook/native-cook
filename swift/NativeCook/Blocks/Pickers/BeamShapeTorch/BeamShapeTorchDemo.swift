import SwiftUI

struct BeamShapeTorchDemo: View {
    static let loop = 9.0
    var body: some View {
        BeamShapeTorch(autoPlay: true)
    }
}

#Preview {
    BeamShapeTorchDemo()
}
