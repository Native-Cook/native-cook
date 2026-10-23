import SwiftUI
import UIKit

/// Layer order, masks, tube passes and mirror follow ChartCanvas.tsx.
struct SpikeToStairChartRenderer {
    typealias Curves = SpikeToStairChartCurves
    typealias Film = SpikeToStairChartFilm
    typealias Assets = SpikeToStairChartAssets
    var f: Film.Frame
    var still: Bool
    var markerU: Double
    var markerAlpha: Double
    var thumb: Double
    var pressed: Int
    var pressAmount: Double
    var intro: Double
    var elapsed: Double
    var columnTimes: [Double]

    private var color: Color { rgb(f.r,f.g,f.b) }
    private var core: Color { rgb(f.coreR,f.coreG,f.coreB) }
    private func rgb(_ r: Double, _ g: Double, _ b: Double, _ alpha: Double = 1) -> Color {
        Color(.sRGB, red: r/255, green: g/255, blue: b/255, opacity: Curves.clamp(alpha))
    }
    private func rect(_ x: Double, _ y: Double, _ w: Double, _ h: Double) -> Path {
        Path(CGRect(x: x,y: y,width: max(0,w),height: max(0,h)))
    }
    private func circle(_ x: Double, _ y: Double, _ r: Double) -> Path {
        Path(ellipseIn: CGRect(x: x-r,y: y-r,width: 2*r,height: 2*r))
    }
    private func rounded(_ x: Double, _ y: Double, _ w: Double, _ h: Double, _ r: Double) -> Path {
        Path(roundedRect: CGRect(x: x,y: y,width: w,height: h),cornerRadius: r)
    }
    // Skia interpolates unpremultiplied channels. Explicit stops preserve its falloff to black.
    private func ramp(_ colors: [Color], positions: [Double]? = nil) -> Gradient {
        let positions = positions ?? colors.indices.map { Double($0)/Double(colors.count-1) }
        let channels = colors.map { color -> [Double] in
            var r: CGFloat = 0
            var g: CGFloat = 0
            var b: CGFloat = 0
            var a: CGFloat = 0
            UIColor(color).getRed(&r,green: &g,blue: &b,alpha: &a)
            return [r,g,b,a]
        }
        var stops: [Gradient.Stop] = []
        for i in 0..<colors.count-1 {
            for j in 0..<16 {
                let p = Double(j)/16
                let values = (0..<4).map { Curves.mix(channels[i][$0],channels[i+1][$0],p) }
                let color = Color(.sRGB,red: values[0],green: values[1],blue: values[2],opacity: values[3])
                stops.append(.init(color: color,location: Curves.mix(positions[i],positions[i+1],p)))
            }
        }
        stops.append(.init(color: colors.last!,location: positions.last!))
        return Gradient(stops: stops)
    }
    private func gradient(_ colors: [Color], _ start: CGPoint, _ end: CGPoint, stops: [Double]? = nil) -> GraphicsContext.Shading {
        .linearGradient(ramp(colors,positions: stops),startPoint: start,endPoint: end)
    }
    private func radial(_ c: GraphicsContext, x: Double, y: Double, radius: Double, colors: [Color], stops: [Double]? = nil) {
        c.fill(circle(x,y,radius),with: .radialGradient(ramp(colors,positions: stops),center: CGPoint(x: x,y: y),startRadius: 0,endRadius: radius))
    }
    private func stroke(_ context: GraphicsContext, _ path: Path, _ color: Color, _ width: Double, blur: Double = 0, alpha: Double = 1) {
        guard alpha > 0 else { return }
        var c = context
        c.opacity *= min(1,alpha)
        if blur > 0 { c.addFilter(.blur(radius: blur)) }
        c.stroke(path,with: .color(color),style: StrokeStyle(lineWidth: width,lineCap: .round,lineJoin: .round))
    }

    func draw(_ context: GraphicsContext) {
        var c = context
        c.translateBy(x: -8*f.jolt,y: 6*f.jolt+f.dip)
        c.translateBy(x: 201,y: 400)
        c.rotate(by: .radians(-.pi/180*f.jolt))
        c.scaleBy(x: 1+0.025*f.jolt,y: 1+0.025*f.jolt)
        c.translateBy(x: -201,y: -400)
        radial(c,x: 201,y: 250,radius: 260,colors: [.white.opacity(18.0/255),.clear])
        radial(c,x: 201,y: 400,radius: 230,colors: [color.opacity(0.1+0.06*f.axisLight),.clear])
        var pool = c
        pool.opacity *= min(1,f.pool)
        pool.translateBy(x: 201,y: 522)
        pool.scaleBy(x: 1,y: 26.0/190)
        radial(pool,x: 0,y: 0,radius: 190,colors: [color.opacity(0.16),.clear])
        floor(c)
        for i in [0,2,4,5] { shard(c,i) }
        scene(c,mirror: false)
        stroke(c,Curves.curves[1].path.trimmedPath(from: f.packetStart,to: f.packetEnd),.white,2,alpha: f.packet*0.6)
        for i in [1,3] { shard(c,i) }
        label(c,0)
        label(c,1)
        marker(c,mirror: false)
        switchArt(context)
    }

    private func floor(_ context: GraphicsContext) {
        var c = context
        c.clip(to: rect(24,522,354,258))
        var mirror = c
        mirror.translateBy(x: 0,y: 1044)
        mirror.scaleBy(x: 1,y: -1)
        scene(mirror,mirror: true)
        marker(mirror,mirror: true)
        c.fill(rect(16,522,370,266),with: gradient([.clear,.black.opacity(56.0/255),.black.opacity(173.0/255),.black],
                                                     .init(x: 0,y: 522),.init(x: 0,y: 770),stops: [0,90.0/248,180.0/248,1]))
        var texture = c
        texture.blendMode = .multiply
        texture.opacity *= 0.62
        let image = texture.resolve(Assets.streaks)
        let width = image.size.width*0.6
        let height = image.size.height*0.6
        if width > 0 && height > 0 {
            for y in stride(from: 522.0,to: 780,by: height) {
                for x in stride(from: 24.0,to: 378,by: width) {
                    texture.draw(image,in: CGRect(x: x,y: y,width: width,height: height))
                }
            }
        }
        var dust = c
        dust.blendMode = .plusLighter
        dust.opacity *= 0.6
        dust.addFilter(.colorMultiply(rgb(178.5+f.r*0.3,178.5+f.g*0.3,178.5+f.b*0.3)))
        dust.draw(Assets.glints,in: CGRect(x: 40,y: 520,width: 322,height: 161))
    }

    private func curtain(_ context: GraphicsContext, mirror: Bool) {
        var c = context
        c.opacity *= Curves.clamp(f.area)*(mirror ? 0.9 : 1)
        c.clip(to: rect(64,236,min(f.hx-64,f.d == 1 ? 274*0.968 : 274),274))
        c.clip(to: Curves.curves[f.d].area)
        c.drawLayer { layer in
            layer.fill(rect(64,236,274,274),with: gradient([
                color.opacity(min(0.75,0.3*max(1,f.area))),color.opacity(0.15*max(1,f.area))
            ],.init(x: 0,y: 236),.init(x: 0,y: 510)))
            layer.blendMode = .destinationIn
            let colors: [Color] = mirror && f.d == 1 ? [.white.opacity(115.0/255),.white,.clear,.clear] : [.white.opacity(115.0/255),.white]
            layer.fill(rect(64,236,274,274),with: gradient(colors,.init(x: 64,y: 0),.init(x: 338,y: 0),
                                                          stops: mirror && f.d == 1 ? [0,(274*0.968-10)/274,0.968,1] : nil))
        }
        if !mirror {
            if f.d == 1 { stroke(c,Curves.grid,color,0.75,alpha: 0.11) }
            else { c.fill(Curves.dots,with: .color(color.opacity(0.11))) }
        }
    }

    private func tube(_ c: GraphicsContext, path: Path, mirror: Bool, color: Color, core: Color, glow: Double = 1, coreWidth: Double = 2) {
        if !mirror {
            var shadow = c
            shadow.translateBy(x: 0,y: 3)
            stroke(shadow,path,.black.opacity(0.5),5,blur: 4)
        }
        stroke(c,path,color,mirror ? 14 : 10,blur: mirror ? 16 : 12,alpha: (mirror ? 0.4 : 0.42)*glow)
        stroke(c,path,color,mirror ? 6 : 4.5,blur: mirror ? 6 : 2.5,alpha: (mirror ? 0.7 : 0.9)*glow)
        if !mirror { stroke(c,path,core,coreWidth) }
    }

    private func scene(_ context: GraphicsContext, mirror: Bool) {
        curtain(context,mirror: mirror)
        if !mirror {
            stroke(context,Curves.grid,.white.opacity(18.0/255),0.75,alpha: f.grid)
            var dots = context
            dots.clip(to: rect(64,236,274*Film.smooth(intro),274))
            dots.fill(Curves.dots,with: .color(.white.opacity(71.0/255*(1-f.grid)*Film.smooth(intro))))
            for i in 0..<4 { column(context,i) }
        }
        let axisY = Curves.clamp(intro*0.9/0.4)
        let axisX = Curves.clamp((intro*0.9-0.35)/0.55)
        stroke(context,Curves.axes,.white,mirror ? 3 : 4,blur: mirror ? 3 : 4,alpha: (mirror ? 0.24 : 0.35)*axisX)
        if !mirror {
            stroke(context,Curves.line([.init(x: 64,y: 236),.init(x: 64,y: 510)]).trimmedPath(from: 0,to: axisY),.white.opacity(235.0/255),1.5)
            stroke(context,Curves.line([.init(x: 64,y: 510),.init(x: 338,y: 510)]).trimmedPath(from: 0,to: axisX),.white.opacity(235.0/255),1.5)
        }
        var line = context
        line.opacity *= f.L >= 7.05 && f.L < 8.07 ? 1-Film.smooth((f.L-7.05)/0.04) : 1
        tube(line,path: Curves.curves[f.d].path.trimmedPath(from: 0,to: f.end),mirror: mirror,color: color,core: core,glow: f.glow)
        if f.broken > 0 {
            for i in 0..<6 { brokenPiece(context,i,mirror: mirror) }
        }
        if f.heat > 0 { heat(context,mirror: mirror) }
        var sparks = Path()
        var sparkCores = Path()
        for i in 0..<30 {
            let p = Film.sparkPoint(f.L,i)
            if p.x > 0 {
                sparks.addEllipse(in: CGRect(x: p.x-2.25,y: p.y-2.25,width: 4.5,height: 4.5))
                sparkCores.addEllipse(in: CGRect(x: p.x-0.8,y: p.y-0.8,width: 1.6,height: 1.6))
            }
        }
        var blurred = context
        blurred.addFilter(.blur(radius: 2))
        blurred.fill(sparks,with: .color(rgb(255,45,61,mirror ? 0.4 : 0.75)))
        if !mirror { context.fill(sparkCores,with: .color(rgb(255,241,217))) }
        if f.gatherFlash > 0 {
            var gather = context
            gather.opacity *= f.gatherFlash*(mirror ? 0.5 : 1)
            radial(gather,x: 64,y: 510,radius: 10+34*f.gatherTime,
                   colors: [.white.opacity(0.9),rgb(255,188,173,0.9),rgb(255,45,61,0)],stops: [0,0.18,1])
            stroke(gather,circle(64,510,6+24*f.gatherTime),rgb(255,154,145),1.5,alpha: 0.7)
        }
        head(context,mirror: mirror)
    }

    private func brokenPiece(_ context: GraphicsContext, _ i: Int, mirror: Bool) {
        let pose = Film.piecePose(f.L,i)
        let piece = Curves.pieces[i]
        var c = context
        c.opacity *= f.broken*pose.alpha
        c.translateBy(x: pose.x,y: pose.y)
        c.rotate(by: .radians(pose.angle))
        c.scaleBy(x: 1,y: pose.sy)
        c.translateBy(x: -piece.cx,y: -piece.cy)
        tube(c,path: piece.path,mirror: mirror,color: rgb(255,45,61),core: rgb(255,160,168),coreWidth: 2/sqrt(pose.sy))
        let t = f.L-(7.05+Double(5-i)*0.045)
        let point = Curves.atArc(0,piece.start)
        var crack = context
        crack.opacity *= Film.pulse(t,0.006,0.084)*(mirror ? 0.5 : 1)
        radial(crack,x: point.x,y: point.y,radius: 2+7*Curves.clamp(t/0.09),
               colors: [.white,rgb(255,215,172),rgb(255,45,61,0)],stops: [0,0.2,1])
    }

    private func heat(_ context: GraphicsContext, mirror: Bool) {
        guard f.heatEnd > f.heatStart else { return }
        var c = context
        c.opacity *= f.heat*(mirror ? 0.7 : 1)
        let path = Curves.line([.init(x: 64,y: 510),.init(x: 326.218,y: 510)]).trimmedPath(from: f.heatStart,to: f.heatEnd)
        stroke(c,path,rgb(255,45,61),10,blur: 10,alpha: 0.5)
        stroke(c,path,rgb(255,45,61),4.5,blur: 2.5,alpha: 0.9)
        if !mirror {
            c.stroke(path,with: gradient([rgb(255,135,147),rgb(255,244,233)],
                                        .init(x: max(64,f.hx-80),y: 510),.init(x: max(65,f.hx),y: 510)),
                     style: StrokeStyle(lineWidth: 2,lineCap: .round))
        }
    }

    private func head(_ context: GraphicsContext, mirror: Bool) {
        guard f.arrow > 0 else { return }
        var c = context
        c.opacity *= f.arrow*(mirror ? 0.35 : 1)
        c.translateBy(x: f.hx,y: f.hy)
        c.rotate(by: .radians(f.angle))
        radial(c,x: 0,y: 0,radius: 14+8*f.comet-5*f.gather,
               colors: [core.opacity(0.6+0.3*f.comet),color.opacity(0.5),color.opacity(0)],stops: [0,0.22,1])
        if !mirror { c.fill(Curves.arrow,with: .color(core)) }
        c.fill(circle(0,0,3.5+3.5*f.gather),with: .color(.white.opacity(f.comet)))
    }

    private func column(_ c: GraphicsContext, _ i: Int) {
        let point = Curves.atU(1,Curves.risers[i])
        guard f.d == 1 && f.hx >= point.x else { return }
        let alpha = 0.22+0.5*max(Film.columnFlash(f.L,i),Film.pulse(elapsed-columnTimes[i],0.12,0.4))
        c.fill(rect(point.x,point.y,0.8,510-point.y),with: gradient([color.opacity(alpha),.clear],point.point,.init(x: point.x,y: 510)))
    }

    private func shard(_ context: GraphicsContext, _ i: Int) {
        guard Assets.shards.count == 6 else { return }
        let pose = Film.shardPose(f.L,i,still: still,life: f.life)
        guard pose.alpha > 0 else { return }
        let size = Curves.shards[i][2]
        var c = context
        c.opacity *= pose.alpha
        c.translateBy(x: pose.x,y: pose.y)
        c.rotate(by: .radians(pose.angle))
        c.clip(to: rect(-size/2,-size/2,size,size))
        var glass = c
        glass.addFilter(.colorMultiply(rgb(255*0.85+f.r*0.15,255*0.85+f.g*0.15,255*0.85+f.b*0.15)))
        glass.draw(Assets.shards[i],in: CGRect(x: -size/2,y: -size/2,width: size,height: size))
        if i < 2 {
            c.translateBy(x: -size/2,y: -size/2)
            c.scaleBy(x: size/256,y: size/256)
            let points: [CGPoint] = i == 0 ? [.init(x: 43,y: 53),.init(x: 175,y: 33),.init(x: 219,y: 177)] : [.init(x: 62,y: 31),.init(x: 211,y: 87),.init(x: 162,y: 220)]
            stroke(c,Curves.line(points),color.opacity(0.25),2)
        }
    }

    private func label(_ context: GraphicsContext, _ d: Int) {
        guard d == 0 || f.label > 0 else { return }
        let run = Assets.labels[d]
        let reveal = intro < 1 ? Film.smooth(intro) : f.label
        let edge = 40+322*reveal
        let ink = rgb(244,246,245)
        let colors: [Color] = d == 1 || intro < 1 ? [ink,.clear] : [.clear,ink]
        var c = context
        c.translateBy(x: 0,y: 4*(d == 1 ? 1-f.label : f.label))
        let path = run.path.applying(CGAffineTransform(translationX: 201-run.width/2,y: 204))
        let shade = gradient(colors,.init(x: edge,y: 0),.init(x: edge+24,y: 0))
        for (radius,alpha) in [(14.0,0.25),(6.0,0.8),(0.0,1.0)] {
            var layer = c
            layer.opacity *= alpha
            if radius > 0 { layer.addFilter(.blur(radius: radius)) }
            layer.fill(path,with: shade)
        }
        if d == 1 && f.glitch > 0 {
            for (y,dx,h) in [(187.0,4.0,8.0),(195.0,-4.0,9.0)] {
                var glitch = c
                glitch.opacity *= f.glitch*0.35
                glitch.clip(to: rect(60,y,290,h))
                glitch.translateBy(x: dx,y: 0)
                glitch.fill(path,with: .color(.white))
            }
        }
    }

    private func marker(_ context: GraphicsContext, mirror: Bool) {
        guard markerAlpha > 0 else { return }
        let u = Curves.clamp(markerU,0,f.d == 1 ? 0.968 : 0.957)
        let p = Curves.atU(f.d,u)
        var c = context
        c.opacity *= markerAlpha*(mirror ? 0.35 : 1)
        c.translateBy(x: p.x,y: p.y)
        let scale = 0.9+0.1*markerAlpha
        c.scaleBy(x: scale,y: scale)
        c.fill(rect(-0.5,0,1,510-p.y),with: gradient([color.opacity(0.5),color.opacity(0.15)],.zero,.init(x: 0,y: 274)))
        radial(c,x: 0,y: 0,radius: 15,colors: [color.opacity(0.25),color.opacity(0.075)])
        c.fill(circle(0,0,3.5),with: .color(.white))
        if !mirror {
            c.translateBy(x: Curves.clamp(p.x,92,310)-p.x-36,y: -42)
            let chip = rounded(0,0,72,28,14)
            c.fill(chip,with: .color(rgb(12,14,13,0.88)))
            stroke(c,chip,color,1,alpha: 0.55)
            let day = Assets.days[min(30,Int((30*u/(f.d == 1 ? 1 : 0.957)).rounded()))]
            let value = Assets.values[Int((Curves.clamp(p.v)*100).rounded())]
            c.fill(day.path.applying(.init(translationX: 10,y: 18)),with: .color(.white.opacity(0.6)))
            c.fill(value.path.applying(.init(translationX: 62-value.width,y: 19)),with: .color(.white))
        }
    }

    private func switchArt(_ context: GraphicsContext) {
        let track = rounded(133,770,136,44,22)
        context.fill(track,with: .color(.white.opacity(13.0/255)))
        context.stroke(track,with: gradient([.white.opacity(56.0/255),.white.opacity(15.0/255)],.init(x: 0,y: 770),.init(x: 0,y: 814)),lineWidth: 1)
        let p = Curves.clamp(thumb)
        let tint = rgb(255-215*p,45+179*p,61+61*p)
        var c = context
        c.translateBy(x: 169+64*thumb,y: 792)
        let scale = pressed == Int(thumb.rounded()) ? 1-0.03*pressAmount : 1
        c.scaleBy(x: scale,y: scale)
        c.translateBy(x: -31,y: -18)
        let pill = rounded(0,0,62,36,18)
        var bloom = c
        bloom.addFilter(.blur(radius: 8))
        bloom.fill(pill,with: .color(tint.opacity(0.25)))
        c.fill(pill,with: .color(.white.opacity(23.0/255)))
        stroke(c,pill,tint,1,alpha: 0.55)
        for d in 0...1 {
            let active = d == 1 ? p : 1-p
            let press = pressed == d ? pressAmount*0.2 : 0
            let color = d == 1 ? rgb(255-215*active*(1-press),255-31*active*(1-press),255-133*active*(1-press),0.38+0.62*active+press)
                : rgb(255,255-210*active*(1-press),255-194*active*(1-press),0.38+0.62*active+press)
            var glyph = context
            glyph.translateBy(x: d == 1 ? 233 : 169,y: 792)
            let compression = pressed == d ? 1-0.03*pressAmount : 1
            glyph.scaleBy(x: compression,y: compression)
            stroke(glyph,Curves.glyphs[d],color,1.75,blur: 2.5,alpha: 0.6*active)
            stroke(glyph,Curves.glyphs[d],color,1.75)
        }
    }
}
