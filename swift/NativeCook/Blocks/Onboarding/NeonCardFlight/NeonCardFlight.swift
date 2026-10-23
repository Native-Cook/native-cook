import SwiftUI
import UIKit

/// Source-tracked card flights with locally bundled, generated sculpture poses.
struct NeonCardFlight: View {
    var autoPlay = false
    var renderTime: Double? = nil

    static let loop = 8.0
    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @State private var origin = Date.now
    @State private var manualTime: Double? = nil
    @State private var dragOrigin: Double? = nil
    @State private var returnStart: Date? = nil
    @State private var resume: Task<Void, Never>? = nil
    @State private var haptic = UIImpactFeedbackGenerator(style: .light)
    @State private var lastThreshold = false

    private let paper = Color(red: 1, green: 0.992, blue: 1)
    private let ink = Color(red: 0.0745, green: 0.0863, blue: 0.0902)

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let width = proxy.size.width + insets.leading + insets.trailing
            let height = proxy.size.height + insets.top + insets.bottom
            let scale = min(width / 393, height / 852)
            TimelineView(.animation(paused: renderTime != nil || reduceMotion || (!autoPlay && manualTime == nil))) { timeline in
                let frame = shown(timeline.date)
                ZStack(alignment: .topLeading) {
                    paper
                    ZStack(alignment: .topLeading) {
                        hero(frame.time)
                            .opacity(1 - frame.reset)
                        if frame.reset > 0 {
                            hero(0).opacity(frame.reset)
                        }
                        header
                        actions
                    }
                    .frame(width: 393, height: 852, alignment: .topLeading)
                    .scaleEffect(scale, anchor: .topLeading)
                    .offset(x: (width - 393 * scale) / 2, y: (height - 852 * scale) / 2)
                }
                .frame(width: width, height: height, alignment: .topLeading)
                .clipped()
            }
            .ignoresSafeArea()
        }
        .preferredColorScheme(.light)
        .onAppear { origin = .now; haptic.prepare() }
        .onDisappear { resume?.cancel() }
    }

    private struct Frame { var time: Double; var reset: Double }

    private func shown(_ date: Date) -> Frame {
        if let renderTime { return Frame(time: renderTime.truncatingRemainder(dividingBy: Self.loop), reset: 0) }
        if reduceMotion { return Frame(time: 5.4, reset: 0) }
        if let manualTime {
            let blend = returnStart.map { Self.ease(Self.seg(date.timeIntervalSince($0), 0, 0.65)) } ?? 0
            return Frame(time: manualTime, reset: blend)
        }
        return Frame(time: autoPlay ? date.timeIntervalSince(origin).truncatingRemainder(dividingBy: Self.loop) : 0, reset: 0)
    }

    private func takeOver() {
        let frame = shown(.now)
        resume?.cancel()
        returnStart = nil
        manualTime = frame.time
    }

    private func scheduleResume() {
        resume?.cancel()
        resume = Task { @MainActor in
            try? await Task.sleep(for: .seconds(2))
            guard !Task.isCancelled else { return }
            returnStart = .now
            try? await Task.sleep(for: .seconds(0.65))
            guard !Task.isCancelled else { return }
            manualTime = nil
            returnStart = nil
            origin = .now
        }
    }

    private var scrub: some Gesture {
        DragGesture(minimumDistance: 0)
            .onChanged { value in
                if dragOrigin == nil {
                    takeOver()
                    dragOrigin = manualTime
                    lastThreshold = (manualTime ?? 0) >= 3.5
                }
                guard !reduceMotion else { return }
                let time = min(max((dragOrigin ?? 0) + value.translation.width / 55, 0), 6.15)
                manualTime = time
                let crossed = time >= 3.5
                if crossed != lastThreshold {
                    haptic.impactOccurred(intensity: 0.55)
                    lastThreshold = crossed
                }
            }
            .onEnded { _ in dragOrigin = nil; scheduleResume() }
    }

    private var header: some View {
        ZStack(alignment: .topLeading) {
            Rectangle().fill(Color.black.opacity(0.32)).frame(width: 373, height: 0.5).offset(x: 10, y: 64)
            HStack(spacing: 7) {
                Text("N").font(.system(size: 15, weight: .black))
                Text("Welcome to Nova <18").font(.system(size: 12))
            }
            .foregroundStyle(ink)
            .offset(x: 19, y: 73)
            Text("INDEPENDENCE STARTS\nHERE")
                .font(.system(size: 27, weight: .heavy))
                .tracking(-0.65)
                .lineSpacing(-1)
                .foregroundStyle(ink)
                .fixedSize(horizontal: false, vertical: true)
                .frame(width: 355, alignment: .leading)
                .offset(x: 21, y: 111)
        }
        .allowsHitTesting(false)
    }

    private var actions: some View {
        ZStack(alignment: .topLeading) {
            Button { finishFlight() } label: {
                Text("Log in to Nova <18")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(.white)
                    .frame(width: 359, height: 47)
                    .background(Capsule().fill(ink))
            }
            .buttonStyle(.plain)
            .offset(x: 17, y: 691)
            Button { finishFlight() } label: {
                Text("Create a new account")
                    .font(.system(size: 13, weight: .medium))
                    .foregroundStyle(ink)
                    .frame(width: 359, height: 47)
                    .background(Capsule().fill(.white)
                        .shadow(color: .black.opacity(0.10), radius: 3, y: 2)
                        .shadow(color: .black.opacity(0.08), radius: 8, y: 4))
            }
            .buttonStyle(.plain)
            .offset(x: 17, y: 751)
        }
    }

    private func finishFlight() {
        takeOver()
        manualTime = 5.4
        haptic.impactOccurred(intensity: 0.55)
        scheduleResume()
    }

    private func hero(_ time: Double) -> some View {
        let resetOut = Self.ease(Self.seg(time, 6.22, 6.72))
        let resetIn = Self.ease(Self.seg(time, 6.72, 7.32))
        return ZStack(alignment: .topLeading) {
            scene(time).opacity(1 - resetOut)
            if time >= 6.72 { scene(0).opacity(resetIn) }
            Rectangle().fill(.clear)
                .frame(width: 393, height: 483)
                .offset(y: 193)
                .contentShape(Rectangle())
                .gesture(scrub)
                .accessibilityElement()
                .accessibilityLabel("Card flight")
                .accessibilityHint("Drag right to fly the card and assemble the objects. Drag left to rewind.")
                .accessibilityAdjustableAction { direction in
                    takeOver()
                    manualTime = min(max((manualTime ?? 0) + (direction == .increment ? 0.7 : -0.7), 0), 6.15)
                    scheduleResume()
                }
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
    }

    private func scene(_ time: Double) -> some View {
        let card = NeonCardFlightMotion.card(time)
        let source = max(time - 0.65, 0)
        let accentInFront = source >= 3.35 && source <= 4.05
        return ZStack(alignment: .topLeading) {
            Canvas { context, _ in
                Self.speedlines(&context, time: time)
                if !accentInFront { Self.accents(&context, time: time) }
                Self.pieces(&context, time: time, foreground: false)
            }.frame(width: 393, height: 852)
            if card.visible && card.behindForeground { cardImages(card) }
            Canvas { context, _ in Self.pieces(&context, time: time, foreground: true) }
                .frame(width: 393, height: 852)
            if card.visible && !card.behindForeground { cardImages(card) }
            if accentInFront {
                Canvas { context, _ in Self.accents(&context, time: time) }
                    .frame(width: 393, height: 852)
            }
        }
        .frame(width: 393, height: 852, alignment: .topLeading)
        .allowsHitTesting(false)
    }

    @ViewBuilder private func cardImages(_ card: NeonCardFlightMotion.Card) -> some View {
        let moving = hypot(card.trail.x, card.trail.y) > 0.01
        let weights = moving ? [0.06, 0.10, 0.16, 0.25, 1.0] : [1.0]
        ForEach(0..<weights.count, id: \.self) { i in
            let lag = Double(weights.count - 1 - i)
            let corners = card.points.map { CGPoint(x: $0.x - card.trail.x*lag, y: $0.y - card.trail.y*lag) }
            Image(uiImage: card.intro ? NeonCardFlightArt.introCard : NeonCardFlightArt.card).resizable()
                .frame(width: 300, height: 190)
                .projectionEffect(Self.project(corners, width: 300, height: 190))
                .opacity(weights[i])
        }
    }

    private static func pieces(_ context: inout GraphicsContext, time: Double, foreground: Bool) {
        for piece in NeonCardFlightMotion.pieces where piece.foreground == foreground {
            let pose = NeonCardFlightMotion.pose(piece, time)
            guard pose.alpha > 0 else { continue }
            var c = context
            c.opacity = pose.alpha
            c.translateBy(x: pose.x, y: pose.y)
            c.rotate(by: .degrees(pose.roll))
            c.scaleBy(x: pose.scale, y: pose.scale)
            c.concatenate(CGAffineTransform(a: pose.a,b: pose.b,c: pose.c,d: pose.d,tx: pose.tx,ty: pose.ty))
            c.draw(Image(uiImage: NeonCardFlightArt.sprite(piece.name)),
                   in: CGRect(x: -piece.w/2,y: -piece.h/2,width: piece.w,height: piece.h))
        }
    }

    private static func accents(_ context: inout GraphicsContext, time: Double) {
        let s = max(time - 0.65, 0)
        guard s >= 2.4 else { return }
        let keys = NeonCardFlightMotion.accent
        let x = NeonCardFlightMotion.track(s, keys, 1), y = NeonCardFlightMotion.track(s, keys, 2)
        let roll = NeonCardFlightMotion.track(s, keys, 3), alpha = ease(seg(s, 2.4, 2.55))
        var c = context; c.opacity = alpha; c.translateBy(x: x, y: y); c.rotate(by: .degrees(roll))
        for i in 0..<3 {
            let dx = Double(i - 1)*20
            var arrow = Path(); arrow.move(to: CGPoint(x: dx-8,y: -8)); arrow.addLine(to: CGPoint(x: dx+2,y: 0)); arrow.addLine(to: CGPoint(x: dx-8,y: 8))
            var side = c; side.translateBy(x: 0,y: 1.4)
            side.stroke(arrow, with: .color(Color(red: 0.67,green: 0.75,blue: 0)), style: StrokeStyle(lineWidth: 4.2,lineCap: .square,lineJoin: .miter))
            c.stroke(arrow, with: .color(Color(red: 0.91,green: 1,blue: 0)), style: StrokeStyle(lineWidth: 3.2,lineCap: .square,lineJoin: .miter))
        }
    }

    static func seg(_ time: Double, _ start: Double, _ end: Double) -> Double { min(max((time - start) / (end - start), 0), 1) }
    static func ease(_ x: Double) -> Double { x * x * (3 - 2 * x) }
    static func easeOut(_ x: Double) -> Double { 1 - pow(1 - x, 3) }
    static func mix(_ a: Double, _ b: Double, _ p: Double) -> Double { a + (b - a) * p }

    /// A planar homography, not a flattened skew: every pixel and the chip share the card's perspective.
    private static func project(_ p: [CGPoint], width: Double, height: Double) -> ProjectionTransform {
        let dx1 = p[1].x - p[2].x, dx2 = p[3].x - p[2].x
        let dy1 = p[1].y - p[2].y, dy2 = p[3].y - p[2].y
        let sx = p[0].x - p[1].x + p[2].x - p[3].x
        let sy = p[0].y - p[1].y + p[2].y - p[3].y
        let divisor = dx1 * dy2 - dx2 * dy1
        let g = abs(divisor) > 0.00001 ? (sx * dy2 - dx2 * sy) / divisor : 0
        let h = abs(divisor) > 0.00001 ? (dx1 * sy - sx * dy1) / divisor : 0
        var transform = CATransform3DIdentity
        transform.m11 = (p[1].x - p[0].x + g * p[1].x) / width
        transform.m21 = (p[3].x - p[0].x + h * p[3].x) / height
        transform.m41 = p[0].x
        transform.m12 = (p[1].y - p[0].y + g * p[1].y) / width
        transform.m22 = (p[3].y - p[0].y + h * p[3].y) / height
        transform.m42 = p[0].y
        transform.m14 = g / width
        transform.m24 = h / height
        return ProjectionTransform(transform)
    }

    private static func speedlines(_ context: inout GraphicsContext, time: Double) {
        guard time < 2.55 else { return }
        let travel = max(time - 0.65, 0)
        let colors: [Color] = [0xBAB3C4,0xCF408F,0xD8D3DD,0xC5BDCC,0xBDB5C9,0xE2E800,0xD5D2DB,0xBCB3C2,0xC9C4D0,0xA272AD,0xC8C0CD,0xD2CDD6,0xC1BCC8,0xF36B3F,0xD8D3DE,0xC9C4CF].map { Color(uiColor: NeonCardFlightArt.color(UInt32($0))) }
        var clipped = context
        clipped.clip(to: Path(CGRect(x: 0, y: 198, width: 393, height: 654)))
        for i in 0..<192 {
            let hash = fract(sin(Double(i) * 12.9898 + 3.2) * 43758.5453)
            let angle = Double(i) * 2.399963 + hash * 0.4
            let unit = fract(hash + travel * (0.95 + hash * 0.30))
            let radius = 35 + pow(unit, 1.3) * 185
            let length = 12 + pow(unit, 1.15) * (115 + 200 * max(sin(angle), 0))
            let x = cos(angle), y = sin(angle)
            var path = Path()
            path.move(to: CGPoint(x: 196 + x * radius, y: 352 + y * radius))
            path.addLine(to: CGPoint(x: 196 + x * (radius + length), y: 352 + y * (radius + length)))
            let start = CGPoint(x: 196 + x * radius, y: 352 + y * radius)
            let end = CGPoint(x: 196 + x * (radius + length), y: 352 + y * (radius + length))
            let tint = colors[i % colors.count]
            let accent = i % 16 == 1 || i % 16 == 5 || i % 16 == 9 || i % 16 == 13
            let alpha = (0.35 + 0.60 * unit) * (accent ? 0.90 : 0.85)
            let width = (accent ? 1.0 : 0.8) + pow(unit, 1.2) * (accent ? 4.8 : 5.5)
            let shade = GraphicsContext.Shading.linearGradient(Gradient(colors: [tint.opacity(alpha * 0.10), tint.opacity(alpha)]), startPoint: start, endPoint: end)
            var halo = clipped; halo.opacity = 0.16
            halo.stroke(path, with: shade, style: StrokeStyle(lineWidth: width + 2.2, lineCap: .round))
            clipped.stroke(path, with: shade, style: StrokeStyle(lineWidth: width, lineCap: .round))
        }
    }

    private static func fract(_ x: Double) -> Double { x - floor(x) }
}

/// The card is code-drawn; generated sculpture poses are decoded once and kept offline.
private enum NeonCardFlightArt {
    private static let sprites: [String: UIImage] = {
        var images: [String: UIImage] = [:]
        for name in ["orange", "cap", "coin", "cup", "star", "links", "headphones", "locks", "shield"] {
            images[name] = UIImage(named: "NeonCardFlight-\(name)-final")!
        }
        return images
    }()
    static func sprite(_ name: String) -> UIImage { sprites[name]! }

    static func color(_ hex: UInt32, alpha: Double = 1) -> UIColor {
        UIColor(red: CGFloat((hex >> 16) & 255) / 255, green: CGFloat((hex >> 8) & 255) / 255,
                blue: CGFloat(hex & 255) / 255, alpha: alpha)
    }

    static func render(_ size: CGSize, _ draw: (CGContext) -> Void) -> UIImage {
        let format = UIGraphicsImageRendererFormat()
        format.scale = 2
        format.opaque = false
        return UIGraphicsImageRenderer(size: size, format: format).image { draw($0.cgContext) }
    }

    static func gradient(_ context: CGContext, path: CGPath, colors: [UInt32], from: CGPoint, to: CGPoint, locations: [CGFloat]? = nil) {
        context.saveGState()
        context.addPath(path); context.clip()
        let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(), colors: colors.map { color($0).cgColor } as CFArray,
                                  locations: locations)!
        context.drawLinearGradient(gradient, start: from, end: to, options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
        context.restoreGState()
    }

    static func radial(_ context: CGContext, path: CGPath, center: CGPoint, radius: CGFloat, color hex: UInt32, alpha: Double) {
        context.saveGState()
        context.addPath(path); context.clip()
        let gradient = CGGradient(colorsSpace: CGColorSpaceCreateDeviceRGB(),
                                  colors: [color(hex, alpha: alpha).cgColor, color(hex, alpha: 0).cgColor] as CFArray, locations: [0, 1])!
        context.drawRadialGradient(gradient, startCenter: center, startRadius: 0, endCenter: center, endRadius: radius,
                                   options: [.drawsAfterEndLocation])
        context.restoreGState()
    }

    static func fill(_ context: CGContext, _ path: CGPath, _ hex: UInt32, alpha: Double = 1) {
        context.addPath(path); context.setFillColor(color(hex, alpha: alpha).cgColor); context.fillPath()
    }

    static func stroke(_ context: CGContext, _ path: CGPath, _ hex: UInt32, width: CGFloat, alpha: Double = 1) {
        context.addPath(path); context.setStrokeColor(color(hex, alpha: alpha).cgColor)
        context.setLineWidth(width); context.setLineJoin(.round); context.setLineCap(.round); context.strokePath()
    }

    static func rounded(_ rect: CGRect, _ radius: CGFloat) -> CGPath { CGPath(roundedRect: rect, cornerWidth: radius, cornerHeight: radius, transform: nil) }
    static func ellipse(_ rect: CGRect) -> CGPath { CGPath(ellipseIn: rect, transform: nil) }

    static func shifted(_ path: CGPath, x: CGFloat, y: CGFloat) -> CGPath {
        var transform = CGAffineTransform(translationX: x, y: y)
        return path.copy(using: &transform)!
    }

    static func shadow(_ context: CGContext, _ path: CGPath, blur: CGFloat = 10, y: CGFloat = 7) {
        context.saveGState()
        context.setShadow(offset: CGSize(width: 1, height: y), blur: blur, color: color(0x312346, alpha: 0.20).cgColor)
        fill(context, path, 0x302141, alpha: 0.38)
        context.restoreGState()
    }

    static func text(_ value: String, x: CGFloat, y: CGFloat, size: CGFloat, weight: UIFont.Weight, color hex: UInt32) {
        (value as NSString).draw(at: CGPoint(x: x, y: y), withAttributes: [.font: UIFont.systemFont(ofSize: size, weight: weight), .foregroundColor: color(hex)])
    }

    static let card: UIImage = makeCard(intro: false)
    static let introCard: UIImage = makeCard(intro: true)
    static func makeCard(intro: Bool) -> UIImage { render(CGSize(width: 300, height: 190)) { context in
        let surface = rounded(CGRect(x: 0, y: 0, width: 300, height: 190), 7)
        gradient(context, path: surface, colors: [0xF3FF06, 0xEEFF00, 0xE8F900], from: CGPoint(x: 20, y: 0), to: CGPoint(x: 270, y: 210))
        stroke(context, rounded(CGRect(x: 0.7, y: 0.7, width: 298.6, height: 188.6), 6), 0xF9FF6C, width: 1.4, alpha: 0.8)
        let edge = CGMutablePath(); edge.move(to: CGPoint(x: 4, y: 188)); edge.addLine(to: CGPoint(x: 296, y: 188))
        stroke(context, edge, 0xBECE00, width: 2)
        if intro {
            context.saveGState(); context.translateBy(x: 270,y: 12); context.rotate(by: .pi/2)
            text("Nova",x: 0,y: 0,size: 22,weight: .bold,color: 0x151819); context.restoreGState()
        } else { text("Nova", x: 18, y: 16, size: 22, weight: .bold, color: 0x151819) }
        let chipX: CGFloat = intro ? 105.5 : 237, chipY: CGFloat = intro ? 148 : 131
        let chip = rounded(CGRect(x: chipX, y: chipY, width: 33, height: 27), 4)
        gradient(context, path: chip, colors: [0xE8E7E0, 0xADAFA7, 0xF8F7EE, 0xBCBFB4], from: CGPoint(x: chipX+2, y: chipY), to: CGPoint(x: chipX+32, y: chipY+28), locations: [0, 0.33, 0.62, 1])
        stroke(context, chip, 0x95998C, width: 0.7)
        let grooves = CGMutablePath()
        grooves.move(to: CGPoint(x: chipX+10, y: chipY+1)); grooves.addLine(to: CGPoint(x: chipX+10, y: chipY+26))
        grooves.move(to: CGPoint(x: chipX+23, y: chipY+1)); grooves.addLine(to: CGPoint(x: chipX+23, y: chipY+26))
        grooves.move(to: CGPoint(x: chipX+1, y: chipY+9)); grooves.addLine(to: CGPoint(x: chipX+32, y: chipY+9))
        grooves.move(to: CGPoint(x: chipX+1, y: chipY+18)); grooves.addLine(to: CGPoint(x: chipX+32, y: chipY+18))
        stroke(context, grooves, 0x8F9486, width: 0.65)
        radial(context, path: surface, center: CGPoint(x: 86, y: -110), radius: 300, color: 0xFFFFFF, alpha: 0.12)
    } }

}
