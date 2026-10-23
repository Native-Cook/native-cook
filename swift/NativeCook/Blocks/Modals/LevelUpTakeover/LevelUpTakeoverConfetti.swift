import SwiftUI

/// Paper cards, ribbons and dots thrown up and out from one point; none of them take touches.
struct LevelUpTakeoverConfetti: View {
    /// Seconds into the loop.
    let time: Double
    /// Second of the loop the burst fires at.
    let at: Double
    /// The point the pieces leave from.
    let origin: CGPoint
    /// How many of the table's pieces to fire.
    var count = pieces

    private typealias M = LevelUpTakeoverMotion

    private static let pieces = 48
    private static let colors: [Color] = [0xff4d5e, 0x2f6bff, 0x2fc96b, 0xffd21a, 0xff8a2a, 0xb65cff].map { Color(hex: $0) }
    /// Width, height and corner of the three shapes: a card, a ribbon and a dot.
    private static let shapes: [(w: Double, h: Double, r: Double)] = [(9, 14, 2), (6, 20, 3), (10, 10, 5)]
    /// Launch speed in points a second, the fan's half-angle around straight up, gravity and air drag.
    private static let speedMin = 520.0
    private static let speedMax = 1050.0
    private static let fan = 78.0
    private static let gravity = 900.0
    private static let drag = 1.8
    private static let life = 2.4
    private static let fadeAt = 2.0
    private static let spin = 900.0

    private struct Piece {
        let color: Color
        let shape: Int
        let vx: Double
        let vy: Double
        let spin: Double
        let flutter: Double
    }

    /// One fixed table, so every burst, in every loop, is the same.
    private static let table: [Piece] = (0..<pieces).map { i in
        let angle = (M.hash(i, 1) - 0.5) * 2 * fan * .pi / 180
        let speed = speedMin + M.hash(i, 2) * (speedMax - speedMin)
        return Piece(
            color: colors[i % colors.count],
            shape: Int(M.hash(i, 6) * Double(shapes.count)),
            vx: sin(angle) * speed,
            vy: -cos(angle) * speed,
            spin: (M.hash(i, 3) < 0.5 ? -1 : 1) * (0.4 + M.hash(i, 4) * 0.6) * spin,
            flutter: 0.5 + M.hash(i, 5)
        )
    }

    var body: some View {
        Canvas { context, _ in
            let age = time - at
            if age < 0 || age > Self.life { return }
            // Speed dies away to a steady fall: the usual drag-and-gravity path in closed form.
            let settle = (1 - exp(-Self.drag * age)) / Self.drag
            let fall = Self.gravity / Self.drag
            let pop = M.easeOut(M.seg(age, 0, 0.08))
            var layer = context
            layer.opacity = 1 - M.seg(age, Self.fadeAt, Self.life)
            for piece in Self.table.prefix(count) {
                let shape = Self.shapes[piece.shape]
                var bit = layer
                bit.translateBy(x: origin.x + piece.vx * settle, y: origin.y + (piece.vy - fall) * settle + fall * age)
                bit.rotate(by: .degrees(piece.spin * age))
                // Turning edge-on as it tumbles, like paper.
                bit.scaleBy(x: pop, y: (0.35 + 0.65 * abs(cos(age * piece.flutter * 6))) * pop)
                let rect = CGRect(x: -shape.w / 2, y: -shape.h / 2, width: shape.w, height: shape.h)
                bit.fill(Path(roundedRect: rect, cornerRadius: shape.r), with: .color(piece.color))
            }
        }
        .allowsHitTesting(false)
    }
}
