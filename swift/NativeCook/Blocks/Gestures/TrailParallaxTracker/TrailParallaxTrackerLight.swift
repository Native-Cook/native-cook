import Foundation

struct TrailParallaxTrackerPalette {let source:[Float];let destination:[Float];let sky:[[Double]];let count:Int}
enum TrailParallaxTrackerLight {
    private static func lin(_ c:Double)->Double {c<=0.04045 ? c/12.92:pow((c+0.055)/1.055,2.4)}
    private static func enc(_ c:Double)->Double {let v=c.tptClamp(0,1);return v<=0.0031308 ? v*12.92:1.055*pow(v,1/2.4)-0.055}
    private static func rgb(_ h:UInt32)->[Double] {[Double((h>>16)&255)/255,Double((h>>8)&255)/255,Double(h&255)/255]}
    private static func labLinear(_ c:[Double])->[Double] {
        let r=c[0],g=c[1],b=c[2],l=cbrt(0.4122214708*r+0.5363325363*g+0.0514459929*b),m=cbrt(0.2119034982*r+0.6806995451*g+0.1073969566*b),s=cbrt(0.0883024619*r+0.2817188376*g+0.6299787005*b)
        return [0.2104542553*l+0.793617785*m-0.0040720468*s,1.9779984951*l-2.428592205*m+0.4505937099*s,0.0259040371*l+0.7827717662*m-0.808675766*s]
    }
    private static func lab(_ c:UInt32)->[Double] {labLinear(rgb(c).map(lin))}
    private static func labRGB(_ c:[Double])->[Double] {
        let L=c[0],a=c[1],b=c[2],l=pow(L+0.3963377774*a+0.2158037573*b,3),m=pow(L-0.1055613458*a-0.0638541728*b,3),s=pow(L-0.0894841775*a-1.291485548*b,3)
        return [4.0767416621*l-3.3077115913*m+0.2309699292*s,-1.2684380046*l+2.6097574011*m-0.3413193965*s,-0.0041960863*l-0.7034186147*m+1.707614701*s]
    }
    static func hour(_ distance:Double)->Double {
        let k=TrailParallaxTrackerWorld.hourKeys;if distance<=k[0][0] {return k[0][1]}
        for i in 1..<k.count where distance<=k[i][0] {return k[i-1][1]+(k[i][1]-k[i-1][1])*(distance-k[i-1][0])/(k[i][0]-k[i-1][0])};return k.last![1]
    }
    private static func light(_ row:Double)->[Double] {let i=min(Int(floor(row)),3),k=(row-Double(i)).tptClamp(0,1),h=TrailParallaxTrackerWorld.hours;return (0..<4).map{h[i][$0]+(h[i+1][$0]-h[i][$0])*k}}
    private static func relight(_ value:[Double],_ l:[Double],_ n:[Double])->[Double] {let cs=l[3]/n[3];return labRGB([value[0]+l[0]-n[0],value[1]*cs+l[1]-n[1],value[2]*cs+l[2]-n[2]])}
    private static let fills=TrailParallaxTrackerWorld.biomes.map{$0.fills.map(lab)}
    private static let skies=TrailParallaxTrackerWorld.biomes.map{$0.sky.map(lab)}
    private static let blues:[ [Double] ]=[UInt32(0x1E2850),0x27335E,0x3A4675].map(lab)
    static func palette(_ biome:Int,row:Double)->TrailParallaxTrackerPalette {
        let b=TrailParallaxTrackerWorld.biomes[biome],L=light(row),N=TrailParallaxTrackerWorld.hours[b.native]
        var source=[Float](),destination=[Float](),sky=[[Double]]()
        for i in 0..<6 {if i<b.fills.count {source += rgb(b.fills[i]).map(Float.init);destination += relight(fills[biome][i],L,N).map(enc).map(Float.init)}else{source += [9,9,9];destination += [0,0,0]}}
        let blue=(row-3).tptClamp(0,1),l3=light(min(row,3))
        for i in 0..<3 {
            var c=relight(skies[biome][i],blue>0 ? l3:L,N)
            if blue>0 {let a=labLinear(c),t=blues[i],glow=sin(Double.pi*blue);c=labRGB([a[0]+(t[0]-a[0])*blue,a[1]+(t[1]-a[1])*blue+[0.035,0.05,0.07][i]*glow,a[2]+(t[2]-a[2])*blue+[-0.02,0,0.035][i]*glow])}
            sky.append(c.map(enc))
        };return TrailParallaxTrackerPalette(source:source,destination:destination,sky:sky,count:b.fills.count)
    }
}
