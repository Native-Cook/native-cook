import SwiftUI
import UIKit

// Faithful to RN: a 210 pt coupon, eleven layers through 22 pt of glass, yaw followed by
// pitch, and the same 800 pt perspective projection. Both outward sides show the image.
// 6 s loop: sway throughout; cubic full turn 1.4–3.1; landing wobble 3.1–3.9;
// twelve sparks 3.1–3.8; code shine 4.2–5.0. Pan adds 0.5°/pt yaw, −0.4°/pt pitch.
// Release returns by exp(−5t)cos(10t) for 0.8 s, holds 2 s, then restarts the loop.
struct GlassCouponBadge: View {
    static let loop: Double = 6
    var brand = "Parcel"
    var code = "SPRING25"
    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduced
    @Environment(\.scenePhase) private var scenePhase
    @GestureState private var fingerDown = false
    @State private var epoch = Date()
    @State private var accumulated = 0.0
    @State private var running = false
    @State private var loopOrigin = 0.0
    @State private var manual = false
    @State private var touching = false
    @State private var released: Double?
    @State private var heldClock = 0.0
    @State private var dragYaw = 0.0
    @State private var dragPitch = 0.0
    @State private var previousTranslation = CGSize.zero
    @State private var touchVersion = 0
    @State private var frozen = GlassCouponBadgeMotion.Frame.rest
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)

    var body: some View {
        GeometryReader { geometry in
            let safe = geometry.safeAreaInsets
            let size = CGSize(width: geometry.size.width + safe.leading + safe.trailing,
                              height: geometry.size.height + safe.top + safe.bottom)
            let center = CGPoint(x: size.width / 2, y: size.height * 0.38 + 30)
            let base = GlassCouponBadgeArtwork.base(size: size, safe: safe, brand: brand, code: code)
            TimelineView(.animation(paused: !running || (!autoPlay && !manual))) { timeline in
                let current = reduced ? frozen : frame(at: elapsed(at: timeline.date))
                ZStack(alignment: .topLeading) {
                    Image(uiImage: base)
                        .resizable()
                        .frame(width: size.width, height: size.height)
                        .accessibilityHidden(true)
                    Canvas(colorMode: .nonLinear) { context, _ in
                        GlassCouponBadgeArtwork.effects(context, size: size, safe: safe, center: center, frame: current)
                    }
                    .allowsHitTesting(false)
                    GlassCouponBadgeSlab(frame: current, center: center)
                        .frame(width: size.width, height: size.height)
                        .allowsHitTesting(false)
                    Color.clear
                        .frame(width: 250, height: 250)
                        .contentShape(Rectangle())
                        .gesture(pan, including: reduced ? .none : .all)
                        .accessibilityLabel("Coupon. Drag to turn it.")
                        .accessibilityAddTraits(.isImage)
                        .offset(x: center.x - 125, y: center.y - 125)
                }
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .accessibilityElement(children: .contain)
                .accessibilityLabel("Reward unlocked. 25% off your next order. For hitting five orders this spring. Valid on everything until 31 May. Coupon code \(code). Apply coupon.")
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .ignoresSafeArea()
        }
        .background(GlassCouponBadgeArtwork.color(0xF4F2EF).ignoresSafeArea())
        .preferredColorScheme(.dark)
        .onAppear {
            epoch = .now
            running = scenePhase == .active && !reduced
            haptic.prepare()
        }
        .onDisappear { updateRunning(false) }
        .onChange(of: scenePhase) { _, phase in updateRunning(phase == .active && !reduced) }
        .onChange(of: reduced) { _, value in
            if value { frozen = frame(at: elapsed(at: .now)) }
            updateRunning(scenePhase == .active && !value)
        }
        .onChange(of: fingerDown) { _, down in
            // GestureState resets on cancellation too, matching RN's onFinalize.
            if !down && touching {
                Task { @MainActor in
                    await Task.yield()
                    if !fingerDown && touching { finishPan() }
                }
            }
        }
        .task(id: touchVersion) {
            guard !touching, let released else { return }
            while elapsed(at: .now) <= released + 2.8 {
                do { try await Task.sleep(for: .milliseconds(50)) } catch { return }
            }
            guard !Task.isCancelled, !touching else { return }
            loopOrigin = elapsed(at: .now)
            manual = false
            self.released = nil
        }
    }

    private func elapsed(at date: Date) -> Double {
        accumulated + (running ? max(0, date.timeIntervalSince(epoch)) : 0)
    }
    private func updateRunning(_ value: Bool) {
        let now = Date()
        accumulated = elapsed(at: now)
        epoch = now
        running = value
    }
    private func frame(at now: Double) -> GlassCouponBadgeMotion.Frame {
        if manual {
            let x = released.map { max(0, now - $0) } ?? 0
            let k = touching ? 1 : (x < 0.8 ? exp(-5 * x) * cos(10 * x) : 0)
            return .init(clock: heldClock, yaw: dragYaw * k, pitch: dragPitch * k, scale: 1, sparks: 0)
        }
        return autoPlay ? GlassCouponBadgeMotion.at(now - loopOrigin) : .rest
    }
    private var pan: some Gesture {
        DragGesture(minimumDistance: 0)
            .updating($fingerDown) { _, down, _ in down = true }
            .onChanged { value in
                guard !reduced else { return }
                if !touching {
                    let current = frame(at: elapsed(at: .now))
                    heldClock = current.clock
                    dragYaw = GlassCouponBadgeMotion.shortYaw(current.yaw)
                    dragPitch = current.pitch
                    touching = true
                    manual = true
                    released = nil
                    previousTranslation = .zero
                    touchVersion += 1
                    haptic.impactOccurred()
                    haptic.prepare()
                }
                dragYaw += Double(value.translation.width - previousTranslation.width) * 0.5
                dragPitch = min(50, max(-50, dragPitch - Double(value.translation.height - previousTranslation.height) * 0.4))
                previousTranslation = value.translation
            }
            .onEnded { _ in finishPan() }
    }
    private func finishPan() {
        guard touching else { return }
        touching = false
        dragYaw = GlassCouponBadgeMotion.shortYaw(dragYaw)
        released = elapsed(at: .now)
        touchVersion += 1
    }
}

private enum GlassCouponBadgeMotion {
    struct Frame {
        var clock: Double
        var yaw: Double
        var pitch: Double
        var scale: Double
        var sparks: Double
        static let rest = Frame(clock: 0, yaw: 0, pitch: 0, scale: 1, sparks: 0)
    }
    static func segment(_ t: Double, _ a: Double, _ b: Double) -> Double {
        min(1, max(0, (t - a) / (b - a)))
    }
    static func cubic(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }
    static func shortYaw(_ yaw: Double) -> Double {
        (yaw.truncatingRemainder(dividingBy: 360) + 540).truncatingRemainder(dividingBy: 360) - 180
    }
    static func at(_ elapsed: Double) -> Frame {
        let t = max(0, elapsed).truncatingRemainder(dividingBy: GlassCouponBadge.loop)
        let w = 2 * Double.pi * t / GlassCouponBadge.loop
        let x = t - 3.1
        let wobble = x > 0 && x < 0.8 ? 10 * exp(-5 * x) * sin(14 * x) : 0
        let spin = cubic(segment(t, 1.4, 3.1))
        return Frame(clock: t, yaw: 14 * sin(w) + 360 * spin + wobble,
                     pitch: 8 * sin(2 * w), scale: 1 + 0.08 * sin(.pi * segment(t, 1.4, 3.1)),
                     sparks: t >= 3.1 && t < 3.8 ? segment(t, 3.1, 3.8) : 0)
    }
    static func transform(z: Double, frame: Frame, center: CGPoint) -> CATransform3D {
        let ay = frame.yaw * .pi / 180, ax = frame.pitch * .pi / 180
        let cy = cos(ay), sy = sin(ay), cp = cos(ax), sp = sin(ax)
        let s = 105 * frame.scale
        func project(_ x: Double, _ y: Double) -> CGPoint {
            let x1 = x * cy + z * sy
            let z1 = -x * sy + z * cy
            let y2 = y * cp - z1 * sp
            let z2 = y * sp + z1 * cp
            let k = 800 / (800 - z2)
            return CGPoint(x: center.x + x1 * k, y: center.y + y2 * k)
        }
        let q = [project(-s, -s), project(s, -s), project(s, s), project(-s, s)]
        let dx1 = q[1].x - q[2].x, dx2 = q[3].x - q[2].x
        let dx3 = q[0].x - q[1].x + q[2].x - q[3].x
        let dy1 = q[1].y - q[2].y, dy2 = q[3].y - q[2].y
        let dy3 = q[0].y - q[1].y + q[2].y - q[3].y
        let den = dx1 * dy2 - dx2 * dy1
        let g = den == 0 ? 0 : (dx3 * dy2 - dx2 * dy3) / den
        let k = den == 0 ? 0 : (dx1 * dy3 - dx3 * dy1) / den
        var m = CATransform3DIdentity
        m.m11 = (q[1].x - q[0].x + g * q[1].x) / 210
        m.m21 = (q[3].x - q[0].x + k * q[3].x) / 210
        m.m41 = q[0].x
        m.m12 = (q[1].y - q[0].y + g * q[1].y) / 210
        m.m22 = (q[3].y - q[0].y + k * q[3].y) / 210
        m.m42 = q[0].y
        m.m14 = g / 210
        m.m24 = k / 210
        return m
    }
}

private struct GlassCouponBadgeSlab: UIViewRepresentable {
    var frame: GlassCouponBadgeMotion.Frame
    var center: CGPoint
    func makeUIView(context: Context) -> GlassCouponBadgeSlabView { GlassCouponBadgeSlabView() }
    func updateUIView(_ view: GlassCouponBadgeSlabView, context: Context) {
        view.update(frame: frame, center: center)
    }
}

private final class GlassCouponBadgeSlabView: UIView {
    private let slices = (0..<11).map { _ in CALayer() }
    override init(frame: CGRect) {
        super.init(frame: frame)
        isUserInteractionEnabled = false
        for slice in slices {
            slice.bounds = CGRect(x: 0, y: 0, width: 210, height: 210)
            slice.anchorPoint = .zero
            slice.position = .zero
            slice.isDoubleSided = true
            slice.minificationFilter = .trilinear
            slice.magnificationFilter = .linear
            layer.addSublayer(slice)
        }
    }
    required init?(coder: NSCoder) { fatalError("init(coder:) has not been implemented") }
    func update(frame: GlassCouponBadgeMotion.Frame, center: CGPoint) {
        let front = cos(frame.yaw * .pi / 180) * cos(frame.pitch * .pi / 180) >= 0
        CATransaction.begin()
        CATransaction.setDisableActions(true)
        for n in 0..<11 {
            let i = front ? n : 10 - n
            slices[n].contents = n == 10 ? GlassCouponBadgeArtwork.badge?.cgImage : GlassCouponBadgeArtwork.tinted[i]?.cgImage
            slices[n].transform = GlassCouponBadgeMotion.transform(z: -11 + 22 * Double(i) / 10, frame: frame, center: center)
            slices[n].zPosition = CGFloat(n)
        }
        CATransaction.commit()
    }
}

private enum GlassCouponBadgeArtwork {
    static func uiColor(_ hex: UInt32, alpha: CGFloat = 1) -> UIColor {
        UIColor(red: CGFloat((hex >> 16) & 255) / 255, green: CGFloat((hex >> 8) & 255) / 255,
                blue: CGFloat(hex & 255) / 255, alpha: alpha)
    }
    static func color(_ hex: UInt32, alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
              blue: Double(hex & 255) / 255, opacity: alpha)
    }
    static let badge: UIImage? = {
        guard let source = UIImage(named: "GlassCouponBadge-badge.png") else { return nil }
        return source.preparingForDisplay() ?? source
    }()
    static let tinted: [UIImage?] = (0..<11).map { i in
        guard let badge else { return nil }
        let mid = 1 - abs(Double(i) / 10 * 2 - 1)
        let ink = UIColor(red: (200 - 60 * mid).rounded() / 255,
                          green: (204 - 90 * mid).rounded() / 255,
                          blue: (232 - 10 * mid).rounded() / 255, alpha: 1)
        let format = UIGraphicsImageRendererFormat()
        format.scale = 1
        return UIGraphicsImageRenderer(size: badge.size, format: format).image { renderer in
            let rect = CGRect(origin: .zero, size: badge.size)
            badge.draw(in: rect)
            renderer.cgContext.setBlendMode(.sourceIn)
            ink.setFill()
            renderer.cgContext.fill(rect)
        }
    }
    private static var bases: [String: UIImage] = [:]
    static func base(size: CGSize, safe: EdgeInsets, brand: String, code: String) -> UIImage {
        let key = "\(size)|\(safe)|\(brand)|\(code)"
        if let image = bases[key] { return image }
        let format = UIGraphicsImageRendererFormat()
        format.scale = 3
        format.opaque = true
        let image = UIGraphicsImageRenderer(size: size, format: format).image { renderer in
            let ctx = renderer.cgContext
            uiColor(0xF4F2EF).setFill()
            ctx.fill(CGRect(origin: .zero, size: size))
            text(brand, rect: CGRect(x: 20, y: safe.top + 8, width: size.width - 40, height: 34), size: 20, weight: .black, alignment: .left)
            text("Bag (3)", rect: CGRect(x: 20, y: safe.top + 8, width: size.width - 40, height: 34), size: 14, weight: .semibold, ink: uiColor(0x16141A, alpha: 0.6), alignment: .right)
            let tileWidth = (size.width - 40) * 0.47
            for (i, hex) in [UInt32(0xF6C9B8), 0xC9D8F6, 0xD9F0C9, 0xF0E2B8].enumerated() {
                uiColor(hex).setFill()
                UIBezierPath(roundedRect: CGRect(x: 20 + CGFloat(i % 2) * (tileWidth + 12), y: safe.top + 60 + CGFloat(i / 2) * 182, width: tileWidth, height: 170), cornerRadius: 20).fill()
            }
            uiColor(0x0C0818, alpha: 0.5).setFill()
            ctx.fill(CGRect(origin: .zero, size: size))
            let sheetTop = size.height * 0.38
            UIColor.white.setFill()
            UIBezierPath(roundedRect: CGRect(x: 0, y: sheetTop, width: size.width, height: size.height - sheetTop + 32), byRoundingCorners: [.topLeft, .topRight], cornerRadii: CGSize(width: 32, height: 32)).fill()
            uiColor(0x16141A, alpha: 0.15).setFill()
            UIBezierPath(roundedRect: CGRect(x: size.width / 2 - 20, y: sheetTop + 8, width: 40, height: 5), cornerRadius: 3).fill()
            let width = size.width - 48
            let buttonY = size.height - safe.bottom - 14 - 58
            let chipY = buttonY - 14 - 56
            let subY = chipY - 18 - 42
            let headingY = subY - 8 - 32
            let kickerHeight = textLayout("REWARD UNLOCKED", width: width, size: 13, weight: .heavy, kern: 1.6).size.height
            text("REWARD UNLOCKED", rect: CGRect(x: 24, y: headingY - 8 - kickerHeight, width: width, height: kickerHeight), size: 13, weight: .heavy, kern: 1.6, ink: uiColor(0x7C5CFF))
            text("25% off your next order", rect: CGRect(x: 24, y: headingY, width: width, height: 32), size: 28, weight: .black, kern: -0.8, lineHeight: 32)
            text("For hitting five orders this spring. Valid on everything until 31 May.", rect: CGRect(x: 24, y: subY, width: width, height: 42), size: 15, weight: .regular, ink: uiColor(0x16141A, alpha: 0.6), lineHeight: 21, multiline: true)
            let chipRect = CGRect(x: 24, y: chipY, width: width, height: 56)
            uiColor(0x7C5CFF, alpha: 0.07).setFill()
            UIBezierPath(roundedRect: chipRect, cornerRadius: 16).fill()
            let border = UIBezierPath(roundedRect: chipRect.insetBy(dx: 0.75, dy: 0.75), cornerRadius: 15.25)
            border.lineWidth = 1.5
            border.setLineDash([4.5, 4.5], count: 2, phase: 0)
            uiColor(0x7C5CFF, alpha: 0.5).setStroke()
            border.stroke()
            // RN's border participates in the chip's flex-box content inset.
            text(code, rect: chipRect.insetBy(dx: 19.5, dy: 0), size: 20, weight: .black, kern: 3, alignment: .left)
            text("Copy", rect: chipRect.insetBy(dx: 19.5, dy: 0), size: 15, weight: .bold, ink: uiColor(0x7C5CFF), alignment: .right)
            uiColor(0x16141A).setFill()
            UIBezierPath(roundedRect: CGRect(x: 24, y: buttonY, width: width, height: 58), cornerRadius: 29).fill()
            text("Apply coupon", rect: CGRect(x: 24, y: buttonY, width: width, height: 58), size: 17, weight: .bold, ink: .white)
        }
        bases[key] = image
        return image
    }
    private static func text(_ string: String, rect: CGRect, size: CGFloat, weight: UIFont.Weight,
                             kern: CGFloat? = nil, ink: UIColor = uiColor(0x16141A),
                             alignment: NSTextAlignment = .center, lineHeight: CGFloat? = nil, multiline: Bool = false) {
        let layout = textLayout(string, width: rect.width, size: size, weight: weight, kern: kern,
                                ink: ink, alignment: alignment, lineHeight: lineHeight, multiline: multiline)
        let origin = CGPoint(x: rect.minX, y: rect.minY + (rect.height - layout.size.height) / 2)
        let range = layout.manager.glyphRange(for: layout.container)
        layout.manager.drawBackground(forGlyphRange: range, at: origin)
        layout.manager.drawGlyphs(forGlyphRange: range, at: origin)
        // NSTextStorage owns the layout manager; retain both through drawing.
        withExtendedLifetime(layout.storage) {}
    }
    private static func textLayout(_ string: String, width: CGFloat, size: CGFloat, weight: UIFont.Weight,
                                   kern: CGFloat? = nil, ink: UIColor = uiColor(0x16141A),
                                   alignment: NSTextAlignment = .center, lineHeight: CGFloat? = nil,
                                   multiline: Bool = false) -> (storage: NSTextStorage, manager: NSLayoutManager, container: NSTextContainer, size: CGSize) {
        let font = UIFont.systemFont(ofSize: size, weight: weight)
        let paragraph = NSMutableParagraphStyle()
        paragraph.alignment = alignment
        paragraph.lineBreakMode = multiline ? .byWordWrapping : .byClipping
        if let lineHeight {
            paragraph.minimumLineHeight = lineHeight
            paragraph.maximumLineHeight = lineHeight
        }
        var attributes: [NSAttributedString.Key: Any] = [.font: font, .foregroundColor: ink, .paragraphStyle: paragraph]
        if let kern { attributes[.kern] = kern }
        // RN adds baseline offset only when the requested line is at least the font height.
        if let lineHeight, lineHeight >= font.lineHeight {
            attributes[.baselineOffset] = (lineHeight - font.lineHeight) / 2
        }
        let storage = NSTextStorage(string: string, attributes: attributes)
        let manager = NSLayoutManager()
        manager.usesFontLeading = false
        let container = NSTextContainer(size: CGSize(width: width, height: .greatestFiniteMagnitude))
        container.lineFragmentPadding = 0
        container.maximumNumberOfLines = 0
        container.lineBreakMode = .byClipping
        storage.addLayoutManager(manager)
        manager.addTextContainer(container)
        manager.ensureLayout(for: container)
        let used = manager.usedRect(for: container).size
        let measured = CGSize(width: ceil(used.width * 3) / 3, height: ceil(used.height * 3) / 3)
        return (storage, manager, container, measured)
    }
    static func effects(_ context: GraphicsContext, size: CGSize, safe: EdgeInsets, center: CGPoint, frame: GlassCouponBadgeMotion.Frame) {
        let u = GlassCouponBadgeMotion.segment(frame.clock, 4.2, 5)
        if u > 0 && u < 1 {
            let chipY = size.height - safe.bottom - 14 - 58 - 14 - 56
            var shine = context
            shine.clip(to: Path(roundedRect: CGRect(x: 24, y: chipY, width: size.width - 48, height: 56), cornerRadius: 16))
            // Transform order and implicit absolute x match RN's chip child.
            shine.translateBy(x: 24 + 1.5 + 30 - 120 + 420 * u, y: chipY + 28)
            shine.rotate(by: .degrees(18))
            shine.fill(Path(CGRect(x: -30, y: -46.5, width: 60, height: 93)), with: .linearGradient(
                Gradient(colors: [.white.opacity(0), .white.opacity(0.9), .white.opacity(0)]),
                startPoint: CGPoint(x: -30, y: 0), endPoint: CGPoint(x: 30, y: 0)))
        }
        context.fill(Path(ellipseIn: CGRect(x: center.x - 189, y: center.y - 189, width: 378, height: 378)),
                     with: .radialGradient(Gradient(colors: [color(0x7C5CFF, alpha: 0.35), color(0x7C5CFF, alpha: 0)]), center: center, startRadius: 0, endRadius: 189))
        var shadow = context
        shadow.addFilter(.blur(radius: 14 / 0.92))
        let sw = 147 * max(0.35, abs(cos(frame.yaw * .pi / 180)))
        shadow.fill(Path(ellipseIn: CGRect(x: center.x - sw / 2, y: center.y + 126, width: sw, height: 22)), with: .color(color(0x28145A, alpha: 0.25)))
        if frame.sparks > 0 {
            let progress = frame.sparks
            let radius = 40 + 110 * (1 - pow(1 - progress, 2)) + 210 * 0.35
            for i in 0..<12 {
                let angle = Double(i) / 12 * .pi * 2 + 0.2
                let dotRadius = 6 * (1 - progress * 0.6)
                let dot = CGPoint(x: center.x + cos(angle) * radius, y: center.y + sin(angle) * radius)
                context.fill(Path(ellipseIn: CGRect(x: dot.x - dotRadius, y: dot.y - dotRadius, width: dotRadius * 2, height: dotRadius * 2)),
                             with: .color(color([0xFF8A1F, 0x7C5CFF, 0x4FC3FF][i % 3], alpha: 1 - progress)))
            }
        }
    }
}

#Preview {
    GlassCouponBadge(autoPlay: true)
}
