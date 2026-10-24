import SwiftUI

/// Source-fitted body, face and independently moving keycap vectors.
/// Geometry interpolates continuously; the source color field holds at60Hz in all ports.
final class BuzzStreaksMascots {
    private struct KeyGradient:Decodable { let start:[Double]; let end:[Double]; let colors:[[Double]] }
    private struct KeyLetter:Decodable { let points:[[Double]] }
    private struct KeyDrawing:Decodable {
        let baseline_size:[Double]; let outer:[[Double]]; let face:[[Double]]
        let letters:[KeyLetter]; let letterCurves:[[Double]]; let ink_rgb:[Double]
        let outer_gradient:KeyGradient; let face_gradient:KeyGradient
    }
    private struct KeyDrawings:Decodable { let characters:[String:KeyDrawing] }
    private struct KeyImage: Decodable {
        let imageSize: [Double]
        let size: [Double]
        let center: [Double]
        let angle: Double
    }
    private struct CharacterRig: Decodable {
        let keyImage: KeyImage
        let key: [[Double]]
        let face: [[Double]]
        let faceBaselineAngle: Double
        let bodyGradient: [[Double]]
        let arm: [[Double]]
    }
    private struct Rig: Decodable { let characters: [String: CharacterRig] }
    private struct Spark: Decodable {
        let points: [[Double]]
        let opacity: Double
    }
    private struct Sparks: Decodable {
        let fillRGB: [Double]
        let frames: [[Spark]]
    }
    private struct Body: Decodable {
        let paths: [[Double]]
        let primary: [Int]
        let secondary: [String: [Int]]
        let whiteFace: [String: [Int]]
    }
    private struct Geometry: Decodable {
        let pointScale: Double
        let colors: [String: [Double]]
        let characters: [String: Body]
    }
    private let rig: Rig?
    private let geometry: Geometry?
    private let bodyCurves: [String:[[Double]]]
    private let keys:KeyDrawings?
    private let keyPaths:[String:[Path]]
    private let sparkFrames: [[(Path, Double)]]
    private let sparkColor: Color

    init() {
        func load<T: Decodable>(_ name: String, _: T.Type) -> T? {
            guard let url = Bundle.main.url(forResource: "BuzzStreaks-\(name)", withExtension: "json"),
                  let data = try? Data(contentsOf: url) else { return nil }
            return try? JSONDecoder().decode(T.self, from: data)
        }
        rig = load("rig", Rig.self)
        let sourceGeometry = load("geometry", Geometry.self)
        geometry = sourceGeometry
        if let sourceGeometry {
            bodyCurves = sourceGeometry.characters.mapValues { body in
                body.paths.map { Self.cubicContour($0, unit:sourceGeometry.pointScale) }
            }
        } else { bodyCurves = [:] }
        keys = load("key-vectors",KeyDrawings.self)
        var paths:[String:[Path]]=[:]
        for (kind,key) in keys?.characters ?? [:] {
            func plate(_ points:[[Double]])->Path { Path { p in
                guard !points.isEmpty else {return}
                let last=points.last!
                p.move(to:CGPoint(x:(last[0]+points[0][0])/2,y:(last[1]+points[0][1])/2))
                for i in points.indices {
                    let next=points[(i+1)%points.count]
                    p.addQuadCurve(to:CGPoint(x:(points[i][0]+next[0])/2,y:(points[i][1]+next[1])/2),control:CGPoint(x:points[i][0],y:points[i][1]))
                }
                p.closeSubpath()
            } }
            let letters=Path { p in
                for contour in key.letterCurves where contour.count >= 8 {
                    p.move(to:CGPoint(x:contour[0],y:contour[1]))
                    for i in stride(from:2,to:contour.count,by:6) {
                        p.addCurve(to:CGPoint(x:contour[i+4],y:contour[i+5]),
                            control1:CGPoint(x:contour[i],y:contour[i+1]),
                            control2:CGPoint(x:contour[i+2],y:contour[i+3]))
                    }
                    p.closeSubpath()
                }
            }
            paths[kind]=[plate(key.outer),plate(key.face),letters]
        }
        keyPaths=paths
        let sparks = load("sparks", Sparks.self)
        let rgb = sparks?.fillRGB ?? [225, 233, 75]
        sparkColor = Color(red: rgb[0] / 255, green: rgb[1] / 255, blue: rgb[2] / 255)
        sparkFrames = sparks?.frames.map { frame in frame.map { spark in
            (Path { p in
                for (index, point) in spark.points.enumerated() {
                    let position = CGPoint(x: point[0], y: point[1])
                    if index == 0 { p.move(to: position) } else { p.addLine(to: position) }
                }
                p.closeSubpath()
            }, spark.opacity)
        } } ?? []
        precondition(rig != nil && geometry != nil && paths.count == 2,
               "BuzzStreaks requires valid rig, body and key vector geometry.")
    }

    var isReady: Bool { rig != nil && geometry != nil && keyPaths.count == 2 }

    private func sample(_ values: [[Double]], _ t: Double) -> [Double] {
        let f = min(Double(values.count - 1), max(0, t * 60))
        let i = Int(f), j = min(values.count - 1, i + 1), p = f - Double(i)
        return zip(values[i], values[j]).map { $0 + ($1 - $0) * p }
    }

    /// Fixed cubic verbs preserve interpolation while smooth tangents remove polygon facets.
    /// Real corners have zero handles; handles are bounded to prevent loops at small islands.
    private static func cubicContour(_ raw:[Double],unit:Double)->[Double] {
        let count=raw.count/2
        guard count>=3 else {return []}
        func point(_ index:Int)->CGPoint {
            let i=(index+count)%count
            return CGPoint(x:raw[2*i]/unit,y:raw[2*i+1]/unit)
        }
        var handles=[CGPoint](repeating:.zero,count:count)
        for i in 0..<count {
            let previous=point(i-1),p=point(i),next=point(i+1)
            let ax=p.x-previous.x,ay=p.y-previous.y,bx=next.x-p.x,by=next.y-p.y
            let a=hypot(ax,ay),b=hypot(bx,by)
            guard min(a,b)>1e-8,(ax*bx+ay*by)/(a*b)>=0.42261826174069944 else {continue}
            var x=(next.x-previous.x)/6,y=(next.y-previous.y)/6
            let length=hypot(x,y),limit=0.45*min(a,b)
            if length>limit {x*=limit/length;y*=limit/length}
            handles[i]=CGPoint(x:x,y:y)
        }
        var output=[Double]();output.reserveCapacity(2+6*count)
        let first=point(0);output.append(contentsOf:[first.x,first.y])
        for i in 0..<count {
            let j=(i+1)%count,p=point(i),next=point(j),h=handles[i],k=handles[j]
            output.append(contentsOf:[p.x+h.x,p.y+h.y,next.x-k.x,next.y-k.y,next.x,next.y])
        }
        return output
    }

    private func path(_ frames: [Int], curves:[[Double]], _ t: Double) -> Path {
        guard !frames.isEmpty else { return Path() }
        let f = min(Double(frames.count - 1), max(0, t * 60))
        let i = Int(f), j = min(frames.count - 1, i + 1), p = f - Double(i)
        let a = curves[frames[i]], b = curves[frames[j]]
        guard !a.isEmpty else { return Path() }
        return Path { path in
            func point(_ n:Int)->CGPoint {
                if a.count == b.count {return CGPoint(x:a[n]+(b[n]-a[n])*p,y:a[n+1]+(b[n+1]-a[n+1])*p)}
                return CGPoint(x:a[n],y:a[n+1])
            }
            path.move(to:point(0))
            for n in stride(from:2,to:a.count,by:6) {
                path.addCurve(to:point(n+4),control1:point(n),control2:point(n+2))
            }
            path.closeSubpath()
        }
    }

    func draw(_ kind: String, time: Double, signature:BuzzStreaksSignature? = nil, context: inout GraphicsContext) {
        guard let rig = rig?.characters[kind], let body = geometry?.characters[kind],let curves=bodyCurves[kind],
              let drawing=keys?.characters[kind],let paths=keyPaths[kind] else { return }
        if kind == "orange", !sparkFrames.isEmpty {
            for (path, opacity) in sparkFrames[min(sparkFrames.count - 1, max(0, Int(time * 60 + 1e-7)))] {
                var c = context
                c.opacity = opacity
                c.fill(path, with: .color(sparkColor))
            }
        }
        let g = rig.bodyGradient[min(rig.bodyGradient.count-1,max(0,Int(time*60 + 1e-7)))]
        let fill = GraphicsContext.Shading.linearGradient(Gradient(colors: [
            Color(red: g[4]/255, green: g[5]/255, blue: g[6]/255),
            Color(red: g[7]/255, green: g[8]/255, blue: g[9]/255),
        ]), startPoint: CGPoint(x: g[0], y: g[1]), endPoint: CGPoint(x: g[2], y: g[3]))
        let silhouette = path(body.primary, curves:curves, time)
        let islands=body.secondary.values.map {path($0,curves:curves,time)}
        context.fill(silhouette, with: fill)
        for island in islands { context.fill(island, with: fill) }

        let key = sample(rig.key, time)
        if key[5] > 0.001 {
            var prop = context
            prop.opacity = key[5]
            prop.translateBy(x: key[0], y: key[1])
            prop.rotate(by: .degrees(key[4]))
            prop.scaleBy(x:key[2]/drawing.baseline_size[0],y:key[3]/drawing.baseline_size[1])
            func shading(_ g:KeyGradient)->GraphicsContext.Shading {
                .linearGradient(Gradient(colors:g.colors.map {Color(red:$0[0]/255,green:$0[1]/255,blue:$0[2]/255)}),
                    startPoint:CGPoint(x:g.start[0],y:g.start[1]),endPoint:CGPoint(x:g.end[0],y:g.end[1]))
            }
            prop.fill(paths[0],with:shading(drawing.outer_gradient))
            // Contact moves only the face and ink within the source outer shell.
            prop.translateBy(x:0,y:(signature?.inset(kind,time) ?? 0)/(key[3]/drawing.baseline_size[1]))
            prop.fill(paths[1],with:shading(drawing.face_gradient))
            if kind == "pink",let signature {
                let alpha=signature.reflection(kind,time)
                if alpha>0 {
                    var light=prop;light.clip(to:paths[1])
                    let radius=signature.reflectionRadius/(key[3]/drawing.baseline_size[1])
                    light.fill(Path(ellipseIn:CGRect(x:-radius,y:-radius,width:2*radius,height:2*radius)),
                        with:.radialGradient(Gradient(colors:[.white.opacity(alpha),.clear]),center:.zero,startRadius:0,endRadius:radius))
                }
            }
            prop.fill(paths[2],with:.color(Color(red:drawing.ink_rgb[0]/255,green:drawing.ink_rgb[1]/255,blue:drawing.ink_rgb[2]/255)),style:FillStyle(eoFill:true))
            // Restore only the source foreground hand over the keycap.
            let arm = sample(rig.arm, time)
            var front = context
            front.clip(to: Path(CGRect(x: min(arm[0], arm[2]) - 7,
                                      y: min(arm[1], arm[3]),
                                      width: abs(arm[2] - arm[0]) + 14,
                                      height: abs(arm[3] - arm[1]) + 8)))
            front.fill(silhouette, with: fill)
            for island in islands { front.fill(island, with: fill) }
        }
        if kind == "orange",let signature {
            let alpha=signature.reflection(kind,time)
            if alpha>0 {
                var light=context;light.clip(to:silhouette)
                let point=CGPoint(x:75,y:325)
                let radius=signature.reflectionRadius
                light.fill(Path(ellipseIn:CGRect(x:point.x-radius,y:point.y-radius,width:2*radius,height:2*radius)),
                    with:.radialGradient(Gradient(colors:[.white.opacity(alpha),.clear]),center:point,startRadius:0,endRadius:radius))
            }
        }
        drawFace(kind, body:body,time:time,baselineAngle:rig.faceBaselineAngle,values:sample(rig.face,time),gaze:signature?.gaze(kind,time) ?? .zero,context:&context)
    }

    private func drawFace(_ kind: String, body: Body, time: Double, baselineAngle: Double, values v: [Double], gaze:CGPoint, context: inout GraphicsContext) {
        for eye in 0...1 {
            let n = eye * 4
            var c = context
            if let frames = body.whiteFace[eye == 0 ? "leftEye" : "rightEye"], !frames.isEmpty,
               frames[min(frames.count - 1, max(0, Int(time * 60 + 1e-7)))] != 0,let curves=bodyCurves[kind] {
                let white = path(frames, curves:curves, time)
                c.fill(white, with: .color(.white))
                c.clip(to: white)
                let r = v[n + 3] * (kind == "orange" ? 0.35 : 0.29)
                c.fill(Path(ellipseIn: CGRect(x: v[8 + eye * 2] + gaze.x - r, y: v[9 + eye * 2] + gaze.y - r,
                                              width: 2*r, height: 2*r)), with: .color(.black))
                continue
            }
            c.translateBy(x: v[n], y: v[n + 1])
            c.rotate(by: .degrees(v[13 + eye]))
            let rect = CGRect(x: -v[n + 2] / 2, y: -v[n + 3] / 2, width: v[n + 2], height: v[n + 3])
            let white: Path
            if kind == "orange" {
                let rx = rect.width/2, h = rect.height, bottom = h/2, k = 0.5522847498
                white = Path { p in
                    p.move(to:CGPoint(x:-rx,y:bottom))
                    p.addCurve(to:CGPoint(x:0,y:bottom-h), control1:CGPoint(x:-rx,y:bottom-k*h), control2:CGPoint(x:-k*rx,y:bottom-h))
                    p.addCurve(to:CGPoint(x:rx,y:bottom), control1:CGPoint(x:k*rx,y:bottom-h), control2:CGPoint(x:rx,y:bottom-k*h))
                    p.closeSubpath()
                }
            } else if eye == 0 { white = Path(roundedRect: rect, cornerRadius: 1) }
            else { white = Path(ellipseIn: rect) }
            c.fill(white, with: .color(.white))
            c.clip(to: white)
            c.rotate(by: .degrees(-v[13 + eye]))
            let r = v[n + 3] * (kind == "orange" ? 0.35 : 0.29)
            c.fill(Path(ellipseIn: CGRect(x: v[8 + eye * 2] + gaze.x - v[n] - r,
                                          y: v[9 + eye * 2] + gaze.y - v[n + 1] - r,
                                          width: 2*r, height: 2*r)), with: .color(.black))
        }
        if kind == "pink", let frames = body.whiteFace["mouth"], !frames.isEmpty,
           frames[min(frames.count - 1, max(0, Int(time * 60 + 1e-7)))] != 0,let curves=bodyCurves[kind] {
            context.fill(path(frames, curves:curves, time), with: .color(.white))
            return
        }
        var mouth = context
        let cx = (v[0] + v[4]) / 2, cy = (v[1] + v[5]) / 2
        let scale = (v[2] + v[6]) / (kind == "orange" ? 34.24 : 33.42)
        mouth.translateBy(x: cx, y: cy)
        mouth.rotate(by: .degrees(v[12] - baselineAngle))
        mouth.scaleBy(x: scale, y: scale)
        let smile = Path { p in
            p.move(to: CGPoint(x: kind == "orange" ? -17 : -2, y: kind == "orange" ? 12 : 18))
            p.addQuadCurve(to: CGPoint(x: kind == "orange" ? -3 : 10, y: kind == "orange" ? 13 : 16),
                           control: CGPoint(x: kind == "orange" ? -10 : 5, y: kind == "orange" ? 21 : 23))
        }
        mouth.stroke(smile, with: .color(.white), style: StrokeStyle(lineWidth: 1.25, lineCap: .round))
    }
}
