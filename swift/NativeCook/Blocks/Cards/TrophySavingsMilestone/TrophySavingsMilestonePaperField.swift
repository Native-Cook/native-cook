import SwiftUI
import UIKit

@MainActor
struct TrophySavingsMilestonePaperField: View {
    enum Layer { case rear, front }
    let scene: TrophySavingsMilestoneCelebration
    let layer: Layer
    private struct Piece {
        var x: Double; var y: Double; var length: Double; var width: Double; var angle: Double; var bend: Double; var color: UInt32; var kind: String; var opacity: Double
        var face: Path; var fold: Path
    }
    private struct Pose { var x=0.0; var y=0.0; var rotation=0.0; var sx=1.0; var sy=1.0; var lift=0.0; var reverse=0.0 }
    private static let colors: [UInt32] = [0xF37E87,0xEF98CD,0x68BFE8,0xA5DC63,0xF5D264]
    private static let data: (pieces:[Piece],ids:[Int]) = {
        guard let url = Bundle.main.url(forResource:"TrophySavingsMilestone-reference-paper",withExtension:"json"),let bytes = try? Data(contentsOf:url),let object = try? JSONSerialization.jsonObject(with:bytes) as? [String:Any],let rows = object["pieces"] as? [[Any]],let ids = object["ids"] as? [Int] else { preconditionFailure("Missing trophy reference paper data") }
        let pieces = rows.map { row -> Piece in
            let l = row[2] as! Double, w = row[3] as! Double, b = row[5] as! Double, kind = row[7] as! String
            let h=l/2,s=w/2
            var face = Path(),fold = Path()
            if kind == "yellowCurl" { face = TrophySavingsMilestonePaths.parse("M10 -29C-2 -28 -7 -23 -2 -14C13 6 7 9 -5 16C-10 20 -12 23 -12 29") }
            else if kind == "greenCurl" { face = TrophySavingsMilestonePaths.parse("M2 -23C-7 -22 -10 -8 0 -12C11 -18 13 -6 3 -6C-10 -7 -12 9 0 8C12 7 11 19 9 23") }
            else if kind == "speck" { face = Path(ellipseIn:CGRect(x:-s,y:-h,width:w,height:l)) }
            else if kind == "arc" {
                face.move(to:CGPoint(x:-h,y:-s));face.addQuadCurve(to:CGPoint(x:h,y:-s),control:CGPoint(x:0,y:-b-s));face.addLine(to:CGPoint(x:h,y:s));face.addQuadCurve(to:CGPoint(x:-h,y:s),control:CGPoint(x:0,y:-b+s));face.closeSubpath()
            } else {
                face.move(to:CGPoint(x:-s,y:-h));face.addQuadCurve(to:CGPoint(x:s,y:-h),control:CGPoint(x:0,y:-h+b))
                face.addCurve(to:CGPoint(x:s*0.8,y:h),control1:CGPoint(x:s*0.9,y:-h*0.28),control2:CGPoint(x:s*0.3,y:h*0.36))
                face.addQuadCurve(to:CGPoint(x:-s,y:h),control:CGPoint(x:0,y:h-b))
                face.addCurve(to:CGPoint(x:-s,y:-h),control1:CGPoint(x:-s*0.3,y:h*0.26),control2:CGPoint(x:-s*1.3,y:-h*0.24));face.closeSubpath()
                fold.move(to:CGPoint(x:-s,y:-h));fold.addQuadCurve(to:CGPoint(x:s,y:-h),control:CGPoint(x:0,y:-h+b));fold.addLine(to:CGPoint(x:s*0.3,y:-h*0.4));fold.closeSubpath()
            }
            return Piece(x:row[0] as! Double,y:row[1] as! Double,length:l,width:w,angle:row[4] as! Double,bend:b,color:colors[row[6] as! Int],kind:kind,opacity:row[8] as! Double,face:face,fold:fold)
        }; return (pieces,ids)
    }()
    private static let travel: [(Double,Double,Double)] = [(-24,-22,-1),(-27,-14,1),(28,-22,-1),(18,30,1),(-30,12,-1),(26,30,1),(-10,10,1),(12,-12,-1),(-6,-12,1),(13,6,-1),(12,10,1),(12,-8,-1),(9,-9,1),(-13,12,-1),(-7,12,-1),(-12,11,1),(-9,-8,1),(10,8,-1)]
    private static let crestFacing = [0.24,Double.pi-0.3,-0.32,Double.pi+0.24,0.36,-0.24]
    private struct StaticPaper: View {
        var body: some View { Canvas { context,_ in
            let moving = Set(data.ids)
            for (id,piece) in data.pieces.enumerated() where !moving.contains(id) {
                var local=context;local.translateBy(x:piece.x,y:piece.y);local.rotate(by:.degrees(piece.angle));paintSource(piece,in:&local)
            }
        }.frame(width:393,height:350) }
    }
    private static let staticImage: UIImage? = { let renderer=ImageRenderer(content:StaticPaper());renderer.scale=3;return renderer.uiImage }()
    var body: some View {
        Canvas { context,_ in
            if layer == .rear {
                if let image=Self.staticImage { context.draw(Image(uiImage:image),in:CGRect(x:0,y:0,width:393,height:350)) }
                let power=scene.active && !scene.reduced ? Self.smooth((scene.progress-0.08)/0.74) : 0
                if power > 0 {
                    let stops:[Gradient.Stop] = [.init(color:Color(hex:0xFFD887).opacity(0.35*power),location:0),.init(color:Color(hex:0xFFE3AD).opacity(0.24*power),location:0.45),.init(color:Color(hex:0xFFF0CB).opacity(0.055*power),location:0.82),.init(color:Color(hex:0xFFF0CB).opacity(0),location:1)]
                    context.fill(Path(CGRect(x:0,y:0,width:393,height:350)),with:.radialGradient(Gradient(stops:stops),center:CGPoint(x:196,y:157),startRadius:0,endRadius:190))
                }
            }
            let range = layer == .front ? 0..<6 : 6..<18
            for index in range { draw(index,in:&context) }
        }.frame(width:393,height:350).allowsHitTesting(false).accessibilityHidden(true)
    }
    private static func smooth(_ x: Double)->Double { TrophySavingsMilestoneMotion.smooth(x) }
    private func pose(_ index: Int)->Pose {
        let near=index<6,start=scene.wakeStarts[index],age=(scene.clock-start)/scene.cadence-(near ? 0 : 0.04)
        guard scene.active && !scene.reduced && start >= 0 && age >= 0 else { return Pose() }
        let flutter=age <= 0.72 ? sin(.pi*age/0.72)*exp(-1.7*age) : 0
        let held=Self.smooth(scene.progress/0.65)*Self.smooth(age/0.16)
        let breeze=Self.smooth(age/0.1)*(1-Self.smooth((age-0.56)/0.28))
        let after=scene.contactStart >= 0 && scene.clock-scene.contactStart > 0.12*scene.cadence
        let recoil=after ? Self.smooth((0.98-scene.progress)/0.25)*sin(.pi*scene.progress) : 0
        let wind=max(breeze*(0.25+0.75*Self.smooth(scene.progress/0.5)),0.6*recoil)
        let energy=TrophySavingsMilestoneMotion.clamp(0.8*held+0.3*flutter)
        let (dx,dy,direction)=Self.travel[index],scale=1+(near ? 0.64 : 0.22)*energy,i=Double(index)
        let raw=direction*((near ? 3.3 : 2.6)*wind*sin(age*7.5+i*0.81)+1.8*flutter)
        let crest=near ? Self.smooth((scene.progress-0.78)/0.22)*Self.smooth(age/0.16) : 0
        let flip=raw*(1-0.95*crest)+(near ? Self.crestFacing[index] : 0)*crest+0.12*wind*sin(age*6+i)*crest
        let facing=cos(flip)
        return Pose(x:dx*energy+(near ? 10 : 5.5)*wind*sin(age*9+i*0.81),y:dy*energy+(near ? 9 : 5)*wind*cos(age*7.5+i*0.67),rotation:direction*0.55*energy+(near ? 1.05 : 0.75)*wind*sin(age*8.5+i*0.73),sx:scale*max(0.075,abs(facing))*(near ? 1+0.72*energy : 1),sy:scale*(1-0.06*pow(sin(flip),2)),lift:energy,reverse:Self.smooth(-facing/0.16))
    }
    private func draw(_ index:Int,in context:inout GraphicsContext) {
        let piece=Self.data.pieces[Self.data.ids[index]],p=pose(index),near=index<6
        var local=context;local.translateBy(x:piece.x,y:piece.y)
        if !near || p.lift != 0 {
            local.translateBy(x:p.x,y:p.y)
            let axis=(piece.angle+(piece.kind == "arc" ? 90 : 0))*Double.pi/180
            local.rotate(by:.radians(p.rotation+(near ? axis : 0)));local.scaleBy(x:p.sx,y:p.sy)
            if near { local.rotate(by:.radians(-axis)) }
        }
        if p.lift > 0 {
            var shadow=local;shadow.translateBy(x:1,y:near ? 3 : 1.5);shadow.rotate(by:.degrees(piece.angle));shadow.opacity *= p.lift*(near ? 0.19 : 0.07)
            if near { shadow.addFilter(.blur(radius:1.5)) };shadow.fill(piece.face,with:.color(Color(hex:0x66515A)))
        }
        local.rotate(by:.degrees(piece.angle));Self.paintSource(piece,in:&local)
        let factor=near ? 0.84 : 0.78,r=Double((piece.color>>16)&255),g=Double((piece.color>>8)&255),b=Double(piece.color&255)
        if p.reverse > 0 { local.fill(piece.face,with:.color(Color(red:(r*factor).rounded()/255,green:(g*factor).rounded()/255,blue:(b*factor).rounded()/255).opacity(p.reverse))) }
        if near && p.lift > 0 {
            var lip=local;lip.opacity *= p.lift*0.28
            let arc=piece.kind == "arc",start=CGPoint(x:arc ? 0 : -piece.width/2,y:arc ? -piece.width/2-piece.bend : 0),end=CGPoint(x:arc ? 0 : piece.width/2,y:arc ? piece.width/2-piece.bend : 0)
            let stops:[Gradient.Stop] = [.init(color:.clear,location:0),.init(color:.clear,location:0.72),.init(color:Color(red:1,green:249/255,blue:237/255).opacity(0.8),location:0.86),.init(color:Color(red:80/255,green:28/255,blue:58/255).opacity(0.45),location:1)]
            lip.fill(piece.face,with:.linearGradient(Gradient(stops:stops),startPoint:start,endPoint:end))
        }
        if p.lift > 0 { local.stroke(piece.face,with:.color(Color(hex:0xFFF4DF).opacity(p.lift*(near ? 0.45 : 0.2))),lineWidth:0.55) }
    }
    private static func paintSource(_ p:Piece,in context:inout GraphicsContext) {
        var local=context;local.opacity *= p.opacity
        if p.kind == "yellowCurl" || p.kind == "greenCurl" { local.stroke(p.face,with:.color(Color(hex:p.color)),style:StrokeStyle(lineWidth:p.width,lineCap:.round)) }
        else { local.fill(p.face,with:.color(Color(hex:p.color)));if !p.fold.isEmpty { local.fill(p.fold,with:.color(.white.opacity(0.18))) } }
    }
}
