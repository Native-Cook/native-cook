import SwiftUI

/// A burst of confetti out of the toast and a short rain from the top; none of it takes touches.
struct TaskToastConfettiConfetti: View {
    /// Seconds into the loop.
    let time: Double
    /// Second of the loop the burst starts at.
    let at: Double
    /// Where the burst starts, and the screen the pieces fall through.
    let origin: CGPoint
    let size: CGSize

    private typealias M = TaskToastConfettiMotion

    /// Pieces thrown out of the toast, and pieces that rain from the top edge a moment later.
    private static let burst = 40
    private static let rain = 16
    private static let colors: [Color] = [0x2b6bff, 0x8fb3ff, 0xf39a10, 0xffd23f, 0xff5b36, 0x2b6bff, 0x5ad19a].map { Color(hex: $0) }
    /// Width, height and corner radius of the three shapes: a card, a ribbon and a dot.
    private static let shapes: [(w: Double, h: Double, r: Double)] = [(10, 14, 2), (6, 20, 3), (10, 10, 5)]
    /// Burst speeds in points per second, the air drag that slows them, and the speed they settle to.
    private static let speedMin = 420.0
    private static let speedMax = 980.0
    private static let drag = 2.4
    private static let fallSpeed = 170.0
    private static let rainDelay = 0.25
    private static let lifeMin = 2.7
    private static let lifeMax = 3.3
    private static let sway = 16.0

    private struct Piece {
        let color: Color
        let shape: Int
        /// Launch velocity, or for the rain the start position across the screen (a fraction).
        let vx: Double
        let vy: Double
        let x: Double
        let rain: Bool
        let delay: Double
        let life: Double
        let swayRate: Double
        let swayPhase: Double
        let spin: Double
        let flipRate: Double
    }

    /// One fixed table, so every loop throws the same confetti.
    private static let table: [Piece] = (0..<(burst + rain)).map { i in
        let rain = i >= burst
        // A fan from a little above the horizon on the left, down, to a little above it on the right.
        let angle = (-14 + M.hash(i, 1) * 208) * .pi / 180
        let speed = speedMin + M.hash(i, 2) * (speedMax - speedMin)
        return Piece(
            color: colors[i % colors.count],
            shape: Int(M.hash(i, 3) * Double(shapes.count)),
            vx: rain ? 0 : cos(angle) * speed,
            vy: rain ? fallSpeed : sin(angle) * speed * 0.85,
            x: 0.04 + M.hash(i, 4) * 0.92,
            rain: rain,
            delay: rain ? rainDelay + M.hash(i, 5) * 0.6 : M.hash(i, 5) * 0.08,
            life: lifeMin + M.hash(i, 6) * (lifeMax - lifeMin),
            swayRate: 1.2 + M.hash(i, 7) * 1.4,
            swayPhase: M.hash(i, 8) * tau,
            spin: (M.hash(i, 9) < 0.5 ? -1 : 1) * (240 + M.hash(i, 10) * 480),
            flipRate: 3 + M.hash(i, 11) * 5
        )
    }

    var body: some View {
        Canvas { context, _ in
            for piece in Self.table {
                let age = time - at - piece.delay
                if age < 0 || age > piece.life { continue }
                let shape = Self.shapes[piece.shape]
                // Thrown out with drag, then settling to a gentle fall: the closed form of v' = -k (v - v_end).
                let slow = (1 - exp(-Self.drag * age)) / Self.drag
                let x = piece.rain ? piece.x * size.width : origin.x + piece.vx * slow
                let y = piece.rain
                    ? -24 + Self.fallSpeed * 1.4 * age
                    : origin.y + Self.fallSpeed * age + (piece.vy - Self.fallSpeed) * slow
                // The flutter grows in as the throw dies down.
                let flutter = min(1, age / 0.7)
                let pop = M.easeOut(M.seg(age, 0, 0.12))
                var layer = context
                layer.opacity = 1 - M.seg(age / piece.life, 0.75, 1)
                layer.translateBy(x: x + Self.sway * flutter * sin(age * piece.swayRate * tau + piece.swayPhase), y: y)
                layer.rotate(by: .degrees(piece.spin * age))
                layer.scaleBy(x: pop, y: cos(age * piece.flipRate + piece.swayPhase) * pop)
                let rect = CGRect(x: -shape.w / 2, y: -shape.h / 2, width: shape.w, height: shape.h)
                layer.fill(Path(roundedRect: rect, cornerRadius: shape.r), with: .color(piece.color))
            }
        }
        .frame(width: size.width, height: size.height)
        .allowsHitTesting(false)
    }
}
