import SwiftUI

/// The sign-up on the whole screen, typing, pulling and feeding sheets by itself.
struct TypewriterSignupDemo: View {
    var body: some View {
        TypewriterSignup(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    TypewriterSignupDemo()
}
