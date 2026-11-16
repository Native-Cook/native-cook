import SwiftUI

struct WovenSpeakerPlayerLayout {
    var width: Double, height: Double, scale: Double, cx: Double, cy: Double, originY: Double
    init(_ size: CGSize, _ top: Double, _ bottom: Double) {
        width = size.width; height = size.height; scale = min(width/402,1)*(360.0/330)
        cx = width/2
        let pillBottom = height-max(bottom,16)-40*scale
        originY = pillBottom-712*scale
        cy = (max(top,20) + originY + (590-19)*scale)/2
    }
    func y(_ value: Double) -> Double { originY+value*scale }
}

struct WovenSpeakerPlayerFrame: View {
    var look: WovenSpeakerPlayerLook
    var layout: WovenSpeakerPlayerLayout
    var pixels: Double
    var index: Int
    var slide: Double
    var pillPress: Double
    typealias R = WovenSpeakerPlayerResources
    typealias M = WovenSpeakerPlayerMotion

    var body: some View {
        let l = layout
        ZStack(alignment: .topLeading) {
            Rectangle().fill(.white)
                .colorEffect(ShaderLibrary.wovenSpeakerPlayer(.float2(l.cx,l.cy), .float(l.scale), .float(pixels), .floatArray(look.uniforms)))
            Canvas(opaque: false, colorMode: .nonLinear) { c, _ in
                var dome = c; dome.translateBy(x: l.cx,y: l.cy); dome.scaleBy(x: l.scale,y: l.scale)
                display(dome)
                var page = c; page.translateBy(x: l.cx-201*l.scale,y: l.originY); page.scaleBy(x: l.scale,y: l.scale)
                text(page)
            }
        }.frame(width: l.width,height: l.height,alignment: .topLeading)
    }
    private func linear(_ colors: [Color], _ a: CGPoint, _ b: CGPoint, _ stops: [Double]? = nil) -> GraphicsContext.Shading {
        let gradient = stops.map { Gradient(stops: zip(colors,$0).map { Gradient.Stop(color: $0.0,location: $0.1) }) } ?? Gradient(colors: colors)
        return .linearGradient(gradient,startPoint: a,endPoint: b)
    }
    private func radial(_ colors: [Color], _ center: CGPoint, _ r: Double, _ stops: [Double]? = nil) -> GraphicsContext.Shading {
        let gradient = stops.map { Gradient(stops: zip(colors,$0).map { Gradient.Stop(color: $0.0,location: $0.1) }) } ?? Gradient(colors: colors)
        return .radialGradient(gradient,center: center,startRadius: 0,endRadius: r)
    }
    private func display(_ c: GraphicsContext) {
        let k = look, head = -Double.pi/2+k.pos/200*2*Double.pi
        c.fill(R.circle(105.4),with: .color(R.color(0x050506)))
        var glass = c; glass.clip(to: R.circle(102.8))
        glass.fill(R.circle(102.8),with: linear([R.color(0x101020),R.color(0x0C0916)],CGPoint(x: 0,y: -102.8),CGPoint(x: 0,y: 102.8)))
        // Plus is applied after the two lobes have been composited into their opacity group, as in Skia.
        var glow = glass; glow.opacity = k.bloom; glow.blendMode = .plusLighter
        glow.drawLayer { layer in
            for x in [-24.0,24.0] {
                let center = CGPoint(x: x,y: -6)
                layer.fill(R.circle(k.bloomR,x,-6),with: radial([R.color(0xB3181F,176.0/255),R.color(0xB3181F,68.0/255),R.color(0xB3181F,0)],center,k.bloomR,[0,0.55,1]))
            }
        }
        glass.stroke(R.circle(96.5),with: .color(.white.opacity(0.07)),lineWidth: 1)
        var arc = glass; arc.rotate(by: .radians(-.pi/2))
        arc.stroke(R.arc(96.5,0,max(k.pos/200*360,4)),with: .conicGradient(Gradient(stops: [.init(color: R.color(0xFF3B30),location: 0),.init(color: R.color(0xFF7A45),location: 0.58),.init(color: R.color(0xFF7A45),location: 1)]),center: .zero,angle: .zero),style: StrokeStyle(lineWidth: 1.7,lineCap: .round))
        var dot = glass; dot.opacity = k.handle; dot.translateBy(x: 96.5*cos(head),y: 96.5*sin(head)); dot.scaleBy(x: 1+0.6*k.head,y: 1+0.6*k.head)
        dot.drawLayer { layer in
            layer.fill(R.circle(3),with: .color(R.color(0xFF7A45))); layer.fill(R.circle(3),with: .color(.white.opacity(0.28)))
        }
        let bezel = Gradient(stops: zip([0x1B1C1F,0x46474C,0x9A9CA6,0x46474C,0x1B1C1F] as [UInt32],[0.0,0.3,0.625,0.85,1]).map { .init(color: R.color($0.0),location: $0.1) })
        c.stroke(R.circle(104),with: .conicGradient(bezel,center: .zero,angle: .zero),lineWidth: 1.3)
        c.stroke(R.circle(103.2),with: .color(.black.opacity(0.6)),lineWidth: 0.6)
        var reflection = c; reflection.opacity = k.refl*0.85
        reflection.drawLayer { layer in
            layer.addFilter(.blur(radius: 1.2))
            layer.stroke(R.arc(104,head*180 / .pi-15,30),with: .color(R.color(0xFF3B30)),style: StrokeStyle(lineWidth: 1.5,lineCap: .round))
        }
        nowPlaying(glass)
    }
    private func nowPlaying(_ c: GraphicsContext) {
        let k = look
        var clock = c; clock.translateBy(x: 0,y: -80)
        let scale = M.mix(1,17.0/12,min(k.turn,1.08)); clock.scaleBy(x: scale,y: scale); clock.opacity = 0.92
        let w = R.digits[0].width, wc = R.digits[10].width, xm = -(3*w+wc)/2
        let cells = [xm-w,xm,xm+w,xm+w+wc,xm+2*w+wc], widths = [w,w,wc,w,w]
        let seconds = max(0,Int(floor(k.pos+1e-6))), label = String(format: "%d:%02d",seconds/60,seconds%60)
        let chars = Array(label.count < 5 ? " "+label : label)
        for i in 0..<min(5,chars.count) {
            if chars[i] == " " { continue }
            let n = chars[i] == ":" ? 10 : Int(String(chars[i])) ?? 0, run = R.digits[n]
            R.draw(clock,run,cells[i]+(widths[i]-run.width)/2,0.364*12,.color(R.color(0xCFCDD8)))
        }
        var row = c; row.translateBy(x: 0,y: -57)
        var cast = row; cast.translateBy(x: -30,y: 0); cast.opacity = 0.82
        for r in [13*0.46,13*0.28] {
            cast.stroke(R.arc(r,150,240).applying(CGAffineTransform(translationX: 0,y: -13*0.06)),with: .color(R.color(0xE6E3F2)),style: StrokeStyle(lineWidth: 1.2,lineCap: .round))
        }
        var foot = Path(); foot.move(to: CGPoint(x: 0,y: -13*0.04)); foot.addLine(to: CGPoint(x: 13*0.42,y: 6.5)); foot.addLine(to: CGPoint(x: -13*0.42,y: 6.5)); foot.closeSubpath()
        cast.fill(foot,with: .color(R.color(0xE6E3F2)))
        row.fill(R.circle(8),with: .color(R.color(0xF2B5AE)))
        R.draw(row,R.avatar,-R.avatar.width/2,0.364*9,.color(R.color(0x6A2B30)))
        var wave = Path()
        for (i,h) in [0.3,0.62,1,0.55,0.28].enumerated() {
            let x = 30+Double(i-2)*14*0.22; wave.move(to: CGPoint(x: x,y: -h*7)); wave.addLine(to: CGPoint(x: x,y: h*7))
        }
        row.stroke(wave,with: .color(R.color(0xE6E3F2,0.82)),style: StrokeStyle(lineWidth: 1.2,lineCap: .round))
        for i in 0..<3 { cover(c,i) }
        var center = c; center.opacity = 1-0.2*M.clamp(k.turn)
        center.drawLayer { layer in
            layer.fill(Path(roundedRect: CGRect(x: -36,y: -0.3,width: 72,height: 0.6),cornerRadius: 0.3),with: linear([R.color(0xFFB020,0),R.color(0xFFB020),R.color(0xFFB020),R.color(0xFFB020,0)],CGPoint(x: -36,y: 0),CGPoint(x: 36,y: 0),[0,0.18,0.82,1]))
            var g = layer; g.scaleBy(x: k.press,y: k.press)
            let p = R.glyph(k.glyph)
            g.stroke(p,with: .color(R.color(0xE6E3F2,0.9)),style: StrokeStyle(lineWidth: 1.4,lineJoin: .round))
            g.fill(p,with: linear([R.color(0xF4F2FB),R.color(0xB3AFC6)],CGPoint(x: 0,y: -8),CGPoint(x: 0,y: 8)))
        }
        for d in [-1.0,1.0] {
            var disc = c; disc.translateBy(x: d*67,y: 0)
            disc.fill(R.circle(13),with: .color(R.color(0x8C78C8,0.12)))
            disc.stroke(R.circle(12.7),with: .color(R.color(0x8C78C8,0.32)),lineWidth: 0.6)
            disc.translateBy(x: d*0.8,y: 0); disc.fill(R.chevron(d),with: .color(R.color(0xE6E3F2,0.88)))
        }
        for i in 0..<3 {
            let dist = Double(i-index)*140+slide
            var t = c; t.translateBy(x: dist*0.6,y: 0); t.opacity = M.clamp(1-abs(dist)/(140*0.7))
            t.drawLayer { layer in
                R.draw(layer,R.displayTitles[i],-R.displayTitles[i].width/2,54+0.364*15,.color(.white))
                R.draw(layer,R.displayArtists[i],-R.displayArtists[i].width/2,73+0.364*8.5,.color(R.color(0xA9A5B5)))
            }
        }
    }
    private func cover(_ c: GraphicsContext, _ i: Int) {
        let dist = Double(i-index)*140+slide
        var c = c; c.translateBy(x: dist*0.25,y: 0); c.opacity = M.clamp(1-abs(dist)/(140*0.9))
        guard c.opacity > 0 else { return }
        c.drawLayer { layer in
            var clip = layer; clip.clip(to: R.cover)
            clip.fill(Path(roundedRect: CGRect(x: -36,y: -36,width: 72,height: 72),cornerRadius: 20),with: linear([R.color(0xC6303A),R.color(0x5C0E14)],CGPoint(x: -36,y: -36),CGPoint(x: 18,y: 36)))
            var art = clip; art.scaleBy(x: 72.0/68,y: 72.0/68)
            let suns = [CGPoint(x: 14,y: -8),CGPoint(x: -12,y: -12),CGPoint(x: 2,y: 12)], radii = [19.0,16,25]
            let sun = suns[i], r = radii[i], figure = R.figure(i), len = max(hypot(sun.x,sun.y),1)
            art.fill(R.circle(r*1.9,sun.x,sun.y),with: radial([R.color(0xFF8A5C,0.4),R.color(0xFF8A5C,0)],sun,r*1.9))
            art.fill(R.circle(r,sun.x,sun.y),with: linear([R.color(0xFF8A5C),R.color(0xFF7452)],CGPoint(x: sun.x,y: sun.y-r),CGPoint(x: sun.x,y: sun.y+r)))
            art.fill(figure.applying(CGAffineTransform(translationX: sun.x/len*0.9,y: sun.y/len*0.9-0.3)),with: .color(R.color(0xFFB088)))
            art.fill(figure,with: linear([R.color(0x24060A),R.color(0x120204)],CGPoint(x: 0,y: -26),CGPoint(x: 0,y: 34)))
            for n in 0..<34 { art.fill(Path(CGRect(x: -34,y: -33+Double(n)*2,width: 68,height: 0.45)),with: .color(.black.opacity(0.09))) }
            clip.fill(Path(roundedRect: CGRect(x: -36,y: -36,width: 72,height: 72),cornerRadius: 20),with: .color(.black.opacity(0.2*M.clamp(look.turn))))
            layer.stroke(R.cover,with: .color(.white.opacity(0.12)),lineWidth: 0.5)
        }
    }
    private func text(_ c: GraphicsContext) {
        let k = look
        R.draw(c,R.headline,201-R.headline.width/2,590+0.364*38-4*k.title,.color(.white),1-M.smooth(0,0.5,k.title))
        for i in 0..<3 {
            let dist = Double(i-index)*280+slide, fade = M.clamp(1-abs(dist)/224)*M.smooth(0.5,1,k.title)
            var title = c; title.translateBy(x: 201,y: 590); title.scaleBy(x: 1+0.006*min(k.pulse,1),y: 1); title.translateBy(x: -201,y: -590)
            let tint = 0.1*M.clamp(k.wash/0.05)
            let rgb = [1.0,59.0/255,48.0/255].map { M.mix(1,$0,tint)*255 }
            let bottom = Color(.sRGB,red: rgb[0].rounded()/255,green: rgb[1].rounded()/255,blue: rgb[2].rounded()/255,opacity: 1)
            R.draw(title,R.pageTitles[i],201-R.pageTitles[i].width/2+dist,590+0.364*38+4*(1-k.title),linear([.white,bottom],CGPoint(x: 0,y: 576),CGPoint(x: 0,y: 612)),fade)
        }
        for i in 0..<2 {
            let y = i == 0 ? -4*k.line : 4*(1-k.line), alpha = i == 0 ? 1-M.smooth(0,0.5,k.line) : M.smooth(0.5,1,k.line)
            R.draw(c,R.lines[i],201-R.lines[i].width/2,632+0.364*17+y,.color(R.color(0x8E8E93)),alpha)
        }
        var pill = c; pill.translateBy(x: 201,y: 692); pill.scaleBy(x: pillPress,y: pillPress); pill.translateBy(x: -201,y: -692)
        pill.stroke(Path(roundedRect: CGRect(x: 139,y: 672,width: 124,height: 40),cornerRadius: 20),with: .color(.white),lineWidth: 1)
        for i in 0..<2 {
            let y = i == 0 ? -4*k.pill : 4*(1-k.pill), alpha = i == 0 ? 1-M.smooth(0,0.5,k.pill) : M.smooth(0.5,1,k.pill)
            R.draw(pill,R.pills[i],201-R.pills[i].width/2,692+0.364*15+y,.color(.white),alpha)
        }
    }
}
