import SwiftUI

struct FilamentBulbDimmerArt {
    typealias H=FilamentBulbDimmerTheme
    let layout:H.Layout
    let frame:FilamentBulbDimmerMotion.Frame
    var drawBack=true
    let resources=FilamentBulbDimmerResources.shared
    func circle(_ c:GraphicsContext,_ p:CGPoint,_ r:Double,_ color:Color) { c.fill(Path(ellipseIn:CGRect(x:p.x-r,y:p.y-r,width:r*2,height:r*2)),with:.color(color)) }
    func icon(_ c:GraphicsContext,_ x:Double,_ y:Double,_ on:Bool) {
        var d=c; d.translateBy(x:x-13,y:y-17); d.scaleBy(x:26/20,y:31/24)
        d.fill(FilamentBulbDimmerResources.bulb,with:.color(on ? H.color(0xFFF8D6,0.92):.white.opacity(0.32)))
        d.stroke(FilamentBulbDimmerResources.bulb,with:.color(.white.opacity(on ? 0.95:0.4)),style:StrokeStyle(lineWidth:1.2,lineJoin:.round))
        if on { d.stroke(FilamentBulbDimmerResources.rays,with:.color(.white.opacity(0.9)),style:StrokeStyle(lineWidth:1.2,lineCap:.round)) }
    }
    func draw(_ c:GraphicsContext) {
        let l=layout,f=frame,r=resources,sw=f.colour,glow=FilamentBulbDimmerMotion.lum(f.heat)/FilamentBulbDimmerMotion.lum(0.78)
        let band=r.band(l)
        c.fill(r.shapes["tab"]!,with:.color(H.color(0x282832)))
        var tab=c; tab.translateBy(x:32*l.sx,y:l.tab.midY-0.5); tab.rotate(by:.degrees(-90))
        r.text(tab,"tab",0,-13.5,H.color(0x3D3C46),center:true)
        for (rect,label) in [(l.chipL,"left"),(l.chipR,"right")] {
            c.fill(r.shapes[label]!,with:.color(H.color(0x2B2A32)))
            r.text(c,label,rect.midX,rect.midY-11,H.color(0xECE8EE),center:true)
        }
        let bw=l.rOut-l.rIn
        let shading=GraphicsContext.Shading.linearGradient(Gradient(colors:[H.accent(sw,0),H.accent(sw,1),H.accent(sw,2)]),startPoint:CGPoint(x:l.cx-l.rOut,y:0),endPoint:CGPoint(x:l.cx+l.rOut,y:0))
        var blur=c; blur.opacity=0.42*min(1.4,glow)
        // Skia BlurMask's blur argument is sigma; outer excludes the original band.
        blur.drawLayer { layer in
            layer.drawLayer { soft in
                soft.addFilter(.blur(radius:12));soft.stroke(band,with:shading,style:StrokeStyle(lineWidth:bw,lineCap:.round))
            }
            layer.blendMode = .destinationOut
            layer.stroke(band,with:.color(.white),style:StrokeStyle(lineWidth:bw,lineCap:.round))
        }
        var b=c; b.opacity=0.42+0.58*min(1,glow); b.stroke(band,with:shading,style:StrokeStyle(lineWidth:bw,lineCap:.round))
        let knob=l.arcPoint(f.level),a=atan2(knob.y-l.cy,knob.x-l.cx),dialR=46*l.sx
        var needle=Path(); needle.move(to:CGPoint(x:l.cx+cos(a)*(l.rIn-6),y:l.cy+sin(a)*(l.rIn-6))); needle.addLine(to:CGPoint(x:l.cx+cos(a)*(dialR+6),y:l.cy+sin(a)*(dialR+6)))
        c.stroke(needle,with:.color(.white.opacity(0.35)),lineWidth:1.2)
        let kr=17+3*f.knobPress
        var kb=c; kb.addFilter(.blur(radius:4)); circle(kb,knob,kr,.white); circle(c,knob,kr,.white)
        circle(c,knob,7.5-1.5*f.knobPress,H.accent(sw,4))
        let dy=l.cy-12*l.k
        c.fill(r.shapes["dial"]!,with:.color(.white))
        r.text(c,"intensity",l.cx,dy-21,H.color(0xA3A1A6),center:true)
        percent(c,l.cx,dy-7,f.shownPct)
        let rm=(l.rOut+l.rIn)/2
        icon(c,l.cx-rm,l.cy+l.leg-6,false); icon(c,l.cx+rm,l.cy+l.leg-6,true)
        let hx=H.dotX(l.sx,f.trail),hillH=l.h-l.dotsY+42,y=l.dotsY-42
        var hill=Path(); hill.move(to:CGPoint(x:hx-70,y:y+hillH)); hill.addCurve(to:CGPoint(x:hx,y:y),control1:CGPoint(x:hx-30,y:y+hillH),control2:CGPoint(x:hx-26,y:y)); hill.addCurve(to:CGPoint(x:hx+70,y:y+hillH),control1:CGPoint(x:hx+26,y:y),control2:CGPoint(x:hx+30,y:y+hillH)); hill.closeSubpath()
        c.fill(hill,with:.color(H.color(0x36363E)))
        var line=Path(); line.move(to:CGPoint(x:hx,y:y+56)); line.addLine(to:CGPoint(x:hx,y:l.h)); c.stroke(line,with:.color(.white.opacity(0.12)),lineWidth:1)
        let lead=H.dotX(l.sx,f.lead),trail=H.dotX(l.sx,f.trail),ring=CGRect(x:min(lead,trail)-11.5,y:l.dotsY-11.5,width:abs(lead-trail)+23,height:23)
        c.stroke(Path(roundedRect:ring,cornerRadius:11.5),with:.color(H.accent(sw,5)),lineWidth:1.2-0.35*min(1,abs(f.lead-f.trail)))
        for i in 0..<5 { circle(c,CGPoint(x:H.xs[i]*l.sx,y:l.dotsY),6,H.color(H.dots[i])) }
        if drawBack {
            var back=c; back.translateBy(x:43*l.sx-6,y:l.y(65)-10)
            back.stroke(FilamentBulbDimmerResources.back,with:.color(H.color(0xE9E5EB)),style:StrokeStyle(lineWidth:2,lineCap:.round,lineJoin:.round))
        }
        r.text(c,"title",l.w/2,l.y(65)-11,H.color(0xE9E5EB),center:true)
        c.fill(r.shapes["card"]!,with:.color(H.color(0x3D383F)))
        r.text(c,"name",54*l.sx,l.y(160)-18)
        lampSwitch(c,glow)
        let clock=CGRect(x:168*l.sx,y:l.y(198),width:34,height:34)
        circle(c,CGPoint(x:clock.midX,y:clock.midY),17,H.color(0x4A444C))
        var cc=c; cc.translateBy(x:clock.midX-8,y:clock.midY-8); cc.stroke(FilamentBulbDimmerResources.clock,with:.color(H.color(0xD8D3DA)),style:StrokeStyle(lineWidth:1.4,lineCap:.round))
        r.text(c,"schedule",211*l.sx,l.y(198)+8,H.color(0xECE8EE))
    }
    func percent(_ c:GraphicsContext,_ x:Double,_ y:Double,_ value:Double) {
        let widths=[100.0,10,1].map { p in p==1 ? 15.4:15.4*max(0,min(1,value-(p-1))) }
        var left=x-(widths.reduce(0,+)+22)/2
        for (index,place) in [100.0,10,1].enumerated() {
            let v=max(0,value),pos=place==1 ? v.truncatingRemainder(dividingBy:10):floor(v/place).truncatingRemainder(dividingBy:10)+max(0,v.truncatingRemainder(dividingBy:place)-(place-1))
            var d=c; d.clip(to:Path(CGRect(x:left,y:y,width:widths[index],height:30))); d.opacity=widths[index]/15.4
            for n in max(0,Int(floor(pos)))...min(10,max(0,Int(floor(pos)))+1) { resources.text(d,"\(n%10)",left+widths[index]/2,y+(Double(n)-pos)*30,H.color(0x141217),center:true) }
            left+=widths[index]
        }
        resources.text(c,"%",left+11,y,H.color(0x141217),center:true)
    }
    func lampSwitch(_ c:GraphicsContext,_ glow:Double) {
        let l=layout,f=frame,t=f.tog,h=l.toggle.height,w=l.toggle.width,kd=h-6,stretch=4*t*(1-t)*12
        let accent=H.accent(f.colour,3)
        // Reanimated interpolateColor uses gamma 2.2; the renderer supplies the same two endpoints.
        let color=H.mix(H.color(0x4A444C),accent,t)
        c.fill(Path(roundedRect:l.toggle,cornerRadius:h/2),with:.color(color))
        resources.text(c,"On",l.toggle.minX+16+(1-t)*(-6),l.toggle.midY-9.5,alpha:max(0,min(1,t)))
        resources.text(c,"Off",l.toggle.maxX-14-resources.width("Off")+t*6,l.toggle.midY-9.5,alpha:max(0,min(1,1-t)))
        let rect=CGRect(x:l.toggle.minX+3+t*(w-kd-6)-stretch/2,y:l.toggle.minY+3,width:kd+stretch,height:kd)
        c.fill(Path(roundedRect:rect,cornerRadius:kd/2),with:.color(.white))
        var icon=c; icon.translateBy(x:rect.midX-7,y:rect.midY-8); 
        let ic=H.mix(H.color(0x8B858D),accent,min(1,glow*1.3))
        icon.fill(FilamentBulbDimmerResources.switchBulb,with:.color(ic)); icon.stroke(FilamentBulbDimmerResources.switchBulb,with:.color(ic),style:StrokeStyle(lineWidth:1.2,lineJoin:.round))
    }
}
