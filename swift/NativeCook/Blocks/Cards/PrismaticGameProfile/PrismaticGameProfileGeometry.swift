import UIKit

/// Pure RN-authority geometry. Every actor, material and hit test uses this map.
enum PrismaticGameProfileGeometry {
    struct Rect: Equatable {
        var x: Double; var y: Double; var width: Double; var height: Double; var radius: Double = 0
        var cg: CGRect { CGRect(x:x,y:y,width:width,height:height) }
        var midX: Double { x+width/2 }; var midY: Double { y+height/2 }
    }
    struct Origin: Equatable { var x: Double; var y: Double; var size: Double }
    struct Light { var material = -1; var phase = 0.0; var power = 0.0 }
    struct Frame {
        var rail=1.0; var time=0.0; var light=Light(); var panel=0.0; var kind=0; var reduced=false; var active=true
    }
    struct Formation { var body:Double;var portrait:Double;var name:Double;var handle:Double;var skills:Double;var score:Double;var seal:Double;var curl:Double }
    struct Source {
        var fontScale=1.0
        var avatar=Origin(x:68,y:202,size:88)
        var name=Rect(x:128,y:186,width:241,height:32)
        var handle=Rect(x:128,y:222,width:241,height:20)
        var balances=Rect(x:24,y:262,width:345,height:56,radius:20)
        var balanceStacked=false; var balanceIcon=32.0
        var whiteTop=190.0
        var collection=Rect(x:24,y:344,width:345,height:24)
        var statistics=Rect(x:251,y:334,width:118,height:44,radius:16)
        var rail=Origin(x:196.5,y:468,size:164)
        var railTop=386.0;var pitch=146.0;var sideShrink=68.0
        var skills=Rect(x:130.5,y:556,width:132,height:20)
        var score=Rect(x:130.5,y:576,width:132,height:32)
        var arrowsY=560.0; var separatorY=610.0
        var last=Rect(x:24,y:627,width:345,height:24)
        var game=Rect(x:24,y:663,width:345,height:64)
        var tabs=Rect(x:24,y:746,width:345,height:72)
        var overflow=0.0
    }
    struct Glyph { var width:Double;var height:Double;var x:Double;var y:Double;var scale:Double }
    struct Identity {
        var avatarY=298.0;var avatarScale=2.05;var portraitTop=163.0;var portraitHeight=270.0
        var name=Glyph(width:120,height:38,x:130.5,y:469.9,scale:1.1)
        var handle=Glyph(width:99,height:18,x:117.96,y:504.52,scale:1.08)
        var skills=Glyph(width:30,height:17,x:81.75,y:564.775,scale:1.15)
        var score=Glyph(width:49,height:26,x:113.5,y:604.55,scale:2)
        var sealX=286.5;var sealScale=1.05;var headingTop=109.0
        func glyph(_ key:String)->Glyph { switch key {case "name":name;case "handle":handle;case "skills":skills;default:score} }
    }
    struct Pose { var x:Double;var y:Double;var scale:Double;var bounds:Rect }
    static func clamp(_ x:Double,_ lo:Double=0,_ hi:Double=1)->Double { max(lo,min(hi,x)) }
    static func mix(_ a:Double,_ b:Double,_ t:Double)->Double { a+(b-a)*t }
    static func smooth(_ x:Double)->Double { let t=clamp(x);return t*t*(3-2*t) }
    static func step(_ p:Double,_ a:Double,_ b:Double)->Double { smooth((p-a)/(b-a)) }
    /// Same cubic-bezier tables as the approved Expo engine.
    static func bezier(_ x:Double,_ x1:Double,_ y1:Double,_ x2:Double,_ y2:Double)->Double {
        let x=clamp(x);if x==0 || x==1{return x}
        func value(_ t:Double,_ a:Double,_ b:Double)->Double { 3*(1-t)*(1-t)*t*a+3*(1-t)*t*t*b+t*t*t }
        var lo=0.0,hi=1.0
        for _ in 0..<30 {let t=(lo+hi)/2;if value(t,x1,x2)<x{lo=t}else{hi=t}}
        return value((lo+hi)/2,y1,y2)
    }
    static func formEase(_ x:Double)->Double { bezier(x,0.77,0,0.175,1) }
    static func contentEase(_ x:Double)->Double { bezier(x,0.23,1,0.32,1) }
    static func formation(_ progress:Double,_ reduced:Bool=false)->Formation {
        let p=clamp(progress)
        if reduced {let e=p>0 ? 1.0:0;return Formation(body:e,portrait:e,name:e,handle:e,skills:e,score:e,seal:e,curl:0)}
        let r=1-step(p,0,0.8)
        return Formation(body:1-r*r*r,portrait:step(p,0.18,0.82),name:step(p,0.24,0.88),handle:step(p,0.28,0.9),skills:step(p,0.3,0.94),score:step(p,0.34,0.96),seal:step(p,0.08,0.97),curl:step(p,0,0.14)*(1-step(p,0.7,0.97)))
    }
    static func surface(_ p:Double,_ origin:Origin,_ reduced:Bool=false)->Rect {
        let t=formation(p,reduced).body,size=max(1,origin.size)
        return Rect(x:mix(origin.x-size/2,36.5,t),y:mix(origin.y-size/2,85,t),width:mix(size,320,t),height:mix(size,590,t),radius:mix(size/2,34,t))
    }
    static func sourceBadge(_ q:Double,_ index:Int,_ g:Source)->Origin {
        let d=min(1,abs(Double(index)-q))
        return Origin(x:g.rail.x+g.pitch*(Double(index)-q),y:g.rail.y,size:g.rail.size-g.sideShrink*d)
    }
    static func avatar(_ p:Double,_ reduced:Bool,_ g:Source,_ l:Identity)->Rect {
        let x=reduced ? (p>0 ? 1.0:0):step(p,0.3,0.56),y=reduced ? (p>0 ? 1.0:0):step(p,0.3,0.8),z=reduced ? (p>0 ? 1.0:0):step(p,0.43,0.8)
        let cx=mix(g.avatar.x,196.5,x),cy=mix(g.avatar.y,l.avatarY,y),s=mix((g.avatar.size+6)/140,l.avatarScale,z)
        return Rect(x:cx-59*s,y:cy-61*s,width:118*s,height:124*s)
    }
    static func sourcePortraitOpacity(_ p:Double,_ reduced:Bool)->Double { reduced ? (p>0 ? 0:1):1-step(p,0.37,0.46) }
    static func identityPose(_ key:String,_ p:Double,_ reduced:Bool,_ g:Source,_ l:Identity)->Pose {
        let f=formation(p,reduced),target=l.glyph(key)
        let start:Rect,phase:Double,startScale:Double
        switch key {
        case "name":start=g.name;phase=f.name;startScale=30/32
        case "handle":start=g.handle;phase=f.name;startScale=14/13
        case "skills":start=g.skills;phase=f.skills;startScale=14/13
        default:start=g.score;phase=f.score;startScale=27/20
        }
        let paired=key=="name" || key=="handle"
        let sourceX=paired ? start.x+target.width*startScale/2:196.5
        let vertical=paired ? (reduced ? (p>0 ? 1.0:0):step(p,0.18,0.43)):phase
        let footer=step(p,0.08,0.20)
        let horizontal=paired ? 1-pow(1-vertical,2):reduced ? (p>0 ? 1.0:0):1-pow(1-footer,3)
        let scale=mix(startScale,target.scale,phase)
        let left=mix(sourceX-target.width*startScale/2,target.x-target.width*target.scale/2,horizontal)
        let x=left+target.width*scale/2,y=mix(start.midY,target.y,vertical)
        return Pose(x:x,y:y,scale:scale,bounds:Rect(x:x-target.width*scale/2,y:y-target.height*scale/2,width:target.width*scale,height:target.height*scale))
    }
    static func island(_ p:Double,_ reduced:Bool,_ g:Source,_ l:Identity)->Rect {
        let a=avatar(p,reduced,g,l),s=a.width/118,r=s/l.avatarScale,x=a.x+59*s,y=a.y+61*s
        return Rect(x:x+(54.5-196.5)*r,y:y+(l.portraitTop-l.avatarY)*r,width:284*r,height:l.portraitHeight*r,radius:28*r)
    }
    static func avoidance(_ p:Double,_ reduced:Bool,_ g:Source,_ l:Identity)->Rect {
        let rects=[avatar(p,reduced,g,l),island(p,reduced,g,l),identityPose("name",p,reduced,g,l).bounds,identityPose("handle",p,reduced,g,l).bounds]
        let x=rects.map(\.x).min()!,y=rects.map(\.y).min()!
        return Rect(x:x,y:y,width:rects.map{$0.x+$0.width}.max()!-x,height:rects.map{$0.y+$0.height}.max()!-y)
    }
    static func coverage(_ b:Rect,_ r:Rect)->Double {
        let radius=max(0,min(b.radius,b.width/2,b.height/2));var outside = -100000.0
        for x in [r.x,r.x+r.width] {for y in [r.y,r.y+r.height] {
            let qx=abs(x-b.midX)-(b.width/2-radius),qy=abs(y-b.midY)-(b.height/2-radius)
            outside=max(outside,hypot(max(qx,0),max(qy,0))+min(max(qx,qy),0)-radius)
        }}
        return smooth((-outside-6)/8)
    }
    static func sealChannels(_ p:Double,_ reduced:Bool)->(x:Double,y:Double,scale:Double) {
        if reduced {let e=p>0 ? 1.0:0;return(e,e,e)}
        return(step(p,0.1,0.3),step(p,0.08,0.32),step(p,0.08,0.32))
    }
    static func sealLift(_ p:Double,_ reduced:Bool)->Double { reduced ? 0:12*step(p,0.1,0.28)*(1-step(p,0.8,0.97)) }
    static func catchStrength(_ seconds:Double)->Double {
        let age=seconds-5.8;if age<=0 || age>=0.35{return 0}
        return age<=0.09 ? contentEase(age/0.09):1-contentEase((age-0.09)/0.26)
    }
    static func content(_ seconds:Double,_ reduced:Bool)->Double { reduced ? 0:contentEase((seconds-5.8)/0.09)*(1-contentEase((seconds-7.51)/0.09)) }
    static func tilt(_ seconds:Double)->(x:Double,y:Double) {
        let e=step(seconds,5.8,6.1)*(1-step(seconds,7.25,7.6))
        return(sin((seconds-5.8)*3.4)*4.5*e,sin((seconds-5.8)*4.1)*6*e)
    }
    static func underFinger(_ rail:Double,_ origin:Double,_ initial:Light=Light())->Light {
        let nearest=Int(clamp(rail.rounded(),0,2)),offset=rail-Double(nearest),e=smooth(abs(rail-origin)/0.12),focus=1-smooth(abs(offset)/0.5)
        let phase=clamp(0.5+offset*0.72+clamp((rail-origin)*0.15,-0.15,0.15),0.12,0.88)
        let carry=initial.material==nearest ? initial:Light()
        return Light(material:nearest,phase:mix(carry.phase,phase,e),power:mix(carry.power,0.34*focus,e))
    }
    static func finiteLight(_ index:Int,_ age:Double,_ duration:Double,_ carry:Light=Light())->Light {
        let t=clamp(age/duration),h=smooth(age/0.12),power=smooth(t/0.18)*(1-smooth((t-0.57)/0.43))
        return Light(material:index,phase:carry.power<=0 ? t:mix(carry.phase,t,h),power:carry.power<=0 ? power:mix(carry.power,power,h))
    }
    static func gallery(_ seconds:Double,_ reduced:Bool=false)->Frame {
        if reduced {return Frame(reduced:true,active:false)}
        let t=clamp(seconds,0,10);var f=Frame(time:t);var moving = -1,pulse = -1.0,duration=1.0,origin=1.0
        if t>=0.6 && t<1 {f.rail=1-smooth((t-0.6)/0.4);moving=1}
        else if t>=1 && t<2 {f.rail=0;f.light.material=0;pulse=t-1}
        else if t>=2 && t<2.4 {f.rail=smooth((t-2)/0.4);moving=0}
        else if t>=2.4 && t<3.4 {f.light.material=1;pulse=t-2.4;origin=0}
        else if t>=3.4 && t<3.8 {f.rail=1+smooth((t-3.4)/0.4);moving=1}
        else if t>=3.8 && t<4.9 {f.rail=2;f.light.material=2;pulse=t-3.8;duration=1.1}
        else if t>=4.9 && t<5.2 {f.rail=2-smooth((t-4.9)/0.3)}
        else if t>=5.2 && t<5.8 {f.kind=1;f.panel=formEase((t-5.2)/0.6)}
        else if t>=5.8 && t<7.6 {f.kind=1;f.panel=1}
        else if t>=7.6 && t<8.4 {f.kind=1;f.panel=1-formEase((t-7.6)/0.8)}
        if moving>=0 {f.light=underFinger(f.rail,Double(moving))}
        else if pulse>=0 {f.light=finiteLight(f.light.material,pulse,duration,underFinger(f.rail,origin))}
        else {f.light=Light()}
        return f
    }
    static func matrix(_ progress:Double,_ tiltX:Double,_ tiltY:Double,_ reduced:Bool)->CATransform3D {
        let p=reduced ? 0:clamp(progress),lift=96*p,s=1-lift/1000
        let x=tiltX*p*Double.pi/180,y=tiltY*p*Double.pi/180,z = -1.25*p*Double.pi/180
        let cx=cos(x),sx=sin(x),cy=cos(y),sy=sin(y),cz=cos(z),sz=sin(z)
        let a=cy*cz,b = -cy*sz,c=sy,d=cx*sz+sx*sy*cz,e=cx*cz-sx*sy*sz,f = -sx*cy
        let g=sx*sz-cx*sy*cz,h=sx*cz+cx*sy*sz,i=cx*cy
        return CATransform3D(m11:s*a,m12:s*d,m13:s*g,m14:-s*g/1000,m21:s*b,m22:s*e,m23:s*h,m24:-s*h/1000,m31:s*c,m32:s*f,m33:s*i,m34:-s*i/1000,m41:0,m42:0,m43:lift,m44:s)
    }
    static func source(_ fontScale:Double,_ measures:[String:Double]=[:])->Source {
        var g=Source();g.fontScale=fontScale
        func m(_ key:String,_ base:Double)->Double {measures[key] ?? base*fontScale}
        func ceil4(_ n:Double)->Double {ceil(n/4)*4}
        var badge=164.0,group=16.0
        func apply(){
            let nh=m("nameHeight",32),hh=m("handleHeight",20),sh=m("skillsHeight",20),score=m("scoreHeight",32)
            g.name.y=186-min(28,max(0,nh-32));g.name.height=nh;g.handle.y=g.name.y+nh+4;g.handle.height=hh
            g.whiteTop=190-(186-g.name.y);g.balances.y=max(246,g.handle.y+hh+4)+16
            g.balanceStacked=m("balanceWidth",73)+40>148;g.balanceIcon=g.balanceStacked ? 28:32
            g.balances.height=max(56,ceil4((g.balanceStacked ? 32:0)+m("balanceHeight",24)+16))
            let top=g.balances.y+g.balances.height+group
            g.statistics.height=max(44,ceil4(m("statisticsHeight",18)+16));g.statistics.width=max(118,ceil4(m("statisticsWidth",58)+58));g.statistics.x=369-g.statistics.width
            let headingHeight=max(44,g.statistics.height,m("sectionHeight",24));g.collection.y=top+(headingHeight-m("sectionHeight",24))/2;g.collection.height=m("sectionHeight",24)
            g.statistics.y=top+(headingHeight-g.statistics.height)/2
            g.railTop=top+headingHeight+8;g.rail.size=badge;g.rail.y=g.railTop+badge/2;g.sideShrink=badge-96
            let caption=g.railTop+badge+4,captionHeight=max(56,sh+score+4)
            g.skills.y=caption+(captionHeight-sh-score)/2;g.skills.height=sh;g.score.y=g.skills.y+sh;g.score.height=score
            g.arrowsY=caption+(captionHeight-44)/2;g.separatorY=caption+captionHeight
            g.last.y=g.separatorY+17;g.last.height=m("lastHeight",24);g.game.y=g.last.y+g.last.height+12
            g.game.height=max(64,m("gameCopyHeight",44),m("playHeight",20)+24)
            g.tabs.height=max(72,ceil4(36+m("tabHeight",16)+16));g.tabs.y=818-g.tabs.height
            g.overflow=max(0,g.game.y+g.game.height+16-g.tabs.y)
        }
        apply();if g.overflow>0 {badge-=min(68,ceil4(g.overflow));apply()};if g.overflow>0 {group=8;apply()}
        return g
    }
}
