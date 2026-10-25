import SwiftUI

/// The supplies drawer fills the screen, opening and closing by itself.
struct DrawerCabinetSheetDemo: View {
    var body: some View {
        DrawerCabinetSheet(autoPlay: true)
            .statusBarHidden()
    }
}

#Preview {
    DrawerCabinetSheetDemo()
}
