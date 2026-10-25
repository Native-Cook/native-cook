import SwiftUI

/// The thermostat on the whole screen, turning colder and warmer by itself.
struct ThermostatFrostDialDemo: View {
    var body: some View {
        ThermostatFrostDial(autoPlay: true)
            .background(ThermostatFrostDialPalette.base.ignoresSafeArea())
            .statusBarHidden()
    }
}

#Preview {
    ThermostatFrostDialDemo()
}
