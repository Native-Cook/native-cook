import SwiftUI
import UIKit
import CoreText

final class ArrowRankCardsArtwork {
    static let shared=ArrowRankCardsArtwork()
    static let albums=[["Paper","Lanterns"],["Glass","Harbour"],["Voltage","Garden"]]
    static let titles=[["Slow","Meridian"],["Asked The","Weather"],["Motel","Carousel"]]
    static let artists=["Odessa Vale","June Arlo","Nico Brava"], ranks=["4","5","2"]
    static let panels: [UInt32]=[0x17B15A,0xC9C4BE,0xF54A35], muted: [UInt32]=[0x0E7C3D,0x736D68,0x9B291D]
    static let chips=[[("A",UInt32(0xDDF53F)),("K",0xDDB9F6)],[("A",0xDDF53F),("E",0xC9F6C1),("G",0x3FE8B2)],[("H",0xFF9F9F),("G",0x3FE8B2)]]
    private var fonts: [Bool:CGFont]=[:],runs: [String:(Path,Double)]=[:]
    private(set) var faces: [UIImage]=[]
    static func color(_ n: UInt32,_ alpha: Double=1)->Color { Color(.sRGB,red:Double((n>>16)&255)/255,green:Double((n>>8)&255)/255,blue:Double(n&255)/255,opacity:alpha) }
    private init() {
        for (bold,name) in [(false,"Bold"),(true,"ExtraBold")] {
            if let url=Bundle.main.url(forResource:"ArrowRankCards-InterTight-"+name,withExtension:"ttf"),let data=CGDataProvider(url:url as CFURL),let font=CGFont(data) { fonts[bold]=font;CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil) }
        }
        let format=UIGraphicsImageRendererFormat();format.scale=3;format.opaque=true
        faces=(0..<3).map { i in UIGraphicsImageRenderer(size:CGSize(width:375,height:374),format:format).image { renderer in
            let c=renderer.cgContext
            c.setFillColor(UIColor(Self.color(Self.panels[i])).cgColor);c.fill(CGRect(x:0,y:0,width:375,height:374))
            func draw(_ str: String,_ x: Double,_ baseline: Double,_ size: Double,_ color: UInt32) {
                c.saveGState();c.translateBy(x:x,y:baseline);c.setFillColor(UIColor(Self.color(color)).cgColor);c.addPath(run(str,size).0.cgPath);c.fillPath();c.restoreGState()
            }
            draw(Self.titles[i][0],11,16+34*1490/2048,34,0x0B0B0B);draw(Self.titles[i][1],11,53+34*1490/2048,34,0x0B0B0B)
            draw(Self.artists[i],11,94+15*1490/2048,15,Self.muted[i])
            for (j,chip) in Self.chips[i].enumerated() {
                let x=42+Double(j)*71;c.setFillColor(UIColor(Self.color(chip.1)).cgColor);c.fillEllipse(in:CGRect(x:x-31,y:122,width:62,height:62))
                c.setLineWidth(2);c.setStrokeColor(UIColor.white.withAlphaComponent(0.35).cgColor);c.strokeEllipse(in:CGRect(x:x-30,y:123,width:60,height:60))
                draw(chip.0,x-run(chip.0,30).1/2,153+30*1490/4096,30,0x0B0B0B)
            }
        } }
    }
    func run(_ text: String,_ size: Double,_ extra: Bool=false,_ track: Double=0)->(Path,Double) {
        let key="\(text)|\(size)|\(extra)|\(track)";if let r=runs[key] { return r }
        guard let cg=fonts[extra] else { return (Path(),0) }
        let font=CTFontCreateWithGraphicsFont(cg,size,nil,nil)
        let line=CTLineCreateWithAttributedString(NSAttributedString(string:text,attributes:[NSAttributedString.Key(kCTFontAttributeName as String):font,.kern:track]))
        let path=CGMutablePath()
        for object in CTLineGetGlyphRuns(line) as NSArray {
            let run=object as! CTRun,n=CTRunGetGlyphCount(run);var glyphs=[CGGlyph](repeating:0,count:n),positions=[CGPoint](repeating:.zero,count:n)
            CTRunGetGlyphs(run,CFRange(location:0,length:0),&glyphs);CTRunGetPositions(run,CFRange(location:0,length:0),&positions)
            for i in 0..<n { if let p=CTFontCreatePathForGlyph(font,glyphs[i],nil) { path.addPath(p,transform:CGAffineTransform(a:1,b:0,c:0,d:-1,tx:positions[i].x,ty:-positions[i].y)) } }
        }
        let r=(Path(path),CTLineGetTypographicBounds(line,nil,nil,nil));runs[key]=r;return r
    }
    func text(_ c: inout GraphicsContext,_ text: String,_ x: Double,_ baseline: Double,_ size: Double,_ color: UInt32=0x0B0B0B,_ extra: Bool=false,_ track: Double=0) {
        var ctx=c;ctx.translateBy(x:x,y:baseline);ctx.fill(run(text,size,extra,track).0,with:.color(Self.color(color)))
    }
    static func arrow(_ f: ArrowRankCardsFrame)->Path {
        let head=(f.head+f.shake) * .pi/180,lag=max(-75,min(75,f.head-f.tail)) * .pi/180,tail=head-lag
        let hx=cos(head),hy=sin(head),tx=cos(tail),ty=sin(tail),s=lag<0 ? -1.0 : 1.0,r=28.0
        let back=22*max(0,-hx)+32*max(0,hy)-14*max(0,-hy),px=73-back*hx,py=313-back*hy
        let arc=r*abs(lag),shaft=97.443651+f.stretch,straight=max(0,shaft-arc),lt=straight/2,lh=straight-lt
        let ax=px-57*tx+f.beckon*tx,ay=py-57*ty+f.beckon*ty,e1x=ax+lt*tx,e1y=ay+lt*ty
        let ox=e1x-s*r*ty,oy=e1y+s*r*tx,e2x=ox+s*r*hy,e2y=oy-s*r*hx,bx=e2x+lh*hx,by=e2y+lh*hy,k=4/3*tan(abs(lag)/4)*r
        var p=Path();p.move(to:CGPoint(x:ax,y:ay));p.addLine(to:CGPoint(x:e1x,y:e1y))
        if arc>0.01 { p.addCurve(to:CGPoint(x:e2x,y:e2y),control1:CGPoint(x:e1x+k*tx,y:e1y+k*ty),control2:CGPoint(x:e2x-k*hx,y:e2y-k*hy)) }
        p.addLine(to:CGPoint(x:bx,y:by));p.move(to:CGPoint(x:bx-37.221826*hx+37.221826*hy,y:by-37.221826*hy-37.221826*hx));p.addLine(to:CGPoint(x:bx,y:by));p.addLine(to:CGPoint(x:bx-37.221826*hx-37.221826*hy,y:by-37.221826*hy+37.221826*hx));return p
    }
}
