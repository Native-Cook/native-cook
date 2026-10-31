import SwiftUI

struct FirstLootRewardArt {
    typealias M = FirstLootRewardMotion
    typealias R = FirstLootRewardResources
    let w: Double, h: Double, top: Double, bottom: Double
    var bx:Double { w/2 }; var by:Double { top+182 }
    private let resources = R.shared
    private static let sparkles:[[Double]] = [[3,-106,9,1,0],[111,-12,9,1,1],[-126,20,4.5,1,1],[75,70,4.5,1,0],[-75,-76,3,0,0],[93,-60,3,0,0],[-71,83,3,0,1],[-111,-87,1.5,0,1],[50,-100,1.5,0,1],[105,-49,1.5,0,1],[119,10,1.5,0,1],[-89,58,1.5,0,1],[90,44,1.5,0,1],[-115,-2,1,0,1],[-45,-102,1,0,1]]
    private func circle(_ c:inout GraphicsContext,_ x:Double,_ y:Double,_ r:Double,_ color:Int,_ a:Double=1) {
        c.fill(Path(ellipseIn:CGRect(x:x-r,y:y-r,width:r*2,height:r*2)),with:.color(R.color(color,a)))
    }
    private func blur(_ c:GraphicsContext,_ sigma:Double,_ alpha:Double,_ draw:(inout GraphicsContext)->Void) {
        var ctx=c; ctx.opacity *= max(0,alpha); ctx.addFilter(.blur(radius:sigma)); ctx.drawLayer { layer in draw(&layer) }
    }
    private func radial(_ c:inout GraphicsContext,_ x:Double,_ y:Double,_ r:Double,_ gradient:Gradient,_ alpha:Double=1) {
        var ctx=c; ctx.opacity *= alpha; ctx.translateBy(x:x,y:y); ctx.scaleBy(x:r,y:r)
        ctx.fill(resources.unitCircle,with:.radialGradient(gradient,center:.zero,startRadius:0,endRadius:1))
    }
    func draw(_ source:GraphicsContext,_ f:M.Frame) {
        var c=source
        radial(&c,bx,by,230,R.auraGradient,f.sceneA)
        if f.sceneA > 0.01 && f.sparkA > 0.01 {
            for (i,s) in Self.sparkles.enumerated() {
                let pop=M.out(M.seg(f.sparkA,Double(i)*0.04,Double(i)*0.04+0.45)), k=pop*f.sceneA
                if k <= 0.01 { continue }
                let x=bx+s[0]-f.tiltX*(4+s[2]), y=by+s[1]-f.tiltY*(4+s[2])
                if s[3] == 1 { c.fill(R.star(x,y,s[2]*(0.6+0.4*pop)),with:.color(R.color(s[4] == 0 ? 0x6a38c8 : 0xcdb9f7,min(1,k*1.2)))) }
                else { circle(&c,x,y,s[2]*pop,s[4] == 0 ? 0x6a38c8 : 0x7a64a0,min(1,k*1.1)) }
            }
        }
        let o=f.chest.since
        if o >= 0 && o <= 1.2 {
            let bloom=M.smooth(o/0.06)*(1-M.smooth(M.seg(o,0.35,0.8))), radius=150*(0.85+0.15*f.power)
            if bloom > 0.01 { radial(&c,bx,by,radius,R.bloomGradient,bloom) }
        }
        if f.ring > 0.01 {
            var ring=c; ring.translateBy(x:bx,y:by)
            blur(ring,4,0.35*f.ring) { $0.stroke(resources.ring,with:.color(R.color(0xc39bff)),lineWidth:4) }
            ring.stroke(resources.ring,with:.color(R.color(0xb9a2e8,0.9*f.ring)),lineWidth:1.5)
        }
        badge(c,f)
        if o >= 0 && o <= 1.6 {
            let rw=M.seg(o,0.08,0.6)
            if rw > 0 && rw < 1 {
                let radius=92+rw*80
                c.stroke(Path(ellipseIn:CGRect(x:bx-radius,y:by-radius,width:radius*2,height:radius*2)),with:.color(R.color(0xcdb9f7,0.45*(1-rw))),lineWidth:1.4)
            }
            for i in 0..<12 {
                let ii=Double(i), st=0.12+M.hash(ii*3.1)*0.45, k=(o-st)/0.6
                if k <= 0 || k >= 1 { continue }
                let x=bx+(M.hash(ii*7.3)-0.5)*46+sin(k*6+ii)*2, y=by-4-k*(26+M.hash(ii*1.9)*22), a=sin(k*Double.pi)*0.95
                if i%3 == 0 { c.fill(R.star(x,y,2.4),with:.color(R.color(0xffffff,a))) }
                else { circle(&c,x,y,0.8+M.hash(ii*5.5)*0.7,i%2 == 1 ? 0xcdb9f7 : 0xffffff,a) }
            }
        }
    }
    private func badge(_ source:GraphicsContext,_ f:M.Frame) {
        if f.badgeA <= 0.005 { return }
        var c=source; c.translateBy(x:bx,y:by)
        let scale=f.badgeS*(1-f.press*0.04); c.scaleBy(x:scale,y:scale)
        c.concatenate(CGAffineTransform(a:1,b:f.tiltY*0.05,c:f.tiltX*0.05,d:1,tx:0,ty:0))
        c.opacity *= f.badgeA
        c.drawLayer { layer in
            blur(layer,20+12*f.charge,0.35+0.5*f.charge) { $0.fill(resources.outer,with:.color(R.color(0x8e5cf0))) }
            let box=CGRect(x:-150,y:-150,width:300,height:300)
            layer.draw(resources.rim,in:box)
            var holo=layer; holo.opacity *= 0.45*min(1,abs(f.tiltX)+abs(f.tiltY))+0.3*f.charge
            let turn=f.t/4*360+f.tiltX*120+f.tiltY*60
            holo.fill(resources.outer,with:.conicGradient(Gradient(colors:[0xffb3f0,0xb9a2ff,0x8fe8ff,0xc8ffd9,0xffd7a8,0xffb3f0].map { R.color($0) }),center:.zero,angle:.degrees(turn)))
            layer.draw(resources.faceRecess,in:box)
            let tilt=abs(f.tiltX)+abs(f.tiltY)
            if tilt > 0.02 {
                var sheen=layer; sheen.opacity *= min(0.4,tilt*0.4)
                let pos=f.tiltX*0.9
                sheen.fill(resources.face,with:.linearGradient(Gradient(colors:[.white.opacity(0),.white.opacity(0.9),.white.opacity(0)]),startPoint:CGPoint(x:(pos-0.35)*R.ro,y:-R.ro),endPoint:CGPoint(x:(pos+0.35)*R.ro,y:R.ro)))
                // Re-draw the recess: in Skia the sheen is painted before the recess.
                sheen = layer; sheen.clip(to:resources.inner); sheen.draw(resources.faceRecess,in:box)
            }
            var glow=layer; glow.clip(to:resources.inner)
            blur(glow,18,0.18+0.5*f.chest.light) { $0.fill(resources.innerGlow,with:.color(R.color(0xc39bff))) }
            var chest=layer; chest.translateBy(x:f.tiltX*3,y:2+f.tiltY*3); chest.scaleBy(x:0.94,y:0.94)
            let sq=f.chest.gather*0.07+f.chest.bump*0.05
            let shake=f.chest.gather > 0.5 ? sin(f.chest.gather*40)*1.6*(f.chest.gather-0.5) : 0
            chest.translateBy(x:shake,y:23); chest.scaleBy(x:1+sq*0.8,y:1-sq); chest.translateBy(x:0,y:-23)
            chest.draw(resources.body,in:box)
            if f.chest.light > 0.01 {
                blur(chest,6,f.chest.light) { $0.fill(resources.chestLight,with:.color(.white)) }
                chest.fill(resources.chestStrip,with:.color(.white.opacity(f.chest.light)))
                blur(chest,16,0.55*f.chest.light) { $0.fill(resources.chestHalo,with:.color(R.color(0xf2e6ff))) }
            }
            chest.translateBy(x:-6*f.chest.lid,y:-15*f.chest.lid)
            chest.translateBy(x:0,y:6); chest.rotate(by:.degrees(-24*f.chest.lid+f.tiltX*2)); chest.translateBy(x:0,y:-6)
            chest.draw(resources.lid,in:box)
        }
    }
}
