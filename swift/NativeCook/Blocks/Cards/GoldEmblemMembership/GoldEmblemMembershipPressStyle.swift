import SwiftUI

struct GoldEmblemMembershipPressStyle: PrimitiveButtonStyle {
    let reduced: Bool
    func makeBody(configuration: Configuration) -> some View {
        GoldEmblemMembershipPressContent(configuration: configuration, reduced: reduced)
    }
}
