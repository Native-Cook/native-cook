import SwiftUI
import UIKit
import CoreText
import CoreImage

final class MicCordOnboardingResources {
    static let shared = MicCordOnboardingResources()
    static func uiColor(_ hex: UInt32) -> UIColor {
        UIColor(red: Double((hex >> 16)&255)/255, green: Double((hex >> 8)&255)/255, blue: Double(hex&255)/255, alpha: 1)
    }
    static func color(_ hex: UInt32) -> Color { Color(uiColor(hex)) }
    let mic: Image
    let titleFont: UIFont
    let buttonFont: UIFont
    private static let space = CGColorSpace(name: CGColorSpace.sRGB)!
    init() {
        for name in ["Montserrat-Bold", "Inter-SemiBold"] {
            if let url = Bundle.main.url(forResource: "MicCordOnboarding-\(name)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
        titleFont = UIFont(name: "Montserrat-Bold", size: 30) ?? .boldSystemFont(ofSize: 30)
        buttonFont = UIFont(name: "Inter-SemiBold", size: 17) ?? .systemFont(ofSize: 17, weight: .semibold)
        mic = Image(uiImage: Self.makeMic())
    }
    private static func makeMic() -> UIImage {
        let format = UIGraphicsImageRendererFormat(); format.scale = 3; format.preferredRange = .standard
        let size = CGSize(width: 90, height: 198)
        let highlight = UIGraphicsImageRenderer(size: size, format: format).image { r in
            uiColor(0xffffff).withAlphaComponent(0.35).setFill()
            UIBezierPath(ovalIn: CGRect(x: 45-24, y: 10+92, width: 12, height: 54)).fill()
        }
        let input = CIImage(image: highlight)!
        // Skia's MakeBlur sigma is 4 points. This is computed once, never in a frame callback.
        let blurred = input.applyingFilter("CIGaussianBlur", parameters: [kCIInputRadiusKey: 12])
        let hi = CIContext(options: [.workingColorSpace: space, .outputColorSpace: space]).createCGImage(blurred, from: input.extent)!
        return UIGraphicsImageRenderer(size: size, format: format).image { r in
            let c = r.cgContext; c.translateBy(x: 45, y: 10)
            func gradient(_ path: CGPath, _ x0: Double, _ x1: Double, _ colors: [UInt32]) {
                c.saveGState(); c.addPath(path); c.clip()
                let cols = colors.map { uiColor($0).cgColor } as CFArray
                let g = CGGradient(colorsSpace: space, colors: cols, locations: nil)!
                c.drawLinearGradient(g, start: CGPoint(x:x0,y:0), end: CGPoint(x:x1,y:0), options: [.drawsBeforeStartLocation,.drawsAfterEndLocation]); c.restoreGState()
            }
            let handle = CGMutablePath()
            handle.move(to: CGPoint(x:-13,y:0)); handle.addLine(to: CGPoint(x:13,y:0)); handle.addLine(to: CGPoint(x:19,y:70)); handle.addLine(to: CGPoint(x:-19,y:70)); handle.closeSubpath()
            gradient(handle, -19, 19, [0x7a1270,0xe43fcf,0xb0219e,0x5e0d56])
            gradient(CGPath(roundedRect: CGRect(x:-30,y:66,width:60,height:13), cornerWidth:4,cornerHeight:4,transform:nil), -30, 30, [0x5a0c52,0xff7be6,0xc22eb0,0x4d0a46])
            gradient(CGPath(roundedRect: CGRect(x:-33,y:78,width:66,height:96), cornerWidth:33,cornerHeight:33,transform:nil), -33, 33, [0x8f1683,0xff6be3,0xff4fd8,0xa81d98,0x5a0c52])
            c.setStrokeColor(uiColor(0x3c0637).withAlphaComponent(0.65).cgColor); c.setLineWidth(3); c.setLineCap(.round)
            for i in 0..<9 {
                let y = 96+Double(i)*8.5, half = 26*sqrt(max(0, 1-pow((y-126)/52,2)))
                c.move(to: CGPoint(x:-half,y:y)); c.addLine(to: CGPoint(x:half,y:y)); c.strokePath()
            }
            gradient(CGPath(rect: CGRect(x:-5,y:80,width:10,height:92),transform:nil), -6, 6, [0x6a0f60,0xff9bec,0x6a0f60])
            c.translateBy(x:-45,y:-10)
            UIImage(cgImage:hi,scale:3,orientation:.up).draw(in:CGRect(origin:.zero,size:size))
        }
    }
}
