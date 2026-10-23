import CoreGraphics
import Foundation

/// The backdrop behind the revealed cards: a pale plane with soft, slowly turning shapes
/// and motes of light rising over it.
enum BoosterPackBackdrop {
  static let background: UInt32 = 0xE5E9EF
  static let pattern: UInt32 = 0xB9C3CF

  static let ART_WIDTH = 218.0
  static let ART_HEIGHT = ART_WIDTH * (88.0 / 63.0)
  static let ROTATION_DURATION = 38_000.0
  static let ENTRY_SCALE = 1.5
  static let RESTING_SCALE = 1.0
  static let BACKDROP_BLUR = 16.0

  /// Vector silhouettes on a 218 × 304.5 artboard: `M x y L x y … Z`.
  static let PATTERN_PATHS = [
    "M27.1 4.9 L6.2 4.9 L6.9 0 L0 0 L0 8.4 L1.4 5.6 L4.9 6.3 L4.9 43.2 L30.5 13.9 Z",
    "M5.6 66.2 L34 44.6 L56.9 36.2 L88.9 18.8 L102.8 22.3 L152.7 23.7 L164.5 22.3 L174.3 16 L175 22.3 L184 30 L212.4 41.1 L212.4 7 L206.9 4.2 L55.5 4.9 L52.1 12.5 L11.1 54.4 Z",
    "M108.3 34.1 L91.6 36.2 L85.4 42.5 L50.7 52.3 L34 62.7 L20.1 77.3 L11.1 98.3 L11.1 108.7 L19.4 120.5 L19.4 124.7 L4.9 142.8 L4.9 170 L9 185.4 L4.9 186.7 L4.9 212.5 L18.1 216.7 L18.1 209 L12.5 195.8 L16 195.1 L35.4 212.5 L55.5 208.3 L54.2 227.9 L61.1 239 L67.3 241.8 L90.9 239 L97.2 246 L115.2 247.4 L145.1 234.1 L170.1 218.1 L188.8 200 L197.9 186.7 L195.8 174.9 L202.7 157.5 L203.4 140.8 L198.6 127.5 L190.2 118.5 L177.7 117.8 L164.5 105.2 L152 100.3 L136.1 101 L134 103.8 L136.1 111.5 L155.5 115.7 L160.4 127.5 L179.1 142.8 L182.6 151.2 L181.2 163.8 L165.9 171.4 L153.4 188.8 L150.7 188.8 L148.6 183.3 L143.7 181.2 L128.4 198.6 L118 203.5 L102.8 204.9 L89.6 202.1 L86.8 205.6 L82.6 204.9 L77.8 198.6 L56.2 147.7 L51.4 141.5 L43 139.4 L46.5 110.8 L59 83.6 L54.8 72.5 L78.5 60.6 L115.2 54.4 L120.1 51.6 L121.5 46 L145.1 48.8 L164.5 55.7 L169.4 54.4 L163.8 46 L152.7 40.4 Z",
    "M181.9 65.5 L177 71.1 L177 81.5 L192.3 100.3 L212.4 114.3 L212.4 70.4 L193.7 63.4 Z",
    "M154.1 80.8 L126.4 72.5 L115.2 73.2 L107.6 77.3 L90.3 77.3 L81.2 81.5 L69.4 94.8 L61.8 109.4 L62.5 117.1 L68 118.5 L76.4 115 L81.9 110.1 L86.8 97.6 L97.2 96.9 L128.4 81.5 L143 81.5 L153.4 86.4 Z",
    "M213.1 201.4 L211.8 199.3 L206.9 208.3 L187.5 225.1 L179.1 237.6 L164.5 241.8 L152 249.5 L134 271.8 L93 272.5 L86.8 264.8 L58.3 248.8 L35.4 239.7 L11.1 235.5 L4.2 244.6 L4.2 291.3 L6.2 297.5 L21.5 295.4 L31.2 298.2 L70.1 298.2 L84.7 294.1 L115.9 294.8 L152.7 282.2 L175.6 269 L202 244.6 L207.6 236.2 L212.4 219.5 Z",
    "M212.4 288.5 L182.6 289.9 L168.7 297.5 L206.2 298.2 L212.4 296.1 Z",
  ]

  struct Particle {
    let drift: Double
    let duration: Double
    let left: Double
    let maxOpacity: Double
    let phase: Double
    let size: Double
    let waveOffset: Double
  }

  static let PARTICLE_COUNT = 26
  private static let PARTICLE_SIZES: [Double] = [3, 4, 6, 3, 8, 5, 11, 4, 7, 3, 5, 9]

  private static func fraction(_ index: Int, _ multiplier: Int, _ offset: Int) -> Double {
    Double((index * multiplier + offset) % 997) / 997
  }

  static let particles: [Particle] = (0..<PARTICLE_COUNT).map { index in
    let size = PARTICLE_SIZES[index % PARTICLE_SIZES.count]
    return Particle(
      drift: (index % 2 == 0 ? -1 : 1) * (4 + fraction(index, 179, 41) * 10),
      duration: 11_500 + fraction(index, 263, 73) * 6_000,
      left: 0.03 + fraction(index, 619, 17) * 0.94,
      maxOpacity: min(0.54, 0.22 + size * 0.022 + fraction(index, 131, 59) * 0.08),
      phase: fraction(index, 397, 29),
      size: size,
      waveOffset: fraction(index, 449, 97) * Double.pi * 2)
  }

  private static func path(_ description: String) -> CGPath {
    let path = CGMutablePath()
    var numbers: [Double] = []
    var command: Character = "M"
    func flush() {
      guard numbers.count == 2 else { return }
      let point = CGPoint(x: numbers[0], y: numbers[1])
      if command == "M" { path.move(to: point) } else { path.addLine(to: point) }
      numbers = []
    }
    for token in description.split(separator: " ") {
      var number = token
      if let first = token.first, first.isLetter {
        if first == "Z" {
          path.closeSubpath()
          continue
        }
        // A command letter is written against its first coordinate.
        command = first
        number = token.dropFirst()
      }
      if let value = Double(number) {
        numbers.append(value)
        flush()
      }
    }
    return path
  }

  /// The silhouettes on the square plane, blurred, as a coverage mask at a quarter of the
  /// plane's size in points. The blur is 16 points, so nothing finer than that is lost.
  static func blurredPattern(planeSize: Double, artSize: Double) -> (side: Int, bytes: [UInt8]) {
    let scale = 0.25
    let side = max(Int((planeSize * scale).rounded(.up)), 1)
    var bytes = [UInt8](repeating: 0, count: side * side)
    if let context = CGContext(
      data: &bytes, width: side, height: side, bitsPerComponent: 8, bytesPerRow: side,
      space: CGColorSpaceCreateDeviceGray(), bitmapInfo: CGImageAlphaInfo.none.rawValue) {
      let artOffset = (planeSize - artSize) / 2
      context.translateBy(x: 0, y: Double(side))
      context.scaleBy(x: scale, y: -scale)
      context.translateBy(x: artOffset, y: artOffset)
      context.scaleBy(x: artSize / ART_WIDTH, y: artSize / ART_HEIGHT)
      context.setFillColor(gray: 1, alpha: 1)
      for description in PATTERN_PATHS {
        context.addPath(path(description))
        context.fillPath()
      }
    }

    // A separable Gaussian with clamped edges.
    let sigma = BACKDROP_BLUR * scale
    let reach = Int((sigma * 3).rounded(.up))
    var kernel = (-reach...reach).map { exp(-Double($0 * $0) / (2 * sigma * sigma)) }
    let sum = kernel.reduce(0, +)
    kernel = kernel.map { $0 / sum }
    var source = bytes.map(Double.init)
    var blurred = [Double](repeating: 0, count: side * side)
    for vertical in [false, true] {
      for y in 0..<side {
        for x in 0..<side {
          var value = 0.0
          for tap in -reach...reach {
            let sx = vertical ? x : min(max(x + tap, 0), side - 1)
            let sy = vertical ? min(max(y + tap, 0), side - 1) : y
            value += source[sy * side + sx] * kernel[tap + reach]
          }
          blurred[y * side + x] = value
        }
      }
      source = blurred
    }
    return (side, source.map { UInt8(min(max($0.rounded(), 0), 255)) })
  }
}
