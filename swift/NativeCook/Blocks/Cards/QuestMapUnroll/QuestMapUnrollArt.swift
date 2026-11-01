import SwiftUI
import UIKit

struct QuestMapUnrollArt {
    let g: QuestMapUnrollGeometry
    let r = QuestMapUnrollResources.shared
    private func color(_ rgb: UInt32, _ a: Double = 1) -> Color {
        Color(uiColor: QuestMapUnrollResources.color(rgb, a))
    }
    private func circle(_ c: inout GraphicsContext, _ x: Double, _ y: Double, _ radius: Double, _ rgb: UInt32, _ a: Double = 1) {
        var circle = c
        circle.translateBy(x:x,y:y)
        circle.scaleBy(x:radius,y:radius)
        circle.fill(r.unitCircle, with:.color(color(rgb,a)))
    }
    private func box(_ c: inout GraphicsContext, _ rect: CGRect, _ rgb: UInt32, _ a: Double = 1) {
        c.fill(Path(rect), with: .color(color(rgb,a)))
    }
    private func label(_ c: inout GraphicsContext, _ key: String, _ x: Double, _ y: Double, centered: Bool = false) {
        guard let im = r.labels[key] else { return }
        let xx = x - (centered ? (r.widths[key] ?? 0)/2 : 0)
        c.draw(Image(uiImage: im), in: CGRect(x:xx-4,y:y-4,width:im.size.width,height:im.size.height))
    }
    private func icon(_ c: inout GraphicsContext, _ key: String, _ x: Double, _ centerY: Double) {
        guard let im = r.labels[key] else { return }
        label(&c, key, x, centerY - (im.size.height-8)/2, centered:true)
    }
    func draw(_ context: GraphicsContext, _ f: QuestMapUnrollMotion.Frame, _ reduced: Bool, pressed: Int = -1) {
        var c = context
        drawMap(&c, f, reduced)
        drawHeader(&c)
        drawShop(&c, pressed)
        if f.card > 0.0001 { drawCard(&c, f) }
    }
    private func drawMap(_ c: inout GraphicsContext, _ f: QuestMapUnrollMotion.Frame, _ reduced: Bool) {
        let u = reduced ? (f.u > 0.5 ? 1.0 : 0.0) : f.u
        let edgeY = g.rect.minY + g.rect.height * u
        if u > 0.001 {
            var page = c
            page.clip(to: Path(CGRect(x:g.rect.minX,y:g.rect.minY,width:g.rect.width,height:edgeY-g.rect.minY)))
            page.opacity = reduced ? f.u : 1
            page.draw(Image(uiImage:r.page).interpolation(.none), in:g.rect)
        }
        for (i,n) in g.nodes.enumerated() {
            let pass = (edgeY-n.y)/60
            if pass <= 0 { continue }
            let k = reduced ? 1 : QuestMapUnrollMotion.backOut(pass)
            var badge = c
            badge.translateBy(x:n.x,y:n.y)
            badge.scaleBy(x:k,y:k)
            let current = abs(f.wisp-Double(i)) < 0.02 || (i == 2 && f.wisp < 2.02)
            if current {
                let pulse = reduced ? 0.5 : 0.5+0.5*sin(f.t*2 * .pi*3/9)
                circle(&badge,0,0,30+6*pulse,0xFFE38A,0.35+0.25*pulse)
            }
            circle(&badge,0,3,22,0x8B4A2B,0.35)
            circle(&badge,0,0,22,i<=2 ? 0xF4E3C8 : 0xE7D2B1)
            circle(&badge,0,0,18,i<=2 ? 0xFFFFFF : 0xF1E2C8)
            label(&badge,"number\(i)", -(r.widths["number\(i)"] ?? 0).rounded()/2,7-r.numberFont.ascender)
            for s in 0..<3 {
                let a = -.pi/2 + Double(s-1)*0.55
                var star = badge
                star.translateBy(x:cos(a)*32,y:sin(a)*32)
                star.fill(r.star, with:.color(color(s < QuestMapUnrollMotion.stars[i] ? 0xFFC23D : 0xD9C3A0)))
            }
        }
        if u > 0.95 {
            let w = g.along(f.wisp)
            c.draw(Image(uiImage:r.glow),in:CGRect(x:w.x-54,y:w.y-30-54,width:108,height:108))
            circle(&c,w.x,w.y-30,6,0xFFFBE6)
            for s in 1..<6 {
                let tail = g.along(max(0,f.wisp-Double(s)*0.04))
                circle(&c,tail.x,tail.y-30,4-Double(s)*0.6,0xFFE38A,0.6-Double(s)*0.1)
            }
        }
        let h = 34+26*(1-u)
        box(&c,CGRect(x:g.rect.minX-6,y:edgeY-8,width:g.rect.width+12,height:18),0x5A2E18,0.18)
        c.draw(Image(uiImage:r.edge).interpolation(.none),in:CGRect(x:g.rect.minX-8,y:edgeY-h*0.55,width:g.rect.width+16,height:h))
    }
    private func drawHeader(_ c: inout GraphicsContext) {
        let chrome = QuestMapUnrollControlArt.get(g.size.width)
        c.draw(Image(uiImage:chrome.header),in:CGRect(x:0,y:g.top+18,width:g.size.width,height:44))
    }
    private func drawShop(_ c: inout GraphicsContext, _ pressed: Int) {
        let chrome = QuestMapUnrollControlArt.get(g.size.width)
        let y=g.size.height-g.bottom-14-chrome.shopHeight
        for i in 0..<4 {
            let x=16+Double(i)*(g.size.width-114)/3
            var item=c
            if pressed == i {
                item.translateBy(x:x+41,y:y+chrome.shopHeight/2)
                item.scaleBy(x:0.96,y:0.96)
                item.translateBy(x:-x-41,y:-y-chrome.shopHeight/2)
            }
            item.draw(Image(uiImage:chrome.shop[i]),in:CGRect(x:x,y:y,width:82,height:chrome.shopHeight))
        }
    }
    private func drawCard(_ c: inout GraphicsContext, _ f: QuestMapUnrollMotion.Frame) {
        let chrome = QuestMapUnrollControlArt.get(g.size.width)
        var card=c
        card.opacity=f.card
        let y=g.size.height-g.bottom-172+(1-f.card)*30
        card.draw(Image(uiImage:chrome.cards[max(0,min(5,f.level))]),in:CGRect(x:20,y:y,width:g.size.width-40,height:76))
    }
}
