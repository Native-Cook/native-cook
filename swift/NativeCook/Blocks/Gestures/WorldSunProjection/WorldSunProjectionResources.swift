import SwiftUI
import UIKit

final class WorldSunProjectionResources: @unchecked Sendable {
    static let shared = WorldSunProjectionResources()
    let environment: CGImage
    static func color(_ hex: UInt32, alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
              blue: Double(hex & 255) / 255, opacity: alpha)
    }
    private static func ui(_ hex: UInt32, alpha: CGFloat = 1) -> UIColor {
        UIColor(red: CGFloat((hex >> 16) & 255) / 255, green: CGFloat((hex >> 8) & 255) / 255,
                blue: CGFloat(hex & 255) / 255, alpha: alpha)
    }
    private static func hash(_ x: Int, _ y: Int, _ seed: Int = 93) -> Double {
        var v = UInt32(truncatingIfNeeded: x &* 374_761_393 &+ y &* 668_265_263 &+ seed &* 1447)
        v = (v ^ (v >> 13)) &* 1_274_126_177
        return Double(v ^ (v >> 16)) / Double(UInt32.max)
    }
    private static func noise(_ x: Double, _ y: Double) -> Double {
        let ix = Int(floor(x)), iy = Int(floor(y))
        let u = WorldSunProjectionMath.smooth(x - floor(x)), v = WorldSunProjectionMath.smooth(y - floor(y))
        let a = WorldSunProjectionMath.mix(hash(ix, iy), hash(ix + 1, iy), u)
        let b = WorldSunProjectionMath.mix(hash(ix, iy + 1), hash(ix + 1, iy + 1), u)
        return WorldSunProjectionMath.mix(a, b, v)
    }
    private static func point(_ azimuth: Double, _ elevation: Double) -> CGPoint {
        CGPoint(x: (azimuth + 180) / 360 * 2048, y: (90 - elevation) / 150 * 1024)
    }
    private init() {
        let width = 2048, height = 1024
        let memory = calloc(width * height * 4, 1)!
        let pixels = memory.assumingMemoryBound(to: UInt8.self)
        let mix: (Double, Double, Double) -> Double = WorldSunProjectionMath.mix
        for y in 0..<height {
            let elevation = 90 - (Double(y) + 0.5) / 1024 * 150
            for x in 0..<width {
                let xx = Double(x), yy = Double(y)
                let grain = Self.hash(x, y) - 0.5
                let broad = Self.noise(xx / 91, yy / 43)
                let fine = Self.noise(xx / 23 + 7, yy / 17 + 3)
                var r: Double, g: Double, b: Double
                if elevation >= -0.8 {
                    let sky = WorldSunProjectionMath.clamp(elevation / 75)
                    r = mix(179, 101, sky); g = mix(202, 159, sky); b = mix(214, 208, sky)
                    var cloud = 0.0
                    for bank in 0..<3 {
                        let center = [17.0, 36, 57][bank]
                        let spread = [5.0, 7, 5][bank]
                        let envelope = exp(-pow((elevation - center) / spread, 2))
                        let rippled = Self.noise(xx / (65 + 19 * Double(bank)), yy / 22 + Double(bank) * 9)
                        cloud = max(cloud, envelope * WorldSunProjectionMath.smooth((rippled + broad * 0.35 - 0.43) / 0.43) * 0.64)
                    }
                    r = mix(r, 217, cloud); g = mix(g, 230, cloud); b = mix(b, 235, cloud)
                } else if elevation >= -5 {
                    let water = WorldSunProjectionMath.clamp((-elevation - 0.8) / 4.2)
                    let band = sin(water * 16 * .pi + 1.2 * broad + 0.16 * sin(xx / 29))
                    let foam = pow(max(0, band), 9) * (0.16 + 0.18 * broad)
                    r = mix(127, 107, water) + foam * 58
                    g = mix(159, 139, water) + foam * 48
                    b = mix(162, 144, water) + foam * 37
                } else {
                    let ground = WorldSunProjectionMath.clamp((-elevation - 5) / 38)
                    let tone = (broad - 0.5) * 12 + (fine - 0.5) * 5
                    r = mix(98, 70, ground) + tone; g = mix(103, 76, ground) + tone; b = mix(93, 66, ground) + tone
                    let wet = exp(-pow((elevation + 6.2 + broad * 1.3) / 1.6, 2)) * 0.20
                    r = mix(r, 157, wet); g = mix(g, 171, wet); b = mix(b, 167, wet)
                }
                let index = (y * width + x) * 4
                pixels[index] = UInt8(max(0, min(255, r + grain * 5)))
                pixels[index + 1] = UInt8(max(0, min(255, g + grain * 5)))
                pixels[index + 2] = UInt8(max(0, min(255, b + grain * 5)))
                pixels[index + 3] = 255
            }
        }
        let space = CGColorSpace(name: CGColorSpace.sRGB)!
        let context = CGContext(data: memory, width: width, height: height, bitsPerComponent: 8,
                                bytesPerRow: width * 4, space: space,
                                bitmapInfo: CGBitmapInfo.byteOrder32Big.rawValue | CGImageAlphaInfo.premultipliedLast.rawValue)!
        context.translateBy(x: 0, y: 1024); context.scaleBy(x: 1, y: -1)
        UIGraphicsPushContext(context)
        for k in 0..<400 {
            let azimuth = -180 + 360 * Self.hash(k, 91), elevation = -8 - 51 * Self.hash(k, 92)
            let p = Self.point(azimuth, elevation), radius = 0.3 + 1.2 * Self.hash(k, 93)
            Self.ui(k.isMultiple(of: 3) ? 0xA5A99A : 0x343E35, alpha: 0.14).setFill()
            UIBezierPath(ovalIn: CGRect(x: p.x, y: p.y, width: radius * 1.7, height: radius)).fill()
        }
        var twigs: [(CGPoint, CGPoint, Double)] = []
        func branch(_ azimuth: Double, _ elevation: Double, _ dx: Double, _ dy: Double,
                    _ thickness: Double, _ depth: Int, _ seed: Int) {
            let a = Self.point(azimuth, elevation), b = Self.point(azimuth + dx, elevation + dy)
            let path = UIBezierPath(); path.move(to: a)
            let bend = (Self.hash(seed, 12) - 0.5) * 15
            let c1 = CGPoint(x: a.x + (b.x - a.x) * 0.28 + bend, y: a.y + (b.y - a.y) * 0.32)
            let c2 = CGPoint(x: a.x + (b.x - a.x) * 0.72 - bend, y: a.y + (b.y - a.y) * 0.78)
            path.addCurve(to: b, controlPoint1: c1, controlPoint2: c2)
            var left: [CGPoint] = [], right: [CGPoint] = []
            for sample in 0...16 {
                let u = Double(sample) / 16, v = 1 - u
                let p = CGPoint(x: v*v*v*a.x + 3*v*v*u*c1.x + 3*v*u*u*c2.x + u*u*u*b.x,
                                y: v*v*v*a.y + 3*v*v*u*c1.y + 3*v*u*u*c2.y + u*u*u*b.y)
                let dx = 3*v*v*(c1.x-a.x) + 6*v*u*(c2.x-c1.x) + 3*u*u*(b.x-c2.x)
                let dy = 3*v*v*(c1.y-a.y) + 6*v*u*(c2.y-c1.y) + 3*u*u*(b.y-c2.y)
                let length = max(0.001, hypot(dx,dy))
                let radius = thickness * (1 - 0.38*u) * (0.49 + 0.025*Self.hash(seed,sample))
                left.append(CGPoint(x:p.x-dy/length*radius,y:p.y+dx/length*radius))
                right.append(CGPoint(x:p.x+dy/length*radius,y:p.y-dx/length*radius))
            }
            let bark = UIBezierPath(); bark.move(to:left[0])
            for p in left.dropFirst() { bark.addLine(to:p) }
            for p in right.reversed() { bark.addLine(to:p) }; bark.close()
            Self.ui(depth < 2 ? 0x172720 : 0x303A2C).setFill(); bark.fill()
            if depth > 2 {
                Self.ui(0x655E4C, alpha: 0.38).setStroke(); path.lineWidth = thickness * 0.10
                path.setLineDash([4,2,11,1,6,3],count:6,phase:CGFloat(seed%7))
                context.saveGState(); context.translateBy(x: -thickness * 0.22, y: 0); path.stroke(); context.restoreGState()
            }
            let lower = max(0, (27 - elevation) / max(0.001, dy))
            let upper = min(1, (85 - elevation) / max(0.001, dy))
            if upper > lower {
                twigs.append((Self.point(azimuth + dx * lower, elevation + dy * lower),
                              Self.point(azimuth + dx * upper, elevation + dy * upper), Self.hash(seed, 7)))
            }
            if depth == 0 { return }
            let e = min(88.0, elevation + dy), az = azimuth + dx
            for fork in 0..<2 {
                let direction = Double(fork * 2 - 1)
                let nextX = dx * 0.46 + direction * (7 + 4 * Self.hash(seed, fork))
                let nextY = abs(dy) * (0.48 + 0.12 * Self.hash(seed, fork + 3)) + 1
                branch(az, e, nextX, min(nextY, 89 - e), max(0.65, thickness * 0.48), depth - 1, seed * 3 + fork + 1)
            }
        }
        // Continue the three trunks below the photographed field of view so their bases
        // enter the ground naturally rather than ending as rectangular floating cuts.
        func rootedTrunk(_ az: Double, _ elevation: Double, _ dx: Double, _ dy: Double, _ width: Double) {
            let top = Self.point(az, elevation)
            let bottom = Self.point(az - dx * (-60 - elevation) / -dy, -60)
            let outline = UIBezierPath()
            outline.move(to: CGPoint(x: top.x - width * 0.50, y: top.y - 1))
            outline.addCurve(to: CGPoint(x: bottom.x - width * 0.84, y: bottom.y),
                             controlPoint1: CGPoint(x: top.x - width * 0.52, y: top.y + (bottom.y-top.y)*0.28),
                             controlPoint2: CGPoint(x: bottom.x - width * 0.64, y: top.y + (bottom.y-top.y)*0.78))
            outline.addLine(to: CGPoint(x: bottom.x + width * 0.84, y: bottom.y))
            outline.addCurve(to: CGPoint(x: top.x + width * 0.50, y: top.y - 1),
                             controlPoint1: CGPoint(x: bottom.x + width * 0.64, y: top.y + (bottom.y-top.y)*0.78),
                             controlPoint2: CGPoint(x: top.x + width * 0.52, y: top.y + (bottom.y-top.y)*0.28))
            outline.close(); Self.ui(0x303A2C).setFill(); outline.fill()
        }
        rootedTrunk(22, -24, -5, 59, 23)
        rootedTrunk(7, -18, -19, 56, 15)
        rootedTrunk(-17, -20, -17, 55, 18)
        branch(22, -24, -5, 59, 23, 5, 1)
        branch(7, -18, -19, 56, 15, 5, 2)
        branch(-17, -20, -17, 55, 18, 5, 3)
        // Feathered needle fans leave small irregular sky holes through the fixed canopy.
        for k in 0..<900 {
            let twig = twigs[k % twigs.count], u = Self.hash(k, 42)
            let center = CGPoint(x: mix(twig.0.x, twig.1.x, u), y: mix(twig.0.y, twig.1.y, u))
            let side = k.isMultiple(of:2) ? -1.0 : 1.0
            let angle = atan2(twig.1.y - twig.0.y, twig.1.x - twig.0.x) + side * (0.7 + Self.hash(k,43) * 0.65)
            let length = 34 + 48 * Self.hash(k, 44)
            let bend = (Self.hash(k,45)-0.5)*length*0.35
            let stem = UIBezierPath();stem.move(to:center)
            stem.addQuadCurve(to:CGPoint(x:center.x+cos(angle)*length,y:center.y+sin(angle)*length),
                              controlPoint:CGPoint(x:center.x+cos(angle)*length*0.5-sin(angle)*bend,
                                                   y:center.y+sin(angle)*length*0.5+cos(angle)*bend))
            Self.ui(0x172720,alpha:0.85).setStroke();stem.lineWidth=0.65;stem.stroke()
            for needle in 0..<14 {
                let u=(Double(needle)+0.25+Self.hash(k,needle)*0.4)/14
                let spine=CGPoint(x:center.x+cos(angle)*length*u-sin(angle)*bend*4*u*(1-u),
                                  y:center.y+sin(angle)*length*u+cos(angle)*bend*4*u*(1-u))
                let reach=(3+7*Self.hash(k,needle+55))*pow(sin(.pi*u),0.7)
                for flank in [-1.0,1] {
                    let tip=CGPoint(x:spine.x+cos(angle)*reach*0.35-sin(angle)*reach*flank,
                                    y:spine.y+sin(angle)*reach*0.35+cos(angle)*reach*flank)
                    let needlePath=UIBezierPath();needlePath.move(to:spine)
                    needlePath.addQuadCurve(to:tip,controlPoint:CGPoint(x:spine.x+cos(angle)*reach*0.5-sin(angle)*reach*flank*0.45,
                                                                       y:spine.y+sin(angle)*reach*0.5+cos(angle)*reach*flank*0.45))
                    Self.ui(needle.isMultiple(of:3) ? 0x34483B:0x0C1713,alpha:0.65+0.3*Self.hash(k,needle)).setStroke()
                    needlePath.lineWidth=0.55+0.5*Self.hash(needle,k);needlePath.lineCapStyle = .round;needlePath.stroke()
                }
            }
        }
        UIGraphicsPopContext()
        let bytes = NSData(bytesNoCopy: memory, length: width * height * 4, freeWhenDone: true)
        environment = CGImage(width: width, height: height, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: width * 4,
                              space: space, bitmapInfo: CGBitmapInfo(rawValue: CGBitmapInfo.byteOrder32Big.rawValue | CGImageAlphaInfo.premultipliedLast.rawValue),
                              provider: CGDataProvider(data: bytes as CFData)!, decode: nil, shouldInterpolate: true, intent: .defaultIntent)!
    }
}
