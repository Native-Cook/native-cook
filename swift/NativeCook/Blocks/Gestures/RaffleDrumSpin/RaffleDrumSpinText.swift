import CoreText
import SwiftUI
import UIKit

/// CoreText glyph masks with SkFont's default non-subpixel placement and baseline snapping.
/// Cache keys describe glyph pixel positions, never story times or reference frames.
enum RaffleDrumSpinText {
    private final class Glyphs: NSObject {
        let runs: [(glyph: String, advance: Double)]
        init(_ runs: [(glyph: String, advance: Double)]) { self.runs = runs }
    }

    private final class Label: NSObject {
        let image: Image
        let imageSize: CGSize
        let ascender: Double
        let padding: Double

        init(runs: [(glyph: String, advance: Double)], name: String, size: Double, ink: UIColor, gap: Double, rasterScale: Double, pixelOffsets: [Int]?) {
            let font = UIFont(name: name, size: size) ?? UIFont.systemFont(ofSize: size)
            let width = runs.reduce(0) { $0 + $1.advance } + gap * Double(max(0, runs.count-1))
            padding = ceil(2 * rasterScale) / rasterScale
            ascender = ceil(font.ascender * rasterScale) / rasterScale
            let format = UIGraphicsImageRendererFormat()
            format.scale = rasterScale
            format.opaque = false
            format.preferredRange = .standard
            let dimensions = CGSize(width: ceil((width+padding*2)*rasterScale)/rasterScale, height: ceil((font.lineHeight+padding*2)*rasterScale)/rasterScale)
            let baseline = padding + ascender
            let pad = padding
            let bitmap = UIGraphicsImageRenderer(size: dimensions, format: format).image { renderer in
                let cg = renderer.cgContext
                cg.setFillColor(ink.cgColor)
                cg.setShouldAntialias(true)
                cg.setShouldSmoothFonts(false)
                cg.setShouldSubpixelPositionFonts(true)
                cg.textMatrix = .identity
                cg.translateBy(x: 0, y: dimensions.height)
                cg.scaleBy(x: 1, y: -1)
                var x = pad
                var ids: [CGGlyph] = []
                var positions: [CGPoint] = []
                for (index,run) in runs.enumerated() {
                    var units = Array(run.glyph.utf16)
                    var glyphs = Array(repeating: CGGlyph(), count: units.count)
                    if CTFontGetGlyphsForCharacters(font as CTFont, &units, &glyphs, units.count), let glyph = glyphs.first {
                        ids.append(glyph)
                        let gx = pixelOffsets.map { pad + Double($0[index])/rasterScale } ?? x
                        positions.append(CGPoint(x: gx, y: dimensions.height-baseline))
                    }
                    x += run.advance + gap
                }
                if !ids.isEmpty { CTFontDrawGlyphs(font as CTFont, &ids, &positions, ids.count, cg) }
            }
            image = Image(decorative: bitmap.cgImage!, scale: rasterScale)
            imageSize = bitmap.size
        }
    }

    private static let glyphCache = NSCache<NSString, Glyphs>()
    private static let imageCache = NSCache<NSString, Label>()

    private static let warmed = NSCache<NSString, NSNumber>()

    static func prewarmTicket(scaleX: Double, displayScale: Double) {
        let warmKey = "labels-\(scaleX)-\(displayScale)" as NSString
        guard warmed.object(forKey:warmKey) == nil else { return }
        func prepare(_ strings: [String], _ name: String, _ baseSize: Double, _ tracking: Double, _ inks: [Int], _ grids: [Double], _ snap: Bool) {
            let size = Double(Float(baseSize*scaleX))
            for grid in grids { for string in strings { for ink in inks {
                let red=CGFloat((ink>>16)&255)/255, green=CGFloat((ink>>8)&255)/255, blue=CGFloat(ink&255)/255
                let runs=glyphRuns(string,name:name,size:size)
                var offsets: [Int]?
                var pattern: UInt64=14695981039346656037
                if snap {
                    var values: [Int]=[]
                    var cursor=0.0
                    for run in runs {
                        let pixel=Int(floor(cursor*grid+0.5));values.append(pixel)
                        pattern=(pattern ^ UInt64(bitPattern:Int64(pixel))) &* 1099511628211
                        cursor += run.advance + tracking*size
                    }
                    offsets=values
                }
                let key="\(grid)|\(name)|\(size)|\(tracking)|\(Float(red).bitPattern)|\(Float(green).bitPattern)|\(Float(blue).bitPattern)|\(snap)|\(pattern)|\(string)" as NSString
                if imageCache.object(forKey:key) != nil { continue }
                let label=Label(runs:runs,name:name,size:size,ink:UIColor(red:red,green:green,blue:blue,alpha:1),gap:tracking*size,rasterScale:grid,pixelOffsets:offsets)
                imageCache.setObject(label,forKey:key)
            } } }
        }
        prepare(["WINNING ENTRY"],RaffleDrumSpinAssets.sourceSerif,10.5,0.12,[0x4a2c07,0xfff1c4],[displayScale],false)
        prepare(["#"]+(0..<10).map(String.init),RaffleDrumSpinAssets.sourceSerif,30,0,[0x4a2c07,0xfff1c4],[displayScale],false)
        prepare(["ENTRIES"],RaffleDrumSpinAssets.interSemiBold,8,0.08,[0x4a2c07],[displayScale],false)
        prepare(["×1","×2","×3"],RaffleDrumSpinAssets.sourceSerif,22,0,[0x4a2c07,0xfff1c4],[displayScale],false)
        prepare(RaffleDrumSpinDraw.cardCopy.flatMap{$0},RaffleDrumSpinAssets.interRegular,15,0,[0xc7c0d6,0xa9a2ba],[displayScale,1],true)
        prepare(["Play","Claim ticket"],RaffleDrumSpinAssets.interSemiBold,17,0,[0xffffff,0x3b2205],[displayScale],true)
        warmed.setObject(1,forKey:warmKey)
    }

    static func glyphRuns(_ string: String, name: String, size: Double) -> [(glyph: String, advance: Double)] {
        let scalarSize = Double(Float(size))
        let key = "\(name)|\(scalarSize)|\(string)" as NSString
        if let cached = glyphCache.object(forKey: key) { return cached.runs }
        let font = UIFont(name: name, size: scalarSize) ?? UIFont.systemFont(ofSize: scalarSize)
        let runs: [(glyph: String, advance: Double)] = string.map { character in
            var units = Array(String(character).utf16)
            var glyphs = Array(repeating: CGGlyph(), count: units.count)
            guard CTFontGetGlyphsForCharacters(font as CTFont, &units, &glyphs, units.count), let glyph = glyphs.first else { return (String(character), 0) }
            var value = glyph
            var advance = CGSize.zero
            CTFontGetAdvancesForGlyphs(font as CTFont, .horizontal, &value, &advance, 1)
            return (String(character), advance.width)
        }
        glyphCache.setObject(Glyphs(runs), forKey: key)
        return runs
    }

    static func draw(context: inout GraphicsContext, string: String, name: String, size: Double, color: Color, x: Double?, centerX: Double?, baseline: Double, trackingEm: Double = 0, baselineSnap: Bool = true) {
        var red: CGFloat = 0, green: CGFloat = 0, blue: CGFloat = 0, alpha: CGFloat = 0
        guard UIColor(color).getRed(&red, green: &green, blue: &blue, alpha: &alpha), alpha > 0.0001 else { return }
        let scalarSize = Double(Float(size))
        let displayScale: Double = context.environment.displayScale
        let runs = glyphRuns(string, name: name, size: scalarSize)
        let gap = trackingEm * scalarSize
        let width = runs.reduce(0) { $0 + $1.advance } + gap * Double(max(0,runs.count-1))
        let originX = x ?? ((centerX ?? 0) - width/2)
        let m = context.transform
        // SkFont baseline snapping applies to scale/translate draws, not perspective or rotation.
        let snap = baselineSnap && m.b == 0 && m.c == 0 && m.a == m.d && m.a > 0
        let rasterScale = displayScale * (snap ? Double(m.a) : 1)
        let rawFirst = (Double(m.a)*originX + Double(m.tx)) * displayScale
        let firstPixel = floor(rawFirst + 0.5)
        var pattern: UInt64 = 14695981039346656037
        var advance = 0.0
        if snap {
            for run in runs {
                let pixel = Int64(floor(rawFirst + advance*rasterScale + 0.5) - firstPixel)
                pattern = (pattern ^ UInt64(bitPattern: pixel)) &* 1099511628211
                advance += run.advance + gap
            }
        }
        let key = "\(rasterScale)|\(name)|\(scalarSize)|\(trackingEm)|\(Float(red).bitPattern)|\(Float(green).bitPattern)|\(Float(blue).bitPattern)|\(snap)|\(pattern)|\(string)" as NSString
        let label: Label
        if let cached = imageCache.object(forKey: key) { label = cached }
        else {
            var offsets: [Int]?
            if snap {
                var values: [Int] = []
                var cursor = 0.0
                for run in runs {
                    values.append(Int(floor(rawFirst + cursor*rasterScale + 0.5) - firstPixel))
                    cursor += run.advance + gap
                }
                offsets = values
            }
            label = Label(runs: runs, name: name, size: scalarSize, ink: UIColor(red:red,green:green,blue:blue,alpha:1), gap:gap, rasterScale:rasterScale, pixelOffsets:offsets)
            imageCache.setObject(label, forKey:key, cost:Int(Double(label.imageSize.width*label.imageSize.height)*rasterScale*rasterScale*4))
        }
        let drawX = snap ? (firstPixel/displayScale-Double(m.tx))/Double(m.a) : originX
        let drawBaseline = snap ? (floor((Double(m.d)*baseline+Double(m.ty))*displayScale + 0.5)/displayScale-Double(m.ty))/Double(m.d) : baseline
        var drawing = context
        drawing.opacity *= Double(alpha)
        drawing.draw(label.image, in:CGRect(x:drawX-label.padding,y:drawBaseline-label.ascender-label.padding,width:label.imageSize.width,height:label.imageSize.height))
    }
}
