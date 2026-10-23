import SwiftUI

/// Glass "Select" pill that whites out and pulls apart, metaball style, into ○, △ and a "Done" pill.
/// Tapping "Done" merges them back.
struct LiquidSplitButton: View {
    enum Option { case circle, triangle }

    var label = "Select"
    var doneLabel = "Done"
    /// Controlled state. Leave `nil` to let the button keep its own.
    var isExpanded: Binding<Bool>? = nil
    /// Split and merge by itself with a pause in each state. Tapping still works.
    var autoPlay = false
    /// One of the two round options was tapped while split.
    var onOption: (Option) -> Void = { _ in }

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var inner = false
    @State private var pressed = false
    /// 0 = one "Select" pill, 1 = split into three.
    @State private var progress = Tween(0)
    @State private var settling = false
    @State private var settleToken = 0

    private var open: Bool { isExpanded?.wrappedValue ?? inner }

    var body: some View {
        ZStack {
            TimelineView(.animation(paused: !settling)) { timeline in
                let s = progress.value(at: timeline.date)
                ZStack {
                    Canvas { context, size in
                        draw(context, size: size, s: s)
                    }
                    .padding(-Metrics.bleed)
                    Text(label)
                        .opacity(1 - seg(s, 0.02, 0.16))
                        .accessibilityHidden(true)
                    Text(doneLabel)
                        .frame(width: Metrics.doneW)
                        .frame(maxWidth: .infinity, alignment: .trailing)
                        .opacity(seg(s, 0.5, 0.75))
                        .accessibilityHidden(true)
                }
                .font(.system(size: 17, weight: .medium))
                .lineLimit(1)
                .foregroundStyle(ink)
                .allowsHitTesting(false)
            }

            if open {
                HStack(spacing: Metrics.gap) {
                    hit("Circle", width: Metrics.circle) { onOption(.circle) }
                    hit("Triangle", width: Metrics.circle) { onOption(.triangle) }
                    hit(doneLabel, width: Metrics.doneW) { setOpen(false) }
                }
            } else {
                hit(label, width: Metrics.pillW) { setOpen(true) }
            }
        }
        .frame(width: Metrics.width, height: Metrics.height)
        .scaleEffect(pressed ? 0.97 : 1)
        .animation(.timingCurve(0.23, 1, 0.32, 1, duration: 0.13), value: pressed)
        .sensoryFeedback(.impact(weight: .light), trigger: pressed) { _, pressed in pressed }
        .onChange(of: open, initial: true) { _, open in
            progress.retarget(open ? 1 : 0, duration: reduceMotion ? 0.22 : Metrics.splitSeconds, curve: .linear)
            settling = true
            settleToken += 1
        }
        .task(id: settleToken) {
            // Keep the timeline running until the split has finished, then let it pause.
            try? await Task.sleep(for: .seconds(progress.duration + 0.05))
            if !Task.isCancelled { settling = false }
        }
        // Re-armed after every change, so a tap simply restarts the pause.
        .task(id: AutoKey(open: open, active: autoPlay && !reduceMotion && !pressed)) {
            guard autoPlay, !reduceMotion, !pressed else { return }
            let pause = open ? Metrics.autoOpenSeconds : Metrics.autoClosedSeconds
            try? await Task.sleep(for: .seconds(Metrics.splitSeconds + pause))
            if !Task.isCancelled { setOpen(!open) }
        }
    }

    private struct AutoKey: Equatable {
        let open: Bool
        let active: Bool
    }

    private func setOpen(_ next: Bool) {
        // The touch target that was just tapped is about to be swapped out, and may never report its release.
        pressed = false
        if let isExpanded { isExpanded.wrappedValue = next } else { inner = next }
    }

    private func hit(_ name: String, width: Double, action: @escaping () -> Void) -> some View {
        Button(action: action) { Color.clear }
            .buttonStyle(PressReporter(pressed: $pressed))
            .frame(width: width, height: Metrics.height)
            .accessibilityLabel(name)
    }

    // MARK: Drawing

    private func draw(_ context: GraphicsContext, size: CGSize, s: Double) {
        let bounds = CGRect(origin: .zero, size: size)
        if reduceMotion {
            // Reduced motion: a plain cross-fade between the pill and the split trio.
            drawStill(context, split: false, opacity: 1 - s)
            drawStill(context, split: true, opacity: s)
        } else {
            drawGoo(context, bounds: bounds, s: s)
        }

        var icons = context
        icons.opacity = seg(s, 0.5, 0.75)
        let cy = Metrics.bleed + Metrics.height / 2
        let circle = CGPoint(x: Metrics.bleed + pieceCx(0, s), y: cy)
        icons.stroke(
            Path(ellipseIn: CGRect(x: circle.x - 8.5, y: circle.y - 8.5, width: 17, height: 17)),
            with: .color(ink), lineWidth: 2
        )
        let triangle = CGPoint(x: Metrics.bleed + pieceCx(1, s), y: cy)
        icons.stroke(
            Path { path in
                path.move(to: CGPoint(x: triangle.x, y: triangle.y - 8.6))
                path.addLine(to: CGPoint(x: triangle.x + 9.2, y: triangle.y + 6.8))
                path.addLine(to: CGPoint(x: triangle.x - 9.2, y: triangle.y + 6.8))
                path.closeSubpath()
            },
            with: .color(ink), style: StrokeStyle(lineWidth: 2, lineJoin: .round)
        )
    }

    private func drawGoo(_ context: GraphicsContext, bounds: CGRect, s: Double) {
        let outer = blob(s, inset: 0, dy: 0)
        let glow = flash(s)

        var shadow = context
        shadow.opacity = 0.3
        shadow.addFilter(.blur(radius: Metrics.gooBlur))
        shadow.fill(blob(s, inset: 0, dy: 7), with: .color(shadowColor))

        if glow > 0 {
            var halo = context
            halo.opacity = glow * 0.7
            halo.addFilter(.blur(radius: 12))
            halo.fill(outer, with: .color(.white))
        }

        gooFill(context, outer, with: rimShading, bounds: bounds)
        gooFill(context, blob(s, inset: Metrics.rim, dy: 0), with: bodyShading, bounds: bounds)
        if glow > 0 {
            var white = context
            white.opacity = glow
            gooFill(white, outer, with: .color(.white), bounds: bounds)
        }
    }

    /// Gooey look: blur the pieces, then turn the soft alpha back into a hard edge and use it as a
    /// mask for the shading. Pieces that are close enough share blur, so they stay bridged until
    /// they have really moved apart.
    private func gooFill(_ context: GraphicsContext, _ path: Path, with shading: GraphicsContext.Shading, bounds: CGRect) {
        context.drawLayer { layer in
            layer.clipToLayer { mask in
                mask.addFilter(.alphaThreshold(min: 0.5, color: .white))
                mask.addFilter(.blur(radius: Metrics.gooBlur))
                mask.drawLayer { pieces in
                    pieces.fill(path, with: .color(.white))
                }
            }
            layer.fill(Path(bounds), with: shading)
        }
    }

    private func drawStill(_ context: GraphicsContext, split: Bool, opacity: Double) {
        guard opacity > 0 else { return }
        var faded = context
        faded.opacity = opacity
        faded.drawLayer { layer in
            var shadow = layer
            shadow.opacity = 0.3
            shadow.addFilter(.blur(radius: Metrics.gooBlur))
            shadow.fill(still(split: split, inset: 0, dy: 7), with: .color(shadowColor))
            layer.fill(still(split: split, inset: 0, dy: 0), with: rimShading)
            layer.fill(still(split: split, inset: Metrics.rim, dy: 0), with: bodyShading)
        }
    }

    private var rimShading: GraphicsContext.Shading {
        .linearGradient(
            Gradient(stops: [
                .init(color: Color(hex: 0xf1f2f5), location: 0),
                .init(color: Color(hex: 0xcfd1d6), location: 0.55),
                .init(color: Color(hex: 0xf6f7fa), location: 1),
            ]),
            startPoint: CGPoint(x: 0, y: Metrics.bleed), endPoint: CGPoint(x: 0, y: Metrics.bleed + Metrics.height)
        )
    }

    private var bodyShading: GraphicsContext.Shading {
        .linearGradient(
            Gradient(stops: [
                .init(color: Color(hex: 0xcbcdd2), location: 0),
                .init(color: Color(hex: 0xb9bcc2), location: 0.55),
                .init(color: Color(hex: 0xd5d7dc), location: 1),
            ]),
            startPoint: CGPoint(x: 0, y: Metrics.bleed), endPoint: CGPoint(x: 0, y: Metrics.bleed + Metrics.height)
        )
    }
}

private enum Metrics {
    static let height = 52.0
    static let pillW = 114.0
    static let circle = 52.0
    static let gap = 12.0
    static let doneW = 104.0
    static let width = circle + gap + circle + gap + doneW
    /// Centres and widths of the three pieces once split: ○, △ and the "Done" pill.
    static let pieceCx = [circle / 2, circle + gap + circle / 2, width - doneW / 2]
    static let pieceW = [circle, circle, doneW]
    /// The canvas overflows the button by this much on every side to fit the blur the goo is made from.
    static let bleed = 40.0
    static let splitSeconds = 1.15
    static let autoOpenSeconds = 2.0
    static let autoClosedSeconds = 1.4
    static let gooBlur = 9.0
    static let rim = 1.1
    /// Mid-transition the glass whites out and swells by this much.
    static let inflate = 4.0
}

private let ink = Color(hex: 0x0b0b0d)
private let shadowColor = Color(hex: 0x1e222c)

private func seg(_ s: Double, _ from: Double, _ to: Double) -> Double {
    min(1, max(0, (s - from) / (to - from)))
}
private func smooth(_ u: Double) -> Double { u * u * (3 - 2 * u) }
/// How far the pieces have moved from the single pill (0) to their own places (1).
private func spread(_ s: Double) -> Double {
    let u = seg(s, 0.12, 1)
    return 1 - (1 - u) * (1 - u) * (1 - u)
}
private func flash(_ s: Double) -> Double {
    smooth(seg(s, 0.03, 0.2)) * (1 - smooth(seg(s, 0.45, 0.65)))
}
private func pieceCx(_ index: Int, _ s: Double) -> Double {
    Metrics.width / 2 + (Metrics.pieceCx[index] - Metrics.width / 2) * spread(s)
}

/// The three pieces as one path. `inset` shrinks them (rim), `dy` drops them (shadow).
private func blob(_ s: Double, inset: Double, dy: Double) -> Path {
    let m = spread(s)
    let grow = Metrics.inflate * flash(s) - inset
    let h = Metrics.height + grow * 2
    var path = Path()
    for i in 0..<3 {
        let w = Metrics.pillW + (Metrics.pieceW[i] - Metrics.pillW) * m + grow * 2
        let x = Metrics.bleed + pieceCx(i, s) - w / 2
        path.addPath(capsule(CGRect(x: x, y: Metrics.bleed + dy - grow, width: w, height: h)))
    }
    return path
}

/// Still pieces for the reduced-motion cross-fade: the pill alone, or the split trio.
private func still(split: Bool, inset: Double, dy: Double) -> Path {
    let h = Metrics.height - inset * 2
    var path = Path()
    func add(_ cx: Double, _ width: Double) {
        let w = width - inset * 2
        path.addPath(capsule(CGRect(x: Metrics.bleed + cx - w / 2, y: Metrics.bleed + dy + inset, width: w, height: h)))
    }
    if split {
        for i in 0..<3 { add(Metrics.pieceCx[i], Metrics.pieceW[i]) }
    } else {
        add(Metrics.width / 2, Metrics.pillW)
    }
    return path
}

/// Invisible button face that only reports its pressed state, so the shared glass can react.
private struct PressReporter: ButtonStyle {
    @Binding var pressed: Bool

    func makeBody(configuration: Configuration) -> some View {
        configuration.label
            .contentShape(Capsule())
            .onChange(of: configuration.isPressed) { _, isPressed in pressed = isPressed }
    }
}

#Preview {
    ZStack {
        Color(hex: 0xacb0b6).ignoresSafeArea()
        LiquidSplitButton(autoPlay: true)
    }
}
