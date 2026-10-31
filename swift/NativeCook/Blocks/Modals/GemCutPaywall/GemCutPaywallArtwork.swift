import SwiftUI

enum GemCutPaywallArtwork {
    typealias H = GemCutPaywallTheme
    typealias M = GemCutPaywallMotion
    static func label(_ c: inout GraphicsContext, _ text: String, _ x: Double, _ y: Double, _ size: Double, _ weight: String = "Regular", _ color: Color = H.ink, _ anchor: UnitPoint = .center) {
        c.draw(Text(text).font(H.font(size,weight)).foregroundColor(color),at:CGPoint(x:x,y:y),anchor:anchor)
    }
    static func blend(_ a: UInt32, _ b: UInt32, _ k: Double) -> Color {
        func ch(_ shift: UInt32) -> Double {
            let x=Double((a>>shift)&255)/255, y=Double((b>>shift)&255)/255
            return pow(pow(x,2.2)*(1-k)+pow(y,2.2)*k,1/2.2)
        }
        return Color(.sRGB,red:ch(16),green:ch(8),blue:ch(0),opacity:1)
    }
    static func draw(_ context: inout GraphicsContext, _ l: H.Layout, _ f: M.Frame, _ driver: GemCutPaywallDriver, _ now: Double, _ reduced: Bool) {
        var c=context
        let glints=M.glints(f.yaw,f.pitch,f.cut,f.spark)
        for i in 0..<9 {
            let a=glints[i*3+2]; if a<0.001 { continue }
            var g=c; g.opacity=a; g.blendMode = .plusLighter
            g.translateBy(x:l.gemX+glints[i*3]*l.gemR,y:l.gemY+glints[i*3+1]*l.gemR)
            g.scaleBy(x:4+22*a,y:4+22*a); g.rotate(by:.radians(0.2))
            var p=Path(); p.move(to:CGPoint(x:0,y:-1)); p.addQuadCurve(to:CGPoint(x:1,y:0),control:CGPoint(x:0.09,y:-0.09)); p.addQuadCurve(to:CGPoint(x:0,y:1),control:CGPoint(x:0.09,y:0.09)); p.addQuadCurve(to:CGPoint(x:-1,y:0),control:CGPoint(x:-0.09,y:0.09)); p.addQuadCurve(to:CGPoint(x:0,y:-1),control:CGPoint(x:-0.09,y:-0.09)); p.closeSubpath()
            g.drawLayer { layer in layer.addFilter(.blur(radius:0.04)); layer.fill(p,with:.color(.white)) }
            g.drawLayer { layer in layer.addFilter(.blur(radius:0.12)); layer.fill(Path(ellipseIn:CGRect(x:-0.16,y:-0.16,width:0.32,height:0.32)),with:.color(Color(red:220/255,green:215/255,blue:1).opacity(0.9))) }
        }
        // RN's swap reserves the Free label width while Plus/Pro are absolute overlays.
        let prefix=Text("Facet ").font(H.font(28,"SemiBold")).kerning(-0.4)
        let free=Text("Free").font(H.font(28,"SemiBold")).kerning(-0.4)
        let pW=c.resolve(prefix).measure(in:CGSize(width:1000,height:34)).width
        let fW=c.resolve(free).measure(in:CGSize(width:1000,height:34)).width
        let x=(l.w-pW-fW)/2
        c.draw(prefix.foregroundColor(H.ink),at:CGPoint(x:x,y:l.titleTop+17),anchor:.leading)
        for i in 0..<3 {
            var tc=c; tc.opacity = driver.opacity(i,now)
            tc.draw(Text(H.names[i]).font(H.font(28,"SemiBold")).kerning(-0.4).foregroundColor(H.ink),at:CGPoint(x:x+pW,y:l.titleTop+17),anchor:.leading)
        }
        let digitW=c.resolve(Text("0").font(H.font(17,"Medium")).monospacedDigit()).measure(in:CGSize(width:100,height:22)).width
        let dotW=c.resolve(Text(".").font(H.font(17,"Medium"))).measure(in:CGSize(width:100,height:22)).width
        let dollarW=c.resolve(Text("$").font(H.font(17,"Medium"))).measure(in:CGSize(width:100,height:22)).width
        let perW=c.resolve(Text(" / month").font(H.font(17,"Medium"))).measure(in:CGSize(width:1000,height:22)).width
        var dx=(l.w-(dollarW+3*digitW+dotW+perW))/2
        label(&c,"$",dx,l.priceTop+11,17,"Medium",H.ink,.leading); dx += dollarW
        let digits=driver.digits(now)
        for i in 0..<4 {
            if i == 1 { label(&c,".",dx,l.priceTop+11,17,"Medium",H.ink,.leading); dx += dotW; continue }
            var dc=c; dc.clip(to:Path(CGRect(x:dx,y:l.priceTop,width:digitW,height:22)))
            for d in 0..<10 { label(&dc,String(d),dx,l.priceTop+11+(Double(d)-digits[i])*22,17,"Medium",H.ink,.leading) }
            dx += digitW
        }
        label(&c," / month",dx,l.priceTop+11,17,"Medium",H.mute,.leading)
        let plan=CGRect(x:16,y:l.planTop,width:l.planW,height:48)
        c.fill(Path(roundedRect:plan,cornerRadius:24),with:.color(H.surface))
        c.stroke(Path(roundedRect:plan,cornerRadius:24),with:.color(H.line),lineWidth:1/UIScreen.main.scale)
        c.fill(Path(roundedRect:CGRect(x:20+f.cut*l.thumbRun,y:l.planTop+4,width:l.segW,height:40),cornerRadius:20),with:.color(H.thumb))
        for i in 0..<3 { label(&c,H.names[i],20+l.segW*(Double(i)+0.5),l.planTop+24,15,"SemiBold",blend(0x8F8AA3,0x140F26,max(0,1-abs(f.cut*2-Double(i))))) }
        for i in 0..<3 {
            let from=Double(i == 2 ? 2 : 1), lit=M.clamp01((f.cut*2-(from-0.6))/0.6), y=l.featTop+Double(i)*36
            var icon=c; icon.translateBy(x:32,y:y+2)
            var check=Path(); check.move(to:CGPoint(x:4.5,y:10.5)); check.addLine(to:CGPoint(x:8.5,y:14.5)); check.addLine(to:CGPoint(x:15.5,y:6))
            icon.opacity=lit; icon.stroke(check,with:.color(H.ink),style:StrokeStyle(lineWidth:2,lineCap:.round,lineJoin:.round))
            icon.opacity=1-lit
            var lock=Path(); lock.move(to:CGPoint(x:6.5,y:9)); lock.addLine(to:CGPoint(x:6.5,y:7)); lock.addCurve(to:CGPoint(x:10,y:3.5),control1:CGPoint(x:6.5,y:5.067),control2:CGPoint(x:8.067,y:3.5)); lock.addCurve(to:CGPoint(x:13.5,y:7),control1:CGPoint(x:11.933,y:3.5),control2:CGPoint(x:13.5,y:5.067)); lock.addLine(to:CGPoint(x:13.5,y:9))
            icon.stroke(lock,with:.color(H.mute),lineWidth:1.6)
            icon.fill(Path(roundedRect:CGRect(x:4,y:9,width:12,height:8),cornerRadius:1),with:.color(H.mute))
            label(&c,H.features[i],64,y+12,15,"Regular",blend(0x8F8AA3,0xF5F3FA,lit),.leading)
        }
        for i in 0..<3 {
            var tc=c; tc.opacity = driver.opacity(i,now)
            label(&tc,H.notes[i],l.w/2,l.noteTop+9,13,"Regular",H.mute)
        }
        let fingerCut=driver.manual ? driver.frozenFingerCut : f.cut
        var fx=20+l.segW/2+fingerCut*l.thumbRun, fy=l.planTop+24
        if f.fingerOn == 1 { fx=l.gemX+M.flickOffset(driver.loopTime)*l.gemR; fy=l.gemY+l.gemR*0.05 }
        else if f.fingerOn>=2 { fx=20+l.segW*(f.fingerOn == 2 ? 1.5 : 0.5) }
        let radius=22*(1-0.12*f.press), ring=Path(ellipseIn:CGRect(x:fx-radius,y:fy-radius,width:radius*2,height:radius*2))
        c.fill(ring,with:.color(.white.opacity(0.16*f.finger))); c.stroke(ring,with:.color(.white.opacity(0.55*f.finger)),lineWidth:1.5)
    }
}
