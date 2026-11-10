import CoreText
import SwiftUI
import UIKit

final class FilamentBulbDimmerResources {
    static let shared=FilamentBulbDimmerResources()
    struct Label { let image: Image; let width,height,bitmapWidth: Double }
    private var labels: [String:Label]=[:]
    private var fonts: [String:UIFont]=[:]
    private var layout: FilamentBulbDimmerTheme.Layout?
    private var cachedBand=Path()
    var shapes:[String:Path]=[:]
    init() {
        var names: [String:String]=[:]
        for weight in ["Regular","Medium","SemiBold","Bold"] {
            if let url=Bundle.main.url(forResource:"FilamentBulbDimmer-Inter-\(weight)",withExtension:"ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil)
                if let descriptors=CTFontManagerCreateFontDescriptorsFromURL(url as CFURL) as? [CTFontDescriptor],let d=descriptors.first {
                    names[weight]=CTFontCopyPostScriptName(CTFontCreateWithFontDescriptor(d,16,nil)) as String
                }
            }
        }
        for (key,string,weight,size,height,tracking) in [
            ("title","Lamp Details","Medium",18.0,22.0,0.0),("name","Living Room Lamp","SemiBold",29,36,-0.2),
            ("schedule","9:00 pm to 5:00 am","Medium",13,18,0),("left","4 kwh","Medium",18,22,0),("right","18° C","Medium",18,22,0),
            ("tab","Energy Usage","Medium",22,27,0),("intensity","Intensity","Regular",11,14,0),
            ("On","On","Medium",15,19,0),("Off","Off","Medium",15,19,0),("%","%","Bold",25,30,0)
        ] { make(key,string,names[weight],size,height,tracking) }
        for i in 0...9 { make("\(i)","\(i)",names["Bold"],25,30,0) }
    }
    private func make(_ key: String,_ string: String,_ name: String?,_ size: Double,_ height: Double,_ tracking: Double) {
        var font=name.flatMap { UIFont(name:$0,size:size) } ?? UIFont.systemFont(ofSize:size)
        if key=="%" || Int(key) != nil {
            font=UIFont(descriptor:font.fontDescriptor.addingAttributes([.featureSettings:[[UIFontDescriptor.FeatureKey.type:kNumberSpacingType,.selector:kMonospacedNumbersSelector]]]),size:size)
        }
        let text=NSAttributedString(string:string,attributes:[.font:font,.kern:tracking,.foregroundColor:UIColor.white])
        let width=text.size().width
        let format=UIGraphicsImageRendererFormat(); format.scale=3
        let bitmap=UIGraphicsImageRenderer(size:CGSize(width:ceil(width+4),height:height+4),format:format).image { renderer in
            let c=renderer.cgContext; let baseline=2+(height-font.ascender+font.descender)/2+font.ascender
            c.translateBy(x:2,y:baseline); c.scaleBy(x:1,y:-1)
            CTLineDraw(CTLineCreateWithAttributedString(text),c)
        }
        labels[key]=Label(image:Image(uiImage:bitmap),width:width,height:height,bitmapWidth:ceil(width+4))
    }
    func text(_ c: GraphicsContext,_ key: String,_ x: Double,_ y: Double,_ color: Color = .white,center: Bool = false,alpha: Double = 1) {
        guard let l=labels[key] else { return }
        var context=c; context.opacity*=alpha
        var image=context.resolve(l.image); image.shading = .color(color)
        context.draw(image,in:CGRect(x:x-(center ? l.width/2:0)-2,y:y-2,width:l.bitmapWidth,height:l.height+4))
    }
    func width(_ key:String)->Double { labels[key]?.width ?? 0 }
    func band(_ l: FilamentBulbDimmerTheme.Layout)->Path {
        if layout != l {
            layout=l
            shapes=["tab":Path(roundedRect:l.tab,cornerRadius:32),"left":Path(roundedRect:l.chipL,cornerRadius:16),"right":Path(roundedRect:l.chipR,cornerRadius:16),"card":Path(roundedRect:l.card,cornerRadius:24),"dial":Path(ellipseIn:CGRect(x:l.cx-46*l.sx,y:l.cy-12*l.k-46*l.sx,width:92*l.sx,height:92*l.sx))]
            let rm=(l.rOut+l.rIn)/2
            var p=Path(); p.move(to:CGPoint(x:l.cx-rm,y:l.cy+l.leg)); p.addLine(to:CGPoint(x:l.cx-rm,y:l.cy))
            p.addArc(center:CGPoint(x:l.cx,y:l.cy),radius:rm,startAngle:.degrees(180),endAngle:.degrees(360),clockwise:false)
            p.addLine(to:CGPoint(x:l.cx+rm,y:l.cy+l.leg)); cachedBand=p
        }; return cachedBand
    }
    static let back: Path = { var p=Path(); p.move(to:CGPoint(x:10,y:2)); p.addLine(to:CGPoint(x:2,y:10)); p.addLine(to:CGPoint(x:10,y:18)); return p }()
    static let clock: Path = { var p=Path(ellipseIn:CGRect(x:1.5,y:1.5,width:13,height:13)); p.move(to:CGPoint(x:8,y:4.5)); p.addLine(to:CGPoint(x:8,y:8)); p.addLine(to:CGPoint(x:10.5,y:9.5)); return p }()
    static let bulb: Path = {
        var p=Path();p.addArc(center:CGPoint(x:10,y:9.5),radius:6.5,startAngle:.degrees(-90),endAngle:.radians(acos(-3.8/6.5)),clockwise:true)
        p.addLine(to:CGPoint(x:6.2,y:17));p.addLine(to:CGPoint(x:13.8,y:17));p.addLine(to:CGPoint(x:13.8,y:14.8))
        p.addArc(center:CGPoint(x:10,y:9.5),radius:6.5,startAngle:.radians(acos(3.8/6.5)),endAngle:.degrees(-90),clockwise:true);p.closeSubpath()
        p.move(to:CGPoint(x:7.2,y:20));p.addLine(to:CGPoint(x:12.8,y:20));return p
    }()
    static let switchBulb:Path = {
        // SVG: M7 1.5 a5 5 0 0 0 -3 9 V12 h6 V10.5 a5 5 0 0 0 -3 -9 Z M5 14 h4.
        // The 9.487 pt chords give the SVG arcs their common centre (7, 6.5).
        var p=Path(); let ca=CGPoint(x:7,y:6.5),cb=CGPoint(x:7,y:6.5)
        p.move(to:CGPoint(x:7,y:1.5));p.addArc(center:ca,radius:5,startAngle:.radians(atan2(1.5-ca.y,7-ca.x)),endAngle:.radians(atan2(10.5-ca.y,4-ca.x)),clockwise:true)
        p.addLine(to:CGPoint(x:4,y:12));p.addLine(to:CGPoint(x:10,y:12));p.addLine(to:CGPoint(x:10,y:10.5))
        p.addArc(center:cb,radius:5,startAngle:.radians(atan2(10.5-cb.y,10-cb.x)),endAngle:.radians(atan2(1.5-cb.y,7-cb.x)),clockwise:true);p.closeSubpath()
        p.move(to:CGPoint(x:5,y:14));p.addLine(to:CGPoint(x:9,y:14));return p
    }()
    static let rays: Path = { var p=Path(); for (a,b) in [(CGPoint(x:10,y:0),CGPoint(x:10,y:1.5)),(CGPoint(x:2,y:3),CGPoint(x:3,y:4)),(CGPoint(x:18,y:3),CGPoint(x:17,y:4)),(CGPoint(x:0,y:10),CGPoint(x:1.5,y:10)),(CGPoint(x:20,y:10),CGPoint(x:18.5,y:10))] { p.move(to:a); p.addLine(to:b) }; return p }()
}
