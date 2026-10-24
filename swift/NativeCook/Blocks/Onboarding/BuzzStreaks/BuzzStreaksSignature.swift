import SwiftUI

enum BuzzStreaksMotionProfile { case approved, signature }

/// Additive choreography sampled from the scene clock; no independent animation state.
final class BuzzStreaksSignature {
    private struct Relay: Decodable { let start:Double; let end:Double; let points:[[Double]] }
    private struct Lock: Decodable { let start:Double; let end:Double; let ids:[String]; let x:Double; let y:Double; let reflectionAlpha:Double }
    private struct Portal: Decodable { let id:String; let start:Double; let end:Double; let points:[[Double]]; let lipX:Double }
    private struct Domino: Decodable { let start:Double; let stagger:Double; let duration:Double; let translation:Double; let rotation:Double; let scale:Double }
    private struct Packet:Decodable {let headRadius:Double;let coreRadius:Double;let trailLength:Double;let trailWidth:Double;let alpha:Double;let edgeFade:Double}
    private struct Lip:Decodable {let length:Double;let width:Double;let alpha:Double;let duration:Double}
    private struct Data: Decodable {
        let packet:Packet;let lip:Lip;let reflectionRadius:Double
        let easing:[String:[Double]]; let contact:[String:[Double]]; let relay:[Relay]
        let reflection:[String:[Double]]; let gaze:[String:[Double]]
        let rewardLock:Lock; let portal:[Portal]; let badgeReveal:[Double]; let domino:Domino
    }
    private let data:Data
    private let trail:Path
    private let head:Path
    private let core:Path
    private let lipPath:Path
    var reflectionRadius:Double {data.reflectionRadius}

    init() {
        guard let url=Bundle.main.url(forResource:"BuzzStreaks-signature",withExtension:"json"),
              let bytes=try? Foundation.Data(contentsOf:url),let decoded=try? JSONDecoder().decode(Data.self,from:bytes)
        else {preconditionFailure("BuzzStreaks requires its shared Signature cue contract.")}
        data=decoded
        let p=decoded.packet,l=decoded.lip
        trail=Path(roundedRect:CGRect(x:-p.trailLength,y:-p.trailWidth/2,width:p.trailLength,height:p.trailWidth),cornerRadius:p.trailWidth/2)
        head=Path(ellipseIn:CGRect(x:-p.headRadius,y:-p.headRadius,width:2*p.headRadius,height:2*p.headRadius))
        core=Path(ellipseIn:CGRect(x:-p.coreRadius,y:-p.coreRadius,width:2*p.coreRadius,height:2*p.coreRadius))
        lipPath=Path(roundedRect:CGRect(x:-l.length/2,y:0.7-l.width/2,width:l.length,height:l.width),cornerRadius:l.width/2)
    }
    private func progress(_ t:Double,_ start:Double,_ end:Double)->Double? {
        guard t>=start,t<end else {return nil};return (t-start)/(end-start)
    }
    private func ease(_ p:Double,_ name:String)->Double {
        let rows=data.easing[name]!,f=min(1,max(0,p))*Double(rows.count-1),i=Int(f),j=min(rows.count-1,i+1)
        return rows[i]+(rows[j]-rows[i])*(f-Double(i))
    }
    private func cubic(_ points:[[Double]],_ p:Double)->CGPoint {
        let a=1-p,b=a*a*a,c=3*a*a*p,d=3*a*p*p,e=p*p*p
        return CGPoint(x:b*points[0][0]+c*points[1][0]+d*points[2][0]+e*points[3][0],
                       y:b*points[0][1]+c*points[1][1]+d*points[2][1]+e*points[3][1])
    }
    func inset(_ kind:String,_ t:Double)->Double {
        guard let c=data.contact[kind],t>=c[0],t<c[2] else {return 0}
        return c[3]*(t<c[1] ? ease((t-c[0])/(c[1]-c[0]),"contact") : 1-ease((t-c[1])/(c[2]-c[1]),"contact"))
    }
    func reflection(_ kind:String,_ t:Double)->Double {
        guard let c=data.reflection[kind],let p=progress(t,c[0],c[1]) else {return 0}
        return c[2]*sin(.pi*p)
    }
    func gaze(_ kind:String,_ t:Double)->CGPoint {
        guard let c=data.gaze[kind],let p=progress(t,c[0],c[1]) else {return .zero}
        let w=sin(.pi*p);return CGPoint(x:c[2]*w,y:c[3]*w)
    }
    func drawRelay(_ t:Double,context:inout GraphicsContext) {
        for cue in data.relay {
            guard let raw=progress(t,cue.start,cue.end) else {continue}
            let p=ease(raw,"travel"),position=cubic(cue.points,p),a=1-p
            let dx=3*a*a*(cue.points[1][0]-cue.points[0][0])+6*a*p*(cue.points[2][0]-cue.points[1][0])+3*p*p*(cue.points[3][0]-cue.points[2][0])
            let dy=3*a*a*(cue.points[1][1]-cue.points[0][1])+6*a*p*(cue.points[2][1]-cue.points[1][1])+3*p*p*(cue.points[3][1]-cue.points[2][1])
            var c=context
            c.opacity=data.packet.alpha*min(1,min((t-cue.start)/data.packet.edgeFade,(cue.end-t)/data.packet.edgeFade))
            c.translateBy(x:position.x,y:position.y);c.rotate(by:.radians(atan2(dy,dx)))
            c.fill(trail,with:.linearGradient(Gradient(colors:[.clear,Color(red:0.88,green:0.96,blue:0.25)]),startPoint:CGPoint(x:-data.packet.trailLength,y:0),endPoint:.zero))
            c.fill(head,with:.color(Color(red:0.92,green:1,blue:0.36)))
            c.fill(core,with:.color(.white))
        }
    }
    func coin(_ id:String,_ t:Double,_ original:[Double])->[Double] {
        var value=original
        for cue in data.portal where cue.id==id {
            if let p=progress(t,cue.start,cue.end) {
                let point=cubic(cue.points,ease(p,"travel"));value[0]=point.x;value[1]=point.y
            }
        }
        let lock=data.rewardLock
        if let index=lock.ids.firstIndex(of:id),let p=progress(t,lock.start,lock.end) {
            let w=sin(2 * .pi*p)*pow(1-p,2)
            value[0]+=(index==0 ? lock.x : -lock.x)*w;value[1]+=lock.y*w
        }
        return value
    }
    func coinGloss(_ id:String,_ t:Double)->Double {
        let c=data.rewardLock
        guard c.ids.contains(id),let p=progress(t,c.start,c.end) else {return 0}
        return c.reflectionAlpha*sin(.pi*p)
    }
    func badgeGate(_ id:String,_ t:Double)->Double {
        guard id=="upToDay15" else {return 1}
        return ease((t-data.badgeReveal[0])/(data.badgeReveal[1]-data.badgeReveal[0]),"contact")
    }
    func domino(_ day:Int,_ t:Double)->(y:Double,rotation:Double,scale:Double) {
        let c=data.domino,start=c.start+Double(day-5)*c.stagger
        guard (5...7).contains(day),let p=progress(t,start,start+c.duration) else {return (0,0,1)}
        let w=sin(.pi*p);return (-c.translation*w,-c.rotation*w,1+c.scale*w)
    }
    func drawPortalLip(_ t:Double,context:inout GraphicsContext) {
        for cue in data.portal {
            guard let p=progress(t,cue.start,cue.start+data.lip.duration) else {continue}
            var c=context;c.opacity=data.lip.alpha*sin(.pi*p)
            c.translateBy(x:cue.lipX,y:0)
            c.fill(lipPath,
                   with:.linearGradient(Gradient(colors:[.clear,Color(red:0.95,green:1,blue:0.62),.clear]),
                                        startPoint:CGPoint(x:-data.lip.length/2,y:0),endPoint:CGPoint(x:data.lip.length/2,y:0)))
        }
    }
}
