import SwiftUI
import UIKit
import CoreText

enum FocusInHomeTilesArtwork {
    typealias M=FocusInHomeTilesMotion
    typealias R=FocusInHomeTilesResources
    private static let grain=UIImage(contentsOfFile:Bundle.main.url(forResource:"FocusInHomeTiles-paper-grain",withExtension:"png")!.path)!
    private static var fonts:[String:CTFont]=[:],runs:[String:Run]=[:]
    private struct Run{var paths:[Path];var xs:[Double];var width:Double}
    private static func font(_ size:Double,_ medium:Bool)->CTFont{let key="\(size)/\(medium)";if let f=fonts[key]{return f};let name="FocusInHomeTiles-Figtree-"+(medium ? "Medium":"Regular"),url=Bundle.main.url(forResource:name,withExtension:"ttf")!;let f=CTFontCreateWithGraphicsFont(CGFont(CGDataProvider(url:url as CFURL)!)!,size,nil,nil);fonts[key]=f;return f}
    private static func run(_ value:String,_ size:Double,_ medium:Bool,_ tracking:Double)->Run{let key="\(value)/\(size)/\(medium)/\(tracking)";if let r=runs[key]{return r};let f=font(size,medium),line=CTLineCreateWithAttributedString(NSAttributedString(string:value,attributes:[.font:f,.kern:tracking*size]));var paths=[Path](),xs=[Double]()
        for item in CTLineGetGlyphRuns(line) as! [CTRun]{let n=CTRunGetGlyphCount(item);var ids=[CGGlyph](repeating:0,count:n),positions=[CGPoint](repeating:.zero,count:n);CTRunGetGlyphs(item,CFRange(),&ids);CTRunGetPositions(item,CFRange(),&positions);for i in 0..<n{var flip=CGAffineTransform(scaleX:1,y:-1);paths.append(CTFontCreatePathForGlyph(f,ids[i],nil).map{Path($0.copy(using:&flip)!)} ?? Path());xs.append(positions[i].x)}}
        let r=Run(paths:paths,xs:xs,width:CTLineGetTypographicBounds(line,nil,nil,nil));runs[key]=r;return r
    }
    private static func rgb(_ h:Int)->[Double]{[Double(h>>16&255)/255,Double(h>>8&255)/255,Double(h&255)/255]}
    private static func color(_ h:Int,_ alpha:Double=1)->Color{let v=rgb(h);return Color(.sRGB,red:v[0],green:v[1],blue:v[2],opacity:max(0,min(alpha,1)))}
    private static func text(_ c:inout GraphicsContext,_ value:String,_ x:Double,_ baseline:Double,_ size:Double,_ medium:Bool,_ tint:Color,_ track:Double=0,_ width:Double=0){let r=run(value,size,medium,track),x=x+(width>0 ? (width-r.width)/2 : 0);for i in r.paths.indices{var cc=c;cc.translateBy(x:x+r.xs[i],y:baseline);cc.fill(r.paths[i],with:.color(tint))}}
    static func draw(_ c:inout GraphicsContext,_ f:M.Frame,height:Double,pixels:Double,time:Double){
        var rects=[Double](),pars=[Double](),cols=[Double]()
        for shape in R.shapes{let g=shape.group,q=M.groupGeo(Double(g),f.s,f.fp),focus=M.groupFocus(Double(g),f);var y=shape.rect[1],h=shape.rect[3]
            if shape.bar>=0{h=M.barHeight(Double(shape.bar),f.grow[shape.bar]);y=697-h}
            let x=q.cx+(shape.rect[0]-q.cx)*q.scale,yy=q.cy+(y-q.cy)*q.scale+q.dy,w=shape.rect[2]*q.scale,hh=h*q.scale,visible=yy+hh > -60 && yy<height+60
            rects += [x,yy,w,hh];pars += [shape.radius*q.scale,focus.r,visible ? focus.a : 0,shape.kind];cols += rgb(shape.color)+[M.GROUPS[g].plane<f.fp ? -1:1]
        }
        let data=[0,1/pixels,pixels]+rgb(0xE4E3D3)+rects+pars+cols
        c.fill(Path(CGRect(x:0,y:0,width:402,height:height)),with:.shader(ShaderLibrary.focusInHomeTilesScene(.floatArray(data.map(Float.init)),.image(Image(uiImage:grain)))))
        for g in 0..<8{let group=M.GROUPS[g],q=M.groupGeo(Double(g),f.s,f.fp),focus=M.groupFocus(Double(g),f),top=group.rect[1]+q.dy;if top>940 || top+group.rect[3] < -60{continue}
            var layer=c;layer.translateBy(x:q.cx,y:q.cy+q.dy);layer.scaleBy(x:q.scale,y:q.scale);layer.translateBy(x:-q.cx,y:-q.cy)
            layer.drawLayer{cc in cc.opacity=focus.a*(g==7 ? 0.6+0.4*f.alert : 1);cc.clip(to:Path(CGRect(x:group.rect[0]-44,y:group.rect[1]-44,width:group.rect[2]+88,height:group.rect[3]+88)));if focus.r>0.02{cc.addFilter(.blur(radius:focus.r*0.5))};contents(&cc,g,f)}
        }
        if f.front<1.9{let u=[0,f.front,f.dir,time]+rgb(0x1E2D23);c.fill(Path(CGRect(x:0,y:0,width:402,height:height)),with:.shader(ShaderLibrary.focusInHomeTilesFront(.floatArray(u.map(Float.init)))))}
        let orange=[0,1/pixels,f.ox,f.oy,max(f.ow,0.5),max(f.oh,0.5),f.or,f.oth,f.flood,f.olamp,3*sin(2*Double.pi*0.3*time),0.5+0.5*cos(2*Double.pi*time)]+rgb(0xF25A05)+rgb(0xF8A35E)+rgb(0xE2E1CF)+rgb(0xF7FAE8)
        c.fill(Path(CGRect(x:f.ox-f.ow/2-60,y:f.oy-f.oh/2-60,width:f.ow+120,height:f.oh+120)),with:.shader(ShaderLibrary.focusInHomeTilesOrange(.floatArray(orange.map(Float.init)))))
        if f.glyph>0.001{var glyph=c;glyph.translateBy(x:f.ox-9,y:f.oy+f.oh/2-32);glyph.scaleBy(x:0.75,y:0.75);glyph.stroke(R.paths["power"]!.trimmedPath(from:0,to:f.glyph),with:.color(color(0x1E2D23)),style:StrokeStyle(lineWidth:2.4,lineCap:.round,lineJoin:.round))}
    }
    private static func icon(_ c:inout GraphicsContext,_ name:String,_ x:Double,_ y:Double,_ size:Double,_ tint:Color,_ width:Double=2){guard let p=R.paths[name]else{return};var cc=c;cc.translateBy(x:x-size/2,y:y-size/2);cc.scaleBy(x:size/24,y:size/24);cc.stroke(p,with:.color(tint),style:StrokeStyle(lineWidth:width,lineCap:.round,lineJoin:.round))}
    private static func figure(_ c:inout GraphicsContext,_ digit:Int,_ x:Double,_ y:Double){var cc=c;cc.translateBy(x:x,y:y);cc.scaleBy(x:0.85,y:0.85);cc.stroke(R.paths["digit\(digit)"]!,with:.color(color(0x1F2A22)),style:StrokeStyle(lineWidth:3.4,lineCap:.round,lineJoin:.round))}
    private static func figures(_ c:inout GraphicsContext,_ value:Int,_ right:Double,_ base:Double){let deg=right-22.8,y=base-55.25;figure(&c,value/10%10,deg-85.6,y);figure(&c,value%10,deg-42.8,y);figure(&c,10,deg,y)}
    private static func contents(_ c:inout GraphicsContext,_ g:Int,_ f:M.Frame){
        for t in R.texts where t.group==g{text(&c,t.value,t.x,t.base,t.size,t.medium,color(t.color),t.tracking,t.width)}
        for q in R.icons where q.group==g{icon(&c,q.name,q.x,q.y,q.size,color(q.color))}
        if g==1{figures(&c,24,366,500)}
        if g==3{let deg=340.2,y=100.75
            for pos in 0..<2{let a=pos==0 ? Int(floor(f.degA/10))%10 : Int(floor(f.degA+0.5))%10,b=pos==0 ? Int(floor(f.degB/10))%10 : Int(floor(f.degB+0.5))%10,x=pos==0 ? deg-85.6 : deg-42.8
                if a==b || f.degK<=0 || f.degK>=1{figure(&c,f.degK>=1 ? b:a,x,y)}else{c.drawLayer{cc in cc.opacity=1-f.degK;cc.addFilter(.blur(radius:4*f.degK));figure(&cc,a,x,y+6*f.degK)};c.drawLayer{cc in cc.opacity=f.degK;cc.addFilter(.blur(radius:4*(1-f.degK)));figure(&cc,b,x,y-8*(1-f.degK))}}
            };figure(&c,10,deg,y)
        }
        if g==4{for x in stride(from:47.0,through:310,by:6){c.fill(Path(ellipseIn:CGRect(x:x-1,y:423+f.dip,width:2,height:2)),with:.color(color(0xA9AB9B)))}}
        if g==6{var picker=c;picker.clip(to:Path(roundedRect:CGRect(x:16,y:218,width:370,height:222),cornerRadius:40))
            for i in 0..<5{let u=Double(i)-f.pick,sy=cos(min(abs(u),1.6)*0.62),op=M.clamp01(1.6-abs(u)),ink=M.clamp01(1-abs(u));guard op>0 else{continue};var row=picker;row.translateBy(x:0,y:329+u*44.5);row.scaleBy(x:1,y:sy);row.translateBy(x:0,y:-329);row.opacity=op;row.clip(to:Path(CGRect(x:16,y:303,width:370,height:52)))
                let a=rgb(0xA9AB9B),b=rgb(0x1F2A22),v=(0..<3).map{M.mix(a[$0],b[$0],ink)};text(&row,["Entrance","Kitchen","Living room","Bedroom","Hallway"][i],37,336.5,22,false,Color(.sRGB,red:v[0],green:v[1],blue:v[2]),0,180)
                row.drawLayer{value in let blur=3*M.clamp01(abs(f.pick-floor(f.pick+0.5))*4);if blur>0.01{value.addFilter(.blur(radius:blur))};icon(&value,["timer","hourglassSmall","hourglassSmall","boltSmall","timer"][i],237,329,16,color(0x1F2A22),2.4);text(&value,["2 HR","12 MIN","28 MIN","1.1 KWH","4 HR"][i],258,334.7,16,true,color(0x1F2A22),0.04)}
            }
        }
    }
}
