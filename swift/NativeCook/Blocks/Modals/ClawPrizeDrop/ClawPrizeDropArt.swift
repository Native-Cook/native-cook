import SwiftUI

enum ClawPrizeDropArt {
    typealias M = ClawPrizeDropMotion
    static func draw(_ context: GraphicsContext, _ g: M.Geo, _ o: M.Frame, _ reduced: Bool) {
        let res = ClawPrizeDropResources.shared
        let c = context
        func rr(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double, _ color: UInt32, _ a: Double = 1, _ stroke: Double = 0) {
            let p = Path(roundedRect:CGRect(x:x,y:y,width:w,height:h),cornerRadius:r)
            let style = GraphicsContext.Shading.color(ClawPrizeDropResources.color(color).opacity(a))
            if stroke > 0 { c.stroke(p,with:style,style:StrokeStyle(lineWidth:stroke,lineCap:.round)) }
            else { c.fill(p,with:style) }
        }
        for i in 0..<6 {
            var px = g.px[i], py = g.py[i], sc = 1.0, rot = 0.0, alpha = 1.0
            if i == o.prize {
                if o.carry > 0 {
                    px = o.x+o.swing*0.6
                    py = g.railY+40+(g.floorY-g.railY-40)*o.drop+g.ps*0.55
                    rot = o.swing*0.8
                } else if o.fall >= 0 {
                    if o.fall > 0.5 {
                        if o.respawn <= 0 { continue }
                        sc = 0.6+0.4*o.respawn; py = g.py[i]-(1-o.respawn)*80; alpha = o.respawn
                    } else {
                        let f = o.fall/0.5
                        px = g.chuteX
                        py = g.railY+40+g.ps*0.55+(g.y+g.h+40-(g.railY+40))*f*f
                        rot = reduced ? 0 : f*140; alpha = 1-M.clamp((f-0.8)/0.2)
                    }
                }
            }
            var p = c; p.opacity = alpha; p.translateBy(x:px,y:py); p.rotate(by:.degrees(rot))
            p.draw(res.prizes[i],in:CGRect(x:-g.ps*sc/2,y:-g.ps*sc/2,width:g.ps*sc,height:g.ps*sc))
        }
        let cx = o.x+o.swing, cy = g.railY+30+(g.floorY-g.railY-40)*o.drop
        var cable = Path(); cable.move(to:CGPoint(x:o.x,y:g.railY)); cable.addLine(to:CGPoint(x:cx,y:cy-18))
        c.stroke(cable,with:.color(ClawPrizeDropResources.color(0x5d6b80)),style:StrokeStyle(lineWidth:3,lineCap:.round))
        rr(cx-18,cy-24,36,22,8,0x3a4558)
        let open = 1-o.close
        for k in [-1.0,1.0] {
            var jaw = Path(); jaw.move(to:CGPoint(x:cx+k*10,y:cy-4))
            jaw.addQuadCurve(to:CGPoint(x:cx+k*(6+10*open),y:cy+34),control:CGPoint(x:cx+k*(20+10*open),y:cy+18))
            c.stroke(jaw,with:.color(ClawPrizeDropResources.color(0x3a4558)),style:StrokeStyle(lineWidth:7,lineCap:.round))
        }
    }
}
