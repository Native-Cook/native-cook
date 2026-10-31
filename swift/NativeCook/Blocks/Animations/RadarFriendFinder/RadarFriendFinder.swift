import CoreText
import SwiftUI
import UIKit

// RN reference: a 168 pt radar at 47% height, two linear turns per 6 s loop, 60 degree wake.
// Maya locks over 3.4–3.9 s and leaves over 5.4–5.9 s. Avatar: ease-out-back(k=1.6).
// Dashed line, card opacity and 24 pt lift: ease-out-cubic(lock).
// Drag steers; release within .52 rad locks with one light haptic. The lock enters in .4 s,
// holds until 2 s after release and fades for .4 s. The beam drifts at quarter speed throughout
// those 2.4 s, then autoplay resumes from its bearing. Reduced motion freezes the RN state.
struct RadarFriendFinder: View {
    static let loop: Double = 6
    var brand = "beacon"
    var title = "Find your\ncrew"
    var action = "Walk me there"
    var autoPlay = false

    private typealias M = RadarFriendFinderMotion
    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scene
    @GestureState private var fingerDown = false
    @State private var appeared = false
    @State private var epoch = Date()
    @State private var accumulated = 0.0
    @State private var loopOrigin = 0.0
    @State private var manual = false
    @State private var touching = false
    @State private var touchBegan = 0.0
    @State private var touchLock = 0.0
    @State private var heldBeam = -Double.pi / 2
    @State private var locked = 0
    @State private var lastNear = -1
    @State private var released = 0.0
    @State private var releaseBeam = -Double.pi / 2
    @State private var touches = 0
    @State private var retained = M.Frame.rest
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    private func elapsed(_ date: Date, ignoringReduced: Bool = false) -> Double {
        accumulated + (appeared && scene == .active && (!reduced || ignoringReduced) ? max(0, date.timeIntervalSince(epoch)) : 0)
    }
    private func frame(_ date: Date, ignoringReduced: Bool = false) -> M.Frame {
        if reduced && !ignoringReduced { return retained }
        let now = elapsed(date, ignoringReduced: ignoringReduced)
        if touching {
            return .init(beam: heldBeam, lock: max(0, touchLock - 4 * (now - touchBegan)), locked: locked)
        }
        if manual {
            let since = max(0, now - released)
            if since <= M.manualDuration {
                let amount = lastNear >= 0 ? (since < M.hold ? M.clamp(since / M.fade) : 1 - M.clamp((since - M.hold) / M.fade)) : 0
                return .init(beam: releaseBeam + M.speed * 0.25 * since, lock: amount, locked: locked)
            }
            // Continuation is derived even if the settling task wakes one frame late.
            return M.automatic(now - resumedOrigin(), autoPlay: autoPlay)
        }
        return M.automatic(now - loopOrigin, autoPlay: autoPlay)
    }
    private func resumedOrigin() -> Double {
        let endBeam = releaseBeam + M.speed * 0.25 * M.manualDuration
        // Preserve JS's signed remainder for negative bearings.
        let turn = ((endBeam + .pi / 2) / M.tau).truncatingRemainder(dividingBy: 1)
        return released + M.manualDuration - turn * M.turn
    }

    var body: some View {
        GeometryReader { geometry in
            let safe = geometry.safeAreaInsets
            let size = CGSize(width: geometry.size.width + safe.leading + safe.trailing,
                              height: geometry.size.height + safe.top + safe.bottom)
            let footerHeight = safe.bottom + 168
            TimelineView(.animation(paused: !appeared || scene != .active || reduced || (!autoPlay && !manual))) { timeline in
                let f = frame(timeline.date)
                Canvas(opaque: true, colorMode: .nonLinear) { context, _ in
                    RadarFriendFinderArt.draw(context, size: size, safe: safe, frame: f, brand: brand, title: title, action: action)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(brand). \(title). Radar of nearby friends. Drag round the scope to steer the beam.")
                .overlay(alignment: .bottom) {
                    Color.clear.frame(height: 58)
                        .padding(.horizontal, 20).padding(.bottom, safe.bottom + 14)
                        .accessibilityLabel(action).accessibilityAddTraits(.isButton).allowsHitTesting(false)
                }
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .contentShape(Path { path in
                path.addRect(CGRect(origin: .zero, size: size))
                // The RN footer sits above and outside the scene's GestureDetector.
                path.addRect(CGRect(x: 20, y: size.height - footerHeight, width: size.width - 40, height: footerHeight))
            }, eoFill: true)
            .gesture(pan(size: size), including: reduced ? .none : .all)
            .ignoresSafeArea()
        }
        .background(M.color(0x06110C).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            RadarFriendFinderArt.prepare()
            epoch = .now
            appeared = true
            haptic.prepare()
        }
        .onDisappear { accumulated = elapsed(.now); appeared = false }
        .onChange(of: scene) { old, _ in
            let now = Date()
            if old == .active && !reduced { accumulated += max(0, now.timeIntervalSince(epoch)) }
            epoch = now
        }
        .onChange(of: reduced) { _, new in
            let now = Date()
            if new { retained = frame(now, ignoringReduced: true); accumulated = elapsed(now, ignoringReduced: true) }
            epoch = now
        }
        .onChange(of: fingerDown) { _, down in
            if !down && touching {
                Task { @MainActor in
                    await Task.yield()
                    if !fingerDown && touching { finishPan() }
                }
            }
        }
        .task(id: touches) {
            guard manual, !touching else { return }
            // RN freezes elapsed time when inactive or reduced.
            while elapsed(.now) - released < M.manualDuration {
                let remaining = M.manualDuration - (elapsed(.now) - released)
                do { try await Task.sleep(for: .seconds(min(0.1, remaining))) } catch { return }
            }
            guard !Task.isCancelled, !touching else { return }
            loopOrigin = resumedOrigin(); locked = 0; manual = false
        }
    }

    private func pan(size: CGSize) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($fingerDown) { _, down, _ in down = true }
            .onChanged { value in
                guard !reduced else { return }
                let date = Date()
                if !touching {
                    let current = frame(date)
                    touchLock = current.lock; locked = current.locked; touchBegan = elapsed(date)
                    manual = true; touching = true; touches += 1
                }
                heldBeam = atan2(value.location.y - size.height * 0.47, value.location.x - size.width / 2)
            }
            .onEnded { _ in finishPan() }
    }
    private func finishPan() {
        guard touching else { return }
        touching = false
        var best = -1, bestDistance = 0.52
        for (index, friend) in M.friends.enumerated() {
            let wrapped = ((heldBeam - friend.bearing).truncatingRemainder(dividingBy: M.tau) + .pi * 3)
                .truncatingRemainder(dividingBy: M.tau) - .pi
            let distance = min(abs(wrapped), M.tau - abs(wrapped))
            if distance < bestDistance { best = index; bestDistance = distance }
        }
        lastNear = best
        if best >= 0 { locked = best; haptic.impactOccurred(); haptic.prepare() }
        releaseBeam = heldBeam; released = elapsed(.now); touches += 1
    }
}

private enum RadarFriendFinderMotion {
    static let tau = Double.pi * 2, turn = 3.0, scope = 168.0, hold = 2.0, fade = 0.4
    static let speed = tau / turn, manualDuration = hold + fade
    static let green: UInt32 = 0x2BFF88
    struct Friend {
        var initials: String, name: String
        var bearing: Double, range: Double
        var meters: Int
        var color: UInt32
    }
    static let friends = [
        Friend(initials: "MA", name: "Maya", bearing: 0.95, range: 0.55, meters: 120, color: 0xFF7AB6),
        Friend(initials: "JO", name: "Jonah", bearing: 2.4, range: 0.78, meters: 260, color: 0x7CC8FF),
        Friend(initials: "RI", name: "Rina", bearing: 3.3, range: 0.4, meters: 90, color: 0xFFD45C),
        Friend(initials: "TE", name: "Teo", bearing: 4.25, range: 0.86, meters: 310, color: 0xB79CFF),
        Friend(initials: "SA", name: "Sami", bearing: 5.2, range: 0.62, meters: 180, color: 0x7CFFB2),
        Friend(initials: "LU", name: "Luz", bearing: 0.15, range: 0.9, meters: 340, color: 0xFF9E6B)
    ]
    struct Frame {
        var beam: Double, lock: Double
        var locked: Int
        static let rest = Frame(beam: -.pi / 2, lock: 0, locked: 0)
    }
    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func segment(_ t: Double, _ a: Double, _ b: Double) -> Double { clamp((t - a) / (b - a)) }
    static func cubic(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func back(_ x: Double) -> Double { 1 + 2.6 * pow(x - 1, 3) + 1.6 * pow(x - 1, 2) }
    static func automatic(_ elapsed: Double, autoPlay: Bool) -> Frame {
        guard autoPlay else { return .rest }
        let t = elapsed.truncatingRemainder(dividingBy: RadarFriendFinder.loop)
        return .init(beam: -.pi / 2 + speed * t, lock: segment(t, 3.4, 3.9) * (1 - segment(t, 5.4, 5.9)), locked: 0)
    }
    static func color(_ hex: UInt32, alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
              blue: Double(hex & 255) / 255, opacity: clamp(alpha))
    }
}

private enum RadarFriendFinderArt {
    private typealias M = RadarFriendFinderMotion
    private typealias T = RadarFriendFinderType
    private static let cardName = T.text("Maya is near the main stage", size: 15, bold: true, hex: 0xE8FFF2)
    private static let cardMeta = T.text("120 m · south-east · 2 min walk", size: 13, hex: 0xE8FFF2, alpha: 0.6)
    private static let cardInitials = T.text("MA", size: 15, bold: true, hex: 0x06110C)

    static func prepare() {
        // Rasterize static copy before the clock starts, rather than on the first lock frame.
        _ = cardName
        _ = cardMeta
        _ = cardInitials
        for friend in M.friends {
            _ = T.glyph(friend.initials, size: 13, hex: M.green)
            _ = T.glyph(friend.initials, size: 13, hex: 0x06110C)
        }
    }
    private static func circle(_ x: Double, _ y: Double, _ radius: Double) -> Path {
        Path(ellipseIn: CGRect(x: x - radius, y: y - radius, width: radius * 2, height: radius * 2))
    }
    static func draw(_ original: GraphicsContext, size: CGSize, safe: EdgeInsets,
                     frame f: RadarFriendFinderMotion.Frame, brand: String, title: String, action: String) {
        var c = original
        let w = size.width, h = size.height, cx = w / 2, cy = h * 0.47
        c.fill(Path(CGRect(origin: .zero, size: size)), with: .color(M.color(0x06110C)))
        let top = safe.top + 8
        let brandText = T.text(brand, size: 20, bold: true, hex: M.green)
        brandText.draw(&c, x: 24, y: top + (34 - brandText.size.height) / 2)
        let live = T.text("● LIVE · 6 friends", size: 12, hex: M.green, alpha: 0.7)
        live.draw(&c, x: w - 24 - live.size.width, y: top + (34 - live.size.height) / 2)
        for (index, line) in title.components(separatedBy: "\n").enumerated() {
            T.text(line, size: 34, bold: true, hex: 0xE8FFF2, tracking: -1, lineHeight: 40)
                .draw(&c, x: 24, y: top + 48 + Double(index) * 40)
        }
        scope(&c, cx: cx, cy: cy, frame: f)

        let footerTop = h - safe.bottom - 168
        var search = c
        search.opacity = 1 - M.clamp(f.lock * 2)
        let searching = T.text("Scanning 400 m around you…", size: 14, hex: M.green, alpha: 0.75)
        searching.draw(&search, x: cx - searching.size.width / 2, y: footerTop + (84 - searching.size.height) / 2)
        let amount = M.cubic(M.clamp(f.lock))
        if amount > 0 {
            // Animated.View fades the composed card, including overlapping background/avatar.
            var cardLayer = c
            cardLayer.opacity = amount
            cardLayer.drawLayer { card in
                card.opacity = 1
                // The RN footer always describes Maya, even when a different friend is selected.
                let target = M.friends[0]
                let name = cardName
                let meta = cardMeta
                let cardHeight = max(44, name.size.height + 3 + meta.size.height) + 30
                let y = footerTop + (84 - cardHeight) / 2 + 24 * (1 - amount)
                let rect = CGRect(x: 20, y: y, width: w - 40, height: cardHeight)
                card.fill(Path(roundedRect: rect, cornerRadius: 22), with: .color(M.color(0x0F2A1D)))
                card.stroke(Path(roundedRect: rect.insetBy(dx: 0.5, dy: 0.5), cornerRadius: 21.5),
                            with: .color(M.color(M.green, alpha: 0.25)), lineWidth: 1)
                let avatarX = 57.0, avatarY = y + cardHeight / 2
                card.fill(circle(avatarX, avatarY, 22), with: .color(M.color(target.color)))
                let initials = cardInitials
                initials.draw(&card, x: avatarX - initials.size.width / 2, y: avatarY - initials.size.height / 2)
                let copyY = y + (cardHeight - name.size.height - 3 - meta.size.height) / 2
                name.draw(&card, x: 93, y: copyY)
                meta.draw(&card, x: 93, y: copyY + name.size.height + 3)
            }
        }
        let button = CGRect(x: 20, y: footerTop + 96, width: w - 40, height: 58)
        c.fill(Path(roundedRect: button, cornerRadius: 29), with: .color(M.color(M.green)))
        let buttonText = T.text(action, size: 16, bold: true, hex: 0x06110C)
        buttonText.draw(&c, x: cx - buttonText.size.width / 2, y: button.midY - buttonText.size.height / 2)
    }
    private static func scope(_ c: inout GraphicsContext, cx: Double, cy: Double, frame f: M.Frame) {
        let face = circle(cx, cy, M.scope)
        c.fill(face, with: .radialGradient(Gradient(colors: [M.color(0x0E2A1C), M.color(0x081A11)]),
                                          center: CGPoint(x: cx, y: cy), startRadius: 0, endRadius: M.scope))
        for i in 1...4 {
            c.stroke(circle(cx, cy, M.scope * Double(i) / 4), with: .color(M.color(M.green, alpha: 0.18)), lineWidth: 1)
        }
        var cross = Path()
        cross.move(to: CGPoint(x: cx - M.scope, y: cy)); cross.addLine(to: CGPoint(x: cx + M.scope, y: cy))
        cross.move(to: CGPoint(x: cx, y: cy - M.scope)); cross.addLine(to: CGPoint(x: cx, y: cy + M.scope))
        c.stroke(cross, with: .color(M.color(M.green, alpha: 0.18)), lineWidth: 1)
        c.fill(face, with: .conicGradient(Gradient(stops: [
            .init(color: M.color(M.green, alpha: 0), location: 0),
            .init(color: M.color(M.green, alpha: 0), location: 0.833),
            .init(color: M.color(M.green, alpha: 0.42), location: 1)
        ]), center: CGPoint(x: cx, y: cy), angle: .radians(f.beam)))
        var edge = Path()
        edge.move(to: CGPoint(x: cx, y: cy))
        edge.addLine(to: CGPoint(x: cx + cos(f.beam) * M.scope, y: cy + sin(f.beam) * M.scope))
        c.stroke(edge, with: .color(M.color(M.green, alpha: 0.95)), lineWidth: 2)
        for (i, friend) in M.friends.enumerated() {
            let x = cx + cos(friend.bearing) * friend.range * M.scope
            let y = cy + sin(friend.bearing) * friend.range * M.scope
            let delta = ((f.beam - friend.bearing).truncatingRemainder(dividingBy: M.tau) + M.tau).truncatingRemainder(dividingBy: M.tau)
            let glow = exp(-2.2 * delta / M.tau * M.turn), lock = i == f.locked ? f.lock : 0
            halo(&c, x: x, y: y, alpha: 0.35 * glow)
            c.fill(circle(x, y, 4 + 2 * glow), with: .color(M.color(M.green, alpha: 0.35 + 0.65 * glow)))
            if lock > 0 {
                let scale = M.back(M.clamp(lock))
                var line = Path()
                line.move(to: CGPoint(x: cx, y: cy))
                line.addLine(to: CGPoint(x: cx + (x - cx) * M.cubic(lock), y: cy + (y - cy) * M.cubic(lock)))
                c.stroke(line, with: .color(M.color(friend.color, alpha: 0.8 * lock)), style: StrokeStyle(lineWidth: 1.5, dash: [5, 5]))
                c.stroke(circle(x, y, 26 * scale + 6 * sin(f.beam * 4) * lock), with: .color(M.color(friend.color, alpha: lock)), lineWidth: 2)
                c.fill(circle(x, y, 20 * scale), with: .color(M.color(friend.color)))
                var letters = c
                letters.translateBy(x: x - 9 * scale, y: y + 4.5 * scale)
                letters.scaleBy(x: scale, y: scale)
                T.glyph(friend.initials, size: 13, hex: 0x06110C).draw(&letters, x: 0, baseline: 0)
            } else {
                var letters = c
                letters.opacity = 0.4 + 0.6 * glow
                T.glyph(friend.initials, size: 13, hex: M.green).draw(&letters, x: x + 9, baseline: y - 6)
            }
        }
        c.fill(circle(cx, cy, 5), with: .color(M.color(M.green)))
    }
    private static func halo(_ c: inout GraphicsContext, x: Double, y: Double, alpha: Double) {
        // Skia BlurStyle.Solid keeps the 10 pt disc solid and blurs only outside it.
        var outside = c
        var clip = Path(CGRect(x: x - 40, y: y - 40, width: 80, height: 80))
        clip.addPath(circle(x, y, 10))
        outside.clip(to: clip, style: FillStyle(eoFill: true))
        outside.drawLayer { layer in
            layer.addFilter(.blur(radius: 8))
            layer.fill(circle(x, y, 10), with: .color(M.color(M.green, alpha: alpha)))
        }
        c.fill(circle(x, y, 10), with: .color(M.color(M.green, alpha: alpha)))
    }
}

// Cache TextKit line boxes for RN's UIKit metrics and baseline glyphs for Skia's drawText.
private enum RadarFriendFinderType {
    struct Sprite {
        let image: Image, size: CGSize
        let baseline: CGFloat, left: CGFloat
        func draw(_ c: inout GraphicsContext, x: CGFloat, y: CGFloat) {
            c.draw(image, in: CGRect(x: x - left, y: y, width: size.width, height: size.height))
        }
        func draw(_ c: inout GraphicsContext, x: CGFloat, baseline y: CGFloat) { draw(&c, x: x, y: y - baseline) }
    }
    static let registered: Void = {
        for weight in ["Bold", "Medium"] {
            if let url = Bundle.main.url(forResource: "RadarFriendFinder-JetBrainsMono-\(weight)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()
    private static var cache: [String: Sprite] = [:]
    private static func font(_ size: CGFloat, bold: Bool) -> UIFont {
        _ = registered
        return UIFont(name: "JetBrainsMono-\(bold ? "Bold" : "Medium")", size: size)
            ?? .monospacedSystemFont(ofSize: size, weight: bold ? .bold : .medium)
    }
    private static func ink(_ hex: UInt32, _ alpha: Double) -> UIColor {
        UIColor(red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, alpha: alpha)
    }
    static func text(_ string: String, size: CGFloat, bold: Bool = false, hex: UInt32,
                     alpha: Double = 1, tracking: CGFloat = 0, lineHeight: CGFloat? = nil) -> Sprite {
        let key = "text|\(string)|\(size)|\(bold)|\(hex)|\(alpha)|\(tracking)|\(lineHeight ?? 0)"
        if let hit = cache[key] { return hit }
        let face = font(size, bold: bold)
        var attributes: [NSAttributedString.Key: Any] = [.font: face, .foregroundColor: ink(hex, alpha)]
        if tracking != 0 { attributes[.kern] = tracking }
        if let lineHeight {
            let paragraph = NSMutableParagraphStyle()
            paragraph.minimumLineHeight = lineHeight; paragraph.maximumLineHeight = lineHeight
            attributes[.paragraphStyle] = paragraph
            if lineHeight >= face.lineHeight { attributes[.baselineOffset] = (lineHeight - face.lineHeight) / 2 }
        }
        let storage = NSTextStorage(string: string, attributes: attributes), layout = NSLayoutManager()
        layout.usesFontLeading = false
        let container = NSTextContainer(size: CGSize(width: 2000, height: 200))
        container.lineFragmentPadding = 0; container.maximumNumberOfLines = 1; container.lineBreakMode = .byClipping
        layout.addTextContainer(container); storage.addLayoutManager(layout)
        let range = layout.glyphRange(for: container), bounds = layout.usedRect(for: container)
        let format = UIGraphicsImageRendererFormat(); format.scale = UIScreen.main.scale
        let dimensions = CGSize(width: max(1, ceil(bounds.width * format.scale) / format.scale), height: max(1, ceil(bounds.height * format.scale) / format.scale))
        let ui = UIGraphicsImageRenderer(size: dimensions, format: format).image { _ in layout.drawGlyphs(forGlyphRange: range, at: .zero) }
        let sprite = Sprite(image: Image(uiImage: ui), size: dimensions, baseline: string.isEmpty ? 0 : layout.location(forGlyphAt: 0).y, left: 0)
        cache[key] = sprite
        return sprite
    }
    static func glyph(_ string: String, size: CGFloat, hex: UInt32) -> Sprite {
        let key = "glyph|\(string)|\(size)|\(hex)"
        if let hit = cache[key] { return hit }
        let face = font(size, bold: true)
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: string, attributes: [.font: face, .foregroundColor: ink(hex, 1)]))
        let padding: CGFloat = 2, width = CGFloat(CTLineGetTypographicBounds(line, nil, nil, nil))
        let dimensions = CGSize(width: ceil(width) + padding * 2, height: ceil(face.ascender - face.descender) + padding * 2)
        let baseline = padding + face.ascender
        let format = UIGraphicsImageRendererFormat(); format.scale = UIScreen.main.scale
        let ui = UIGraphicsImageRenderer(size: dimensions, format: format).image { renderer in
            let context = renderer.cgContext
            context.translateBy(x: padding, y: baseline); context.scaleBy(x: 1, y: -1)
            context.textPosition = .zero
            CTLineDraw(line, context)
        }
        let sprite = Sprite(image: Image(uiImage: ui), size: dimensions, baseline: baseline, left: padding)
        cache[key] = sprite
        return sprite
    }
}

#Preview { RadarFriendFinder(autoPlay: true) }
