import SwiftUI

struct CardVacuumSealDemo: View {
    static let loop = CardVacuumSeal.loop
    var body: some View {
        CardVacuumSeal(autoPlay: true)
    }
}

#Preview {
    CardVacuumSealDemo()
}
