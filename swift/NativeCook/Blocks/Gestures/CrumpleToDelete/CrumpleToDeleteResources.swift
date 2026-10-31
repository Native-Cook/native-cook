/// Bundled artwork, fonts, and cached note textures for the Crumple to Delete renderer.
import AVFoundation
import CoreImage
import CoreText
import ImageIO
import UIKit

/// Prepares static artwork once while the paper mesh changes each frame.
final class CrumpleToDeleteResources {
    static let shared = CrumpleToDeleteResources()
    let desk: UIImage, paper: UIImage, bin: UIImage
    private var notes: [Double: (UIImage, UIImage)] = [:]
    private let ci = CIContext(options: [.workingColorSpace: CGColorSpace(name: CGColorSpace.sRGB)!])
    private init() {
        for name in [
            "Archivo-ExtraBold", "Archivo-SemiBold", "Archivo-Medium", "JetBrainsMono-Medium", "GochiHand-Regular",
        ] {
            if let url = Self.url(name, "ttf") { CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil) }
        }
        desk = Self.image("desk", "jpg")
        paper = Self.image("paper", "webp")
        bin = Self.image("bin", "webp")
    }
    static func url(_ name: String, _ ext: String) -> URL? {
        Bundle.main.url(forResource: "CrumpleToDelete-\(name)", withExtension: ext)
    }
    static func image(_ name: String, _ ext: String) -> UIImage {
        guard let url = url(name, ext), let source = CGImageSourceCreateWithURL(url as CFURL, nil),
            let cg = CGImageSourceCreateImageAtIndex(
                source, 0, [kCGImageSourceShouldCacheImmediately: true] as CFDictionary)
        else { return UIImage() }
        return UIImage(cgImage: cg)
    }
    static func color(_ hex: UInt32, _ alpha: Double = 1) -> UIColor {
        UIColor(
            red: Double((hex >> 16) & 255) / 255, green: Double((hex >> 8) & 255) / 255,
            blue: Double(hex & 255) / 255, alpha: alpha)
    }
    func font(_ name: String, _ size: Double) -> UIFont {
        UIFont(name: name, size: size) ?? .systemFont(ofSize: size)
    }
    func render(_ size: CGSize, _ scale: Double = 3, _ draw: (CGContext) -> Void) -> UIImage {
        let f = UIGraphicsImageRendererFormat()
        f.scale = scale
        f.opaque = false
        return UIGraphicsImageRenderer(size: size, format: f).image { draw($0.cgContext) }
    }
    func cover(_ image: UIImage, _ box: CGRect) {
        guard image.size.width > 0 else { return }
        let s = max(box.width / image.size.width, box.height / image.size.height)
        let r = CGRect(
            x: box.midX - image.size.width * s / 2, y: box.midY - image.size.height * s / 2,
            width: image.size.width * s, height: image.size.height * s)
        let ctx = UIGraphicsGetCurrentContext()!
        ctx.saveGState()
        ctx.clip(to: box)
        image.draw(in: r)
        ctx.restoreGState()
    }
    func noteImages(_ g: CrumpleToDeleteMotion.Geo) -> (UIImage, UIImage) {
        if let pair = notes[g.nw] { return pair }
        func make(_ title: String, _ lines: [String]) -> UIImage {
            render(CGSize(width: g.nw, height: g.nh), 2) { c in
                cover(paper, CGRect(x: 0, y: 0, width: g.nw, height: g.nh))
                func text(_ s: String, _ size: Double, _ baseline: Double, _ red: Bool = false) {
                    let face = font("GochiHand-Regular", size)
                    // Skia Text draws unshaped glyph advances, without CTLine's kerning.
                    let ct = face as CTFont
                    let characters = Array(s.utf16)
                    var glyphs = [CGGlyph](repeating: 0, count: characters.count)
                    CTFontGetGlyphsForCharacters(ct, characters, &glyphs, characters.count)
                    var advances = [CGSize](repeating: .zero, count: glyphs.count)
                    CTFontGetAdvancesForGlyphs(ct, .horizontal, glyphs, &advances, glyphs.count)
                    var x = 0.0
                    let positions = advances.map { advance -> CGPoint in
                        let point = CGPoint(x: x, y: 0)
                        x += advance.width
                        return point
                    }
                    c.saveGState()
                    c.setFillColor(Self.color(red ? 0xB7472E : 0x2A2621).cgColor)
                    // Align native glyph raster origins with Skia's 2x text atlas.
                    c.translateBy(x: g.nw * 0.2 + 1.0 / 3, y: baseline - 1.0 / 3)
                    c.scaleBy(x: 1, y: -1)
                    c.textMatrix = .identity
                    CTFontDrawGlyphs(ct, glyphs, positions, glyphs.count, c)
                    c.restoreGState()
                }
                let line0 = 176 * g.nw / 880
                let gap = 51.2 * g.nw / 880
                text(title, 30, line0 - 6)
                for (i, s) in lines.enumerated() { text(s, 20, line0 + gap * Double(i + 1) - 5, i == 1) }
            }
        }
        let pair = (
            make(
                "Saturday market",
                [
                    "sourdough + 2 lemons", "call Mai about the van!", "return the library books",
                    "fix the bike light", "flowers for Ana",
                ]),
            make("Ideas for the trip", ["night train to the coast", "pack the film camera", "ask about the cabin"])
        )
        notes[g.nw] = pair
        return pair
    }
    func blurred(_ image: UIImage, _ sigma: Double) -> UIImage {
        guard let cg = image.cgImage else { return image }
        let input = CIImage(cgImage: cg)
        let output = input.applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: sigma * image.scale])
        guard let result = ci.createCGImage(output, from: input.extent) else { return image }
        return UIImage(cgImage: result, scale: image.scale, orientation: .up)
    }
    func backdrop(_ g: CrumpleToDeleteMotion.Geo) -> UIImage {
        let pair = noteImages(g)
        let shadow = render(CGSize(width: g.nw + 100, height: g.nh + 100)) { c in
            c.setFillColor(Self.color(0x3C2814, 0.28).cgColor)
            c.addPath(
                UIBezierPath(roundedRect: CGRect(x: 54, y: 58, width: g.nw - 8, height: g.nh), cornerRadius: 6)
                    .cgPath)
            c.fillPath()
        }
        let soft = blurred(shadow, 10)
        return render(CGSize(width: g.width, height: g.height)) { c in
            cover(desk, CGRect(x: 0, y: 0, width: g.width, height: g.height))
            c.setFillColor(Self.color(0x281E14, 0.06).cgColor)
            c.fill(CGRect(x: 0, y: 0, width: g.width, height: g.height))
            c.saveGState()
            c.translateBy(x: g.hx, y: g.hy + 6)
            c.rotate(by: 0.035)
            soft.draw(in: CGRect(x: -g.nw / 2 - 50, y: -g.nh / 2 - 50, width: g.nw + 100, height: g.nh + 100))
            pair.1.draw(in: CGRect(x: -g.nw / 2, y: -g.nh / 2, width: g.nw, height: g.nh))
            c.restoreGState()
        }
    }
    func binShadow(_ g: CrumpleToDeleteMotion.Geo) -> UIImage {
        let img = render(CGSize(width: g.bw + 64, height: 90)) { c in
            c.setFillColor(Self.color(0x322314, 0.35).cgColor)
            c.fillEllipse(in: CGRect(x: 40, y: 32, width: g.bw - 16, height: 26))
        }
        return blurred(img, 8)
    }
}
