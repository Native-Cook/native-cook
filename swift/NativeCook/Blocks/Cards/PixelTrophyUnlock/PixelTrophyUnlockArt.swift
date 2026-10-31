import SwiftUI
import UIKit

struct PixelTrophyUnlockArt {
    typealias R = PixelTrophyUnlockResources
    typealias M = PixelTrophyUnlockMotion
    let size: CGSize
    let top: Double
    let bottom: Double
    let resources = R.shared
    var rowY: Double { top+186 }
    var gridY: Double { rowY+258 }
    func position(_ i: Int) -> CGPoint { CGPoint(x:24+Double(i%4)*(80+(size.width-368)/3),y:gridY+Double(i/4)*156) }
    func draw(_ ctx: GraphicsContext, _ k: M.Channels, _ shake: Int, _ shakeTime: Double) {
        var c = ctx
        c.draw(resources.background(size,top),in:CGRect(origin:.zero,size:size))
        for i in 0..<3 {
            let x = 24+(Double(i)+k.shift)*146
            if x<=size.width+10, let image = resources.big[PixelTrophyUnlockTheme.recent[i]] { c.draw(image,in:CGRect(x:x,y:rowY,width:128,height:176)) }
        }
        for i in 0..<8 {
            let p = position(i)
            if p.y>size.height { continue }
            let sat = i == 0 ? 1 : i == 1 ? k.colour : 0
            let scale = i == 1 ? 1+0.08*k.pulse : 1
            let dx = i == shake && shakeTime>=0 && shakeTime<0.45 ? sin(shakeTime*40)*5*exp(-shakeTime*7) : 0
            var tile = c;tile.translateBy(x:p.x+40+dx,y:p.y+49);tile.scaleBy(x:scale,y:scale);tile.translateBy(x:-40,y:-49)
            var coloured = tile
            let dim = M.mix(0.62,1,sat)
            let a = (1-sat)*0.2126, b = (1-sat)*0.7152, d = (1-sat)*0.0722
            var matrix = ColorMatrix()
            matrix.r1 = Float((a+sat)*dim);matrix.r2 = Float(b*dim);matrix.r3 = Float(d*dim)
            matrix.g1 = Float(a*dim);matrix.g2 = Float((b+sat)*dim);matrix.g3 = Float(d*dim)
            matrix.b1 = Float(a*dim);matrix.b2 = Float(b*dim);matrix.b3 = Float((d+sat)*dim)
            coloured.addFilter(.colorMatrix(matrix))
            let scene = PixelTrophyUnlockTheme.grid[i]+(i == 1 && k.colour>0.02 ? "-color" : "")
            if let image = resources.small[scene] { coloured.draw(image,in:CGRect(x:0,y:0,width:80,height:98)) }
            if i == 1 && k.sweep>=0 && k.sweep<=1 {
                var band = tile;band.clip(to:R.sweepClip);band.blendMode = .plusLighter
                let bx = -48+k.sweep*176
                band.fill(R.smallBounds,with:.linearGradient(R.sweepGradient,startPoint:CGPoint(x:bx-24,y:0),endPoint:CGPoint(x:bx+24,y:39.2)))
            }
            let lock = i == 0 ? 0 : i == 1 ? 1-min(1,k.colour*3) : 1
            if lock>0.01 {
                tile.withCGContext { cg in
                    cg.setFillColor(R.color("#1a1b20",0.85*lock).cgColor);cg.fillEllipse(in:CGRect(x:25,y:45.08-15,width:30,height:30))
                    cg.setFillColor(R.color("#d9dbe2",lock).cgColor);cg.addPath(R.lockBody);cg.fillPath()
                    let lift = i == 1 ? min(1,k.colour*3)*4 : 0
                    cg.saveGState();cg.translateBy(x:0,y:-lift)
                    cg.setLineWidth(2.4);cg.setStrokeColor(R.color("#d9dbe2",lock).cgColor);cg.addPath(R.shackle);cg.strokePath()
                    cg.restoreGState()
                }
            }
        }
        if k.fly>0.001 && k.gone<0.999 {
            let from = position(1),x = M.mix(from.x+40,88,k.fly),y = M.mix(from.y+49,rowY+88,k.fly)-sin(k.fly * .pi)*90
            let scale = M.mix(80/128,1,M.smooth(k.fly))*(1+k.bounce)*(1-0.25*k.gone)
            var flight = c;flight.translateBy(x:x,y:y);flight.rotate(by:.degrees(sin(k.fly * .pi) * -10));flight.scaleBy(x:scale,y:scale);flight.opacity = 1-k.gone
            flight.drawLayer { layer in
                var shadow = layer;shadow.addFilter(.blur(radius:14));shadow.fill(R.shadowBounds,with:.color(.black.opacity(max(0,0.45*sin(k.fly * .pi)))))
                if let image = resources.big["tenfold"] { layer.draw(image,in:CGRect(x:-64,y:-88,width:128,height:176)) }
            }
        }
    }
}
