import SwiftUI
import UIKit

/// The RN reference's single timer island: pill → minutes card → countdown bar → pill.
/// All geometry and crossfades come from the same nine-second clock.
/// 0–0.8 rest; 0.8–1.3 expand; 1.6–2.6 roll 24→26; 2.6–2.9 press Start;
/// 2.9–3.4 squeeze to bar; 3.4–7.6 count down; 7.3–7.6 press Cancel;
/// 7.6–8.1 shrink; 8.1–9 rest. Shape curves are ease-out-back, k = 1.3.
struct TimerPillExpand: View {
    var brand = "Simmer"
    var autoPlay = false
    static let loop: Double = 9

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var elapsedBeforePause = 0.0
    @State private var resumedAt: Date?
    @State private var origin = 0.0

    private typealias M = TimerPillExpandMotion
    private typealias A = TimerPillExpandDrawing

    var body: some View {
        GeometryReader { geometry in
            let safe = geometry.safeAreaInsets
            let size = CGSize(
                width: geometry.size.width + safe.leading + safe.trailing,
                height: geometry.size.height + safe.top + safe.bottom
            )
            ZStack(alignment: .topLeading) {
                Color(red: 236 / 255, green: 235 / 255, blue: 232 / 255)

                Canvas { context, _ in
                    context.withCGContext { cg in
                        A.page(cg, size: size, safe: safe, brand: brand)
                    }
                }
                .allowsHitTesting(false)
                .accessibilityElement(children: .ignore)
                .accessibilityLabel("\(brand). Step 3 of 7. Let the ragù simmer. Lid half on, lowest flame. Stir every few minutes so nothing catches. Chef's note: It is ready when the oil pools on top and the sauce coats a spoon. Next step.")

                TimelineView(.animation(paused: resumedAt == nil || !autoPlay)) { timeline in
                    let t = clock(at: timeline.date)
                    let island = M.island(at: t)
                    Canvas { context, _ in
                        context.withCGContext { cg in
                            A.island(cg, state: island)
                        }
                    }
                    .frame(width: island.width, height: island.height, alignment: .topLeading)
                    .contentShape(RoundedRectangle(cornerRadius: island.radius))
                    .onTapGesture { advance() }
                    .accessibilityElement(children: .ignore)
                    .accessibilityLabel("Timer")
                    .accessibilityAddTraits(.isButton)
                    .accessibilityValue(island.bar > 0.5 ? island.countdown : "Timers")
                    .position(x: size.width / 2, y: size.height * 0.46)
                }
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .onAppear { updateClock() }
        .onChange(of: scenePhase) { updateClock() }
        .onChange(of: reduceMotion) { updateClock() }
    }

    private func elapsed(at date: Date) -> Double {
        elapsedBeforePause + (resumedAt.map { max(0, date.timeIntervalSince($0)) } ?? 0)
    }

    private func clock(at date: Date) -> Double {
        if reduceMotion { return 3.6 }
        guard autoPlay else { return 0 }
        let time = elapsed(at: date) - origin
        return (time.truncatingRemainder(dividingBy: Self.loop) + Self.loop)
            .truncatingRemainder(dividingBy: Self.loop)
    }

    private func updateClock() {
        let now = Date()
        elapsedBeforePause = elapsed(at: now)
        resumedAt = scenePhase == .active && !reduceMotion ? now : nil
    }

    private func advance() {
        guard autoPlay, !reduceMotion else { return }
        let now = Date()
        let t = clock(at: now)
        let next = t < 0.8 ? 0.8 : t < 2.6 ? 2.6 : t < 7.3 ? 7.3 : 0.8
        // The RN tap advances the origin and continues the loop immediately, without a hold.
        origin = elapsed(at: now) - next
        UIImpactFeedbackGenerator(style: .light).impactOccurred()
    }
}

private enum TimerPillExpandMotion {
    struct Island {
        var width = 200.0
        var height = 58.0
        var radius = 29.0
        var pill: Double
        var card: Double
        var bar: Double
        var minutes: Double
        var start: Double
        var cancel: Double
        var running: Double

        var countdown: String {
            let left = max(0, 26 * 60 - Int(floor(running)))
            return String(format: "%d:%02d", left / 60, left % 60)
        }
    }

    static func segment(_ t: Double, _ a: Double, _ b: Double) -> Double {
        min(1, max(0, (t - a) / (b - a)))
    }

    static func back(_ x: Double) -> Double {
        1 + 2.3 * pow(x - 1, 3) + 1.3 * pow(x - 1, 2)
    }

    static func cubic(_ x: Double) -> Double {
        x < 0.5 ? 4 * x * x * x : 1 - pow(-2 * x + 2, 3) / 2
    }

    static func island(at t: Double) -> Island {
        var state = Island(
            pill: 1 - segment(t, 0.8, 1) + segment(t, 7.8, 8.1),
            card: segment(t, 1, 1.3) * (1 - segment(t, 2.9, 3.1)),
            bar: segment(t, 3.1, 3.4) * (1 - segment(t, 7.6, 7.8)),
            minutes: 24 + 2 * cubic(segment(t, 1.6, 2.6)),
            start: 1 - 0.1 * sin(.pi * segment(t, 2.6, 2.9)),
            cancel: 1 - 0.12 * sin(.pi * segment(t, 7.3, 7.6)),
            running: max(0, t - 3.4)
        )
        if t >= 0.8, t < 2.9 {
            let u = back(segment(t, 0.8, 1.3))
            state.width = 200 + 112 * u
            state.height = 58 + 192 * u
            state.radius = 29 + 11 * u
        } else if t >= 2.9, t < 7.6 {
            let u = back(segment(t, 2.9, 3.4))
            state.width = 312 + 32 * u
            state.height = 250 - 150 * u
            state.radius = 40 + 10 * u
        } else if t >= 7.6 {
            let u = back(segment(t, 7.6, 8.1))
            state.width = 344 - 144 * u
            state.height = 100 - 42 * u
            state.radius = 50 - 21 * u
        }
        return state
    }
}

private enum TimerPillExpandDrawing {
    private static let ink = color(0x1C1C1E)
    private static let orange = color(0xFF9F0A)

    private static func color(_ hex: UInt32) -> UIColor {
        UIColor(red: CGFloat((hex >> 16) & 255) / 255,
                green: CGFloat((hex >> 8) & 255) / 255,
                blue: CGFloat(hex & 255) / 255, alpha: 1)
    }

    /// TextKit uses the same system faces, line heights and leading as RN on iOS.
    /// Layouts (including all wheel rows) are cached, so no glyph layout occurs per frame.
    private final class Label {
        let storage: NSTextStorage
        let manager: NSLayoutManager
        let container: NSTextContainer
        let size: CGSize

        init(_ text: String, font: UIFont, color: UIColor, width: CGFloat,
             lineHeight: CGFloat?, tracking: CGFloat, alignment: NSTextAlignment) {
            let paragraph = NSMutableParagraphStyle()
            paragraph.alignment = alignment
            if let lineHeight {
                paragraph.minimumLineHeight = lineHeight
                paragraph.maximumLineHeight = lineHeight
            }
            var attributes: [NSAttributedString.Key: Any] = [
                .font: font, .foregroundColor: color, .paragraphStyle: paragraph, .kern: tracking
            ]
            if let lineHeight, lineHeight >= font.lineHeight {
                attributes[.baselineOffset] = (lineHeight - font.lineHeight) / 2
            }
            let textStorage = NSTextStorage(string: text, attributes: attributes)
            let layout = NSLayoutManager()
            layout.usesFontLeading = false
            let textContainer = NSTextContainer(size: CGSize(width: width, height: .greatestFiniteMagnitude))
            textContainer.lineFragmentPadding = 0
            textContainer.lineBreakMode = .byClipping
            layout.addTextContainer(textContainer)
            textStorage.addLayoutManager(layout)
            layout.ensureLayout(for: textContainer)
            let used = layout.usedRect(for: textContainer).size
            let scale = UIScreen.main.scale
            storage = textStorage
            manager = layout
            container = textContainer
            size = CGSize(width: ceil(used.width * scale) / scale,
                          height: ceil(used.height * scale) / scale)
        }

        func draw(at point: CGPoint) {
            manager.drawGlyphs(forGlyphRange: manager.glyphRange(for: container), at: point)
        }
    }

    private static var labels: [String: Label] = [:]

    private static func label(_ text: String, size: CGFloat, weight: UIFont.Weight = .regular,
                              color: UIColor = .white, width: CGFloat = 1000,
                              lineHeight: CGFloat? = nil, tracking: CGFloat = 0,
                              alignment: NSTextAlignment = .left, digits: Bool = false) -> Label {
        let key = "\(text)|\(size)|\(weight.rawValue)|\(color)|\(width)|\(lineHeight ?? 0)|\(tracking)|\(alignment.rawValue)|\(digits)"
        if let cached = labels[key] { return cached }
        let font = digits ? UIFont.monospacedDigitSystemFont(ofSize: size, weight: weight)
                          : UIFont.systemFont(ofSize: size, weight: weight)
        let result = Label(text, font: font, color: color, width: width,
                           lineHeight: lineHeight, tracking: tracking, alignment: alignment)
        labels[key] = result
        return result
    }

    static func page(_ cg: CGContext, size: CGSize, safe: EdgeInsets, brand: String) {
        UIGraphicsPushContext(cg)
        defer { UIGraphicsPopContext() }
        let top = safe.top + 8
        let brandLabel = label(brand, size: 18, weight: .heavy, color: ink)
        brandLabel.draw(at: CGPoint(x: 24, y: top + (34 - brandLabel.size.height) / 2))
        let meta = label("Step 3 of 7", size: 14, weight: .semibold, color: ink.withAlphaComponent(0.55))
        meta.draw(at: CGPoint(x: size.width - 24 - meta.size.width, y: top + (34 - meta.size.height) / 2))

        let heading = label("Let the ragù simmer", size: 34, weight: .black, color: ink,
                            width: size.width - 48, lineHeight: 38, tracking: -1)
        heading.draw(at: CGPoint(x: 24, y: top + 34 + 14))
        label("Lid half on, lowest flame. Stir every few minutes so nothing catches.",
              size: 16, color: ink.withAlphaComponent(0.65), width: size.width - 48, lineHeight: 22)
            .draw(at: CGPoint(x: 24, y: top + 34 + 14 + heading.size.height + 8))

        let buttonY = size.height - safe.bottom - 14 - 58
        fill(cg, rect: CGRect(x: 20, y: buttonY, width: size.width - 40, height: 58), radius: 29, color: ink)
        let action = label("Next step", size: 17, weight: .bold)
        action.draw(at: CGPoint(x: (size.width - action.size.width) / 2, y: buttonY + (58 - action.size.height) / 2))

        let tipWidth = size.width - 76
        let tipTitle = label("Chef's note", size: 15, weight: .heavy, color: ink, width: tipWidth)
        let tip = label("It is ready when the oil pools on top and the sauce coats a spoon.",
                        size: 15, color: ink.withAlphaComponent(0.65), width: tipWidth, lineHeight: 21)
        let tipHeight = 36 + tipTitle.size.height + 4 + tip.size.height
        let tipY = buttonY - 14 - tipHeight
        fill(cg, rect: CGRect(x: 20, y: tipY, width: size.width - 40, height: tipHeight), radius: 22, color: .white)
        tipTitle.draw(at: CGPoint(x: 38, y: tipY + 18))
        tip.draw(at: CGPoint(x: 38, y: tipY + 18 + tipTitle.size.height + 4))
    }

    static func island(_ cg: CGContext, state s: TimerPillExpandMotion.Island) {
        UIGraphicsPushContext(cg)
        defer { UIGraphicsPopContext() }
        let bounds = CGRect(x: 0, y: 0, width: s.width, height: s.height)
        let shape = CGPath(roundedRect: bounds, cornerWidth: s.radius, cornerHeight: s.radius, transform: nil)
        cg.addPath(shape)
        cg.clip()
        cg.setFillColor(color(0x2C2C2E).cgColor)
        cg.fill(bounds)
        // RN's overflow:hidden clips the island's own shadow as well as its children.
        if s.pill > 0 {
            cg.saveGState()
            cg.setAlpha(s.pill)
            cg.beginTransparencyLayer(auxiliaryInfo: nil)
            let text = label("Timers", size: 21, weight: .medium)
            let left = (s.width - text.size.width - 22 - 28) / 2
            text.draw(at: CGPoint(x: left, y: (s.height - text.size.height) / 2))
            let watch = CGRect(x: left + text.size.width + 22, y: (s.height - 28) / 2, width: 28, height: 28)
            cg.setStrokeColor(orange.cgColor)
            cg.setLineWidth(2.5)
            cg.strokeEllipse(in: watch.insetBy(dx: 1.25, dy: 1.25))
            cg.saveGState()
            cg.translateBy(x: watch.midX, y: watch.midY - 3)
            cg.rotate(by: -35 * .pi / 180)
            fill(cg, rect: CGRect(x: -1.25, y: -4.5, width: 2.5, height: 9), radius: 1.25, color: orange)
            cg.restoreGState()
            cg.endTransparencyLayer()
            cg.restoreGState()
        }
        if s.card > 0 {
            cg.saveGState()
            cg.setAlpha(s.card)
            cg.beginTransparencyLayer(auxiliaryInfo: nil)
            cg.saveGState()
            cg.clip(to: CGRect(x: 28, y: 0, width: 64, height: s.height))
            for n in 18..<32 {
                let near = max(0, min(1, 1 - abs(Double(n) - s.minutes)))
                let scale = 0.68 + 0.32 * near
                let rowY = 106 + (Double(n) - s.minutes) * 38
                cg.saveGState()
                cg.setAlpha(0.28 + 0.72 * near)
                cg.translateBy(x: 28 + 32, y: rowY + 19)
                cg.scaleBy(x: scale, y: scale)
                label(String(n), size: 40, weight: .semibold, width: 64, lineHeight: 38, digits: true)
                    .draw(at: CGPoint(x: -32, y: -19))
                cg.restoreGState()
            }
            cg.restoreGState()
            label("mins", size: 24, weight: .medium).draw(at: CGPoint(x: 92, y: 111))
            button(cg, text: "Start", x: s.width - 18 - 74, y: s.height - 18 - 74,
                   diameter: 74, scale: s.start, background: color(0x0E3B12), foreground: color(0x32D74B), size: 19)
            cg.endTransparencyLayer()
            cg.restoreGState()
        }
        if s.bar > 0 {
            cg.saveGState()
            cg.setAlpha(s.bar)
            cg.beginTransparencyLayer(auxiliaryInfo: nil)
            let ringY = (s.height - 72) / 2
            let centre = CGPoint(x: 14 + 36, y: ringY + 36)
            cg.setLineWidth(3)
            cg.setLineCap(.butt)
            cg.setStrokeColor(orange.withAlphaComponent(0.25).cgColor)
            arc(cg, centre: centre, fraction: 1)
            cg.setLineCap(.round)
            cg.setStrokeColor(orange.cgColor)
            arc(cg, centre: centre, fraction: 1 - s.running / (26 * 60) * 6)
            let count = label(s.countdown, size: 17, weight: .semibold, digits: true)
            count.draw(at: CGPoint(x: centre.x - count.size.width / 2, y: centre.y - count.size.height / 2))
            let buttonY = (s.height - 64) / 2
            button(cg, text: "Cancel", x: s.width - 14 - 64 - 10 - 64, y: buttonY,
                   diameter: 64, scale: s.cancel, background: color(0x48484A), foreground: .white, size: 14)
            button(cg, text: "Pause", x: s.width - 14 - 64, y: buttonY,
                   diameter: 64, scale: 1, background: color(0x3A2500), foreground: orange, size: 14)
            cg.endTransparencyLayer()
            cg.restoreGState()
        }
    }

    private static func arc(_ cg: CGContext, centre: CGPoint, fraction: Double) {
        cg.beginPath()
        cg.addArc(center: centre, radius: 32, startAngle: -.pi / 2,
                  endAngle: -.pi / 2 + 359.9 * .pi / 180 * fraction, clockwise: false)
        cg.strokePath()
    }

    private static func button(_ cg: CGContext, text: String, x: Double, y: Double,
                               diameter: Double, scale: Double, background: UIColor,
                               foreground: UIColor, size: CGFloat) {
        cg.saveGState()
        cg.translateBy(x: x + diameter / 2, y: y + diameter / 2)
        cg.scaleBy(x: scale, y: scale)
        cg.setFillColor(background.cgColor)
        cg.fillEllipse(in: CGRect(x: -diameter / 2, y: -diameter / 2, width: diameter, height: diameter))
        let text = label(text, size: size, weight: .semibold, color: foreground)
        text.draw(at: CGPoint(x: -text.size.width / 2, y: -text.size.height / 2))
        cg.restoreGState()
    }

    private static func fill(_ cg: CGContext, rect: CGRect, radius: CGFloat, color: UIColor) {
        cg.setFillColor(color.cgColor)
        cg.addPath(CGPath(roundedRect: rect, cornerWidth: radius, cornerHeight: radius, transform: nil))
        cg.fillPath()
    }
}

#Preview {
    TimerPillExpand(autoPlay: true)
}
