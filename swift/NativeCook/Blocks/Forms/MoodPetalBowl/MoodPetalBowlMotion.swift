import Foundation
import CoreGraphics

enum MoodPetalBowlMood: Int, CaseIterable {
    case calm, low, bright, tense
    var title: String { ["CALM", "LOW", "BRIGHT", "TENSE"][rawValue] }
    var hex: UInt32 { [0x0CB782, 0x567DF0, 0xF1DF67, 0xEE1644][rawValue] }
    func stepped(_ delta: Int) -> Self { Self(rawValue: (rawValue + delta + 4) % 4)! }
}
struct MoodPetalBowlRGBA: Equatable {
    var r: Double; var g: Double; var b: Double; var a: Double
    init(hex: UInt32, alpha: Double = 1) {
        r = Double((hex >> 16) & 255) / 255; g = Double((hex >> 8) & 255) / 255
        b = Double(hex & 255) / 255; a = alpha
    }
    static func mix(_ a: Self, _ b: Self, _ q: Double) -> Self {
        var c = a
        c.r += (b.r - a.r) * q; c.g += (b.g - a.g) * q
        c.b += (b.b - a.b) * q; c.a += (b.a - a.a) * q
        return c
    }
}
struct MoodPetalBowlToken: Equatable {
    let id: Int
    var mood: MoodPetalBowlMood
    var center: CGPoint
    var angle: Double
    var alpha: Double
    var batchAge: Double
}
struct MoodPetalBowlScene: Equatable {
    var tokens: [MoodPetalBowlToken]
    var labelWeights: [Double]
    var labelOffsets: [Double]
    var capsuleX: Double
    var dots: [MoodPetalBowlRGBA]
    var selectedDay: Int
    var captionAlpha: Double
    var captionDay: Int
    var dominantMood: MoodPetalBowlMood {
        let index = labelWeights.indices.max { labelWeights[$0] < labelWeights[$1] } ?? 0
        return MoodPetalBowlMood(rawValue: index) ?? .calm
    }
}
struct MoodPetalBowlTransition {
    let from: MoodPetalBowlScene
    let mood: MoodPetalBowlMood
    let day: Int
    let dots: [MoodPetalBowlRGBA]
    let direction: Double
    let speedup: Double
    let outgoing: [MoodPetalBowlToken]
}

struct MoodPetalBowlLayout {
    let radii: [Double]
    let targets: [CGPoint]
    let angles: [Double]
}

/// The normalized cached contours are shared by raster art and containment.
enum MoodPetalBowlGeometry {
    private static let layouts: [MoodPetalBowlLayout] = [
        // Calm: source silhouette bounds; authored seven-body arrangement.
        .init(radii: [59, 48, 54, 31, 37, 41, 29],
              targets: [.init(x:101,y:258), .init(x:183,y:295), .init(x:281,y:282), .init(x:282,y:350), .init(x:116,y:346), .init(x:190,y:376), .init(x:244,y:366)],
              angles: [20, 31, -12, 44, 11, -27, 58]),
        // Low: source silhouette bounds; authored seven-body arrangement.
        .init(radii: [61, 43, 56, 33, 36, 39, 29],
              targets: [.init(x:110,y:282), .init(x:181,y:317), .init(x:247,y:258), .init(x:289,y:326), .init(x:116,y:347), .init(x:194,y:376), .init(x:254,y:357)],
              angles: [-20, 31, -12, 44, 11, -27, 58]),
        // Bright: source silhouette bounds; authored seven-body arrangement.
        .init(radii: [53, 40, 82, 34, 37, 40, 29],
              targets: [.init(x:110,y:286), .init(x:168,y:317), .init(x:230,y:272), .init(x:296,y:284), .init(x:138,y:359), .init(x:203,y:376), .init(x:258,y:358)],
              angles: [20, 31, -12, 44, 11, -27, 58]),
        // Tense: source silhouette bounds; authored seven-body arrangement.
        .init(radii: [61, 38, 51, 31, 34, 42, 26],
              targets: [.init(x:106,y:273), .init(x:202,y:278), .init(x:279,y:315), .init(x:286,y:352), .init(x:115,y:350), .init(x:192,y:373), .init(x:205,y:326)],
              angles: [-20, 31, -12, 44, 11, -27, 58]),
    ]
    static func layout(for mood: MoodPetalBowlMood) -> MoodPetalBowlLayout { layouts[mood.rawValue] }
    static func radius(id: Int, mood: MoodPetalBowlMood) -> Double { layouts[mood.rawValue].radii[id] }
    static let spawns: [CGPoint] = [.init(x:104,y:151),.init(x:222,y:122),.init(x:270,y:160),
        .init(x:301,y:130),.init(x:151,y:145),.init(x:197,y:105),.init(x:245,y:135)]
    static let painterOrder = [0, 2, 1, 3, 4, 6, 5]
    static let contours: [[CGPoint]] = MoodPetalBowlMood.allCases.map(makeContour)
    private static func makeContour(_ mood: MoodPetalBowlMood) -> [CGPoint] {
        if mood == .tense {
            let vertices = (0..<24).map { i -> CGPoint in
                let a = Double(i) * .pi / 12 - .pi / 2, r = i.isMultiple(of: 2) ? 1.0 : 0.82
                return CGPoint(x: cos(a) * r, y: sin(a) * r)
            }
            var poly: [CGPoint] = []
            for i in vertices.indices {
                let v = vertices[i], before = vertices[(i + 23) % 24], after = vertices[(i + 1) % 24]
                func inset(_ p: CGPoint) -> CGPoint {
                    let length = hypot(p.x - v.x, p.y - v.y), d = min(3 / 43.0, length * 0.4)
                    return CGPoint(x: v.x + (p.x - v.x) * d / length, y: v.y + (p.y - v.y) * d / length)
                }
                let a = inset(before), b = inset(after)
                for j in 0..<12 {
                    let u = Double(j) / 11, s = 1 - u
                    poly.append(CGPoint(x: s * s * a.x + 2 * s * u * v.x + u * u * b.x,
                                        y: s * s * a.y + 2 * s * u * v.y + u * u * b.y))
                }
            }
            var lengths = [0.0]
            for i in 0..<poly.count { lengths.append(lengths.last! + hypot(poly[(i+1)%poly.count].x-poly[i].x, poly[(i+1)%poly.count].y-poly[i].y)) }
            return (0..<256).map { i in
                let at = lengths.last! * Double(i) / 256
                let k = min(poly.count - 1, max(0, (lengths.firstIndex { $0 > at } ?? poly.count) - 1))
                let q = (at - lengths[k]) / (lengths[k+1] - lengths[k]), a = poly[k], b = poly[(k+1)%poly.count]
                return CGPoint(x: a.x + (b.x-a.x)*q, y: a.y + (b.y-a.y)*q)
            }
        }
        return (0..<256).map { i in
            let a = Double(i) * 2 * .pi / 256
            let r: Double
            switch mood {
            case .calm: r = 0.80 + 0.20 * cos(4*a)
            case .low: r = 0.86 + 0.14 * cos(8*a)
            case .bright: r = 0.77 + 0.23 * cos(4*a)
            case .tense: r = 1
            }
            let phase = mood == .calm ? Double.pi / 4 : 0
            return CGPoint(x: cos(a + phase) * r, y: sin(a + phase) * r * (mood == .low ? 1.08 : 1))
        }
    }
    static func isContained(_ token: MoodPetalBowlToken) -> Bool {
        let a = token.angle * .pi / 180, c = cos(a), s = sin(a), r = radius(id: token.id, mood: token.mood)
        for p in contours[token.mood.rawValue] {
            let x = token.center.x + r * (c*p.x-s*p.y), y = token.center.y + r * (s*p.x+c*p.y)
            if y >= 273 && hypot(x-196.5,y-273) > 151.5 { return false }
        }
        return true
    }
    static func contained(from: MoodPetalBowlToken, desired: MoodPetalBowlToken) -> MoodPetalBowlToken {
        if from.center == desired.center && from.angle == desired.angle { return desired }
        if isContained(desired) { return desired }
        var low = 0.0, high = 1.0
        for _ in 0..<12 {
            let q = (low + high) / 2
            if isContained(interpolate(from, desired, q)) { low = q } else { high = q }
        }
        return interpolate(from, desired, low)
    }
    static func interpolate(_ a: MoodPetalBowlToken, _ b: MoodPetalBowlToken, _ q: Double) -> MoodPetalBowlToken {
        var p = a
        p.center = CGPoint(x: a.center.x + (b.center.x-a.center.x)*q, y: a.center.y + (b.center.y-a.center.y)*q)
        p.angle = a.angle + (b.angle-a.angle)*q; p.alpha = a.alpha + (b.alpha-a.alpha)*q
        return p
    }
}

enum MoodPetalBowlMath {
    static let loop = 8.0
    static let days = ["Monday", "Tuesday", "Wednesday", "Thursday", "Friday", "Saturday", "Sunday"]
    static let dateX: [Double] = [40, 92.1666666667, 144.3333333333, 196.5, 248.6666666667, 300.8333333333, 353]
    static let initialRecords: [MoodPetalBowlMood?] = [.bright,.low,.calm,.calm,nil,nil,nil]
    private typealias G = MoodPetalBowlGeometry
    static func clamp(_ x: Double, _ a: Double = 0, _ b: Double = 1) -> Double { min(b, max(a, x)) }
    static func smooth(_ x: Double) -> Double { let u = clamp(x); return u*u*(3-2*u) }
    static func ease(_ x: Double) -> Double { bezier(x, 0.2, 0.8, 0.2, 1) }
    static func io(_ x: Double) -> Double { bezier(x, 0.65, 0, 0.35, 1) }
    static func colors(records: [MoodPetalBowlMood?], day: Int, mood: MoodPetalBowlMood) -> [MoodPetalBowlRGBA] {
        records.enumerated().map { i, saved in .init(hex: i == day ? mood.hex : (saved?.hex ?? 0xE6E1D6)) }
    }
    static func settled(_ mood: MoodPetalBowlMood = .calm, day: Int = 3,
                        records: [MoodPetalBowlMood?] = initialRecords, poster: Bool = false) -> MoodPetalBowlScene {
        let layout = G.layout(for: mood)
        let tokens = (0..<7).map { i in
            MoodPetalBowlToken(id:i,mood:mood,center:layout.targets[i],angle:layout.angles[i],alpha:poster && i<4 ? 0:1,batchAge:2)
        }
        return .init(tokens:tokens,labelWeights:(0..<4).map{$0 == mood.rawValue ? 1:0},labelOffsets:[0,0,0,0],
                     capsuleX:dateX[day],dots:colors(records:records,day:day,mood:mood),selectedDay:day,captionAlpha:0,captionDay:day)
    }
    static func rest(records: [MoodPetalBowlMood?] = initialRecords) -> MoodPetalBowlScene { settled(records:records,poster:true) }
    static func contactTime(_ id: Int, mood: MoodPetalBowlMood) -> Double {
        (35 + sqrt(35*35 + 2*1750*(G.layout(for: mood).targets[id].y-G.spawns[id].y))) / 1750
    }
    static func flight(id: Int, mood: MoodPetalBowlMood, age: Double, bounce: Double) -> MoodPetalBowlToken {
        let layout = G.layout(for: mood)
        let s = max(0, age), t = contactTime(id, mood: mood), target = layout.targets[id], spawn = G.spawns[id]
        let sign = id.isMultiple(of:2) ? 1.0:-1.0, spin = sign*(65+9*Double(id))
        var p = MoodPetalBowlToken(id:id,mood:mood,center:spawn,angle:layout.angles[id]-spin*t,alpha:smooth(age/0.08),batchAge:age)
        if s < t {
            let u = s/t, q = 1-pow(1-u,3)
            p.center = CGPoint(x:spawn.x+(target.x-spawn.x)*q+sign*8*pow(sin(.pi*u),2),y:spawn.y-35*s+0.5*1750*s*s)
            p.angle += spin*s
        } else {
            let v = s-t, e = 1-smooth(v/bounce)
            p.center = CGPoint(x:target.x+sign*(2+0.4*Double(id))*exp(-8*v)*sin(13*v)*e,
                               y:target.y-(18+2*Double(id))*exp(-7*v)*abs(sin(14*v))*e)
            p.angle = layout.angles[id]+sign*24*exp(-7*v)*sin(14*v)*e
        }
        return p
    }
    static func authored(_ time: Double, records: [MoodPetalBowlMood?] = initialRecords) -> MoodPetalBowlScene {
        let t = max(0,time).truncatingRemainder(dividingBy:loop)
        var scene = rest(records:records)
        if t < 3.14 {
            let ids = [0,2,1,3]
            for (rank,id) in ids.enumerated() { scene.tokens[id] = flight(id:id,mood:.calm,age:t-1.10-Double(rank)*0.075,bounce:0.56) }
            var jostle = 0.0
            for (rank,id) in ids.enumerated() {
                let age = t-(1.10+Double(rank)*0.075+contactTime(id,mood:.calm))
                if age>0 && age<0.45 { jostle = max(jostle,pow(sin(.pi*age/0.45),2)) }
            }
            let impulses: [(Double,Double,Double)] = [(-3,-4,-4),(2,-6,5),(3,-3,-3)]
            for i in 4...6 { let d=impulses[i-4];scene.tokens[i].center.x += d.0*jostle;scene.tokens[i].center.y += d.1*jostle;scene.tokens[i].angle += d.2*jostle }
            if t>=2.82 { let q=ease((t-2.82)/0.30);for i in 0..<7 {scene.tokens[i].alpha *= 1-q;scene.tokens[i].center.y += 5*q} }
        } else if t < 5.94 {
            let ids=[0,2,1,4,3,5,6]
            for (rank,id) in ids.enumerated() {scene.tokens[id]=flight(id:id,mood:.bright,age:t-3.14-Double(rank)*0.085,bounce:0.56)}
            if t>=5.55 {let q=ease((t-5.55)/0.33);for i in 0..<7 {scene.tokens[i].alpha *= 1-q;scene.tokens[i].center.y += 5*q}}
        } else {
            for (rank,id) in [4,5,6].enumerated() {scene.tokens[id]=flight(id:id,mood:.calm,age:t-5.94-Double(rank)*0.070,bounce:0.56)}
        }
        if t>=2.94 && t<5.72 {
            let q=ease((t-2.94)/0.24)
            scene.labelWeights=[1-q,0,q,0];scene.labelOffsets=[12*q,0,-12*(1-q),0]
            scene.dots[3] = .mix(.init(hex:MoodPetalBowlMood.calm.hex),.init(hex:MoodPetalBowlMood.bright.hex),q)
        } else if t>=5.72 && t<7.25 {
            let q=ease((t-5.72)/0.24)
            scene.labelWeights=[q,0,1-q,0];scene.labelOffsets=[-12*(1-q),0,12*q,0]
            scene.dots[3] = .mix(.init(hex:MoodPetalBowlMood.bright.hex),.init(hex:MoodPetalBowlMood.calm.hex),q)
        }
        if t>=7.25 {return rest(records:records)}
        return scene
    }
    static func transition(from scene: MoodPetalBowlScene, mood: MoodPetalBowlMood, day: Int,
                           records: [MoodPetalBowlMood?], direction: Double, velocityX: Double = 0) -> MoodPetalBowlTransition {
        let outgoing=scene.tokens.map { p in var desired=p;desired.center.y += 5;return G.contained(from:p,desired:desired) }
        return .init(from:scene,mood:mood,day:day,dots:colors(records:records,day:day,mood:mood),
                     direction:direction,speedup:min(0.15,abs(velocityX)/6000),outgoing:outgoing)
    }
    static func transitioning(_ plan: MoodPetalBowlTransition, elapsed: Double) -> MoodPetalBowlScene {
        if elapsed<=0 {return plan.from}
        var s=plan.from
        let q=ease(elapsed/0.24)
        for i in 0..<4 {
            let target=i == plan.mood.rawValue
            s.labelWeights[i]=plan.from.labelWeights[i]*(1-q)+(target ? q:0)
            let start=target && plan.from.labelWeights[i]==0 ? -12*plan.direction:plan.from.labelOffsets[i]
            s.labelOffsets[i]=target ? start*(1-q):start+12*plan.direction*q
        }
        s.dots=zip(plan.from.dots,plan.dots).map{.mix($0.0,$0.1,q)}
        s.capsuleX=plan.from.capsuleX+(dateX[plan.day]-plan.from.capsuleX)*capsuleProgress(elapsed)
        s.selectedDay=plan.day;s.captionAlpha=plan.from.captionAlpha*(1-q)
        if elapsed<0.25 {
            let fade=ease(elapsed/0.25)
            s.tokens=zip(plan.from.tokens,plan.outgoing).map { a,b in var p=G.interpolate(a,b,fade);p.alpha=a.alpha*(1-fade);return p }
        } else {
            let ids=[0,2,1,4,3,5,6]
            for (rank,id) in ids.enumerated() {
                let delay=max(0,Double(rank)*0.070-Double(rank)*plan.speedup/6)
                s.tokens[id]=flight(id:id,mood:plan.mood,age:elapsed-0.25-delay,bounce:0.50)
            }
        }
        return s
    }
    static func stirring(from scene: MoodPetalBowlScene, anchor: CGPoint, translation: CGSize,
                         velocity: CGSize) -> MoodPetalBowlScene {
        if translation == .zero && velocity == .zero {return scene}
        let vx=clamp(velocity.width,-900,900),vy=clamp(velocity.height,-900,900)
        let x=28*tanh((translation.width+0.018*vx)/48),y=20*tanh((translation.height+0.018*vy)/48)
        let rotation=35*tanh(vx/900+translation.width/80)
        var result=scene
        for i in 0..<7 where scene.tokens[i].alpha>0 {
            let p=scene.tokens[i],distance=hypot(p.center.x-anchor.x,p.center.y-anchor.y)
            let w=pow(max(0,1-distance/110),2)
            var desired=p;desired.center.x += x*w;desired.center.y += y*w;desired.angle += rotation*w
            result.tokens[i]=G.contained(from:p,desired:desired)
        }
        return result
    }
    static func released(from scene: MoodPetalBowlScene, base: MoodPetalBowlScene,
                         elapsed: Double, cancelled: Bool) -> MoodPetalBowlScene {
        if elapsed<=0 {return scene}
        let duration=cancelled ? 0.24:0.70
        if elapsed>=duration {return base}
        let weight: Double
        if cancelled {weight=1-smooth(elapsed/duration)}
        else {weight=springRemaining(elapsed,response:0.48,damping:0.72)*(1-smooth(elapsed/duration))}
        var result=scene
        for i in 0..<7 {
            let proposed=G.interpolate(base.tokens[i],scene.tokens[i],weight)
            result.tokens[i]=G.contained(from:base.tokens[i],desired:proposed)
        }
        return result
    }
    static func recovering(from scene: MoodPetalBowlScene, elapsed: Double,
                           records: [MoodPetalBowlMood?]) -> MoodPetalBowlScene {
        if elapsed<=0 {return scene}
        let target=rest(records:records)
        if elapsed>=0.70 {return target}
        var s=scene;let q=io(elapsed/0.70)
        for i in 0..<4 {s.labelWeights[i] += (target.labelWeights[i]-s.labelWeights[i])*q;s.labelOffsets[i] *= 1-q}
        s.dots=zip(scene.dots,target.dots).map{.mix($0.0,$0.1,q)}
        s.capsuleX += (target.capsuleX-s.capsuleX)*q;s.selectedDay=3;s.captionAlpha *= 1-q
        if elapsed<0.20 {let fade=ease(elapsed/0.20);for i in 0..<7 {s.tokens[i].alpha *= 1-fade}}
        else {s.tokens=target.tokens;let fade=ease((elapsed-0.20)/0.50);for i in 0..<7 {s.tokens[i].alpha *= fade}}
        return s
    }
    private static func capsuleProgress(_ time: Double) -> Double {
        if time<=0{return 0};if time>=0.50{return 1}
        return 1-springRemaining(time,response:0.30,damping:0.85)*(1-smooth((time-0.35)/0.15))
    }
    static func springRemaining(_ time: Double, response: Double, damping: Double) -> Double {
        let omega=2*Double.pi/response,beta=omega*sqrt(1-damping*damping)
        return exp(-damping*omega*time)*(cos(beta*time)+damping*omega/beta*sin(beta*time))
    }
    private static func bezier(_ x: Double, _ x1: Double, _ y1: Double, _ x2: Double, _ y2: Double) -> Double {
        let x=clamp(x);if x==0 || x==1{return x}
        func f(_ t: Double,_ a: Double,_ b: Double)->Double {let s=1-t;return 3*s*s*t*a+3*s*t*t*b+t*t*t}
        var lo=0.0,hi=1.0
        for _ in 0..<26 {let m=(lo+hi)/2;if f(m,x1,x2)<x{lo=m}else{hi=m}}
        return f((lo+hi)/2,y1,y2)
    }
}
