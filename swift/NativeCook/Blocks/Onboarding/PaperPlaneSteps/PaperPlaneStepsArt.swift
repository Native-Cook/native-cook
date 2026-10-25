import SwiftUI
import UIKit

/*
 Paper Plane Steps — the writing paper, drawn once into an atlas (a port of planeArt.ts): the three
 onboarding sheets side by side and, fourth, the back of the paper. Each sheet: cream laid paper
 with grain, faint ruled lines and a red margin, a perforated stamp in the top-right corner (a
 small landscape in its own palette per step) under a wavy postmark, the step number, a title and
 three lines of copy.
*/

enum PaperPlaneStepsArt {
    static let copy: [(kicker: String, title: String, lines: [String])] = [
        ("No. 1 · Write", "Write it slow", ["Notes travel at the pace", "of paper. Take your time,", "nobody is watching you type."]),
        ("No. 2 · Fold", "Fold it up", ["Every letter is folded by", "hand and leaves the post", "office at nine each evening."]),
        ("No. 3 · Send", "Let it fly", ["Friends far away get it", "the next day, the way a", "letter should arrive."]),
    ]

    private static let stamps: [[UInt32]] = [
        [0xF6B26B, 0xE07A4F, 0x8E3B46, 0x3E2A4F],
        [0x9ED8D1, 0x4FA3A5, 0x2C6E7F, 0x1D3B52],
        [0xD9C2F0, 0x9C7CC8, 0x5E4B8B, 0x2E2A4F],
    ]

    private static let ink: UInt32 = 0x2B2F45
    private static let paper: [UInt32] = [0xFBF6EA, 0xF5EEDD, 0xEFE6D2]
    private static let backPaper: [UInt32] = [0xF3ECDC, 0xEDE4D0, 0xE6DCC6]

    private static func rgba(_ r: Double, _ g: Double, _ b: Double, _ a: Double = 1) -> UIColor {
        UIColor(red: r / 255, green: g / 255, blue: b / 255, alpha: a)
    }

    private static func hex(_ v: UInt32, _ a: Double = 1) -> UIColor {
        rgba(Double((v >> 16) & 0xff), Double((v >> 8) & 0xff), Double(v & 0xff), a)
    }

    private struct Fonts {
        let kicker = UIFont.systemFont(ofSize: 10, weight: .bold)
        let title = UIFont(name: "Georgia-Bold", size: 29) ?? UIFont.systemFont(ofSize: 29, weight: .bold)
        let body = UIFont(name: "Georgia", size: 15.5) ?? UIFont.systemFont(ofSize: 15.5)
        let mark = UIFont.systemFont(ofSize: 6.5, weight: .bold)
    }

    private static func width(_ text: String, _ font: UIFont) -> Double {
        NSAttributedString(string: text, attributes: [.font: font]).size().width
    }

    /// Text with its baseline at y, like Skia's drawText.
    private static func text(_ s: String, _ font: UIFont, _ x: Double, _ y: Double, _ color: UIColor) {
        NSAttributedString(string: s, attributes: [.font: font, .foregroundColor: color]).draw(at: CGPoint(x: x, y: y - font.ascender))
    }

    private static func gradient(_ colors: [UInt32], _ locations: [CGFloat]) -> CGGradient? {
        CGGradient(colorsSpace: CGColorSpace(name: CGColorSpace.sRGB), colors: colors.map { hex($0).cgColor } as CFArray, locations: locations)
    }

    private static func stamp(_ c: CGContext, _ x: Double, _ y: Double, _ w: Double, _ h: Double, _ pal: [UInt32], _ fonts: Fonts) {
        // The perforated stamp paper: a white rectangle with half-circle bites along its edges, over
        // a soft shadow.
        let bites = CGMutablePath()
        let r = 2.2
        let nx = Int((w / 6).rounded())
        for i in 0...nx {
            let bx = x + Double(i) * w / Double(nx)
            bites.addEllipse(in: CGRect(x: bx - r, y: y - r, width: 2 * r, height: 2 * r))
            bites.addEllipse(in: CGRect(x: bx - r, y: y + h - r, width: 2 * r, height: 2 * r))
        }
        let ny = Int((h / 6).rounded())
        for i in 0...ny {
            let by = y + Double(i) * h / Double(ny)
            bites.addEllipse(in: CGRect(x: x - r, y: by - r, width: 2 * r, height: 2 * r))
            bites.addEllipse(in: CGRect(x: x + w - r, y: by - r, width: 2 * r, height: 2 * r))
        }
        let cut = CGPath(rect: CGRect(x: x, y: y, width: w, height: h), transform: nil).subtracting(bites)
        c.saveGState()
        c.setShadow(offset: CGSize(width: 1, height: 2), blur: 4, color: rgba(60, 40, 20, 0.18).cgColor)
        c.setFillColor(hex(0xFFFCF4).cgColor)
        c.addPath(cut)
        c.fillPath()
        c.restoreGState()
        // The picture: sky in three stops, a sun, two hills.
        let ix = x + 5
        let iy = y + 5
        let iw = w - 10
        let ih = h - 16
        let pic = CGRect(x: ix, y: iy, width: iw, height: ih)
        c.saveGState()
        c.clip(to: pic)
        if let g = gradient([pal[0], pal[1], pal[2]], [0, 0.6, 1]) {
            c.drawLinearGradient(g, start: CGPoint(x: ix, y: iy), end: CGPoint(x: ix, y: iy + ih), options: [])
        }
        c.setFillColor(rgba(255, 246, 220, 0.9).cgColor)
        let sr = iw * 0.14
        c.fillEllipse(in: CGRect(x: ix + iw * 0.66 - sr, y: iy + ih * 0.42 - sr, width: 2 * sr, height: 2 * sr))
        c.move(to: CGPoint(x: ix - 2, y: iy + ih * 0.78))
        c.addQuadCurve(to: CGPoint(x: ix + iw * 0.62, y: iy + ih * 0.8), control: CGPoint(x: ix + iw * 0.3, y: iy + ih * 0.5))
        c.addLine(to: CGPoint(x: ix + iw + 2, y: iy + ih + 2))
        c.addLine(to: CGPoint(x: ix - 2, y: iy + ih + 2))
        c.closePath()
        c.setFillColor(hex(pal[2]).cgColor)
        c.fillPath()
        c.move(to: CGPoint(x: ix + iw * 0.3, y: iy + ih + 2))
        c.addQuadCurve(to: CGPoint(x: ix + iw + 2, y: iy + ih * 0.74), control: CGPoint(x: ix + iw * 0.7, y: iy + ih * 0.56))
        c.addLine(to: CGPoint(x: ix + iw + 2, y: iy + ih + 2))
        c.closePath()
        c.setFillColor(hex(pal[3]).cgColor)
        c.fillPath()
        c.restoreGState()
        text("40", fonts.mark, x + 6, y + h - 4, hex(ink))
    }

    private static func postmark(_ c: CGContext, _ x: Double, _ y: Double, _ fonts: Fonts) {
        c.setLineWidth(1.2)
        c.setStrokeColor(rgba(52, 62, 96, 0.55).cgColor)
        c.strokeEllipse(in: CGRect(x: x - 19, y: y - 19, width: 38, height: 38))
        c.strokeEllipse(in: CGRect(x: x - 14.5, y: y - 14.5, width: 29, height: 29))
        for k in 0..<4 {
            let wy = y - 9 + Double(k) * 6
            c.move(to: CGPoint(x: x + 20, y: wy))
            for i in 1...8 {
                let d = Double(i)
                c.addQuadCurve(to: CGPoint(x: x + 20 + d * 7, y: wy), control: CGPoint(x: x + 20 + d * 7 - 3.5, y: wy + (i % 2 == 1 ? -2.5 : 2.5)))
            }
            c.strokePath()
        }
        let a = "25 OCT"
        text(a, fonts.mark, x - width(a, fonts.mark) / 2, y + 3, rgba(52, 62, 96, 0.6))
    }

    @MainActor private static var cache: [String: UIImage] = [:]

    static func key(sw: Double, sh: Double, scale: Double) -> String { "\(sw)x\(sh)@\(scale)" }

    /// The atlas for a size if it was built already.
    @MainActor
    static func cached(sw: Double, sh: Double, scale: Double) -> UIImage? { cache[key(sw: sw, sh: sh, scale: scale)] }

    /// The atlas: 4 × sw wide, sh tall, at `scale` pixels per point, built off the main thread.
    @MainActor
    static func load(sw: Double, sh: Double, scale: Double) async -> UIImage {
        if let image = cached(sw: sw, sh: sh, scale: scale) { return image }
        let image = await Task.detached(priority: .userInitiated) { build(sw: sw, sh: sh, scale: scale) }.value
        cache[key(sw: sw, sh: sh, scale: scale)] = image
        return image
    }

    private static func build(sw: Double, sh: Double, scale: Double) -> UIImage {
        let aw = sw * 4
        let fonts = Fonts()

        // Paper grain at 1 px per pt (premultiplied): one 256 pt tile of the same seeded noise,
        // repeated over the sheets, so the atlas builds quickly.
        let gw = 256
        let gh = 256
        var grain = [UInt8](repeating: 0, count: gw * gh * 4)
        var seed: Int64 = 98765
        for i in 0..<(gw * gh) {
            seed = (seed &* 1_103_515_245 &+ 12345) & 0x7fff_ffff
            let v = Double(seed) / Double(0x7fff_ffff)
            let dark = v < 0.5
            let a = floor(abs(v - 0.5) * 2 * 26)
            let k = a / 255
            grain[i * 4] = UInt8((dark ? 70 : 255) * k)
            grain[i * 4 + 1] = UInt8((dark ? 55 : 252) * k)
            grain[i * 4 + 2] = UInt8((dark ? 30 : 240) * k)
            grain[i * 4 + 3] = UInt8(a)
        }
        let grainImage = CGDataProvider(data: Data(grain) as CFData).flatMap {
            CGImage(width: gw, height: gh, bitsPerComponent: 8, bitsPerPixel: 32, bytesPerRow: gw * 4,
                    space: CGColorSpace(name: CGColorSpace.sRGB)!,
                    bitmapInfo: CGBitmapInfo(rawValue: CGImageAlphaInfo.premultipliedLast.rawValue),
                    provider: $0, decode: nil, shouldInterpolate: false, intent: .defaultIntent)
        }

        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = true
        return UIGraphicsImageRenderer(size: CGSize(width: aw, height: sh), format: format).image { ctx in
            let c = ctx.cgContext
            for k in 0..<4 {
                let x0 = Double(k) * sw
                let back = k == 3
                c.saveGState()
                c.clip(to: CGRect(x: x0, y: 0, width: sw, height: sh))
                if let g = gradient(back ? backPaper : paper, [0, 0.55, 1]) {
                    c.drawLinearGradient(g, start: CGPoint(x: x0, y: 0), end: CGPoint(x: x0 + sw * 0.4, y: sh), options: [.drawsBeforeStartLocation, .drawsAfterEndLocation])
                }
                c.restoreGState()
                if back { continue }
                let cp = copy[k]
                // Ruled lines and the margin.
                c.setLineWidth(0.7)
                c.setStrokeColor(rgba(90, 130, 190, 0.22).cgColor)
                var y = 200.0
                while y < sh - 18 {
                    c.move(to: CGPoint(x: x0 + 14, y: y))
                    c.addLine(to: CGPoint(x: x0 + sw - 14, y: y))
                    y += 24
                }
                c.strokePath()
                c.setStrokeColor(rgba(200, 80, 80, 0.28).cgColor)
                c.move(to: CGPoint(x: x0 + 30, y: 0))
                c.addLine(to: CGPoint(x: x0 + 30, y: sh))
                c.strokePath()
                stamp(c, x0 + sw - 66, 18, 48, 58, stamps[k], fonts)
                postmark(c, x0 + sw - 92, 74, fonts)
                // Copy, squeezed sideways where a line would not fit the sheet.
                text(cp.kicker.uppercased(), fonts.kicker, x0 + 40, 118, hex(0xB4553E))
                text(cp.title, fonts.title, x0 + 40, 154, hex(ink))
                let room = sw - 40 - 16
                for (i, l) in cp.lines.enumerated() {
                    let w = width(l, fonts.body)
                    let by = 196 + Double(i) * 24
                    if w > room {
                        c.saveGState()
                        c.translateBy(x: x0 + 40, y: 0)
                        c.scaleBy(x: room / w, y: 1)
                        text(l, fonts.body, 0, by, hex(ink))
                        c.restoreGState()
                    } else {
                        text(l, fonts.body, x0 + 40, by, hex(ink))
                    }
                }
                // A signature flourish at the foot of the copy.
                let sy = 196 + 3 * 24 + 18.0
                c.move(to: CGPoint(x: x0 + 40, y: sy))
                c.addCurve(to: CGPoint(x: x0 + 80, y: sy - 4), control1: CGPoint(x: x0 + 56, y: sy - 18), control2: CGPoint(x: x0 + 66, y: sy + 10))
                c.addCurve(to: CGPoint(x: x0 + 118, y: sy - 2), control1: CGPoint(x: x0 + 92, y: sy - 14), control2: CGPoint(x: x0 + 100, y: sy + 6))
                c.setLineWidth(1.4)
                c.setLineCap(.round)
                c.setStrokeColor(rgba(43, 47, 69, 0.7).cgColor)
                c.strokePath()
            }
            if let grainImage {
                c.saveGState()
                c.interpolationQuality = .none
                c.clip(to: CGRect(x: 0, y: 0, width: aw, height: sh))
                c.draw(grainImage, in: CGRect(x: 0, y: 0, width: Double(gw), height: Double(gh)), byTiling: true)
                c.restoreGState()
            }
        }
    }
}
