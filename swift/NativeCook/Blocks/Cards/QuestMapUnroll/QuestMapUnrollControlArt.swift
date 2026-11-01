import UIKit

// UI chrome is rasterised once per width, rather than rebuilt by the animation canvas.
final class QuestMapUnrollControlArt {
    private static var cache: [Int: QuestMapUnrollControlArt] = [:]
    static func get(_ width: Double) -> QuestMapUnrollControlArt {
        let key = Int((width*1000).rounded())
        if let art = cache[key] { return art }
        let art = QuestMapUnrollControlArt(width)
        cache[key] = art
        return art
    }
    let header: UIImage
    let shop: [UIImage]
    let cards: [UIImage]
    let shopHeight: Double
    private init(_ width: Double) {
        let r = QuestMapUnrollResources.shared
        let fmt = UIGraphicsImageRendererFormat(); fmt.scale = 3
        func bitmap(_ w: Double, _ h: Double, _ block: (CGContext) -> Void) -> UIImage {
            UIGraphicsImageRenderer(size:CGSize(width:w,height:h),format:fmt).image { block($0.cgContext) }
        }
        func bottomBorder(_ c: CGContext,_ box: CGRect,_ radius: Double,_ rgb: UInt32,_ alpha: Double = 1) {
            // RN clips the bottom strip to the outer rounded outline.
            c.saveGState()
            c.addPath(UIBezierPath(roundedRect:box,cornerRadius:radius).cgPath);c.clip()
            c.setFillColor(QuestMapUnrollResources.color(rgb,alpha).cgColor)
            c.fill(CGRect(x:box.minX,y:box.maxY-3,width:box.width,height:3))
            c.restoreGState()
        }
        func round(_ c: CGContext,_ box: CGRect,_ radius: Double,_ rgb: UInt32,_ border: UInt32? = nil) {
            c.addPath(UIBezierPath(roundedRect:box,cornerRadius:radius).cgPath)
            c.setFillColor(QuestMapUnrollResources.color(rgb).cgColor);c.fillPath()
            if let border { bottomBorder(c,box,radius,border) }
        }
        func label(_ key: String,_ x: Double,_ y: Double,_ centered: Bool = false) {
            guard let im=r.labels[key] else { return }
            im.draw(at:CGPoint(x:x-4-(centered ? (r.widths[key] ?? 0)/2 : 0),y:y-4))
        }
        func icon(_ key: String,_ x: Double,_ y: Double) {
            label(key,x,y-(r.labels[key]!.size.height-8)/2,true)
        }
        header = bitmap(width,44) { c in
            round(c,CGRect(x:16,y:0,width:44,height:44),14,0xFFD45A,0xD99A1F)
            icon("back",38,20.5)
            round(c,CGRect(x:width-60,y:0,width:44,height:44),14,0xFFD45A,0xD99A1F)
            icon("settings",width-38,20.5)
            let nw=r.widths["powerNum"] ?? 0, tw=r.widths["powerText"] ?? 0
            let w=32+16+6+nw+6+tw, x=(width-w)/2
            round(c,CGRect(x:x,y:0,width:w,height:44),14,0x4FB548,0x2F8A2C)
            icon("flash",x+24,20.5)
            label("powerNum",x+38,20.5-(r.labels["powerNum"]!.size.height-8)/2)
            // Yoga snaps this centred text box to the physical-pixel grid.
            let textY=floor((20.5-(r.labels["powerText"]!.size.height-8)/2)*3)/3
            label("powerText",x+38+nw+6,textY)
        }
        let h = 56+r.labels["shop0"]!.size.height-8
        shopHeight = h
        shop = (0..<4).map { i in
            bitmap(82,h) { c in
                let colors: [UInt32] = [0xFF8A5B,0xB26BFF,0xFFC23D,0x4FB548]
                round(c,CGRect(x:15,y:0,width:52,height:52),16,colors[i])
                bottomBorder(c,CGRect(x:15,y:0,width:52,height:52),16,0,0.18)
                icon(["puzzle","gift","bulb","store"][i],41,24.5)
                label("shop\(i)",41,56,true)
            }
        }
        cards = (0..<6).map { i in
            bitmap(width-40,76) { c in
                round(c,CGRect(x:0,y:0,width:width-40,height:76),24,0xFFF4DD)
                c.setFillColor(QuestMapUnrollResources.color(0xF4E3C8).cgColor)
                c.fillEllipse(in:CGRect(x:14,y:15,width:46,height:46))
                c.setFillColor(UIColor.white.cgColor);c.fillEllipse(in:CGRect(x:17,y:18,width:40,height:40))
                label("card\(i)",37,38-(r.labels["card\(i)"]!.size.height-8)/2,true)
                let th=r.labels["title\(i)"]!.size.height-8, sh=r.labels["sub\(i)"]!.size.height-8
                let ty=38-(th+2+sh)/2
                label("title\(i)",72,ty)
                label("sub\(i)",72,ty+th+2)
                c.setFillColor(QuestMapUnrollResources.color(0x4FB548).cgColor)
                c.fillEllipse(in:CGRect(x:width-104,y:13,width:50,height:50))
                icon("play",width-79,38)
            }
        }
    }
}
