import CoreGraphics
import Foundation

/// Cached arc-length tables are generated verbatim from the final approved RN module.
enum TrophySavingsMilestoneStreamerGeometry {
    struct Vector: Decodable, Sendable {
        var x: Double; var y: Double
        var point: CGPoint { CGPoint(x: x, y: y) }
        var unit: Self { self / max(0.000001, hypot(x,y)) }
        var normal: Self { Self(x: -y, y: x) }
        static func +(a: Self,b: Self)->Self { Self(x:a.x+b.x,y:a.y+b.y) }
        static func -(a: Self,b: Self)->Self { Self(x:a.x-b.x,y:a.y-b.y) }
        static func *(a: Self,b: Double)->Self { Self(x:a.x*b,y:a.y*b) }
        static func /(a: Self,b: Double)->Self { Self(x:a.x/b,y:a.y/b) }
    }
    struct Station { var center: Vector; var tangent: Vector; var width: Double; var facing: Double; var joinBlend: Double
        var left: Vector { center+tangent.normal*(width*0.5) }; var right: Vector { center-tangent.normal*(width*0.5) }
    }
    struct Span { var a: Station; var b: Station; var foreground: Bool }
    struct Roll { var center: Vector; var tangent: Vector; var width: Double; var opacity: Double; var foreground: Bool }
    struct Frame {
        var headDistance: Double; var tailDistance: Double; var bodyLength: Double; var tipLength: Double; var coiledLength: Double
        var coilScale: Double; var coilJoint: Vector; var coilWidth: Double; var coilOpacity: Double; var meshOpacity: Double
        var spans: [Span]; var roll: Roll
    }
    private struct GuidePoint: Decodable, Sendable { var distance: Double; var center: Vector; var tangent: Vector; var facing: Double; var foreground: Bool }
    private struct Guide: Decodable, Sendable { var points: [GuidePoint]; var seam: Double; var length: Double; var feedLength: Double; var boundaries: [Double] }
    private struct Tables: Decodable, Sendable { var feed: Guide; var returns: [Guide] }
    private static let tables: Tables = {
        guard let url = Bundle.main.url(forResource: "TrophySavingsMilestone-paper-guides", withExtension: "json"),
              let data = try? Data(contentsOf: url), let decoded = try? JSONDecoder().decode(Tables.self, from: data) else {
            preconditionFailure("Missing TrophySavingsMilestone cached RN guide resource")
        }; return decoded
    }()
    static let feedJoint = Vector(x:35.24,y:154.95)
    private static func smooth(_ x: Double)->Double { TrophySavingsMilestoneMotion.smooth(x) }
    private static func clamp(_ x: Double,_ a: Double = 0,_ b: Double = 1)->Double { min(b,max(a,x)) }
    private static func lerp(_ a: Double,_ b: Double,_ q: Double)->Double { a+(b-a)*q }
    private static func mix(_ a: Vector,_ b: Vector,_ q: Double)->Vector { a+(b-a)*q }
    private static func pair(_ release: Double)->(Guide,Guide,Double) {
        if release < 0 { return (tables.feed,tables.feed,0) }
        let q = clamp(release)*16, low = min(15,Int(floor(q)))
        return (tables.returns[low],tables.returns[low+1],q-Double(low))
    }
    private static func layer(_ curve: Int,_ t: Double,_ release: Double)->Bool {
        if curve == 4 { return release < 0.19 }; if curve == 5 { return true }
        return curve == 6 || (curve == 7 && t < 0.5)
    }
    private static func sample(_ distance: Double,_ release: Double)->GuidePoint {
        let (ga,gb,m) = pair(release), d = clamp(distance,0,lerp(ga.length,gb.length,m))
        var low = 0, high = ga.points.count-1
        while low+1 < high { let mid = (low+high)/2; if lerp(ga.points[mid].distance,gb.points[mid].distance,m) <= d { low = mid } else { high = mid } }
        let a0 = ga.points[low], a1 = gb.points[low], b0 = ga.points[high], b1 = gb.points[high]
        let q = (d-lerp(a0.distance,a1.distance,m))/max(0.000001,lerp(b0.distance,b1.distance,m)-lerp(a0.distance,a1.distance,m))
        let foreground = release < 0 ? d >= tables.feed.seam : layer(Int(floor((Double(low)+0.001)/64)),(Double(low)+q).truncatingRemainder(dividingBy:64)/64,release)
        return GuidePoint(distance:d,center:mix(mix(a0.center,a1.center,m),mix(b0.center,b1.center,m),q),tangent:mix(mix(a0.tangent,a1.tangent,m),mix(b0.tangent,b1.tangent,m),q).unit,facing:lerp(lerp(a0.facing,a1.facing,m),lerp(b0.facing,b1.facing,m),q),foreground:foreground)
    }
    private static func station(_ d: Double,_ opening: Double,_ head: Double,_ release: Double)->Station {
        let p = sample(d,release), projected = 0.8+33.2*abs(p.facing)*opening
        return Station(center:p.center,tangent:p.tangent,width:1.3+(projected-1.3)*smooth(d/22),facing:p.facing,joinBlend:smooth((d-head+22)/22)*smooth((head-45)/65))
    }
    static func frame(progress: Double, pose: Double)->Frame {
        let release = pose >= 1 ? clamp(pose-1) : -1, (a,b,m) = pair(release)
        let feed = lerp(a.feedLength,b.feedLength,m), total = lerp(a.length,b.length,m)
        let turnEnd = release < 0 ? feed : lerp(a.points[7*64].distance,b.points[7*64].distance,m)
        let gather = release < 0 ? 0 : smooth((release-0.78)/0.22)
        let head = release < 0 ? clamp(progress)*tables.feed.length : feed+(turnEnd-feed)*smooth(release/0.5)+(total-turnEnd)*gather
        let tip = 36*smooth((head-45)/65)*(1-gather)
        let body = release < 0 ? min(head,250-tip) : 214*(1-gather)
        let tail = max(0,head-body), coiled = max(0,250-body-tip), opening = smooth((head-24)/50)*(1-0.96*gather)
        var spans: [Span] = []
        if body > 0.000001 {
            var distances = [tail]; let steps = max(1,Int(ceil(body/6.5)))
            if steps > 1 { for i in 1..<steps { distances.append(tail+body*Double(i)/Double(steps)) } }
            for i in a.boundaries.indices { let d = lerp(a.boundaries[i],b.boundaries[i],m); if d > tail && d < head { distances.append(d) } }
            distances.append(head); distances.sort(); spans.reserveCapacity(36)
            for i in 1..<distances.count { let x = distances[i-1], y = distances[i]; if y-x > 0.000001 { spans.append(Span(a:station(x,opening,head,release),b:station(y,opening,head,release),foreground:sample((x+y)*0.5,release).foreground)) } }
        }
        let h = sample(head,release), radius = sqrt(1.6*1.6+0.45*coiled/Double.pi), full = sqrt(1.6*1.6+0.45*250/Double.pi)
        return Frame(headDistance:head,tailDistance:tail,bodyLength:body,tipLength:tip,coiledLength:coiled,coilScale:radius/full,coilJoint:release < 0 ? feedJoint : h.center,coilWidth:1.3+0.8*sin(.pi*(1-coiled/250)),coilOpacity:0.75*(release < 0 ? smooth(coiled/10) : smooth((gather-0.72)/0.28)),meshOpacity:smooth(head/8),spans:spans,roll:Roll(center:h.center,tangent:h.tangent,width:station(head,opening,head,release).width,opacity:smooth(tip/36)*(1-smooth((gather-0.58)/0.24)),foreground:h.foreground))
    }
}
