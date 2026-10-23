import CoreText
import SwiftUI
import UIKit

/// A friends list drawn as a stack of thick direction signs. The whole post turns in 3D from one
/// deterministic heading clock; dragging takes over that same heading and releases into a decay.
struct StackedColourCards: View {
    static let loop: Double = 7.2

    var autoPlay = false

    @Environment(\.accessibilityReduceMotion) private var reduceMotion
    @Environment(\.scenePhase) private var scenePhase
    @State private var motion = StackedColourCardsMotion()
    @State private var hapticTrigger = 0

    var body: some View {
        GeometryReader { proxy in
            let insets = proxy.safeAreaInsets
            let size = CGSize(
                width: proxy.size.width + insets.leading + insets.trailing,
                height: proxy.size.height + insets.top + insets.bottom
            )
            stage(size: size)
                .frame(width: size.width, height: size.height, alignment: .topLeading)
                .offset(x: -insets.leading, y: -insets.top)
        }
        .background(StackedColourCardsMetrics.backgroundTop.ignoresSafeArea())
        .preferredColorScheme(.dark)
        .statusBarHidden(false)
        .sensoryFeedback(.selection, trigger: hapticTrigger)
    }

    private func stage(size: CGSize) -> some View {
        let paused = scenePhase != .active || !motion.needsFrames(
            at: .now,
            autoPlay: autoPlay,
            reducedMotion: reduceMotion
        )
        return TimelineView(.animation(paused: paused)) { timeline in
            let heading = motion.heading(
                at: timeline.date,
                autoPlay: autoPlay,
                reducedMotion: reduceMotion
            )
            ZStack(alignment: .topLeading) {
                LinearGradient(
                    stops: [
                        .init(color: StackedColourCardsMetrics.backgroundTop, location: 0),
                        .init(color: StackedColourCardsMetrics.backgroundMiddle, location: 0.7),
                        .init(color: StackedColourCardsMetrics.backgroundBottom, location: 1),
                    ],
                    startPoint: .top,
                    endPoint: .bottom
                )

                ForEach(StackedColourCardsMetrics.signs.reversed()) { sign in
                    StackedColourCardsBoard(sign: sign, heading: heading, centreX: size.width / 2)
                }

                ForEach(StackedColourCardsMetrics.signs.filter { $0.named }) { sign in
                    Color.clear
                        .frame(width: 220, height: sign.height)
                        .offset(x: size.width / 2 - 110, y: sign.y - sign.height / 2)
                        .accessibilityElement()
                        .accessibilityLabel(sign.distance.map { "\(sign.name), \($0) kilometres away" } ?? sign.name)
                }

                StackedColourCardsTouchSurface { event in
                    let now = Date()
                    switch event {
                    case let .began(x):
                        let current = motion.heading(at: now, autoPlay: autoPlay, reducedMotion: reduceMotion)
                        motion.begin(at: x, heading: current)
                        hapticTrigger += 1
                    case let .changed(x):
                        motion.change(to: x)
                    case let .ended(velocityX):
                        motion.end(at: now, velocityX: velocityX, reducedMotion: reduceMotion)
                    }
                }
            }
            .frame(width: size.width, height: size.height, alignment: .topLeading)
            .clipped()
        }
        .frame(width: size.width, height: size.height, alignment: .topLeading)
    }
}

private extension StackedColourCards {
    struct Sign: Identifiable {
        let name: String
        let named: Bool
        let nameOffset: Double
        let distance: Int?
        let color: UInt32
        let ink: UInt32
        let heading: Double
        let y: Double
        let x: Double
        let length: Double
        let tail: Double
        let height: Double
        let depth: Double
        let tip: Double

        var id: String { name }

        init(
            _ name: String,
            named: Bool = true,
            nameOffset: Double = 0,
            distance: Int? = nil,
            color: UInt32,
            ink: UInt32 = 0xffffff,
            heading: Double,
            y: Double,
            x: Double,
            length: Double,
            tail: Double,
            height: Double,
            depth: Double,
            tip: Double
        ) {
            self.name = name
            self.named = named
            self.nameOffset = nameOffset
            self.distance = distance
            self.color = color
            self.ink = ink
            self.heading = heading
            self.y = y
            self.x = x
            self.length = length
            self.tail = tail
            self.height = height
            self.depth = depth
            self.tip = tip
        }
    }
}

private enum StackedColourCardsMetrics {
    static let backgroundTop = Color.stackedColourCards(hex: 0x112f26)
    static let backgroundMiddle = Color.stackedColourCards(hex: 0x123a30)
    static let backgroundBottom = Color.stackedColourCards(hex: 0x0e5bc7)

    static let pitch = 16.0
    static let focal = 930.0
    static let shadeFace = 1.0
    static let shadeTop = 1.12
    static let shadeBottom = 0.62
    static let shadeEnd = 0.8
    static let shadeSide = 0.1
    static let nameSize = 28.0
    static let distanceSize = 9.0
    static let distanceInset = 28.0

    static let signs: [StackedColourCards.Sign] = [
        .init("Jason", distance: 23, color: 0x74b6a0, heading: 8, y: 24, x: 0, length: 292, tail: 0.575, height: 34, depth: 20, tip: 26),
        .init("Kenji", named: false, color: 0xe64d50, heading: 64, y: 86, x: -41, length: 138, tail: 0.515, height: 38, depth: 18, tip: 20),
        .init("Dylan", nameOffset: 12, distance: 46, color: 0xdc5c2a, ink: 0xf6c39d, heading: 3, y: 128, x: 4, length: 270, tail: 0.515, height: 51, depth: 12.5, tip: 32),
        .init("Priya", named: false, color: 0xdbb66d, ink: 0xc9a85a, heading: 8, y: 175, x: -26, length: 150, tail: 0.5, height: 71, depth: 27, tip: 0),
        .init("Logan", named: false, color: 0xed977f, heading: 37, y: 234.5, x: -24, length: 132, tail: 0.489, height: 55, depth: 10.5, tip: 0),
        .init("Mason", named: false, color: 0xe16228, heading: 47, y: 284.5, x: -16, length: 142, tail: 0.402, height: 46, depth: 10.5, tip: 20),
        .init("Noah", nameOffset: 13, color: 0xefa280, ink: 0xe8915f, heading: 8, y: 343, x: 5, length: 269, tail: 0.51, height: 57, depth: 15.5, tip: 30),
        .init("Tomás", named: false, color: 0x1051cd, heading: 2.5, y: 390, x: -5, length: 104, tail: 0.577, height: 27, depth: 31.5, tip: 0),
        .init("Caleb", nameOffset: -25, distance: 23, color: 0x0a65db, heading: 168, y: 434.5, x: 5, length: 196, tail: 0.28, height: 65, depth: 10.5, tip: 32),
        .init("Amara", named: false, color: 0xb0c9d9, heading: 11, y: 482.5, x: -12, length: 102, tail: 0.492, height: 42, depth: 34, tip: 0),
        .init("Sam", nameOffset: 4, color: 0xf0aa88, ink: 0xe8955f, heading: 4, y: 540, x: 1, length: 232, tail: 0.48, height: 62, depth: 18, tip: 28),
        .init("Lena", named: false, color: 0xa4b44e, heading: 23, y: 615, x: 8, length: 132, tail: 0.485, height: 93, depth: 32, tip: 20),
        .init("Jack", nameOffset: 22, color: 0xf2918a, ink: 0xf7c0b8, heading: 13, y: 699, x: -1, length: 228, tail: 0.48, height: 68, depth: 12, tip: 0),
        .init("Owen", nameOffset: -2, color: 0xf3a986, ink: 0xe99a6a, heading: -8, y: 765.5, x: 0, length: 170, tail: 0.5, height: 75, depth: 18, tip: 0),
        .init("Ryan", nameOffset: 45, distance: 21, color: 0xec6647, ink: 0xf6c0a0, heading: -3, y: 839.5, x: 0, length: 214, tail: 0.45, height: 72, depth: 14, tip: 28),
    ]
}

private struct StackedColourCardsMotion {
    private enum Phase { case automatic, dragging, released, idle }

    private static let keys: [(time: Double, heading: Double)] = [
        (0, 0), (0.6, -11), (1.2, -23), (1.8, -35), (2.4, -18), (3, -7),
        (3.6, 0), (4.2, 3), (4.8, -17), (5.4, -3), (6, -8), (6.6, -15), (7.2, 0),
    ]
    private static let resumeAfter = 3.5
    private static let returnDuration = 0.7
    private static let degreesPerPoint = 0.45
    private static let deceleration = 0.994
    private static let slopeFactor = 0.1

    private var phase = Phase.automatic
    private var loopStart = Date()
    private var dragStartX = 0.0
    private var dragStartHeading = 0.0
    private var manualHeading = 0.0
    private var releaseHeading = 0.0
    private var releaseVelocity = 0.0
    private var releaseDate = Date()

    mutating func begin(at x: Double, heading: Double) {
        phase = .dragging
        dragStartX = x
        dragStartHeading = heading
        manualHeading = heading
    }

    mutating func change(to x: Double) {
        guard phase == .dragging else { return }
        manualHeading = dragStartHeading - (x - dragStartX) * Self.degreesPerPoint
    }

    mutating func end(at date: Date, velocityX: Double, reducedMotion: Bool) {
        releaseHeading = manualHeading
        releaseVelocity = -velocityX * Self.degreesPerPoint
        releaseDate = date
        phase = reducedMotion ? .idle : .released
    }

    func needsFrames(at date: Date, autoPlay: Bool, reducedMotion: Bool) -> Bool {
        guard !reducedMotion else { return false }
        if autoPlay { return true }
        guard phase == .released else { return false }
        return abs(decayVelocity(age: max(0, date.timeIntervalSince(releaseDate)))) >= 1
    }

    func heading(at date: Date, autoPlay: Bool, reducedMotion: Bool) -> Double {
        switch phase {
        case .dragging, .idle:
            return manualHeading
        case .automatic:
            guard autoPlay, !reducedMotion else { return 0 }
            return Self.headingAt(date.timeIntervalSince(loopStart).truncatingRemainder(dividingBy: StackedColourCards.loop))
        case .released:
            guard !reducedMotion else { return manualHeading }
            let age = max(0, date.timeIntervalSince(releaseDate))
            if !autoPlay || age < Self.resumeAfter {
                return releaseHeading + decayDistance(age: age)
            }
            let returnFrom = Self.normalised(releaseHeading + decayDistance(age: Self.resumeAfter))
            if age < Self.resumeAfter + Self.returnDuration {
                let k = min(1, (age - Self.resumeAfter) / Self.returnDuration)
                let smooth = k * k * (3 - 2 * k)
                return returnFrom * (1 - smooth)
            }
            let loopAge = (age - Self.resumeAfter - Self.returnDuration)
                .truncatingRemainder(dividingBy: StackedColourCards.loop)
            return Self.headingAt(loopAge)
        }
    }

    private func decayVelocity(age: Double) -> Double {
        let lambda = (1 - Self.deceleration) * Self.slopeFactor * 1_000
        return releaseVelocity * exp(-lambda * age)
    }

    private func decayDistance(age: Double) -> Double {
        let lambda = (1 - Self.deceleration) * Self.slopeFactor * 1_000
        guard lambda > 0 else { return releaseVelocity * age }
        return releaseVelocity * (1 - exp(-lambda * age)) / lambda
    }

    private static func normalised(_ value: Double) -> Double {
        (value.truncatingRemainder(dividingBy: 360) + 540).truncatingRemainder(dividingBy: 360) - 180
    }

    private static func headingAt(_ time: Double) -> Double {
        var index = 0
        while index < keys.count - 2, keys[index + 1].time <= time { index += 1 }
        let t1 = keys[index].time
        let v1 = keys[index].heading
        let t2 = keys[index + 1].time
        let v2 = keys[index + 1].heading
        let v0 = index > 0 ? keys[index - 1].heading : keys[keys.count - 2].heading
        let v3 = index + 2 < keys.count ? keys[index + 2].heading : keys[1].heading
        let u = min(1, max(0, (time - t1) / (t2 - t1)))
        let u2 = u * u
        let u3 = u2 * u
        return 0.5 * (2 * v1 + (-v0 + v2) * u + (2 * v0 - 5 * v1 + 4 * v2 - v3) * u2 + (-v0 + 3 * v1 - 3 * v2 + v3) * u3)
    }
}

private struct StackedColourCardsBoard: View {
    let sign: StackedColourCards.Sign
    let heading: Double
    let centreX: Double

    var body: some View {
        let geometry = StackedColourCardsBoardGeometry(sign: sign, heading: heading, centreX: centreX)
        ZStack(alignment: .topLeading) {
            Canvas { context, _ in
                context.fill(geometry.top, with: .color(.stackedColourCards(hex: StackedColourCardsColor.shade(sign.color, by: StackedColourCardsMetrics.shadeTop))))
                context.fill(geometry.end, with: .color(.stackedColourCards(hex: StackedColourCardsColor.shade(sign.color, by: StackedColourCardsMetrics.shadeEnd))))
                context.fill(geometry.tipLower, with: .color(.stackedColourCards(hex: StackedColourCardsColor.shade(sign.color, by: sign.tip > 0 ? 0.74 : StackedColourCardsMetrics.shadeEnd))))
                context.fill(geometry.tipUpper, with: .color(.stackedColourCards(hex: StackedColourCardsColor.shade(sign.color, by: 0.92))))
                context.fill(geometry.face, with: .color(.stackedColourCards(hex: sign.color)))
            }

            StackedColourCardsLettering(sign: sign, geometry: geometry)
                .frame(width: geometry.faceWidth, height: sign.height, alignment: .topLeading)
                .projectionEffect(geometry.textProjection)
                .opacity(geometry.inkOpacity)
        }
        .frame(maxWidth: .infinity, maxHeight: .infinity, alignment: .topLeading)
        .allowsHitTesting(false)
        .accessibilityHidden(true)
    }
}

private struct StackedColourCardsLettering: View {
    let sign: StackedColourCards.Sign
    let geometry: StackedColourCardsBoardGeometry

    var body: some View {
        Canvas { context, _ in
            let nameFont = UIFont.systemFont(ofSize: StackedColourCardsMetrics.nameSize, weight: .medium)
            let distanceFont = UIFont.systemFont(ofSize: StackedColourCardsMetrics.distanceSize, weight: .medium)
            let nameWidth = StackedColourCardsText.width(sign.name, font: nameFont)
            let distanceText = sign.distance.map { "\($0) km" } ?? ""
            let distanceWidth = StackedColourCardsText.width(distanceText, font: distanceFont)
            let nameBaseline = sign.height / 2 + StackedColourCardsMetrics.nameSize * 0.36 - (distanceText.isEmpty ? 0 : 2)

            if sign.named {
                StackedColourCardsText.draw(
                    sign.name,
                    in: context,
                    font: .system(size: StackedColourCardsMetrics.nameSize, weight: .medium),
                    color: .stackedColourCards(hex: sign.ink),
                    x: (geometry.faceWidth - nameWidth) / 2 + sign.nameOffset,
                    baseline: nameBaseline
                )
            }
            if !distanceText.isEmpty {
                StackedColourCardsText.draw(
                    distanceText,
                    in: context,
                    font: .system(size: StackedColourCardsMetrics.distanceSize, weight: .medium),
                    color: .stackedColourCards(hex: sign.ink),
                    x: geometry.front
                        ? geometry.faceWidth - StackedColourCardsMetrics.distanceInset - distanceWidth
                        : StackedColourCardsMetrics.distanceInset,
                    baseline: sign.height - 6
                )
            }
        }
    }
}

private struct StackedColourCardsBoardGeometry {
    let face: Path
    let top: Path
    let end: Path
    let tipUpper: Path
    let tipLower: Path
    let textProjection: ProjectionTransform
    let inkOpacity: Double
    let faceWidth: Double
    let front: Bool

    init(sign: StackedColourCards.Sign, heading: Double, centreX: Double) {
        let halfHeight = sign.height / 2
        let halfDepth = sign.depth / 2
        let tailX = -sign.length * sign.tail
        let pointX = sign.length - sign.length * sign.tail
        let shoulderX = pointX - sign.tip
        faceWidth = shoulderX - tailX
        let tipLength = hypot(sign.tip, halfHeight)
        let originX = centreX + sign.x
        let yaw = sign.heading + heading

        func p(_ x: Double, _ y: Double, _ z: Double) -> CGPoint {
            StackedColourCardsProjection.project(x: x, y: y, z: z, yaw: yaw, centreX: originX, centreY: sign.y)
        }
        func polygon(_ points: [CGPoint]) -> Path {
            guard let first = points.first else { return Path() }
            var path = Path()
            path.move(to: first)
            for point in points.dropFirst() { path.addLine(to: point) }
            path.closeSubpath()
            return path
        }

        front = StackedColourCardsProjection.facing(nx: 0, ny: 0, nz: 1, yaw: yaw) > 0
        let z = front ? halfDepth : -halfDepth
        face = polygon([p(tailX, halfHeight, z), p(shoulderX, halfHeight, z), p(pointX, 0, z), p(shoulderX, -halfHeight, z), p(tailX, -halfHeight, z)])
        top = StackedColourCardsProjection.facing(nx: 0, ny: 1, nz: 0, yaw: yaw) > 0
            ? polygon([p(tailX, halfHeight, -halfDepth), p(shoulderX, halfHeight, -halfDepth), p(shoulderX, halfHeight, halfDepth), p(tailX, halfHeight, halfDepth)])
            : Path()
        end = StackedColourCardsProjection.facing(nx: -1, ny: 0, nz: 0, yaw: yaw) > 0
            ? polygon([p(tailX, halfHeight, -halfDepth), p(tailX, halfHeight, halfDepth), p(tailX, -halfHeight, halfDepth), p(tailX, -halfHeight, -halfDepth)])
            : Path()

        if sign.tip > 0 {
            tipUpper = StackedColourCardsProjection.facing(nx: halfHeight / tipLength, ny: sign.tip / tipLength, nz: 0, yaw: yaw) > 0
                ? polygon([p(shoulderX, halfHeight, -halfDepth), p(pointX, 0, -halfDepth), p(pointX, 0, halfDepth), p(shoulderX, halfHeight, halfDepth)])
                : Path()
            tipLower = StackedColourCardsProjection.facing(nx: halfHeight / tipLength, ny: -sign.tip / tipLength, nz: 0, yaw: yaw) > 0
                ? polygon([p(shoulderX, -halfHeight, -halfDepth), p(pointX, 0, -halfDepth), p(pointX, 0, halfDepth), p(shoulderX, -halfHeight, halfDepth)])
                : Path()
        } else {
            tipUpper = Path()
            tipLower = StackedColourCardsProjection.facing(nx: 1, ny: 0, nz: 0, yaw: yaw) > 0
                ? polygon([p(pointX, halfHeight, -halfDepth), p(pointX, halfHeight, halfDepth), p(pointX, -halfHeight, halfDepth), p(pointX, -halfHeight, -halfDepth)])
                : Path()
        }

        let a = front ? tailX : shoulderX
        let b = front ? shoulderX : tailX
        textProjection = StackedColourCardsProjection.rectToQuad(
            width: faceWidth,
            height: sign.height,
            p0: p(a, halfHeight, z),
            p1: p(b, halfHeight, z),
            p2: p(b, -halfHeight, z),
            p3: p(a, -halfHeight, z)
        )
        let facing = abs(StackedColourCardsProjection.facing(nx: 0, ny: 0, nz: 1, yaw: yaw))
        inkOpacity = min(1, max(0, (facing - 0.18) / 0.22))
    }
}

private enum StackedColourCardsProjection {
    private static let radians = Double.pi / 180
    private static let sinPitch = sin(StackedColourCardsMetrics.pitch * radians)
    private static let cosPitch = cos(StackedColourCardsMetrics.pitch * radians)

    static func project(x: Double, y: Double, z: Double, yaw: Double, centreX: Double, centreY: Double) -> CGPoint {
        let cosine = cos(yaw * radians)
        let sine = sin(yaw * radians)
        let projectedX = x * cosine + z * sine
        let depth = -x * sine + z * cosine
        let projectedY = y * cosPitch - depth * sinPitch
        let pitchedDepth = depth * cosPitch + y * sinPitch
        let scale = StackedColourCardsMetrics.focal / (StackedColourCardsMetrics.focal - pitchedDepth)
        return CGPoint(x: centreX + projectedX * scale, y: centreY - projectedY * scale)
    }

    static func facing(nx: Double, ny: Double, nz: Double, yaw: Double) -> Double {
        let depth = -nx * sin(yaw * radians) + nz * cos(yaw * radians)
        return depth * cosPitch + ny * sinPitch
    }

    static func rectToQuad(width: Double, height: Double, p0: CGPoint, p1: CGPoint, p2: CGPoint, p3: CGPoint) -> ProjectionTransform {
        let dx1 = p1.x - p2.x
        let dx2 = p3.x - p2.x
        let dx3 = p0.x - p1.x + p2.x - p3.x
        let dy1 = p1.y - p2.y
        let dy2 = p3.y - p2.y
        let dy3 = p0.y - p1.y + p2.y - p3.y
        var g = 0.0
        var h = 0.0
        if abs(dx3) > 1e-6 || abs(dy3) > 1e-6 {
            let determinant = dx1 * dy2 - dx2 * dy1
            if abs(determinant) > 1e-9 {
                g = (dx3 * dy2 - dx2 * dy3) / determinant
                h = (dx1 * dy3 - dx3 * dy1) / determinant
            }
        }
        var transform = ProjectionTransform()
        transform.m11 = (p1.x - p0.x + g * p1.x) / width
        transform.m21 = (p3.x - p0.x + h * p3.x) / height
        transform.m31 = p0.x
        transform.m12 = (p1.y - p0.y + g * p1.y) / width
        transform.m22 = (p3.y - p0.y + h * p3.y) / height
        transform.m32 = p0.y
        transform.m13 = g / width
        transform.m23 = h / height
        transform.m33 = 1
        return transform
    }
}

private enum StackedColourCardsText {
    static func width(_ text: String, font: UIFont) -> Double {
        guard !text.isEmpty else { return 0 }
        let line = CTLineCreateWithAttributedString(NSAttributedString(string: text, attributes: [.font: font]))
        return CTLineGetTypographicBounds(line, nil, nil, nil)
    }

    static func draw(_ text: String, in context: GraphicsContext, font: Font, color: Color, x: Double, baseline: Double) {
        let resolved = context.resolve(Text(text).font(font).foregroundStyle(color))
        let size = resolved.measure(in: CGSize(width: 2_000, height: 200))
        let firstBaseline = resolved.firstBaseline(in: size)
        context.draw(resolved, at: CGPoint(x: x, y: baseline - Double(firstBaseline)), anchor: .topLeading)
    }
}

private enum StackedColourCardsColor {
    static func shade(_ hex: UInt32, by factor: Double) -> UInt32 {
        func component(_ value: UInt32) -> UInt32 {
            let source = Double(value)
            let changed = factor >= 1 ? source + (255 - source) * (factor - 1) : source * factor
            return UInt32(min(255, max(0, changed.rounded())))
        }
        let red = component((hex >> 16) & 255)
        let green = component((hex >> 8) & 255)
        let blue = component(hex & 255)
        return (red << 16) | (green << 8) | blue
    }
}

private extension Color {
    static func stackedColourCards(hex: UInt32) -> Color {
        Color(
            .sRGB,
            red: Double((hex >> 16) & 255) / 255,
            green: Double((hex >> 8) & 255) / 255,
            blue: Double(hex & 255) / 255
        )
    }
}

private enum StackedColourCardsTouchEvent {
    case began(Double)
    case changed(Double)
    case ended(velocityX: Double)
}

/// A zero-delay UIKit hold recognizer keeps the horizontal turn alive beside the gallery back swipe.
private struct StackedColourCardsTouchSurface: UIViewRepresentable {
    let onEvent: (StackedColourCardsTouchEvent) -> Void

    func makeUIView(context: Context) -> UIView {
        let view = UIView()
        view.backgroundColor = .clear
        let recognizer = UILongPressGestureRecognizer(target: context.coordinator, action: #selector(Coordinator.receive(_:)))
        recognizer.minimumPressDuration = 0
        recognizer.allowableMovement = .greatestFiniteMagnitude
        recognizer.cancelsTouchesInView = false
        recognizer.delegate = context.coordinator
        view.addGestureRecognizer(recognizer)
        return view
    }

    func updateUIView(_ uiView: UIView, context: Context) {
        context.coordinator.parent = self
    }

    func makeCoordinator() -> Coordinator { Coordinator(parent: self) }

    final class Coordinator: NSObject, UIGestureRecognizerDelegate {
        var parent: StackedColourCardsTouchSurface
        private var previousX = 0.0
        private var previousTime = 0.0
        private var velocityX = 0.0

        init(parent: StackedColourCardsTouchSurface) {
            self.parent = parent
        }

        func gestureRecognizer(
            _ gestureRecognizer: UIGestureRecognizer,
            shouldRecognizeSimultaneouslyWith otherGestureRecognizer: UIGestureRecognizer
        ) -> Bool { true }

        @objc func receive(_ recognizer: UILongPressGestureRecognizer) {
            let x = recognizer.location(in: recognizer.view).x
            let now = ProcessInfo.processInfo.systemUptime
            switch recognizer.state {
            case .began:
                previousX = x
                previousTime = now
                velocityX = 0
                parent.onEvent(.began(x))
            case .changed:
                let elapsed = now - previousTime
                if elapsed > 0 { velocityX = (x - previousX) / elapsed }
                previousX = x
                previousTime = now
                parent.onEvent(.changed(x))
            case .ended, .cancelled, .failed:
                parent.onEvent(.ended(velocityX: velocityX))
            default:
                break
            }
        }
    }
}
