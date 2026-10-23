import SwiftUI

enum PricingValue: Equatable {
    case free, monthly, annual
}

/// Pricing switch. "Free | Premium" where choosing Premium slides the black pill across and opens it
/// into a nested "Monthly | Annual" switch with its own white pill; choosing Free closes it again.
struct PricingSegmentedButton: View {
    /// Controlled selection. Leave `nil` to let the control keep its own.
    var value: Binding<PricingValue>? = nil
    var freeLabel = "Free"
    var premiumLabel = "Premium"
    var monthlyLabel = "Monthly"
    var annualLabel = "Annual"
    /// Walks Free → Monthly → Annual → Monthly → Free by itself, pausing on each.
    var autoPlay = false
    var onValueChange: (PricingValue) -> Void = { _ in }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var inner = PricingValue.free
    /// Premium reopens on whichever billing period was chosen last.
    @State private var lastBilling = PricingValue.monthly
    @State private var pressed = false
    /// Which way autoPlay is walking through the options.
    @State private var forward = true

    private var selected: PricingValue { value?.wrappedValue ?? inner }
    private var premium: Bool { selected != .free }
    private var billingChoice: PricingValue { selected == .free ? lastBilling : selected }

    var body: some View {
        Face(
            plan: premium ? 1 : 0, billing: billingChoice == .annual ? 1 : 0,
            freeLabel: freeLabel, premiumLabel: premiumLabel, monthlyLabel: monthlyLabel, annualLabel: annualLabel
        )
        .animation(reduceMotion ? nil : .timingCurve(0.65, 0, 0.35, 1, duration: Metrics.planSeconds), value: premium)
        .animation(
            reduceMotion ? nil : .timingCurve(0.65, 0, 0.35, 1, duration: Metrics.billingSeconds), value: billingChoice
        )
        .frame(width: Metrics.width, height: Metrics.height)
        .overlay {
            // The three hit areas stay in place whatever is selected, so a press is never cut short
            // by its own button being swapped out. While Free is selected the two right-hand
            // quarters both mean "Premium", and only the first is announced.
            HStack(spacing: 0) {
                segment(freeLabel, selected: !premium, width: Metrics.shift) { select(.free) }
                segment(premium ? monthlyLabel : premiumLabel, selected: selected == .monthly, width: Metrics.shift / 2) {
                    select(premium ? .monthly : lastBilling)
                }
                segment(annualLabel, selected: selected == .annual, width: Metrics.shift / 2) {
                    select(premium ? .annual : lastBilling)
                }
                .accessibilityHidden(!premium)
            }
        }
        .scaleEffect(pressed ? 0.97 : 1)
        .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: pressed)
        .sensoryFeedback(.impact(weight: .light), trigger: pressed) { _, pressed in pressed }
        .accessibilityElement(children: .contain)
        // Re-armed after every change, so a tap simply restarts the pause.
        .task(id: AutoKey(selected: selected, active: autoPlay && !reduceMotion && !pressed)) {
            guard autoPlay, !reduceMotion, !pressed else { return }
            try? await Task.sleep(for: .seconds(Metrics.autoPauseSeconds))
            if Task.isCancelled { return }
            if selected == .free { forward = true }
            if selected == .annual { forward = false }
            select(selected == .monthly ? (forward ? .annual : .free) : .monthly)
        }
    }

    private func select(_ next: PricingValue) {
        guard next != selected else { return }
        if next != .free { lastBilling = next }
        if let value { value.wrappedValue = next } else { inner = next }
        onValueChange(next)
    }

    private func segment(_ label: String, selected: Bool, width: Double, action: @escaping () -> Void) -> some View {
        Button(action: action) { Text(label) }
            .buttonStyle(SegmentStyle(pressed: $pressed))
            .frame(width: width, height: Metrics.height)
            .accessibilityLabel(label)
            .accessibilityAddTraits(selected ? .isSelected : [])
    }

    private struct AutoKey: Equatable {
        let selected: PricingValue
        let active: Bool
    }

    /// An invisible hit area that reports its press state; everything visible is drawn by `Face`.
    private struct SegmentStyle: ButtonStyle {
        @Binding var pressed: Bool

        func makeBody(configuration: Configuration) -> some View {
            Color.clear
                .contentShape(Rectangle())
                .onChange(of: configuration.isPressed) { _, isPressed in pressed = isPressed }
        }
    }
}

private enum Metrics {
    static let width = 336.0
    static let height = 48.0
    /// Gap between the track and the black pill, and between the black pill and the white one.
    static let inset = 3.0
    static let innerInset = 2.0
    static let pillWidth = width / 2 - inset * 2
    static let pillHeight = height - inset * 2
    /// How far the black pill travels between Free and Premium.
    static let shift = width / 2
    static let knobWidth = (pillWidth - innerInset * 2) / 2
    static let knobHeight = pillHeight - innerInset * 2
    static let knobX = shift + inset + innerInset
    /// The canvas overflows the control by this much on every side to fit the drop shadow.
    static let bleed = 48.0
    static let planSeconds = 0.48
    static let billingSeconds = 0.38
    static let autoPauseSeconds = 1.5
    static let stretch = 1.4
}

private func seg(_ t: Double, _ from: Double, _ to: Double) -> Double {
    min(1, max(0, (t - from) / (to - from)))
}

// A pill's two ends follow the same progress on different curves, so whichever end faces the
// direction of travel moves first and the pill stretches on the way, in both directions.
private func lead(_ x: Double) -> Double { 1 - pow(1 - min(max(x, 0), 1), Metrics.stretch) }
private func lag(_ x: Double) -> Double { pow(min(max(x, 0), 1), Metrics.stretch) }

private func gray(_ from: Double, _ to: Double, _ t: Double) -> Color {
    Color(.sRGB, white: (from + (to - from) * t) / 255, opacity: 1)
}

/// `Animatable` so SwiftUI's own animations can drive the numbers the `Canvas` and the labels read.
private struct Face: View, Animatable {
    /// 0 = Free, 1 = Premium.
    var plan: Double
    /// 0 = Monthly, 1 = Annual.
    var billing: Double
    let freeLabel: String
    let premiumLabel: String
    let monthlyLabel: String
    let annualLabel: String

    var animatableData: AnimatablePair<Double, Double> {
        get { AnimatablePair(plan, billing) }
        set {
            plan = newValue.first
            billing = newValue.second
        }
    }

    var body: some View {
        // The white pill grows into place as the black one arrives.
        let open = seg(plan, 0.4, 1)
        // "Premium" lifts away while the two billing labels grow out of its small print.
        let gone = seg(plan, 0, 0.5)

        ZStack(alignment: .topLeading) {
            Canvas { context, _ in
                draw(context, open: open)
            }
            .padding(-Metrics.bleed)

            Text(freeLabel)
                .foregroundStyle(gray(255, 179, plan))
                .frame(width: Metrics.shift, height: Metrics.height)

            VStack(spacing: -1) {
                Text(premiumLabel)
                    .foregroundStyle(Color(hex: 0xb3b3b3))
                Text("\(monthlyLabel) · \(annualLabel)")
                    .font(.system(size: 8.5, weight: .medium))
                    .foregroundStyle(Color(hex: 0xcdcdcd))
            }
            .frame(width: Metrics.shift, height: Metrics.height)
            .opacity(1 - gone)
            .offset(x: Metrics.shift, y: -6 * gone)

            billingLabel(monthlyLabel, color: gray(0, 138, billing), open: open)
                .offset(x: Metrics.knobX)
            billingLabel(annualLabel, color: gray(138, 0, billing), open: open)
                .offset(x: Metrics.knobX + Metrics.knobWidth)
        }
        .font(.system(size: 14, weight: .semibold))
        .lineLimit(1)
        .frame(width: Metrics.width, height: Metrics.height, alignment: .topLeading)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }

    private func billingLabel(_ text: String, color: Color, open: Double) -> some View {
        Text(text)
            .foregroundStyle(color)
            .scaleEffect(0.6 + 0.4 * open)
            .offset(y: 9 * (1 - open))
            .opacity(open)
            .frame(width: Metrics.knobWidth, height: Metrics.height)
    }

    private func pill(left: Double, right: Double, height: Double, scale: Double) -> Path {
        let w = (right - left) * scale
        let h = height * scale
        return capsule(CGRect(
            x: Metrics.bleed + (left + right) / 2 - w / 2, y: Metrics.bleed + (Metrics.height - h) / 2,
            width: w, height: h
        ))
    }

    private func draw(_ context: GraphicsContext, open: Double) {
        let bleed = Metrics.bleed
        let track = CGRect(x: bleed, y: bleed, width: Metrics.width, height: Metrics.height)

        var shadow = context
        shadow.addFilter(.blur(radius: 13))
        shadow.fill(
            capsule(CGRect(x: bleed + 16, y: bleed + 18, width: Metrics.width - 32, height: Metrics.height - 8)),
            with: .color(.black.opacity(0.17))
        )
        context.fill(capsule(track.insetBy(dx: -0.5, dy: -0.5)), with: .color(Color(hex: 0xe6e6e6)))
        context.fill(capsule(track), with: .color(Color(hex: 0xfafafa)))

        context.fill(
            pill(
                left: Metrics.inset + Metrics.shift * lag(plan),
                right: Metrics.inset + Metrics.pillWidth + Metrics.shift * lead(plan),
                height: Metrics.pillHeight, scale: 1
            ),
            with: .color(Color(hex: 0x050505))
        )
        context.fill(
            pill(
                left: Metrics.knobX + Metrics.knobWidth * lag(billing),
                right: Metrics.knobX + Metrics.knobWidth + Metrics.knobWidth * lead(billing),
                height: Metrics.knobHeight, scale: 0.6 + 0.4 * open
            ),
            with: .color(.white.opacity(open))
        )
    }
}

#Preview {
    ZStack {
        Color(hex: 0xfdfdfd).ignoresSafeArea()
        PricingSegmentedButton(autoPlay: true)
    }
}
