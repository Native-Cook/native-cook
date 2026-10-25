import SwiftUI
import UIKit

/*
 Crayon Box Picker — "Doodlebug · Pick a colour". An open box of twelve wax crayons stands above a
 sheet of drawing paper. Pull a crayon up and it slides out against the box's friction, its name
 showing on the wrapper, and wobbles free while its neighbours lean into the gap; let go and it
 swings down onto the paper, scribbles a swatch in wax (the old swatch fades under it), becomes the
 current colour (its name rolls in), and goes home into its slot.

 Touch: drag a crayon upward (0.85 × the finger, rubber-banding past fully out); let go past 75 %
 to pick it, or earlier and it springs back (0.4 s, damping 0.7). Sideways speed tips it (wobble).
 A tick when it comes free, a light tap as it is picked, a tick when the name changes. With
 autoPlay, 2 s after a pick finishes the scene cross-fades to the loop's first frame (0.8 s).
 Reduced motion: nothing moves by itself; a pick changes the colour and swatch at once.

 One Canvas: desk, paper, box, the twelve crayons and the paper's tooth are baked once at 2x off
 the main thread; per frame the Canvas places the crayon images, draws the swatches (wax strokes
 with the paper's tooth knocked out) and the box front.
 */
struct CrayonBoxPicker: View {
    var autoPlay = false

    static let loop = 9.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var engine = CrayonBoxPickerEngine()
    @State private var running = true
    @State private var hero: CGRect = .zero
    @State private var art: CrayonBoxPickerImages?
    @State private var scribble = CrayonBoxPickerScribble()
    @State private var dragging = false
    @State private var onCrayon = false

    private typealias S = CrayonBoxPickerStyle
    private static let space = "crayonBoxPicker"

    var body: some View {
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + insets.leading + insets.trailing, height: geo.size.height + insets.top + insets.bottom)
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(S.background.ignoresSafeArea())
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let geo: CrayonBoxPickerGeo? = hero.width > 0 ? CrayonBoxPickerGeo(w: size.width, h: size.height, hero: hero) : nil
        return ZStack(alignment: .topLeading) {
            S.background
            TimelineView(.animation(paused: !running)) { timeline in
                let date = timeline.date
                let _ = engine.step(date)
                Canvas { ctx, _ in
                    _ = date
                    guard let geo, let art else { return }
                    draw(ctx, geo, art)
                }
                .frame(width: size.width, height: size.height)
            }
            .allowsHitTesting(false)

            column(insets: insets, geo: geo)
                .frame(width: size.width, height: size.height, alignment: .top)
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
        .coordinateSpace(name: Self.space)
        .clipped()
        .onAppear { engine.setMode(auto: autoPlay && !reduceMotion, reduced: reduceMotion) }
        .onChange(of: reduceMotion) { _, r in
            engine.setMode(auto: autoPlay && !r, reduced: r)
            wake()
        }
        .task(id: geo) {
            guard let geo else { return }
            let path = CrayonBoxPickerScribble(geo)
            let baked = await Task.detached(priority: .userInitiated) { () -> CrayonBoxPickerImages? in
                let toothRect = CGRect(x: geo.sx - geo.rx - 12, y: geo.sy - geo.ry - 16, width: (geo.rx + 12) * 2, height: (geo.ry + 16) * 2)
                guard let desk = CrayonBoxPickerBake.desk(geo), let front = CrayonBoxPickerBake.front(geo),
                      let tooth = CrayonBoxPickerBake.tooth(toothRect) else { return nil }
                var crayons: [Image] = []
                for i in 0..<CrayonBoxPickerMotion.crayons.count {
                    guard let c = CrayonBoxPickerBake.crayon(geo, i) else { return nil }
                    crayons.append(Image(decorative: c, scale: 2))
                }
                return CrayonBoxPickerImages(
                    desk: Image(decorative: desk, scale: 2), front: Image(decorative: front, scale: 2), crayons: crayons,
                    tooth: Image(decorative: tooth, scale: 2).interpolation(.high), toothRect: toothRect
                )
            }.value
            guard let baked else { return }
            scribble = path
            art = baked
            engine.artReady = true
            wake()
        }
        .task(id: running) {
            // Pauses the timeline when nothing moves.
            guard running else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(100))
                if !engine.wantsFrames && engine.artReady {
                    running = false
                    return
                }
            }
        }
    }

    private func wake() {
        if !running { running = true }
    }

    private func draw(_ ctx: GraphicsContext, _ g: CrayonBoxPickerGeo, _ art: CrayonBoxPickerImages) {
        switch engine.mode {
        case .auto, .still:
            CrayonBoxPickerArt.drawLoopFrame(ctx, g, art, scribble, t: engine.mode == .auto ? engine.loopT : 0)
        case .user, .back:
            let f = engine.userFrame()
            CrayonBoxPickerArt.drawPick(ctx, g, art, scribble, crayon: f.0, phase: f.1, swatch: f.2)
            let k = engine.returnK
            if k > 0 {
                var l = ctx
                l.opacity = k
                l.drawLayer { layer in CrayonBoxPickerArt.drawLoopFrame(layer, g, art, scribble, t: 0) }
            }
        }
    }

    private func column(insets: EdgeInsets, geo: CrayonBoxPickerGeo?) -> some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                Image(systemName: "chevron.left")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(S.ink)
                    .frame(width: 40, height: 40)
                    .background(
                        Circle().fill(Color(red: 250 / 255, green: 253 / 255, blue: 251 / 255, opacity: 0.78))
                            .shadow(color: S.shade.opacity(0.18), radius: 1, y: 1)
                            .shadow(color: S.shade.opacity(0.1), radius: 8, y: 6)
                    )
                Spacer(minLength: 0)
                Text("Doodlebug")
                    .font(.system(size: 15, weight: .bold))
                    .foregroundStyle(S.ink)
                Spacer(minLength: 0)
                Text("Page 3")
                    .font(.system(size: 12, weight: .semibold).monospacedDigit())
                    .foregroundStyle(S.muted)
                    .padding(.horizontal, 12)
                    .frame(height: 28)
                    .background(Capsule().fill(Color(red: 250 / 255, green: 253 / 255, blue: 251 / 255, opacity: 0.6)))
            }
            .frame(height: 44)

            VStack(alignment: .leading, spacing: 4) {
                Text("Pick a colour")
                    .font(.system(size: 30, weight: .heavy))
                    .kerning(-0.4)
                    .foregroundStyle(S.ink)
                    .frame(height: 36)
                Text("Pull a crayon out of the box")
                    .font(.system(size: 15))
                    .foregroundStyle(S.muted)
            }
            .frame(maxWidth: .infinity, alignment: .leading)
            .padding(.top, 12)

            Color.clear
                .frame(maxWidth: .infinity, minHeight: 300, maxHeight: .infinity)
                .contentShape(Rectangle())
                .onGeometryChange(for: CGRect.self) { $0.frame(in: .named(Self.space)) } action: { hero = $0 }
                .gesture(dragGesture(geo))
                .accessibilityElement()
                .accessibilityLabel("Box of twelve crayons. Drag one up to pick its colour.")

            HStack(alignment: .bottom, spacing: 0) {
                VStack(alignment: .leading, spacing: 2) {
                    smallLabel("Colour")
                    TimelineView(.animation(paused: !running)) { _ in
                        CrayonBoxPickerName(colour: engine.colour)
                    }
                }
                Spacer(minLength: 0)
                VStack(alignment: .trailing, spacing: 2) {
                    smallLabel("Box")
                    Text("12 wax crayons")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(S.ink)
                        .frame(height: S.nameH)
                }
            }
            .padding(.top, 8)

            Button {} label: {
                Text("Start drawing")
                    .font(.system(size: 17, weight: .semibold))
                    .foregroundStyle(Color(red: 0xF3 / 255, green: 0xFA / 255, blue: 0xF7 / 255))
                    .frame(maxWidth: .infinity)
                    .frame(height: 56)
                    .background(
                        Capsule().fill(S.ink)
                            .overlay(Capsule().strokeBorder(Color.white.opacity(0.12), lineWidth: 0.5).mask(LinearGradient(colors: [.white, .clear], startPoint: .top, endPoint: .center)))
                            .shadow(color: S.ink.opacity(0.25), radius: 2, y: 2)
                            .shadow(color: S.ink.opacity(0.2), radius: 12, y: 12)
                    )
            }
            .buttonStyle(CrayonBoxPickerPress(scale: 0.97))
            .padding(.top, 16)
        }
        .padding(.horizontal, 20)
        .padding(.top, insets.top + 6)
        .padding(.bottom, insets.bottom + 12)
    }

    private func smallLabel(_ s: String) -> some View {
        Text(s.uppercased())
            .font(.system(size: 12, weight: .semibold))
            .kerning(0.4)
            .foregroundStyle(S.muted)
    }

    private func dragGesture(_ geo: CrayonBoxPickerGeo?) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .named(Self.space))
            .onChanged { v in
                guard let geo else { return }
                if !dragging {
                    dragging = true
                    onCrayon = engine.begin(at: v.startLocation, geo: geo)
                    wake()
                }
                guard onCrayon, hypot(v.translation.width, v.translation.height) >= 2 else { return }
                if engine.move(translationY: v.translation.height, velocityX: v.velocity.width, geo: geo) {
                    UISelectionFeedbackGenerator().selectionChanged()
                }
            }
            .onEnded { _ in
                dragging = false
                guard onCrayon else { return }
                onCrayon = false
                if engine.end() {
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                    if !engine.reduced {
                        DispatchQueue.main.asyncAfter(deadline: .now() + 1.47) { UISelectionFeedbackGenerator().selectionChanged() }
                    }
                }
                wake()
            }
    }
}

enum CrayonBoxPickerStyle {
    static let ink = Color(red: 0x17 / 255, green: 0x3A / 255, blue: 0x30 / 255)
    static let muted = Color(red: 0x4E / 255, green: 0x6E / 255, blue: 0x66 / 255)
    static let shade = Color(red: 20 / 255, green: 50 / 255, blue: 40 / 255)
    static let background = Color(red: 0xA9 / 255, green: 0xC9 / 255, blue: 0xC2 / 255)
    static let nameH: CGFloat = 28
}

struct CrayonBoxPickerPress: ButtonStyle {
    var scale: CGFloat
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.scaleEffect(configuration.isPressed ? scale : 1)
    }
}

/// The colour's name with its dot, the new one rolling up as the old one leaves.
struct CrayonBoxPickerName: View {
    var colour: (Int, Int, Double)

    var body: some View {
        ZStack(alignment: .topLeading) {
            ForEach(Array(CrayonBoxPickerMotion.crayons.enumerated()), id: \.offset) { i, c in
                let roll = colour.2
                let (opacity, y): (Double, Double) = i == colour.1 ? (roll, (1 - roll) * CrayonBoxPickerStyle.nameH)
                    : (i == colour.0 && roll < 1) ? (1 - roll, -roll * CrayonBoxPickerStyle.nameH)
                    : (0, CrayonBoxPickerStyle.nameH)
                HStack(spacing: 8) {
                    Circle()
                        .fill(c.color
                            .shadow(.inner(color: .black.opacity(0.2), radius: 1.5, y: -2))
                            .shadow(.inner(color: .white.opacity(0.35), radius: 1, y: 1)))
                        .frame(width: 14, height: 14)
                        .shadow(color: Color(red: 20 / 255, green: 40 / 255, blue: 30 / 255, opacity: 0.3), radius: 1, y: 1)
                    Text(c.name)
                        .font(.system(size: 22, weight: .heavy))
                        .foregroundStyle(CrayonBoxPickerStyle.ink)
                }
                .frame(height: CrayonBoxPickerStyle.nameH)
                .opacity(opacity)
                .offset(y: y)
            }
        }
        .frame(width: 180, height: CrayonBoxPickerStyle.nameH, alignment: .topLeading)
        .clipped()
    }
}
