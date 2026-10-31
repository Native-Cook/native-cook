import CoreText
import ImageIO
import SwiftUI
import UIKit

/// Glaze & Co.'s flavour carousel, ported from the React Native reference.
/// Three 2.4 s stops make one 7.2 s loop. Each stop holds for 1.6 s, then rolls
/// to the next doughnut with an 0.8 s ease-in-out cubic and a damped landing squash.
/// Swipe 215 pt per flavour; release snaps in 0.4 s, holds for 2 s, then resumes
/// from that flavour at 1 s into its stop. Reduced motion keeps the first flavour still.
struct DonutFlavourPicker: View {
    static let loop: Double = 7.2

    var brand = "Glaze & Co."
    var action = "Add to box"
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reducedMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var origin = Date()
    @State private var pausedAt: Date?
    @State private var pausedFrame = DonutFlavourPickerMotion.Frame(index: 0, landed: 1)
    @GestureState private var gestureIsDown = false
    @State private var touching = false
    @State private var dragActivated = false
    @State private var manual = false
    @State private var grabbed = 0.0
    @State private var dragged = 0.0
    @State private var snapFrom = 0.0
    @State private var snapTo = 0.0
    @State private var released: Date?
    @State private var restingIndex = 0.0
    @State private var lastStep = 0
    @State private var touchRevision = 0
    @State private var haptic = UISelectionFeedbackGenerator()

    private typealias M = DonutFlavourPickerMotion
    private typealias A = DonutFlavourPickerAssets
    private typealias T = DonutFlavourPickerType

    var body: some View {
        GeometryReader { outer in
            let safe = outer.safeAreaInsets
            GeometryReader { geometry in
                TimelineView(.animation(paused: reducedMotion || scenePhase != .active || (!autoPlay && !manual) || touching)) { timeline in
                    let frame = frame(at: pausedAt ?? timeline.date)
                    Canvas { context, size in
                        draw(&context, size: size, safe: safe, frame: frame)
                    }
                    .frame(width: geometry.size.width, height: geometry.size.height, alignment: .topLeading)
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("Doughnut flavours. Swipe to roll to the next one.")
                    .accessibilityValue(A.flavours[M.wrap(M.round(frame.index))].name)
                }
                .contentShape(Rectangle())
                .gesture(drag(height: geometry.size.height, safeBottom: safe.bottom))
                .overlay(alignment: .bottomTrailing) {
                    // The reference's action is a display-only button, exposed to accessibility.
                    Color.clear
                        .frame(width: max(0, geometry.size.width - 188), height: 58)
                        .contentShape(Capsule())
                        .accessibilityLabel(action)
                        .accessibilityAddTraits(.isButton)
                        .padding(.trailing, 24)
                        .padding(.bottom, safe.bottom + 14)
                }
            }
            .ignoresSafeArea()
        }
        .background(M.color(A.flavours[0].tint).ignoresSafeArea())
        .preferredColorScheme(.light)
        .onAppear {
            origin = .now
            haptic.prepare()
            updatePause()
        }
        .onChange(of: scenePhase) { _, _ in updatePause() }
        .onChange(of: reducedMotion) { _, _ in updatePause() }
        .onChange(of: gestureIsDown) { _, isDown in
            // GestureState also resets on cancellation, mirroring RN's onFinalize.
            if !isDown { finishTouch() }
        }
        .task(id: touchRevision) { await resumeAfterTouch() }
    }

    private func frame(at date: Date) -> M.Frame {
        reducedMotion ? pausedFrame : liveFrame(at: date)
    }

    private func liveFrame(at date: Date) -> M.Frame {
        if touching { return .init(index: dragged, landed: 1) }
        if manual, let released {
            let since = max(0, date.timeIntervalSince(released))
            if since < M.snap {
                return .init(index: snapFrom + (snapTo - snapFrom) * M.ease(since / M.snap), landed: 1)
            }
            if since <= M.snap + M.hold || !autoPlay {
                return .init(index: snapTo, landed: since - M.snap)
            }
            // Also derive the resumed phase here: a delayed task cannot freeze the loop.
            return M.carousel(M.mod(snapTo, 3) * M.stop + 1 + since - M.snap - M.hold)
        }
        return autoPlay ? M.carousel(max(0, date.timeIntervalSince(origin))) : .init(index: restingIndex, landed: 1)
    }

    private func drag(height: CGFloat, safeBottom: CGFloat) -> some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($gestureIsDown) { _, isDown, _ in isDown = true }
            .onChanged { value in
                guard !reducedMotion else { return }
                // RN's footer sits above its full-screen pan detector.
                let footerTop = height - safeBottom - 14 - 58 - 12 - 96
                guard value.startLocation.y < footerTop else { return }
                if !touching {
                    let current = frame(at: .now).index
                    touching = true
                    manual = true
                    grabbed = current
                    dragged = current
                    dragActivated = false
                    lastStep = M.round(current)
                    released = nil
                    touchRevision += 1
                }
                // Freeze on touch-down; RN starts updating only after its 4 pt threshold.
                if !dragActivated {
                    guard hypot(value.translation.width, value.translation.height) >= 4 else { return }
                    dragActivated = true
                }
                let index = grabbed - Double(value.translation.width) / M.gap
                dragged = index
                if M.round(index) != lastStep {
                    lastStep = M.round(index)
                    haptic.selectionChanged()
                    haptic.prepare()
                }
            }
            .onEnded { _ in finishTouch() }
    }

    private func finishTouch() {
        guard touching else { return }
        touching = false
        snapFrom = dragged
        snapTo = Double(M.round(dragged))
        released = pausedAt ?? .now
        touchRevision += 1
    }

    private func updatePause() {
        if reducedMotion || scenePhase != .active {
            if pausedAt == nil {
                let now = Date()
                pausedFrame = liveFrame(at: now)
                pausedAt = now
                touchRevision += 1
            }
        } else if let pausedAt {
            let pause = Date().timeIntervalSince(pausedAt)
            origin = origin.addingTimeInterval(pause)
            if let released { self.released = released.addingTimeInterval(pause) }
            self.pausedAt = nil
            touchRevision += 1
        }
    }

    private func resumeAfterTouch() async {
        guard manual, !touching, pausedAt == nil, let released else { return }
        let remaining = max(0, M.snap + M.hold - Date().timeIntervalSince(released))
        try? await Task.sleep(for: .seconds(remaining))
        guard !Task.isCancelled, !touching, scenePhase == .active, !reducedMotion else { return }
        restingIndex = snapTo
        origin = released.addingTimeInterval(M.snap + M.hold - M.mod(snapTo, 3) * M.stop - 1)
        manual = false
    }

    private func draw(_ context: inout GraphicsContext, size: CGSize, safe: EdgeInsets, frame: M.Frame) {
        let width = size.width
        let cy = size.height * 0.43
        let base = Int(floor(frame.index))
        let fraction = frame.index - floor(frame.index)
        let tint = M.mix(A.flavours[M.wrap(base)].tint, A.flavours[M.wrap(base + 1)].tint, fraction)
        let deep = M.mix(A.flavours[M.wrap(base)].deep, A.flavours[M.wrap(base + 1)].deep, fraction)
        context.fill(Path(CGRect(origin: .zero, size: size)), with: .color(tint))

        for k in A.flavours.indices {
            let r = M.offset(k, frame.index)
            var wordContext = context
            wordContext.opacity = 0.16 * (1 - M.clamp(abs(r) * 1.4))
            let word = T.text(A.flavours[k].word, size: 168, face: .italic, hex: A.flavours[k].deep, lineHeight: 200)
            word.draw(&wordContext, x: (width - word.size.width) / 2 + r * 140, y: cy - 100)
        }

        let title = T.text("Doughnuts", size: 30, face: .italic)
        // RN constrains this Text to its 34 pt header row, shorter than the font's
        // natural 39 pt line box. Preserve that placement and the row's clipping.
        var titleContext = context
        titleContext.clip(to: Path(CGRect(x: 24, y: safe.top + 8, width: width - 48, height: 34)))
        title.draw(&titleContext, x: 24, y: safe.top + 8)
        let brandText = T.text(brand, size: 14, weight: .bold, alpha: 0.6)
        brandText.draw(&context, x: width - 24 - brandText.size.width, y: safe.top + 8 + (34 - brandText.size.height) / 2)

        drawDonuts(&context, index: frame.index, landed: frame.landed, cx: width / 2, cy: cy)

        let footerTop = size.height - safe.bottom - 14 - 58 - 12 - 96
        for k in A.flavours.indices {
            let f = A.flavours[k]
            let opacity = 1 - M.smooth(abs(M.offset(k, frame.index)) / 0.5)
            var info = context
            info.opacity = opacity
            let y = footerTop + 10 * (1 - opacity)
            let name = T.text(f.name, size: 38, face: .regular, tracking: -0.5)
            let price = T.text(f.price, size: 20, weight: .heavy, hex: f.deep)
            name.draw(&info, x: 24, y: y)
            let priceTop = name.baseline - price.baseline
            price.draw(&info, x: width - 24 - price.size.width, y: y + priceTop)
            let notes = T.text(f.notes, size: 15, alpha: 0.65, lineHeight: 21, maxWidth: min(300, width - 48), lines: 0)
            notes.draw(&info, x: 24, y: y + max(name.size.height, priceTop + price.size.height) + 4)
        }

        let actionY = footerTop + 96 + 12
        context.fill(Path(roundedRect: CGRect(x: 24, y: actionY, width: 128, height: 58), cornerRadius: 29), with: .color(.white.opacity(0.7)))
        let minus = T.text("−", size: 22, weight: .semibold)
        let count = T.text("2", size: 18, weight: .heavy)
        let plus = T.text("+", size: 22, weight: .semibold)
        let items = [minus, count, plus]
        let space = (128 - items.reduce(CGFloat(0)) { $0 + $1.size.width }) / 3
        var x = 24 + space / 2
        for item in items {
            item.draw(&context, x: x, y: actionY + (58 - item.size.height) / 2)
            x += item.size.width + space
        }
        let button = CGRect(x: 164, y: actionY, width: width - 188, height: 58)
        context.fill(Path(roundedRect: button, cornerRadius: 29), with: .color(deep))
        let label = T.text(action, size: 17, weight: .bold, hex: 0xFFFFFF)
        label.draw(&context, x: button.midX - label.size.width / 2, y: button.midY - label.size.height / 2)
    }

    private func drawDonuts(_ context: inout GraphicsContext, index: Double, landed: Double, cx: CGFloat, cy: CGFloat) {
        let order = A.flavours.indices.sorted { abs(M.offset($0, index)) > abs(M.offset($1, index)) }
        for j in order {
            guard let image = A.images[j] else { continue }
            let r = M.offset(j, index)
            let x = cx + r * M.gap
            let scale = 1 - 0.5 * min(1, abs(r))
            let side = M.size * scale
            let squash = abs(r) < 0.02 ? M.landing(landed) : 0
            let turn = r * M.gap / (side / 2)

            context.drawLayer { shadow in
                shadow.addFilter(.blur(radius: 12 * scale))
                let rect = CGRect(x: x - side * 0.36, y: cy + side * 0.4, width: side * 0.72, height: side * 0.11)
                shadow.fill(Path(ellipseIn: rect), with: .color(Color(red: 60 / 255, green: 30 / 255, blue: 20 / 255).opacity(0.22)))
            }

            var donut = context
            donut.translateBy(x: x, y: cy + side / 2 * squash)
            donut.scaleBy(x: 1 + squash, y: 1 - squash)
            donut.rotate(by: .radians(turn))
            donut.draw(image, in: CGRect(x: -side / 2, y: -side / 2, width: side, height: side))
        }
    }
}

private enum DonutFlavourPickerMotion {
    static let stop = 2.4
    static let switchAt = 1.6
    static let size = 290.0
    static let gap = 215.0
    static let snap = 0.4
    static let hold = 2.0

    struct Frame {
        var index: Double
        var landed: Double
    }

    static func clamp(_ x: Double) -> Double { min(1, max(0, x)) }
    static func mod(_ x: Double, _ n: Double) -> Double { (x.truncatingRemainder(dividingBy: n) + n).truncatingRemainder(dividingBy: n) }
    static func wrap(_ i: Int) -> Int { ((i % 3) + 3) % 3 }
    /// JavaScript Math.round rounds negative half-integers toward positive infinity.
    static func round(_ x: Double) -> Int { Int(floor(x + 0.5)) }
    static func ease(_ x: Double) -> Double { x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2 }
    static func smooth(_ x: Double) -> Double {
        let u = clamp(x)
        return u * u * (3 - 2 * u)
    }
    static func offset(_ j: Int, _ index: Double) -> Double {
        var r = Double(j) - mod(index, 3)
        if r > 1.5 { r -= 3 }
        if r <= -1.5 { r += 3 }
        return r
    }
    static func landing(_ x: Double) -> Double {
        x <= 0 || x > 0.8 ? 0 : 0.06 * exp(-7 * x) * sin(16 * x)
    }
    static func carousel(_ time: Double) -> Frame {
        let t = mod(time, DonutFlavourPicker.loop)
        let k = floor(t / stop)
        let u = t - k * stop
        return Frame(index: k + ease(clamp((u - switchAt) / (stop - switchAt))), landed: u)
    }
    static func color(_ hex: UInt32, alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255, blue: Double(hex & 255) / 255, opacity: alpha)
    }
    static func mix(_ a: UInt32, _ b: UInt32, _ u: Double) -> Color {
        func channel(_ shift: UInt32) -> Double {
            (Double((a >> shift) & 255) * (1 - u) + Double((b >> shift) & 255) * u).rounded() / 255
        }
        return Color(.sRGB, red: channel(16), green: channel(8), blue: channel(0))
    }
}

private enum DonutFlavourPickerAssets {
    struct Flavour {
        let word: String
        let name: String
        let notes: String
        let price: String
        let tint: UInt32
        let deep: UInt32
    }
    static let flavours = [
        Flavour(word: "Berry", name: "Strawberry glaze", notes: "Brioche dough, fresh strawberry icing, white sprinkles.", price: "$3.20", tint: 0xFBE1E8, deep: 0xB0244F),
        Flavour(word: "Cocoa", name: "Dark chocolate", notes: "Seventy per cent cocoa glaze with gold sugar pearls.", price: "$3.40", tint: 0xF0E2D6, deep: 0x5A2E16),
        Flavour(word: "Matcha", name: "Matcha drizzle", notes: "Ceremonial matcha icing and a white chocolate drizzle.", price: "$3.60", tint: 0xE2EFD8, deep: 0x3D6B2A),
    ]
    /// Decode each bundled PNG once, before it is used by the animation canvas.
    static let images: [Image?] = ["strawberry", "chocolate", "matcha"].map { name in
        guard let url = Bundle.main.url(forResource: "DonutFlavourPicker-donut-" + name, withExtension: "png"),
              let source = CGImageSourceCreateWithURL(url as CFURL, nil),
              let cgImage = CGImageSourceCreateImageAtIndex(source, 0, [kCGImageSourceShouldCacheImmediately: true] as CFDictionary) else { return nil }
        // Skia's drawImageRect uses nearest sampling unless sampling is specified.
        return Image(decorative: cgImage, scale: 1, orientation: .up).interpolation(.none)
    }
}

/// TextKit's line boxes and baselines mirror React Native's iOS text layout.
/// The immutable glyph images are cached; moving text only changes its transform and opacity.
private enum DonutFlavourPickerType {
    enum Face: String { case system, regular, italic }
    struct Sprite {
        let image: Image
        let size: CGSize
        let baseline: CGFloat
        func draw(_ context: inout GraphicsContext, x: CGFloat, y: CGFloat) {
            context.draw(image, in: CGRect(x: x, y: y, width: size.width, height: size.height))
        }
    }
    static let registered: Void = {
        for name in ["Regular", "Italic"] {
            if let url = Bundle.main.url(forResource: "DonutFlavourPicker-InstrumentSerif-" + name, withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()
    private static var cache: [String: Sprite] = [:]

    static func text(_ string: String, size: CGFloat, face: Face = .system, weight: UIFont.Weight = .regular,
                     hex: UInt32 = 0x2A1A14, alpha: Double = 1, tracking: CGFloat = 0,
                     lineHeight: CGFloat? = nil, maxWidth: CGFloat = 2000, lines: Int = 1) -> Sprite {
        _ = registered
        let key = "\(string)|\(size)|\(face.rawValue)|\(weight.rawValue)|\(hex)|\(alpha)|\(tracking)|\(lineHeight ?? 0)|\(maxWidth)|\(lines)"
        if let hit = cache[key] { return hit }
        let font: UIFont
        switch face {
        case .system: font = .systemFont(ofSize: size, weight: weight)
        case .regular: font = UIFont(name: "InstrumentSerif-Regular", size: size) ?? .systemFont(ofSize: size)
        case .italic: font = UIFont(name: "InstrumentSerif-Italic", size: size) ?? .italicSystemFont(ofSize: size)
        }
        let color = UIColor(red: CGFloat((hex >> 16) & 255) / 255, green: CGFloat((hex >> 8) & 255) / 255, blue: CGFloat(hex & 255) / 255, alpha: alpha)
        var attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: color]
        if tracking != 0 { attributes[.kern] = tracking }
        if let lineHeight {
            let paragraph = NSMutableParagraphStyle()
            paragraph.minimumLineHeight = lineHeight
            paragraph.maximumLineHeight = lineHeight
            attributes[.paragraphStyle] = paragraph
            if lineHeight >= font.lineHeight { attributes[.baselineOffset] = (lineHeight - font.lineHeight) / 2 }
        }
        let storage = NSTextStorage(string: string, attributes: attributes)
        let layout = NSLayoutManager()
        layout.usesFontLeading = false
        let container = NSTextContainer(size: CGSize(width: maxWidth, height: 1000))
        container.lineFragmentPadding = 0
        container.maximumNumberOfLines = lines
        container.lineBreakMode = lines == 1 ? .byClipping : .byWordWrapping
        layout.addTextContainer(container)
        storage.addLayoutManager(layout)
        let range = layout.glyphRange(for: container)
        let rect = layout.usedRect(for: container)
        let scale = UIScreen.main.scale
        let dimensions = CGSize(width: max(1, ceil(rect.width * scale) / scale), height: max(1, ceil(rect.height * scale) / scale))
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        let image = UIGraphicsImageRenderer(size: dimensions, format: format).image { _ in
            layout.drawGlyphs(forGlyphRange: range, at: .zero)
        }
        let sprite = Sprite(image: Image(uiImage: image), size: dimensions, baseline: layout.location(forGlyphAt: 0).y)
        cache[key] = sprite
        return sprite
    }
}

#Preview {
    DonutFlavourPicker(autoPlay: true)
}
