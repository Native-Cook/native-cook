import SwiftUI
import UIKit

// The two six-panel strips repeat their first two panels at the end. The right
// strip is reversed, and travels down while the left travels up.
// Loop (8 s): four 2 s steps, each holding for 1.3 s, then moving for 0.7 s
// with ease-in-out cubic. The right column follows 80 ms later.
// Drag: one step per panel height; release snaps for 0.35 s, holds for 2 s,
// then resumes the loop from that step. Reduced motion holds the initial grid.
struct SwissPosterShuffle: View {
    static let loop: Double = 8
    var title = "Typo Week 2026 · Seminar"
    var when = "Monday, 13:30–16:30 · Room H4.07"
    var action = "Save ticket"
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @State private var origin = Date()
    @State private var inactiveAt: Date?
    @State private var dragging = false
    @State private var dragActivated = false
    @GestureState private var gestureActive = false
    @State private var grabbed = 0.0
    @State private var dragged = 0.0
    @State private var lastStep = 0.0
    @State private var releasedAt: Date?
    @State private var releasedFrom = 0.0
    @State private var snapTo = 0.0
    @State private var snapFinished = true
    @State private var haptic = UISelectionFeedbackGenerator()

    var body: some View {
        GeometryReader { geo in
            let safe = geo.safeAreaInsets
            let size = CGSize(width: geo.size.width + safe.leading + safe.trailing,
                              height: geo.size.height + safe.top + safe.bottom)
            let posterTop = safe.top + 8
            let posterHeight = (size.height * 0.68 - posterTop).rounded()
            let panelHeight = posterHeight / 2
            let columnWidth = size.width / 2
            let artwork = SwissPosterShuffleArtwork.make(width: columnWidth, height: panelHeight)
            ZStack(alignment: .topLeading) {
                Color(hex: 0xEFEDE6)
                TimelineView(.animation(paused: reduced || scenePhase != .active || (!autoPlay && !dragging && snapFinished))) { timeline in
                    let steps = steps(at: inactiveAt ?? timeline.date)
                    ZStack(alignment: .topLeading) {
                        column(artwork.left, width: columnWidth, height: panelHeight)
                            .offset(y: -Self.wrap(steps.left) * panelHeight)
                        column(artwork.right, width: columnWidth, height: panelHeight)
                            .offset(x: columnWidth, y: -(4 - Self.wrap(steps.right)) * panelHeight)
                    }
                    .frame(width: size.width, height: posterHeight, alignment: .topLeading)
                    .background(Color(hex: 0x0B0B0B))
                    .clipped()
                }
                .frame(width: size.width, height: posterHeight, alignment: .topLeading)
                .contentShape(Rectangle())
                .gesture(drag(panelHeight: panelHeight))
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("Typo Week poster. Drag to rearrange.")
                .offset(y: posterTop)

                ticket(width: size.width - 48)
                    .position(x: size.width / 2, y: size.height - safe.bottom - 16 - 64)
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .onAppear {
            origin = .now
            haptic.prepare()
        }
        .onChange(of: scenePhase) { _, phase in
            if phase != .active {
                finishDrag()
                if inactiveAt == nil { inactiveAt = .now }
            } else if let paused = inactiveAt {
                let interval = Date().timeIntervalSince(paused)
                origin = origin.addingTimeInterval(interval)
                if let release = releasedAt { releasedAt = release.addingTimeInterval(interval) }
                inactiveAt = nil
            }
        }
        .onChange(of: gestureActive) { _, active in
            // RN finalizes a cancelled pan as well as an ordinary release.
            if !active { finishDrag() }
        }
        .task(id: SwissPosterShuffleSettleKey(release: releasedAt, active: scenePhase == .active)) {
            guard !autoPlay, let release = releasedAt, scenePhase == .active else { return }
            try? await Task.sleep(for: .seconds(max(0, 0.35 - Date().timeIntervalSince(release))))
            guard !Task.isCancelled, !dragging else { return }
            snapFinished = true
        }
    }

    private func column(_ images: [UIImage], width: CGFloat, height: CGFloat) -> some View {
        VStack(spacing: 0) {
            ForEach(images.indices, id: \.self) { i in
                Image(uiImage: images[i])
                    .resizable()
                    .frame(width: width, height: height)
            }
        }
        .frame(width: width, height: height * 6, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    private func ticket(width: CGFloat) -> some View {
        // TextKit gives the same Helvetica Neue baselines and fixed line heights
        // as RN. These static text images are cached, never redrawn per frame.
        VStack(alignment: .leading, spacing: 0) {
            text(title, width: width, height: 30, size: 24, lineHeight: 30, medium: true, color: 0x0B0B0B, tracking: -0.4)
            text(when, width: width, height: 20, size: 15, lineHeight: 20, medium: false, color: 0x6B6A66)
                .padding(.top, 6)
            text(action, width: width, height: 54, size: 17, lineHeight: nil, medium: true, color: 0xFFFFFF, centered: true)
                .background(Color(hex: 0x0B0B0B), in: RoundedRectangle(cornerRadius: 4))
                .padding(.top, 18)
                .accessibilityAddTraits(.isButton)
        }
        .frame(width: width, height: 128, alignment: .topLeading)
    }

    private func text(_ string: String, width: CGFloat, height: CGFloat, size: CGFloat, lineHeight: CGFloat?, medium: Bool, color: UInt32, tracking: CGFloat = 0, centered: Bool = false) -> some View {
        Image(uiImage: SwissPosterShuffleArtwork.text(string, width: width, height: height, size: size, lineHeight: lineHeight, medium: medium, color: color, tracking: tracking, centered: centered))
            .resizable()
            .frame(width: width, height: height)
            .accessibilityLabel(string)
    }

    private func steps(at date: Date) -> (left: Double, right: Double) {
        guard !reduced else { return (0, 0) }
        if dragging { return (dragged, dragged) }
        if let release = releasedAt {
            let since = max(0, date.timeIntervalSince(release))
            if since < 0.35 {
                let step = releasedFrom + (snapTo - releasedFrom) * Self.ease(since / 0.35)
                return (step, step)
            }
            if since <= 2.35 || !autoPlay { return (snapTo, snapTo) }
            let t = Self.wrap(snapTo) * 2 + since - 2.35
            return (Self.step(at: t), Self.step(at: t - 0.08))
        }
        guard autoPlay else { return (0, 0) }
        let t = max(0, date.timeIntervalSince(origin))
        return (Self.step(at: t), Self.step(at: t - 0.08))
    }

    private func drag(panelHeight: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($gestureActive) { _, active, _ in active = true }
            .onChanged { value in
                guard !reduced else { return }
                if !dragging {
                    grabbed = steps(at: .now).left
                    dragged = grabbed
                    lastStep = floor(grabbed + 0.5)
                    dragging = true
                    dragActivated = false
                    releasedAt = nil
                    snapFinished = false
                }
                if !dragActivated {
                    guard hypot(value.translation.width, value.translation.height) >= 4 else { return }
                    dragActivated = true
                }
                dragged = grabbed - value.translation.height / panelHeight
                let nearest = floor(dragged + 0.5)
                if nearest != lastStep {
                    lastStep = nearest
                    haptic.selectionChanged()
                    haptic.prepare()
                }
            }
            .onEnded { _ in
                finishDrag()
            }
    }

    private func finishDrag() {
        guard dragging else { return }
        releasedFrom = dragged
        snapTo = floor(dragged + 0.5)
        releasedAt = .now
        dragging = false
        dragActivated = false
    }

    private static func wrap(_ s: Double) -> Double {
        (s.truncatingRemainder(dividingBy: 4) + 4).truncatingRemainder(dividingBy: 4)
    }

    private static func ease(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    private static func step(at t: Double) -> Double {
        let u = (t.truncatingRemainder(dividingBy: loop) + loop).truncatingRemainder(dividingBy: loop)
        let k = floor(u / 2)
        return k + ease(min(1, max(0, (u - k * 2 - 1.3) / 0.7)))
    }
}

private struct SwissPosterShuffleSettleKey: Equatable {
    let release: Date?
    let active: Bool
}

private enum SwissPosterShuffleArtwork {
    private struct Panel {
        let bg: UInt32
        let fg: UInt32
        let word: String
        let turn: CGFloat
        let size: CGFloat
        let end: Bool
    }

    struct Strips {
        let left: [UIImage]
        let right: [UIImage]
    }

    private static let left = [
        Panel(bg: 0x0B0B0B, fg: 0xF6F5F0, word: "Typo", turn: 0, size: 76, end: true),
        Panel(bg: 0xF6F5F0, fg: 0x0B0B0B, word: "Week", turn: -90, size: 80, end: true),
        Panel(bg: 0xE8261C, fg: 0xF6F5F0, word: "Seminar", turn: 90, size: 60, end: false),
        Panel(bg: 0x0B0B0B, fg: 0xF6F5F0, word: "2026", turn: 180, size: 74, end: true),
    ]
    private static let right = [
        Panel(bg: 0xF6F5F0, fg: 0x0B0B0B, word: "in\nMotion", turn: 0, size: 46, end: false),
        Panel(bg: 0xE8261C, fg: 0xF6F5F0, word: "Monday\n13:30–16:30\nRoom H4.07", turn: 0, size: 19, end: false),
        Panel(bg: 0x0B0B0B, fg: 0xF6F5F0, word: "Typo", turn: 90, size: 76, end: true),
        Panel(bg: 0xF6F5F0, fg: 0x0B0B0B, word: "Week", turn: 180, size: 76, end: false),
    ]
    private static var stripCache: [String: Strips] = [:]
    private static var textCache: [String: UIImage] = [:]

    static func make(width: CGFloat, height: CGFloat) -> Strips {
        let key = "\(width)|\(height)"
        if let cached = stripCache[key] { return cached }
        let l = left.map { panel($0, width: width, height: height) }
        let r = right.map { panel($0, width: width, height: height) }
        let result = Strips(left: l + [l[0], l[1]], right: Array((r + [r[0], r[1]]).reversed()))
        stripCache[key] = result
        return result
    }

    private static func color(_ hex: UInt32) -> UIColor {
        UIColor(red: CGFloat((hex >> 16) & 255) / 255, green: CGFloat((hex >> 8) & 255) / 255, blue: CGFloat(hex & 255) / 255, alpha: 1)
    }

    private static func layout(_ string: String, width: CGFloat, size: CGFloat, lineHeight: CGFloat?, medium: Bool, color hex: UInt32, tracking: CGFloat, lines: Int = 0, centered: Bool = false) -> (NSLayoutManager, NSTextContainer, NSTextStorage) {
        let font = UIFont(name: medium ? "HelveticaNeue-Medium" : "HelveticaNeue", size: size) ?? .systemFont(ofSize: size)
        let paragraph = NSMutableParagraphStyle()
        if let lineHeight {
            paragraph.minimumLineHeight = lineHeight
            paragraph.maximumLineHeight = lineHeight
        }
        paragraph.alignment = centered ? .center : .left
        var attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color(hex), .kern: tracking, .paragraphStyle: paragraph]
        if let lineHeight, lineHeight >= font.lineHeight {
            attributes[.baselineOffset] = (lineHeight - font.lineHeight) / 2
        }
        let storage = NSTextStorage(string: string, attributes: attributes)
        let manager = NSLayoutManager()
        manager.usesFontLeading = false
        let container = NSTextContainer(size: CGSize(width: width, height: .greatestFiniteMagnitude))
        container.lineFragmentPadding = 0
        container.maximumNumberOfLines = lines
        container.lineBreakMode = lines > 0 ? .byTruncatingTail : .byClipping
        manager.addTextContainer(container)
        storage.addLayoutManager(manager)
        manager.ensureLayout(for: container)
        return (manager, container, storage)
    }

    private static func panel(_ p: Panel, width: CGFloat, height: CGFloat) -> UIImage {
        let sideways = abs(p.turn) == 90
        let w = sideways ? height : width
        let h = sideways ? width : height
        let lineHeight = p.size * (p.size > 30 ? 0.95 : 1.2)
        let tracking = p.size > 30 ? -p.size * 0.04 : 0
        let count = p.word.components(separatedBy: "\n").count
        let (manager, container, storage) = layout(p.word, width: w - 20, size: p.size, lineHeight: lineHeight, medium: true, color: p.fg, tracking: tracking, lines: count)
        // Fit only when a glyph would be truncated, retaining RN's fixed line height
        // and tracking as the font size changes.
        let original = storage.copy() as! NSAttributedString
        var lower: CGFloat = 0.02
        var upper: CGFloat = 1
        func fits() -> Bool {
            let glyphs = manager.glyphRange(for: container)
            return glyphs.length > 0 && manager.truncatedGlyphRange(inLineFragmentForGlyphAt: glyphs.location + glyphs.length - 1).location == NSNotFound
        }
        if !fits() {
            for _ in 0..<10 {
                let ratio = (lower + upper) / 2
                storage.setAttributedString(original)
                storage.addAttribute(.font, value: UIFont(name: "HelveticaNeue-Medium", size: max(4, p.size * ratio))!, range: NSRange(location: 0, length: storage.length))
                manager.ensureLayout(for: container)
                if fits() { lower = ratio } else { upper = ratio }
            }
            storage.setAttributedString(original)
            storage.addAttribute(.font, value: UIFont(name: "HelveticaNeue-Medium", size: max(4, p.size * lower))!, range: NSRange(location: 0, length: storage.length))
            manager.ensureLayout(for: container)
        }
        let textHeight = ceil(manager.usedRect(for: container).height * UIScreen.main.scale) / UIScreen.main.scale
        let format = UIGraphicsImageRendererFormat()
        format.opaque = true
        return UIGraphicsImageRenderer(size: CGSize(width: width, height: height), format: format).image { renderer in
            color(p.bg).setFill()
            renderer.fill(CGRect(x: 0, y: 0, width: width, height: height))
            let ctx = renderer.cgContext
            ctx.translateBy(x: width / 2, y: height / 2)
            ctx.rotate(by: p.turn * .pi / 180)
            let origin = CGPoint(x: -w / 2 + 10, y: p.end ? h / 2 - 10 - textHeight : -h / 2 + 10)
            withExtendedLifetime(storage) {
                manager.drawGlyphs(forGlyphRange: manager.glyphRange(for: container), at: origin)
            }
        }
    }

    static func text(_ string: String, width: CGFloat, height: CGFloat, size: CGFloat, lineHeight: CGFloat?, medium: Bool, color: UInt32, tracking: CGFloat, centered: Bool) -> UIImage {
        let key = "\(string)|\(width)|\(height)|\(size)|\(lineHeight ?? 0)|\(medium)|\(color)|\(tracking)|\(centered)"
        if let cached = textCache[key] { return cached }
        let (manager, container, storage) = layout(string, width: width, size: size, lineHeight: lineHeight, medium: medium, color: color, tracking: tracking, centered: centered)
        let textHeight = manager.usedRect(for: container).height
        let result = UIGraphicsImageRenderer(size: CGSize(width: width, height: height)).image { _ in
            withExtendedLifetime(storage) {
                manager.drawGlyphs(forGlyphRange: manager.glyphRange(for: container), at: CGPoint(x: 0, y: centered ? (height - textHeight) / 2 : 0))
            }
        }
        textCache[key] = result
        return result
    }
}

#Preview {
    SwissPosterShuffle(autoPlay: true)
}
