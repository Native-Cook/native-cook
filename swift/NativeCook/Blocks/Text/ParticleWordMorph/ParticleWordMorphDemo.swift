import SwiftUI

/// The particle words alone on their night, cycling by themselves.
struct ParticleWordMorphDemo: View {
    var body: some View {
        ParticleWordMorph(autoPlay: true)
            .background(Color(hex: 0x060a1c).ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    ParticleWordMorphDemo()
}
