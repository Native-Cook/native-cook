import Foundation
import CoreGraphics

struct VelvetNapStudioField: @unchecked Sendable {
    // Raw numerical RGBA8: manual brightness correction UInt16 little-endian, angle UInt8, coverage UInt8.
    // Never interpret this buffer as a color-managed image.
    let bytes: Data
    static let side = 64
    static let empty = VelvetNapStudioField(bytes: Data((0..<4096).flatMap { _ in [UInt8(0), 128, 0, 0] }))
    func value(_ index: Int) -> Double {
        let n = Int(bytes[index * 4]) | Int(bytes[index * 4 + 1]) << 8
        return n >= 32768 ? 1 + Double(n - 32768) / 32767 : 1 - Double(32768 - n) / 32768
    }
    func angle(_ index: Int) -> Double { Double(bytes[index * 4 + 2]) / 255 * 2 * .pi - .pi }
    static func encode(_ value: Double) -> UInt16 {
        let value = min(2, max(0, value))
        return UInt16((value >= 1 ? 32768 + (value - 1) * 32767 : 32768 - (1 - value) * 32768).rounded())
    }
}

struct VelvetNapStudioFrame {
    var time = 0.0
    var authorWeight = 1.0
    var fields: [VelvetNapStudioField] = []
    var weights: [Double] = []
    var strength = 1.0
    static let rest = VelvetNapStudioFrame(time: 0)
}

struct VelvetNapStudioStroke {
    var points: [CGPoint]
    var angle: Double
    var width: Double
}

enum VelvetNapStudioMath {
    static let loop = 8.0
    static func clamp(_ x: Double, _ lo: Double = 0, _ hi: Double = 1) -> Double { min(hi, max(lo, x)) }
    static func smooth(_ x: Double) -> Double { let u = clamp(x); return u * u * (3 - 2 * u) }
    static func segment(_ t: Double, _ a: Double, _ b: Double) -> Double { smooth((t - a) / (b - a)) }
    static func inverseSmooth(_ value:Double)->Double {0.5-sin(asin(1-2*clamp(value))/3)}
    static func wrap(_ angle: Double) -> Double { atan2(sin(angle), cos(angle)) }
    static func angle(from: Double, to: Double, mix: Double) -> Double { from + wrap(to - from) * clamp(mix) }
    static func hash(_ value: Int) -> Double { let x = sin(Double(value + 42) * 12.9898) * 43758.5453; return x - floor(x) }
    static func bezier(_ t: Double) -> CGPoint {
        let u = 1 - t
        return CGPoint(x: u*u*u*50 + 3*u*u*t*106 + 3*u*t*t*173 + t*t*t*244,
                       y: u*u*u*203 + 3*u*u*t*190 + 3*u*t*t*78 + t*t*t*65)
    }
    static func tangent(_ t: Double) -> CGPoint {
        let u = 1 - t
        let x = 3*u*u*56 + 6*u*t*67 + 3*t*t*71
        let y = 3*u*u * -13 + 6*u*t * -112 + 3*t*t * -13
        let l = hypot(x,y)
        return CGPoint(x:x/l,y:y/l)
    }
    static let path: [(point: CGPoint, arc: Double)] = {
        var result: [(CGPoint, Double)] = [(bezier(0), 0)]
        var length = 0.0
        for i in 1...128 {
            let point = bezier(Double(i)/128), previous = result[i-1].0
            length += hypot(point.x-previous.x, point.y-previous.y)
            result.append((point,length))
        }
        return result.map { ($0.0,$0.1/length) }
    }()
    static let pathLength = (1..<path.count).reduce(0.0) { value, i in
        value + hypot(path[i].point.x-path[i-1].point.x,path[i].point.y-path[i-1].point.y)
    }
    static func nearest(_ point: CGPoint) -> (s: Double, distance: Double, angle: Double) {
        var best = Double.greatestFiniteMagnitude, arc = 0.0, theta = 0.0
        for i in 1..<path.count {
            let a = path[i-1].point, b = path[i].point
            let x = b.x-a.x, y = b.y-a.y
            let h = clamp(((point.x-a.x)*x+(point.y-a.y)*y)/(x*x+y*y))
            let d = hypot(point.x-a.x-x*h,point.y-a.y-y*h)
            if d < best { best=d; arc=path[i-1].arc+(path[i].arc-path[i-1].arc)*h; theta=atan2(y,x) }
        }
        if arc<=0 {
            let a=path[0].point,t=tangent(0),along=(point.x-a.x)*t.x+(point.y-a.y)*t.y
            if along<0 {arc=along/pathLength}
        } else if arc>=1 {
            let a=path[path.count-1].point,t=tangent(1),along=(point.x-a.x)*t.x+(point.y-a.y)*t.y
            if along>0 {arc=1+along/pathLength}
        }
        return (arc,best,theta)
    }
    static func brightness(_ angle: Double) -> Double {
        1.03 + 0.45 * clamp((cos(angle)*0.8-sin(angle)*0.6)/0.85,-1,1)
    }
    static func authored(_ time: Double) -> VelvetNapStudioFrame {
        VelvetNapStudioFrame(time: max(0,time).truncatingRemainder(dividingBy:loop))
    }
    static func fanDelay(_ point:CGPoint)->Double {
        var best=Double.greatestFiniteMagnitude,delay=0.0
        for k in 0..<21 {
            let d=hypot(point.x-(22+263*hash(2*k)),point.y-(36+194*hash(2*k+1)))
            if d<best {best=d;delay=Double(k%5)*0.04}
        }
        return delay
    }
    static let grid: [(s:Double,distance:Double,angle:Double,delay:Double)] = {
        (0..<4096).map { i in
            let point=CGPoint(x:Double(i%64)*307/63,y:Double(i/64)*274/63),p=nearest(point)
            return (p.s,p.distance,p.angle,fanDelay(point))
        }
    }()
    static func authoredValue(_ point: CGPoint, time: Double) -> Double {
        let p=nearest(point)
        return authoredSample(s:p.s,distance:p.distance,angle:p.angle,delay:fanDelay(point),time:time)
    }
    static func authoredSample(s:Double,distance:Double,angle:Double,delay:Double,time:Double)->Double {
        let t = time.truncatingRemainder(dividingBy:loop)
        if t <= 1 || t >= 7.55 { return 1 }
        let taper = smooth((s*pathLength+16)/16)*smooth(((1-s)*pathLength+16)/16)
        let band = (1-segment(distance,20,33))*taper
        let reverseBand = (1-segment(distance,21,34))*taper
        // A round head reaches an off-axis point at the circular chord, not on
        // a straight isochronal cross-section. Keep the90ms local response.
        let forwardLead=sqrt(max(0,33*33-distance*distance))/pathLength
        let reverseLead=sqrt(max(0,34*34-distance*distance))/pathLength
        let forwardArrival=1+1.76*inverseSmooth(s-forwardLead)
        let reverseArrival=3+1.76*inverseSmooth(1-s-reverseLead)
        let forward=segment(t,forwardArrival,forwardArrival+0.09)
        let backward=segment(t,reverseArrival,reverseArrival+0.09)
        let relaxation = 1-segment(t,5.75+delay,7.55)
        let bright = (brightness(angle)-1)*band*forward
        let dark = (brightness(angle + .pi)-1)*reverseBand*backward
        return 1 + (bright*(1-backward) + dark)*relaxation
    }
    static func value(_ frame: VelvetNapStudioFrame, point: CGPoint, index: Int) -> Double {
        let p=grid[index]
        var delta = (authoredSample(s:p.s,distance:p.distance,angle:p.angle,delay:p.delay,time:frame.time)-1)*frame.authorWeight
        for i in frame.fields.indices { delta += (frame.fields[i].value(index)-1)*frame.weights[i] }
        return 1+delta*frame.strength
    }
    static func baked(_ frame: VelvetNapStudioFrame) -> VelvetNapStudioField {
        var data = Data(count:64*64*4)
        for y in 0..<64 { for x in 0..<64 {
            let index = y*64+x, point = CGPoint(x:Double(x)*307/63,y:Double(y)*274/63)
            let n = VelvetNapStudioField.encode(value(frame,point:point,index:index))
            data[index*4]=UInt8(n & 255); data[index*4+1]=UInt8(n >> 8)
            data[index*4+2]=0; data[index*4+3]=255
        }}
        return .init(bytes:data)
    }
    static func manualBaked(_ frame:VelvetNapStudioFrame)->VelvetNapStudioField {
        // Preserve the analytic front as a separate baseline. Never quantize that baseline
        // into a manual snapshot: untouched pixels must remain unchanged after Down.
        var manual=frame;manual.authorWeight=0
        return baked(manual)
    }
    static func coalesced(_ frame:VelvetNapStudioFrame)->VelvetNapStudioFrame {
        guard frame.fields.count>=3 else {return frame}
        // Retain the analytic authored front, including its sub-grid feather, exactly.
        // Only linear manual layers are baked into the bounded numerical snapshot.
        let manual=VelvetNapStudioFrame(authorWeight:0,fields:frame.fields,weights:frame.weights,strength:frame.strength)
        return .init(time:frame.time,authorWeight:frame.authorWeight*frame.strength,
                     fields:[baked(manual)],weights:[1],strength:1)
    }
    static func distance(_ p: CGPoint, _ a: CGPoint, _ b: CGPoint) -> Double {
        let dx=b.x-a.x,dy=b.y-a.y,l=dx*dx+dy*dy
        let u=l>0 ? clamp(((p.x-a.x)*dx+(p.y-a.y)*dy)/l):0
        return hypot(p.x-a.x-dx*u,p.y-a.y-dy*u)
    }
    static func stamped(_ field: VelvetNapStudioField, from: CGPoint, to: CGPoint, angle: Double, width: Double, baseline:VelvetNapStudioFrame = .rest) -> VelvetNapStudioField {
        var bytes=field.bytes
        for y in 0..<64 { for x in 0..<64 {
            let i=y*64+x, point=CGPoint(x:Double(x)*307/63,y:Double(y)*274/63)
            let d=distance(point,from,to), r=width/2
            let influence=1-segment(d,max(0,r-7),r+4)
            guard influence>0 else { continue }
            let path=grid[i]
            let authoredDelta=(authoredSample(s:path.s,distance:path.distance,angle:path.angle,delay:path.delay,time:baseline.time)-1)*baseline.authorWeight*baseline.strength
            let current=field.value(i)+authoredDelta
            let ratio=field.value(i)+(brightness(angle)-current)*influence
            let n=VelvetNapStudioField.encode(ratio)
            bytes[i*4]=UInt8(n & 255); bytes[i*4+1]=UInt8(n >> 8)
            let old=field.angle(i), theta=self.angle(from:old,to:angle,mix:influence)
            bytes[i*4+2]=UInt8((clamp((wrap(theta) + .pi)/(2 * .pi))*255).rounded())
            bytes[i*4+3]=UInt8((max(Double(bytes[i*4+3])/255,influence)*255).rounded())
        }}
        return .init(bytes:bytes)
    }
    static func mixed(from: VelvetNapStudioFrame, to field: VelvetNapStudioField, amount: Double) -> VelvetNapStudioFrame {
        let amount=clamp(amount)
        if amount==0 { return from }
        if amount==1 { return .init(time:from.time,authorWeight:from.authorWeight*from.strength,fields:[field],weights:[1]) }
        var result=from
        // Input events coalesce a previous three-layer capture before starting another stamp.
        // No field allocation or CPU rasterization is performed by this per-frame function.
        result.authorWeight *= from.strength
        result.weights=result.weights.map { $0*(1-amount)*from.strength }
        result.fields.append(field);result.weights.append(amount);result.strength=1
        return result
    }
    static func recovering(_ frame: VelvetNapStudioFrame, elapsed: Double) -> VelvetNapStudioFrame {
        if elapsed>=0.6 { return .rest }
        var frame=frame;frame.strength *= 1-smooth(elapsed/0.6);return frame
    }
}
