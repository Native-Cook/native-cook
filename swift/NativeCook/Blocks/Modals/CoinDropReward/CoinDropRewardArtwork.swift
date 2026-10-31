import SwiftUI
import UIKit

enum CoinDropRewardArtwork {
  typealias H = CoinDropRewardTheme
  typealias M = CoinDropRewardMotion
  static func label(_ context: inout GraphicsContext,_ text: String,_ x: Double,_ y: Double,_ size: Double,_ height: Double,_ color: Color,_ weight: String = "Regular",_ anchor: UnitPoint = .leading) {
    context.draw(Text(text).font(H.font(size,weight)).foregroundColor(color),at:CGPoint(x:x,y:y+height/2),anchor:anchor)
  }
  static func rounded(_ c: inout GraphicsContext,_ rect: CGRect,_ radius: Double,_ color: Color) {
    c.fill(Path(roundedRect:rect,cornerRadius:radius),with:.color(color))
  }
  static func coin(_ context: inout GraphicsContext,_ x: Double,_ y: Double,_ size: Double) {
    var c = context; c.translateBy(x:x,y:y); c.scaleBy(x:size/44,y:size/44)
    c.fill(Path(ellipseIn:CGRect(x:1,y:1,width:42,height:42)),with:.linearGradient(Gradient(colors:[H.color(0x6FA4FF),H.color(0x1E55F0)]),startPoint:CGPoint(x:8.8,y:0),endPoint:CGPoint(x:35.2,y:44)))
    c.stroke(Path(ellipseIn:CGRect(x:6,y:6,width:32,height:32)),with:.color(.white.opacity(0.35)),lineWidth:1.5)
    var p = Path(); p.move(to:CGPoint(x:17.5,y:31)); p.addLine(to:CGPoint(x:17.5,y:13)); p.addLine(to:CGPoint(x:23.5,y:13))
    p.addCurve(to:CGPoint(x:29,y:18.5),control1:CGPoint(x:26.537566,y:13),control2:CGPoint(x:29,y:15.462434))
    p.addCurve(to:CGPoint(x:23.5,y:24),control1:CGPoint(x:29,y:21.537566),control2:CGPoint(x:26.537566,y:24))
    p.addLine(to:CGPoint(x:17.5,y:24))
    c.stroke(p,with:.color(.white),style:StrokeStyle(lineWidth:3.6,lineCap:.round,lineJoin:.round))
  }
  static func wallet(_ c: inout GraphicsContext) {
    var p = Path(); p.move(to:CGPoint(x:3,y:6.5))
    p.addCurve(to:CGPoint(x:5.5,y:4),control1:CGPoint(x:3,y:5.1193),control2:CGPoint(x:4.1193,y:4))
    p.addLine(to:CGPoint(x:14.5,y:4)); p.addCurve(to:CGPoint(x:16,y:5.5),control1:CGPoint(x:15.328427,y:4),control2:CGPoint(x:16,y:4.671573))
    p.addLine(to:CGPoint(x:16,y:7)); p.addLine(to:CGPoint(x:16.5,y:7))
    p.addCurve(to:CGPoint(x:18,y:8.5),control1:CGPoint(x:17.328427,y:7),control2:CGPoint(x:18,y:7.671573)); p.addLine(to:CGPoint(x:18,y:14.5))
    p.addCurve(to:CGPoint(x:16,y:16.5),control1:CGPoint(x:18,y:15.604569),control2:CGPoint(x:17.104569,y:16.5)); p.addLine(to:CGPoint(x:5.5,y:16.5))
    p.addCurve(to:CGPoint(x:3,y:14),control1:CGPoint(x:4.119288,y:16.5),control2:CGPoint(x:3,y:15.380712)); p.closeSubpath()
    c.fill(p,with:.color(H.blue)); c.fill(Path(ellipseIn:CGRect(x:13.1,y:10.1,width:2.8,height:2.8)),with:.color(.white))
  }
  static func page(_ context: inout GraphicsContext,_ l: CoinDropRewardLayout,_ f: CoinDropRewardFrame,_ selected: Int,_ left: [Double],_ pressed: Int?) {
    var c = context
    label(&c,"Today's visit missions",20,l.titleTop,22,30,H.ink,"SemiBold")
    label(&c,"Browse a product each day to earn points.",20,l.titleTop+34,14,20,H.mute)
    var w = c
    w.translateBy(x:l.walletLeft+62,y:l.walletTop+20)
    let wobble = M.wobbleAt(f.wobbleU); w.scaleBy(x:1+0.1*wobble,y:1-0.08*wobble)
    w.translateBy(x:-62,y:-20)
    w.drawLayer { shadow in
      shadow.addFilter(.shadow(color:H.color(0x28285A).opacity(0.1),radius:7,x:0,y:4))
      rounded(&shadow,CGRect(x:0,y:0,width:124,height:40),20,.white)
    }
    var icon = w; icon.translateBy(x:14,y:10); wallet(&icon)
    label(&w,"Points",42,10,14,20,H.ink,"SemiBold")
    var plus = c; plus.opacity = max(0,sin(.pi*f.plus))
    label(&plus,"+5 P",l.walletLeft-44-10*f.plus,l.walletTop+10,14,20,H.blue,"SemiBold")
    label(&c,"2 missions",20,l.sectionTop,14,18,H.accent,"Medium")
    for i in 0..<2 {
      let y = l.rowsTop+Double(i)*72
      if pressed == i { rounded(&c,CGRect(x:20,y:y,width:l.w-40,height:72),16,H.color(0xF2F1FA)) }
      var rowCoin = c
      rowCoin.opacity = f.out && selected == i ? 0 : (0.35+0.65*(1-M.smooth(0,0.25,left[i])))*(1-M.smooth(3.7,4,left[i]))
      coin(&rowCoin,20,y+14,44)
      // RN flex:1 reserves the cooldown pill before truncating the title.
      let secs = Int(ceil(left[i]))
      let available = l.w-20-78-(secs > 0 ? 111 : 22)
      c.drawLayer { text in
        text.clip(to:Path(CGRect(x:78,y:y,width:max(0,available),height:72)))
        let title = Text(H.missions[i]).font(H.font(16,"SemiBold")).foregroundColor(H.ink)
        text.draw(title,in:CGRect(x:78,y:y+15,width:max(0,available),height:22))
        label(&text,"Visit the shop · 5 P",78,y+39,13,18,H.mute)
      }
      if secs > 0 {
        let text = Text("Again in 0:0\(secs)").font(H.font(12,"Medium")).monospacedDigit().foregroundColor(H.blue)
        let resolved = c.resolve(text); let size = resolved.measure(in:CGSize(width:200,height:28))
        let pillWidth = size.width+20, x = l.w-20-pillWidth
        rounded(&c,CGRect(x:x,y:y+22,width:pillWidth,height:28),14,H.blueSoft)
        c.draw(resolved,at:CGPoint(x:x+pillWidth/2,y:y+36),anchor:.center)
      } else {
        var p = Path(); p.move(to:CGPoint(x:l.w-26.5,y:y+31)); p.addLine(to:CGPoint(x:l.w-21.5,y:y+36)); p.addLine(to:CGPoint(x:l.w-26.5,y:y+41))
        c.stroke(p,with:.color(H.color(0xB5B6C2)),style:StrokeStyle(lineWidth:1.8,lineCap:.round,lineJoin:.round))
      }
    }
    label(&c,"Bonus mission",20,l.bonusLabelTop,14,18,H.accent,"Medium")
    rounded(&c,CGRect(x:20,y:l.bonusTop+14,width:44,height:44),22,H.stamp)
    let dots = c
    dots.draw(Text("···").font(H.font(16,"SemiBold")).tracking(1).foregroundColor(H.mute),at:CGPoint(x:42,y:l.bonusTop+36))
    label(&c,"Opens at 1 PM",78,l.bonusTop+15,13,18,H.blue,"Medium")
    label(&c,"Mystery product",78,l.bonusTop+35,16,22,H.ink,"SemiBold")
    var line = Path(); line.move(to:CGPoint(x:20,y:l.daysTop)); line.addLine(to:CGPoint(x:l.w-20,y:l.daysTop))
    c.stroke(line,with:.color(H.line),lineWidth:1/UIScreen.main.scale)
    for i in 0..<7 {
      let x = 40+Double(i)*(l.w-80)/6, y = l.daysTop+12
      label(&c,H.days[i],x,y,12,14.52,i == 5 ? H.accent : H.mute,"Medium",.center)
      let rect = CGRect(x:x-15,y:y+20.52,width:30,height:30)
      c.fill(Path(ellipseIn:rect),with:.color(i == 5 ? H.page : H.stamp))
      if i == 5 { c.stroke(Path(ellipseIn:rect.insetBy(dx:0.75,dy:0.75)),with:.color(H.accent),lineWidth:1.5) }
      if H.stamped.contains(i) { coin(&c,x-13,y+22.52,26) }
    }
    if f.toast > 0 {
      var tile = c; tile.opacity = f.toast
      tile.translateBy(x:l.tileCx,y:l.tileCy); tile.scaleBy(x:0.86+0.14*f.toast,y:0.86+0.14*f.toast)
      tile.drawLayer { shadow in
        shadow.addFilter(.shadow(color:H.color(0x323C8C).opacity(0.16),radius:25,x:0,y:18))
        shadow.fill(Path(roundedRect:CGRect(x:-118,y:-118,width:236,height:236),cornerSize:CGSize(width:36,height:36),style:.continuous),with:.color(.white))
      }
      tile.opacity *= f.text
      label(&tile,"You got 5 points",0,118-28-19.36+6*(1-f.text),16,19.36,H.blue,"SemiBold",.center)
    }
  }
  static func uniforms(_ l: CoinDropRewardLayout,_ s: CoinDropRewardFrame,_ selected: Int) -> (SIMD4<Float>,SIMD4<Float>,SIMD4<Float>) {
    let p = l.rowCoin(selected), lc = M.clamp01(s.launch)
    let bx = p.x+(l.toastCoin.x-p.x)*lc, by = p.y+(l.toastCoin.y-p.y)*lc-30*sin(.pi*lc)
    let px = bx+l.dx*s.pull, py = by+l.dy*s.pull
    let x = px+(l.walletIcon.x-px)*s.fly, y = py+(l.walletIcon.y-py)*s.fly-46*sin(.pi*s.fly)
    let size = (22+44*s.launch)*(1-0.82*s.fly)
    let a = atan2(l.dy,-l.dx)
    let stretch = 0.6*M.clamp01(s.pull/M.pullFull)*(1-s.fly)+0.7*sin(.pi*s.fly)
    return (SIMD4(Float(x),Float(y),Float(size),Float((s.out ? 1 : 0)*(1-M.smooth(0.88,1,s.fly)))),SIMD4(Float(s.melt),Float(2*Double.pi*s.flip),Float(Double.pi/2-a),Float(stretch)),SIMD4(Float(s.trail*(1-M.smooth(0.85,1,s.fly))),Float(UIScreen.main.scale),0,0))
  }
  static func finger(_ context: inout GraphicsContext,_ l: CoinDropRewardLayout,_ s: CoinDropRewardFrame) {
    guard s.finger > 0 else { return }
    var c = context; c.opacity = 0.85*s.finger
    let x = s.onCoin == 1 ? l.toastCoin.x+l.dx*s.fingerPull : l.w*0.62
    let y = s.onCoin == 1 ? l.toastCoin.y+l.dy*s.fingerPull : l.rowCoin(0).y
    c.translateBy(x:x,y:y); c.scaleBy(x:1-0.12*s.press,y:1-0.12*s.press)
    c.fill(Path(ellipseIn:CGRect(x:-22,y:-22,width:44,height:44)),with:.color(H.color(0x1E2850).opacity(0.18)))
    c.stroke(Path(ellipseIn:CGRect(x:-21.25,y:-21.25,width:42.5,height:42.5)),with:.color(H.color(0x1E2850).opacity(0.35)),lineWidth:1.5)
  }
}
