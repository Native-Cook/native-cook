import SwiftUI

enum LiquidSwipePagesArt {
    case tiles, flag, coins
}

/// A page's picture: flat shapes in white and in shades of the page's colour, in a 300 × 340 box.
struct LiquidSwipePagesArtwork: View {
    let kind: LiquidSwipePagesArt
    /// The page's colour: the tiles' icons are cut out of it.
    let color: Color
    /// Seconds since the screen appeared.
    let now: Double
    let still: Bool

    static let size = CGSize(width: 300, height: 340)

    // Tiles: five app tiles juggled on an arc over a disc. Each bobs, a fifth of a turn after its neighbour.
    private static let tile = 60.0
    private static let tileRadius = 16.0
    // A symbol is drawn larger than an icon font's glyph of the same size: this matches 28.
    private static let tileIcon = 23.0
    private static let bob = 6.0
    private static let disc = (x: 150.0, y: 232.0, r: 60.0, core: 36.0)
    private static let tiles: [(icon: String, x: Double, y: Double, tilt: Double)] = [
        ("photo.fill", 26, 175, -12),
        ("play.fill", 74, 112, -6),
        ("cloud.sun.fill", 150, 88, 0),
        ("bubble.left.fill", 226, 112, 6),
        ("slider.horizontal.3", 274, 175, 12),
    ]

    // Flag: a pole planted on a hill, with a second hill behind. The flag's free end waves.
    private static let hill = [CGPoint(x: 30, y: 322), CGPoint(x: 150, y: 202), CGPoint(x: 270, y: 322)]
    private static let backHill = [CGPoint(x: 130, y: 322), CGPoint(x: 214, y: 248), CGPoint(x: 298, y: 322)]
    private static let pole = CGRect(x: 148, y: 18, width: 4, height: 196)
    private static let flag = CGRect(x: 152, y: 24, width: 70, height: 44)
    private static let flagWave = 5.0
    private static let flagTurns = 0.9
    private static let flagSteps = 12

    // Coins: three discs that rise like a chart, and an arrow through them.
    private static let coins: [(x: Double, y: Double, r: Double)] = [(70, 243, 25), (150, 180, 35), (238, 93, 45)]
    private static let coinRim = 7.0
    private static let trend = [CGPoint(x: 24, y: 291), CGPoint(x: 70, y: 243), CGPoint(x: 150, y: 180), CGPoint(x: 238, y: 93), CGPoint(x: 270, y: 61)]
    private static let trendHead = [CGPoint(x: 252, y: 61), CGPoint(x: 270, y: 61), CGPoint(x: 270, y: 79)]

    var body: some View {
        Group {
            switch kind {
            case .tiles: tiles
            case .flag: flag
            case .coins: coins
            }
        }
        .frame(width: Self.size.width, height: Self.size.height)
        .accessibilityHidden(true)
    }

    private var tiles: some View {
        ZStack {
            Circle().fill(.white.opacity(0.16))
                .frame(width: Self.disc.r * 2, height: Self.disc.r * 2)
                .position(x: Self.disc.x, y: Self.disc.y)
            Circle().fill(.white.opacity(0.16))
                .frame(width: Self.disc.core * 2, height: Self.disc.core * 2)
                .position(x: Self.disc.x, y: Self.disc.y)
            ForEach(Array(Self.tiles.enumerated()), id: \.offset) { index, tile in
                let lift = still ? 0 : -Self.bob * sin(2 * .pi * (now / LiquidSwipePagesMotion.ambient + Double(index) / Double(Self.tiles.count)))
                RoundedRectangle(cornerRadius: Self.tileRadius, style: .continuous)
                    .fill(.white.opacity(0.9))
                    .frame(width: Self.tile, height: Self.tile)
                    .overlay {
                        Image(systemName: tile.icon)
                            .font(.system(size: Self.tileIcon, weight: .semibold))
                            .foregroundStyle(color)
                    }
                    .rotationEffect(.degrees(tile.tilt))
                    .position(x: tile.x, y: tile.y + lift)
            }
        }
    }

    private var flag: some View {
        Canvas { context, _ in
            func triangle(_ points: [CGPoint]) -> Path {
                var path = Path()
                path.addLines(points)
                path.closeSubpath()
                return path
            }
            context.fill(triangle(Self.backHill), with: .color(.black.opacity(0.07)))
            context.fill(triangle(Self.hill), with: .color(.black.opacity(0.13)))
            context.fill(Path(roundedRect: Self.pole, cornerRadius: Self.pole.width / 2), with: .color(.white))

            let phase = still ? 0 : 2 * .pi * now / LiquidSwipePagesMotion.ambient
            var top: [CGPoint] = []
            var bottom: [CGPoint] = []
            for i in 0...Self.flagSteps {
                let f = Double(i) / Double(Self.flagSteps)
                let x = Self.flag.minX + Self.flag.width * f
                // Held at the pole, free at the far end.
                let y = Self.flag.minY + Self.flagWave * f * sin(phase - f * Self.flagTurns * 2 * .pi)
                top.append(CGPoint(x: x, y: y))
                bottom.append(CGPoint(x: x, y: y + Self.flag.height))
            }
            context.fill(triangle(top + bottom.reversed()), with: .color(.white))
        }
    }

    private var coins: some View {
        Canvas { context, _ in
            for coin in Self.coins {
                context.fill(Path(ellipseIn: CGRect(x: coin.x - coin.r, y: coin.y - coin.r, width: coin.r * 2, height: coin.r * 2)), with: .color(.white.opacity(0.26)))
            }
            for coin in Self.coins {
                let r = coin.r - Self.coinRim
                context.stroke(Path(ellipseIn: CGRect(x: coin.x - r, y: coin.y - r, width: r * 2, height: r * 2)), with: .color(.white.opacity(0.6)), lineWidth: 2)
            }
            var trend = Path()
            trend.addLines(Self.trend)
            trend.addLines(Self.trendHead)
            context.stroke(trend, with: .color(.white), style: StrokeStyle(lineWidth: 4, lineCap: .round, lineJoin: .round))
        }
    }
}
