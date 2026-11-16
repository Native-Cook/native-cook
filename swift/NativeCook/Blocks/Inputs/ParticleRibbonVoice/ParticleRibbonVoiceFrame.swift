import SwiftUI

/// RN's layout maps the centre group around y=414/874; the control docks independently to the safe area.
struct ParticleRibbonVoiceLayout {
    let width: Double, height: Double, top: Double, bottom: Double, scale: Double, y0: Double
    init(width: Double, height: Double, top: Double, bottom: Double, centerY: Double = 476) {
        self.width = width; self.height = height; self.top = top; self.bottom = bottom
        scale = width/402
        let y = height*414/874-414*scale+(centerY-476)*scale
        y0 = y+max(0,top+16-(y+(376-88)*scale))
    }
    func y(_ design: Double) -> Double { y0+design*scale }
}

struct ParticleRibbonVoiceFrame: View {
    let frame: ParticleRibbonVoiceMotion.Frame
    let cap: [Double], ask: [Double], clock: Double, textAlpha: Double
    let layout: ParticleRibbonVoiceLayout
    let typography: ParticleRibbonVoiceTypography
    var textMultiplier: Double = 1
    var control = true
    @Environment(\.displayScale) private var displayScale
    private typealias R = ParticleRibbonVoiceResources

    var body: some View {
        let g = layout
        ZStack(alignment:.topLeading) {
            R.page
            Rectangle().fill(.white)
                .colorEffect(ShaderLibrary.particleRibbonVoice(.floatArray(uniforms)))
                .frame(width:g.width,height:270*g.scale,alignment:.topLeading)
                .offset(y:g.y(330))
            Canvas(opaque:false,colorMode:.nonLinear) { context, _ in
                drawText(context)
                if control { drawControl(context) }
            }
            .frame(width:g.width,height:g.height,alignment:.topLeading)
            .opacity(textAlpha)
        }
        .frame(width:g.width,height:g.height,alignment:.topLeading)
        .allowsHitTesting(false)
    }
    private var uniforms: [Float] {
        let g = layout, f = frame
        // Order is exactly prv-uniforms.ts. Keep the RN iOS P3 conversion, then adapt the host in Metal.
        var out = [g.width,270*g.scale,g.scale,330,f.phase]
        out += f.line; out += f.sheet; out += f.twA; out += f.twB; out += f.think
        out += f.pul; out += f.win; out += f.winB; out += f.bump
        out += [f.opt[0],f.opt[1],1,f.opt[3]]
        out += [f.extra[0],3.0/255,3.0/255,4.0/255]
        out += R.ramp.flatMap { $0 }; out += [0]
        return out.map(Float.init)
    }
    private func drawLine(_ c: GraphicsContext, _ line: ParticleRibbonVoiceTypography.Line,
                          x: Double, top: Double, height: Double, color: Color, alpha: Double = 1) {
        guard alpha>0.00001 else { return }
        var c = c
        // RCTText's explicit lineHeight centres the font metrics in the line fragment.
        let baseline = top+(height-line.ascent-line.descent-line.leading)/2+line.ascent
        c.translateBy(x:x,y:baseline); c.opacity *= alpha
        c.fill(line.path,with:.color(color))
    }
    private func centered(_ c: GraphicsContext, _ line: ParticleRibbonVoiceTypography.Line,
                          top: Double, height: Double, color: Color, alpha: Double = 1) {
        drawLine(c,line,x:(layout.width-line.width)/2,top:top,height:height,color:color,alpha:alpha)
    }
    private func drawText(_ c: GraphicsContext) {
        let g = layout, s = g.scale, mult = min(textMultiplier,1.5)
        let fs = 35*s*mult, lh = 44*s*mult, track = -0.024*35*s
        let greet = typography.rows("Hey Mara,",size:fs,tracking:track,weight:"ExtraLight",width:g.width-32)
        let idle = typography.rows("How can I help\u{00a0}you?",size:fs,tracking:track,weight:"Light",width:g.width-32)
        // The normal-flow idle layer determines the minimum-height row even while invisible, just as in RN.
        let rowH = max(44*s,lh*Double(idle.count)), greetTop = g.y(376)-rowH-lh*Double(greet.count)
        for (i,line) in greet.enumerated() { centered(c,line,top:greetTop+Double(i)*lh,height:lh,color:R.greeting) }
        let bodyTop = g.y(376)-rowH
        for (i,line) in idle.enumerated() {
            centered(c,line,top:bodyTop+Double(i)*lh+(1-cap[0])*6*s,height:lh,color:R.ink,alpha:cap[0])
        }
        let listening = typography.line("I’m listening",size:fs,tracking:track)
        centered(c,listening,top:bodyTop+(1-cap[1])*6*s,height:lh,color:R.ink,alpha:cap[1])
        let dotSize = 6.4*s
        for i in 0..<3 {
            let x = g.width/2-22.4*s+Double(i)*19.2*s
            let y = bodyTop+(44*s-dotSize)/2+1*s
            c.fill(Path(ellipseIn:CGRect(x:x,y:y,width:dotSize,height:dotSize)),with:.color(R.ink.opacity(cap[2+i])))
        }
        let words = R.reply.map { typography.line($0,size:fs,tracking:track) }
        let positions = flow(words.map(\.width),gap:0.24*35*s,width:g.width-32)
        for i in words.indices {
            let p = positions[i], wordX = (16+p.x+words[i].width/2)/s, ramp = R.rampAt(wordX/402)
            let k = 0.35*cap[12+i], ink = [253.0/255,253.0/255,254.0/255]
            let rgb = (0..<3).map { floor(255*(ink[$0]+(ramp[$0]-ink[$0])*k)+0.5)/255 }
            let color = Color(.sRGB,red:rgb[0],green:rgb[1],blue:rgb[2],opacity:1)
            drawLine(c,words[i],x:16+p.x,top:bodyTop+Double(p.row)*lh,height:lh,color:color,alpha:cap[5+i])
        }
        if control {
            let am = min(textMultiplier,1.3), ah = 25*s*am
            let words = R.ask.map { typography.line($0,size:19*s*am,tracking:-0.01*19*s) }
            let positions = flow(words.map(\.width),gap:0.26*19*s,width:g.width-32)
            for i in words.indices {
                let p = positions[i]
                // RN AskWord's translation is 4 screen points, without design scaling.
                drawLine(c,words[i],x:16+p.x,top:g.y(598)+Double(p.row)*ah+(1-ask[i])*4,height:ah,color:R.greeting,alpha:ask[i])
            }
        }
    }
    private struct ParticleRibbonVoiceWordPosition { var x: Double; var row: Int }
    private func flow(_ widths: [Double], gap: Double, width: Double) -> [ParticleRibbonVoiceWordPosition] {
        var rows: [[Int]] = [[]], used = 0.0
        for i in widths.indices {
            let add = (rows[rows.count-1].isEmpty ? 0:gap)+widths[i]
            if used+add>width && !rows[rows.count-1].isEmpty { rows.append([i]); used = widths[i] }
            else { rows[rows.count-1].append(i); used += add }
        }
        var out = Array(repeating:ParticleRibbonVoiceWordPosition(x:0,row:0),count:widths.count)
        for (row, ids) in rows.enumerated() {
            let total = ids.reduce(0.0) { $0+widths[$1] }+Double(max(0,ids.count-1))*gap
            var x = (width-total)/2
            for i in ids { out[i] = ParticleRibbonVoiceWordPosition(x:x,row:row); x += widths[i]+gap }
        }
        return out
    }
    private func drawControl(_ c: GraphicsContext) {
        let g = layout, s = g.scale, d = 60*s, v = ask[7]
        let hintH = 15*1.3*s*min(textMultiplier,1.3)
        let bottom = max(g.bottom,12)+14*s, top = g.height-bottom-hintH-14*s-d
        var body = c
        body.translateBy(x:g.width/2,y:top+d/2)
        body.scaleBy(x:1-0.05*v,y:1-0.05*v)
        let ring = CGRect(x:-d/2,y:-d/2,width:d,height:d), hairline = 2/displayScale
        let haloScale = 1.16+0.05*sin(2*Double.pi*clock/1.1)*v
        var halo = body
        halo.scaleBy(x:haloScale,y:haloScale)
        halo.stroke(Path(ellipseIn:ring.insetBy(dx:hairline/2,dy:hairline/2)),with:.color(R.ink.opacity(0.45*v)),lineWidth:hairline)
        body.stroke(Path(ellipseIn:ring.insetBy(dx:hairline/2,dy:hairline/2)),with:.color(R.ink.opacity(0.22)),lineWidth:hairline)
        body.fill(Path(ellipseIn:ring),with:.color(R.ink.opacity(v)))
        var mic = body
        mic.scaleBy(x:d*0.4/24,y:d*0.4/24); mic.translateBy(x:-12,y:-12)
        // Preserve the two alpha layers instead of interpolating the overlapping strokes.
        mic.stroke(microphone,with:.color(R.ink.opacity(1-v)),style:StrokeStyle(lineWidth:1.4,lineCap:.round,lineJoin:.round))
        mic.stroke(microphone,with:.color(R.page.opacity(v)),style:StrokeStyle(lineWidth:1.4,lineCap:.round,lineJoin:.round))
        let hintSize = 15*s*min(textMultiplier,1.3), hintTop = top+d+14*s
        centered(c,typography.line("Hold to talk",size:hintSize),top:hintTop,height:hintH,color:R.greeting,alpha:1-v)
        centered(c,typography.line("Release to send",size:hintSize),top:hintTop,height:hintH,color:R.greeting,alpha:v)
    }
    private var microphone: Path {
        var p = Path()
        p.move(to:CGPoint(x:12,y:3.2))
        p.addArc(center:CGPoint(x:12,y:6.2),radius:3,startAngle:.degrees(-90),endAngle:.degrees(-180),clockwise:true)
        p.addLine(to:CGPoint(x:9,y:11.8))
        p.addArc(center:CGPoint(x:12,y:11.8),radius:3,startAngle:.degrees(180),endAngle:.degrees(0),clockwise:true)
        p.addLine(to:CGPoint(x:15,y:6.2))
        p.addArc(center:CGPoint(x:12,y:6.2),radius:3,startAngle:.degrees(0),endAngle:.degrees(-90),clockwise:true)
        p.closeSubpath()
        p.move(to:CGPoint(x:5.8,y:11.2))
        p.addArc(center:CGPoint(x:12,y:11.2),radius:6.2,startAngle:.degrees(180),endAngle:.degrees(0),clockwise:true)
        p.move(to:CGPoint(x:12,y:17.4)); p.addLine(to:CGPoint(x:12,y:20.8))
        return p
    }
}
