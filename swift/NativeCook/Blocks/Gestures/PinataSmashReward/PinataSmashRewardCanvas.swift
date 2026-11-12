import SwiftUI

/// The RN draw order, including the body-local SrcATop glow layer and top-anchored header band.
struct PinataSmashRewardCanvas: View {
    let frame: PinataSmashRewardMotion.Frame
    let width, height, scale, dy, top: Double
    private typealias R = PinataSmashRewardResources
    private typealias M = PinataSmashRewardMotion

    var body: some View {
        let mesh = PinataSmashRewardMesh.uniforms(PinataSmashRewardMesh.vertices(frame, tail: false))
        let tail = PinataSmashRewardMesh.uniforms(PinataSmashRewardMesh.vertices(frame, tail: true))
        Canvas { context, _ in draw(context) } symbols: {
            PinataSmashRewardMeshImage(image: R.tail, uniforms: tail).tag(0)
            PinataSmashRewardMeshImage(image: R.body, uniforms: mesh).tag(1)
            PinataSmashRewardMeshImage(image: R.burst, uniforms: mesh).tag(2)
            PinataSmashRewardMeshImage(image: R.crack1, uniforms: mesh).tag(3)
            PinataSmashRewardMeshImage(image: R.crack2, uniforms: mesh).tag(4)
        }
        .frame(width: width, height: height, alignment: .topLeading)
    }

    private func circle(_ c: GraphicsContext, _ x: Double, _ y: Double, _ r: Double, _ color: Color) {
        c.fill(Path(ellipseIn: CGRect(x: x-r, y: y-r, width: 2*r, height: 2*r)), with: .color(color))
    }
    private func rounded(_ c: GraphicsContext, _ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double, _ color: Color) {
        c.fill(Path(roundedRect: CGRect(x: x, y: y, width: w, height: h), cornerRadius: r), with: .color(color))
    }
    private func solidBlur(_ context: GraphicsContext, _ path: Path, _ sigma: Double, _ color: Color) {
        // Skia BlurStyle.Solid (the source's numeric style 1): merge the opaque original mask
        // over its blurred mask, then apply the paint alpha once to the resulting coverage.
        var c = context
        c.clipToLayer { mask in
            mask.drawLayer { blurred in
                blurred.addFilter(.blur(radius: sigma))
                blurred.fill(path, with: .color(.white))
            }
            mask.fill(path, with: .color(.white))
        }
        c.fill(Path(path.boundingRect.insetBy(dx: -sigma*4, dy: -sigma*4)), with: .color(color))
    }
    private func line(_ c: GraphicsContext, _ pts: [CGPoint], _ color: Color, _ w: Double, _ round: Bool = false, _ join: CGLineJoin = .miter) {
        var p = Path(); p.addLines(pts)
        c.stroke(p, with: .color(color), style: StrokeStyle(lineWidth: w, lineCap: round ? .round : .butt, lineJoin: join))
    }
    private func glow(_ context: GraphicsContext, _ x: Double, _ y: Double, _ r: Double, _ color: Color, _ alpha: Double) {
        guard alpha > 0.003, r > 0.5 else { return }
        let pv = R.pivots[10], s = r / pv.cx
        var c = context
        c.opacity *= min(1, alpha)
        var image = c.resolve(Image(decorative: R.sprites[10], scale: 1))
        image.shading = .color(color)
        c.draw(image, in: CGRect(x: x-s*pv.cx, y: y-s*pv.cy, width: s*pv.w, height: s*pv.h))
    }
    private func token(_ context: GraphicsContext, _ x: Double, _ y: Double, _ s: Double, _ a: Double) {
        let r = 17*s
        guard r >= 0.5, a > 0.01 else { return }
        var c = context; c.opacity *= a
        let rect = CGRect(x: x-r, y: y-r, width: 2*r, height: 2*r)
        c.fill(Path(ellipseIn: rect), with: .linearGradient(Gradient(stops: [
            .init(color: R.color(0xB57BE0), location: 0), .init(color: R.color(0x8B41BE), location: 0.45),
            .init(color: R.color(0x6E2C9C), location: 1)]),
            startPoint: CGPoint(x: x-r*0.5, y: y-r), endPoint: CGPoint(x: x+r*0.4, y: y+r)))
        circle(c, x, y, r*0.84, R.color(0x8B41BE))
        c.fill(Path(ellipseIn: CGRect(x: x-r*0.62, y: y-r*0.8, width: r*0.9, height: r*0.5)), with: .color(.white.opacity(0.18)))
        for i in 0..<2 {
            let cy = y-r*0.14+Double(i)*r*0.34, w = r*0.42, h = r*0.26
            line(c, [.init(x: x-w, y: cy+h*0.5), .init(x: x, y: cy-h*0.5), .init(x: x+w, y: cy+h*0.5)], .white.opacity(0.9), 2.2*s, true, .round)
        }
    }
    private func pill(_ context: GraphicsContext) {
        let f = frame, w = f.pillW, h = 45.5+(43.6-45.5)*f.wB
        let x = 201.3-w/2+f.shake, y = 483.7+(45.5-h)/2
        let sum = max(1e-4, f.wL+f.wB+f.wD), b = f.wB/sum, d = f.wD/sum
        func channel(_ v: Double) -> Double { (20/255.0*(1-b))*(1-d)+v/255*d }
        rounded(context, x, y, w, h, 11, Color(.sRGB, red: channel(240), green: channel(239), blue: channel(235)))
        var c = context
        c.clip(to: Path(roundedRect: CGRect(x: x,y: y,width: w,height: h), cornerRadius: 11))
        let cx = 201.3+f.shake, base = 483.7+45.5/2+15.5*0.35
        let la = M.clamp01((f.wL-0.55)/0.45)
        if la > 0.01 {
            let tx = cx-(R.label.width+8+13)/2
            R.text(c, R.label, tx, base, .white.opacity(la))
            let bx = tx+R.label.width+8, by = 483.7+45.5/2-17/2
            let points: [[Double]] = [[8.2,0],[0.6,10],[6,10],[4.6,17],[12.4,6.6],[7.1,6.6]]
            var p = Path(); p.addLines(points.map { .init(x: bx+$0[0],y: by+$0[1]) }); p.closeSubpath()
            c.stroke(p, with: .color(.white.opacity(la)), style: StrokeStyle(lineWidth: 1.6,lineJoin: .round))
        }
        let ba = M.clamp01((f.wB-0.4)/0.6)
        if ba > 0.01 {
            let tx = cx-50, ty = 483.7+45.5/2-6.7/2, fw = min(100,max(6,100*f.fill))
            rounded(c, tx,ty,100,6.7,3.35,R.color(0xD6D6D4,ba))
            if f.fillGlow > 0.01 {
                solidBlur(c,Path(roundedRect:CGRect(x:tx-1,y:ty-1,width:fw+2,height:8.7),cornerRadius:4.35),2,R.color(0xB57CFF,0.55*f.fillGlow*ba))
            }
            rounded(c,tx,ty,fw,6.7,3.35,R.color(0x8745DD,ba))
        }
        let da = min(M.clamp01((f.wD-0.5)/0.5),f.doneText)
        if da > 0.01 { R.text(c,R.done,cx-R.done.width/2,base,R.color(0xA3A3A0,da)) }
        if f.sparks >= 0 && f.sparks < 0.5 {
            let u = f.sparks/0.5, ex = 261.3, ey = 483.7+45.5/2
            for i in 0..<6 {
                let a = (-70+Double(i)*28)*Double.pi/180, r0 = 6+34*u, r1 = r0+9*(1-u)
                line(context,[.init(x: ex+cos(a)*r0,y: ey+sin(a)*r0),.init(x: ex+cos(a)*r1,y: ey+sin(a)*r1)],R.color(0x8745DD,1-u),2,true)
            }
        }
    }
    private func header(_ context: GraphicsContext) {
        R.text(context,R.title,24,78+top,R.color(0x141414))
        var c = context
        let cx = R.chipX0+R.chipWidth/2, cy = 76+top, b = 1+0.08*frame.chipBump
        c.translateBy(x: cx,y: cy); c.scaleBy(x: b,y: b); c.translateBy(x: -cx,y: -cy)
        rounded(c,R.chipX0,58+top,R.chipWidth,36,18,R.color(0xF3EEFD))
        token(c,R.chipX0+19,76+top,11/17.0,1)
        var x = R.chipX0+8+22+7
        let n = Int(floor(frame.count+0.5))
        for char in String(n) {
            if let digit = char.wholeNumberValue {
                R.text(c,R.digits[digit],x,76+top+15*0.36,R.color(0x6B2FB3)); x += R.digits[digit].width
            }
        }
    }
    private func confetti(_ c: GraphicsContext) {
        let colors: [UInt32] = [0xCE38FA,0x59DA8F,0x72A5F1,0xB257EC,0xEFB238,0xE94B91]
        for conf in frame.conf where conf.a > 0.01 {
            var cc = c; cc.translateBy(x: conf.x,y: conf.y); cc.rotate(by: .radians(conf.rot))
            let hw = conf.w/2, hh = conf.h/2
            var p = Path()
            let pts: [CGPoint] = conf.diamond != 0 ? [.init(x:0,y:-hh),.init(x:hw,y:0),.init(x:0,y:hh),.init(x:-hw,y:0)] : [.init(x:-hw,y:-hh),.init(x:hw,y:-hh),.init(x:hw,y:hh),.init(x:-hw,y:hh)]
            p.addLines(pts); p.closeSubpath(); cc.fill(p,with: .color(R.color(colors[conf.c],conf.a)))
        }
    }
    private func streamers(_ c: GraphicsContext) {
        guard frame.streamers >= 0 else { return }
        for i in 0..<10 {
            let n = Double(i), tau = frame.streamers-M.hash(n+40)*0.5
            if tau < 0 { continue }
            let v = 180+80*M.hash(n+41), x0 = 24+354*(n+M.hash(n+42)*0.8)/10, y0 = -60+v*tau
            if y0 > 900 { continue }
            let sway = 20*sin(tau*2.4+n)
            let pts = (0...12).map { s -> CGPoint in
                let q = Double(s)/12
                return .init(x:x0+sway+7*sin(q*Double.pi*3+tau*9+n),y:y0+q*46)
            }
            line(c,pts,R.color(i%2 == 0 ? 0x59DA8F : 0xCE38FA,frame.fade*(1-M.seg(y0,780,860))),3,true)
        }
    }
    private func loot(_ c: GraphicsContext) {
        for piece in frame.loot where piece.a > 0.01 {
            let pv = R.pivots[piece.k], s = M.kindW[piece.k]/pv.w*piece.s*max(0.25,abs(piece.sx))
            var cc = c; cc.opacity *= piece.a
            cc.translateBy(x:piece.x,y:piece.y); cc.rotate(by:.radians(piece.rot)); cc.scaleBy(x:s,y:s)
            cc.draw(Image(decorative:R.sprites[piece.k],scale:1),in:CGRect(x:-pv.cx,y:-pv.cy,width:pv.w,height:pv.h))
        }
    }
    private func draw(_ context: GraphicsContext) {
        let f = frame
        context.fill(Path(CGRect(x:0,y:0,width:width,height:height)),with:.color(.white))
        var view = context; view.scaleBy(x:scale,y:scale); view.translateBy(x:0,y:dy)
        var stage = view
        stage.translateBy(x:f.stageX,y:f.stageY); stage.scaleBy(x:f.stage,y:f.stage); stage.translateBy(x:-f.stageX,y:-f.stageY)
        let sw = 150*(1-0.4*f.shadowLift), sh = 18*(1-0.4*f.shadowLift)
        solidBlur(stage,Path(ellipseIn:CGRect(x:f.ax-10-sw/2,y:838-sh/2,width:sw,height:sh)),6,.black.opacity(0.05*(1-0.5*f.shadowLift)))
        let x0 = R.ox+R.layout.ribbon_attach_point.x*R.k-8
        var ribbon = Path(); ribbon.move(to:.init(x:x0,y:-30))
        ribbon.addQuadCurve(to:.init(x:f.ax,y:f.ay),control:.init(x:(x0+f.ax)/2-f.bow*2,y:(-30+f.ay)/2))
        for (color,w,a) in [(UInt32(0xD9C6F2),16.2,1.0),(0xE9D7FF,14.6,1.0),(0xF3E9FF,5.0,0.75)] {
            stage.stroke(ribbon,with:.color(R.color(color,a)),style:StrokeStyle(lineWidth:w,lineCap:.butt))
        }
        stage.drawLayer { c in
            func mesh(_ id: Int, _ alpha: Double) {
                if alpha > 0.002, let symbol = c.resolveSymbol(id:id) { var cc = c; cc.opacity *= alpha; cc.draw(symbol,at:.zero,anchor:.topLeading) }
            }
            mesh(0,1)
            if f.burst < 0.999 { mesh(1,1) }
            if f.burst > 0.001 { mesh(2,f.burst) }
            if f.crack1 > 0.002 && f.burst < 0.999 { mesh(3,f.crack1*(1-f.burst)) }
            if f.crack2 > 0.002 && f.burst < 0.999 { mesh(4,f.crack2*(1-f.burst)) }
            var lit = c; lit.blendMode = .sourceAtop
            for (i,b) in R.layout.bulbs.enumerated() {
                let p = PinataSmashRewardMesh.place(f,b.center.x,b.center.y), lv = M.bulbLevel(i,f)
                glow(lit,p.x,p.y,20+10*max(0,lv-1),R.color(b.color),0.34*min(1.8,lv))
            }
            if f.bloom > 0.002 {
                let p = PinataSmashRewardMesh.place(f,(f.bloomX-R.ox)/R.k,(f.bloomY-R.oy)/R.k)
                glow(lit,p.x,p.y,90,.white,f.bloom*1.1)
            }
        }
        var halos = stage; halos.blendMode = .screen
        for (i,b) in R.layout.bulbs.enumerated() {
            let p = PinataSmashRewardMesh.place(f,b.center.x,b.center.y), lv = M.bulbLevel(i,f), r = b.radius*R.k
            glow(halos,p.x,p.y,r*(2.4+1.4*M.clamp01(lv-1)),R.color(b.color),0.45+0.5*M.clamp01(lv-1))
            glow(halos,p.x,p.y,r*1.1,.white,0.3+0.6*M.clamp01(lv-0.9))
        }
        if f.hole > 0.002 {
            let p = PinataSmashRewardMesh.place(f,R.layout.flank_center.x,R.layout.flank_center.y)
            glow(halos,p.x,p.y,52,Color(.sRGB,red:1,green:0.83,blue:0.42),f.hole*1.3)
        }
        if f.rippleA > 0.002 {
            let r = f.rippleR
            let gradient = Gradient(stops:[.init(color:R.color(0xDDF5F5,f.rippleA*0.55),location:0),.init(color:R.color(0xDDF5F5,f.rippleA*0.8),location:max(0,1-24/r)),.init(color:R.color(0xDDF5F5,0),location:1)])
            stage.fill(Path(ellipseIn:CGRect(x:f.rippleX-r,y:f.rippleY-r,width:2*r,height:2*r)),with:.radialGradient(gradient,center:.init(x:f.rippleX,y:f.rippleY),startRadius:0,endRadius:r))
        }
        if f.ringA > 0.002 { stage.stroke(Path(ellipseIn:CGRect(x:f.ringX-f.ringR,y:f.ringY-f.ringR,width:2*f.ringR,height:2*f.ringR)),with:.color(R.color(0xB57CFF,f.ringA*0.6)),lineWidth:3) }
        loot(stage); confetti(stage); streamers(stage)
        pill(view)
        var band = context; band.scaleBy(x:scale,y:scale)
        let bottom = 106+top
        band.fill(Path(CGRect(x:0,y:-10,width:402,height:bottom+10)),with:.color(.white))
        band.fill(Path(CGRect(x:0,y:bottom,width:402,height:24)),with:.linearGradient(Gradient(colors:[.white,.white.opacity(0)]),startPoint:.init(x:0,y:bottom),endPoint:.init(x:0,y:bottom+24)))
        for tk in f.tokens {
            for q in tk.tx.indices.reversed() { token(view,tk.tx[q],tk.ty[q],tk.s*(0.9-Double(q)*0.08),0.11-Double(q)*0.025) }
            token(view,tk.x,tk.y,tk.s,tk.a)
        }
        header(band)
        if f.flash > 0.002 { context.fill(Path(CGRect(x:0,y:0,width:width,height:height)),with:.color(.white.opacity(f.flash))) }
    }
}
