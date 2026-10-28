import SwiftUI

struct TrophySavingsMilestoneTargetGlyph: View {
    private static let arrow:[(String,UInt32)] = [
        ("M11.25 14.85L12.8 10.7L15.05 12.5Z",0xA3672C),
        ("M22.15 3.85L23.55 .55L27.65 .25L26.55 3.75L24.55 5.6Z",0x6EBEC1),
        ("M22.15 3.85L24.55 5.6L26.55 3.75L24.55 2.6Z",0x3E939D),
        ("M24.3 4.55L27.7 3.9L31.65 5.6L31.6 7.25L27.65 7.1L24.1 5.85Z",0x2F737D),
        ("M25.1 4.8L28.15 4.65L31.65 5.6L27.7 5.8Z",0x58A4AC)
    ]
    private static let arrowPaths = arrow.map { (TrophySavingsMilestonePaths.parse($0.0),Color(hex:$0.1)) }
    private static let shaft = TrophySavingsMilestonePaths.parse("M13.25 12.25L23.9 2.7L25.55 4.5L14.5 13.6Z")
    private static let shaftLine = TrophySavingsMilestonePaths.parse("M14.25 11.8L24.25 2.9")
    private static let feather = TrophySavingsMilestonePaths.parse("M24.15 4.9L27.1 2.2")
    var body: some View {
        Canvas { context,_ in
            var target=context;target.translateBy(x:11.1,y:14.5);target.rotate(by:.degrees(-43));target.translateBy(x:-11.1,y:-14.5)
            func ellipse(_ x:Double,_ y:Double,_ rx:Double,_ ry:Double)->Path { Path(ellipseIn:CGRect(x:x-rx,y:y-ry,width:2*rx,height:2*ry)) }
            target.fill(ellipse(11.45,15.05,9.25,11.65),with:.linearGradient(Gradient(stops:[.init(color:Color(hex:0xF05C60),location:0),.init(color:Color(hex:0xC72E38),location:0.48),.init(color:Color(hex:0x86232A),location:1)]),startPoint:CGPoint(x:2.2,y:3.4),endPoint:CGPoint(x:20.7,y:26.7)))
            target.fill(ellipse(10.85,14.2,8.9,11.05),with:.linearGradient(Gradient(stops:[.init(color:Color(hex:0xE54D52),location:0),.init(color:Color(hex:0xD1353E),location:0.55),.init(color:Color(hex:0xBB2732),location:1)]),startPoint:CGPoint(x:1.95,y:3.15),endPoint:CGPoint(x:19.75,y:25.25)))
            for item:(Double,Double,Double,Double,UInt32) in [(10.65,13.95,6.95,8.95,0xFFF0E4),(10.65,13.95,5.15,6.9,0xC52D36),(10.5,13.8,3.25,4.7,0xFFF0E3),(10.5,13.8,1.75,2.65,0xB6232D)] { target.fill(ellipse(item.0,item.1,item.2,item.3),with:.color(Color(hex:item.4))) }
            context.fill(Self.arrowPaths[0].0,with:.color(Self.arrowPaths[0].1))
            context.fill(Self.shaft,with:.linearGradient(Gradient(colors:[Color(hex:0x2A7380),Color(hex:0x54ADB5),Color(hex:0x87D1D2)]),startPoint:CGPoint(x:13.25,y:13.6),endPoint:CGPoint(x:25.55,y:2.7)))
            context.stroke(Self.shaftLine,with:.color(Color(hex:0xA0DDDA)),lineWidth:0.65)
            for item in Self.arrowPaths.dropFirst() { context.fill(item.0,with:.color(item.1)) }
            context.stroke(Self.feather,with:.color(Color(hex:0x276E78)),style:StrokeStyle(lineWidth:0.7,lineCap:.round))
        }.frame(width:32,height:26).allowsHitTesting(false).accessibilityHidden(true)
    }
}
