import CoreText
import ImageIO
import SwiftUI
import UIKit

// Fonts, decoded images and glyph outlines are built once per screen width, never in a frame.
final class AuroraTeammatesOnboardingResources {
    typealias H = AuroraTeammatesOnboardingTheme
    struct Label { var path: Path; var width: Double }
    static let shared = AuroraTeammatesOnboardingResources()
    private var graphicsFonts: [String: CGFont] = [:]
    private var cachedLayout:H.Layout?
    private(set) var labels: [String: Label] = [:]
    private(set) var images: [String: Image] = [:]
    private var decoded: [String: UIImage] = [:]
    private(set) var contents: [Int: Image] = [:]
    private(set) var textLayout: AuroraTeammatesOnboardingSheet.TextLayout!
    private(set) var pill = Path()
    private(set) var checkmark = Path()
    private(set) var dotsTrack = Path()
    private(set) var buttonClip = Path()
    private(set) var sheenRect = Path()
    private(set) var bloomCircle = Path()
    private(set) var panels:[Path]=[]
    private(set) var sheets:[Path]=[]
    private(set) var rims:[Path]=[]
    private(set) var dotCircle=Path()
    private(set) var dotPill=Path()
    let icons = AuroraTeammatesOnboardingIcons.paths
    init() {
        for name in ["Inter-Regular", "Inter-SemiBold", "InterDisplay-Bold"] {
            if let url=Bundle.main.url(forResource:"AuroraTeammatesOnboarding-\(name)",withExtension:"ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL,.process,nil)
                if let data=CGDataProvider(url:url as CFURL),let font=CGFont(data) { graphicsFonts[name]=font }
            }
        }
        for name in ["ato-icon-purple","ato-icon-slate"]+H.logos.map({ "ato-logo-\($0)" }) {
            if let url=Bundle.main.url(forResource:"AuroraTeammatesOnboarding-\(name)",withExtension:"png"),
               let src=CGImageSourceCreateWithURL(url as CFURL,nil),let cg=CGImageSourceCreateImageAtIndex(src,0,[kCGImageSourceShouldCacheImmediately:true] as CFDictionary) {
                let image=UIImage(cgImage:cg);decoded[name]=image;images[name]=Image(uiImage:image)
            }
        }
    }
    private func font(_ size:Double,_ name:String)->CTFont {
        if let cg=graphicsFonts[name] { return CTFontCreateWithGraphicsFont(cg,size,nil,nil) }
        return (UIFont(name:name,size:size) ?? UIFont.systemFont(ofSize:size)) as CTFont
    }
    private func label(_ text:String,_ size:Double,_ track:Double=0,_ name:String="Inter-Regular") -> Label {
        // tgfx shapes with HarfBuzz's 26.6 advances, then adds tracking per cluster.
        // Keep the outlines at the requested size and quantise only their measured advances.
        let metricsFont=font(floor(size*64)/64,name),outlineFont=font(size,name)
        let line=CTLineCreateWithAttributedString(NSAttributedString(string:text,attributes:[NSAttributedString.Key(kCTFontAttributeName as String):metricsFont]))
        let p=CGMutablePath();var cursor=0.0
        for run in CTLineGetGlyphRuns(line) as! [CTRun] {
            let count=CTRunGetGlyphCount(run)
            var glyphs=Array(repeating:CGGlyph(0),count:count),positions=Array(repeating:CGPoint.zero,count:count),advances=Array(repeating:CGSize.zero,count:count)
            CTRunGetGlyphs(run,CFRange(location:0,length:0),&glyphs);CTRunGetPositions(run,CFRange(location:0,length:0),&positions);CTRunGetAdvances(run,CFRange(location:0,length:0),&advances)
            var originalCursor=positions.first?.x ?? 0
            for j in 0..<count {
                if let glyph=CTFontCreatePathForGlyph(outlineFont,glyphs[j],nil) {
                    let transform=CGAffineTransform(a:1,b:0,c:0,d:-1,tx:cursor+positions[j].x-originalCursor,ty:-positions[j].y)
                    p.addPath(glyph,transform:transform)
                }
                originalCursor+=advances[j].width
                cursor+=(advances[j].width*64).rounded()/64+track
            }
        }
        return Label(path:Path(p),width:cursor)
    }
    func prepare(_ l:H.Layout) {
        guard cachedLayout != l else { return };cachedLayout=l;labels=[:]
        textLayout=AuroraTeammatesOnboardingSheet.layoutTitle(l) { text,size,tracking,display in
            self.label(text,size,tracking,display ? "InterDisplay-Bold":"Inter-Regular").width
        }
        for (i,text) in H.tokens.enumerated() { labels["token\(i)"]=label(text,l.titleSize,l.titleTracking,"InterDisplay-Bold") }
        for p in 0..<3 { for (j,text) in H.subtitles[p].enumerated() { labels["sub\(p)-\(j)"]=label(text,l.subtitleSize) } }
        let ts=l.S/(402.0/280)
        for i in 0..<5 {
            labels["agent\(i)"]=label(H.agents[i],13.8*ts,0,"Inter-SemiBold")
            labels["agentSub\(i)"]=label(H.agentSubs[i],9.8*ts)
        }
        labels["active"]=label("Active",10.4*ts,0,"Inter-SemiBold")
        for i in 0..<6 {
            labels["integration\(i)"]=label(H.integrations[i],13.6*ts,0,"Inter-SemiBold")
            labels["status\(i)"]=label(i==0 || i==4 ? "• Connected":"Connect",10*ts)
        }
        labels["next"]=label("Next →",l.buttonLabel,0,"Inter-SemiBold")
        labels["done"]=label("You're all set",l.buttonLabel,0,"Inter-SemiBold")
        pill=AuroraTeammatesOnboardingSheet.pillPath(l.buttonW,l.buttonH,0,0)
        let check=l.buttonLabel*0.95,doneW=labels["done"]?.width ?? 0,doneX = -(doneW+check+l.buttonLabel*0.45)/2
        var tick=Path();tick.move(to:CGPoint(x:doneX+check*0.12,y:-check*0.02));tick.addLine(to:CGPoint(x:doneX+check*0.4,y:check*0.28));tick.addLine(to:CGPoint(x:doneX+check*0.92,y:-check*0.36));checkmark=tick
        dotsTrack=Path(roundedRect:CGRect(x:0,y:0,width:AuroraTeammatesOnboardingSheet.trackWidth(l),height:l.trackH),cornerRadius:l.trackH/2)
        buttonClip=Path(roundedRect:CGRect(x:-l.buttonW/2,y:-l.buttonH/2,width:l.buttonW,height:l.buttonH),cornerRadius:l.buttonH/2)
        sheenRect=Path(CGRect(x:-l.buttonH*0.45,y:-l.buttonH*1.2,width:l.buttonH*0.9,height:l.buttonH*2.4))
        bloomCircle=Path(ellipseIn:CGRect(x:-l.buttonH*1.4,y:-l.buttonH*1.4,width:l.buttonH*2.8,height:l.buttonH*2.8))
        panels=[(218.75,43.0,7.0),(116.5,75.0,9.0)].map { w,h,r in
            Path(roundedRect:CGRect(x:-w*l.S/2,y:-h*l.S/2,width:w*l.S,height:h*l.S),cornerRadius:r*l.S)
        }
        sheets=l.sheetTop.map { AuroraTeammatesOnboardingSheet.sheetPath($0,l.depth,l.cx,l,true) }
        rims=l.sheetTop.map { AuroraTeammatesOnboardingSheet.sheetPath($0,l.depth,l.cx,l,false) }
        dotCircle=Path(ellipseIn:CGRect(x:0,y:0,width:l.dot,height:l.dot))
        dotPill=Path(roundedRect:CGRect(x:0,y:0,width:l.pillW,height:l.pillH),cornerRadius:l.pillH/2)
        renderContents(l)
    }
    private func renderContents(_ l:H.Layout) {
        contents=[:]
        for i in 2..<13 {
            let card=i<7,s=l.S,ts=s/(402.0/280),w=(card ? 218.75:116.5)*s+2,h=(card ? 43.0:75.0)*s+2
            let format=UIGraphicsImageRendererFormat();format.scale=UIScreen.main.scale;format.opaque=false
            let bitmap=UIGraphicsImageRenderer(size:CGSize(width:w,height:h),format:format).image { renderer in
                let c=renderer.cgContext;c.translateBy(x:w/2,y:h/2)
                func rounded(_ x:Double,_ y:Double,_ w:Double,_ h:Double,_ radius:Double,_ alpha:Double,_ stroke:Double=0) {
                    let path=CGPath(roundedRect:CGRect(x:x,y:y,width:w,height:h),cornerWidth:radius,cornerHeight:radius,transform:nil)
                    c.addPath(path)
                    if stroke>0 { c.setStrokeColor(UIColor.white.withAlphaComponent(alpha).cgColor);c.setLineWidth(stroke);c.strokePath() }
                    else { c.setFillColor(UIColor.white.withAlphaComponent(alpha).cgColor);c.fillPath() }
                }
                func text(_ key:String,_ x:Double,_ y:Double,_ alpha:Double=1,_ center:Bool=false) {
                    guard let label=labels[key] else { return }
                    c.saveGState();c.translateBy(x:x-(center ? label.width/2:0),y:y);c.addPath(label.path.cgPath);c.setFillColor(UIColor.white.withAlphaComponent(alpha).cgColor);c.fillPath();c.restoreGState()
                }
                if card {
                    let k=i-2,cw=218.75*s,ch=43*s,left = -cw/2,top = -ch/2,tile=30.5*s,x=left+5.5*s,ik=tile*0.6/24
                    rounded(x,-tile/2,tile,tile,7*s,0.12);rounded(x,-tile/2,tile,tile,7*s,0.16,0.7)
                    c.saveGState();c.translateBy(x:x+tile/2-12*ik,y:-12*ik);c.scaleBy(x:ik,y:ik)
                    c.addPath(icons[k].cgPath);c.setLineWidth(1.7);c.setLineCap(.round);c.setLineJoin(.round);c.setStrokeColor(UIColor.white.cgColor);c.strokePath()
                    c.addPath(icons[k<3 ? 5:6].cgPath);c.setFillColor(UIColor.white.cgColor);c.fillPath();c.restoreGState()
                    text("agent\(k)",left+45.75*s,top+20*s);text("agentSub\(k)",left+45.75*s,top+35.75*s,0.62)
                    let pw=37.5*s,ph=15*s,px=cw/2-8.5*s-pw
                    rounded(px,-ph/2,pw,ph,ph/2,0.22);text("active",px+pw/2,3.8*ts,0.92,true)
                } else {
                    let k=i-7,left = -116.5*s/2,top = -75*s/2
                    decoded["ato-logo-\(H.logos[k])"]?.draw(in:CGRect(x:left+8*s,y:top+8*s,width:22*s,height:22*s))
                    text("integration\(k)",left+8*s,top+54*s);text("status\(k)",left+8*s,top+66*s,0.56)
                }
            }
            contents[i]=Image(uiImage:bitmap)
        }
    }
    func text(_ c:GraphicsContext,_ key:String,_ x:Double,_ y:Double,_ shading:GraphicsContext.Shading = .color(.white),center:Bool=false) {
        guard let label=labels[key] else { return }
        var d=c;d.translateBy(x:x-(center ? label.width/2:0),y:y);d.fill(label.path,with:shading)
    }
}
