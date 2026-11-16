import SwiftUI

struct ObjectSigninDissolveButtonStyle: ButtonStyle {
    let index: Int
    let driver: ObjectSigninDissolveDriver
    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .onChange(of: configuration.isPressed) { driver.pressed = configuration.isPressed ? index : -1 }
    }
}
