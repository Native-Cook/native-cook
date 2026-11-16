import SwiftUI

/// The approved RN savings screen; all drawing uses the source's absolute baselines.
struct PourJarGoal: View {
    var autoPlay: Bool
    static let loop = 8.0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.dynamicTypeSize) private var dynamicTypeSize
    @Environment(\.scenePhase) private var scenePhase
    @State private var driver = PourJarGoalDriver()

    init(autoPlay: Bool = false) {
        self.autoPlay = autoPlay
        _ = PourJarGoalResources.register
    }
    private var pinnedTime: Double? {
        #if DEBUG
        let args = ProcessInfo.processInfo.arguments
        if let i = args.firstIndex(of: "-pourJarGoalTime"), i + 1 < args.count,
           let value = Double(args[i + 1]), value.isFinite, value >= 0 { return min(value, Self.loop) }
        #endif
        return nil
    }
    private var fontScale: Double {
        // RCTAccessibilityManager's default iOS multipliers, not SwiftUI's text-style scaling.
        switch dynamicTypeSize {
        case .xSmall: return 0.823
        case .small: return 0.882
        case .medium: return 0.941
        case .large: return 1
        case .xLarge: return 1.118
        case .xxLarge: return 1.235
        case .xxxLarge: return 1.353
        case .accessibility1: return 1.786
        case .accessibility2: return 2.143
        case .accessibility3: return 2.643
        case .accessibility4: return 3.143
        case .accessibility5: return 3.571
        @unknown default: return 1
        }
    }
    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + insets.leading + insets.trailing,
                              height: geo.size.height + insets.top + insets.bottom)
            let layout = PourJarGoalLayout(width: size.width, height: size.height,
                                          top: insets.top, bottom: insets.bottom, fontScale: fontScale)
            PourJarGoalStage(driver: driver, layout: layout, reduced: reduceMotion)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(PourJarGoalResources.color(0xFBF1EE).ignoresSafeArea())
        .preferredColorScheme(.light)
        .onAppear { configure() }
        .onDisappear { driver.stop() }
        .onChange(of: autoPlay) { configure() }
        .onChange(of: reduceMotion) { configure() }
        .onChange(of: scenePhase) { configure() }
    }
    private func configure() {
        driver.configure(autoPlay: autoPlay, reduced: reduceMotion, pin: pinnedTime, active: scenePhase == .active)
    }
}

private struct PourJarGoalStage: View {
    let driver: PourJarGoalDriver
    let layout: PourJarGoalLayout
    let reduced: Bool
    @State private var autoEnabled = false
    @State private var fonts: PourJarGoalResources.Fonts?
    var body: some View {
        Group {
            if layout.largestType {
                PourJarGoalScroll(size: CGSize(width: layout.width, height: layout.height), content: content)
            } else { content }
        }
        .onChange(of: layout, initial: true) { fonts = PourJarGoalResources.Fonts(layout) }
    }
    private var content: some View {
        ZStack(alignment: .topLeading) {
            if let fonts {
                PourJarGoalFrame(driver: driver, layout: layout, fonts: fonts, reduced: reduced, autoEnabled: autoEnabled)
            }
            PourJarGoalControls(driver: driver, layout: layout, autoEnabled: $autoEnabled)
        }
        .frame(width: layout.width, height: layout.height, alignment: .topLeading)
    }
}

private struct PourJarGoalControls: View {
    let driver: PourJarGoalDriver
    let layout: PourJarGoalLayout
    @Binding var autoEnabled: Bool
    @Environment(\.dismiss) private var dismiss
    @State private var showOptions = false

    var body: some View {
        let l = layout, s = l.s, scene = driver.scene
        ZStack(alignment: .topLeading) {
            target("Back to goals", CGRect(x: l.x(19), y: l.headerY, width: 44, height: 44)) { dismiss() }
            target("Goal options", CGRect(x: l.x(339), y: l.headerY, width: 44, height: 44)) { showOptions = true }
            PourJarGoalTouchSurface(
                began: { points in driver.drag(points, width: l.jar.width, height: l.jar.height, began: true) },
                moved: { points in driver.drag(points, width: l.jar.width, height: l.jar.height, began: false) },
                ended: { vy in driver.endDrag(velocityY: vy, height: l.jar.height) },
                cancelled: { driver.cancel() })
                .frame(width: l.jar.width, height: l.jar.height)
                .offset(x: l.jar.minX, y: l.jar.minY)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Sora, spring 2027. \(PourJarGoalMotion.money(scene.accessibleValue)) of $3,100. \(Int(floor(scene.accessibleValue / 3100 * 100))) percent.")
                .accessibilityValue("\(Int(scene.accessibleValue))")
                .accessibilityHint("Savings goal. Drag the liquid to add money. Swipe up or down to adjust, then activate to pour.")
                .accessibilityAdjustableAction { direction in
                    if direction == .increment { driver.adjust(1) }
                    else if direction == .decrement { driver.adjust(-1) }
                }
                .accessibilityAction { driver.adjust(0, confirm: true) }
            Color.clear
                .frame(width: 358 * s, height: l.cardH)
                .offset(x: l.x(22), y: l.cardsY)
                .allowsHitTesting(false)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Left to go, \(PourJarGoalMotion.money(3100 - scene.accessibleValue)). At $40 a week, \(Int(ceil(max(0, (3100 - scene.accessibleValue).rounded()) / 40))) weeks.")
            PourJarGoalTouchSurface(began: { _ in driver.beginPour() }, moved: { _ in },
                                    ended: { _ in driver.endPour() }, cancelled: { driver.cancel() })
                .frame(width: 234 * s, height: max(48, l.buttonH))
                .offset(x: l.x(22), y: l.buttonY)
                .accessibilityElement(children: .ignore)
                .accessibilityAddTraits(.isButton)
                .accessibilityLabel("Pour in")
                .accessibilityHint("Tap to add $100, or hold to keep pouring")
                .accessibilityAction { driver.adjust(10, confirm: true) }
            target("Automatic weekly saving", CGRect(x: l.x(266), y: l.buttonY, width: 114 * s, height: max(48, l.buttonH))) {
                autoEnabled.toggle()
            }
            .accessibilityRepresentation { Toggle("Automatic weekly saving", isOn: $autoEnabled) }
            if scene.hasUndo {
                let toastH = fontsToastSize * 3.5 + 20 * s
                let rect = l.largeType
                    ? CGRect(x: l.jar.minX, y: l.jar.minY + 17 * s, width: l.jar.width, height: max(48, toastH))
                    : CGRect(x: l.x(63), y: l.headerY + 22 * s - 24, width: l.x(339) - l.x(63), height: 48)
                target("Undo last pour", rect) { driver.undo() }
            }
        }
        .frame(width: l.width, height: l.height, alignment: .topLeading)
        .alert("Sora, spring 2027", isPresented: $showOptions) {
            Button("OK", role: .cancel) { }
        } message: { Text("Goal: $3,100\nWeekly saving: $40") }
    }
    private var fontsToastSize: Double { 14 * layout.s * (layout.largeType ? layout.fontScale : 1) }
    private func target(_ label: String, _ rect: CGRect, action: @escaping () -> Void) -> some View {
        Button(action: action) { Color.clear.contentShape(Rectangle()) }
            .buttonStyle(.plain)
            .frame(width: rect.width, height: rect.height)
            .offset(x: rect.minX, y: rect.minY)
            .accessibilityLabel(label)
    }
}
