import CoreImage
import SwiftUI
import UIKit

/*
 The message the smoke forms, baked once into one full-screen mask image (the React Native
 smokeArt.ts bakes two): the sharp words in the red channel and the same words blurred (σ 7 pt) in
 the green channel, on black. The shader samples it as its layer through a displacement. The
 message line is sized down from 27 pt until it fits the screen. SF stands in for Helvetica Neue.
*/
struct SmokeSignalToastArt: Sendable {
    static let sender = "KAI · LAKESIDE CREW"
    static let message = "Dinner's ready at the lake"

    let image: UIImage
    /// Where the words are, for the tap / swipe target.
    let box: CGRect

    /// Bakes the mask for a screen of size w × h with the words centred on centerY. Safe off the
    /// main thread.
    static func bake(w: Double, h: Double, centerY: Double, scale: Double) -> SmokeSignalToastArt {
        let small = UIFont.systemFont(ofSize: 13, weight: .bold)
        var size = 27.0
        var big = UIFont.systemFont(ofSize: size, weight: .semibold)
        let room = w - 56
        func width(_ s: String, _ f: UIFont) -> Double {
            (s as NSString).size(withAttributes: [.font: f]).width
        }
        while width(message, big) > room && size > 18 {
            size -= 1
            big = UIFont.systemFont(ofSize: size, weight: .semibold)
        }
        let bw = width(message, big)
        let sw = width(sender, small)
        let top = centerY - (13 + 10 + size) / 2
        let senderBase = top + 12
        let messageBase = top + 13 + 10 + size * 0.8

        let bounds = CGSize(width: max(1, w), height: max(1, h))
        let format = UIGraphicsImageRendererFormat()
        format.scale = scale
        format.opaque = true

        func words(_ color: UIColor) -> (CGContext) -> Void {
            { _ in
                (sender as NSString).draw(
                    at: CGPoint(x: (w - sw) / 2, y: senderBase - small.ascender),
                    withAttributes: [.font: small, .foregroundColor: color])
                (message as NSString).draw(
                    at: CGPoint(x: (w - bw) / 2, y: messageBase - big.ascender),
                    withAttributes: [.font: big, .foregroundColor: color])
            }
        }

        // The blurred copy: green words, blurred with Core Image.
        let greenFormat = UIGraphicsImageRendererFormat()
        greenFormat.scale = scale
        greenFormat.opaque = true
        let green = UIGraphicsImageRenderer(size: bounds, format: greenFormat).image { ctx in
            UIColor.black.setFill()
            ctx.fill(CGRect(origin: .zero, size: bounds))
            words(UIColor(red: 0, green: 1, blue: 0, alpha: 1))(ctx.cgContext)
        }
        var soft: CGImage?
        if let cg = green.cgImage {
            let input = CIImage(cgImage: cg)
            let blurred = input.clampedToExtent().applyingGaussianBlur(sigma: 7 * scale).cropped(to: input.extent)
            soft = CIContext(options: [.useSoftwareRenderer: false]).createCGImage(blurred, from: input.extent)
        }

        let image = UIGraphicsImageRenderer(size: bounds, format: format).image { ctx in
            let c = ctx.cgContext
            UIColor.black.setFill()
            ctx.fill(CGRect(origin: .zero, size: bounds))
            if let soft {
                // UIKit's context is flipped; draw the CGImage the right way up.
                c.saveGState()
                c.translateBy(x: 0, y: bounds.height)
                c.scaleBy(x: 1, y: -1)
                c.draw(soft, in: CGRect(origin: .zero, size: bounds))
                c.restoreGState()
            }
            c.setBlendMode(.plusLighter)
            words(UIColor(red: 1, green: 0, blue: 0, alpha: 1))(c)
        }

        let bx = max(bw, sw)
        return SmokeSignalToastArt(
            image: image,
            box: CGRect(x: (w - bx) / 2 - 16, y: top - 16, width: bx + 32, height: size + 23 + 32)
        )
    }
}
