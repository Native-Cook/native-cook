import SwiftUI
import UIKit

/*
 Smoke Signal Toast — "Campfire", an outdoor group's chat, where a notification arrives as smoke.

 Look: a night sky with stars over a pine treeline, a campfire on the ground at the bottom (flames,
 crossed logs, a ring of stones, sparks, firelight), the group's last messages under the header.
 The fire puffs a cloud that rises and condenses into the message "Dinner's ready at the lake"
 from Kai, which holds readable, then a gust tears it away to the right. One full-screen Metal
 layer effect (SmokeSignalToastShader.metal) over the message baked once as a mask
 (SmokeSignalToastArt.swift); views for the chat.

 autoPlay: loop = 9 s (script in SmokeSignalToastMotion.swift).
 Touch: tap the message to open it (a card with quick replies; the smoke drifts up out of its
 way); swipe it to blow it away in the swipe's direction (past a third of the way, or a fling,
 it goes; otherwise it gathers back). 2 s after the last touch the smoke clears (0.9 s) and the
 loop restarts. Reduced motion: the message shown still and readable, no loop.
*/
struct SmokeSignalToast: View {
    /// Plays the 9 s puff-form-disperse loop until touched.
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.displayScale) private var displayScale
    @State private var sim = SmokeSignalToastSim()
    @State private var art: SmokeSignalToastArt?
    @State private var touched = false
    @State private var downAt: Date?
    @State private var swiping = false

    private typealias M = SmokeSignalToastMotion
    private typealias P = SmokeSignalToastPalette

    var body: some View {
        // The block draws under the system bars and keeps its content clear of them itself.
        GeometryReader { geo in
            let insets = geo.safeAreaInsets
            let size = CGSize(
                width: geo.size.width + insets.leading + insets.trailing,
                height: geo.size.height + insets.top + insets.bottom
            )
            stage(size: size, insets: insets)
                .frame(width: size.width, height: size.height)
                .offset(x: -insets.leading, y: -insets.top)
        }
    }

    private func stage(size: CGSize, insets: EdgeInsets) -> some View {
        let g = SmokeSignalToastGeo(w: max(1, size.width), h: max(1, size.height),
                                    insetTop: insets.top, insetBottom: insets.bottom)
        let auto = autoPlay && !reduceMotion
        let reduced = reduceMotion
        let ready = art != nil
        let paused = !ready || (!auto && !touched)
        let key = "\(Int(size.width))x\(Int(size.height))@\(Int(g.toastY))"
        return TimelineView(.animation(paused: paused)) { timeline in
            let f = sim.step(to: timeline.date, geo: g, auto: auto, reduced: reduced, ready: ready)
            let _ = feelBlown()
            let card = sim.card
            ZStack(alignment: .topLeading) {
                P.base
                scene(f, g, size: size)
                SmokeSignalToastHeader()
                    .frame(width: size.width - 32, height: M.headerH)
                    .offset(x: 12, y: insets.top + 4)
                SmokeSignalToastThread()
                    .frame(width: size.width - 32, alignment: .top)
                    .offset(x: 16, y: insets.top + M.headerH + 16)
                if let art, !reduced {
                    Color.clear
                        .contentShape(Rectangle())
                        .frame(width: art.box.width, height: art.box.height)
                        .gesture(toastGesture)
                        .offset(x: art.box.minX, y: art.box.minY)
                        .accessibilityElement()
                        .accessibilityAddTraits(.isButton)
                        .accessibilityLabel("Kai: \(SmokeSignalToastArt.message). Tap to open, swipe to dismiss.")
                }
                SmokeSignalToastCard(onReply: close)
                    .frame(width: size.width - 40)
                    .opacity(card)
                    .scaleEffect(0.96 + 0.04 * card)
                    .offset(x: 20, y: g.toastY - 92 + (1 - card) * 16)
                    .allowsHitTesting(sim.open)
                SmokeSignalToastInput()
                    .frame(width: size.width - 32, height: M.inputH)
                    .offset(x: 16, y: g.inputTop)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
        }
        .clipped()
        .task(id: key) {
            let w = size.width, h = size.height, cy = g.toastY, scale = displayScale
            guard w > 1 && h > 1 else { return }
            let baked = await Task.detached(priority: .userInitiated) {
                SmokeSignalToastArt.bake(w: w, h: h, centerY: cy, scale: scale)
            }.value
            art = baked
        }
    }

    private func feelBlown() -> Bool {
        if sim.takeBlown() { UIImpactFeedbackGenerator(style: .light).impactOccurred() }
        return true
    }

    private func scene(_ f: SmokeSignalToastFrame, _ g: SmokeSignalToastGeo, size: CGSize) -> some View {
        let shader = ShaderLibrary.default.SmokeSignalToastScene(
            .float2(Float(g.w), Float(g.h)),
            .float4(Float(g.fireX), Float(g.fireY), Float(f.flare), Float(g.fireS)),
            .float4(Float(f.puff[0]), Float(f.puff[1]), Float(f.puff[2]), Float(f.puff[3])),
            .float4(Float(f.text[0]), Float(f.text[1]), Float(f.text[2]), Float(f.text[3])),
            .float4(Float(f.wind[0]), Float(f.wind[1]), Float(f.wind[2]), Float(f.wind[3])),
            .float(Float(f.phase - floor(f.phase))),
            .float(Float(g.treeY))
        )
        // The layer is the message mask; the shader draws the whole scene over it.
        return ZStack {
            Color.black
            if let art {
                Image(uiImage: art.image)
                    .resizable()
                    .interpolation(.high)
            }
        }
        .frame(width: size.width, height: size.height)
        .layerEffect(shader, maxSampleOffset: CGSize(width: 320, height: 320))
        .allowsHitTesting(false)
    }

    /// Tap (under 0.35 s) opens the message; a swipe (over 8 pt) blows it away.
    private var toastGesture: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { v in
                if downAt == nil {
                    downAt = .now
                    touched = true
                    sim.touch()
                }
                let d = hypot(v.translation.width, v.translation.height)
                if swiping || d > 8 {
                    swiping = true
                    if sim.open || sim.frame.text[3] < 0.3 { return }
                    sim.drag(v.translation.width, v.translation.height)
                }
            }
            .onEnded { v in
                let quick = Date.now.timeIntervalSince(downAt ?? .now) < 0.35
                downAt = nil
                if swiping {
                    swiping = false
                    sim.dragEnd(v.velocity.width, v.velocity.height)
                    return
                }
                if quick && sim.readable && !sim.open {
                    sim.setOpen(true)
                    UIImpactFeedbackGenerator(style: .light).impactOccurred()
                }
            }
    }

    private func close() {
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
        sim.setOpen(false)
    }
}

enum SmokeSignalToastPalette {
    static func hex(_ v: UInt32, _ a: Double = 1) -> Color {
        Color(red: Double((v >> 16) & 0xFF) / 255, green: Double((v >> 8) & 0xFF) / 255,
              blue: Double(v & 0xFF) / 255, opacity: a)
    }

    static let base = hex(0x0B0E1A)
    static let ink = hex(0xEDE7DF)
    static let soft = hex(0xEDE7DF, 0.6)
    static let ember = hex(0xF08A3C)
    static let dark = hex(0x2A1608)
}

private struct SmokeSignalToastAvatar: View {
    let letter: String
    let color: Color
    var size = 32.0
    var ring = SmokeSignalToastPalette.hex(0x0E1220)

    var body: some View {
        Text(letter)
            .font(.system(size: 13, weight: .bold))
            .foregroundStyle(SmokeSignalToastPalette.hex(0xFFF6EC))
            .frame(width: size, height: size)
            .background(Circle().fill(color))
            .overlay(Circle().strokeBorder(ring, lineWidth: 2))
    }
}

private struct SmokeSignalToastHeader: View {
    private typealias P = SmokeSignalToastPalette

    var body: some View {
        HStack(spacing: 10) {
            Button {} label: {
                Path { p in
                    p.move(to: CGPoint(x: 9.5, y: 2.5))
                    p.addLine(to: CGPoint(x: 2.5, y: 10))
                    p.addLine(to: CGPoint(x: 9.5, y: 17.5))
                }
                .stroke(P.ink, style: StrokeStyle(lineWidth: 2.2, lineCap: .round, lineJoin: .round))
                .frame(width: 12, height: 20)
                .frame(width: 36, height: 44)
                .contentShape(Rectangle())
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Back")
            HStack(spacing: -10) {
                SmokeSignalToastAvatar(letter: "K", color: P.hex(0xC9733A))
                SmokeSignalToastAvatar(letter: "M", color: P.hex(0x5F8A6C))
                SmokeSignalToastAvatar(letter: "T", color: P.hex(0x6573A6))
            }
            VStack(alignment: .leading, spacing: 1) {
                Text("Lakeside Crew")
                    .font(.system(size: 18, weight: .bold))
                    .foregroundStyle(P.ink)
                Text("6 around the fire · Pine Hollow")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(P.soft)
            }
            .lineLimit(1)
            .frame(maxWidth: .infinity, alignment: .leading)
        }
    }
}

private struct SmokeSignalToastThread: View {
    private typealias P = SmokeSignalToastPalette

    private struct Message {
        let who: String
        let text: String
        let time: String
        let mine: Bool
        let tint: Color
    }

    private let thread = [
        Message(who: "Mira", text: "Tents are up by the pines", time: "7:58 PM", mine: false, tint: P.hex(0x7FA58A)),
        Message(who: "You", text: "Bringing the kettle and cocoa", time: "8:05 PM", mine: true, tint: P.ember),
        Message(who: "Theo", text: "Who has the lantern?", time: "8:21 PM", mine: false, tint: P.hex(0x8C9AC8)),
    ]

    var body: some View {
        GeometryReader { geo in
            VStack(spacing: 8) {
                Text("TONIGHT")
                    .font(.system(size: 11, weight: .bold))
                    .kerning(1.4)
                    .foregroundStyle(P.hex(0xEDE7DF, 0.4))
                    .padding(.bottom, 2)
                ForEach(thread, id: \.text) { m in
                    row(m, maxBubble: geo.size.width * 0.76)
                }
            }
            .frame(width: geo.size.width)
        }
        .frame(height: 200)
        .allowsHitTesting(false)
    }

    private func row(_ m: Message, maxBubble: Double) -> some View {
        let shape = UnevenRoundedRectangle(
            topLeadingRadius: 18,
            bottomLeadingRadius: m.mine ? 18 : 6,
            bottomTrailingRadius: m.mine ? 6 : 18,
            topTrailingRadius: 18,
            style: .continuous
        )
        let bubble = VStack(alignment: .leading, spacing: 2) {
            if !m.mine {
                Text(m.who)
                    .font(.system(size: 12, weight: .bold))
                    .foregroundStyle(m.tint)
            }
            Text(m.text)
                .font(.system(size: 15, weight: m.mine ? .medium : .regular))
                .foregroundStyle(m.mine ? P.dark : P.ink)
                .lineSpacing(2)
        }
        .padding(.horizontal, 14)
        .padding(.vertical, 9)
        .background {
            if m.mine {
                shape.fill(P.hex(0xF08A3C, 0.88))
                    .shadow(color: P.hex(0xF08A3C, 0.25), radius: 7, y: 4)
            } else {
                shape.fill(P.hex(0xEDE7DF, 0.09))
                    .overlay(shape.strokeBorder(P.hex(0xEDE7DF, 0.14), lineWidth: 0.5))
            }
        }
        .frame(maxWidth: maxBubble, alignment: m.mine ? .trailing : .leading)
        .fixedSize(horizontal: true, vertical: false)
        let time = Text(m.time)
            .font(.system(size: 11))
            .monospacedDigit()
            .foregroundStyle(P.hex(0xEDE7DF, 0.38))
            .padding(.bottom, 4)
        return HStack(alignment: .bottom, spacing: 8) {
            if m.mine {
                Spacer(minLength: 0)
                time
                bubble
            } else {
                bubble
                time
                Spacer(minLength: 0)
            }
        }
    }
}

private struct SmokeSignalToastCard: View {
    let onReply: () -> Void
    private typealias P = SmokeSignalToastPalette

    var body: some View {
        let shape = RoundedRectangle(cornerRadius: 26, style: .continuous)
        VStack(alignment: .leading, spacing: 12) {
            HStack(spacing: 10) {
                SmokeSignalToastAvatar(letter: "K", color: P.hex(0xC9733A), size: 36, ring: Color.white.opacity(0.15))
                VStack(alignment: .leading, spacing: 0) {
                    Text("Kai")
                        .font(.system(size: 16, weight: .bold))
                        .foregroundStyle(P.ink)
                    Text("8:42 PM · Lakeside Crew")
                        .font(.system(size: 12, weight: .medium))
                        .monospacedDigit()
                        .foregroundStyle(P.soft)
                }
                Spacer(minLength: 0)
            }
            Text("\(SmokeSignalToastArt.message). Grilled corn and trout, bring your mugs.")
                .font(.system(size: 17, weight: .medium))
                .lineSpacing(3)
                .foregroundStyle(P.ink)
                .fixedSize(horizontal: false, vertical: true)
            HStack(spacing: 10) {
                Button(action: onReply) {
                    Text("On my way")
                        .font(.system(size: 15, weight: .bold))
                        .foregroundStyle(P.dark)
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                        .background(Capsule().fill(P.ember))
                }
                .buttonStyle(.plain)
                Button(action: onReply) {
                    Text("Save me a plate")
                        .font(.system(size: 15, weight: .semibold))
                        .foregroundStyle(P.ink)
                        .frame(maxWidth: .infinity)
                        .frame(height: 44)
                        .background(Capsule().fill(P.hex(0xEDE7DF, 0.1)))
                }
                .buttonStyle(.plain)
            }
        }
        .padding(18)
        .background {
            shape.fill(P.hex(0x1E1A1E, 0.94))
                .shadow(color: .black.opacity(0.5), radius: 24, y: 24)
                .shadow(color: .black.opacity(0.4), radius: 2, y: 2)
        }
        .overlay(shape.strokeBorder(P.hex(0xEDE7DF, 0.18), lineWidth: 0.5))
        .overlay {
            shape.strokeBorder(
                LinearGradient(colors: [.white.opacity(0.08), .white.opacity(0)],
                               startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.06)),
                lineWidth: 1)
        }
    }
}

private struct SmokeSignalToastInput: View {
    private typealias P = SmokeSignalToastPalette

    var body: some View {
        HStack(spacing: 10) {
            Text("Message Lakeside Crew")
                .font(.system(size: 16))
                .foregroundStyle(P.hex(0xEDE7DF, 0.45))
                .padding(.horizontal, 18)
                .frame(maxWidth: .infinity, alignment: .leading)
                .frame(height: 48)
                .background(Capsule().fill(P.hex(0x141012, 0.72)))
                .overlay(Capsule().strokeBorder(P.hex(0xEDE7DF, 0.16), lineWidth: 0.5))
            Button {} label: {
                Path { p in
                    p.move(to: CGPoint(x: 9, y: 14.5))
                    p.addLine(to: CGPoint(x: 9, y: 3.8))
                    p.move(to: CGPoint(x: 4.2, y: 8.4))
                    p.addLine(to: CGPoint(x: 9, y: 3.6))
                    p.addLine(to: CGPoint(x: 13.8, y: 8.4))
                }
                .stroke(P.dark, style: StrokeStyle(lineWidth: 2.2, lineCap: .round, lineJoin: .round))
                .frame(width: 18, height: 18)
                .frame(width: 48, height: 48)
                .background {
                    Circle().fill(P.ember)
                        .shadow(color: P.hex(0xF08A3C, 0.35), radius: 8, y: 6)
                        .shadow(color: .black.opacity(0.4), radius: 1, y: 1)
                }
                .overlay {
                    Circle().strokeBorder(
                        LinearGradient(colors: [.white.opacity(0.3), .white.opacity(0)],
                                       startPoint: .top, endPoint: UnitPoint(x: 0.5, y: 0.15)),
                        lineWidth: 1)
                }
            }
            .buttonStyle(.plain)
            .accessibilityLabel("Send")
        }
    }
}
