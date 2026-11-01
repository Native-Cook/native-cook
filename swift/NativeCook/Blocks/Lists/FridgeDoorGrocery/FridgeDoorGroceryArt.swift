import SwiftUI
import UIKit

struct FridgeDoorGroceryArt {
    typealias M = FridgeDoorGroceryMotion
    let r = FridgeDoorGroceryResources.shared
    static func color(_ hex: Int, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: Double(hex >> 16 & 255) / 255, green: Double(hex >> 8 & 255) / 255, blue: Double(hex & 255) / 255, opacity: alpha)
    }
    func rect(_ c: GraphicsContext, _ x: Double, _ y: Double, _ w: Double, _ h: Double, _ hex: Int, _ radius: Double = 0, _ alpha: Double = 1) {
        c.fill(Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: min(radius,min(w,h)/2)), with: .color(r.encodedColor(hex, alpha)))
    }
    func circle(_ c: GraphicsContext, _ x: Double, _ y: Double, _ radius: Double, _ hex: Int, _ alpha: Double = 1) {
        c.fill(Path(ellipseIn: CGRect(x: x-radius, y: y-radius, width: radius*2, height: radius*2)), with: .color(r.encodedColor(hex, alpha)))
    }
    func quad(_ c: GraphicsContext, _ v: [Double], _ hex: Int, _ alpha: Double = 1) {
        var p = Path()
        p.move(to: CGPoint(x: v[0], y: v[1]))
        for i in stride(from: 2, to: v.count, by: 2) { p.addLine(to: CGPoint(x: v[i], y: v[i+1])) }
        p.closeSubpath()
        c.fill(p, with: .color(r.encodedColor(hex, alpha)))
    }
    func label(_ c: GraphicsContext, _ key: String, _ x: Double, _ y: Double, _ centered: Bool = false) {
        guard let l = r.labels[key] else { return }
        c.draw(l.image, in: CGRect(x: x - (centered ? l.size.width / 2 : 0), y: y, width: l.size.width, height: l.size.height))
    }
    func draw(_ c: GraphicsContext, _ size: CGSize, _ top: Double, _ bottom: Double, _ f: M.Frame, _ reduced: Bool) {
        let g = M.Geo(size, top)
        var scene = c
        scene.addFilter(.colorShader(ShaderLibrary.fridgeDoorGroceryDecode()))
        scene.drawLayer { layer in
            layer.fill(Self.screenPath(size),with:.color(r.encodedColor(0xFBEFE2)))
            fridge(layer,g,f,reduced)
        }
        let smallH = r.labels["small"]!.size.height
        let titleH = r.labels["title"]!.size.height
        let headerH = ceil(smallH + titleH)
        label(c, "small", 20, top + 8)
        label(c, "title", 20, top + 8 + smallH)
        var bag = c
        let bx = size.width - 44.0, by = top + 8 + headerH / 2
        bag.translateBy(x: bx, y: by)
        bag.scaleBy(x: f.bagScale, y: f.bagScale)
        bag.fill(Self.basketDisc,with:.color(.white))
        label(bag, "basket", 0, -r.labels["basket"]!.size.height / 2, true)
        if f.bag > 0 {
            bag.fill(Self.badgeDisc,with:.color(Self.color(0xFF5A4E)))
            label(bag, "bag\(f.bag)", 16, -16-r.labels["bag\(f.bag)"]!.size.height/2, true)
        }
        let ny = size.height - bottom - 20 - 56
        c.fill(r.notePath(CGRect(x:20,y:ny,width:size.width-40,height:56)),with:.color(.white))
        label(c, "alert", 36, ny + (56-r.labels["alert"]!.size.height)/2)
        label(c, "note", 66, ny + (56-r.labels["note"]!.size.height)/2)
    }
    func fridge(_ c: GraphicsContext, _ g: M.Geo, _ o: M.Frame, _ reduced: Bool) {
        let (x,y,w,h) = (g.x,g.y,g.w,g.h)
        c.draw(r.body(g),in:CGRect(x:x,y:y,width:w+10,height:h+14))
        let open = M.clamp(o.a), ix = x+14, iy = y+14, iw = w-28, ih = h-28
        let sh = ih/4
        for (i,it) in M.items.enumerated() {
            let appear = reduced ? (open > 0.5 ? 1.0 : 0.0) : M.smoother(open*1.6-0.3-Double(it.shelf)*0.08)
            let fl = o.fly[i]
            if fl >= 1 { continue }
            let bx = ix+iw*it.x, by = iy+sh*Double(it.shelf+1)-10
            let fx = bx+(g.bagX-bx)*M.smoother(fl)
            let fy = by+(g.bagY-by)*M.smoother(fl)-sin(.pi*fl)*90
            var item = c
            item.translateBy(x:fx,y:fy+(1-appear)*12)
            let sc = (0.85+0.15*appear)*(1-0.6*fl)
            item.scaleBy(x:sc,y:sc)
            if it.low && fl == 0 && appear > 0.9 {
                let pulse = reduced ? 0.5 : 0.5+0.5*sin(o.t*2 * .pi*3/M.loop)
                circle(item,0,-sh*0.3,sh*(0.3+0.05*pulse),0xFF5A4E,0.08+0.12*pulse)
            }
            var food = item
            food.opacity *= appear
            food.drawLayer { layer in
                var shapes = layer
                shapes.scaleBy(x:sh*0.55,y:sh*0.55)
                Self.foods[i].forEach { shapes.fill($0.0,with:.color(r.encodedColor($0.1,$0.2))) }
            }
            if it.low && fl == 0 && appear > 0.9 {
                item.fill(Self.lowPill,with:.color(r.encodedColor(0xFF5A4E)))
                // Skia's Low label uses a baseline of 15, rather than a Text layout box.
                var low = item
                low.translateBy(x:-r.lowWidth/2,y:15)
                low.fill(r.lowPath,with:.color(.white))
            }
        }
        let ang = reduced ? (open > 0.5 ? 1.95 : 0) : o.a*1.95
        let co = cos(ang), si = sin(ang), near = 1+0.16*si
        let ex = x+w*co, tr = y-h*(near-1)/2, br = y+h+h*(near-1)/2
        let fade = reduced ? 1-open : 1
        if co > 0 {
            quad(c,[x,y,ex,tr,ex,br,x,y+h],0x9FDCCB,fade)
            quad(c,[x,y+4,ex,tr+4,ex,tr+10,x,y+10],0xC6EEE2,fade*0.8)
            let hxp = x+(ex-x)*0.86, hy = y+(tr-y)*0.86+h*0.3
            rect(c,hxp-5,hy,10*max(0.3,co),h*0.24,0xDDE3EA,5,fade)
            circle(c,x+(ex-x)*0.3,y+h*0.18,12*max(0.3,co),0xFF8A65,fade)
        } else {
            quad(c,[x,y,ex,tr,ex,br,x,y+h],0xDDF1EA)
            for sh in 0..<3 {
                let k = 0.28+Double(sh)*0.28
                quad(c,[x,y+h*k,ex,tr+(br-tr)*k,ex,tr+(br-tr)*(k+0.05),x,y+h*(k+0.05)],0xCFE3EA)
            }
        }
    }
    static func screenPath(_ size:CGSize) -> Path {
        FridgeDoorGroceryResources.shared.screenPath(size)
    }
    static let basketDisc = Path(ellipseIn:CGRect(x:-24,y:-24,width:48,height:48))
    static let badgeDisc = Path(ellipseIn:CGRect(x:6,y:-26,width:20,height:20))
    static let lowPill = Path(roundedRect:CGRect(x:-20,y:2,width:40,height:18),cornerRadius:9)
    // Immutable normalized food paths are built once, then transformed at draw time.
    static let foods: [[(Path,Int,Double)]] = {
        var all = [[(Path,Int,Double)]]()
        for kind in 0..<8 {
            var out = [(Path,Int,Double)]()
            func rect(_ x:Double,_ y:Double,_ w:Double,_ h:Double,_ color:Int,_ radius:Double = 0,_ alpha:Double = 1) { out.append((Path(roundedRect:CGRect(x:x,y:y,width:w,height:h),cornerRadius:radius),color,alpha)) }
            func oval(_ x:Double,_ y:Double,_ w:Double,_ h:Double,_ color:Int) { out.append((Path(ellipseIn:CGRect(x:x,y:y,width:w,height:h)),color,1)) }
            func circle(_ x:Double,_ y:Double,_ r:Double,_ color:Int) { oval(x-r,y-r,r*2,r*2,color) }
            func quad(_ v:[Double],_ color:Int,_ transform:CGAffineTransform = .identity) {
                var p = Path(); p.move(to:CGPoint(x:v[0],y:v[1]))
                for i in stride(from:2,to:v.count,by:2) { p.addLine(to:CGPoint(x:v[i],y:v[i+1])) }; p.closeSubpath()
                out.append((p.applying(transform),color,1))
            }
            switch kind {
            case 0:
                rect(-0.32,-0.9,0.64,0.9,0xFFFFFF); quad([-0.32,-0.9,0,-1.15,0.32,-0.9],0xE9EEF5); rect(-0.32,-0.55,0.64,0.22,0x5AA9FF)
            case 1:
                rect(-0.25,-1,0.5,1,0xFFB23D,0.12); rect(-0.12,-1.15,0.24,0.18,0x2F9E62)
            case 2:
                rect(-0.7,-0.3,1.4,0.3,0xC9B9A0,0.06)
                for i in 0..<4 { oval(-0.62+Double(i)*0.32,-0.6,0.26,0.34,0xFFF2DE) }
            case 3:
                quad([-0.6,0,0.6,0,0.6,-0.38,-0.6,-0.12],0xFFD54A); circle(-0.2,-0.12,0.07,0xE8B52E); circle(0.25,-0.2,0.09,0xE8B52E)
            case 4:
                rect(-0.08,-0.4,0.16,0.4,0x7CC46A)
                for i in 0..<4 { circle(-0.25+Double(i%2)*0.5,-0.55-(i>1 ? 0.18:0),0.22,i%2 == 1 ? 0x3F9E4E:0x4FB35C) }
            case 5:
                for i in 0..<3 {
                    let tr = CGAffineTransform(translationX:-0.3+Double(i)*0.3,y:0).rotated(by:Double(-10+i*10) * .pi/180)
                    quad([-0.1,-0.8,0.1,-0.8,0,0],0xFF8A3D,tr)
                    let p = Path(ellipseIn:CGRect(x:-0.08,y:-0.94,width:0.16,height:0.16)).applying(tr)
                    out.append((p,0x4FB35C,1))
                }
            case 6:
                for i in 0..<2 { let d = Double(i)*0.55
                    quad([-0.5+d,-0.5,-0.08+d,-0.5,-0.12+d,0,-0.46+d,0],i == 1 ? 0xFFB8C8:0xC9B8FF)
                    rect(-0.52+d,-0.56,0.46,0.08,0xFFFFFF)
                }
            default:
                rect(-0.28,-0.6,0.56,0.6,0xD9435B,0.1); rect(-0.3,-0.72,0.6,0.14,0xF2D9A0); rect(-0.2,-0.38,0.4,0.18,0xFFFFFF,0,0.85)
            }
            all.append(out)
        }
        return all
    }()
}
