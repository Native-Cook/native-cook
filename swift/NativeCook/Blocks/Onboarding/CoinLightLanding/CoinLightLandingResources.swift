import SwiftUI
import UIKit
import CoreText

final class CoinLightLandingResources {
    static let shared = CoinLightLandingResources()
    private var pages: [String: UIImage] = [:]
    private var rows: [String: Image] = [:]
    let secondary: Image
    let primary: Image
    let fonts: [String: UIFont]
    init() {
        let names = ["InterDisplay-Bold", "Inter-Regular", "Inter-Medium", "Inter-SemiBold"]
        for name in names {
            if let url = Bundle.main.url(forResource: "CoinLightLanding-\(name)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
        let faces = ["title": UIFont(name: "InterDisplay-Bold", size:36)!, "regular": UIFont(name:"Inter24pt-Regular",size:15)!, "medium":UIFont(name:"Inter-Medium",size:15)!, "semi":UIFont(name:"Inter-SemiBold",size:15)!, "glyph":UIFont(name:"Inter-SemiBold",size:11)!, "domain":UIFont(name:"Inter-Medium",size:12)!]
        fonts = faces
        let secondaryBitmap = Self.image(CGSize(width:176,height:44)) { c in
            c.setFillColor(Self.ui(0x1d1d21).cgColor)
            c.addPath(UIBezierPath(roundedRect:CGRect(x:0,y:0,width:176,height:44),cornerRadius:22).cgPath); c.fillPath()
            Self.center("Watch the overview",font:faces["semi"]!,color:.white,rect:CGRect(x:-1.0/3,y:-1.0/3,width:176,height:44))
        }
        let primaryBitmap = Self.image(CGSize(width:164,height:44)) { _ in
            Self.center("Become a creator", font: faces["semi"]!, color: Self.ui(0x050506), rect:CGRect(x:-1.0/3,y:-1.0/3,width:164,height:44))
        }
        secondary=Image(uiImage:secondaryBitmap)
        primary=Image(uiImage:primaryBitmap)
    }
    static func ui(_ hex: Int, _ a: Double = 1) -> UIColor {
        UIColor(red:Double(hex>>16&255)/255,green:Double(hex>>8&255)/255,blue:Double(hex&255)/255,alpha:a)
    }
    static func color(_ hex: Int,_ a: Double = 1) -> Color { Color(uiColor: ui(hex,a)) }
    static func image(_ size: CGSize, _ draw: (CGContext)->Void) -> UIImage {
        let format=UIGraphicsImageRendererFormat(); format.scale=3; format.preferredRange = .standard
        return UIGraphicsImageRenderer(size:size,format:format).image { draw($0.cgContext) }
    }
    static func center(_ text: String,font: UIFont,color: UIColor,rect: CGRect) {
        let attrs: [NSAttributedString.Key:Any] = [.font:font,.foregroundColor:color]
        let size=(text as NSString).size(withAttributes:attrs)
        (text as NSString).draw(at:CGPoint(x:rect.midX-size.width/2,y:rect.midY-size.height/2),withAttributes:attrs)
    }
    private func row(_ c: CGContext,_ i: Int,_ y: Double,_ paid: Int,_ width: Double) {
        c.setFillColor(UIColor.white.cgColor)
        c.addPath(UIBezierPath(roundedRect:CGRect(x:22,y:y+2,width:18,height:18),cornerRadius:4).cgPath); c.fillPath()
        Self.center(["▣","♥","∞","$"][i],font:fonts["glyph"]!,color:Self.ui(0x050506),rect:CGRect(x:22,y:y+2-(i == 0 ? 1 : 1.0/3),width:18,height:18))
        let starts=["Sell your work on the ","Keep 100% of your ","Refer creators and get up to ","$\(paid.formatted(.number.locale(Locale(identifier:"en_US")))) paid to creators "]
        let ends=["Plinth Market","Market earnings","50% commission","in October"]
        let p=NSMutableParagraphStyle(); p.minimumLineHeight=20; p.maximumLineHeight=20
        let a=NSMutableAttributedString(string:starts[i],attributes:[.font:fonts["regular"]!,.foregroundColor:Self.ui(0xffffff,0.6),.paragraphStyle:p])
        a.append(NSAttributedString(string:ends[i],attributes:[.font:fonts["medium"]!,.foregroundColor:UIColor.white,.paragraphStyle:p]))
        a.draw(in:CGRect(x:50,y:y-1,width:width-72,height:40))
    }
    func payout(_ paid: Int,_ width: Double) -> Image {
        let key="\(paid)-\(width)"
        if let cached=rows[key] { return cached }
        let image=Self.image(CGSize(width:width,height:42)) { c in row(c,3,0,paid,width) }
        // The script has only 49 distinct figures; touch can add further payouts.
        if rows.count > 160 { rows.removeAll() }
        let rendered=Image(uiImage:image)
        rows[key]=rendered; return rendered
    }
    func page(_ size: CGSize,_ top: Double,_ bottom: Double) -> UIImage {
        let key="\(size)-\(top)-\(bottom)"
        if let cached=pages[key] { return cached }
        let image=Self.image(size) { c in
            c.setFillColor(Self.ui(0x050506).cgColor); c.fill(CGRect(origin:.zero,size:size))
            func rr(_ rect: CGRect,_ radius: Double,_ color: UIColor) {
                c.setFillColor(color.cgColor); c.addPath(UIBezierPath(roundedRect:rect,cornerRadius:radius).cgPath); c.fillPath()
            }
            for (i,w) in [10.0,16,22].enumerated() { rr(CGRect(x:33-w/2,y:top+17.5+1.0/6+Double(i)*8,width:w,height:5),1.5,.white) }
            for y in [top+23.5+1.0/6,top+30.5+1.0/6] { rr(CGRect(x:size.width-50,y:y,width:20,height:2),1,.white) }
            let p=NSMutableParagraphStyle(); p.minimumLineHeight=40; p.maximumLineHeight=40
            ("Get paid\nto create" as NSString).draw(in:CGRect(x:22,y:top+58,width:330,height:90),withAttributes:[.font:fonts["title"]!,.foregroundColor:UIColor.white,.kern:-0.8,.paragraphStyle:p])
            for i in 0..<3 { row(c,i,top+156+Double(i)*40,733000,size.width) }
            let text="plinth.market", font=fonts["domain"]!
            let tw=(text as NSString).size(withAttributes:[.font:font]).width
            let r=CGRect(x:(size.width-tw-28)/2,y:size.height-bottom-48,width:tw+28,height:30)
            rr(r,15,Self.ui(0x17171a)); Self.center(text,font:font,color:Self.ui(0xffffff,0.75),rect:r.offsetBy(dx:0,dy:-1.0/3))
        }
        pages[key]=image
        // Preload the count-up rather than constructing fonts and text on animation frames.
        for n in 733000...733048 { _ = payout(n,size.width) }
        return image
    }
}
