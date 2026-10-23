import SwiftUI
import UIKit

/// A six-digit code input drawn as soft rounded cells. The focused cell's outline collapses into a
/// glowing, breathing caret; typed digits rise softly into their cells. A wrong code
/// shakes the row on a damped spring and flushes it red before the digits drop out; the right one
/// slides the cells together into a single pill that closes into a circle, draws a check and
/// throws a ring of sparks. A hidden text field takes real keyboard and one-time-code input.
struct MorphOtpInput: View {
    /// The code that unlocks it.
    var code = "481930"
    /// Where the code was sent, shown under the title.
    var destination = "nora@larkpost.com"
    /// Types a wrong code, shakes, types the right one, merges into a check, then starts over.
    var autoPlay = false
    /// Called once the right code is in.
    var onVerified: (() -> Void)? = nil

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine: MorphOtpInputEngine?

    private typealias M = MorphOtpInputMetrics

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(
                width: proxy.size.width + insets.leading + insets.trailing,
                height: proxy.size.height + insets.top + insets.bottom
            )
            Group {
                if let engine, engine.source == code {
                    stage(engine: engine, size: size, top: insets.top, bottom: insets.bottom)
                } else {
                    M.background.color
                }
            }
            .frame(width: size.width, height: size.height)
            .offset(x: -insets.leading, y: -insets.top)
        }
        // The keyboard rises over the block; it does not push it up.
        .ignoresSafeArea(.keyboard)
        .onAppear { if engine?.source != code { engine = MorphOtpInputEngine(code: code) } }
        .onChange(of: code) { engine = MorphOtpInputEngine(code: code) }
    }

    private func stage(engine: MorphOtpInputEngine, size: CGSize, top: Double, bottom: Double) -> some View {
        let cellW = max(24, min(M.cellMaxW, (size.width - 56 - M.cellGap * Double(M.length - 1)) / Double(M.length)))
        let rowW = cellW * Double(M.length) + M.cellGap * Double(M.length - 1)
        let rowTop = top + 262
        return ZStack(alignment: .topLeading) {
            M.background.color

            TimelineView(.animation) { timeline in
                let _ = engine.configure(reduced: reduceMotion, onVerified: onVerified)
                let _ = engine.step(to: timeline.date, autoPlay: autoPlay)
                MorphOtpInputScene(
                    engine: engine,
                    time: engine.now,
                    size: size,
                    top: top,
                    bottom: bottom,
                    cellW: cellW,
                    rowW: rowW,
                    rowTop: rowTop,
                    destination: destination,
                    displayScale: displayScale,
                    reduced: reduceMotion
                )
            }
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .clipped()
    }
}

/// The page: the heading, the row of cells, the status line and the foot.
private struct MorphOtpInputScene: View {
    let engine: MorphOtpInputEngine
    /// The engine's clock: a new value each frame, so SwiftUI draws the scene again.
    let time: Double
    let size: CGSize
    let top: Double
    let bottom: Double
    let cellW: Double
    let rowW: Double
    let rowTop: Double
    let destination: String
    let displayScale: Double
    let reduced: Bool

    private typealias M = MorphOtpInputMetrics
    private static let paper = Color(hex: 0xf4f5f7)
    private static let status = Font.custom("AvenirNext-Medium", fixedSize: 15)

    var body: some View {
        ZStack(alignment: .topLeading) {
            head(success: engine.successShown.value)
                .frame(width: size.width - 56)
                .offset(x: 28, y: top + 48)

            Canvas { context, _ in
                MorphOtpInputRow(engine: engine, cellW: cellW, displayScale: displayScale, reduced: reduced)
                    .draw(in: context, cx: size.width / 2 + engine.shake, cy: rowTop + M.cellH / 2)
            }
            .frame(width: size.width, height: size.height)
            .allowsHitTesting(false)
            .accessibilityHidden(true)

            MorphOtpInputField(engine: engine, text: engine.value, request: engine.focusRequest.id, focused: engine.focusRequest.focused)
                .frame(width: 1, height: 1)
                .opacity(0.02)
                .offset(x: -10, y: -10)
                .accessibilityHidden(true)

            Color.clear
                .frame(width: rowW + 16, height: M.cellH + 24)
                .contentShape(Rectangle())
                .morphOtpInputTap { engine.tapRow() }
                .offset(x: (size.width - rowW) / 2 - 8, y: rowTop - 12)
                .accessibilityElement()
                .accessibilityAddTraits(.isButton)
                .accessibilityLabel(engine.phase == .success ? "Code verified" : "Verification code, \(engine.value.count) of \(M.length) digits entered")
                .accessibilityHint("Opens the keyboard")

            statusLine
                .frame(width: size.width - 48, height: 24, alignment: .top)
                // Clear of the burst, which stays within burstMax of the row's centre.
                .offset(x: 24, y: rowTop + M.cellH / 2 + M.burstMax + 10)

            Text("Codes expire after 10 minutes")
                .font(.custom("AvenirNext-Regular", fixedSize: 13))
                .foregroundStyle(Self.paper.opacity(0.32))
                .frame(width: size.width, height: size.height - bottom - 24, alignment: .bottom)
                .allowsHitTesting(false)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }

    private func head(success: Double) -> some View {
        VStack(spacing: 14) {
            Image(systemName: "envelope.open.fill")
                .font(.system(size: 20))
                .foregroundStyle(M.accent.color)
                .frame(width: 56, height: 56)
                .background(Color(red: 124 / 255, green: 242 / 255, blue: 197 / 255).opacity(0.1), in: Circle())
                .padding(.bottom, 6)
                .accessibilityHidden(true)
            ZStack {
                title("Check your inbox")
                    .opacity(1 - success)
                    .accessibilityHidden(engine.phase == .success)
                title("You're in")
                    .opacity(success)
                    .accessibilityHidden(engine.phase != .success)
            }
            (Text("Enter the 6-digit code we sent to\n")
                + Text(destination).font(.custom("AvenirNext-DemiBold", fixedSize: 16)).foregroundColor(Self.paper))
                .font(.custom("AvenirNext-Regular", fixedSize: 16))
                .foregroundStyle(Self.paper.opacity(0.55))
                .lineSpacing(23 - (UIFont(name: "AvenirNext-Regular", size: 16)?.lineHeight ?? 21.9))
                .multilineTextAlignment(.center)
        }
    }

    private func title(_ text: String) -> some View {
        Text(text)
            .font(.custom("AvenirNext-Bold", fixedSize: 30))
            .kerning(-0.6)
            .foregroundStyle(Self.paper)
            .multilineTextAlignment(.center)
    }

    private var statusLine: some View {
        ZStack(alignment: .top) {
            resendLine
                .opacity(engine.idleShown.value)
                .allowsHitTesting(engine.phase == .idle)
                .accessibilityHidden(engine.phase != .idle)
            Text("That code didn't match. Try again.")
                .font(Self.status)
                .foregroundStyle(M.error.color)
                .opacity(engine.errorShown.value)
                .allowsHitTesting(false)
                .accessibilityHidden(engine.phase != .error)
            Text("Verified. Welcome back, Nora.")
                .font(Self.status)
                .foregroundStyle(M.accent.color)
                .opacity(engine.successShown.value)
                .allowsHitTesting(false)
                .accessibilityHidden(engine.phase != .success)
        }
        .frame(maxWidth: .infinity)
    }

    /// "Resend code in 0:24", counting down once a second, then a tappable "Resend code".
    @ViewBuilder private var resendLine: some View {
        let left = max(0, M.resendSeconds - floor(max(0, engine.now - engine.resendFrom)))
        if left > 0 {
            (Text("Resend code in ")
                + Text(String(format: "0:%02d", Int(left))).foregroundColor(Self.paper).monospacedDigit())
                .font(Self.status)
                .foregroundStyle(Self.paper.opacity(0.55))
        } else {
            Text("Resend code")
                .font(Self.status)
                .foregroundStyle(M.accent.color)
                .padding(12)
                .contentShape(Rectangle())
                .morphOtpInputTap { engine.resend() }
                .padding(-12)
                .accessibilityAddTraits(.isButton)
        }
    }
}

extension View {
    /// A tap read from a touch that barely moves. The scene is rebuilt every frame, which a plain
    /// tap gesture does not survive; a drag recogniser does.
    fileprivate func morphOtpInputTap(_ action: @escaping () -> Void) -> some View {
        gesture(
            DragGesture(minimumDistance: 0)
                .onEnded { value in
                    if hypot(value.translation.width, value.translation.height) < 10 { action() }
                }
        )
        .accessibilityAction { action() }
    }
}

// MARK: - Drawing

/// The cells, their digits and the success mark, drawn about the row's centre.
private struct MorphOtpInputRow {
    let engine: MorphOtpInputEngine
    let cellW: Double
    let displayScale: Double
    let reduced: Bool

    private typealias M = MorphOtpInputMetrics

    /// One cell's box at merge `m`: 0 apart, 1 slid together into a pill, 2 closed into a circle.
    /// Returns its centre x from the row's centre, its width and its corner radius.
    private func box(_ index: Int, _ m: Double) -> (x: Double, w: Double, r: Double) {
        let p1 = morphOtpInputClamp01(m)
        let p2 = morphOtpInputClamp01(m - 1)
        func mix(_ a: Double, _ b: Double, _ t: Double) -> Double { a + (b - a) * t }
        let spacing = mix(mix(cellW + M.cellGap, cellW * M.mergedSpacing, p1), 0, p2)
        // Merged cells are stadiums wide enough to overlap their neighbours, so the union is one pill.
        let width = mix(mix(cellW, cellW * M.mergedSpacing + M.cellH, p1), M.cellH, p2)
        let radius = mix(M.cellRadius, M.cellH / 2, p1)
        return ((Double(index) - Double(M.length - 1) / 2) * spacing, width, radius)
    }

    func draw(in context: GraphicsContext, cx: Double, cy: Double) {
        var row = context
        row.translateBy(x: cx, y: cy)
        let m = engine.merge.value
        let boxM = reduced ? 0 : m
        // Under reduced motion the cells stay put and fade; the circle fades in on its own.
        let visible = reduced ? 1 - morphOtpInputClamp01(m) : 1
        let error = engine.errorAmt.value

        for (i, cell) in engine.cells.enumerated() {
            let b = box(i, boxM)
            var c = row
            c.opacity = visible
            let rect = CGRect(x: b.x - b.w / 2, y: -M.cellH / 2, width: b.w, height: M.cellH)
            let shape = Path(roundedRect: rect, cornerRadius: min(b.r, b.w / 2, M.cellH / 2), style: .circular)
            let base = M.cellFill.mix(M.cellFilled, cell.fill.value)
            let hurt = base.mix(M.cellError, error)
            let fill = reduced ? hurt : hurt.mix(M.accent, morphOtpInputClamp01(m - 1))
            c.fill(shape, with: .color(fill.color))

            // The hairline border, half a stroke inside the cell; mint while the cell has focus.
            let rest = cell.fill.value > 0.5 ? MorphOtpInputColor(0xffffff, alpha: 0.20) : MorphOtpInputColor(0xffffff, alpha: 0.09)
            let edge = rest.mix(M.activeBorder, cell.lit.value).mix(M.error, error)
            let inner = rect.insetBy(dx: M.border / 2, dy: M.border / 2)
            let innerShape = Path(
                roundedRect: inner,
                cornerRadius: max(0, min(b.r - M.border / 2, inner.width / 2, inner.height / 2)),
                style: .circular
            )
            var border = c
            border.opacity = visible * (1 - morphOtpInputClamp01(m * 3))
            border.stroke(innerShape, with: .color(edge.color), lineWidth: M.border)

            // The mint outline collapsing into the caret: the box narrows to a sliver and shortens
            // while its stroke thickens a little; once it lets go it shrinks and fades where it stands.
            let f = cell.focus.value
            let fc = morphOtpInputClamp01(f)
            let caretW = max(0.01, (cellW - M.border) + (0.01 - (cellW - M.border)) * f)
            let caretH = ((M.cellH - M.border) + (M.caretH - (M.cellH - M.border)) * f) * (1 + (0.55 - 1) * cell.leave.value)
            let caretR = (M.cellRadius - M.border / 2) + (M.caretW / 2 - (M.cellRadius - M.border / 2)) * f
            let stroke = 1 + (M.caretW - 1) * f
            let breath = 0.55 + 0.45 * cos(2 * Double.pi * engine.now / M.caretBreath)
            // Breathing only starts once the caret has formed, and nothing shows while the row is merged.
            let settled = morphOtpInputClamp01((fc - 0.75) / 0.25)
            // The collapsing outline starts faint and only reaches full strength as it becomes the caret.
            let caretOpacity = cell.lit.value * (0.3 + 0.7 * fc) * (1 - cell.leave.value) * (1 + (breath - 1) * settled)
                * (1 - morphOtpInputClamp01(m * 2))
            guard caretOpacity > 0.002 else { continue }
            // The glow belongs to the formed caret only: a soft bar behind it, never a blurred outline.
            let glowOpacity = caretOpacity * fc * fc * fc * 0.7
            if glowOpacity > 0.002 {
                var glow = c
                glow.opacity = visible * glowOpacity
                glow.drawLayer { layer in
                    layer.addFilter(.blur(radius: M.caretGlow))
                    layer.fill(
                        Path(
                            roundedRect: CGRect(x: b.x - M.caretW / 2, y: -caretH / 2, width: M.caretW, height: caretH),
                            cornerRadius: M.caretW / 2
                        ),
                        with: .color(M.accent.color)
                    )
                }
            }
            let caretRect = CGRect(x: b.x - caretW / 2, y: -caretH / 2, width: caretW, height: caretH)
            let caret = Path(roundedRect: caretRect, cornerRadius: max(0, min(caretR, caretW / 2, caretH / 2)), style: .circular)
            var line = c
            line.opacity = visible * caretOpacity
            line.stroke(caret, with: .color(M.accent.color), lineWidth: stroke)
        }

        // Digits are drawn after every box so merged boxes never cover them, each clipped to its
        // own cell: typed, it rises softly into place; cleared, it sinks and fades.
        for (i, cell) in engine.cells.enumerated() {
            let k = cell.roll.value
            let shownAmount = k < 1 ? morphOtpInputClamp01(k * 1.4) : morphOtpInputClamp01((2 - k) * 1.5)
            let opacity = shownAmount * (1 - morphOtpInputClamp01((m - 1) * 3)) * visible
            guard opacity > 0.002 else { continue }
            let b = box(i, boxM)
            let y = reduced ? 0 : (k < 1 ? (1 - k) * M.rollFrom : (k - 1) * M.rollOut)
            let scale = reduced ? 1 : 1 + (0.6 - 1) * morphOtpInputClamp01(m - 1)
            // The blur is set in device pixels, so it reads the same at any scale.
            let blur = reduced ? 0 : min(M.rollBlur, abs(1 - k) * M.rollBlur * 1.5) / displayScale
            var c = row
            let clipRect = CGRect(x: b.x - b.w / 2 + 1, y: -M.cellH / 2 + 1, width: max(0, b.w - 2), height: M.cellH - 2)
            c.clip(to: Path(roundedRect: clipRect, cornerRadius: max(0, min(b.r - 1, clipRect.width / 2, clipRect.height / 2)), style: .circular))
            c.translateBy(x: b.x, y: y)
            c.scaleBy(x: scale, y: scale)
            c.opacity = opacity
            let ink = M.digitInk.mix(M.error, error).color
            let text = c.resolve(Text(cell.glyph).font(.custom("AvenirNext-DemiBold", fixedSize: M.digitSize)).foregroundStyle(ink))
            let measured = text.measure(in: CGSize(width: 1000, height: 1000))
            let ascent = text.firstBaseline(in: measured)
            let origin = CGPoint(x: -measured.width / 2, y: M.digitSize * 0.36 - ascent)
            if blur > 0.05 {
                c.drawLayer { layer in
                    layer.addFilter(.blur(radius: blur))
                    layer.draw(text, at: origin, anchor: .topLeading)
                }
            } else {
                c.draw(text, at: origin, anchor: .topLeading)
            }
        }

        drawSuccess(row)
    }

    /// The check drawn into the merged circle, the rings it throws and a scatter of spark dashes.
    private func drawSuccess(_ context: GraphicsContext) {
        let radius = M.cellH / 2
        let m = engine.merge.value
        let check = engine.check.value
        let burst = engine.burst.value
        var popped = context
        if !reduced {
            let pop = 1 + sin(Double.pi * check) * 0.07
            popped.scaleBy(x: pop, y: pop)
        }
        if reduced {
            // Under reduced motion the cells only fade, so the circle fades in here instead.
            var circle = popped
            circle.opacity = morphOtpInputClamp01(m - 1)
            circle.fill(Path(ellipseIn: CGRect(x: -radius, y: -radius, width: radius * 2, height: radius * 2)), with: .color(M.accent.color))
        }
        if check > 0 {
            var mark = Path()
            mark.move(to: CGPoint(x: -11, y: 1))
            mark.addLine(to: CGPoint(x: -3.5, y: 8.5))
            mark.addLine(to: CGPoint(x: 12, y: -8))
            popped.stroke(
                mark.trimmedPath(from: 0, to: min(1, check)),
                with: .color(M.ink.color),
                style: StrokeStyle(lineWidth: 4.6, lineCap: .round, lineJoin: .round)
            )
        }
        guard burst > 0 && burst < 1 else { return }
        let c = context
        func ring(_ r: Double, width: Double, opacity: Double) {
            guard opacity > 0.002 else { return }
            var layer = c
            layer.opacity = opacity
            layer.stroke(Path(ellipseIn: CGRect(x: -r, y: -r, width: r * 2, height: r * 2)), with: .color(M.accent.color), lineWidth: width)
        }
        // The trailing ring starts ringLag later and stops short of the first.
        if burst > M.ringLag {
            let trail = morphOtpInputClamp01((burst - M.ringLag) / (1 - M.ringLag))
            ring(radius + 1 + (radius * (M.ringReach - 0.32) - radius - 1) * M.easeOut.value(at: trail), width: 1.25, opacity: 0.45 * morphOtpInputFadeAfter(trail, 0.4))
        }
        // The leading ring: crisp all the way (never under 1.25 pt), at full strength until 55% of
        // the burst, then gone by its end.
        let eased = M.easeOut.value(at: burst)
        ring(radius + 1 + (radius * M.ringReach - radius - 1) * eased, width: 3 + (1.25 - 3) * burst, opacity: morphOtpInputFadeAfter(burst, 0.55))
        // Short dashes shooting straight out, shortening as they go.
        let sparkOpacity = morphOtpInputFadeAfter(burst, 0.45)
        guard sparkOpacity > 0.002 else { return }
        for i in 0..<M.sparks {
            let angle = Double(i) / Double(M.sparks) * Double.pi * 2 + Double.pi / Double(M.sparks)
            let long = i % 2 == 0
            let reach = long ? M.burstMax - 3 : M.burstMax - 12
            let d = radius + 6 + (reach - radius - 6) * eased
            let half = (long ? 6 : 4.5) + (1 - (long ? 6 : 4.5)) * eased
            var dash = Path()
            dash.move(to: CGPoint(x: cos(angle) * (d - half), y: sin(angle) * (d - half)))
            dash.addLine(to: CGPoint(x: cos(angle) * (d + half), y: sin(angle) * (d + half)))
            var layer = c
            layer.opacity = sparkOpacity
            layer.stroke(dash, with: .color(M.accent.color), style: StrokeStyle(lineWidth: long ? 2.4 : 2, lineCap: .round))
        }
    }
}

// MARK: - Hidden field

/// A text field the size of a pixel that takes real typing, pastes and one-time codes from Messages.
private struct MorphOtpInputField: UIViewRepresentable {
    let engine: MorphOtpInputEngine
    // Passed in so SwiftUI updates the field when they change.
    let text: String
    let request: Int
    let focused: Bool

    func makeCoordinator() -> Coordinator { Coordinator(engine: engine) }

    func makeUIView(context: Context) -> UITextField {
        let field = UITextField()
        field.keyboardType = .numberPad
        field.textContentType = .oneTimeCode
        field.tintColor = .clear
        field.textColor = .clear
        field.delegate = context.coordinator
        return field
    }

    func updateUIView(_ field: UITextField, context: Context) {
        context.coordinator.engine = engine
        if field.text != text { field.text = text }
        if request != context.coordinator.handled {
            context.coordinator.handled = request
            // Asked for during a view update; done just after it.
            let focus = focused
            DispatchQueue.main.async {
                if focus { field.becomeFirstResponder() } else { field.resignFirstResponder() }
            }
        }
    }

    final class Coordinator: NSObject, UITextFieldDelegate {
        var engine: MorphOtpInputEngine
        var handled = 0

        init(engine: MorphOtpInputEngine) {
            self.engine = engine
        }

        func textField(_ field: UITextField, shouldChangeCharactersIn range: NSRange, replacementString string: String) -> Bool {
            let current = field.text ?? ""
            guard let swapped = Range(range, in: current) else { return false }
            let proposed = current.replacingCharacters(in: swapped, with: string)
            // The field only ever shows what the code holds, like a controlled input.
            field.text = engine.type(String(proposed.prefix(MorphOtpInputMetrics.length)))
            return false
        }

        func textFieldDidBeginEditing(_ field: UITextField) { engine.fieldFocused = true }

        func textFieldDidEndEditing(_ field: UITextField) { engine.fieldFocused = false }
    }
}
