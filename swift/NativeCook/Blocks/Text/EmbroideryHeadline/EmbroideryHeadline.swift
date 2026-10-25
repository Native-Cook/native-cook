import SwiftUI
import UIKit

/*
 Embroidery Headline — the cover of "Thread & Needle", issue 9. The headline "Slow made." is
 satin-stitched on linen in a wooden hoop: madder red above, indigo below, every letter filled with
 parallel threads laid edge to edge at its own angle, so each catches the light differently; the
 full stop is a French knot. A needle with its thread lays the satin; the subheading follows.

 Touch: drag across the hoop to sew — moving right lays threads into the next unfinished letter
 (1.6 threads per pt), moving left unpicks the last ones; the needle rides the stitching front and
 leans with the drag; a light tick as each letter is finished, a tap when the knot pops. 2 s after
 the finger lifts the headline finishes itself (≤ 0.9 s, ease-in-out) and the loop starts again from
 its first frame. Reduced motion: the finished cover, still; dragging still sews.

 One Canvas: the table, hoop and linen are baked once at 2x and the satin cut once, both off the
 main thread (vector table and hoop until the bake is ready); per frame the laid threads (a few paths per letter), the knot and the needle.
 */
struct EmbroideryHeadline: View {
    var autoPlay = false

    static let loop = 9.0

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var engine = EmbroideryHeadlineEngine()
    @State private var running = true
    @State private var hero: CGRect = .zero
    @State private var scene: Image?
    @State private var satin: EmbroideryHeadlineSatin?
    @State private var lastX: CGFloat?
    @State private var dragging = false

    private typealias S = EmbroideryHeadlineStyle
    private static let space = "embroideryHeadline"

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
        // The hoop may use 12 pt beyond the column's margins on each side.
        let geo: EmbroideryHeadlineGeo? = hero.width > 0 ? EmbroideryHeadlineGeo(w: size.width, h: size.height, hero: hero.insetBy(dx: -12, dy: 0)) : nil
        return ZStack(alignment: .topLeading) {
            S.background
            TimelineView(.animation(paused: !running)) { timeline in
                let date = timeline.date
                let _ = engine.step(date)
                Canvas { ctx, _ in
                    _ = date
                    guard let geo else { return }
                    // The table, hoop and linen: the baked image once it is ready, cheap vectors until then.
                    if let scene {
                        ctx.draw(scene, in: CGRect(x: 0, y: 0, width: geo.w, height: geo.h))
                    } else {
                        EmbroideryHeadlineArt.drawPlainScene(ctx, geo)
                    }
                    guard let satin, engine.satin != nil else { return }
                    let st = engine.state
                    EmbroideryHeadlineArt.drawSatin(ctx, satin, st.p)
                    EmbroideryHeadlineArt.drawKnot(ctx, satin, scale: st.knot.0, alpha: st.knot.1)
                    let n = st.needle
                    EmbroideryHeadlineArt.drawNeedle(
                        ctx, x: n.0, y: n.1, angle: -0.92 + engine.lean, lift: n.4 * 0.55 + engine.bob,
                        fromX: n.2, fromY: n.3, rgb: engine.needleRGB(st)
                    )
                }
                .frame(width: size.width, height: size.height)
            }
            .allowsHitTesting(false)

            column(insets: insets)
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
            // The satin first (a fraction of a second), so the loop can start; the textured scene after.
            let cut = await Task.detached(priority: .userInitiated) { EmbroideryHeadlineBake.satin(geo) }.value
            guard !Task.isCancelled else { return }
            satin = cut
            engine.configure(geo, cut)
            wake()
            let img = await Task.detached(priority: .userInitiated) { EmbroideryHeadlineBake.scene(geo) }.value
            guard !Task.isCancelled, let img else { return }
            scene = Image(decorative: img, scale: 2)
        }
        .task(id: running) {
            // Pauses the timeline when nothing moves.
            guard running else { return }
            while !Task.isCancelled {
                try? await Task.sleep(for: .milliseconds(100))
                if !engine.wantsFrames && engine.satin != nil {
                    running = false
                    return
                }
            }
        }
    }

    private func wake() {
        if !running { running = true }
    }

    private func column(insets: EdgeInsets) -> some View {
        VStack(spacing: 0) {
            HStack(spacing: 0) {
                Text("THREAD & NEEDLE")
                    .font(.custom("Georgia-Bold", size: 17))
                    .kerning(2.6)
                    .foregroundStyle(S.cream)
                Spacer(minLength: 0)
                Text("No. 9 · Autumn")
                    .font(.system(size: 13, weight: .medium).monospacedDigit())
                    .foregroundStyle(S.muted)
            }
            .frame(height: 36)
            Rectangle()
                .fill(S.cream.opacity(0.28))
                .frame(height: 1 / displayScale)
                .padding(.top, 8)
            HStack(spacing: 0) {
                Text("COVER STORY")
                    .font(.system(size: 11, weight: .bold))
                    .kerning(2)
                    .foregroundStyle(S.accent)
                Spacer(minLength: 0)
                Text("Hand embroidery")
                    .font(.system(size: 12, weight: .medium))
                    .foregroundStyle(S.muted)
            }
            .padding(.top, 12)

            Color.clear
                .frame(maxWidth: .infinity, minHeight: 260, maxHeight: .infinity)
                .contentShape(Rectangle())
                .onGeometryChange(for: CGRect.self) { $0.frame(in: .named(Self.space)) } action: { r in
                    // Ignore sub-point jitter so the satin is not cut again for nothing.
                    if abs(r.minX - hero.minX) > 0.5 || abs(r.minY - hero.minY) > 0.5 || abs(r.width - hero.width) > 0.5 || abs(r.height - hero.height) > 0.5 { hero = r }
                }
                .gesture(sewGesture)
                .accessibilityElement()
                .accessibilityLabel("Headline: Slow made. Drag across the hoop to stitch or unpick it.")

            TimelineView(.animation(paused: !running)) { _ in
                let sub = engine.state.sub
                VStack(alignment: .leading, spacing: 8) {
                    Text("Twelve makers on the quiet patience of the needle")
                        .font(.custom("Georgia-Italic", size: 23))
                        .lineSpacing(29 - 23 * 1.14)
                        .foregroundStyle(S.cream)
                        .fixedSize(horizontal: false, vertical: true)
                    Text("Words by Ines Rook · 18 min read")
                        .font(.system(size: 13))
                        .foregroundStyle(S.muted)
                }
                .frame(maxWidth: .infinity, alignment: .leading)
                .opacity(sub.0)
                .offset(y: sub.1)
            }
            .padding(.bottom, 20)

            HStack(spacing: 12) {
                Button {} label: {
                    Text("Read the story")
                        .font(.system(size: 17, weight: .semibold))
                        .foregroundStyle(Color(red: 0x1F / 255, green: 0x2A / 255, blue: 0x23 / 255))
                        .frame(maxWidth: .infinity)
                        .frame(height: 52)
                        .background(
                            Capsule().fill(S.cream)
                                .overlay(Capsule().strokeBorder(Color.white.opacity(0.7), lineWidth: 0.5).mask(LinearGradient(colors: [.white, .clear], startPoint: .top, endPoint: .center)))
                                .shadow(color: .black.opacity(0.3), radius: 1, y: 1)
                                .shadow(color: .black.opacity(0.25), radius: 12, y: 10)
                        )
                }
                .buttonStyle(EmbroideryHeadlinePress(scale: 0.97))
                Button {} label: {
                    Image(systemName: "bookmark")
                        .font(.system(size: 19, weight: .medium))
                        .foregroundStyle(S.cream)
                        .frame(width: 52, height: 52)
                        .overlay(Circle().strokeBorder(S.cream.opacity(0.3), lineWidth: 1))
                        .contentShape(Circle())
                }
                .buttonStyle(EmbroideryHeadlinePress(scale: 0.94))
                .accessibilityLabel("Save the story")
            }
        }
        .padding(.horizontal, 24)
        .padding(.top, insets.top + 8)
        .padding(.bottom, insets.bottom + 12)
    }

    private var sewGesture: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                if !dragging {
                    dragging = true
                    engine.touchDown()
                    wake()
                }
                // The pan starts sewing once the finger has moved 4 pt.
                guard hypot(v.translation.width, v.translation.height) >= 4 else { return }
                let x = v.location.x
                let dx = lastX.map { x - $0 } ?? (v.translation.width >= 0 ? v.translation.width - 4 : v.translation.width + 4)
                lastX = x
                switch engine.drag(by: dx) {
                case 1: UISelectionFeedbackGenerator().selectionChanged()
                case 2: UIImpactFeedbackGenerator(style: .light).impactOccurred()
                default: break
                }
            }
            .onEnded { _ in
                dragging = false
                lastX = nil
                engine.touchUp()
                wake()
            }
    }
}

enum EmbroideryHeadlineStyle {
    static let cream = Color(red: 0xEF / 255, green: 0xE6 / 255, blue: 0xD4 / 255)
    static let muted = Color(red: 0xA7 / 255, green: 0xB2 / 255, blue: 0xA4 / 255)
    static let accent = Color(red: 0xD9 / 255, green: 0xA5 / 255, blue: 0x6B / 255)
    static let background = Color(red: 0x1E / 255, green: 0x28 / 255, blue: 0x22 / 255)
}

struct EmbroideryHeadlinePress: ButtonStyle {
    var scale: CGFloat
    func makeBody(configuration: Configuration) -> some View {
        configuration.label.scaleEffect(configuration.isPressed ? scale : 1)
    }
}
