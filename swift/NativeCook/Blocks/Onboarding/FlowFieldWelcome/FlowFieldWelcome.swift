import SwiftUI

/// A black welcome screen whose hero is a field of 324 short white strokes. Packed, they are a
/// small shimmering square; opening, the square is torn into a pinwheel and settles as a vortex
/// of rings round a wandering eye, and closing runs the same way back. A tap on the field opens
/// or closes it, and a finger on it pushes the strokes away and turns them like filings.
struct FlowFieldWelcome: View {
    var title = "Welcome"
    var subtitle = "Your journey starts from here"
    var primaryLabel = "Continue with Phone"
    var secondaryLabel = "Continue with Email"
    /// The legal note reads: `legal`, `termsLabel`, `legalAnd`, `privacyLabel`.
    var legal = "By pressing on “Continue with...” you agree\nto our "
    var termsLabel = "Terms of Service"
    var legalAnd = " and "
    var privacyLabel = "Privacy Policy"
    var background = Color.black
    /// The strokes, the title and the first button.
    var ink = Color.white
    /// The subtitle and the legal note.
    var mute = Color(hex: 0x8E8E93)
    /// The second button.
    var surface = Color(hex: 0x2C2C2E)
    /// Opens and closes the field by itself, twice every `FlowFieldWelcomeMotion.loop` seconds.
    var autoPlay = false
    /// Called when the field starts to open (true) or to close (false).
    var onToggle: ((Bool) -> Void)? = nil
    var onPrimary: (() -> Void)? = nil
    var onSecondary: (() -> Void)? = nil
    var onTerms: (() -> Void)? = nil
    var onPrivacy: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    /// When the screen appeared: the clock of the packed strokes' twitch.
    @State private var appeared = Date()
    @State private var open = false
    /// How far the field has opened, at a steady pace; the field eases it per stroke.
    @State private var shape = Tween(0)
    /// The eye circles from `eyeSince` on, starting at the angle `eyeHeld`; it stands still while the field closes.
    @State private var eyeSince: Date? = nil
    @State private var eyeHeld = M.eyeFrom
    /// The touch, from the field's centre in the field's own points, and how strongly it pulls.
    @State private var finger = CGPoint.zero
    @State private var pull = Tween(0)
    /// A finger is on the field: the loop waits.
    @State private var holding = false
    /// Changes on every touch, which restarts the autoPlay loop behind a pause.
    @State private var touches = 0
    @State private var tapHaptic = UIImpactFeedbackGenerator(style: .light)

    var body: some View {
        GeometryReader { proxy in
            // The reader is laid out inside the safe area; the screen itself is that much larger.
            let insets = proxy.safeAreaInsets
            let size = CGSize(width: proxy.size.width + insets.leading + insets.trailing, height: proxy.size.height + insets.top + insets.bottom)
            let markBottom = insets.top + Metrics.markTop + Metrics.mark
            let bottom = max(insets.bottom, 20)
            let room = size.height - bottom - Metrics.foot - markBottom
            let fieldScale = min(1, room / Metrics.fieldRoom)
            let fieldSize = FlowFieldWelcomeField.size * fieldScale

            ZStack(alignment: .top) {
                background
                FlowFieldWelcomeMark(ink: ink, background: background)
                    .frame(width: Metrics.mark, height: Metrics.mark)
                    .padding(.top, insets.top + Metrics.markTop)
                    .accessibilityHidden(true)
                field(scale: fieldScale)
                    .frame(width: fieldSize, height: fieldSize)
                    .position(x: size.width / 2, y: markBottom + room * Metrics.fieldAt)
                foot
                    .padding(.horizontal, Metrics.side)
                    .padding(.bottom, bottom)
                    .frame(width: size.width, height: size.height, alignment: .bottom)
            }
            .frame(width: size.width, height: size.height, alignment: .top)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.dark)
        .task(id: LoopKey(on: autoPlay, touches: touches, holding: holding)) { await loop() }
    }

    // MARK: The field

    private func field(scale: Double) -> some View {
        // Reduced motion: nothing runs; the two figures cross-fade when `open` changes.
        TimelineView(.animation(paused: reduceMotion)) { timeline in
            let now = timeline.date
            FlowFieldWelcomeField(
                progress: reduceMotion ? (open ? 1 : 0) : shape.value(at: now),
                opening: open,
                clock: now.timeIntervalSince(appeared),
                eye: eye(at: now),
                finger: finger,
                pull: pull.value(at: now),
                still: reduceMotion,
                color: ink
            )
            .animation(reduceMotion ? .linear(duration: M.fade) : nil, value: open)
        }
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0)
                .onChanged { value in
                    if !holding {
                        holding = true
                        pull.retarget(1, duration: M.pullIn, curve: Ease.out)
                    }
                    let half = FlowFieldWelcomeField.size * scale / 2
                    finger = CGPoint(x: (value.location.x - half) / scale, y: (value.location.y - half) / scale)
                }
                .onEnded { value in
                    holding = false
                    pull.retarget(0, duration: M.pullOut, curve: Ease.out)
                    // A touch that did not move is a tap.
                    if hypot(value.translation.width, value.translation.height) < Metrics.slop {
                        tapHaptic.impactOccurred()
                        morph(!open)
                    }
                    touches += 1
                }
        )
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("Pattern")
        .accessibilityValue(open ? "Open" : "Packed")
        .accessibilityAddTraits(.isButton)
        .accessibilityAction { morph(!open) }
    }

    private func eye(at date: Date) -> Double {
        eyeHeld + (eyeSince.map { M.eyeSpeed * date.timeIntervalSince($0) } ?? 0)
    }

    /// Opens the field into the vortex, or packs it again, from wherever it is.
    private func morph(_ next: Bool) {
        let now = Date()
        let at = shape.value(at: now)
        if next {
            // From the packed field the eye starts where it always starts.
            if at == 0 { eyeHeld = M.eyeFrom }
            eyeSince = now
        } else {
            eyeHeld = eye(at: now)
            eyeSince = nil
        }
        let to = next ? 1.0 : 0.0
        shape.retarget(to, at: now, duration: M.morph * abs(to - at), curve: .linear)
        open = next
        onToggle?(next)
    }

    private func loop() async {
        guard autoPlay, !holding else { return }
        if touches > 0 {
            // A touch takes over; a while later the field packs again and the loop starts over.
            try? await Task.sleep(for: .seconds(M.userHold))
            guard !Task.isCancelled else { return }
            if open {
                morph(false)
                try? await Task.sleep(for: .seconds(M.morph))
            }
        }
        let clock = ContinuousClock()
        var origin = clock.now
        while !Task.isCancelled {
            try? await clock.sleep(until: origin + .seconds(M.openAt))
            guard !Task.isCancelled else { return }
            morph(true)
            try? await clock.sleep(until: origin + .seconds(M.closeAt))
            guard !Task.isCancelled else { return }
            morph(false)
            origin += .seconds(M.cycle)
            try? await clock.sleep(until: origin)
        }
    }

    private struct LoopKey: Equatable {
        let on: Bool
        let touches: Int
        let holding: Bool
    }

    // MARK: The foot

    private var foot: some View {
        VStack(spacing: 0) {
            Text(title)
                .font(.system(size: 46, weight: .semibold))
                .tracking(-1)
                .foregroundStyle(ink)
                .lineLimit(1)
                .minimumScaleFactor(0.6)
                .frame(height: Metrics.titleLine)
            Text(subtitle)
                .font(.system(size: 15))
                .foregroundStyle(mute)
                .lineLimit(1)
                .frame(height: Metrics.subtitleLine)
                .padding(.top, Metrics.subtitleGap)
            VStack(spacing: Metrics.buttonGap) {
                Button {
                    tapHaptic.impactOccurred()
                    onPrimary?()
                } label: {
                    Text(primaryLabel)
                        .foregroundStyle(background)
                        .frame(maxWidth: .infinity)
                        .frame(height: Metrics.button)
                        .background(Capsule().fill(ink))
                }
                Button {
                    tapHaptic.impactOccurred()
                    onSecondary?()
                } label: {
                    HStack(spacing: 8) {
                        FlowFieldWelcomeEnvelope()
                            .stroke(ink, style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
                            .frame(width: 20, height: 20)
                        Text(secondaryLabel)
                            .foregroundStyle(ink)
                    }
                    .frame(maxWidth: .infinity)
                    .frame(height: Metrics.button)
                    .background(Capsule().fill(surface))
                }
            }
            .font(.system(size: 16, weight: .semibold))
            .lineLimit(1)
            .buttonStyle(PressStyle())
            .padding(.top, Metrics.buttonsGap)
            Text(legalNote)
                .font(.system(size: 11))
                .lineSpacing(3)
                .multilineTextAlignment(.center)
                .foregroundStyle(mute)
                .tint(ink)
                .frame(minHeight: Metrics.legalLine * 2)
                .padding(.top, Metrics.legalGap)
                .environment(\.openURL, OpenURLAction { url in
                    if url == Self.termsLink { onTerms?() } else { onPrivacy?() }
                    return .handled
                })
        }
    }

    private static let termsLink = URL(string: "flow-field-welcome:terms")!
    private static let privacyLink = URL(string: "flow-field-welcome:privacy")!

    /// The legal note with its two document names as underlined links.
    private var legalNote: AttributedString {
        func link(_ label: String, _ url: URL) -> AttributedString {
            var part = AttributedString(label)
            part.link = url
            part.underlineStyle = .single
            return part
        }
        return AttributedString(legal) + link(termsLabel, Self.termsLink) + AttributedString(legalAnd) + link(privacyLabel, Self.privacyLink)
    }

    private struct PressStyle: ButtonStyle {
        func makeBody(configuration: Configuration) -> some View {
            configuration.label
                .contentShape(Capsule())
                .scaleEffect(configuration.isPressed ? Metrics.pressScale : 1)
                .animation(.easeOut(duration: Metrics.pressFor), value: configuration.isPressed)
        }
    }
}

private typealias M = FlowFieldWelcomeMotion

private enum Metrics {
    static let mark = 24.0
    static let markTop = 7.0
    static let side = 24.0
    static let titleLine = 56.0
    static let subtitleGap = 10.0
    static let subtitleLine = 20.0
    static let buttonsGap = 43.0
    static let button = 56.0
    static let buttonGap = 10.0
    static let legalGap = 28.0
    static let legalLine = 16.0
    /// The foot's height, from the title's top to the last line of the legal note.
    static let foot = titleLine + subtitleGap + subtitleLine + buttonsGap + button * 2 + buttonGap + legalGap + legalLine * 2
    /// The field's centre sits a little above the middle of the room between the mark and the
    /// title, and the field shrinks when that room is less than this.
    static let fieldAt = 0.435
    static let fieldRoom = 310.0
    /// A finger that moves less than this has tapped.
    static let slop = 10.0
    static let pressScale = 0.97
    static let pressFor = 0.13
}

/// The round mark at the top: a disc with a head and shoulders cut out of it, in a 24-point box.
struct FlowFieldWelcomeMark: View {
    let ink: Color
    let background: Color

    var body: some View {
        Canvas { context, size in
            var context = context
            context.scaleBy(x: size.width / 24, y: size.height / 24)
            context.fill(Path(ellipseIn: CGRect(x: 0, y: 0, width: 24, height: 24)), with: .color(ink))
            context.fill(Path(ellipseIn: CGRect(x: 8.9, y: 6.3, width: 6.2, height: 6.2)), with: .color(background))
            context.fill(Path(ellipseIn: CGRect(x: 5.8, y: 15.2, width: 12.4, height: 9)), with: .color(background))
        }
    }
}

/// An envelope in a 20 × 20 box.
struct FlowFieldWelcomeEnvelope: Shape {
    func path(in rect: CGRect) -> Path {
        let s = rect.width / 20
        var path = Path(roundedRect: CGRect(x: rect.minX + 2 * s, y: rect.minY + 4.5 * s, width: 16 * s, height: 11 * s), cornerRadius: 2.5 * s, style: .circular)
        path.move(to: CGPoint(x: rect.minX + 3.2 * s, y: rect.minY + 6.6 * s))
        path.addLine(to: CGPoint(x: rect.minX + 10 * s, y: rect.minY + 11.4 * s))
        path.addLine(to: CGPoint(x: rect.minX + 16.8 * s, y: rect.minY + 6.6 * s))
        return path
    }
}

#Preview {
    FlowFieldWelcome(autoPlay: true)
}
