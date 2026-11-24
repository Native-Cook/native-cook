import SwiftUI
import UIKit

/// "Help" in an invented notes app, with a Feedback pill that stretches into a popover.
///
/// Width and height are two springs, 50 ms apart; the popover keeps the pill's bottom edge and
/// centre, the label slides up to become the field's placeholder, and after Send the pill says
/// thanks with a check that draws itself. In `autoPlay` every frame is a pure function of the
/// loop time (`MorphingFeedbackPopoverMotion`); a finger takes the same values over live.
struct MorphingFeedbackPopover: View {
    /// Plays the 8 s loop until a finger takes over; it resumes 2 s after the last touch.
    var autoPlay = true
    /// Called with what was typed and the face that was picked (0 worst … 3 best, -1 none).
    var onSend: (String, Int) -> Void = { _, _ in }

    static let loop: Double = MorphingFeedbackPopoverMotion.loop

    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var model = MorphingFeedbackPopoverModel()
    @State private var diagnostics = true
    @State private var text = ""
    @State private var awake = false
    @State private var keyboard = 0.0
    @State private var finger = MorphingFeedbackPopoverFinger()
    @FocusState private var focused: Bool

    private typealias M = MorphingFeedbackPopoverMotion
    private typealias Art = MorphingFeedbackPopoverArt
    private typealias Layout = MorphingFeedbackPopoverLayout
    private static let space = "MorphingFeedbackPopover"

    init(autoPlay: Bool = true) {
        self.autoPlay = autoPlay
    }

    private var playing: Bool { autoPlay && !reducedMotion }

    var body: some View {
        GeometryReader { outer in
            let insets = outer.safeAreaInsets
            GeometryReader { geometry in
                let layout = Layout(width: geometry.size.width, height: geometry.size.height, top: insets.top, bottom: insets.bottom)
                // The keyboard must not cover the popover.
                let liftTarget = max(0, keyboard - (layout.height - layout.pillBottom) + 12)
                ZStack(alignment: .topLeading) {
                    page(layout)
                    TimelineView(.animation(paused: scenePhase != .active || !(playing || awake))) { timeline in
                        let state = model.state(at: timeline.date, playing: playing, reducedMotion: reducedMotion,
                                                hasText: !text.isEmpty, focused: focused, liftTarget: liftTarget)
                        overlay(state, layout)
                    }
                }
                .frame(width: layout.width, height: layout.height, alignment: .topLeading)
                .clipped()
                .coordinateSpace(name: Self.space)
            }
            .ignoresSafeArea()
        }
        .background(Art.paper.ignoresSafeArea())
        .ignoresSafeArea(.keyboard)
        .preferredColorScheme(.light)
        .onReceive(NotificationCenter.default.publisher(for: UIResponder.keyboardWillChangeFrameNotification)) { note in
            guard let end = note.userInfo?[UIResponder.keyboardFrameEndUserInfoKey] as? CGRect else { return }
            awake = true
            keyboard = max(0, UIScreen.main.bounds.height - end.minY)
        }
        .task(id: awake) {
            // Without the loop the timeline only runs while something moves.
            guard awake, !playing else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(250))
                if model.resting, !focused { awake = false }
            }
        }
    }

    // MARK: The page behind

    private func page(_ layout: Layout) -> some View {
        ZStack(alignment: .topLeading) {
            VStack(alignment: .leading, spacing: 0) {
                HStack(spacing: 4) {
                    Image(systemName: "chevron.left")
                        .font(.system(size: 17, weight: .semibold))
                        .frame(width: 17, height: 17)
                    Text("Settings")
                        .font(.system(size: 17))
                }
                .foregroundStyle(Art.sea)
                .frame(height: 24)
                .padding(.bottom, 8)
                Text("Help")
                    .font(.system(size: 32, weight: .heavy))
                    .tracking(-0.4)
                    .foregroundStyle(Art.ink)
                    .frame(height: 38)
                    .padding(.bottom, 16)
                    .accessibilityAddTraits(.isHeader)
                VStack(spacing: 20) {
                    group {
                        row("book", "Getting started", chevron: true)
                        row("keyboard", "Keyboard shortcuts", chevron: true, divider: true)
                        row("square.and.arrow.down", "Import notes", value: "3 apps", chevron: true, divider: true)
                    }
                    group {
                        row("arrow.triangle.2.circlepath", "Sync", value: "Up to date")
                        row("waveform.path.ecg", "Share diagnostics", divider: true) {
                            Toggle("Share diagnostics", isOn: $diagnostics)
                                .labelsHidden()
                                .tint(Art.sea)
                                .frame(width: 52, height: 32)
                        }
                    }
                    group {
                        row("bell", "What’s new", value: "4.2", chevron: true)
                    }
                }
            }
            .frame(width: layout.width - Layout.side * 2, alignment: .leading)
            .offset(x: Layout.side, y: layout.top + 8)

            Text("Tidemark 4.2 (318)")
                .font(.system(size: 12).monospacedDigit())
                .foregroundStyle(Art.inkSoft)
                .frame(width: layout.width)
                .offset(y: layout.anchor.minY - 30)
        }
        .frame(width: layout.width, height: layout.height, alignment: .topLeading)
    }

    private func group(@ViewBuilder rows: () -> some View) -> some View {
        let outline = RoundedRectangle(cornerRadius: 18, style: .continuous)
        return VStack(spacing: 0) { rows() }
            .background {
                outline.fill(Art.card).shadow(color: Art.ink.opacity(0.05), radius: 9, y: 6)
                outline.fill(Art.card).shadow(color: Art.ink.opacity(0.06), radius: 1, y: 1)
            }
    }

    private func row(_ icon: String, _ label: String, value: String? = nil, chevron: Bool = false, divider: Bool = false) -> some View {
        row(icon, label, value: value, chevron: chevron, divider: divider) { EmptyView() }
    }

    private func row(_ icon: String, _ label: String, value: String? = nil, chevron: Bool = false, divider: Bool = false,
                     @ViewBuilder trailing: () -> some View) -> some View {
        HStack(spacing: 12) {
            Image(systemName: icon)
                .resizable()
                .scaledToFit()
                .fontWeight(.medium)
                .foregroundStyle(Art.sea)
                .frame(width: 13.5, height: 13.5)
                .frame(width: 30, height: 30)
                .background(RoundedRectangle(cornerRadius: 9, style: .continuous).fill(Art.sea.opacity(0.12)))
            Text(label)
                .font(.system(size: 16))
                .foregroundStyle(Art.ink)
                .frame(maxWidth: .infinity, alignment: .leading)
            if let value {
                Text(value)
                    .font(.system(size: 15).monospacedDigit())
                    .foregroundStyle(Art.inkSoft)
            }
            trailing()
            if chevron {
                Image(systemName: "chevron.right")
                    .font(.system(size: 12, weight: .semibold))
                    .foregroundStyle(Art.ink.opacity(0.28))
                    .frame(width: 12, height: 12)
            }
        }
        .padding(.horizontal, 14)
        .frame(height: Layout.row)
        .overlay(alignment: .top) {
            if divider { Art.ink.opacity(0.10).frame(height: 1 / 3).padding(.leading, 56) }
        }
    }

    // MARK: The popover

    private func overlay(_ state: MorphingFeedbackPopoverState, _ layout: Layout) -> some View {
        let open = M.clamp(state.tall, 0, 1)
        return ZStack(alignment: .topLeading) {
            Art.scrim.opacity(M.scrim * open)
                .allowsHitTesting(false)
            // Touches outside only count while the popover is out.
            Color.clear
                .contentShape(Rectangle())
                .gesture(outsideGesture)
                .allowsHitTesting(state.goal == 1)

            frame(state, layout)

            if playing, let ghost = state.ghost, ghost.opacity > 0 {
                ghostDot(ghost, layout)
            }
        }
        .frame(width: layout.width, height: layout.height, alignment: .topLeading)
    }

    /// The stretching frame. Its content does not move with it: it stays where it rests when
    /// open, and the frame opens around it.
    private func frame(_ state: MorphingFeedbackPopoverState, _ layout: Layout) -> some View {
        let anchor = layout.anchor, size = layout.size
        let open = M.clamp(state.tall, 0, 1)
        let width = layout.width(at: state.wide), height = layout.height(at: state.tall)
        let radius = min(height / 2, M.mix(anchor.height / 2, M.radiusOpen, open))
        let outline = RoundedRectangle(cornerRadius: radius, style: .continuous)
        let said = state.thanks
        let swell = said < 0 || reducedMotion ? 0 : sin(Double.pi * M.easeInOutCubic(M.seg(said, M.thanksPulse.0, M.thanksPulse.1)))
        let scale = (1 - (1 - M.pressScale) * state.press) * (1 + M.thanksSwell * swell)
        return ZStack(alignment: .topLeading) {
            outline.fill(Art.popover).shadow(color: Art.depth.opacity(0.26), radius: 16, y: 14)
            outline.fill(Art.popover).shadow(color: Art.depth.opacity(0.28), radius: 1, y: 1)
            ZStack(alignment: .topLeading) {
                content(state, layout)
                label(state, layout)
                thanks(state, layout)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .offset(x: (width - size.width) / 2, y: height - size.height)
            .frame(width: width, height: height, alignment: .topLeading)
            .clipShape(outline)
            // The top inside edge catches a little light.
            outline.fill(Color.white.opacity(0.10))
                .mask {
                    ZStack {
                        outline
                        outline.offset(y: 1).blendMode(.destinationOut)
                    }
                    .compositingGroup()
                }
                .allowsHitTesting(false)
        }
        .frame(width: width, height: height, alignment: .topLeading)
        .contentShape(Rectangle())
        .simultaneousGesture(frameGesture(layout))
        .accessibilityElement(children: .contain)
        .scaleEffect(scale)
        .offset(x: anchor.midX - width / 2, y: anchor.maxY - height + state.dragY - state.lift * open)
    }

    /// Field, faces and Send: each fades up once the frame is nearly open, one after another.
    private func content(_ state: MorphingFeedbackPopoverState, _ layout: Layout) -> some View {
        let gate = M.seg(state.tall, M.contentGate.0, M.contentGate.1)
        func shown(_ order: Int) -> Double {
            M.easeOutCubic(M.seg(state.content, M.contentStart[order], M.contentStart[order] + M.contentSpan)) * gate
        }
        func rise(_ u: Double) -> Double { reducedMotion ? 0 : (1 - u) * M.contentRise }
        let field = shown(0), faces = shown(1), send = shown(2)
        let fieldWidth = layout.size.width - Layout.pad * 2
        let textWidth = fieldWidth - Layout.fieldPad * 2
        let fieldShape = RoundedRectangle(cornerRadius: 16, style: .continuous)
        return ZStack(alignment: .topLeading) {
            ZStack(alignment: .topLeading) {
                fieldShape.fill(Art.onDark.opacity(0.07))
                fieldShape.strokeBorder(Art.onDark.opacity(0.09), lineWidth: 1 / 3)
                // What autoPlay types. It is not the real field, so no keyboard comes up.
                MorphingFeedbackPopoverTyped(text: String(M.sample.prefix(state.typed)), width: textWidth)
                    .offset(x: Layout.fieldPad, y: Layout.fieldPad)
                    .opacity(state.manual ? 0 : 1)
                    .accessibilityHidden(true)
                TextField("", text: $text, axis: .vertical)
                    .font(.system(size: 16))
                    .lineSpacing(Layout.textLine - 19.1)
                    .foregroundStyle(Art.onDark)
                    .tint(Art.mint)
                    .lineLimit(1...6)
                    .focused($focused)
                    .environment(\.colorScheme, .dark)
                    .frame(width: textWidth, alignment: .topLeading)
                    .padding(.top, (Layout.textLine - 19.1) / 2)
                    .offset(x: Layout.fieldPad, y: Layout.fieldPad)
                    .opacity(state.manual ? 1 : 0)
                    .allowsHitTesting(state.manual && state.goal == 1)
                    .accessibilityLabel("Feedback")
            }
            .frame(width: fieldWidth, height: Layout.fieldHeight, alignment: .topLeading)
            .clipShape(fieldShape)
            .opacity(field)
            .offset(x: Layout.pad, y: Layout.pad + rise(field))

            HStack(spacing: Layout.faceStep - Layout.face) {
                ForEach(0..<4, id: \.self) { index in
                    ZStack {
                        MorphingFeedbackPopoverFace(index: index, on: false)
                        MorphingFeedbackPopoverFace(index: index, on: true)
                            .opacity(state.facePicked[index])
                    }
                    .frame(width: Layout.face, height: Layout.face)
                    .scaleEffect(state.facePop[index])
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel(Layout.moods[index])
                    .accessibilityAddTraits(.isButton)
                }
            }
            .opacity(faces)
            .offset(x: Layout.faceLeft, y: Layout.rowTop + rise(faces))

            ZStack {
                Capsule().fill(Art.mint).shadow(color: Art.sendShadow.opacity(0.30), radius: 6, y: 4)
                Capsule().fill(Color.white.opacity(0.45))
                    .mask {
                        ZStack {
                            Capsule()
                            Capsule().offset(y: 1).blendMode(.destinationOut)
                        }
                        .compositingGroup()
                    }
                Text("Send")
                    .font(.system(size: 16, weight: .semibold))
                    .foregroundStyle(Art.ink)
            }
            .frame(width: Layout.sendWidth, height: Layout.rowHeight)
            .scaleEffect(1 - 0.05 * state.sendPress)
            .opacity(state.sendOpacity)
            .opacity(send)
            .offset(x: layout.sendLeft, y: Layout.rowTop + rise(send))
            .accessibilityElement(children: .ignore)
            .accessibilityLabel("Send")
            .accessibilityAddTraits(.isButton)
        }
        .frame(width: layout.size.width, height: layout.size.height, alignment: .topLeading)
    }

    /// The pill's label, which travels up and becomes the field's placeholder.
    private func label(_ state: MorphingFeedbackPopoverState, _ layout: Layout) -> some View {
        let anchor = layout.anchor, size = layout.size
        let open = M.clamp(state.tall, 0, 1)
        let ride = reducedMotion ? open : state.labelFollow
        let said = state.thanks
        let afterSend = said < 0 ? 1 : M.clamp(1 - M.seg(said, 0, M.thanksLabelOut) + M.seg(said, M.thanksLabelIn.0, M.thanksLabelIn.1), 0, 1)
        let covered = (state.covered ? 1 : 0) * M.seg(open, M.contentGate.0, M.contentGate.1)
        return Text("Feedback")
            .font(.system(size: Layout.labelSize, weight: .semibold))
            .foregroundStyle(Art.onDark)
            .lineLimit(1)
            .fixedSize()
            .frame(height: Layout.textLine)
            .offset(y: 0.5)
            .opacity(M.mix(1, M.placeholderAlpha, M.clamp(ride, 0, 1)) * (1 - covered) * afterSend)
            .offset(x: M.mix(size.width / 2 - Layout.labelWidth / 2, Layout.pad + Layout.fieldPad, ride),
                    y: M.mix(size.height - anchor.height / 2 - Layout.textLine / 2, Layout.pad + Layout.fieldPad, ride))
            .allowsHitTesting(false)
    }

    /// What the pill says after Send, next to a check that draws itself.
    private func thanks(_ state: MorphingFeedbackPopoverState, _ layout: Layout) -> some View {
        let anchor = layout.anchor, size = layout.size
        let said = state.thanks
        let opacity = said < 0 ? 0 : M.seg(said, M.thanksIn.0, M.thanksIn.1) * (1 - M.seg(said, M.thanksOut.0, M.thanksOut.1))
        func drawn(_ span: (Double, Double)) -> Double {
            said < 0 ? 0 : reducedMotion ? 1 : M.easeOutCubic(M.seg(said, span.0, span.1))
        }
        func stroke(_ x: Double, _ y: Double, _ length: Double, _ angle: Double, _ u: Double) -> some View {
            Capsule()
                .fill(Art.mint)
                .frame(width: length, height: Layout.stroke)
                .scaleEffect(x: max(u, 0.001), y: 1, anchor: .leading)
                .rotationEffect(.degrees(angle), anchor: .leading)
                .opacity(u > 0 ? 1 : 0)
                .offset(x: x, y: y - Layout.stroke / 2)
        }
        return HStack(spacing: 6) {
            // The check's corners in its 18 pt box: start, elbow, tip.
            ZStack(alignment: .topLeading) {
                stroke(3.5, 9.5, 5.66, 45, drawn(M.thanksShort))
                stroke(7.5, 13.5, 11.01, -50.5, drawn(M.thanksLong))
            }
            .frame(width: 18, height: 18, alignment: .topLeading)
            Text("Thanks!")
                .font(.system(size: Layout.labelSize, weight: .semibold))
                .foregroundStyle(Art.onDark)
                .fixedSize()
        }
        .offset(y: 0.5)
        .frame(width: anchor.width, height: anchor.height)
        .opacity(opacity)
        .offset(x: size.width / 2 - anchor.width / 2, y: size.height - anchor.height)
        .allowsHitTesting(false)
        .accessibilityHidden(opacity == 0)
    }

    private func ghostDot(_ ghost: MorphingFeedbackPopoverGhost, _ layout: Layout) -> some View {
        let origin = layout.origin
        let rowY = origin.y + Layout.rowTop + Layout.rowHeight / 2
        let point: CGPoint
        switch ghost.spot {
        case .pill: point = CGPoint(x: layout.width / 2, y: layout.anchor.midY)
        case .face: point = CGPoint(x: origin.x + Layout.faceLeft + Layout.faceStep * Double(M.ghostFace) + Layout.face / 2, y: rowY)
        case .send: point = CGPoint(x: origin.x + layout.sendLeft + Layout.sendWidth / 2, y: rowY)
        }
        return Circle()
            .fill(Color.white.opacity(0.26))
            .overlay(Circle().strokeBorder(Color.white.opacity(0.8), lineWidth: 1.5))
            .shadow(color: Art.depth.opacity(0.25), radius: 5, y: 2)
            .frame(width: Layout.ghost, height: Layout.ghost)
            .scaleEffect(1 - Layout.ghostPress * ghost.press)
            .opacity(ghost.opacity)
            .offset(x: point.x - Layout.ghost / 2, y: point.y - Layout.ghost / 2)
            .allowsHitTesting(false)
    }

    // MARK: Touch

    private var outsideGesture: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { _ in
                guard !finger.down else { return }
                finger.down = true
                begin { _ in }
            }
            .onEnded { value in
                finger = MorphingFeedbackPopoverFinger()
                guard hypot(value.translation.width, value.translation.height) < 12 else { return }
                var closed = false
                model.touch { live in closed = live.dismiss() }
                if closed {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    focused = false
                }
            }
    }

    /// A touch takes over; what autoPlay had typed moves into the real field.
    private func begin(_ change: (inout MorphingFeedbackPopoverLive) -> Void) {
        awake = true
        if let adopted = model.touch(change) {
            text = String(M.sample.prefix(adopted))
        }
    }

    /// The pill opens on a tap; the open popover takes taps on its faces and Send, and a drag down.
    private func frameGesture(_ layout: Layout) -> some Gesture {
        /// The touch in the open popover's coordinates.
        func inside(_ point: CGPoint, _ live: MorphingFeedbackPopoverLive) -> CGPoint {
            let open = M.clamp(live.state.tall, 0, 1)
            return CGPoint(x: point.x - layout.origin.x, y: point.y - layout.origin.y - live.state.dragY + live.state.lift * open)
        }
        func inRow(_ p: CGPoint) -> Bool { p.y >= Layout.rowTop - 4 && p.y <= Layout.rowTop + Layout.rowHeight + 4 }
        func overSend(_ p: CGPoint) -> Bool { inRow(p) && p.x >= layout.sendLeft - 4 }
        return DragGesture(minimumDistance: 0, coordinateSpace: .named(Self.space))
            .onChanged { value in
                if !finger.down {
                    finger = MorphingFeedbackPopoverFinger(down: true)
                    begin { live in
                        live.fingers = 1
                        if live.state.goal == 0 {
                            live.pressPill(true)
                        } else if overSend(inside(value.startLocation, live)), live.state.covered || live.state.mood >= 0 {
                            live.pressSend(true)
                        }
                    }
                }
                let travel = hypot(value.translation.width, value.translation.height)
                if !finger.panning, travel >= M.dragSlop {
                    finger.panning = true
                    model.touch { live in
                        live.pressPill(false)
                        live.pressSend(false)
                        if live.state.goal == 1 { live.dragBegin() }
                    }
                }
                if finger.panning {
                    model.touch { live in
                        if live.dragging { live.dragMove(value.translation.height) }
                    }
                }
            }
            .onEnded { value in
                let panned = finger.panning
                finger = MorphingFeedbackPopoverFinger()
                var opened = false, closed = false, sent = false, picked = false, field = false
                var mood = -1
                let has = !text.isEmpty
                model.touch { live in
                    live.fingers = 0
                    live.pressPill(false)
                    live.pressSend(false)
                    if panned {
                        closed = live.dragEnd(value.velocity.height)
                    } else if live.state.goal == 0 {
                        opened = live.open()
                    } else {
                        let p = inside(value.location, live)
                        if overSend(p) {
                            if has || live.state.mood >= 0 {
                                mood = live.state.mood
                                sent = live.confirm()
                            }
                        } else if inRow(p) {
                            let index = Int(((p.x - Layout.faceLeft + (Layout.faceStep - Layout.face) / 2) / Layout.faceStep).rounded(.down))
                            if index >= 0, index < 4 {
                                live.pick(index, reducedMotion: reducedMotion)
                                picked = true
                            }
                        } else if p.y < Layout.pad + Layout.fieldHeight {
                            field = true
                        }
                    }
                }
                if opened {
                    text = ""
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                }
                if closed {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    focused = false
                }
                if picked { UISelectionFeedbackGenerator().selectionChanged() }
                if field { focused = true }
                if sent {
                    UINotificationFeedbackGenerator().notificationOccurred(.success)
                    focused = false
                    onSend(text, mood)
                }
            }
    }
}

/// One finger's progress through a gesture.
private struct MorphingFeedbackPopoverFinger {
    var down = false
    var panning = false
}

// MARK: Layout

/// Where everything sits, in points.
struct MorphingFeedbackPopoverLayout {
    private typealias M = MorphingFeedbackPopoverMotion

    static let side = 16.0
    static let row = 52.0
    static let pill = CGSize(width: 132, height: 44)
    static let pillBottom = 28.0 // above the safe area
    static let popoverMax = 361.0
    static let popoverHeight = 232.0
    static let pad = 12.0
    static let fieldHeight = 150.0
    static let fieldPad = 14.0
    static let rowTop = 176.0 // faces and Send
    static let rowHeight = 40.0
    static let faceLeft = 14.0
    static let faceStep = 44.0
    static let face = 40.0
    static let sendWidth = 76.0
    static let labelSize = 16.0
    static let textLine = 20.0
    static let stroke = 2.2
    static let ghost = 46.0
    static let ghostPress = 0.16
    static let moods = ["Bad", "Okay", "Good", "Great"]
    /// The label's width, so it can sit centred in the pill.
    static let labelWidth: Double = {
        let font = UIFont.systemFont(ofSize: labelSize, weight: .semibold)
        return Double(("Feedback" as NSString).size(withAttributes: [.font: font]).width)
    }()

    let width: Double
    let height: Double
    let top: Double
    let pillBottom: Double
    /// The pill's frame.
    let anchor: CGRect
    /// The open popover's size; it keeps the pill's bottom edge and centre.
    let size: CGSize
    /// The open popover's top-left corner.
    let origin: CGPoint
    let sendLeft: Double

    init(width: Double, height: Double, top: Double, bottom: Double) {
        self.width = width
        self.height = height
        self.top = top
        pillBottom = height - max(bottom, 16) - Self.pillBottom
        anchor = CGRect(x: (width - Self.pill.width) / 2, y: pillBottom - Self.pill.height, width: Self.pill.width, height: Self.pill.height)
        size = CGSize(width: min(width - Self.side * 2, Self.popoverMax), height: Self.popoverHeight)
        origin = CGPoint(x: width / 2 - size.width / 2, y: pillBottom - size.height)
        sendLeft = size.width - Self.pad - Self.sendWidth
    }

    func width(at amount: Double) -> Double { max(anchor.width * 0.9, M.mix(anchor.width, size.width, amount)) }
    func height(at amount: Double) -> Double { max(anchor.height * 0.92, M.mix(anchor.height, size.height, amount)) }
}

// MARK: Typed text

/// What autoPlay has typed, laid out the way a text view breaks lines (a SwiftUI `Text` moves a
/// word down to avoid a short last line, which would make the words jump while they are typed).
private struct MorphingFeedbackPopoverTyped: UIViewRepresentable {
    let text: String
    let width: Double

    func makeUIView(context: Context) -> UILabel {
        let label = UILabel()
        label.numberOfLines = 0
        label.isUserInteractionEnabled = false
        label.isAccessibilityElement = false
        return label
    }

    func updateUIView(_ label: UILabel, context: Context) {
        guard label.attributedText?.string != text else { return }
        let paragraph = NSMutableParagraphStyle()
        paragraph.minimumLineHeight = MorphingFeedbackPopoverLayout.textLine
        paragraph.maximumLineHeight = MorphingFeedbackPopoverLayout.textLine
        paragraph.lineBreakStrategy = []
        label.attributedText = NSAttributedString(string: text, attributes: [
            .font: UIFont.systemFont(ofSize: 16),
            .foregroundColor: UIColor(MorphingFeedbackPopoverArt.onDark),
            .paragraphStyle: paragraph,
        ])
    }

    func sizeThatFits(_ proposal: ProposedViewSize, uiView: UILabel, context: Context) -> CGSize? {
        let fit = uiView.sizeThatFits(CGSize(width: width, height: .greatestFiniteMagnitude))
        return CGSize(width: width, height: text.isEmpty ? 0 : fit.height)
    }
}

// MARK: Faces

/// A mood face in a 28 pt box drawn at 30 pt: outlined, or filled once it is picked.
private struct MorphingFeedbackPopoverFace: View {
    let index: Int
    let on: Bool

    private typealias Art = MorphingFeedbackPopoverArt

    var body: some View {
        Canvas { context, size in
            let k = size.width / 28
            context.scaleBy(x: k, y: k)
            let line = on ? Art.ink : Art.onDark.opacity(0.62)
            let ring = Path(ellipseIn: CGRect(x: 2, y: 2, width: 24, height: 24))
            if on { context.fill(ring, with: .color(Art.mint)) }
            context.stroke(ring, with: .color(on ? Art.mint : Art.onDark.opacity(0.62)), lineWidth: 1.6)
            for x in [10.0, 18.0] {
                context.fill(Path(ellipseIn: CGRect(x: x - 1.35, y: 11.4 - 1.35, width: 2.7, height: 2.7)), with: .color(line))
            }
            var mouth = Path()
            switch index {
            case 0:
                mouth.move(to: CGPoint(x: 9.5, y: 19.2))
                mouth.addQuadCurve(to: CGPoint(x: 18.5, y: 19.2), control: CGPoint(x: 14, y: 14.8))
            case 1:
                mouth.move(to: CGPoint(x: 10, y: 18))
                mouth.addLine(to: CGPoint(x: 18, y: 18))
            case 2:
                mouth.move(to: CGPoint(x: 9.5, y: 16.6))
                mouth.addQuadCurve(to: CGPoint(x: 18.5, y: 16.6), control: CGPoint(x: 14, y: 20.6))
            default:
                mouth.move(to: CGPoint(x: 9, y: 15.6))
                mouth.addQuadCurve(to: CGPoint(x: 19, y: 15.6), control: CGPoint(x: 14, y: 23))
                mouth.closeSubpath()
                context.fill(mouth, with: .color(line))
            }
            context.stroke(mouth, with: .color(line), style: StrokeStyle(lineWidth: 1.6, lineCap: .round, lineJoin: .round))
        }
        .frame(width: 30, height: 30)
    }
}

// MARK: Palette

/// A cool paper, one deep sea ink, one mint accent.
enum MorphingFeedbackPopoverArt {
    static let paper = color(0xECF0EE)
    static let card = color(0xFBFCFB)
    static let ink = color(0x14302D)
    static let inkSoft = color(0x6C7F7B)
    static let sea = color(0x2E7F74)
    static let popover = color(0x17332F)
    static let onDark = color(0xF1F7F5)
    static let mint = color(0x9DE6D1)
    static let scrim = color(0x0D1F1D)
    /// Shadow tints under the popover and under Send.
    static let depth = color(0x0A1E1C)
    static let sendShadow = color(0x061614)

    static func color(_ hex: UInt32) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 0xFF) / 255, green: Double((hex >> 8) & 0xFF) / 255,
              blue: Double(hex & 0xFF) / 255, opacity: 1)
    }
}
