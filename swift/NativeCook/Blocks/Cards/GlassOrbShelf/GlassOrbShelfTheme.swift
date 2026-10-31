import UIKit
import CoreText

enum GlassOrbShelfTheme {
    struct App {
        let first: String
        let rest: String
        let tint: UInt32
        let body: String
        var label: String { "\(first) \(rest). \(body)" }
    }
    static let apps = [
        App(first: "Non-fiction", rest: "summaries", tint: 0x5DD39C, body: "Detailed outlines of popular non-fiction books, to learn a topic in ten minutes."),
        App(first: "Travel", rest: "bucket list", tint: 0x78B4FF, body: "Keep track of places you’ve been and dream of visiting, on beautiful cards."),
        App(first: "Film photo", rest: "studio", tint: 0x62D2DF, body: "Turn your photos into soft film shots with grain, light leaks and classic stocks."),
        App(first: "Receipt to", rest: "recipe", tint: 0xFF8A73, body: "Snap your grocery bill and get three recipes made from what you bought.")
    ]
    static func color(_ hex: UInt32) -> UIColor {
        UIColor(red: CGFloat((hex>>16)&255)/255, green: CGFloat((hex>>8)&255)/255, blue: CGFloat(hex&255)/255, alpha: 1)
    }
    static let page = color(0x0D0E11)
    static let card = color(0x17181D)
    static let ink = color(0xF2F3F5)
    static let body = color(0xA4A7AF)
    static let mute = color(0x6C707A)
    // Registers the bundled Inter files once and remembers each weight's PostScript name (the files
    // name themselves "Inter24pt-…" or "Inter-…" depending on the cut, so it is read, not guessed).
    static let names: [String: String] = {
        var out: [String: String] = [:]
        for weight in ["Regular", "SemiBold", "Bold"] {
            guard let url = Bundle.main.url(forResource: "GlassOrbShelf-Inter-\(weight)", withExtension: "ttf") else { continue }
            CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            if let descs = CTFontManagerCreateFontDescriptorsFromURL(url as CFURL) as? [CTFontDescriptor],
               let first = descs.first,
               let name = CTFontDescriptorCopyAttribute(first, kCTFontNameAttribute) as? String {
                out[weight] = name
            }
        }
        return out
    }()
    static func font(_ weight: String, _ size: CGFloat) -> UIFont {
        UIFont(name: names[weight] ?? "Inter-\(weight)", size: size) ?? .systemFont(ofSize: size, weight: weight == "Bold" ? .bold : weight == "SemiBold" ? .semibold : .regular)
    }
    // Cache static card artwork; only transforms and shader uniforms change each frame.
    static func cardImage(_ app: App, width: Double, fontScale: Double = 1) -> UIImage {
        UIGraphicsImageRenderer(size: CGSize(width: width, height: 196)).image { ctx in
            let path = UIBezierPath(roundedRect: CGRect(x: 0.5, y: 0.5, width: width-1, height: 195), cornerRadius: 26)
            card.setFill(); path.fill(); UIColor.white.withAlphaComponent(0.06).setStroke(); path.lineWidth=1; path.stroke()
            let titleScale=min(fontScale,1.2), bodyScale=min(fontScale,1.25)
            let titleLine=26*titleScale, bodyLine=20*bodyScale
            let title = NSMutableAttributedString(string: app.first+" "+app.rest, attributes: [.font: font("SemiBold",22*titleScale), .foregroundColor: ink, .kern: -0.45*titleScale])
            title.addAttribute(.foregroundColor, value: color(app.tint), range: NSRange(location: 0, length: (app.first as NSString).length))
            let p = NSMutableParagraphStyle(); p.minimumLineHeight=titleLine; p.maximumLineHeight=titleLine
            title.addAttribute(.paragraphStyle,value:p,range:NSRange(location:0,length:title.length))
            let titleRect = CGRect(x:21,y:21,width:width-42,height:100)
            let titleH = ceil(title.boundingRect(with: CGSize(width:width-42,height:100),options:[.usesLineFragmentOrigin,.usesFontLeading],context:nil).height/titleLine)*titleLine
            title.draw(with:titleRect,options:[.usesLineFragmentOrigin,.usesFontLeading],context:nil)
            let bp = NSMutableParagraphStyle(); bp.minimumLineHeight=bodyLine; bp.maximumLineHeight=bodyLine; bp.lineBreakMode = .byWordWrapping
            (app.body as NSString).draw(with:CGRect(x:21,y:21+titleH+8,width:width-42,height:bodyLine*3),options:[.usesLineFragmentOrigin,.usesFontLeading],attributes:[.font:font("Regular",15*bodyScale),.foregroundColor:body,.paragraphStyle:bp],context:nil)
            let open = NSAttributedString(string:"Open",attributes:[.font:font("SemiBold",15*fontScale),.foregroundColor:page])
            let sz=open.size()
            let pillWidth=sz.width+36
            let pill = CGRect(x:width-21-pillWidth,y:139,width:pillWidth,height:36)
            ink.setFill(); UIBezierPath(roundedRect:pill,cornerRadius:18).fill()
             open.draw(at:CGPoint(x:pill.midX-sz.width/2,y:pill.midY-sz.height/2))
            let meta=NSAttributedString(string:"Mini app · Free",attributes:[.font:font("Regular",13*fontScale),.foregroundColor:mute])
            meta.draw(at:CGPoint(x:21,y:pill.midY-meta.size().height/2))
        }
    }
}
