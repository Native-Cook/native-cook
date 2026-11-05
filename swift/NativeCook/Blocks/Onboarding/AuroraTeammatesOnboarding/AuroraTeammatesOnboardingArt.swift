import SwiftUI
import UIKit

struct AuroraTeammatesOnboardingArt {
    typealias H = AuroraTeammatesOnboardingTheme
    typealias M = AuroraTeammatesOnboardingMotion
    typealias GL = M.GL
    typealias S = AuroraTeammatesOnboardingSheet
    let l:H.Layout
    let sc:[Double]
    var reduced=false
    var press=0.0
    let r=AuroraTeammatesOnboardingResources.shared
    func rect(_ x:Double,_ y:Double,_ w:Double,_ h:Double,_ radius:Double=0)->Path {
        Path(roundedRect:CGRect(x:x,y:y,width:max(0,w),height:max(0,h)),cornerRadius:radius)
    }
    func linear(_ stops:[(UInt32,Double,Double)],_ x0:Double,_ y0:Double,_ x1:Double,_ y1:Double)->GraphicsContext.Shading {
        .linearGradient(Gradient(stops:stops.map { .init(color:H.color($0.0,$0.1),location:$0.2) }),startPoint:CGPoint(x:x0,y:y0),endPoint:CGPoint(x:x1,y:y1))
    }
    func radial(_ stops:[(UInt32,Double,Double)],_ x:Double,_ y:Double,_ radius:Double)->GraphicsContext.Shading {
        .radialGradient(Gradient(stops:stops.map { .init(color:H.color($0.0,$0.1),location:$0.2) }),center:CGPoint(x:x,y:y),startRadius:0,endRadius:radius)
    }
    func drawPanel(_ c:GraphicsContext,_ i:Int) {
        let o=i*13,s=l.S,card=i<7,h=(card ? 43.0:75.0)*s
        let resting=sc[o+6]==(card ? 218.75:116.5)*s/2 && sc[o+7]==h/2
        let shape=resting ? r.panels[card ? 0:1]:rect(-sc[o+6],-sc[o+7],sc[o+6]*2,sc[o+7]*2,(card ? 7:9)*s)
        c.fill(shape,with:card ? linear([(0xBEB0FF,0.28,0),(0xA092FF,0.17,1)],0,h/2,0,-h/2):linear([(0xC8D6FF,0.32,0),(0xAABEFF,0.16,1)],0,h/2,0,-h/2))
        c.stroke(shape,with:.color(.white.opacity(0.2)),lineWidth:0.8)
    }
    func drawFlare(_ context:GraphicsContext) {
        let x=sc[GL.coreX],y=sc[GL.coreY],f=sc[GL.flare],radius=60+70*f
        guard f>0 else { return }
        var c=context;c.opacity=0.45*f;c.blendMode = .plusLighter
        c.fill(Path(ellipseIn:CGRect(x:x-radius,y:y-radius,width:radius*2,height:radius*2)),with:radial([(0xEBF5FF,0.95,0),(0xAABEFF,0.35,0.45),(0x788CFF,0,1)],x,y,130))
    }
    func draw(_ c:GraphicsContext) {
        let top=sc[GL.sheetTop],dip=sc[GL.dip],cx=sc[GL.coreX]
        let page=Int(sc[GL.from]),resting=top==l.sheetTop[Int(sc[GL.from])] && dip==l.depth && cx==l.cx
        let sheet=resting ? r.sheets[page]:S.sheetPath(top,dip,cx,l,true),rim=resting ? r.rims[page]:S.sheetPath(top,dip,cx,l,false)
        let colors:[UInt32]=[0x0E0C12,0x0E0C16,0x0F0D1D,0x0F0E22,0x0F0E28,0x100F2F,0x101037,0x10103E,0x111250,0x12114E]
        let stops=[0.0,380,420,440,460,480,500,520,580,600]
        c.fill(sheet,with:linear(zip(colors,stops).map { ($0,1,$1*l.S/l.H) },0,0,0,l.H))
        c.fill(sheet,with:linear([(0x060516,0.34,0),(0x060516,0,0.32),(0x060516,0,0.68),(0x060516,0.34,1)],0,0,l.W,0))
        var edge=c;edge.opacity=0.35+0.65*sc[GL.flare]
        edge.stroke(rim,with:linear([(0x4E56BE,0.9,0),(0x606AD6,0.95,0.28),(0xCDD7FF,1,0.5),(0x606AD6,0.95,0.72),(0x4E56BE,0.9,1)],cx-l.cx,0,cx-l.cx+l.W,0),lineWidth:1.1)
        guard let t=r.textLayout else { return }
        let gx0=l.cx-150*(l.S/1.4357),gx1=l.cx+150*(l.S/1.4357)
        let gy0=min(l.sheetTop[0],l.sheetTop[1])+l.titleTop-l.titleCap,gy1=gy0+3*l.titleStep+40
        for i in 0..<8 {
            let a=S.tokenState(t,i,sc,reduced)
            if a[2]<0.0001 { continue }
            var d=c;d.translateBy(x:a[0],y:a[1]);d.opacity=a[2]
            let paint=i>=6 ? linear(zip(H.rainbow,H.rainbowStops).map { ($0,1,$1) },0,0,t.width[i],0):linear([(0xFFFFFF,1,0),(0xFFFFFF,1,0.5),(0xA9A9AF,1,1)],gx0-a[0],gy0-a[1],gx1-a[0],gy1-a[1])
            // tgfx ImageFilter sigmas are canvas pixels; Canvas filters use points.
            d.drawLayer { b in
                if a[3]>0 { b.addFilter(.blur(radius:a[3]/UIScreen.main.scale)) }
                r.text(b,"token\(i)",0,0,paint)
            }
            if i>=6 {
                let g=S.glintState(t,i,sc,reduced)
                var glint=c;glint.translateBy(x:a[0],y:a[1]);glint.opacity=0.85*g[1]*a[2];glint.blendMode = .plusLighter
                r.text(glint,"token\(i)",0,0,linear([(0xFFFFFF,0,0),(0xFFFFFF,0.9,0.5),(0xFFFFFF,0,1)],g[0],0,g[0]+0.4*t.width[i],0))
            }
        }
        for p in 0..<3 {
            let a=S.subtitleState(p,sc,l,reduced)
            if a[1]<0.0001 { continue }
            var d=c;d.translateBy(x:0,y:a[0]);d.opacity=a[1]
            d.drawLayer { b in
                if a[2]>0 { b.addFilter(.blur(radius:a[2]/UIScreen.main.scale)) }
                for j in H.subtitles[p].indices { r.text(b,"sub\(p)-\(j)",t.subX[p][j],Double(j)*l.subtitleStep,.color(H.color(0xBEBDC4))) }
            }
        }
        drawDots(c);drawButton(c)
    }
    func drawDots(_ context:GraphicsContext) {
        let w=S.trackWidth(l)
        var c=context;c.translateBy(x:l.cx-w/2,y:sc[GL.dotsY]-l.trackH/2)
        c.fill(r.dotsTrack,with:.color(H.color(0x1F1E3E)))
        let dim=sc[GL.dotDim],pr=M.mix(174.0/255,94.0/255,dim),pg=M.mix(175.0/255,93.0/255,dim),pb=M.mix(204.0/255,132.0/255,dim)
        for i in 0..<4 {
            let d=S.dotState(i,sc,l)
            let color=Color(.sRGB,red:M.mix(58.0/255,pr,d[3]),green:M.mix(57.0/255,pg,d[3]),blue:M.mix(89.0/255,pb,d[3]),opacity:1)
            if d[1]==l.dot && d[2]==l.dot {
                var dot=c;dot.translateBy(x:d[0],y:(l.trackH-d[2])/2);dot.fill(r.dotCircle,with:.color(color))
            } else if d[1]==l.pillW && d[2]==l.pillH {
                var dot=c;dot.translateBy(x:d[0],y:(l.trackH-d[2])/2);dot.fill(r.dotPill,with:.color(color))
            } else { c.fill(rect(d[0],(l.trackH-d[2])/2,d[1],d[2],d[2]/2),with:.color(color)) }
        }
        guard sc[GL.dotFillA]>0 else { return }
        let d=S.dotState(Int(sc[GL.from]),sc,l)
        var fill=c;fill.opacity=sc[GL.dotFillA]
        fill.fill(rect(d[0],(l.trackH-l.pillH)/2,l.pillH+(l.pillW-l.pillH)*sc[GL.dotFill],l.pillH,l.pillH/2),with:.color(H.color(0xAEAFCC)))
    }
    func drawButton(_ context:GraphicsContext) {
        let w=l.buttonW,h=l.buttonH,y=l.H-sc[GL.buttonBottom]-h/2,glow=min(1,sc[GL.finGlow])
        if glow>0 {
            var b=context;b.translateBy(x:l.cx,y:y);b.scaleBy(x:(0.85+0.35*glow)*(w/h)*0.62,y:0.85+0.35*glow);b.opacity=glow;b.blendMode = .plusLighter
            b.fill(r.bloomCircle,with:radial([(0xE2EAFF,1,0),(0xA0B0FF,0.5,0.45),(0x786EFF,0,1)],0,0,h*1.4))
        }
        var c=context;c.translateBy(x:l.cx,y:y);let scale=1-0.03*press+sc[GL.finPop];c.scaleBy(x:scale,y:scale)
        let amp=reduced ? 0:(abs(press)*1.1*l.S*100).rounded()/100
        let pill=amp==0 ? r.pill:S.pillPath(w,h,amp,sc[GL.phase]*7)
        c.fill(pill,with:linear([(0xFFFFFF,1,0),(0xFFFFFF,1,0.58),(0xBDBDBF,1,1)],-w*0.15,h/2,w/2,-h*0.4))
        var sheen=c;sheen.clip(to:r.buttonClip)
        let fs=sc[GL.finSheen],sx = -w*0.7+w*1.4*(fs>0 ? fs:sc[GL.u])
        sheen.translateBy(x:sx,y:0);sheen.rotate(by:.degrees(18));sheen.opacity=fs>0 ? 0.9*sin(.pi*fs):0.8*sc[GL.flare]
        sheen.fill(r.sheenRect,with:linear([(0xFFFFFF,0,0),(0xFFFFFF,0.95,0.5),(0xFFFFFF,0,1)],-h*0.45,0,h*0.45,0))
        let ck=sc[GL.finCheck],ink=GraphicsContext.Shading.color(H.color(0x0E0E18))
        var next=c;next.translateBy(x:0,y:-5*ck);next.opacity=1-ck;r.text(next,"next",0,l.buttonLabel*0.36,ink,center:true)
        var done=c;done.translateBy(x:0,y:5*(1-ck));done.opacity=ck
        let check=l.buttonLabel*0.95,doneW=r.labels["done"]?.width ?? 0,doneX = -(doneW+check+l.buttonLabel*0.45)/2
        done.stroke(r.checkmark,with:ink,style:StrokeStyle(lineWidth:l.buttonLabel*0.16,lineCap:.round,lineJoin:.round))
        r.text(done,"done",doneX+check+l.buttonLabel*0.45,l.buttonLabel*0.36,ink)
    }
}
