import SwiftUI

struct DetentMethodSheetMethod: Equatable {
    /// An SF Symbol name.
    var icon: String
    var title: String
    var subtitle: String
    /// Shown in the trailing pill.
    var badge: String
    var instant = false
    /// The pill carries a green tick.
    var enabled = false

    static let samples = [
        DetentMethodSheetMethod(icon: "building.columns", title: "Connect bank account", subtitle: "4-5 business days", badge: "Free"),
        DetentMethodSheetMethod(icon: "creditcard", title: "Debit card", subtitle: "Instant transfer", badge: "2% fee", instant: true),
        DetentMethodSheetMethod(icon: "arrow.left.arrow.right", title: "Regular bank transfer", subtitle: "0-1 business days", badge: "Free"),
        DetentMethodSheetMethod(icon: "diamond.fill", title: "Get paid from partner", subtitle: "Instant transfer", badge: "Enabled", instant: true, enabled: true),
        DetentMethodSheetMethod(icon: "wallet.bifold", title: "Crypto wallet", subtitle: "Instant transfer", badge: "1% fee", instant: true),
    ]
}

/// A page with a button that raises a sheet of transfer methods. The sheet rests at a middle
/// detent and a large one and follows a finger, letting go on a fling or on the nearest detent;
/// the page behind scales and darkens with the sheet's position, and at the large detent the rows
/// grow and a fifth one appears. One value, the sheet's top edge, drives all of it.
struct DetentMethodSheet: View {
    /// The methods of the sheet; the last one only shows at the large detent.
    var methods = DetentMethodSheetMethod.samples
    var pageTitle = "Select your source of fund"
    var pageCaption = "Pick a method to add money to your account"
    var buttonLabel = "Add money"
    var sheetTitle = "Select transfer method"
    var sheetCaption = "Select your preferred transfer method to add your money into your account"
    /// Opens and closes the sheet, pulls it to the large detent and picks a method by itself.
    var autoPlay = false
    /// Called with the index of the method that was tapped.
    var onSelect: ((Int) -> Void)? = nil
    /// Called when the sheet is sent to a detent: 0 closed, 1 medium, 2 large.
    var onDetentChange: ((Int) -> Void)? = nil

    /// Seconds for one pass of the autoPlay loop.
    static let loop = 10.0
    // The timeline, in seconds from the start of a loop.
    private static let touchButtonAt = 0.35
    private static let pressButtonAt = 0.5
    private static let openAt = 0.65
    private static let touchGrabberAt = 2.2
    private static let dragUpAt = 2.4
    private static let releaseUpAt = 3.15
    private static let touchRowAt = 5.0
    private static let pressRowAt = 5.4
    private static let selectAt = 5.7
    private static let touchGrabberAgainAt = 6.5
    private static let dragDownAt = 6.7
    private static let releaseDownAt = 7.6
    private static let clearAt = 8.3
    /// Seconds after the last touch before the loop starts again from rest.
    private static let userHold = 3.0

    /// What the animation and the handlers need to know about the screen and the sheet. Written
    /// while drawing and read by handlers, so it is a reference and not state.
    private final class Place {
        var width = 0.0
        var height = 0.0
        var medium = 0.0
        var large = 0.0
        var insets = EdgeInsets()
        /// The sheet's top edge as it is on screen now, which `top` is not while it animates.
        var current = 0.0
    }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var place = Place()
    @State private var detent = 0
    @State private var selected: Int? = nil
    @State private var pressedRow: Int? = nil
    @State private var buttonPressed = false
    @State private var dragging = false
    @State private var touches = 0
    @State private var touching = false
    // The sheet's top edge, in points from the top of the screen; the screen's height is closed.
    @State private var top = 100000.0
    @State private var enter = 0.0
    // The simulated finger of the autoPlay loop.
    @State private var fingerX = 0.0
    @State private var fingerY = 0.0
    @State private var fingerOn = 0.0
    @State private var fingerDown = 0.0
    @State private var fingerFollows = false

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width + insets.leading + insets.trailing
            let height = proxy.size.height + insets.top + insets.bottom
            let _ = layout(width: width, height: height, insets: insets)
            Color.clear
                .modifier(Driven(top) { _, y in
                    screen(y: y)
                })
                .frame(width: width, height: height, alignment: .topLeading)
                .clipped()
                .ignoresSafeArea()
        }
        .environment(\.colorScheme, .light)
        .preferredColorScheme(detent == 0 ? .light : .dark)
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    guard !touching else { return }
                    touching = true
                    touched()
                }
                .onEnded { _ in touching = false }
        )
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await playLoop() }
    }

    /// Records the screen's measures, and puts the sheet away on the first pass.
    private func layout(width: Double, height: Double, insets: EdgeInsets) {
        place.width = width
        place.height = height
        place.insets = insets
        place.medium = (height * M.mediumAt).rounded()
        place.large = max((height * M.largeAt).rounded(), insets.top + 8)
        if top > height {
            DispatchQueue.main.async {
                place.current = height
                top = height
            }
        }
    }

    // MARK: Layout

    private var enterMs: Double { M.enterIn + M.rowStagger * Double(methods.count) }

    private func screen(y: Double) -> some View {
        place.current = y
        let h = place.height
        let w = place.width
        let medium = place.medium
        let large = place.large
        let scale = Self.interpolate(y, [large, medium, h], [M.scaleLarge, M.scaleMedium, 1])
        let radius = Self.interpolate(y, [medium, h], [M.pageRadius, 0])
        let scrim = Self.interpolate(y, [medium, h], [M.scrim, 0])
        let g = Self.interpolate(y, [large, medium], [1, 0])

        return ZStack(alignment: .topLeading) {
            Color.black
            pageView(width: w, height: h)
                .clipShape(UnevenRoundedRectangle(topLeadingRadius: radius, topTrailingRadius: radius, style: .continuous))
                .scaleEffect(scale)

            Color.black.opacity(scrim)
                .onTapGesture { close() }
                .accessibilityLabel("Close")
                .allowsHitTesting(detent != 0)

            sheetView(width: w, height: h - large + 200, g: g)
                .offset(y: y)
                .allowsHitTesting(detent != 0)
                .gesture(pan)

            finger(y: y)
        }
    }

    private func pageView(width: Double, height: Double) -> some View {
        let insets = place.insets
        let bottom = max(insets.bottom, 16)
        return VStack(alignment: .leading, spacing: 0) {
            HStack {
                Image(systemName: "chevron.left")
                    .font(.system(size: 21, weight: .medium))
                    .foregroundStyle(M.ink)
                    .frame(width: 26, height: 26)
                Spacer(minLength: 0)
                VStack(alignment: .trailing, spacing: 0) {
                    Text("Balance").font(.system(size: 12, weight: .medium)).foregroundStyle(M.faint).frame(height: 16)
                    Text("$1,284.50").font(.system(size: 16, weight: .bold).monospacedDigit()).foregroundStyle(M.ink).frame(height: 20)
                }
            }
            .frame(height: 44)
            .padding(.top, 4)
            .padding(.leading, -6)
            Text(pageTitle)
                .font(.system(size: 28, weight: .bold))
                .tracking(-0.7)
                .foregroundStyle(M.ink)
                .frame(height: 34)
                .padding(.top, 16)
            Text(pageCaption)
                .font(.system(size: 16))
                .foregroundStyle(M.mute)
                .frame(height: 22)
                .padding(.top, 6)
            HStack {
                section("Bank accounts")
                Spacer(minLength: 0)
                Button(action: open) {
                    HStack(spacing: 2) {
                        Text("Add").font(.system(size: 14, weight: .semibold))
                        Image(systemName: "plus").font(.system(size: 13, weight: .medium))
                    }
                    .foregroundStyle(M.ink)
                    .padding(.leading, 14)
                    .padding(.trailing, 10)
                    .frame(height: 32)
                    .overlay(Capsule().strokeBorder(M.pillLine, lineWidth: 1))
                    .contentShape(Capsule())
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Add a bank account")
            }
            .padding(.top, 28)
            .padding(.bottom, 4)
            ForEach(M.accounts, id: \.name) { account in
                HStack(spacing: 14) {
                    ZStack {
                        Circle().fill(account.colors.1)
                        Circle().fill(account.colors.0).frame(width: 44, height: 44).offset(x: -6, y: -10)
                        Text(account.letter).font(.system(size: 18, weight: .heavy)).foregroundStyle(.white)
                    }
                    .frame(width: 44, height: 44)
                    .clipShape(Circle())
                    VStack(alignment: .leading, spacing: 2) {
                        HStack(spacing: 8) {
                            rowTitle(account.name)
                            if account.primary {
                                Text("Primary")
                                    .font(.system(size: 11, weight: .bold))
                                    .foregroundStyle(M.green)
                                    .padding(.horizontal, 7)
                                    .frame(height: 20)
                                    .background(Capsule().fill(M.greenWell))
                            }
                        }
                        rowDetail("Checking  ••••  \(account.digits)")
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    Image(systemName: "chevron.right").font(.system(size: 15, weight: .medium)).foregroundStyle(M.faint)
                }
                .frame(height: 68)
            }
            HStack {
                section("Recent top-ups")
                Spacer(minLength: 0)
                Text("See all").font(.system(size: 15, weight: .medium)).foregroundStyle(M.mute)
            }
            .padding(.top, 24)
            .padding(.bottom, 4)
            ForEach(M.recent, id: \.title) { item in
                HStack(spacing: 14) {
                    Image(systemName: "arrow.down")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(M.green)
                        .frame(width: 44, height: 44)
                        .background(Circle().fill(M.greenWell))
                    VStack(alignment: .leading, spacing: 2) {
                        rowTitle(item.title)
                        rowDetail(item.when)
                    }
                    .frame(maxWidth: .infinity, alignment: .leading)
                    Text(item.amount).font(.system(size: 16, weight: .bold).monospacedDigit()).foregroundStyle(M.green)
                }
                .frame(height: 68)
            }
            HStack(spacing: 14) {
                Image(systemName: "bolt.fill")
                    .font(.system(size: 15))
                    .foregroundStyle(M.bolt)
                    .frame(width: 40, height: 40)
                    .background(Circle().fill(M.tipWell))
                VStack(alignment: .leading, spacing: 2) {
                    Text("Instant top-ups are on").font(.system(size: 15, weight: .semibold)).foregroundStyle(M.ink).frame(height: 20)
                    Text("Free up to $500 a month with a debit card.").font(.system(size: 14)).foregroundStyle(M.mute).frame(height: 19)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
            }
            .padding(16)
            .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(M.tip))
            .padding(.top, 20)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, M.margin)
        .padding(.top, insets.top + 8)
        .frame(width: width, height: height, alignment: .topLeading)
        .background(Color.white)
        .overlay(alignment: .bottom) {
            Button(action: open) {
                Text(buttonLabel)
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(maxWidth: .infinity)
                    .frame(height: M.buttonHeight)
            }
            .buttonStyle(DetentMethodSheetButtonStyle(held: buttonPressed))
            .accessibilityLabel(buttonLabel)
            .padding(.horizontal, M.margin)
            .padding(.bottom, bottom)
        }
    }

    private func section(_ text: String) -> some View {
        Text(text).font(.system(size: 17, weight: .semibold)).tracking(-0.2).foregroundStyle(M.ink).frame(height: 22)
    }

    private func rowTitle(_ text: String) -> some View {
        Text(text).font(.system(size: 16, weight: .semibold)).tracking(-0.2).foregroundStyle(M.ink).lineLimit(1).frame(height: 21)
    }

    private func rowDetail(_ text: String) -> some View {
        Text(text).font(.system(size: 14).monospacedDigit()).foregroundStyle(M.faint).frame(height: 19)
    }

    private func sheetView(width: Double, height: Double, g: Double) -> some View {
        VStack(alignment: .leading, spacing: 0) {
            Capsule()
                .fill(M.grabber)
                .frame(width: M.grabberWidth, height: M.grabberHeight)
                .frame(maxWidth: .infinity)
                .padding(.top, 8)
            VStack(alignment: .leading, spacing: 0) {
                Text(sheetTitle)
                    .font(.system(size: 21, weight: .bold))
                    .tracking(-0.4)
                    .foregroundStyle(M.ink)
                    .frame(height: 26)
                    .padding(.top, 22)
                Text(sheetCaption)
                    .font(.system(size: 15))
                    .lineSpacing(2)
                    .foregroundStyle(M.mute)
                    .lineLimit(2)
                    .frame(height: 40, alignment: .top)
                    .padding(.top, 4)
            }
            .modifier(Driven(enter) { content, e in
                let t = min(1, e * enterMs / M.enterIn)
                content.opacity(t).offset(y: 8 * (1 - t))
            })
            VStack(spacing: 0) {
                ForEach(Array(methods.enumerated()), id: \.offset) { index, method in
                    row(index, method, g: g)
                }
            }
            .padding(.top, M.rowsTop)
            Spacer(minLength: 0)
        }
        .padding(.horizontal, M.margin)
        .frame(width: width, height: height, alignment: .top)
        .background {
            UnevenRoundedRectangle(topLeadingRadius: M.sheetRadius, topTrailingRadius: M.sheetRadius, style: .continuous)
                .fill(Color.white)
                .shadow(color: .black.opacity(0.14), radius: 16, y: -8)
        }
    }

    private func row(_ index: Int, _ method: DetentMethodSheetMethod, g: Double) -> some View {
        let last = index == methods.count - 1
        let isSelected = selected == index
        let partner = method.icon == "diamond.fill"
        // The last row exists only at the large detent.
        let extra = last ? min(1, max(0, (g - 0.5) / 0.5)) : 1
        let shape = RoundedRectangle(cornerRadius: M.rowRadius, style: .continuous)
        return Button { choose(index) } label: {
            HStack(spacing: 14) {
                Image(systemName: isSelected ? "checkmark" : method.icon)
                    .font(.system(size: 16, weight: isSelected ? .bold : .regular))
                    .foregroundStyle(isSelected ? Color.white : partner ? M.teal : M.ink)
                    .frame(width: M.icon, height: M.icon)
                    .background(Circle().fill(isSelected ? M.ink : partner ? M.tealWell : M.iconWell))
                VStack(alignment: .leading, spacing: 2) {
                    Text(method.title).font(.system(size: 16, weight: .semibold)).tracking(-0.2).foregroundStyle(M.ink).lineLimit(1).frame(height: 21)
                    HStack(spacing: 3) {
                        if method.instant { Image(systemName: "bolt.fill").font(.system(size: 11)).foregroundStyle(M.bolt) }
                        Text(method.subtitle).font(.system(size: 14)).foregroundStyle(M.mute).lineLimit(1)
                    }
                    .frame(height: 19)
                    .opacity(0.72 + 0.28 * g)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                HStack(spacing: 5) {
                    Text(method.badge).font(.system(size: 13, weight: .semibold)).foregroundStyle(M.ink)
                    if method.enabled { Image(systemName: "checkmark.circle.fill").font(.system(size: 14)).foregroundStyle(M.green) }
                }
                .padding(.horizontal, 12)
                .frame(height: 30)
                .background(Capsule().fill(Color.white))
                .overlay(Capsule().strokeBorder(M.pillLine, lineWidth: 1))
            }
            .padding(.horizontal, 14)
            .frame(maxWidth: .infinity, maxHeight: .infinity)
            .background {
                shape.fill(Color.white)
                    .shadow(color: M.ink.opacity(0.05), radius: 8, y: 6)
                    .shadow(color: M.ink.opacity(0.04), radius: 1, y: 1)
            }
            .overlay(shape.strokeBorder(isSelected ? M.ink : M.line, lineWidth: isSelected ? 1.5 : 1))
            .animation(.easeOut(duration: 0.15), value: isSelected)
        }
        .buttonStyle(DetentMethodSheetRowStyle(held: pressedRow == index))
        .accessibilityAddTraits(isSelected ? .isSelected : [])
        .frame(height: (M.rowMedium + (M.rowLarge - M.rowMedium) * g) * extra)
        .padding(.top, M.rowGap * extra)
        .modifier(Driven(enter) { content, e in
            let t = min(1, max(0, (e * enterMs - Double(index + 1) * M.rowStagger) / M.enterIn))
            content.opacity(t * extra).offset(y: (1 - t) * M.rowRoom)
        })
        .allowsHitTesting(extra > 0)
    }

    private func finger(y: Double) -> some View {
        Color.clear.modifier(Driven(fingerOn) { _, on in
            Color.clear.modifier(Driven(fingerDown) { _, down in
                Circle()
                    .fill(.white)
                    .shadow(color: .black.opacity(0.28), radius: 10, x: 0, y: 2)
                    .frame(width: M.finger, height: M.finger)
                    .scaleEffect(1 - 0.14 * down)
                    .opacity(on)
                    .offset(x: fingerX - M.finger / 2, y: (fingerFollows ? y + 14 : fingerY) - M.finger / 2)
            })
        })
        .allowsHitTesting(false)
    }

    // MARK: The sheet's movement

    private var pan: some Gesture {
        DragGesture(minimumDistance: 10)
            .onChanged { value in
                if !dragging {
                    dragging = true
                    // Takes hold of the sheet where it is.
                    set(place.current)
                }
                let start = detent == 2 ? place.large : detent == 1 ? place.medium : place.height
                let next = start + value.translation.height
                set(next < place.large ? place.large - (place.large - next) * M.rubber : next)
            }
            .onEnded { value in
                dragging = false
                settle(velocity: value.velocity.height)
            }
    }

    /// Moves the sheet's edge without an animation.
    private func set(_ y: Double) {
        withAnimation(nil) { top = y }
        place.current = y
    }

    /// Springs to a detent (0 closed, 1 medium, 2 large), carrying the finger's speed in.
    private func go(_ target: Int, velocity: Double) {
        if target != detent { onDetentChange?(target) }
        detent = target
        let to = target == 2 ? place.large : target == 1 ? place.medium : place.height
        if reduceMotion {
            withAnimation(M.timing(M.fade)) { top = to }
        } else {
            let distance = to - place.current
            let spring = target == 2 ? M.largeSpring : target == 1 ? M.mediumSpring : M.closeSpring
            // SwiftUI takes the speed as a share of the distance to cover each second.
            let speed = abs(distance) > 1 ? velocity / distance : 0
            withAnimation(.interpolatingSpring(mass: 1, stiffness: spring.stiffness, damping: spring.damping, initialVelocity: speed)) { top = to }
        }
        if target == 0 {
            // The rows rise again the next time the sheet opens.
            withAnimation(M.timing(M.fade).delay(0.25)) { enter = 0 }
        }
    }

    /// Lets go of the sheet: a fling picks the next detent that way, a slow release the nearest.
    private func settle(velocity: Double) {
        let y = place.current
        let target: Int
        if abs(velocity) > M.fling {
            if velocity < 0 { target = y > place.medium + M.near ? 1 : 2 } else { target = y < place.medium - M.near ? 1 : 0 }
        } else {
            let ahead = y + velocity * M.project
            let toLarge = abs(ahead - place.large)
            let toMedium = abs(ahead - place.medium)
            let toClosed = abs(ahead - place.height)
            target = toLarge < toMedium && toLarge < toClosed ? 2 : toMedium < toClosed ? 1 : 0
        }
        go(target, velocity: velocity)
    }

    private func open() {
        guard detent == 0 else { return }
        withAnimation(nil) { enter = 0 }
        set(place.height)
        go(1, velocity: 0)
        withAnimation(M.timing(reduceMotion ? M.fade : enterMs, curve: .linear).delay(reduceMotion ? 0 : M.enterAfter / 1000)) { enter = 1 }
    }

    private func close() { go(0, velocity: 0) }

    private func choose(_ index: Int) {
        selected = index
        onSelect?(index)
    }

    // MARK: The autoPlay finger

    private func touchAt(_ x: Double, _ y: Double, follows: Bool = false) {
        fingerX = x
        fingerY = y
        fingerFollows = follows
        withAnimation(M.timing(M.fingerIn)) { fingerOn = 1 }
    }

    private func press() {
        withAnimation(M.timing(100)) { fingerDown = 1 }
    }

    private func lift() {
        withAnimation(M.timing(120)) { fingerDown = 0 }
        withAnimation(M.timing(M.fingerOut)) { fingerOn = 0 }
    }

    private func rowCenter(_ index: Int) -> Double {
        place.large + M.header + Double(index) * (M.rowLarge + M.rowGap) + M.rowLarge / 2
    }

    private func rest() {
        detent = 0
        set(place.current)
        withAnimation(M.timing(M.restFade)) { top = place.height }
        withAnimation(M.timing(M.fade)) { enter = 0 }
        withAnimation(M.timing(M.fade)) { fingerOn = 0 }
        withAnimation(nil) { fingerDown = 0 }
        selected = nil
        pressedRow = nil
        buttonPressed = false
    }

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func touched() {
        touches += 1
    }

    private func playLoop() async {
        guard autoPlay else { return }
        let clock = ContinuousClock()
        if touches > 0 {
            try? await clock.sleep(for: .seconds(Self.userHold), tolerance: .zero)
            guard !Task.isCancelled else { return }
            rest()
        }
        let steps: [(Double, () -> Void)] = [
            (Self.touchButtonAt, { touchAt(place.width / 2, place.height - place.insets.bottom - 16 - M.buttonHeight / 2) }),
            (Self.pressButtonAt, { buttonPressed = true; press() }),
            (Self.openAt, { buttonPressed = false; lift(); open() }),
            (Self.touchGrabberAt, { touchAt(place.width / 2, place.medium + 14, follows: true) }),
            (Self.dragUpAt, {
                press()
                set(place.current)
                withAnimation(M.timing(M.dragUpMs, curve: M.quadInOut)) { top = place.medium - M.dragUp }
            }),
            (Self.releaseUpAt, { settle(velocity: -700); lift() }),
            (Self.touchRowAt, { touchAt(place.width * 0.72, rowCenter(1)) }),
            (Self.pressRowAt, { pressedRow = 1; press() }),
            (Self.selectAt, { pressedRow = nil; choose(1); lift(); go(1, velocity: 0) }),
            (Self.touchGrabberAgainAt, { touchAt(place.width / 2, place.medium + 14, follows: true) }),
            (Self.dragDownAt, {
                press()
                set(place.current)
                withAnimation(M.timing(M.dragDownMs, curve: M.quadIn)) { top = place.height }
            }),
            (Self.releaseDownAt, { settle(velocity: 900); lift() }),
            (Self.clearAt, { selected = nil }),
        ]
        var origin = clock.now
        while !Task.isCancelled {
            for (at, run) in steps {
                try? await clock.sleep(until: origin + .seconds(at), tolerance: .zero)
                guard !Task.isCancelled else { return }
                run()
            }
            origin += .seconds(Self.loop)
            try? await clock.sleep(until: origin, tolerance: .zero)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
    }

    /// Piecewise-linear map of `x` through `xs` (ascending or descending) to `ys`, clamped at the ends.
    private static func interpolate(_ x: Double, _ xs: [Double], _ ys: [Double]) -> Double {
        let ascending = xs[0] < xs[xs.count - 1]
        let ordered = ascending ? Array(zip(xs, ys)) : Array(zip(xs, ys).reversed())
        if x <= ordered[0].0 { return ordered[0].1 }
        for i in 1..<ordered.count where x <= ordered[i].0 {
            let (x0, y0) = ordered[i - 1]
            let (x1, y1) = ordered[i]
            return y0 + (y1 - y0) * (x - x0) / (x1 - x0)
        }
        return ordered[ordered.count - 1].1
    }
}

// MARK: Support

/// Re-evaluates `transform` on every frame of an animation of `value`, so a number that moves
/// under a spring can drive several properties through non-linear maps.
private struct Driven: ViewModifier, Animatable {
    var value: Double
    private let transform: (Content, Double) -> AnyView

    init<Result: View>(_ value: Double, @ViewBuilder _ transform: @escaping (Content, Double) -> Result) {
        self.value = value
        self.transform = { AnyView(transform($0, $1)) }
    }

    var animatableData: Double {
        get { value }
        set { value = newValue }
    }

    func body(content: Content) -> some View { transform(content, value) }
}

private struct DetentMethodSheetButtonStyle: ButtonStyle {
    let held: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .background {
                Capsule()
                    .fill(held || configuration.isPressed ? Color(hex: 0x333333) : M.ink)
                    .shadow(color: M.ink.opacity(0.22), radius: 10, y: 8)
            }
            .contentShape(Capsule())
    }
}

private struct DetentMethodSheetRowStyle: ButtonStyle {
    let held: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .scaleEffect(held || configuration.isPressed ? 0.97 : 1)
            .animation(.easeOut(duration: 0.12), value: held || configuration.isPressed)
    }
}

private typealias M = DetentMethodSheetMetrics

private enum DetentMethodSheetMetrics {
    static let ink = Color(hex: 0x111113)
    static let mute = Color(hex: 0x6b6b70)
    static let faint = Color(hex: 0x9a9aa0)
    static let line = Color(hex: 0xebebee)
    static let pillLine = Color(hex: 0xe2e2e6)
    static let iconWell = Color(hex: 0xf3f3f5)
    static let bolt = Color(hex: 0xf5a623)
    static let green = Color(hex: 0x1fa855)
    static let greenWell = Color(hex: 0xe8f7ee)
    static let teal = Color(hex: 0x0fb5a4)
    static let tealWell = Color(hex: 0xe3f7f4)
    static let grabber = Color(hex: 0xd6d6da)
    static let tip = Color(hex: 0xfff8e8)
    static let tipWell = Color(hex: 0xffeec2)

    static let accounts = [
        (name: "Northgate Bank", digits: "4793", letter: "N", colors: (Color(hex: 0x2c5cc5), Color(hex: 0x1b3a8a)), primary: true),
        (name: "Harbor Credit Union", digits: "2208", letter: "H", colors: (Color(hex: 0xc2410c), Color(hex: 0x8a2c08)), primary: false),
    ]
    static let recent = [
        (title: "From Northgate Bank", when: "Today, 9:41 AM", amount: "+$250.00"),
        (title: "From debit card", when: "Mon, 6:12 PM", amount: "+$80.00"),
    ]

    static let sheetRadius = 28.0
    static let pageRadius = 24.0
    static let grabberWidth = 36.0
    static let grabberHeight = 4.0
    static let margin = 20.0
    // From the sheet's top edge to the first row.
    static let header = 128.0
    static let rowMedium = 68.0
    static let rowLarge = 76.0
    static let rowGap = 10.0
    static let rowRadius = 18.0
    // Grabber, title and caption take 108 points; the first row's own gap and this one reach `header`.
    static let rowsTop = header - 108 - rowGap
    static let icon = 40.0
    static let buttonHeight = 52.0
    static let finger = 40.0

    // Where the sheet's top edge rests, as a share of the screen's height; the page's scale there.
    static let mediumAt = 0.32
    static let largeAt = 0.14
    static let scaleMedium = 0.94
    static let scaleLarge = 0.92
    static let scrim = 0.36
    // The sheet follows a finger 1:1 until the large detent, and a quarter of the way beyond it.
    static let rubber = 0.25
    // Points a second above which a release picks the next detent in its direction; how far ahead
    // the release looks otherwise.
    static let fling = 600.0
    static let project = 0.12
    static let near = 24.0
    // Response 0.5 s with damping ratios 0.8 and 0.7: the large detent overshoots a little more.
    static let mediumSpring = (stiffness: 158.0, damping: 20.0)
    static let largeSpring = (stiffness: 158.0, damping: 17.6)
    static let closeSpring = (stiffness: 190.0, damping: 28.0)
    static let fade = 180.0
    static let enterAfter = 650.0
    static let enterIn = 450.0
    static let rowStagger = 70.0
    static let rowRoom = 24.0
    static let dragUp = 150.0
    static let dragUpMs = 700.0
    static let dragDownMs = 900.0
    static let fingerIn = 150.0
    static let fingerOut = 220.0
    static let restFade = 350.0

    static let cubicOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.33, y: 1), endControlPoint: UnitPoint(x: 0.68, y: 1))
    static let quadIn = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.11, y: 0), endControlPoint: UnitPoint(x: 0.5, y: 0))
    static let quadInOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.455, y: 0.03), endControlPoint: UnitPoint(x: 0.515, y: 0.955))

    /// A timed animation of `ms` milliseconds, cubic ease-out unless told otherwise.
    static func timing(_ ms: Double, curve: UnitCurve = cubicOut) -> Animation {
        .timingCurve(curve, duration: ms / 1000)
    }
}
