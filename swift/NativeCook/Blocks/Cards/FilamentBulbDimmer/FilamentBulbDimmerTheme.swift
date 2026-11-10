import SwiftUI
import simd
import UIKit

enum FilamentBulbDimmerTheme {
    static func color(_ h: Int, _ a: Double = 1) -> Color { Color(.sRGB,red:Double(h>>16&255)/255,green:Double(h>>8&255)/255,blue:Double(h&255)/255,opacity:a) }
    static func mix(_ a:Color,_ b:Color,_ p:Double)->Color {
        var ar:CGFloat=0,ag:CGFloat=0,ab:CGFloat=0,aa:CGFloat=0,br:CGFloat=0,bg:CGFloat=0,bb:CGFloat=0,ba:CGFloat=0
        UIColor(a).getRed(&ar,green:&ag,blue:&ab,alpha:&aa); UIColor(b).getRed(&br,green:&bg,blue:&bb,alpha:&ba)
        let p=max(0,min(1,p))
        func m(_ a:Double,_ b:Double)->Double { pow(pow(a,2.2)+(pow(b,2.2)-pow(a,2.2))*p,1/2.2) }
        return Color(.sRGB,red:m(ar,br),green:m(ag,bg),blue:m(ab,bb))
    }
    static let dots = [0xAF3AFA,0x54F7FC,0xF8BC02,0xFF963F,0xFF6B8B]
    static let names = ["Violet","Ice","Warm","Amber","Rose"]
    static let xs = [62.0,125,201,278,340]
    static let lin: [SIMD3<Double>] = [SIMD3(0.46,0.2,1),SIMD3(0.34,0.86,1),SIMD3(1,0.43,0.11),SIMD3(1,0.3,0.06),SIMD3(1,0.24,0.34)]
    static let accents = [[0x7A52FF,0x9F54FF,0xD884FF,0x9B62F4,0xB884FF,0xAF3AFA],[0x2E9BFF,0x37C6F2,0x86F2FF,0x33A9E0,0x5CD6F5,0x54F7FC],[0xFF8344,0xFE923A,0xFFB71C,0xEE7E5E,0xFFA13A,0xF8BC02],[0xFF6A2C,0xFF7F35,0xFFA445,0xEF6B44,0xFF8A40,0xFF963F],[0xFF4F7C,0xFF6C90,0xFFA0B8,0xEE5F82,0xFF7C9C,0xFF6B8B]]
    static let labs = accents.map { row in row.map { h -> SIMD3<Double> in
        func dec(_ c: Int) -> Double { let v=Double(c)/255; return v<=0.04045 ? v/12.92:pow((v+0.055)/1.055,2.4) }
        let c=SIMD3(dec(h>>16&255),dec(h>>8&255),dec(h&255))
        let l=cbrt(simd_dot(c,SIMD3(0.4122214708,0.5363325363,0.0514459929)))
        let m=cbrt(simd_dot(c,SIMD3(0.2119034982,0.6806995451,0.1073969566)))
        let s=cbrt(simd_dot(c,SIMD3(0.0883024619,0.2817188376,0.6299787005)))
        return SIMD3(0.2104542553*l+0.793617785*m-0.0040720468*s,1.9779984951*l-2.428592205*m+0.4505937099*s,0.0259040371*l+0.7827717662*m-0.808675766*s)
    } }
    static func accentLin(_ sw: Double,_ kind: Int = 1) -> SIMD3<Double> {
        let v=max(0,min(3.9999,sw)), i=Int(v), k=v-Double(i), c=labs[i][kind]+(labs[i+1][kind]-labs[i][kind])*k
        let l=pow(c.x+0.3963377774*c.y+0.2158037573*c.z,3), m=pow(c.x-0.1055613458*c.y-0.0638541728*c.z,3), s=pow(c.x-0.0894841775*c.y-1.291485548*c.z,3)
        return simd_max(.zero,SIMD3(4.0767416621*l-3.3077115913*m+0.2309699292*s,-1.2684380046*l+2.6097574011*m-0.3413193965*s,-0.0041960863*l-0.7034186147*m+1.707614701*s))
    }
    static func accent(_ sw: Double,_ kind: Int) -> Color {
        let c=accentLin(sw,kind)
        func enc(_ v: Double)->Double { let x=max(0,min(1,v)); return (255*(x<=0.0031308 ? x*12.92:1.055*pow(x,1/2.4)-0.055)).rounded()/255 }
        return Color(.sRGB,red:enc(c.x),green:enc(c.y),blue:enc(c.z))
    }
    static func lightColor(_ heat: Double,_ sw: Double)->SIMD3<Double> {
        let t=(900+1800*pow(max(0,heat),0.6))/100
        let g=max(0,min(1,(99.4708025861*log(t)-161.1195681661)/255))
        let b=t<=19 ? 0:max(0,min(1,(138.5177312231*log(t-10)-305.0447927307)/255))
        let bb=SIMD3(1,pow(g,2.2),pow(b,2.2)), v=max(0,min(3.9999,sw)), i=Int(v)
        let smart=max(0,min(1,(heat-0.3)/0.5)), sm=smart*smart*(3-2*smart)
        let c=bb+(lin[i]+(lin[i+1]-lin[i])*(v-Double(i))-bb)*sm
        return c/max(c.x,c.y,c.z,1e-4)
    }
    static func dotX(_ sx: Double,_ sw: Double)->Double { let s=max(0,min(3.9999,sw)),i=Int(s); return (xs[i]+(xs[i+1]-xs[i])*(s-Double(i)))*sx }
    struct Layout: Equatable {
        let w,h,top,bottom: Double
        var sx: Double { w/402 }
        var k: Double { (h-max(bottom,16)-14-top)/788 }
        func y(_ p: Double)->Double { top+(p-43)*k }
        var card: CGRect { CGRect(x:29*sx,y:y(121),width:344*sx,height:135*k) }
        var chipL: CGRect { CGRect(x:50*sx,y:y(313),width:108*sx,height:50*k) }
        var chipR: CGRect { CGRect(x:244*sx,y:y(313),width:108*sx,height:50*k) }
        var tab: CGRect { CGRect(x:-26*sx,y:y(322),width:92*sx,height:250*k) }
        var R: Double { (y(541)-y(364))/3 }
        var anchorX: Double { w/2 }; var anchorY: Double { card.maxY }
        var restY: Double { y(364)-0.5*R }; var cord: Double { restY-anchorY }
        var cx: Double { 202*sx }; var cy: Double { y(724) }
        var rOut: Double { 130*sx }; var rIn: Double { 82*sx }; var leg: Double { 22*k }
        var dotsY: Double { y(831) }
        var toggle: CGRect { CGRect(x:54*sx,y:y(196),width:82*sx,height:38*k) }
        func arcPoint(_ level: Double)->CGPoint {
            let rm=(rOut+rIn)/2,half=Double.pi*rm,s=pow(max(0,min(1,level)),2.1)*(leg*2+half)
            if s<leg { return CGPoint(x:cx-rm,y:cy+leg-s) }
            if s>leg+half { return CGPoint(x:cx+rm,y:cy+s-leg-half) }
            let a=Double.pi-(s-leg)/half*Double.pi
            return CGPoint(x:cx+rm*cos(a),y:cy-rm*sin(a))
        }
        func levelAt(_ p: CGPoint)->Double {
            let rm=(rOut+rIn)/2,half=Double.pi*rm,s: Double
            if p.y>cy { let down=min(leg,p.y-cy); s=p.x<cx ? leg-down:leg+half+down }
            else { let a=atan2(cy-p.y,p.x-cx); s=leg+(Double.pi-max(0,min(Double.pi,a)))/Double.pi*half }
            return pow(max(0,min(1,s/(leg*2+half))),1/2.1)
        }
        func pose(_ f: FilamentBulbDimmerMotion.Frame)->(h:CGPoint,fil:CGPoint,sag:Double) {
            let len=max(8,cord+f.s),sn=sin(f.th),cs=cos(f.th),hx=anchorX+len*sn,hy=anchorY+len*cs
            return (CGPoint(x:hx,y:hy),CGPoint(x:hx+2.55*R*sn,y:hy+2.55*R*cs),sqrt(3*len*max(0,cord-len)/8))
        }
        func finger(_ f: FilamentBulbDimmerMotion.Frame)->CGPoint {
            if f.fingerAt==0 || f.fingerAt==4 {
                let p=pose(f).h,a=f.fingerAt==0 ? 0.42:0,d=f.fingerAt==0 ? 2.95:3.55
                return CGPoint(x:p.x+(cos(f.th)*a+sin(f.th)*d)*R,y:p.y+(-sin(f.th)*a+cos(f.th)*d)*R)
            }
            if f.fingerAt==1 { return arcPoint(f.level) }
            if f.fingerAt==2 { return CGPoint(x:FilamentBulbDimmerTheme.dotX(sx,f.fingerDot),y:dotsY) }
            return CGPoint(x:toggle.midX,y:toggle.midY)
        }
    }
}
