import SwiftUI
import UIKit

final class TapEarnCoinResources {
    static let shared = TapEarnCoinResources()
    let frames: [Image]
    private init() {
        _ = TapEarnCoinTheme.register
        if let url = Bundle.main.url(forResource:"TapEarnCoin-coin-atlas",withExtension:"png"),
           let source = UIImage(contentsOfFile:url.path)?.cgImage {
            frames = (0..<24).compactMap { i in
                guard let crop = source.cropping(to:CGRect(x:i*96,y:0,width:96,height:96)) else { return nil }
                // Decode once rather than on the first draw of each tumbling face.
                let renderer = UIGraphicsImageRenderer(size:CGSize(width:96,height:96))
                let bitmap = renderer.image { _ in UIImage(cgImage:crop).draw(in:CGRect(x:0,y:0,width:96,height:96)) }
                return Image(uiImage:bitmap)
            }
        } else { frames = [] }
    }
}

struct TapEarnCoinArtwork {
    typealias M = TapEarnCoinMotion
    typealias H = TapEarnCoinTheme
    let layout: H.Layout
    func draw(_ c: GraphicsContext, _ live: [Double], _ f: TapEarnCoinFrame, _ date: Double, _ refillAge: Double) {
        let L = layout, s = f.scene
        let object = objectPose(f)
        let pullU = M.seg(s.morph,0,0.55), pull = M.easeIn(pullU), keep = M.clamp01(1-1.2*s.dim)
        for b in stride(from:0,to:live.count,by:4) where b+3 < live.count {
            let age = live[b], seed = live[b+1], power = live[b+3]
            if age < 0 || age > M.burstLife { continue }
            for i in 0..<(M.coins+M.coinsPerPower*Int(power.rounded())) {
                let box = (i+Int(floor(seed)))%3
                let p = M.coinPose(age,seed,i,power,L.coinX,L.coinY-0.32*L.coinR,L.coinR,L.boxX(box),L.digitsTop+L.digitH/2,L.ctaTop)
                let x = M.mix(p.x,object.x,pull), y = M.mix(p.y,object.y,pull)
                let r = p.r*(1-0.85*pull), alpha = p.alpha*keep*(1-M.seg(pullU,0.75,1))
                coin(c,x,y,r,p.tilt,p.spin,alpha,p.lit,p.roll,atlas:true)
            }
        }
        if refillAge >= 0 && refillAge < 0.75 {
            for i in 0..<3 {
                let a = refillAge-Double(i)*0.07
                if a < 0 { continue }
                let fall = M.easeIn(M.seg(a,0,0.38)), settle = M.seg(a,0.38,0.6)
                let y = L.labelTop+9-150+150*fall-6*M.ring(max(0,a-0.38),3,10)
                coin(c,L.w/2+34+Double(i-1)*16,y,7*(1-M.easeOut(settle)),0.55+0.45*cos(a*18+Double(i)),a*2,1-settle,0.5+0.5*sin(a*21+Double(i)),0,atlas:false)
            }
        }
        for b in stride(from:0,to:live.count,by:4) where b+3 < live.count {
            let age = live[b]
            if age < 0 || age > 1 { continue }
            let pp = M.pillPose(age,live[b+1]), alpha = pp.alpha*keep
            if alpha <= 0.01 { continue }
            let text = "+\(Int(live[b+2]))¢", font = H.uiFont(14,"SemiBold")
            let width = (text as NSString).size(withAttributes:[.font:font]).width, pw = width+22.0
            var ctx = c; ctx.opacity = alpha
            ctx.translateBy(x:L.coinX+pp.dx*L.coinR,y:L.coinY-1.02*L.coinR-pp.rise*L.coinR*0.32)
            let scale = pp.scale*(1+0.12*live[b+3]); ctx.scaleBy(x:scale,y:scale)
            ctx.fill(Path(roundedRect:CGRect(x:-pw/2,y:-15,width:pw,height:30),cornerRadius:15),with:.color(H.pill))
            // Skia draws the baseline at 4.6; align the SwiftUI text from the same font ascent.
            let label = Text(text).font(H.font(14,"SemiBold")).foregroundColor(.white)
            let resolved = ctx.resolve(label)
            ctx.draw(resolved,at:CGPoint(x:0,y:4.6-font.ascender+font.lineHeight/2),anchor:.center)
        }
    }
    func objectPose(_ f: TapEarnCoinFrame) -> (x:Double,y:Double,u:Double) {
        let L = layout, s = f.scene, tr = s.travel, lf = s.lift
        let by = L.bellY+(1-s.sheet)*L.sheetH, sy = M.mix(L.coinY,L.coinY-64,lf)
        let x = M.mix(L.coinX,L.bellX,tr)
        let y = M.mix(sy,by,tr)-sin(Double.pi*tr)*L.coinR*0.45
        return (x,y,M.mix(L.coinR*(1+0.1*lf),L.bellSize/1.3,tr))
    }
    private func coin(_ context: GraphicsContext, _ x: Double, _ y: Double, _ r: Double, _ tilt: Double,
                      _ spin: Double, _ alpha: Double, _ lit: Double, _ roll: Double, atlas: Bool) {
        if alpha <= 0.01 || r < 0.5 { return }
        var c = context; c.opacity *= alpha; c.translateBy(x:x,y:y); c.rotate(by:.radians(spin))
        let frames = TapEarnCoinResources.shared.frames
        if atlas && frames.count == 24 {
            let tau = Double.pi*2, pos = (roll.truncatingRemainder(dividingBy:tau)+tau).truncatingRemainder(dividingBy:tau)
            let i = Int((pos/tau*24).rounded())%24, size = r/40*96
            c.draw(frames[i],in:CGRect(x:-size/2,y:-size/2,width:size,height:size)); return
        }
        let face = max(0.12,abs(tilt)), ry = r*face, thick = r*0.2*sqrt(1-face*face)
        c.fill(Path(ellipseIn:CGRect(x:-r,y:-ry+thick,width:2*r,height:2*ry)),with:.color(H.color(0x6C6C66)))
        let hi = (200+52*lit).rounded(), lo = (118+40*lit).rounded()
        func rgb(_ r:Double,_ g:Double,_ b:Double) -> Color { Color(.sRGB,red:r/255,green:g/255,blue:b/255,opacity:1) }
        c.fill(Path(ellipseIn:CGRect(x:-r,y:-ry,width:2*r,height:2*ry)),with:.linearGradient(Gradient(colors:[rgb(hi,hi,hi-6),rgb(lo,lo+2,lo-4)]),startPoint:CGPoint(x:-r*0.8,y:-ry),endPoint:CGPoint(x:r*0.7,y:ry)))
        c.stroke(Path(ellipseIn:CGRect(x:-r*0.7,y:-ry*0.7,width:1.4*r,height:1.4*ry)),with:.color(H.color(0x787870).opacity(0.7)),lineWidth:max(0.6,r*0.09))
        c.fill(Path(ellipseIn:CGRect(x:-r*0.62,y:-ry*0.78,width:r*0.7,height:ry*0.5)),with:.color(H.color(0xFFFFFA).opacity(0.85*lit*lit)))
    }
}
