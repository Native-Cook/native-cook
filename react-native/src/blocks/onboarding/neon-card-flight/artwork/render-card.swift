import AppKit
import CoreGraphics
import ImageIO
import UniformTypeIdentifiers

// A small offscreen bridge for the exact CoreGraphics sprite code from the Swift reference.
struct UIImage {
    enum Orientation { case up }
    var cgImage: CGImage
    var scale: Double
    init(cgImage: CGImage, scale: Double, orientation: Orientation) { self.cgImage = cgImage; self.scale = scale }
    func draw(at point: CGPoint) {
        let c = NSGraphicsContext.current!.cgContext
        let size = CGSize(width: Double(cgImage.width) / scale, height: Double(cgImage.height) / scale)
        c.saveGState(); c.translateBy(x: point.x, y: point.y + size.height); c.scaleBy(x: 1, y: -1)
        c.draw(cgImage, in: CGRect(origin: .zero, size: size)); c.restoreGState()
    }
}

enum NeonCardFlightArt {
    static func color(_ hex: UInt32, alpha: Double = 1) -> NSColor {
        NSColor(red: CGFloat((hex >> 16) & 255) / 255, green: CGFloat((hex >> 8) & 255) / 255,
                blue: CGFloat(hex & 255) / 255, alpha: alpha)
    }

    static func render(_ size: CGSize, _ draw: (CGContext) -> Void) -> UIImage {
        let context = CGContext(data: nil, width: Int(size.width * 2), height: Int(size.height * 2), bitsPerComponent: 8, bytesPerRow: 0,
                                space: CGColorSpaceCreateDeviceRGB(), bitmapInfo: CGImageAlphaInfo.premultipliedLast.rawValue)!
        context.translateBy(x: 0, y: size.height * 2)
        context.scaleBy(x: 2, y: -2)
        NSGraphicsContext.saveGraphicsState()
        NSGraphicsContext.current = NSGraphicsContext(cgContext: context, flipped: true)
        draw(context)
        NSGraphicsContext.restoreGraphicsState()
        return UIImage(cgImage: context.makeImage()!, scale: 2, orientation: .up)
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

    static func text(_ value: String, x: CGFloat, y: CGFloat, size: CGFloat, weight: NSFont.Weight, color hex: UInt32) {
        (value as NSString).draw(at: CGPoint(x: x, y: y), withAttributes: [.font: NSFont.systemFont(ofSize: size, weight: weight), .foregroundColor: color(hex)])
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

let folder = URL(fileURLWithPath: CommandLine.arguments[1], isDirectory: true)
try FileManager.default.createDirectory(at: folder, withIntermediateDirectories: true)
let file = folder.appendingPathComponent("card-intro.png")
let destination = CGImageDestinationCreateWithURL(file as CFURL, UTType.png.identifier as CFString, 1, nil)!
CGImageDestinationAddImage(destination, NeonCardFlightArt.introCard.cgImage, nil)
precondition(CGImageDestinationFinalize(destination))
print(file.path)
