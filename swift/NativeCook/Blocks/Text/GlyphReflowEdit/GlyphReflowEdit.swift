import CoreText
import SwiftUI
import UIKit

/// "Quill": a writing assistant rewrites a paragraph, and the letters both versions share glide to their new places
/// along short arcs; removed glyphs fade and drop, new ones fade in from the accent, line breaks reflow.
///
/// Glyphs are measured once (GlyphReflowEditMotion.swift: CoreText with the 12 pt optical size, as the React Native
/// build's Skia font); one `Canvas` draws them from one morph value. The 9 s loop morphs to Shorter, scrubs back with
/// the bar, switches to Warmer, morphs there and back. Tap a chip to rewrite; drag the bar under the card to move
/// between the two versions. 2 s after the last touch it springs back to the original and the loop restarts.
/// Reduce Motion: the first rewrite in place, a chip swaps the text at once, the bar still scrubs.
struct GlyphReflowEdit: View {
    struct Version { let label: String; let text: String }

    var autoPlay: Bool = true
    var original: String = GlyphReflowEdit.originalText
    var versions: [Version] = GlyphReflowEdit.defaultVersions
    var title: String = "Note to the team"

    static let loop: Double = GlyphReflowEditMotion.loop

    static let originalText =
        "Hi everyone, I wanted to let you know that the launch of the new onboarding has been moved to next Thursday because we still need a little more time to finish testing the payment screens. Please send me anything that is still open by Tuesday."
    static let defaultVersions = [
        Version(label: "Shorter", text: "Hi all, the new onboarding now launches next Thursday. We need more time to test the payment screens. Please send open items by Tuesday."),
        Version(label: "Warmer", text: "Hi everyone, thank you for your patience. We are moving the new onboarding to next Thursday so the payment screens get the testing they deserve. If anything is still open, send it my way by Tuesday."),
        Version(label: "Clearer", text: "The new onboarding launch moves to next Thursday. Testing of the payment screens is not finished. Send me every open item by Tuesday."),
    ]

    private typealias M = GlyphReflowEditMotion
    private typealias G = GlyphReflowEditMotion.Glyph

    private static let desk = Color(hex: 0xE8EBE4)
    private static let paper = Color(hex: 0xFBFBF6)
    private static let ink = Color(hex: 0x1F2522)
    private static let inkSoft = Color(hex: 0x6C756F)
    private static let hairlineColor = Color(hex: 0x1F2522, opacity: 0.1)
    private static let accent = Color(hex: 0x2C6E51)
    private static let accentSoft = Color(hex: 0xD9E7DF)
    private static let hairline = 1 / UIScreen.main.scale

    private static let side = 16.0
    private static let maxWidth = 560.0
    private static let header = 56.0
    private static let cardPad = 20.0
    private static let gap = 12.0
    private static let chips = 44.0
    private static let bar = 58.0
    private static let barLabels = 20.0
    private static let statsH = 68.0
    private static let thumb = 28.0
    private static let buckets = 8

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var model = GlyphReflowEditModel()
    @State private var cache = GlyphReflowEditCache()
    /// Bumped by touches only, so a paused timeline (Reduce Motion) still redraws.
    @State private var revision = 0

    private var playing: Bool { autoPlay && !reduceMotion }

    var body: some View {
        GeometryReader { outer in
            let ins = outer.safeAreaInsets
            GeometryReader { full in
                TimelineView(.animation(minimumInterval: nil, paused: reduceMotion)) { tl in
                    screen(tl.date.timeIntervalSinceReferenceDate, size: full.size, ins: ins)
                }
            }
            .ignoresSafeArea()
        }
        .background(Self.desk.ignoresSafeArea())
        .preferredColorScheme(.light)
        .onAppear { model.reset(reduced: reduceMotion) }
        .onChange(of: reduceMotion) { _, now in model.reset(reduced: now) }
    }

    // MARK: - Layout

    private struct Geo {
        let W, H, left, inner, textWidth, top, cardHeight, chipsTop, barTop, chipWidth, trackLeft, trackWidth, trackY: Double
    }

    private func geo(_ size: CGSize, _ ins: EdgeInsets) -> Geo {
        let W = size.width, H = size.height
        let column = min(W, Self.maxWidth)
        let left = (W - column) / 2 + Self.side
        let inner = column - Self.side * 2
        let top = ins.top + 4
        let bottom = max(ins.bottom, 12)
        let cardHeight = max(H - top - bottom - Self.header - Self.gap - Self.chips - Self.gap - Self.bar - Self.gap - Self.statsH, 180)
        let chipsTop = top + Self.header + cardHeight + Self.gap
        let barTop = chipsTop + Self.chips + Self.gap
        return Geo(W: W, H: H, left: left, inner: inner, textWidth: inner - Self.cardPad * 2, top: top, cardHeight: cardHeight,
                   chipsTop: chipsTop, barTop: barTop, chipWidth: inner / Double(max(versions.count, 1)),
                   trackLeft: left + Self.thumb / 2, trackWidth: inner - Self.thumb,
                   trackY: barTop + Self.barLabels + (Self.bar - Self.barLabels) / 2)
    }

    private func screen(_ now: Double, size: CGSize, ins: EdgeInsets) -> some View {
        let g = geo(size, ins)
        let page = cache.page(original: original, versions: versions.map(\.text), textWidth: g.textWidth, textRoom: g.cardHeight - Self.cardPad * 2)
        let f = model.step(now: now, playing: playing, reduced: reduceMotion)
        _ = revision

        return ZStack(alignment: .topLeading) {
            VStack(spacing: 0) {
                headerRow
                    .frame(height: Self.header)
                card(page, f, g)
                    .frame(height: g.cardHeight)
                chipRow(f, g)
                    .frame(height: Self.chips)
                    .padding(.top, Self.gap)
                barRow(f, g)
                    .frame(height: Self.bar)
                    .padding(.top, Self.gap)
                statsRow(f)
                    .frame(height: Self.statsH)
                    .padding(.top, Self.gap)
            }
            .frame(width: g.inner, alignment: .topLeading)
            .offset(x: g.left, y: g.top)

            ghost(f, g)
        }
        .frame(width: g.W, height: g.H, alignment: .topLeading)
    }

    // MARK: - Header

    private var headerRow: some View {
        HStack(spacing: 10) {
            RoundedRectangle(cornerRadius: 11, style: .continuous)
                .fill(Self.accent)
                .frame(width: 36, height: 36)
                .overlay(
                    RoundedRectangle(cornerRadius: 11, style: .continuous)
                        .strokeBorder(Color.white.opacity(0.4), lineWidth: Self.hairline)
                        .mask(LinearGradient(stops: [.init(color: .black, location: 0), .init(color: .clear, location: 0.3)], startPoint: .top, endPoint: .bottom))
                )
                .overlay(Text("Q").font(.system(size: 19, weight: .bold)).foregroundStyle(Color(hex: 0xF5F8F2)))
            VStack(alignment: .leading, spacing: 1) {
                Text(title).font(.system(size: 17, weight: .semibold)).foregroundStyle(Self.ink).lineLimit(1)
                Text("Quill suggestion").font(.system(size: 13)).foregroundStyle(Self.inkSoft).lineLimit(1)
            }
            .frame(minWidth: 0, maxWidth: .infinity, alignment: .leading)
            Text("Saved").font(.system(size: 13, weight: .medium)).foregroundStyle(Self.inkSoft)
        }
    }

    // MARK: - The card: every glyph from one morph value

    private func card(_ page: GlyphReflowEditPage, _ f: GlyphReflowEditModel.Frame, _ g: Geo) -> some View {
        let morph = page.morphs[min(f.tone, page.morphs.count - 1)]
        let amount = f.progress
        let shape = RoundedRectangle(cornerRadius: 22, style: .continuous)
        return Canvas { ctx, _ in
            ctx.translateBy(x: Self.cardPad, y: Self.cardPad)
            Self.drawMorph(&ctx, morph, amount: amount, paths: page.paths)
        }
        .frame(width: g.inner, height: g.cardHeight)
        .background(shape.fill(Self.paper))
        .overlay(
            // The inset highlight along the top edge.
            shape.strokeBorder(Color.white.opacity(0.9), lineWidth: 1)
                .mask(LinearGradient(stops: [.init(color: .black, location: 0), .init(color: .clear, location: 0.04)], startPoint: .top, endPoint: .bottom))
        )
        .overlay(shape.strokeBorder(Self.hairlineColor, lineWidth: Self.hairline))
        .clipShape(shape)
        .background(
            shape.fill(Self.paper)
                .shadow(color: Color(hex: 0x1F2522, opacity: 0.14), radius: 1.5, y: 1)
                .shadow(color: Color(hex: 0x1F2522, opacity: 0.1), radius: 16, y: 12)
        )
        .accessibilityElement()
        .accessibilityLabel(original)
    }

    private static let incoming: [Color] = (0..<8).map { b in
        let toInk = pow(Double(b) / 7, 2)
        let from = (0x2C / 255.0, 0x6E / 255.0, 0x51 / 255.0), to = (0x1F / 255.0, 0x25 / 255.0, 0x22 / 255.0)
        return Color(.sRGB, red: M.mix(from.0, to.0, toInk), green: M.mix(from.1, to.1, toInk), blue: M.mix(from.2, to.2, toInk), opacity: Double(b + 1) / 8)
    }
    private static let outgoing: [Color] = (0..<8).map { ink.opacity(Double($0 + 1) / 8) }

    private static func drawMorph(_ ctx: inout GraphicsContext, _ m: GlyphReflowEditMorph, amount: Double, paths: [CGGlyph: Path]) {
        let p = min(max(amount, 0), 1)
        var shared = Path()
        for i in m.ids.indices {
            let start = m.delay[i] * G.spread
            let k = M.easeInOutCubic(M.seg(p, start, start + 1 - G.spread))
            let lift = 4 * k * (1 - k) * m.arc[i]
            if let glyph = paths[m.ids[i]] {
                shared.addPath(glyph, transform: CGAffineTransform(translationX: M.mix(m.ax[i], m.bx[i], k), y: M.mix(m.ay[i], m.by[i], k) + lift))
            }
        }
        ctx.fill(shared, with: .color(ink))

        if p < 1 {
            var bucket = [Path](repeating: Path(), count: buckets)
            for i in m.outIds.indices {
                let start = m.outDelay[i] * G.outStagger
                let k = M.seg(p, start, start + G.outEnd)
                if k >= 1 { continue }
                let b = min(Int(((1 - k) * Double(buckets)).rounded(.down)), buckets - 1)
                if let glyph = paths[m.outIds[i]] {
                    bucket[b].addPath(glyph, transform: CGAffineTransform(translationX: m.outX[i], y: m.outY[i] + k * k * G.drop))
                }
            }
            for b in 0..<buckets where !bucket[b].isEmpty { ctx.fill(bucket[b], with: .color(outgoing[b])) }
        }
        if p > G.inStart {
            var bucket = [Path](repeating: Path(), count: buckets)
            for i in m.inIds.indices {
                let start = G.inStart + m.inDelay[i] * G.inStagger
                let k = M.seg(p, start, start + G.inLength)
                if k <= 0 { continue }
                let b = min(Int((k * Double(buckets)).rounded(.down)), buckets - 1)
                if let glyph = paths[m.inIds[i]] {
                    bucket[b].addPath(glyph, transform: CGAffineTransform(translationX: m.inX[i], y: m.inY[i] + (1 - M.easeOutCubic(k)) * G.rise))
                }
            }
            for b in 0..<buckets where !bucket[b].isEmpty { ctx.fill(bucket[b], with: .color(incoming[b])) }
        }
    }

    // MARK: - Tone chips

    private func chipRow(_ f: GlyphReflowEditModel.Frame, _ g: Geo) -> some View {
        ZStack(alignment: .topLeading) {
            Capsule()
                .fill(Self.ink)
                .shadow(color: Color(hex: 0x1F2522, opacity: 0.3), radius: 1, y: 1)
                .frame(width: g.chipWidth, height: Self.chips - 6)
                .offset(x: f.indicator * g.chipWidth, y: 3)
            HStack(spacing: 0) {
                ForEach(versions.indices, id: \.self) { i in
                    let on = 1 - min(abs(f.indicator - Double(i)), 1)
                    ZStack {
                        Text(versions[i].label).foregroundStyle(Self.ink)
                        Text(versions[i].label).foregroundStyle(Color(hex: 0xF5F7F1)).opacity(on).accessibilityHidden(true)
                    }
                    .font(.system(size: 15, weight: .semibold))
                    .frame(width: g.chipWidth, height: Self.chips)
                    .contentShape(Rectangle())
                    .onTapGesture { chip(i) }
                    .accessibilityElement(children: .ignore)
                    .accessibilityAddTraits(.isButton)
                    .accessibilityLabel("Rewrite \(versions[i].label.lowercased())")
                }
            }
        }
        .frame(width: g.inner, height: Self.chips, alignment: .topLeading)
        .background(Capsule().fill(Color(hex: 0x1F2522, opacity: 0.07)))
        .clipShape(Capsule())
    }

    // MARK: - The bar

    private func barRow(_ f: GlyphReflowEditModel.Frame, _ g: Geo) -> some View {
        let p = f.progress
        let clamped = min(max(p, 0), 1)
        let stretch = min(abs(f.velocity) * 0.03, 0.16)
        let lift = f.held ? 1.08 : 1
        return VStack(spacing: 0) {
            HStack {
                Text("Original")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(Self.inkSoft)
                    .opacity(M.mix(1, 0.45, clamped))
                Spacer(minLength: 0)
                ZStack(alignment: .trailing) {
                    ForEach(versions.indices, id: \.self) { i in
                        Text(versions[i].label)
                            .font(.system(size: 13, weight: .semibold))
                            .foregroundStyle(Self.accent)
                            .opacity(1 - min(abs(f.indicator - Double(i)), 1))
                            .accessibilityHidden(abs(f.indicator - Double(i)) >= 0.5)
                    }
                }
                .frame(minWidth: 72, alignment: .trailing)
            }
            .frame(height: Self.barLabels)

            ZStack(alignment: .leading) {
                Capsule()
                    .fill(Color(hex: 0x1F2522, opacity: 0.13))
                    .overlay(alignment: .leading) {
                        Rectangle().fill(Self.accent).scaleEffect(x: clamped, y: 1, anchor: .leading)
                    }
                    .clipShape(Capsule())
                    .frame(width: g.trackWidth, height: 4)
                    .offset(x: Self.thumb / 2)
                Circle()
                    .fill(Self.paper)
                    .overlay(Circle().strokeBorder(Self.hairlineColor, lineWidth: Self.hairline))
                    .shadow(color: Color(hex: 0x1F2522, opacity: 0.25), radius: 1, y: 1)
                    .shadow(color: Color(hex: 0x1F2522, opacity: 0.18), radius: 6, y: 4)
                    .frame(width: Self.thumb, height: Self.thumb)
                    .scaleEffect(x: (1 + stretch) * lift, y: (1 - stretch * 0.6) * lift)
                    .offset(x: p * g.trackWidth)
            }
            .frame(width: g.inner, height: Self.bar - Self.barLabels, alignment: .leading)
            .contentShape(Rectangle().inset(by: -10))
            .gesture(scrub(g))
            .accessibilityElement()
            .accessibilityLabel("Scrub between the original and the rewrite")
        }
    }

    // MARK: - Stats

    private func statsRow(_ f: GlyphReflowEditModel.Frame) -> some View {
        let s = cache.stats(original: original, versions: versions.map(\.text))
        return HStack(spacing: 0) {
            stat("Words", s.words, f)
            rule
            stat("Read time", s.read, f)
            rule
            stat("Length", s.change, f)
        }
        .frame(maxHeight: .infinity)
        .background(RoundedRectangle(cornerRadius: 18, style: .continuous).fill(Self.accentSoft))
    }

    private var rule: some View {
        Rectangle().fill(Color(hex: 0x1F2522, opacity: 0.18)).frame(width: Self.hairline, height: 30)
    }

    private func stat(_ label: String, _ values: [String], _ f: GlyphReflowEditModel.Frame) -> some View {
        let p = min(max(f.progress, 0), 1)
        return VStack(spacing: 2) {
            ZStack {
                ForEach(values.indices, id: \.self) { i in
                    let shown = i == 0 ? 1 - p : (f.tone == i - 1 ? p : 0)
                    Text(values[i])
                        .font(.system(size: 20, weight: .bold).monospacedDigit())
                        .foregroundStyle(Self.ink)
                        .lineLimit(1)
                        .opacity(shown)
                        .accessibilityHidden(shown < 0.5)
                }
            }
            .frame(height: 26)
            Text(label).font(.system(size: 12)).foregroundStyle(Color(hex: 0x4F5D55))
        }
        .frame(minWidth: 0, maxWidth: .infinity)
    }

    // MARK: - The ghost finger of the loop

    private func ghost(_ f: GlyphReflowEditModel.Frame, _ g: Geo) -> some View {
        let t = f.clock
        let opacity = f.auto && playing ? M.ghostOpacityAt(t) : 0
        var x = g.left + g.chipWidth * 0.5
        var y = g.chipsTop + Self.chips / 2
        if t >= 4.45 {
            x = g.left + g.chipWidth * 1.5
        } else if t >= M.morphA.1 {
            x = g.trackLeft + (t < M.scrubBack.0 ? 1 : min(max(f.progress, 0), 1)) * g.trackWidth
            y = g.trackY
        }
        return Circle()
            .fill(Color(hex: 0x1F2522, opacity: 0.2))
            .overlay(Circle().strokeBorder(Color.white.opacity(0.75), lineWidth: 1))
            .frame(width: 44, height: 44)
            .scaleEffect(1 - M.ghostPressAt(t) * 0.18)
            .opacity(opacity)
            .offset(x: x - 22, y: y - 22)
            .allowsHitTesting(false)
            .accessibilityHidden(true)
    }

    // MARK: - Touch

    private func chip(_ index: Int) {
        UISelectionFeedbackGenerator().selectionChanged()
        model.chip(index, reduced: reduceMotion)
        revision &+= 1
    }

    private func scrub(_ g: Geo) -> some Gesture {
        DragGesture(minimumDistance: 0, coordinateSpace: .local)
            .onChanged { v in
                let value = M.rubber((v.location.x - Self.thumb / 2) / g.trackWidth)
                if model.scrub(value, velocity: v.velocity.width / g.trackWidth) {
                    UISelectionFeedbackGenerator().selectionChanged()
                }
                revision &+= 1
            }
            .onEnded { _ in
                model.release(reduced: reduceMotion)
                revision &+= 1
            }
    }
}

/// The page and stats, measured once per size and kept across frames.
final class GlyphReflowEditCache {
    private var key = ""
    private var built: GlyphReflowEditPage?
    private var statsKey = ""
    private var builtStats: (words: [String], read: [String], change: [String])?

    func page(original: String, versions: [String], textWidth: Double, textRoom: Double) -> GlyphReflowEditPage {
        let k = "\(textWidth)|\(textRoom)|\(original)|\(versions.joined(separator: "|"))"
        if let built, k == key { return built }
        let page = GlyphReflowEditPage(original: original, versions: versions, textWidth: textWidth, textRoom: textRoom)
        key = k
        built = page
        return page
    }

    func stats(original: String, versions: [String]) -> (words: [String], read: [String], change: [String]) {
        let k = original + "|" + versions.joined(separator: "|")
        if let builtStats, k == statsKey { return builtStats }
        let rows = [GlyphReflowEditLayout.stats(original, original)] + versions.map { GlyphReflowEditLayout.stats(original, $0) }
        let s = (rows.map(\.words), rows.map(\.read), rows.map(\.change))
        statsKey = k
        builtStats = s
        return s
    }
}

/// The loop clock, the spring of the bar and the touch state. A plain class read by the timeline: stepping it
/// never invalidates SwiftUI, so no state is written per frame.
final class GlyphReflowEditModel {
    struct Frame {
        let clock: Double
        let progress: Double
        let velocity: Double
        let tone: Int
        let indicator: Double
        let held: Bool
        let auto: Bool
    }

    private typealias M = GlyphReflowEditMotion

    private var last: Double?
    private var clock = 0.0
    private var auto = true
    private var progress = 0.0
    private var velocity = 0.0
    private var target = 0.0
    private var tone = 0
    private var indicator = 0.0
    private var pending = -1
    private var returning = false
    private var touched = false
    private var held = false
    private var idle = 0.0
    private var side = 0

    func reset(reduced: Bool) {
        clock = 0
        auto = true
        returning = false
        pending = -1
        tone = 0
        indicator = 0
        velocity = 0
        target = reduced ? 1 : 0
        progress = reduced ? 1 : 0
        last = nil
    }

    private var frame: Frame {
        Frame(clock: clock, progress: progress, velocity: velocity, tone: tone, indicator: indicator, held: held, auto: auto)
    }

    func step(now: Double, playing: Bool, reduced: Bool) -> Frame {
        let dt = last.map { min(max(now - $0, 0), 0.048) } ?? 0
        last = now
        if reduced || dt <= 0 { return frame }

        if touched {
            touched = false
            idle = 0
            returning = false
            if auto {
                auto = false
                velocity = 0
            }
        }

        if auto {
            guard playing else { return frame }
            var t = clock + dt
            if t >= M.loop { t -= M.loop }
            clock = t
            progress = M.progressAt(t)
            tone = M.toneAt(t)
            indicator = M.toneIndicatorAt(t)
            return frame
        }

        var p = progress, v = velocity
        if !held {
            idle += dt
            if pending >= 0 && abs(p) < 0.015 && abs(v) < 0.6 {
                tone = pending
                pending = -1
                target = 1
            }
            v += (M.stiffness * (target - p) - M.damping * v) * dt
            p += v * dt
            if abs(target - p) < 0.0008 && abs(v) < 0.01 {
                p = target
                v = 0
            }
            velocity = v
            progress = p
            if playing && !returning && idle >= M.resumeAfter {
                returning = true
                pending = -1
                target = 0
            }
        }

        let wanted = returning && abs(p) < 0.015 ? 0 : Double(tone)
        let moved = indicator + (wanted - indicator) * (1 - exp(-dt * 14))
        indicator = abs(wanted - moved) < 0.002 ? wanted : moved

        if returning && p == 0 {
            tone = 0
            if indicator == 0 {
                returning = false
                auto = true
                clock = 0
            }
        }
        return frame
    }

    func chip(_ index: Int, reduced: Bool) {
        if reduced {
            // No glide: the chip swaps the text at once.
            tone = index
            indicator = Double(index)
            progress = 1
            target = 1
            pending = -1
            return
        }
        touched = true
        if tone == index && pending < 0 {
            target = progress > 0.5 ? 0 : 1
        } else {
            pending = index
            target = 0
        }
    }

    /// Returns true when the thumb crosses the middle (a tick).
    func scrub(_ value: Double, velocity v: Double) -> Bool {
        touched = true
        if !held {
            held = true
            pending = -1
            velocity = 0
            side = value > 0.5 ? 1 : 0
            progress = value
            target = value
            return false
        }
        progress = value
        target = value
        velocity = v
        let now = value > 0.5 ? 1 : 0
        guard now != side else { return false }
        side = now
        return true
    }

    func release(reduced: Bool) {
        held = false
        idle = 0
        if reduced {
            let settled = min(max(progress, 0), 1)
            target = settled
            progress = settled
            velocity = 0
            return
        }
        target = progress + velocity * 0.16 > 0.5 ? 1 : 0
    }
}
