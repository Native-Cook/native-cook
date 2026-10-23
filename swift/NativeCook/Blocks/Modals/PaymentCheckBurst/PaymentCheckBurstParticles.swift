import SwiftUI

/// The particles of the screen, in two tables built from the same seeded numbers on every stack:
/// green confetti that falls from the top, and silver coins that spill out of the reward bar.
enum PaymentCheckBurstParticles {
    private typealias M = PaymentCheckBurstMotion

    // Green confetti falls from the top of the screen.
    private static let confetti = 42
    private static let confettiColors: [Color] = [0x2fbf71, 0x7be0a8, 0x16a05a, 0xb9f2d2].map { Color(hex: $0) }
    private static let confettiSpread = 1.2
    private static let fallMin = 2.0
    private static let fallMax = 2.8
    private static let sway = 18.0
    private static let spin = 540.0

    // Silver coins spill out of the reward bar and tumble down.
    private static let coins = 12
    private static let coinMin = 16.0
    private static let coinMax = 30.0
    private static let coinSpeedMin = 220.0
    private static let coinSpeedMax = 520.0
    private static let coinFan = 62.0
    private static let coinGravity = 1100.0
    private static let coinLife = 2.2
    private static let coinFadeAt = 1.6
    private static let coinSpread = 0.45

    private struct Piece {
        let color: Color
        /// Every third piece is a thin strip.
        let strip: Bool
        let x: Double
        let drift: Double
        let delay: Double
        let fall: Double
        let sway: Double
        let phase: Double
        let spin: Double
    }

    private struct Coin {
        let size: Double
        let vx: Double
        let vy: Double
        let delay: Double
        let spin: Double
        let tilt: Double
    }

    private static let confettiTable: [Piece] = (0..<confetti).map { i in
        Piece(
            color: confettiColors[i % confettiColors.count],
            strip: i % 3 == 0,
            x: 0.04 + M.hash(i, 1) * 0.92,
            drift: (M.hash(i, 2) - 0.5) * 70,
            delay: M.hash(i, 3) * confettiSpread,
            fall: fallMin + M.hash(i, 4) * (fallMax - fallMin),
            sway: 0.6 + M.hash(i, 5) * 1.2,
            phase: M.hash(i, 6) * tau,
            spin: (M.hash(i, 7) < 0.5 ? -1 : 1) * (0.4 + M.hash(i, 8) * 0.6) * spin
        )
    }

    private static let coinTable: [Coin] = (0..<coins).map { i in
        let angle = (M.hash(i, 11) - 0.5) * 2 * coinFan * .pi / 180
        let speed = coinSpeedMin + M.hash(i, 12) * (coinSpeedMax - coinSpeedMin)
        return Coin(
            size: coinMin + M.hash(i, 13) * (coinMax - coinMin),
            vx: sin(angle) * speed,
            vy: -cos(angle) * speed,
            delay: M.hash(i, 14) * coinSpread,
            spin: 3 + M.hash(i, 15) * 5,
            tilt: (M.hash(i, 16) - 0.5) * 70
        )
    }

    /// Green squares and strips falling from the top of the screen.
    static func drawConfetti(_ context: GraphicsContext, time: Double, at: Double, size: CGSize) {
        for piece in confettiTable {
            let age = time - at - piece.delay
            if age < 0 || age > piece.fall { continue }
            let p = age / piece.fall
            let w = piece.strip ? 4.0 : 9.0
            let h = piece.strip ? 16.0 : 9.0
            let x = piece.x * size.width + piece.drift * p + sway * sin(p * piece.sway * tau + piece.phase)
            let y = -10 + size.height * 0.9 * pow(p, 1.25)
            let pop = M.easeOut(M.seg(p, 0, 0.06))
            var layer = context
            layer.opacity = 1 - M.seg(p, 0.6, 1)
            layer.translateBy(x: x + w / 2, y: y + h / 2)
            layer.rotate(by: .degrees(piece.spin * p))
            layer.scaleBy(x: pop, y: pop)
            layer.fill(Path(roundedRect: CGRect(x: -w / 2, y: -h / 2, width: w, height: h), cornerRadius: 2), with: .color(piece.color))
        }
    }

    /// Silver coins thrown out of one point.
    static func drawCoins(_ context: GraphicsContext, time: Double, at: Double, origin: CGPoint) {
        for coin in coinTable {
            let age = time - at - coin.delay
            if age < 0 || age > coinLife { continue }
            let s = coin.size
            let pop = M.easeOut(M.seg(age, 0, 0.1))
            var layer = context
            layer.opacity = 1 - M.seg(age, coinFadeAt, coinLife)
            layer.translateBy(x: origin.x + coin.vx * age + s / 2, y: origin.y + coin.vy * age + 0.5 * coinGravity * age * age + s / 2)
            layer.rotate(by: .degrees(coin.tilt))
            // Turning about its vertical axis: the coin's width follows the cosine.
            layer.scaleBy(x: pop * max(0.12, abs(cos(age * coin.spin))), y: pop)
            let disc = CGRect(x: -s / 2, y: -s / 2, width: s, height: s)
            layer.fill(
                Path(ellipseIn: disc),
                with: .linearGradient(
                    Gradient(colors: [Color(hex: 0xf4f4f6), Color(hex: 0xa9aaaf)]),
                    startPoint: CGPoint(x: disc.minX + 0.2 * s, y: disc.minY),
                    endPoint: CGPoint(x: disc.minX + 0.8 * s, y: disc.maxY)
                )
            )
            layer.stroke(Path(ellipseIn: disc.insetBy(dx: 2.5, dy: 2.5)), with: .color(.rgba(120, 120, 126, 0.6)), lineWidth: 1)
            layer.fill(star(radius: s * 0.45 * 0.46), with: .color(.white.opacity(0.95)))
        }
    }

    /// A five-pointed star centred on the origin, the size of the icon font's star at 0.45 of the coin.
    private static func star(radius: Double) -> Path {
        var path = Path()
        for i in 0..<10 {
            let r = i % 2 == 0 ? radius : radius * 0.48
            let a = Double(i) * .pi / 5 - .pi / 2
            let point = CGPoint(x: cos(a) * r, y: sin(a) * r + radius * 0.06)
            if i == 0 { path.move(to: point) } else { path.addLine(to: point) }
        }
        path.closeSubpath()
        return path
    }
}
