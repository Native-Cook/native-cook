import SwiftUI

/// The product page on the whole screen, squeezing the bottle into the cart by itself.
struct SauceSqueezeCartDemo: View {
    var body: some View {
        SauceSqueezeCart(autoPlay: true)
    }
}

#Preview {
    SauceSqueezeCartDemo()
}
