import SwiftUI

/// The approved RN sign-in screen, including its independent scan clock and touch physics.
struct ObjectSigninDissolve: View {
    var autoPlay = false
    var onContinue: ((String) -> Void)?
    var onThemeChange: ((String) -> Void)?
    static let loop = 8.0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var driver = ObjectSigninDissolveDriver()
    @GestureState private var gestureActive = false
    @State private var gesturePending = false

    init(autoPlay: Bool = false, onContinue: ((String) -> Void)? = nil, onThemeChange: ((String) -> Void)? = nil) {
        self.autoPlay = autoPlay; self.onContinue = onContinue; self.onThemeChange = onThemeChange
        _ = ObjectSigninDissolveResources.register
    }
    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let l = ObjectSigninDissolveLayout(width: geo.size.width + insets.leading + insets.trailing,
                      height: geo.size.height + insets.top + insets.bottom, top: insets.top, bottom: insets.bottom)
            stage(l)
                .offset(x: -insets.leading, y: -insets.top)
                .onAppear { driver.configure(auto: autoPlay, reduce: reduceMotion, layout: l); driver.start() }
                .onChange(of: geo.size) { driver.configure(auto: autoPlay, reduce: reduceMotion, layout: l) }
                .onChange(of: geo.safeAreaInsets) { driver.configure(auto: autoPlay, reduce: reduceMotion, layout: l) }
                .onChange(of: autoPlay) { driver.configure(auto: autoPlay, reduce: reduceMotion, layout: l) }
                .onChange(of: reduceMotion) {
                    driver.configure(auto: autoPlay, reduce: reduceMotion, layout: l)
                    driver.active(scenePhase == .active)
                }
        }
        .preferredColorScheme(driver.barDark ? .light : .dark)
        .onChange(of: scenePhase) { driver.active(scenePhase == .active) }
        .onDisappear { driver.stop() }
        .onChange(of: gestureActive) {
            if !gestureActive && gesturePending { driver.end(velocity: 0, cancelled: true); gesturePending = false }
        }
    }
    private func stage(_ l: ObjectSigninDissolveLayout) -> some View {
        ZStack(alignment: .topLeading) {
            ObjectSigninDissolveFrame(driver: driver, layout: l, reduce: reduceMotion)
            Color.clear.contentShape(Rectangle())
                .gesture(DragGesture(minimumDistance: 0)
                    .updating($gestureActive) { _, active, _ in active = true }
                    .onChanged { value in
                        if !gesturePending { gesturePending = true; driver.begin(value.startLocation) }
                        driver.move(value.location, translation: value.translation)
                    }
                    .onEnded { value in
                        driver.end(velocity: value.velocity.width); gesturePending = false
                    })
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Pear")
                .accessibilityHint("Swipe up or down to turn it")
                .accessibilityAdjustableAction { direction in driver.bump(direction == .increment ? 1 : -1) }
            Button {
                driver.toggleTheme(); onThemeChange?(driver.isLight ? "light" : "dark")
            } label: { Color.clear.contentShape(Rectangle()) }
                .buttonStyle(.plain)
                .frame(width: 52, height: 52)
                .position(x: l.x(360), y: l.yt(81))
                .accessibilityLabel(driver.isLight ? "Switch to dark theme" : "Switch to light theme")
                .accessibilityValue(driver.isLight ? "On" : "Off")
                .accessibilityAddTraits(.isToggle)
            provider(0, "email", "Continue with email", l.pill)
            provider(1, "passkey", "Passkey", l.half(0))
            provider(2, "phone", "Phone", l.half(1))
        }
        .frame(width: l.width, height: l.height, alignment: .topLeading)
        .clipped()
    }
    private func provider(_ index: Int, _ id: String, _ label: String, _ box: CGRect) -> some View {
        Button { driver.press(); onContinue?(id) } label: { Color.clear.contentShape(Rectangle()) }
            .buttonStyle(ObjectSigninDissolveButtonStyle(index: index, driver: driver))
            .frame(width: box.width, height: box.height)
            .position(x: box.midX, y: box.midY)
            .accessibilityLabel(label)
    }
}
