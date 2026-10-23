import Foundation
import CoreGraphics

struct LenticularEditionPassPose: Equatable {
    var yaw: Double
    var pitch: Double
    var roll: Double
    static let zero = Self(yaw: 0, pitch: 0, roll: 0)
    static let rest = Self(yaw: -9, pitch: 3, roll: -2)
    static func + (a: Self, b: Self) -> Self { .init(yaw: a.yaw+b.yaw, pitch: a.pitch+b.pitch, roll: a.roll+b.roll) }
    static func - (a: Self, b: Self) -> Self { .init(yaw: a.yaw-b.yaw, pitch: a.pitch-b.pitch, roll: a.roll-b.roll) }
    static func * (a: Self, b: Double) -> Self { .init(yaw: a.yaw*b, pitch: a.pitch*b, roll: a.roll*b) }
}
struct LenticularEditionPassFrame: Equatable {
    var pose: LenticularEditionPassPose
    var angularVelocity: LenticularEditionPassPose = .zero
    var explicitViewB: Double? = nil
    var collectionMix: Double = 0
}

enum LenticularEditionPassGeometry {
    static let width = 333.0
    static let height = 211.0
    static let focal = 800.0
    static let center = CGPoint(x: 196.5, y: 366.5)
    static let bounds = CGRect(x: 21.5, y: 242.5, width: 350, height: 265)
    static let restPose = LenticularEditionPassPose.rest
    static let localCorners = [CGPoint(x: -166.5, y: -105.5), CGPoint(x: 166.5, y: -105.5),
                               CGPoint(x: 166.5, y: 105.5), CGPoint(x: -166.5, y: 105.5)]
    static let ridgeTerms: [Double] = (0..<230).map { j in
        let u = (Double(j)+0.5)*1.45-166.5
        return 0.035*u+0.8*sin(2*Double.pi*u/333)
    }
    /// Row-major Rz * Rx * Ry; local x right, y down, z toward the viewer.
    static func rotation(_ pose: LenticularEditionPassPose) -> [Double] {
        let y=pose.yaw * .pi/180, p=pose.pitch * .pi/180, r=pose.roll * .pi/180
        let cy=cos(y),sy=sin(y),cp=cos(p),sp=sin(p),cr=cos(r),sr=sin(r)
        return [cr*cy-sr*sp*sy, -sr*cp, cr*sy+sr*sp*cy,
                sr*cy+cr*sp*sy, cr*cp, sr*sy-cr*sp*cy,
                -cp*sy, sp, cp*cy]
    }
    static func bow(_ point: CGPoint) -> Double {
        3.5*(1-pow(2*point.x/width,2))*(1-pow(2*point.y/height,2))
    }
    static func project(local: CGPoint, pose: LenticularEditionPassPose, depth: Double? = nil) -> CGPoint {
        let m=rotation(pose),z=depth ?? bow(local)
        let x=m[0]*local.x+m[1]*local.y+m[2]*z
        let y=m[3]*local.x+m[4]*local.y+m[5]*z
        let worldZ=m[6]*local.x+m[7]*local.y+m[8]*z
        let scale=focal/(focal-worldZ)
        return CGPoint(x:center.x+x*scale,y:center.y+y*scale)
    }
    static func corners(pose: LenticularEditionPassPose) -> [CGPoint] {
        localCorners.map { project(local:$0,pose:pose,depth:0) }
    }
    static func valid(_ pose: LenticularEditionPassPose) -> Bool {
        corners(pose:pose).allSatisfy { p in
            p.x.isFinite && p.y.isFinite && p.x>=bounds.minX && p.x<=bounds.maxX && p.y>=bounds.minY && p.y<=bounds.maxY
        }
    }
    static func constrain(_ desired: LenticularEditionPassPose) -> LenticularEditionPassPose {
        if valid(desired) { return desired }
        var low=0.0,high=1.0
        for _ in 0..<12 {
            let q=(low+high)/2
            if valid(restPose+(desired-restPose)*q) { low=q } else { high=q }
        }
        return restPose+(desired-restPose)*low
    }
    static func actualVelocity(desired: LenticularEditionPassPose, velocity: LenticularEditionPassPose) -> LenticularEditionPassPose {
        if valid(desired) { return velocity }
        // Directional derivative of the same bounded pose, never hidden intent.
        let h=0.0001
        return (constrain(desired+velocity*h)-constrain(desired-velocity*h))*(1/(2*h))
    }
    static func inverseHit(screen: CGPoint, pose: LenticularEditionPassPose) -> CGPoint? {
        let m=rotation(pose),px=(screen.x-center.x)/focal,py=(screen.y-center.y)/focal
        // Inverse rotation is the transpose of the shared forward matrix.
        let c=(m[6]*focal,m[7]*focal,m[8]*focal)
        let d=(m[0]*px+m[3]*py-m[6],m[1]*px+m[4]*py-m[7],m[2]*px+m[5]*py-m[8])
        guard abs(d.2)>0.0001 else { return nil }
        let plane = -c.2/d.2
        var t=plane
        for _ in 0..<6 {
            let x=c.0+t*d.0,y=c.1+t*d.1,z=c.2+t*d.2
            let a=1-pow(2*x/width,2),b=1-pow(2*y/height,2)
            let surface=3.5*a*b
            let bx = -28*x/(width*width)*b, by = -28*y/(height*height)*a
            let derivative=d.2-bx*d.0-by*d.1
            guard abs(derivative)>0.0001 else { return nil }
            t=min(plane+8,max(plane-8,t-(z-surface)/derivative))
        }
        let point=CGPoint(x:c.0+t*d.0,y:c.1+t*d.1)
        let qx=abs(point.x)-(width/2-6),qy=abs(point.y)-(height/2-6)
        let distance=hypot(max(qx,0),max(qy,0))+min(max(qx,qy),0)-6
        return t>0 && distance<=0 ? point:nil
    }
    static func ridgeWeight(index: Int, frame: LenticularEditionPassFrame) -> Double {
        if let override=frame.explicitViewB { return LenticularEditionPassMath.clamp(override) }
        return LenticularEditionPassMath.smooth((frame.pose.yaw+ridgeTerms[index]+0.7)/1.4)
    }
    static func meanViewB(_ frame: LenticularEditionPassFrame) -> Double {
        if let override=frame.explicitViewB { return LenticularEditionPassMath.clamp(override) }
        return ridgeTerms.reduce(0) { $0+LenticularEditionPassMath.smooth((frame.pose.yaw+$1+0.7)/1.4) }/230
    }
}

struct LenticularEditionPassSupportSample {
    var latent: Double
    var latentVelocity: Double
    var angle: Double
    var velocity: Double
    static let zero = Self(latent:0,latentVelocity:0,angle:0,velocity:0)
}
/// Exact cubic-forced latent ODE. No frame accumulation or wall-clock state.
struct LenticularEditionPassVelocitySupport {
    var q0: Double = 0
    var dq0: Double = 0
    var k: Double = 0
    private func forced(_ a: Double) -> (Double,Double) {
        let omega=2*Double.pi/0.4,c=0.78*omega,beta=omega*sqrt(1-0.78*0.78),w2=omega*omega,A=0.12
        let b3=2*k/(A*A*A),b2 = -3*k/(A*A)-6*c*b3/w2
        let b1 = -(4*c*b2+6*b3)/w2,b0=k-(2*c*b1+2*b2)/w2
        let C=q0-b0,D=(dq0-b1+c*C)/beta,e=exp(-c*a),co=cos(beta*a),si=sin(beta*a)
        let q=((b3*a+b2)*a+b1)*a+b0+e*(C*co+D*si)
        let v=(3*b3*a+2*b2)*a+b1+e*((-c*C+beta*D)*co+(-c*D-beta*C)*si)
        return (q,v)
    }
    func sample(_ age: Double) -> LenticularEditionPassSupportSample {
        if age>=0.60 { return .zero }
        let q:Double,v:Double
        if age<=0 { q=q0;v=dq0 }
        else if age<=0.12 { (q,v)=forced(age) }
        else {
            let start=forced(0.12),s=age-0.12,omega=2*Double.pi/0.4,c=0.78*omega
            let beta=omega*sqrt(1-0.78*0.78),C=start.0,D=(start.1+c*C)/beta,e=exp(-c*s),co=cos(beta*s),si=sin(beta*s)
            q=e*(C*co+D*si);v=e*((-c*C+beta*D)*co+(-c*D-beta*C)*si)
        }
        let u=LenticularEditionPassMath.clamp((age-0.40)/0.20)
        let envelope=1-u*u*(3-2*u),rate=age>0.40 ? -6*u*(1-u)/0.20:0
        let latent=q*envelope,derivative=v*envelope+q*rate,t=tanh(latent)
        return .init(latent:latent,latentVelocity:derivative,angle:2*t,velocity:2*(1-t*t)*derivative)
    }
    func retarget(velocity: Double, elapsed: Double) -> Self {
        let current=sample(elapsed)
        return .init(q0:current.latent,dq0:current.latentVelocity,k:LenticularEditionPassMath.clamp(velocity,-1200,1200)/600)
    }
}

enum LenticularEditionPassMath {
    static let loop = 8.0
    static func clamp(_ x:Double,_ low:Double=0,_ high:Double=1)->Double { min(high,max(low,x)) }
    static func smooth(_ x:Double)->Double { let q=clamp(x);return q*q*(3-2*q) }
    static func smoothRate(_ x:Double)->Double { x>0 && x<1 ? 6*x*(1-x):0 }
    static func ease(_ x:Double)->Double { curve(x,0.2,0.8,0.2,1).0 }
    static func io(_ x:Double)->Double { curve(x,0.65,0,0.35,1).0 }
    static func frame(desired: LenticularEditionPassPose, velocity: LenticularEditionPassPose = .zero,
                      explicitViewB:Double?=nil,collectionMix:Double=0)->LenticularEditionPassFrame {
        .init(pose:LenticularEditionPassGeometry.constrain(desired),
              angularVelocity:LenticularEditionPassGeometry.actualVelocity(desired:desired,velocity:velocity),
              explicitViewB:explicitViewB,collectionMix:collectionMix)
    }
    static func rest()->LenticularEditionPassFrame { .init(pose:.rest) }
    static func authored(_ time:Double)->LenticularEditionPassFrame {
        let t=max(0,time).truncatingRemainder(dividingBy:loop)
        if t<=1.10 || t>=7.26 { return rest() }
        func segment(_ a:Double,_ b:Double,_ start:LenticularEditionPassPose,_ end:LenticularEditionPassPose,_ anticipation:Bool=false)->LenticularEditionPassFrame {
            let q=anticipation ? curve((t-a)/(b-a),0.2,0.8,0.2,1):curve((t-a)/(b-a),0.65,0,0.35,1)
            return frame(desired:start+(end-start)*q.0,velocity:(end-start)*(q.1/(b-a)))
        }
        let anticipated=LenticularEditionPassPose(yaw:-10.5,pitch:3,roll:-2)
        let b=LenticularEditionPassPose(yaw:14,pitch:-4,roll:2),a=LenticularEditionPassPose(yaw:-14,pitch:6,roll:-3)
        if t<1.23 { return segment(1.10,1.23,.rest,anticipated,true) }
        if t<2.80 { return segment(1.23,2.80,anticipated,b) }
        if t<3.38 { return frame(desired:b) }
        if t<5.16 { return segment(3.38,5.16,b,a) }
        if t<5.70 { return frame(desired:a) }
        if t<7.08 { return segment(5.70,7.08,a,.init(yaw:-9.35,pitch:3,roll:-2)) }
        let s=finiteSpring(start:-9.35,velocity:0,target:-9,time:t-7.08,response:0.22,damping:0.86,end:0.18,tailStart:0)
        return frame(desired:.init(yaw:s.0,pitch:3,roll:-2),velocity:.init(yaw:s.1,pitch:0,roll:0))
    }
    static func rubber(_ x:Double,limit:Double)->Double {
        if abs(x)<=limit { return x }
        let over=abs(x)-limit
        return (x<0 ? -1:1)*(limit+over*0.35/(1+over/8))
    }
    static func rubberRate(_ x:Double,limit:Double)->Double {
        abs(x)<=limit ? 1:0.35/pow(1+(abs(x)-limit)/8,2)
    }
    private static func dragAxis(_ value:Double,_ delta:Double,_ limit:Double)->Double {
        rubber(value+delta,limit:limit)+(value-rubber(value,limit:limit))*(1-smooth(abs(delta)/8))
    }
    private static func dragAxisRate(_ value:Double,_ delta:Double,_ limit:Double)->Double {
        let direction=delta==0 ? 0.0:(delta<0 ? -1.0:1.0)
        return rubberRate(value+delta,limit:limit)-(value-rubber(value,limit:limit))*smoothRate(abs(delta)/8)*direction/8
    }
    private static func dragDesired(from base:LenticularEditionPassFrame,translation:CGSize,
                                    yawExtra:Double,pitchExtra:Double)->LenticularEditionPassPose {
        .init(yaw:dragAxis(base.pose.yaw,0.13*translation.width,18)+yawExtra,
              pitch:dragAxis(base.pose.pitch,-0.08*translation.height,10)+pitchExtra,
              roll:dragAxis(base.pose.roll,0.02*translation.width,4))
    }
    static func dragged(from base:LenticularEditionPassFrame,translation:CGSize,
                        yawSupport:LenticularEditionPassVelocitySupport,pitchSupport:LenticularEditionPassVelocitySupport,
                        supportAge:Double)->LenticularEditionPassFrame {
        let sy=yawSupport.sample(supportAge),sp=pitchSupport.sample(supportAge)
        // Capture compensation vanishes smoothly after eight degrees of travel.
        // At zero travel an already over-limit captured pose remains exact.
        let desired=dragDesired(from:base,translation:translation,yawExtra:sy.angle,pitchExtra:sp.angle)
        return frame(desired:desired,velocity:.init(yaw:sy.velocity,pitch:sp.velocity,roll:0),
                     explicitViewB:base.explicitViewB,collectionMix:base.collectionMix)
    }
    static func releaseVelocity(base:LenticularEditionPassFrame,translation:CGSize,pointer:CGSize,
                                candidateAge:Double,inherited:LenticularEditionPassPose?,
                                yawSupport:LenticularEditionPassVelocitySupport,pitchSupport:LenticularEditionPassVelocitySupport,
                                supportAge:Double)->LenticularEditionPassPose {
        let age=1-smooth(candidateAge/0.12),sy=yawSupport.sample(supportAge),sp=pitchSupport.sample(supportAge)
        var user=inherited ?? .init(yaw:0.13*clamp(pointer.width,-1200,1200)*dragAxisRate(base.pose.yaw,0.13*translation.width,18),
            pitch:-0.08*clamp(pointer.height,-1200,1200)*dragAxisRate(base.pose.pitch,-0.08*translation.height,10),
            roll:0.02*clamp(pointer.width,-1200,1200)*dragAxisRate(base.pose.roll,0.02*translation.width,4))
        user=user*age
        let candidate=user + .init(yaw:sy.velocity,pitch:sp.velocity,roll:0)
        let desired=dragDesired(from:base,translation:translation,yawExtra:sy.angle,pitchExtra:sp.angle)
        return LenticularEditionPassGeometry.actualVelocity(desired:desired,velocity:candidate)
    }
    static func targetView(from frame:LenticularEditionPassFrame,pointer:CGSize)->Int {
        let requested=frame.pose + .init(yaw:0.13*clamp(pointer.width,-1200,1200)*0.08,
            pitch:-0.08*clamp(pointer.height,-1200,1200)*0.08,roll:0.02*clamp(pointer.width,-1200,1200)*0.08)
        let projected=LenticularEditionPassGeometry.constrain(requested)
        if projected.yaw>0 { return 1 };if projected.yaw<0 { return 0 }
        return LenticularEditionPassGeometry.meanViewB(frame)>0.5 ? 1:0
    }
    static func released(from:LenticularEditionPassFrame,view:Int,elapsed:Double)->LenticularEditionPassFrame {
        if elapsed<=0 { return from }
        let target=LenticularEditionPassPose(yaw:view==0 ? -12:12,pitch:3,roll:-2)
        if elapsed>=0.75 { return frame(desired:target,explicitViewB:from.explicitViewB,collectionMix:from.collectionMix) }
        let y=finiteSpring(start:from.pose.yaw,velocity:from.angularVelocity.yaw,target:target.yaw,time:elapsed,response:0.45,damping:0.82,end:0.75,tailStart:0.55)
        let p=finiteSpring(start:from.pose.pitch,velocity:from.angularVelocity.pitch,target:target.pitch,time:elapsed,response:0.45,damping:0.82,end:0.75,tailStart:0.55)
        let r=finiteSpring(start:from.pose.roll,velocity:from.angularVelocity.roll,target:target.roll,time:elapsed,response:0.45,damping:0.82,end:0.75,tailStart:0.55)
        return frame(desired:.init(yaw:y.0,pitch:p.0,roll:r.0),velocity:.init(yaw:y.1,pitch:p.1,roll:r.1),
                     explicitViewB:from.explicitViewB,collectionMix:from.collectionMix)
    }
    static func cancelled(from:LenticularEditionPassFrame,base:LenticularEditionPassFrame,elapsed:Double)->LenticularEditionPassFrame {
        if elapsed<=0 { return from };if elapsed>=0.24 { var s=base;s.angularVelocity = .zero;return s }
        let u=elapsed/0.24,q=smooth(u),rate=smoothRate(u)/0.24,delta=base.pose-from.pose
        return frame(desired:from.pose+delta*q,velocity:delta*rate,explicitViewB:from.explicitViewB,collectionMix:from.collectionMix)
    }
    static func recovering(from:LenticularEditionPassFrame,elapsed:Double)->LenticularEditionPassFrame {
        if elapsed<=0 { return from };if elapsed>=0.65 { return rest() }
        let q=curve(elapsed/0.65,0.65,0,0.35,1),delta=LenticularEditionPassPose.rest-from.pose
        return frame(desired:from.pose+delta*q.0,velocity:delta*(q.1/0.65),
            explicitViewB:from.explicitViewB.map{$0*(1-q.0)},collectionMix:from.collectionMix*(1-q.0))
    }
    static func collecting(from:LenticularEditionPassFrame,elapsed:Double)->LenticularEditionPassFrame {
        if elapsed<=0 { return from }
        var s=from;s.angularVelocity = .zero;s.collectionMix += (1-s.collectionMix)*ease(elapsed/0.18);return s
    }
    static func reduced(from:LenticularEditionPassFrame,view:Double,elapsed:Double)->LenticularEditionPassFrame {
        let q=ease(elapsed/0.18)
        return .init(pose:.zero,explicitViewB:(from.explicitViewB ?? 0)+(view-(from.explicitViewB ?? 0))*q,
                     collectionMix:from.collectionMix)
    }
    static func finiteSpring(start:Double,velocity:Double,target:Double,time:Double,response:Double,damping:Double,end:Double,tailStart:Double)->(Double,Double) {
        if time<=0 { return (start,velocity) };if time>=end { return (target,0) }
        let omega=2*Double.pi/response,c=damping*omega,beta=omega*sqrt(1-damping*damping),C=start-target,D=(velocity+c*C)/beta
        let e=exp(-c*time),co=cos(beta*time),si=sin(beta*time),raw=e*(C*co+D*si)
        let v=e*((-c*C+beta*D)*co+(-c*D-beta*C)*si),u=clamp((time-tailStart)/(end-tailStart))
        let g=1-smooth(u),dg = -smoothRate(u)/(end-tailStart)
        return (target+raw*g,v*g+raw*dg)
    }
    private static func curve(_ x:Double,_ x1:Double,_ y1:Double,_ x2:Double,_ y2:Double)->(Double,Double) {
        func value(_ t:Double,_ a:Double,_ b:Double)->Double { let s=1-t;return 3*s*s*t*a+3*s*t*t*b+t*t*t }
        func rate(_ t:Double,_ a:Double,_ b:Double)->Double { 3*pow(1-t,2)*a+6*(1-t)*t*(b-a)+3*t*t*(1-b) }
        if x<=0 { return (0,y1/x1) };if x>=1 { return (1,(1-y2)/(1-x2)) }
        var lo=0.0,hi=1.0
        for _ in 0..<26 { let m=(lo+hi)/2;if value(m,x1,x2)<x {lo=m}else{hi=m} }
        let t=(lo+hi)/2
        return (value(t,y1,y2),rate(t,y1,y2)/rate(t,x1,x2))
    }
}
