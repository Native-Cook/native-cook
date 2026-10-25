import SwiftUI
import UIKit

/// "Atelier", a photo editor's oil mode: the finger drags the photo like wet paint.
///
/// A warm dark studio screen, a still life photo on a rounded card with two shadows. One Metal
/// layer effect (PaintSmearPhoto.metal) displaces the photo by a drag field made of up to 24 trail
/// points, pulls colour along the stroke, scores bristle lines across it and lights the ridges of
/// the moved paint from the upper left. Under the card: brush size chips (S/M/L), a Wet–Dry slider
/// (drier paint = harsher bristles, shorter drag, less sheen) and Reset.
///
/// autoPlay (loop 9 s, PaintSmearPhotoMotion.swift): 0–1.4 rest (the clean photo), three curved
/// strokes 1.4–2.4, 2.6–3.6 and 3.8–4.8, 4.8–6.2 hold, 6.2–8.2 the paint flows back, 8.2–9 rest.
///
/// Touch: drag on the photo to smear it (a point every 16 pt). Double-tap or Reset: the paint flows
/// back (0.85 s). 2 s after the last touch it flows back the same way and the loop starts again.
/// Reduced motion: no loop, the clean photo; smearing still works.
struct PaintSmearPhoto: View {
    /// Plays the 9 s paint-and-flow-back loop until touched.
    var autoPlay = false

    static let loop = 9.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = PaintSmearPhotoEngine()
    @State private var brush = 1
    @State private var dry = 0.35
    @State private var spin = 0.0
    /// Keeps the timeline running after a touch while autoPlay is off.
    @State private var awake = false

    private typealias M = PaintSmearPhotoMetrics

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let W = size.width
        let H = size.height
        let cardW = max(0, W - 32)
        let cardTop = insets.top + 68
        // The tools sit at the bottom above the hint; the photo fills everything between.
        let hintBottom = insets.bottom + 12
        let toolsBottom = hintBottom + 18 + 16
        let cardH = max(0, (H - cardTop - toolsBottom - M.toolsH - 20).rounded())

        return ZStack(alignment: .topLeading) {
            LinearGradient(
                stops: [
                    .init(color: Color(hex: 0x231d18), location: 0),
                    .init(color: Color(hex: 0x1a1613), location: 0.5),
                    .init(color: Color(hex: 0x120f0d), location: 1),
                ],
                startPoint: .top,
                endPoint: .bottom
            )
            .frame(width: W, height: H)

            bar
                .padding(.horizontal, 16)
                .frame(width: W, height: 40)
                .offset(y: insets.top + 12)

            if cardH > 0 {
                photoCard(cardW: cardW, cardH: cardH)
                    .offset(x: 16, y: cardTop)

                tools
                    .padding(.horizontal, 16)
                    .frame(width: W, height: H - toolsBottom, alignment: .bottom)
            }

            Text("Drag to smear · double-tap to reset")
                .font(.system(size: 13, weight: .medium))
                .foregroundStyle(M.faint)
                .multilineTextAlignment(.center)
                .padding(.horizontal, 24)
                .frame(width: W, height: H - hintBottom, alignment: .bottom)
                .allowsHitTesting(false)
        }
        .frame(width: W, height: H, alignment: .topLeading)
        .background(Color(hex: 0x1a1613))
        .clipped()
    }

    // MARK: Photo

    private func photoCard(cardW: Double, cardH: Double) -> some View {
        let auto = autoPlay && !reduceMotion
        let paused = !auto && !awake
        return TimelineView(.animation(paused: paused)) { timeline in
            let f = engine.step(to: timeline.date, w: cardW, h: cardH, auto: auto)
            let _ = sleepWhenIdle(auto: auto)
            ZStack {
                RoundedRectangle(cornerRadius: 22, style: .continuous)
                    .fill(Color(hex: 0x0f0d0c))
                    .shadow(color: .black.opacity(0.4), radius: 1.5, y: 2)
                    .shadow(color: .black.opacity(0.45), radius: 22, y: 22)
                if let photo = M.photo {
                    Image(uiImage: photo)
                        .resizable()
                        .scaledToFill()
                        .frame(width: cardW, height: cardH)
                        .clipped()
                        .layerEffect(
                            ShaderLibrary.paintSmearPhoto(
                                .float2(max(cardW, 1), max(cardH, 1)),
                                .floatArray(f.trail),
                                .float(f.sigma),
                                .float(dry),
                                .float(f.flow),
                                .float(22)
                            ),
                            maxSampleOffset: .zero
                        )
                }
            }
            .frame(width: cardW, height: cardH)
            .contentShape(RoundedRectangle(cornerRadius: 22, style: .continuous))
            .gesture(
                DragGesture(minimumDistance: 0)
                    .onChanged { value in
                        awake = true
                        engine.dragChanged(start: value.startLocation, location: value.location)
                    }
                    .onEnded { _ in engine.dragEnded() }
            )
            .simultaneousGesture(
                TapGesture(count: 2).onEnded {
                    awake = true
                    reset()
                }
            )
            .accessibilityElement()
            .accessibilityLabel("Still life photo. Drag to smear it like wet oil paint; double-tap to reset.")
            .accessibilityAction(named: "Reset") { reset() }
        }
    }

    /// Lets the timeline pause once the paint is still with autoPlay off.
    private func sleepWhenIdle(auto: Bool) -> Bool {
        if awake && !auto && engine.isIdle {
            Task { @MainActor in if engine.isIdle { awake = false } }
        }
        return true
    }

    private func reset() {
        engine.reset()
        M.light.impactOccurred()
        withAnimation(.timingCurve(0.77, 0, 0.175, 1, duration: 0.7)) { spin -= 360 }
    }

    // MARK: Chrome

    private var bar: some View {
        HStack {
            HStack(spacing: 12) {
                Text("Atelier")
                    .font(.system(size: 28, weight: .bold))
                    .kerning(-0.4)
                    .foregroundStyle(M.ink)
                Text("Oil mode")
                    .font(.system(size: 12, weight: .bold))
                    .kerning(0.4)
                    .foregroundStyle(M.accent)
                    .padding(.horizontal, 10)
                    .frame(height: 26)
                    .background(Capsule().fill(Color.rgba(233, 162, 75, 0.16)))
            }
            Spacer()
            Button {} label: {
                Text("Export")
                    .font(.system(size: 15, weight: .semibold))
                    .foregroundStyle(M.dark)
                    .padding(.horizontal, 16)
                    .frame(height: 36)
                    .background(
                        Capsule()
                            .fill(M.paper)
                            .overlay(M.innerHighlight(0.7, shape: Capsule()))
                            .shadow(color: .black.opacity(0.35), radius: 1, y: 1)
                    )
            }
            .buttonStyle(.plain)
        }
    }

    private var tools: some View {
        VStack(spacing: 16) {
            HStack(spacing: 12) {
                toolLabel("Brush")
                HStack(spacing: 8) {
                    ForEach(0..<3, id: \.self) { i in chip(i) }
                    Spacer(minLength: 0)
                }
                .frame(maxWidth: .infinity)
                Button {
                    awake = true
                    reset()
                } label: {
                    HStack(spacing: 6) {
                        PaintSmearPhotoResetIcon()
                            .frame(width: 16, height: 16)
                            .rotationEffect(.degrees(spin))
                        Text("Reset")
                            .font(.system(size: 14, weight: .semibold))
                            .foregroundStyle(M.ink)
                    }
                    .padding(.horizontal, 14)
                    .frame(height: 36)
                    .background(M.quietFill(Capsule()))
                }
                .buttonStyle(.plain)
                .accessibilityLabel("Reset the photo")
            }
            .frame(height: 40)
            HStack(spacing: 12) {
                toolLabel("Paint")
                HStack(spacing: 12) {
                    Text("Wet")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(M.mute)
                    slider
                    Text("Dry")
                        .font(.system(size: 13, weight: .medium))
                        .foregroundStyle(M.mute)
                }
                Spacer(minLength: 0)
            }
            .frame(height: 40)
        }
    }

    private func toolLabel(_ text: String) -> some View {
        Text(text)
            .font(.system(size: 13, weight: .semibold))
            .foregroundStyle(M.faint)
            .frame(width: 44, alignment: .leading)
    }

    private func chip(_ i: Int) -> some View {
        let on = brush == i
        let label = ["S", "M", "L"][i]
        let dot = 6 + Double(i) * 4
        return Button {
            brush = i
            engine.setBrush(i)
            awake = true
            M.selection.selectionChanged()
        } label: {
            HStack(spacing: 6) {
                Circle()
                    .fill(on ? M.dark : M.mute)
                    .frame(width: dot, height: dot)
                Text(label)
                    .font(.system(size: 14, weight: .semibold))
                    .foregroundStyle(on ? M.dark : M.mute)
            }
            .padding(.horizontal, 12)
            .frame(minWidth: 52)
            .frame(height: 36)
            .background {
                if on {
                    Capsule().fill(M.paper)
                } else {
                    M.quietFill(Capsule())
                }
            }
        }
        .buttonStyle(.plain)
        .accessibilityLabel("Brush size \(label)")
        .accessibilityAddTraits(on ? .isSelected : [])
    }

    private var slider: some View {
        ZStack(alignment: .leading) {
            Capsule()
                .fill(M.ink.opacity(0.14))
                .frame(width: M.sliderW, height: 4)
            Capsule()
                .fill(M.accent)
                .frame(width: max(M.sliderW * dry, 0.001), height: 4)
            Circle()
                .fill(Color(hex: 0xf6eee2))
                .overlay(M.innerHighlight(0.9, shape: Circle()))
                .shadow(color: .black.opacity(0.35), radius: 1, y: 1)
                .shadow(color: .black.opacity(0.3), radius: 7, y: 6)
                .frame(width: M.thumb, height: M.thumb)
                .offset(x: dry * (M.sliderW - M.thumb))
        }
        .frame(width: M.sliderW, height: 40)
        .contentShape(Rectangle())
        .gesture(
            DragGesture(minimumDistance: 0).onChanged { value in
                awake = true
                engine.touched()
                dry = min(max((value.location.x - M.thumb / 2) / (M.sliderW - M.thumb), 0), 1)
            }
        )
        .accessibilityElement()
        .accessibilityLabel("Paint wetness")
        .accessibilityValue("\(Int((dry * 100).rounded())) percent dry")
        .accessibilityAdjustableAction { direction in
            switch direction {
            case .increment: dry = min(dry + 0.1, 1)
            case .decrement: dry = max(dry - 0.1, 0)
            @unknown default: break
            }
        }
    }
}

/// The circular arrow of the Reset button (a 16 × 16 pt drawing).
struct PaintSmearPhotoResetIcon: View {
    var body: some View {
        Canvas { c, _ in
            // M13 8 A5 5 0 1 1 11.2 4.2: a large arc of radius 5 around (8, 8), clockwise on screen.
            var arc = Path()
            let start = atan2(0.0, 5.0)
            let end = atan2(4.2 - 8, 11.2 - 8)
            arc.addArc(center: CGPoint(x: 8, y: 8), radius: 5, startAngle: .radians(start), endAngle: .radians(end + 2 * .pi), clockwise: false)
            let style = StrokeStyle(lineWidth: 1.8, lineCap: .round, lineJoin: .round)
            c.stroke(arc, with: .color(PaintSmearPhotoMetrics.ink), style: style)
            var head = Path()
            head.move(to: CGPoint(x: 11.6, y: 1.6))
            head.addLine(to: CGPoint(x: 11.4, y: 4.6))
            head.addLine(to: CGPoint(x: 8.4, y: 4.3))
            c.stroke(head, with: .color(PaintSmearPhotoMetrics.ink), style: style)
        }
    }
}

@MainActor
enum PaintSmearPhotoMetrics {
    static let ink = Color(hex: 0xf3ece2)
    static let mute = Color(hex: 0xf3ece2, opacity: 0.6)
    static let faint = Color(hex: 0xf3ece2, opacity: 0.36)
    static let accent = Color(hex: 0xe9a24b)
    static let paper = Color(hex: 0xf1e7d8)
    static let dark = Color(hex: 0x2b2118)
    static let sliderW = 168.0
    static let thumb = 26.0
    /// Two tool rows of 40 pt with 16 pt between.
    static let toolsH = 96.0
    static let light = UIImpactFeedbackGenerator(style: .light)
    static let selection = UISelectionFeedbackGenerator()

    /// The still life, decoded once.
    static let photo: UIImage? = {
        guard let url = Bundle.main.url(forResource: "PaintSmearPhoto-still-life", withExtension: "jpg"),
              let image = UIImage(contentsOfFile: url.path) else { return nil }
        return image.preparingForDisplay() ?? image
    }()

    /// The quiet glassy fill of the chips and Reset.
    static func quietFill<S: InsettableShape>(_ shape: S) -> some View {
        shape
            .fill(ink.opacity(0.07))
            .overlay(shape.strokeBorder(ink.opacity(0.12), lineWidth: 0.5))
    }

    /// A 1 pt highlight along the top inside edge.
    static func innerHighlight<S: InsettableShape>(_ opacity: Double, shape: S) -> some View {
        shape.strokeBorder(
            LinearGradient(
                colors: [Color.white.opacity(opacity), Color.white.opacity(0)],
                startPoint: .top,
                endPoint: UnitPoint(x: 0.5, y: 0.15)
            ),
            lineWidth: 1
        )
    }
}
