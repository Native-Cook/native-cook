import SwiftUI

enum GlassApertureCameraArt {
    typealias R = GlassApertureCameraResources
    typealias M = GlassApertureCameraMotion
    private static let star: Path = polygon([(12,1.8),(15.1,8.3),(22.2,9.2),(17,14.1),(18.3,21.2),(12,17.8),(5.7,21.2),(7,14.1),(1.8,9.2),(8.9,8.3)])
    private static let pen: Path = polygon([(2,14),(3,10.5),(11.5,2),(14,4.5),(5.5,13)])
    private static let bolt: Path = polygon([(7.5,1),(1.5,10),(6,10),(4.5,17),(10.5,7.5),(6,7.5)])
    private static let flip: Path = {
        var p = Path(); p.move(to: CGPoint(x:14.5,y:9))
        p.addArc(center: CGPoint(x:9,y:9),radius:5.5,startAngle:.radians(0),endAngle:.radians(Double.pi*1.5),clockwise:false)
        p.addLine(to: CGPoint(x:12.5,y:3.5)); p.move(to: CGPoint(x:10.5,y:1))
        p.addLine(to: CGPoint(x:13,y:3.5)); p.addLine(to: CGPoint(x:10.5,y:6)); return p
    }()
    private static let check: Path = {
        var p = Path(); p.move(to: CGPoint(x:4.6,y:8.3)); p.addLine(to:CGPoint(x:7,y:10.6)); p.addLine(to:CGPoint(x:11.4,y:5.8)); return p
    }()
    private static let grid: Path = {
        var p = Path()
        for row in 0..<3 { for column in 0..<3 {
            p.addRoundedRect(in:CGRect(x:column*5,y:row*5,width:3,height:3),cornerSize:CGSize(width:0.6,height:0.6),style:.circular)
        } }; return p
    }()
    private static let unitCircle = Path(ellipseIn:CGRect(x:-1,y:-1,width:2,height:2))
    private struct GlassApertureCameraPaths {
        let w, h, viewH: Double
        let footer: Path
        init(_ l: GlassApertureCameraTheme.Layout) {
            w = l.w; h = l.h; viewH = l.viewH
            footer = Path(CGRect(x:0,y:l.viewH,width:l.w,height:l.h-l.viewH))
        }
    }
    private static var cached: GlassApertureCameraPaths?
    private static func paths(_ l: GlassApertureCameraTheme.Layout) -> GlassApertureCameraPaths {
        if let cached, cached.w == l.w, cached.h == l.h, cached.viewH == l.viewH { return cached }
        let paths = GlassApertureCameraPaths(l); cached = paths; return paths
    }
    private static let chip = Path(roundedRect:CGRect(x:-34,y:-15,width:68,height:30),cornerRadius:15,style:.circular)
    private static func polygon(_ points: [(Double,Double)]) -> Path {
        var p = Path(); if let first = points.first { p.move(to:CGPoint(x:first.0,y:first.1)) }
        for (x,y) in points.dropFirst() { p.addLine(to:CGPoint(x:x,y:y)) }; p.closeSubpath(); return p
    }
    private static func label(_ c: GraphicsContext,_ label:R.Label,_ x:Double,_ y:Double,_ color:Color = .white) {
        c.draw(label.text.foregroundColor(color), at:CGPoint(x:x,y:y))
    }
    private static func circle(_ c: GraphicsContext,_ x:Double,_ y:Double,_ radius:Double,_ color:Color,_ stroke:Double = 0) {
        var local = c; local.translateBy(x:x,y:y); local.scaleBy(x:radius,y:radius)
        if stroke > 0 { local.stroke(unitCircle,with:.color(color),lineWidth:stroke/radius) }
        else { local.fill(unitCircle,with:.color(color)) }
    }
    private static func icon(_ c: GraphicsContext,_ path:Path,_ x:Double,_ y:Double,_ size:Double,_ source:Double,_ color:Color = .white) {
        var local = c; local.translateBy(x:x-size/2,y:y-size/2); local.scaleBy(x:size/source,y:size/source)
        local.fill(path,with:.color(color))
    }
    static func draw(_ context: GraphicsContext,_ l:GlassApertureCameraTheme.Layout,_ f:M.Frame,_ reduced:Bool,_ displayScale:Double) {
        let r = R.shared
        let c = context
        let y = l.Y(607)
        icon(c,star,48*l.sx,y,18,24,R.star)
        circle(c,148*l.sx,y,4.5,.white)
        // RN gridTool overrides the 44 pt width with 56, retaining left = grid*sx - 22.
        let fiveWidth = r.five.size.width-4
        let groupWidth = 14+6+fiveWidth
        let groupLeft = 250*l.sx+6-groupWidth/2
        var gc = c; gc.translateBy(x:groupLeft,y:y-7); gc.fill(grid,with:.color(.white))
        label(c,r.five,groupLeft+20+fiveWidth/2,y)
        icon(c,pen,349*l.sx,y,16,16)

        for (target,x,radius) in [(1,82*l.sx,20.0),(2,l.shutterX,26.0),(3,316*l.sx,20.0)] {
            var control = c; control.translateBy(x:x,y:l.shutterY)
            let pressure = f.button == target ? f.buttonPress : 0
            let s = reduced ? 1 : 1-(target == 2 ? 0.1 : 0.04)*pressure
            control.scaleBy(x:s,y:s); control.opacity = reduced ? 1-0.28*pressure : 1
            let color = target == 2 ? Color(.sRGB,red:244/255,green:244/255,blue:242/255) : Color(.sRGB,white:16/255,opacity:0.72)
            circle(control,0,0,radius,color)
            if target == 1 {
                var b = control; b.translateBy(x:-6,y:-9); b.fill(bolt,with:.color(.white))
            } else if target == 3 {
                var b = control; b.translateBy(x:-9,y:-9)
                b.stroke(flip,with:.color(.white),style:StrokeStyle(lineWidth:1.6,lineCap:.round,lineJoin:.round))
            }
        }
        if f.iris > 0 {
            var chipC = c; chipC.opacity = f.iris
            chipC.translateBy(x:l.irisX,y:l.irisY-l.irisR-29+(1-f.iris)*6)
            chipC.fill(chip,with:.color(Color(.sRGB,white:12/255,opacity:0.66)))
            if let text = r.chips[M.label(f.fnum)] { label(chipC,text,-0.5/displayScale,-1/displayScale) }
        }
    }
    static func drawFooter(_ c: GraphicsContext,_ l:GlassApertureCameraTheme.Layout,_ f:M.Frame,_ reduced:Bool,_ displayScale:Double) {
        let r = R.shared
        c.fill(paths(l).footer,with:.color(Color(.sRGB,white:22/255,opacity:1)))
        let bumpY = reduced || f.shot < 0 ? 0 : 5*M.shotAt(f.shot).bump
        var rest = c; rest.opacity = 1-f.sent
        rest.translateBy(x:l.w/2,y:l.viewH+35+bumpY)
        let scale = 1-0.012*bumpY
        rest.scaleBy(x:scale,y:scale)
        rest.translateBy(x:-l.w/2,y:-l.viewH-35)
        label(rest,r.to,l.w/2-0.5/displayScale,l.viewH+25+1/displayScale)
        label(rest,r.share,l.w/2,l.viewH+45.5,.white.opacity(0.62))
        if f.sent > 0.5 {
            var sent = c; sent.opacity = f.sent
            let sentWidth = r.sent.size.width-4, width = 16+8+sentWidth
            let x = (l.w-width)/2, y = l.viewH+35+(1-f.sent)*6+bumpY
            circle(sent,x+8+1/displayScale,y,7.2,R.star)
            var tick = sent; tick.translateBy(x:x+1/displayScale,y:y-8)
            tick.stroke(check,with:.color(Color(.sRGB,white:11/255,opacity:1)),style:StrokeStyle(lineWidth:1.7,lineCap:.round,lineJoin:.round))
            label(sent,r.sent,x+24+sentWidth/2,y)
        }
    }
    static func drawOverlay(_ context: GraphicsContext,_ l:GlassApertureCameraTheme.Layout,_ f:M.Frame) {
        let c = context
        if f.ring >= 0 {
            let p = f.ringPoint ?? (f.t < 7 ? l.far : l.near)
            let radius = 46-18*min(1,f.ring/0.25)
            let alpha = f.ring < 0.9 ? 0.95 : 0.95*(1-(f.ring-0.9)/0.3)
            circle(c,p.x,p.y,radius,Color(.sRGB,red:1,green:214/255,blue:90/255,opacity:alpha),1.5)
        }
        if f.finger > 0 {
            let p: CGPoint
            if f.fingerAt == 0 { p = CGPoint(x:l.irisX-f.spread/2,y:l.irisY+f.spread*0.35) }
            else if f.fingerAt == 1 { p = l.far }
            else if f.fingerAt == 3 { p = l.near }
            else { p = CGPoint(x:l.shutterX,y:l.shutterY) }
            let radius = 22*(1-0.12*f.press)
            for point in f.fingerAt == 0 ? [p,CGPoint(x:l.irisX+f.spread/2,y:l.irisY-f.spread*0.35)] : [p] {
                circle(c,point.x,point.y,radius,.white.opacity(0.12*f.finger))
                circle(c,point.x,point.y,radius,.white.opacity(0.6*f.finger),1.5)
            }
        }
    }
}
