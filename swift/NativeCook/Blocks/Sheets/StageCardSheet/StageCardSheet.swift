import SwiftUI

struct StageCardSheetPermission: Equatable {
    /// An SF Symbol name.
    var icon: String
    var title: String
    var caption: String

    static let samples = [
        StageCardSheetPermission(icon: "person.2", title: "Allow collaboration", caption: "Anyone with the link can edit and add tracks"),
        StageCardSheetPermission(icon: "square.and.arrow.down", title: "Allow downloads", caption: "Anyone with the link can download audio"),
        StageCardSheetPermission(icon: "person.crop.circle", title: "Require account", caption: "Only people with an account will be able to listen"),
    ]
}

/// A project page whose artwork is a hero until "Enable link sharing" is pressed: then it shrinks
/// into a cover with a streak of light across it while the sharing panel rises below. "Make project
/// private" asks first in an alert, and Continue folds everything back. The autoPlay loop presses
/// the same buttons; nothing is shared or deleted here.
struct StageCardSheet: View {
    var title = "First"
    var caption = "60fps · 1 track"
    var enableLabel = "Enable link sharing"
    var footnote = "This will create a shareable link. Anyone with the link will be able to listen and share this project."
    /// Written under "Reset link".
    var generatedOn = "September 24, 2024"
    var permissions = StageCardSheetPermission.samples
    /// Opens the sheet and closes it again by itself every `StageCardSheet.loop` seconds.
    var autoPlay = false
    /// Called when the sheet has opened, i.e. the link exists.
    var onEnable: (() -> Void)? = nil
    /// Called when "Continue" in the dialog makes the project private.
    var onMakePrivate: (() -> Void)? = nil
    var onPermissionChange: ((Int, Bool) -> Void)? = nil

    /// Seconds for one pass of the autoPlay loop.
    static let loop = 9.0
    // The timeline, in seconds from the start of a loop.
    private static let tapAt = 0.9
    private static let dialogAt = 4.2
    private static let continuePressAt = 5.5
    private static let confirmAt = 5.75
    /// Seconds after the last touch before the loop starts again from rest.
    private static let userHold = 3.0

    private enum Stage { case closed, loading, open }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var stage = Stage.closed
    @State private var dialog = false
    @State private var autoPressed = false
    @State private var on: [Bool] = []
    @State private var pending: Task<Void, Never>? = nil
    @State private var touches = 0
    @State private var touching = false
    @State private var loadingSince = Date.now
    // 0 closed, 1 open: the artwork's size and the panel come from it.
    @State private var sheet = 0.0
    @State private var panel = 0.0
    @State private var streak = 0.0
    @State private var busy = 0.0
    @State private var alert = 0.0

    private var states: [Bool] { on.count == permissions.count ? on : permissions.map { _ in false } }

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width + insets.leading + insets.trailing
            let height = proxy.size.height + insets.top + insets.bottom
            screen(width: width, height: height, insets: insets)
                .frame(width: width, height: height, alignment: .topLeading)
                .clipped()
                .ignoresSafeArea()
        }
        .environment(\.colorScheme, .light)
        .preferredColorScheme(.dark)
        .simultaneousGesture(
            DragGesture(minimumDistance: 0)
                .onChanged { _ in
                    guard !touching else { return }
                    touching = true
                    touched()
                }
                .onEnded { _ in touching = false }
        )
        .onDisappear { pending?.cancel() }
        .task(id: LoopKey(on: autoPlay, touches: touches)) { await playLoop() }
    }

    // MARK: Layout

    private func screen(width: Double, height: Double, insets: EdgeInsets) -> some View {
        let sheetTop = insets.top + M.sheetGap
        let sheetHeight = height - sheetTop
        let artClosed = min(M.artClosedMax, width - 2 * (M.margin + 16))
        // Centre the hero in the room between the close button and the button at the bottom.
        let artTopClosed = max(64, min(M.artTopClosed, (sheetHeight - artClosed - 260) / 2))
        let titleTopClosed = artTopClosed + artClosed + M.titleGap
        let titleTopOpen = M.artTopOpen + M.artOpen + M.titleGapOpen
        let panelTop = titleTopOpen + (M.titleHeight + 2 + M.captionHeight) * M.titleScaleOpen + M.panelGap
        let bottom = max(insets.bottom, 16)

        return ZStack(alignment: .topLeading) {
            Color.black

            // The page the sheet came from, peeking out above it.
            RoundedRectangle(cornerRadius: M.sheetRadius, style: .continuous)
                .fill(M.behind)
                .frame(width: width - 36, height: 40)
                .offset(x: 18, y: insets.top)

            ZStack(alignment: .topLeading) {
                M.page
                LinearGradient(colors: [M.glowPage, M.glowPage.opacity(0)], startPoint: .top, endPoint: .bottom)
                    .frame(width: width, height: sheetHeight * 0.6)
                    .allowsHitTesting(false)

                Image(systemName: "xmark")
                    .font(.system(size: 16, weight: .medium))
                    .foregroundStyle(M.ink)
                    .frame(width: M.close, height: M.close)
                    .background(RoundedRectangle(cornerRadius: 12, style: .continuous).fill(M.well))
                    .offset(x: width - 16 - M.close, y: 16)

                // Under the artwork, so the rows rise from behind the shrinking cover.
                panelView
                    .frame(width: width - 2 * M.margin, alignment: .top)
                    .offset(x: M.margin, y: panelTop)
                    .allowsHitTesting(stage == .open)

                Color.clear
                    .modifier(Driven(sheet) { content, p in
                        let size = artClosed + (M.artOpen - artClosed) * p
                        let radius = M.artRadiusClosed + (M.artRadiusOpen - M.artRadiusClosed) * p
                        content
                            .frame(width: size, height: size)
                            .background { art(size: size, radius: radius) }
                            .offset(x: (width - size) / 2, y: artTopClosed + (M.artTopOpen - artTopClosed) * p)
                    })
                    .allowsHitTesting(false)

                titleBlock
                    .frame(width: width)
                    .modifier(Driven(sheet) { content, p in
                        content
                            .scaleEffect(1 + (M.titleScaleOpen - 1) * p, anchor: .top)
                            .offset(y: titleTopClosed + (titleTopOpen - titleTopClosed) * p)
                    })
                    .allowsHitTesting(false)

                dock
                    .padding(.horizontal, M.margin)
                    .frame(width: width, alignment: .top)
                    .modifier(Driven(sheet) { content, sheet in
                        let p = min(1, sheet * 2)
                        content.opacity(1 - p).offset(y: p * M.rowRise)
                    })
                    .offset(y: sheetHeight - bottom - M.dockHeight)
                    .allowsHitTesting(stage == .closed)
            }
            .frame(width: width, height: sheetHeight, alignment: .topLeading)
            .clipShape(UnevenRoundedRectangle(topLeadingRadius: M.sheetRadius, topTrailingRadius: M.sheetRadius, style: .continuous))
            .offset(y: sheetTop)

            dialogLayer
                .frame(width: width, height: height)
                .allowsHitTesting(dialog)
        }
    }

    /// The artwork: a pink-to-violet square lit from the upper left, rays of light wiped in from
    /// the left, a flash as they land, a fine light edge and a coloured glow below.
    private func art(size: Double, radius: Double) -> some View {
        let shape = RoundedRectangle(cornerRadius: radius, style: .continuous)
        return ZStack {
            LinearGradient(stops: M.artStops, startPoint: .topLeading, endPoint: .bottomTrailing)
            LinearGradient(colors: [.white.opacity(0.42), .white.opacity(0)], startPoint: .topLeading, endPoint: UnitPoint(x: 0.55, y: 0.6))
            LinearGradient(colors: [M.shade.opacity(0), M.shade.opacity(0.22)], startPoint: UnitPoint(x: 0.45, y: 0.45), endPoint: .bottomTrailing)
            Color.clear.modifier(Driven(streak) { _, p in
                StageCardSheetRays(wipe: p * 116).frame(width: size, height: size)
            })
            Color.white.modifier(Driven(streak) { content, p in content.opacity(0.32 * sin(.pi * p)) })
        }
        .frame(width: size, height: size)
        .clipShape(shape)
        .overlay { shape.strokeBorder(.white.opacity(0.28), lineWidth: M.hairline * 2) }
        .background {
            ZStack {
                // Box shadows: a blur of B is a Gaussian of B / 2, and the spread grows the shape.
                RoundedRectangle(cornerRadius: radius + 6, style: .continuous)
                    .fill(M.glow)
                    .frame(width: size + 12, height: size + 12)
                    .offset(y: 24)
                    .blur(radius: 36)
                shape.fill(M.contact).offset(y: 4).blur(radius: 5)
            }
        }
    }

    private var titleBlock: some View {
        VStack(spacing: 2) {
            Text(title)
                .font(.system(size: 28, weight: .bold))
                .tracking(-0.6)
                .foregroundStyle(M.ink)
                .frame(height: M.titleHeight)
            HStack(spacing: 6) {
                Image(systemName: "lock.fill")
                    .font(.system(size: 8, weight: .semibold))
                    .foregroundStyle(.white)
                    .frame(width: 16, height: 16)
                    .background(Circle().fill(M.avatar))
                Text(caption)
                    .font(.system(size: 15, weight: .medium))
                    .foregroundStyle(M.mute)
            }
            .frame(height: M.captionHeight)
        }
    }

    // MARK: The panel

    private var panelView: some View {
        VStack(spacing: 0) {
            reveal(0) {
                HStack(spacing: 10) {
                    action("doc.on.doc", "Copy link")
                    action("square.and.arrow.up", "Share")
                }
            }
            reveal(1) {
                HStack(spacing: 6) {
                    Image(systemName: "arrow.clockwise").font(.system(size: 13, weight: .medium))
                    Text("Reset link").font(.custom("Menlo", size: 14))
                }
                .foregroundStyle(M.body)
                .frame(maxWidth: .infinity, minHeight: 20)
                .padding(.top, 18)
            }
            reveal(2) {
                Text("Last generated on \(generatedOn)")
                    .font(.system(size: 12))
                    .foregroundStyle(M.faint)
                    .frame(maxWidth: .infinity, minHeight: 16)
                    .padding(.top, 4)
            }
            reveal(3) {
                Button(action: askPrivate) {
                    HStack(spacing: 6) {
                        Image(systemName: "lock.fill").font(.system(size: 11))
                        Text("Make project private").font(.custom("Menlo", size: 14))
                    }
                    .foregroundStyle(M.danger)
                    .frame(maxWidth: .infinity, minHeight: 20)
                    .padding(8)
                    .contentShape(Rectangle())
                }
                .buttonStyle(.plain)
                .padding(-8)
                .padding(.top, 14)
            }
            reveal(4) {
                Text("Permissions")
                    .font(.custom("Menlo", size: 12))
                    .tracking(0.4)
                    .foregroundStyle(M.faint)
                    .frame(maxWidth: .infinity, minHeight: 16, alignment: .leading)
                    .padding(.top, 24)
                    .padding(.bottom, 2)
            }
            ForEach(Array(permissions.enumerated()), id: \.offset) { index, permission in
                reveal(5 + index) { permissionRow(index, permission) }
            }
        }
    }

    private func reveal<Content: View>(_ index: Int, @ViewBuilder _ content: () -> Content) -> some View {
        content().modifier(Driven(panel) { view, p in
            let t = min(1, max(0, (p * M.panelIn - Double(index) * M.rowStagger) / M.rowIn))
            view.opacity(t).offset(y: (1 - t) * M.rowRise)
        })
    }

    private func action(_ icon: String, _ label: String) -> some View {
        VStack(spacing: 2) {
            Image(systemName: icon).font(.system(size: 15, weight: .medium)).frame(height: 18)
            Text(label).font(.system(size: 13, weight: .semibold)).frame(height: 16)
        }
        .foregroundStyle(M.ink)
        .frame(maxWidth: .infinity)
        .frame(height: 48)
        .background(RoundedRectangle(cornerRadius: 14, style: .continuous).fill(M.well))
    }

    private func permissionRow(_ index: Int, _ permission: StageCardSheetPermission) -> some View {
        HStack(spacing: 12) {
            Image(systemName: permission.icon)
                .font(.system(size: 15))
                .foregroundStyle(M.body)
                .frame(width: 36, height: 36)
                .background(RoundedRectangle(cornerRadius: 10, style: .continuous).fill(M.well))
            VStack(alignment: .leading, spacing: 0) {
                Text(permission.title)
                    .font(.system(size: 15, weight: .semibold))
                    .tracking(-0.2)
                    .foregroundStyle(M.ink)
                    .frame(height: 20)
                Text(permission.caption)
                    .font(.system(size: 13))
                    .foregroundStyle(M.mute)
                    .lineLimit(2)
                    .lineSpacing(1.5)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            Button { toggle(index) } label: {
                let isOn = states[index]
                ZStack(alignment: .leading) {
                    Capsule().fill(isOn ? M.on : M.off)
                    Circle().fill(.white)
                        .frame(width: M.switchHeight - 4, height: M.switchHeight - 4)
                        .shadow(color: .black.opacity(0.15), radius: 4, y: 3)
                        .shadow(color: .black.opacity(0.06), radius: 0.5, y: 1)
                        .padding(2)
                        .offset(x: isOn ? M.switchWidth - M.switchHeight : 0)
                        .animation(M.knob, value: isOn)
                }
                .frame(width: M.switchWidth, height: M.switchHeight)
                .padding(8)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .padding(-8)
            .accessibilityLabel(permission.title)
            .accessibilityValue(states[index] ? "On" : "Off")
            .accessibilityAddTraits(.isToggle)
        }
        .frame(height: M.rowHeight)
        .overlay(alignment: .top) {
            if index > 0 { Rectangle().fill(M.line).frame(height: M.hairline) }
        }
    }

    // MARK: The dock and the alert

    private var dock: some View {
        VStack(spacing: 12) {
            Button(action: enable) {
                ZStack {
                    HStack(spacing: 8) {
                        Image(systemName: "link").font(.system(size: 15, weight: .semibold))
                        Text(enableLabel).font(.system(size: 16, weight: .semibold)).tracking(-0.2)
                    }
                    .foregroundStyle(.white)
                    .modifier(Driven(busy) { content, p in content.opacity(1 - p) })
                    TimelineView(.animation(paused: stage != .loading)) { timeline in
                        let turns = stage == .loading
                            ? timeline.date.timeIntervalSince(loadingSince).truncatingRemainder(dividingBy: M.spin) / M.spin
                            : 0
                        // A ring with its top quarter lit, like a border whose top edge is white.
                        Circle()
                            .strokeBorder(.white.opacity(0.25), lineWidth: 2)
                            .overlay {
                                Circle().inset(by: 1).trim(from: 0, to: 0.25)
                                    .stroke(.white, lineWidth: 2)
                                    .rotationEffect(.degrees(-135))
                            }
                            .frame(width: 20, height: 20)
                            .rotationEffect(.degrees(turns * 360))
                    }
                    .modifier(Driven(busy) { content, p in content.opacity(p) })
                }
                .frame(maxWidth: .infinity)
                .frame(height: M.buttonHeight)
                .background {
                    Color.clear.modifier(Driven(sheet) { _, p in
                        Capsule().fill(M.buttonColor(min(1, max(0, p / 0.4))))
                    })
                    .shadow(color: M.ink.opacity(0.18), radius: 8, y: 6)
                }
                .contentShape(Capsule())
            }
            .buttonStyle(.plain)
            .accessibilityLabel(enableLabel)
            Text(footnote)
                .font(.system(size: 12))
                .lineSpacing(1.7)
                .multilineTextAlignment(.center)
                .foregroundStyle(M.faint)
                .padding(.horizontal, 12)
                .frame(height: M.footnoteHeight, alignment: .top)
        }
    }

    private var dialogLayer: some View {
        ZStack {
            M.scrim.modifier(Driven(alert) { content, p in content.opacity(p) })
            VStack(spacing: 0) {
                VStack(spacing: 4) {
                    Text("Make project private?")
                        .font(.system(size: 17, weight: .semibold))
                        .tracking(-0.3)
                        .foregroundStyle(M.ink)
                    Text("This will disable the link and remove everyone from the project.")
                        .font(.system(size: 13))
                        .lineSpacing(2)
                        .foregroundStyle(M.body)
                }
                .multilineTextAlignment(.center)
                .padding(.horizontal, 20)
                .padding(.top, 20)
                .padding(.bottom, 18)
                Rectangle().fill(M.dialogLine).frame(height: M.hairline)
                HStack(spacing: 0) {
                    Button(action: closeDialog) {
                        Text("Cancel").font(.system(size: 17)).foregroundStyle(M.accent)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(StageCardSheetDialogStyle(held: false))
                    Rectangle().fill(M.dialogLine).frame(width: M.hairline)
                    Button(action: makePrivate) {
                        Text("Continue").font(.system(size: 17, weight: .semibold)).foregroundStyle(M.danger)
                            .frame(maxWidth: .infinity, maxHeight: .infinity)
                            .contentShape(Rectangle())
                    }
                    .buttonStyle(StageCardSheetDialogStyle(held: autoPressed))
                }
                .frame(height: 46)
            }
            .frame(width: M.dialogWidth)
            .background(M.dialog)
            .clipShape(RoundedRectangle(cornerRadius: 16, style: .continuous))
            .shadow(color: .black.opacity(0.22), radius: 24, y: 20)
            .shadow(color: .black.opacity(0.08), radius: 3, y: 2)
            .modifier(Driven(alert) { content, p in content.opacity(min(1, p * 1.4)).scaleEffect(1.12 - 0.12 * p) })
        }
    }

    // MARK: Actions

    private func move(_ value: Binding<Double>, to target: Double, spring: Animation = M.spring) {
        withAnimation(reduceMotion ? M.timing(M.fade) : spring) { value.wrappedValue = target }
    }

    private func animate(_ value: Binding<Double>, to target: Double, _ animation: Animation?) {
        withAnimation(animation) { value.wrappedValue = target }
    }

    private func open() {
        pending = nil
        stage = .open
        move($sheet, to: 1)
        animate($panel, to: 1, M.timing(reduceMotion ? M.fade : M.panelIn, curve: M.quadOut))
        animate($streak, to: 1, reduceMotion ? nil : M.timing(M.streakWipe, curve: M.cubicOut))
        onEnable?()
    }

    private func enable() {
        guard stage == .closed else { return }
        stage = .loading
        loadingSince = .now
        animate($busy, to: 1, M.timing(M.busyIn))
        pending = Task { @MainActor in
            try? await Task.sleep(for: .seconds(M.loading), tolerance: .zero)
            guard !Task.isCancelled else { return }
            open()
        }
    }

    private func closeDialog() {
        dialog = false
        autoPressed = false
        move($alert, to: 0, spring: M.dialogSpring)
    }

    private func makePrivate() {
        closeDialog()
        stage = .closed
        // The artwork waits for the panel to clear before it grows back.
        animate($sheet, to: 0, reduceMotion ? M.timing(M.fade) : M.spring.delay(M.growDelay / 1000))
        animate($panel, to: 0, M.timing(reduceMotion ? M.fade : M.panelOut, curve: M.quadIn))
        animate($streak, to: 0, M.timing(M.fade))
        animate($busy, to: 0, M.timing(reduceMotion ? M.fade : 500))
        onMakePrivate?()
    }

    private func askPrivate() {
        dialog = true
        move($alert, to: 1, spring: M.dialogSpring)
    }

    private func toggle(_ index: Int) {
        var next = states
        next[index].toggle()
        on = next
        onPermissionChange?(index, next[index])
    }

    private func rest() {
        pending?.cancel()
        pending = nil
        stage = .closed
        dialog = false
        autoPressed = false
        on = permissions.map { _ in false }
        for value in [$sheet, $panel, $streak, $busy, $alert] { animate(value, to: 0, M.timing(M.restFade)) }
    }

    /// A touch takes over from the autoPlay loop, which starts again from rest a while later.
    private func touched() {
        autoPressed = false
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
            (Self.tapAt, enable),
            (Self.dialogAt, askPrivate),
            (Self.continuePressAt, { autoPressed = true }),
            (Self.confirmAt, makePrivate),
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

/// Rays of light fanning out from the artwork's lower left, in a 100 × 100 box, revealed left to
/// right by a clip that is `wipe` wide.
private struct StageCardSheetRays: View {
    var wipe: Double

    // x1, y1, x2, y2, width, peak opacity.
    private static let rays: [[Double]] = [
        [-6, 80, 104, 30, 1.5, 0.95],
        [6, 76, 102, 46, 0.7, 0.9],
        [14, 84, 98, 56, 0.5, 0.8],
        [0, 72, 90, 22, 0.45, 0.85],
        [22, 70, 106, 38, 1.0, 0.9],
        [-8, 88, 80, 64, 1.1, 0.75],
        [30, 64, 100, 26, 0.4, 0.8],
        [10, 92, 94, 72, 0.45, 0.6],
        [42, 60, 106, 18, 0.65, 0.85],
        [16, 80, 74, 58, 3.2, 0.28],
        [-4, 74, 96, 36, 5, 0.16],
    ]
    // Four-pointed glints near the rays' ends: x, y, size.
    private static let glints: [[Double]] = [[80, 20, 3.2], [66, 31, 1.8], [90, 46, 1.4]]

    var body: some View {
        Canvas { context, size in
            guard wipe > 0 else { return }
            context.scaleBy(x: size.width / 100, y: size.height / 100)
            context.clip(to: Path(CGRect(x: -8, y: 0, width: wipe, height: 100)))
            for r in Self.rays {
                var path = Path()
                path.move(to: CGPoint(x: r[0], y: r[1]))
                path.addLine(to: CGPoint(x: r[2], y: r[3]))
                let gradient = Gradient(stops: [
                    .init(color: .white.opacity(0), location: 0),
                    .init(color: .white.opacity(r[5]), location: 0.62),
                    .init(color: .white.opacity(0), location: 1),
                ])
                context.stroke(
                    path,
                    with: .linearGradient(gradient, startPoint: CGPoint(x: r[0], y: r[1]), endPoint: CGPoint(x: r[2], y: r[3])),
                    style: StrokeStyle(lineWidth: r[4], lineCap: .round)
                )
            }
            for g in Self.glints {
                context.fill(Self.glint(x: g[0], y: g[1], r: g[2]), with: .color(.white.opacity(0.9)))
            }
        }
    }

    private static func glint(x: Double, y: Double, r: Double) -> Path {
        let k = r * 0.22
        let points = [(x, y - r), (x + k, y - k), (x + r, y), (x + k, y + k), (x, y + r), (x - k, y + k), (x - r, y), (x - k, y - k)]
        return Path { path in
            path.addLines(points.map { CGPoint(x: $0.0, y: $0.1) })
            path.closeSubpath()
        }
    }
}

private struct StageCardSheetDialogStyle: ButtonStyle {
    let held: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label.background(Color.black.opacity(held || configuration.isPressed ? 0.08 : 0))
    }
}

private typealias M = StageCardSheetMetrics

private enum StageCardSheetMetrics {
    static let page = Color(hex: 0xfdfcfd)
    static let behind = Color(hex: 0xc9c8cd)
    static let ink = Color(hex: 0x111113)
    static let body = Color(hex: 0x3c3c43)
    static let mute = Color(hex: 0x7c7c83)
    static let faint = Color(hex: 0x9a9aa0)
    static let well = Color(hex: 0xf2f2f4)
    static let line = Color.rgba(60, 60, 67, 0.12)
    static let danger = Color(hex: 0xe5252a)
    static let accent = Color(hex: 0x2b7be8)
    static let on = Color(hex: 0x34c759)
    static let off = Color(hex: 0xe9e9eb)
    static let avatar = Color(hex: 0x8e8e93)
    static let artStops: [Gradient.Stop] = [
        .init(color: Color(hex: 0xf9a8dc), location: 0),
        .init(color: Color(hex: 0xee72d6), location: 0.38),
        .init(color: Color(hex: 0xd43be0), location: 0.74),
        .init(color: Color(hex: 0xb81ad2), location: 1),
    ]
    static let shade = Color.rgba(120, 0, 150, 1)
    static let glow = Color.rgba(226, 72, 240, 0.42)
    static let contact = Color.rgba(110, 0, 130, 0.16)
    static let glowPage = Color.rgba(232, 96, 246, 0.10)
    static let scrim = Color.black.opacity(0.32)
    static let dialog = Color.rgba(250, 250, 252, 0.97)
    static let dialogLine = Color.rgba(60, 60, 67, 0.3)
    static let hairline = 1.0 / 3

    /// The button greys out as the sheet opens: a straight mix from ink to light grey.
    static func buttonColor(_ t: Double) -> Color {
        Color(.sRGB, red: (17 + (214 - 17) * t) / 255, green: (17 + (214 - 17) * t) / 255, blue: (19 + (219 - 19) * t) / 255)
    }

    static let margin = 20.0
    static let close = 36.0
    // The page is a sheet over a darkened screen, with the page it came from peeking out behind it.
    static let sheetGap = 10.0
    static let sheetRadius = 14.0
    // The artwork as a hero, and as the small cover above the panel (from the sheet's top).
    static let artClosedMax = 330.0
    static let artOpen = 120.0
    static let artRadiusClosed = 22.0
    static let artRadiusOpen = 14.0
    static let artTopClosed = 104.0
    static let artTopOpen = 24.0
    static let titleGap = 24.0
    static let titleGapOpen = 14.0
    static let titleHeight = 32.0
    static let captionHeight = 20.0
    // The title shrinks with the artwork, from its top edge.
    static let titleScaleOpen = 0.78
    static let panelGap = 20.0
    static let buttonHeight = 52.0
    static let footnoteHeight = 32.0
    static let dockHeight = buttonHeight + 12 + footnoteHeight
    static let rowHeight = 64.0
    static let switchWidth = 51.0
    static let switchHeight = 31.0
    static let dialogWidth = 270.0

    // One value moves the artwork and reveals the panel, so they read as one object.
    static let spring = Animation.interpolatingSpring(mass: 1, stiffness: 130, damping: 19)
    static let dialogSpring = Animation.interpolatingSpring(mass: 1, stiffness: 260, damping: 17)
    static let knob = Animation.timingCurve(0.25, 0.1, 0.25, 1, duration: 0.2)
    static let fade = 180.0
    /// Seconds the spinner shows before the sheet opens.
    static let loading = 0.7
    static let busyIn = 160.0
    static let spin = 0.7
    // Rows of the panel rise one after another.
    static let rowIn = 450.0
    static let rowStagger = 60.0
    static let rowRise = 40.0
    static let panelIn = rowIn + rowStagger * 8
    static let panelOut = 200.0
    static let growDelay = 110.0
    static let streakWipe = 520.0
    static let restFade = 350.0

    static let quadInOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.455, y: 0.03), endControlPoint: UnitPoint(x: 0.515, y: 0.955))
    static let quadOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.5, y: 1), endControlPoint: UnitPoint(x: 0.89, y: 1))
    static let quadIn = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.11, y: 0), endControlPoint: UnitPoint(x: 0.5, y: 0))
    static let cubicOut = UnitCurve.bezier(startControlPoint: UnitPoint(x: 0.33, y: 1), endControlPoint: UnitPoint(x: 0.68, y: 1))

    /// A timed animation of `ms` milliseconds; Reanimated's default curve is quad in-out.
    static func timing(_ ms: Double, curve: UnitCurve = quadInOut) -> Animation {
        .timingCurve(curve, duration: ms / 1000)
    }
}
