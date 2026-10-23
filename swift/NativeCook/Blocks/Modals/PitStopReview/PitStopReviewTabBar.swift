import SwiftUI
import UIKit

enum PitStopReviewTab: String, CaseIterable {
    case home = "Home", calendar = "Calendar", results = "Results", circuits = "Circuits", customize = "Customize"

    var symbol: String {
        switch self {
        case .home: "house.fill"
        case .calendar: "calendar"
        case .results: "trophy"
        case .circuits: "globe"
        case .customize: "plus.rectangle.on.rectangle"
        }
    }
}

/// iOS 26 Liquid Glass tab bar: a static regular-glass capsule that refracts the feed scrolling under it, and a lens
/// highlight (a plain view moved by transform only, so the glass never re-lays out) that springs to the active tab.
/// Dragging along the bar lifts the lens (it swells like a magnifier) and it follows the finger; on release it snaps to
/// the nearest tab and selects it. Older systems get a blur under a translucent fill and the same lens as a light pill.
struct PitStopReviewTabBar: View {
    typealias TB = PitStopReviewTheme.TabBar

    /// Lens spring and lift (same as the sibling Paddock dock): duration 0.42 s, damping ratio 0.72.
    static let spring = Animation.spring(duration: 0.42, bounce: 0.28)
    static let quietSlide = Animation.timingCurve(0.23, 1, 0.32, 1, duration: 0.16)
    static let liftIn = Animation.timingCurve(0.23, 1, 0.32, 1, duration: 0.16)
    /// Retained from the canonical LENS token set; Reanimated currently releases with `spring`, as does this port.
    static let liftOut = 0.26
    static let lift = 1.12
    static let stretch = 0.14

    let active: PitStopReviewTab
    let onSelect: (PitStopReviewTab) -> Void
    let scale: CGFloat
    let disabled: Bool
    let quiet: Bool

    @State private var x: Double
    @State private var lift = 1.0
    @State private var dragging = false
    @Environment(\.displayScale) private var displayScale

    private var cell: Double { (TB.width * scale - TB.padding * scale * 2) / Double(PitStopReviewTab.allCases.count) }
    private var index: Int { PitStopReviewTab.allCases.firstIndex(of: active) ?? 0 }

    init(active: PitStopReviewTab, scale: CGFloat, disabled: Bool, quiet: Bool, onSelect: @escaping (PitStopReviewTab) -> Void) {
        self.active = active; self.scale = scale; self.disabled = disabled; self.quiet = quiet; self.onSelect = onSelect
        let cell = (TB.width * scale - TB.padding * scale * 2) / 5
        _x = State(initialValue: Double(PitStopReviewTab.allCases.firstIndex(of: active) ?? 0) * cell)
    }

    var body: some View {
        let width = TB.width * scale, height = TB.height * scale, pad = TB.padding * scale
        let lensW = TB.lensWidth * scale, lensH = TB.lensHeight * scale
        let capsule = Capsule(style: .continuous)
        ZStack(alignment: .topLeading) {
            glass(capsule)
            lens(width: lensW, height: lensH)
                .frame(width: lensW, height: lensH)
                .modifier(PitStopReviewLensPose(x: x, lift: lift, cell: cell, quiet: quiet))
                .offset(x: pad + (cell - lensW) / 2, y: (height - lensH) / 2)
                .allowsHitTesting(false)
                .accessibilityHidden(true)
            HStack(spacing: 0) {
                ForEach(PitStopReviewTab.allCases, id: \.self) { tab in
                    Image(systemName: tab.symbol)
                        .font(.system(size: TB.icon * scale, weight: .medium))
                        .foregroundStyle(TB.iconColor)
                        .frame(width: cell, height: height)
                        .accessibilityElement(children: .ignore)
                        .accessibilityLabel(tab.rawValue)
                        .accessibilityAddTraits(tab == active ? [.isButton, .isSelected] : .isButton)
                        .accessibilityAction { commit(tab) }
                }
            }
            .padding(.horizontal, pad)
            .allowsHitTesting(false)
            PitStopReviewDockTouch(enabled: !disabled, onBegan: began, onMoved: moved, onEnded: ended, onCancelled: finalize)
                .accessibilityHidden(true)
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .clipShape(capsule)
        .overlay { capsule.strokeBorder(Color.white.opacity(0.14), lineWidth: 1 / displayScale).allowsHitTesting(false) }
        .onChange(of: active) { _, _ in
            guard !dragging else { return }
            withAnimation(quiet ? Self.quietSlide : Self.spring) { x = Double(index) * cell }
        }
        .onChange(of: quiet) { _, _ in x = Double(index) * cell; lift = 1 }
        .accessibilityElement(children: .contain)
        .accessibilityAddTraits(.isTabBar)
    }

    @ViewBuilder private func glass(_ shape: Capsule) -> some View {
        if #available(iOS 26.0, *) {
            Color.clear.glassEffect(.regular.tint(Color(.sRGB, red: 16 / 255, green: 14 / 255, blue: 18 / 255, opacity: 0.42)), in: shape)
        } else {
            ZStack {
                Rectangle().fill(.ultraThinMaterial).environment(\.colorScheme, .dark)
                Color(.sRGB, red: 15 / 255, green: 13 / 255, blue: 16 / 255, opacity: 0.68)
            }
        }
    }

    /// A plain highlight with a specular rim (a bright top edge fading down), moved by transform only.
    private func lens(width: CGFloat, height: CGFloat) -> some View {
        ZStack {
            Capsule(style: .continuous).fill(TB.lensFill)
            Capsule(style: .continuous)
                .fill(LinearGradient(stops: [.init(color: .white.opacity(0.30), location: 0), .init(color: .white.opacity(0.04), location: 0.5),
                                             .init(color: .white.opacity(0.10), location: 1)], startPoint: .top, endPoint: .bottom))
                .opacity(0.6)
        }
    }

    private func tabIndex(at point: CGFloat) -> Int? {
        let pad = TB.padding * scale
        let i = Int(floor((point - pad) / cell))
        return point >= pad && i >= 0 && i < PitStopReviewTab.allCases.count ? i : nil
    }
    private func commit(_ tab: PitStopReviewTab) {
        UISelectionFeedbackGenerator().selectionChanged()
        onSelect(tab)
    }

    private func began() {
        if !quiet { withAnimation(Self.liftIn) { lift = Self.lift } }
    }
    private func moved(_ point: CGFloat, travel: CGFloat) {
        if !dragging && abs(travel) > 8 { dragging = true }
        guard dragging else { return }
        var t = Transaction(); t.disablesAnimations = true
        withTransaction(t) { x = min(cell * 4, max(0, Double(point) - TB.padding * scale - cell / 2)) }
    }
    private func ended(_ point: CGFloat) {
        if dragging {
            let i = min(4, max(0, Int((x / cell).rounded())))
            withAnimation(Self.spring) { x = Double(i) * cell }
            commit(PitStopReviewTab.allCases[i])
        } else if let i = tabIndex(at: point) {
            commit(PitStopReviewTab.allCases[i])
        }
        finalize()
    }
    private func finalize() {
        dragging = false
        withAnimation(Self.spring) { lift = 1 }
    }
}

/// The lens stretches with distance from the nearest cell (travel) and swells with the lift; both ride the
/// animated `x` / `lift`, so the spring drives every frame.
private struct PitStopReviewLensPose: ViewModifier, Animatable {
    var x: Double
    var lift: Double
    let cell: Double
    let quiet: Bool

    var animatableData: AnimatablePair<Double, Double> {
        get { .init(x, lift) }
        set { x = newValue.first; lift = newValue.second }
    }

    func body(content: Content) -> some View {
        let travel = quiet ? 0 : min(1, 2 * abs(x / cell - (x / cell).rounded()))
        let l = quiet ? 1 : lift
        content
            .scaleEffect(x: l * (1 + PitStopReviewTabBar.stretch * travel), y: l * (1 - PitStopReviewTabBar.stretch * 0.4 * travel))
            .offset(x: x)
    }
}

/// Touch surface over the bar: a zero-delay press that runs beside the gallery's back-swipe and takes no touches from it.
private struct PitStopReviewDockTouch: UIViewRepresentable {
    var enabled: Bool
    var onBegan: () -> Void
    var onMoved: (CGFloat, CGFloat) -> Void
    var onEnded: (CGFloat) -> Void
    var onCancelled: () -> Void

    func makeUIView(context: Context) -> TouchView {
        let view = TouchView()
        view.backgroundColor = .clear
        let press = UILongPressGestureRecognizer(target: view, action: #selector(TouchView.handle(_:)))
        press.minimumPressDuration = 0
        press.allowableMovement = .greatestFiniteMagnitude
        press.cancelsTouchesInView = false
        press.delegate = view
        view.addGestureRecognizer(press)
        view.press = press
        return view
    }

    func updateUIView(_ view: TouchView, context: Context) {
        view.press?.isEnabled = enabled
        view.onBegan = onBegan; view.onMoved = onMoved; view.onEnded = onEnded; view.onCancelled = onCancelled
    }

    final class TouchView: UIView, UIGestureRecognizerDelegate {
        var press: UILongPressGestureRecognizer?
        var onBegan: () -> Void = {}
        var onMoved: (CGFloat, CGFloat) -> Void = { _, _ in }
        var onEnded: (CGFloat) -> Void = { _ in }
        var onCancelled: () -> Void = {}
        private var startX: CGFloat = 0

        func gestureRecognizer(_ gestureRecognizer: UIGestureRecognizer, shouldRecognizeSimultaneouslyWith other: UIGestureRecognizer) -> Bool { true }

        @objc func handle(_ recognizer: UILongPressGestureRecognizer) {
            let x = recognizer.location(in: self).x
            switch recognizer.state {
            case .began: startX = x; onBegan()
            case .changed: onMoved(x, x - startX)
            case .ended: onEnded(x)
            case .cancelled, .failed: onCancelled()
            default: break
            }
        }
    }
}
