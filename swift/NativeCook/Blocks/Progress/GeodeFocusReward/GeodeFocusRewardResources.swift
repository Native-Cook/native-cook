import SwiftUI
import UIKit
import simd

struct GeodeFocusRewardVertex {
    var point: SIMD3<Double>
    var uv: CGPoint
}
struct GeodeFocusRewardTriangle {
    var a: GeodeFocusRewardVertex
    var b: GeodeFocusRewardVertex
    var c: GeodeFocusRewardVertex
    var cut: Bool
}
struct GeodeFocusRewardMeshPiece {
    var center: SIMD3<Double>
    var pivot: SIMD3<Double>
    var finalCenter: SIMD3<Double>
    var finalRotation: SIMD3<Double>
    var delay: Double
    var polygon: [CGPoint]
    var triangles: [GeodeFocusRewardTriangle]
}
struct GeodeFocusRewardCrack {
    var piece: Int
    var a: SIMD3<Double>
    var b: SIMD3<Double>
}

/// Geometry is prepared separately from bitmap material so motion reads never warm a raster cache.
final class GeodeFocusRewardGeometry {
    static let shared = GeodeFocusRewardGeometry()
    let pieces: [GeodeFocusRewardMeshPiece]
    let cracks: [GeodeFocusRewardCrack]
    let hull: [CGPoint]
    private typealias M = GeodeFocusRewardMath
    static func hash(_ value: Int) -> Double {
        var x = UInt32(truncatingIfNeeded: value) &+ 0x9E3779B9
        x = (x ^ (x >> 16)) &* 0x7FEB352D
        x = (x ^ (x >> 15)) &* 0x846CA68B
        return Double(x ^ (x >> 16)) / 4294967295
    }
    static func height(_ x: Double, _ y: Double) -> Double {
        let r = max(0, 1 - pow(x / 79, 2) - pow(y / 121, 2))
        let dome = 46 * sqrt(r)
        let crown = 22 * exp(-0.5 * (pow((x - 8) / 16, 2) + pow((y + 68) / 12, 2)))
        let right = 17 * exp(-0.5 * (pow((x - 35) / 12, 2) + pow((y + 4) / 27, 2)))
        let lower = 12 * exp(-0.5 * (pow((x + 20) / 15, 2) + pow((y - 49) / 17, 2)))
        let ridgeX = -24 + 8 * sin(y * 0.023)
        let ridge = 7 * exp(-0.5 * pow((x - ridgeX) / 11, 2)) * sqrt(r)
        return 10 + dome + ridge - crown - right - lower
    }
    private init() {
        let sites: [CGPoint] = [CGPoint(x: -42,y: -60),CGPoint(x: 0,y: -85),CGPoint(x: 41,y: -63),CGPoint(x: -56,y: -12),CGPoint(x: 0,y: -29),CGPoint(x: 54,y: -7),CGPoint(x: -43,y: 49),CGPoint(x: 3,y: 33),CGPoint(x: 48,y: 47),CGPoint(x: -16,y: 85),CGPoint(x: -70,y: -42),CGPoint(x: 67,y: 68)]
        let targets: [SIMD3<Double>] = [SIMD3(-130,-100,24),SIMD3(-26,-150,-18),SIMD3(118,-108,28),SIMD3(-138,3,-12),SIMD3(-38,-90,65),SIMD3(140,14,-20),SIMD3(-112,108,20),SIMD3(62,116,67),SIMD3(128,114,-18),SIMD3(-32,150,8),SIMD3(-160,-70,-25),SIMD3(164,116,-32)]
        let angles: [SIMD3<Double>] = [SIMD3(-42,24,-31),SIMD3(12,-51,18),SIMD3(48,27,34),SIMD3(-56,12,-23),SIMD3(-31,-26,24),SIMD3(57,18,41),SIMD3(-41,37,-38),SIMD3(35,44,27),SIMD3(52,-22,49),SIMD3(-22,58,-17),SIMD3(-58,-16,-42),SIMD3(59,33,52)]
        let boundary = (0..<18).map { i -> CGPoint in
            let angle = -.pi / 2 + Double(i) * 2 * .pi / 18
            let r = 0.84 + 0.16 * Self.hash(i + 53)
            return CGPoint(x: cos(angle) * 75 * r + 4 * sin(2 * angle + 0.3), y: sin(angle) * 113 * (0.92 + 0.08 * Self.hash(i + 153)))
        }
        hull = boundary
        var polygons: [[CGPoint]] = []
        for i in 0..<12 {
            var poly = boundary
            for j in 0..<12 where j != i {
                let n = CGPoint(x: sites[j].x - sites[i].x, y: sites[j].y - sites[i].y)
                let d = (sites[j].x * sites[j].x + sites[j].y * sites[j].y - sites[i].x * sites[i].x - sites[i].y * sites[i].y) * 0.5
                poly = Self.clip(poly, normal: n, distance: d)
            }
            polygons.append(poly)
        }
        let areas = polygons.map(Self.area), minimum = areas.min() ?? 0, maximum = areas.max() ?? 1
        var result: [GeodeFocusRewardMeshPiece] = []
        for i in 0..<12 {
            let poly = polygons[i], c = Self.centroid(poly), z = Self.height(c.x, c.y)
            let center = SIMD3(c.x, c.y, z), radial = simd_normalize(SIMD2(c.x + 0.01, c.y))
            let pivot2 = poly.max { ($0.x * radial.x + $0.y * radial.y) < ($1.x * radial.x + $1.y * radial.y) } ?? c
            let pivot = SIMD3(pivot2.x, pivot2.y, Self.height(pivot2.x, pivot2.y))
            let thickness = 10 + 8 * Self.hash(i + 553)
            var triangles: [GeodeFocusRewardTriangle] = []
            let cv = GeodeFocusRewardVertex(point: center, uv: c)
            let cb = GeodeFocusRewardVertex(point: center - SIMD3(0,0,thickness), uv: c)
            for edge in 0..<poly.count {
                let a = poly[edge], b = poly[(edge + 1) % poly.count]
                let av = GeodeFocusRewardVertex(point: SIMD3(a.x,a.y,Self.height(a.x,a.y)),uv:a)
                let bv = GeodeFocusRewardVertex(point: SIMD3(b.x,b.y,Self.height(b.x,b.y)),uv:b)
                var ab = av, bb = bv; ab.point.z -= thickness; bb.point.z -= thickness
                triangles.append(.init(a: cv,b: av,c: bv,cut:false))
                triangles.append(.init(a: cb,b: bb,c: ab,cut:false))
                var fa = av, fb = bv, ba = ab, bc = bb
                fa.uv = CGPoint(x:0,y:0); fb.uv = CGPoint(x:64,y:0)
                ba.uv = CGPoint(x:0,y:24); bc.uv = CGPoint(x:64,y:24)
                triangles.append(.init(a:fa,b:ba,c:bc,cut:true)); triangles.append(.init(a:fa,b:bc,c:fb,cut:true))
            }
            let delay = 0.025 + 0.045 * (1 - (areas[i] - minimum) / max(1,maximum - minimum))
            result.append(.init(center:center,pivot:pivot,finalCenter:targets[i],finalRotation:angles[i],delay:delay,polygon:poly,triangles:triangles))
        }
        pieces = result
        var shared: [(Int,CGPoint,CGPoint)] = []
        for i in 0..<12 {
            let poly = polygons[i]
            for k in 0..<poly.count {
                let a = poly[k], b = poly[(k+1)%poly.count]
                for j in (i+1)..<12 {
                    let other = polygons[j]
                    if other.indices.contains(where: { l in Self.near(a,other[(l+1)%other.count]) && Self.near(b,other[l]) }) { shared.append((i,a,b)); break }
                }
            }
        }
        let aims = [CGPoint(x:0,y:-58),CGPoint(x:-35,y:-8),CGPoint(x:28,y:38),CGPoint(x:0,y:76)]
        var selected: [GeodeFocusRewardCrack] = [], used = Set<Int>()
        for aim in aims {
            let ids = shared.indices.filter { !used.contains($0) }
            if let index = ids.min(by: { lhs,rhs in
                func distance(_ i: Int) -> Double { let e=shared[i];return hypot((e.1.x+e.2.x)/2-aim.x,(e.1.y+e.2.y)/2-aim.y) }
                return distance(lhs) < distance(rhs)
            }) {
                used.insert(index);let e=shared[index]
                selected.append(.init(piece:e.0,a:SIMD3(e.1.x,e.1.y,Self.height(e.1.x,e.1.y)+0.3),b:SIMD3(e.2.x,e.2.y,Self.height(e.2.x,e.2.y)+0.3)))
            }
        }
        cracks = selected
    }
    static func near(_ a: CGPoint,_ b: CGPoint) -> Bool { hypot(a.x-b.x,a.y-b.y)<0.02 }
    static func area(_ p: [CGPoint]) -> Double { abs(p.indices.reduce(0) { s,i in let b=p[(i+1)%p.count];return s+p[i].x*b.y-b.x*p[i].y })*0.5 }
    private static func centroid(_ p: [CGPoint]) -> CGPoint {
        var area=0.0,x=0.0,y=0.0
        for i in p.indices { let a=p[i],b=p[(i+1)%p.count],c=a.x*b.y-b.x*a.y;area+=c;x+=(a.x+b.x)*c;y+=(a.y+b.y)*c }
        return CGPoint(x:x/(3*area),y:y/(3*area))
    }
    private static func clip(_ poly:[CGPoint],normal n:CGPoint,distance d:Double)->[CGPoint] {
        guard !poly.isEmpty else{return []};var result:[CGPoint]=[]
        for i in poly.indices {
            let a=poly[i],b=poly[(i+1)%poly.count],da=a.x*n.x+a.y*n.y-d,db=b.x*n.x+b.y*n.y-d
            if da<=0.000001 {result.append(a)}
            if (da<0 && db>0)||(da>0 && db<0) {let q=da/(da-db);result.append(CGPoint(x:a.x+(b.x-a.x)*q,y:a.y+(b.y-a.y)*q))}
        }
        return result
    }
}

/// Ordinary sRGB artwork, never RGB-packed numeric shader data.
final class GeodeFocusRewardResources {
    static let shared = GeodeFocusRewardResources()
    let rock: Image
    let cut: Image
    let floorLight: Image
    let contact: Image
    let glow: Image
    let cores: [Image]
    let rockRect = CGRect(x:-80,y:-120,width:160,height:240)
    let cutRect = CGRect(x:0,y:0,width:64,height:24)
    let coreRect = CGRect(x:-82,y:-106,width:164,height:212)
    private typealias M = GeodeFocusRewardMath
    private typealias G = GeodeFocusRewardGeometry
    private init() {
        let w=320,h=480
        var bytes=[UInt8](repeating:255,count:w*h*4)
        var heights=[Double](repeating:0,count:w*h)
        var broadField=[Double](repeating:0,count:w*h),fineField=[Double](repeating:0,count:w*h)
        for y in 0..<h {for x in 0..<w {
            let px=Double(x)/2-80,py=Double(y)/2-120,i=y*w+x
            let broad=Self.noise(px*0.035,py*0.035,53),fine=Self.noise(px*0.23,py*0.17,83)
            let ridge=1-abs(2*Self.noise(px*0.085,py*0.073,173)-1)
            let micro=Self.noise(px*0.79,py*0.67,193)
            broadField[i]=broad;fineField[i]=fine
            heights[i]=G.height(px,py)+3*broad+4.5*ridge+1.1*fine+0.35*micro
        }}
        let rockLight=simd_normalize(SIMD3(-0.65,-0.65,0.85))
        for y in 0..<h {for x in 0..<w {
            let px=Double(x)/2-80,py=Double(y)/2-120,i=y*w+x
            let broad=broadField[i],fine=fineField[i],pore=Self.noise(px*0.91,py*0.91,109)
            let dx=heights[y*w+min(w-1,x+1)]-heights[y*w+max(0,x-1)]
            let dy=heights[min(h-1,y+1)*w+x]-heights[max(0,y-1)*w+x]
            let normal=simd_normalize(SIMD3(-dx,-dy,1))
            let diffuse=0.14+0.98*max(0,simd_dot(normal,rockLight))
            let radial=sqrt(pow(px/79,2)+pow(py/121,2))
            let occlusion=0.72+0.28*(1-M.sm(radial,0.72,1.02))
            let base=Self.mix(Self.rgb(0x333D3C),Self.rgb(0x7A8177),0.40+0.35*broad)
            var c=base*diffuse*occlusion*(0.92+0.12*fine+0.06*pore)*(1-0.12*pow(pore,10))
            let chalk=pow(max(0,(pore-0.78)/0.22),5)*0.035
            c=Self.mix(c,Self.rgb(0xA0A395),chalk)
            Self.put(&bytes,(y*w+x)*4,c,1)
        }}
        rock=Self.image(bytes,width:w,height:h)
        var cutBytes=[UInt8](repeating:255,count:256*96*4)
        for y in 0..<96 {for x in 0..<256 {
            let a=Double(x)/4,b=Double(y)/4,n=Self.noise(a*0.16,b*0.23,501),fine=Self.noise(a*0.91,b*0.91,731)
            let c=Self.mix(Self.rgb(0x101916),Self.rgb(0x59615D),0.16+0.55*n)*(0.83+0.25*fine)
            Self.put(&cutBytes,(y*256+x)*4,c,1)
        }}
        cut=Self.image(cutBytes,width:256,height:96)
        var f=[UInt8](repeating:0,count:512*192*4)
        for y in 0..<192 {for x in 0..<512 {
            let px=(Double(x)+0.5)/512*300-150,py=(Double(y)+0.5)/192*100-50
            let outer=exp(-0.5*(pow(px/91,2)+pow(py/16,2)))
            let hole=exp(-0.5*(pow(px/48,2)+pow((py+1)/9,2)))
            Self.put(&f,(y*512+x)*4,Self.rgb(0xCCD1C6),0.14*max(0,outer-hole*0.96)*Self.feather(x,y,512,192,56))
        }}
        floorLight=Self.image(f,width:512,height:192)
        var c=[UInt8](repeating:0,count:192*80*4)
        for y in 0..<80 {for x in 0..<192 {let a=(Double(x)-95.5)/42,b=(Double(y)-39.5)/12;Self.put(&c,(y*192+x)*4,.zero,0.38*exp(-0.5*(a*a+b*b))*Self.feather(x,y,192,80,16))}}
        contact=Self.image(c,width:192,height:80)
        var g=[UInt8](repeating:0,count:256*320*4)
        for y in 0..<320 {for x in 0..<256 {
            let a=(Double(x)+0.5)/256*240-120,b=(Double(y)+0.5)/320*310-155
            let r=sqrt(pow(a/65,2)+pow(b/94,2)),d=max(0,r-1)
            let alpha=0.22*exp(-pow(d*65/12,2)*0.5)+0.06*exp(-pow(d*65/54,2)*0.5)
            Self.put(&g,(y*256+x)*4,Self.rgb(0xDB9328),alpha*(r<1 ? 0.28:1)*Self.feather(x,y,256,320,44))
        }}
        glow=Self.image(g,width:256,height:320)
        cores=Self.makeCores()
    }
    private static func feather(_ x:Int,_ y:Int,_ width:Int,_ height:Int,_ inset:Double)->Double {
        let dx=Double(min(x,width-1-x)),dy=Double(min(y,height-1-y))
        return M.smooth(dx/inset)*M.smooth(dy/inset)
    }
    private static func noise(_ x:Double,_ y:Double,_ seed:Int)->Double {
        let xi=Int(floor(x)),yi=Int(floor(y)),u=M.smooth(x-floor(x)),v=M.smooth(y-floor(y))
        let a=G.hash(xi &* 73856093 ^ yi &* 19349663 ^ seed),b=G.hash((xi+1) &* 73856093 ^ yi &* 19349663 ^ seed)
        let c=G.hash(xi &* 73856093 ^ (yi+1) &* 19349663 ^ seed),d=G.hash((xi+1) &* 73856093 ^ (yi+1) &* 19349663 ^ seed)
        return M.mix(M.mix(a,b,u),M.mix(c,d,u),v)
    }
    private static func triangleNoise(_ x:Double,_ y:Double,_ seed:Int)->Double {
        let xi=Int(floor(x)),yi=Int(floor(y)),u=x-floor(x),v=y-floor(y)
        let a=G.hash(xi &* 73856093 ^ yi &* 19349663 ^ seed)
        let b=G.hash((xi+1) &* 73856093 ^ yi &* 19349663 ^ seed)
        let c=G.hash(xi &* 73856093 ^ (yi+1) &* 19349663 ^ seed)
        let d=G.hash((xi+1) &* 73856093 ^ (yi+1) &* 19349663 ^ seed)
        return u+v<1 ? a+(b-a)*u+(c-a)*v : d+(c-d)*(1-u)+(b-d)*(1-v)
    }
    private static func rgb(_ hex:UInt32)->SIMD3<Double> {SIMD3(Double((hex>>16)&255)/255,Double((hex>>8)&255)/255,Double(hex&255)/255)}
    private static func mix(_ a:SIMD3<Double>,_ b:SIMD3<Double>,_ q:Double)->SIMD3<Double> {a+(b-a)*q}
    private static func put(_ bytes:inout[UInt8],_ i:Int,_ color:SIMD3<Double>,_ alpha:Double) {
        let a=M.clamp(alpha)
        bytes[i]=UInt8(round(M.clamp(color.x)*a*255));bytes[i+1]=UInt8(round(M.clamp(color.y)*a*255));bytes[i+2]=UInt8(round(M.clamp(color.z)*a*255));bytes[i+3]=UInt8(round(a*255))
    }
    private static func image(_ bytes:[UInt8],width:Int,height:Int)->Image {
        let provider=CGDataProvider(data:Data(bytes) as CFData)!
        let cg=CGImage(width:width,height:height,bitsPerComponent:8,bitsPerPixel:32,bytesPerRow:width*4,space:CGColorSpace(name:CGColorSpace.sRGB)!,bitmapInfo:CGBitmapInfo(rawValue:CGImageAlphaInfo.premultipliedLast.rawValue),provider:provider,decode:nil,shouldInterpolate:true,intent:.defaultIntent)!
        return Image(decorative:cg,scale:1)
    }
    private struct CoreVertex {var p:SIMD3<Double>;var uv:SIMD2<Double>}
    private static func makeCores()->[Image] {
        let outline:[SIMD2<Double>]=[SIMD2(-0.10,-1),SIMD2(0.20,-0.995),SIMD2(0.40,-0.94),SIMD2(0.68,-0.64),SIMD2(0.83,-0.35),SIMD2(0.86,-0.15),SIMD2(0.80,0.43),SIMD2(0.60,0.68),SIMD2(0.40,0.85),SIMD2(-0.08,1),SIMD2(-0.59,0.76),SIMD2(-0.86,0.20),SIMD2(-0.70,-0.47),SIMD2(-0.40,-0.86)]
        var rounded:[SIMD2<Double>]=[]
        for i in outline.indices {
            let point=outline[i],prev=outline[(i+outline.count-1)%outline.count],next=outline[(i+1)%outline.count]
            let a=point+(prev-point)*0.13,b=point+(next-point)*0.13
            for j in 0..<3 {let t=Double(j)/2,u=1-t;rounded.append(a*(u*u)+point*(2*u*t)+b*(t*t))}
        }
        let outer=rounded.map {v in CoreVertex(p:SIMD3(v.x*66/0.86,v.y*95,0),uv:SIMD2(v.x*66/0.86,v.y*95))}
        var rings:[[CoreVertex]]=[outer]
        for radius in [0.73,0.45,0.22] {
            let vertices=outer.map {v->CoreVertex in
                let q=radius
                let x=v.p.x*q-3*(1-q),y=v.p.y*q-8*(1-q)
                let z=40*sqrt(max(0,1-pow(x/66,2)-pow(y/95,2)))
                return .init(p:SIMD3(x,y,z),uv:SIMD2(x,y))
            }
            rings.append(vertices)
        }
        let center=CoreVertex(p:SIMD3(-4,-9,40*sqrt(1-pow(4.0/66,2)-pow(9.0/95,2))),uv:SIMD2(-4,-9))
        var triangles:[(CoreVertex,CoreVertex,CoreVertex)]=[]
        for ring in 0..<3 {for i in outer.indices {
            let j=(i+1)%outer.count,a=rings[ring],b=rings[ring+1]
            triangles.append((a[i],a[j],b[i]));triangles.append((a[j],b[j],b[i]))
        }}
        for i in outer.indices {triangles.append((rings[3][i],rings[3][(i+1)%outer.count],center))}
        let materialW=288,materialH=400
        var material=[SIMD3<Double>](repeating:.zero,count:materialW*materialH)
        var rims=[Double](repeating:0,count:materialW*materialH)
        var normals=[SIMD3<Double>](repeating:.zero,count:materialW*materialH)
        var curvedHighlights=[Double](repeating:0,count:materialW*materialH)
        for y in 0..<materialH {for x in 0..<materialW {
            let px=Double(x)/2-72,py=Double(y)/2-100
            let warpX=(noise(px*0.037,py*0.037,201)-0.5)*15
            let warpY=(noise(px*0.043+21,py*0.043-37,219)-0.5)*13
            let u=px+warpX,v=py+warpY
            let scale=8+12*noise(px*0.026,py*0.026,227)
            let coarse=triangleNoise(u/scale,v/scale,231)
            let middle=triangleNoise((u+37)*0.23,(v-21)*0.19,277)
            let fine=triangleNoise(u*0.71,v*0.61,341)
            let fire=0.52*coarse+0.31*middle+0.17*fine
            let warm=M.sm(fire,0.50,0.61)
            let green=mix(rgb(0x123D1B),rgb(0x286D25),0.25+0.75*middle)
            let amber=mix(rgb(0xCE591A),rgb(0xEF9C1E),0.25+0.75*fine)
            var color=mix(green,amber,warm)
            let greenFire=M.sm(middle,0.62,0.82)*(1-warm)
            color=mix(color,rgb(0x91B83D),greenFire*0.62)
            let vein=(1-M.sm(abs(coarse-0.52),0.009,0.035))*(0.25+0.75*fine)
            color=mix(color,rgb(0xEF9C1E),vein*0.28)
            color *= 0.78+0.34*fine
            let dx1=px+22,dy1=py+36,dx2=px-18,dy2=py-62
            let glint1=exp(-0.5*(pow(dx1/0.65,2)+pow(dy1/2.1,2)))
            let glint2=exp(-0.5*(pow(dx2/0.65,2)+pow(dy2/2.4,2)))
            color=mix(color,rgb(0xFFE0A6),M.clamp(glint1*0.75+glint2*0.90))
            let materialIndex=y*materialW+x
            material[materialIndex]=color
            let radial=max(0.015,1-pow(px/66,2)-pow(py/95,2))
            normals[materialIndex]=simd_normalize(SIMD3(px*40/(66*66),py*40/(95*95),sqrt(radial)))
            let curveX = -43+12*pow((py+8)/95,2)
            let streak=exp(-0.5*pow((px-curveX)/0.75,2))
            curvedHighlights[materialIndex]=streak*M.sm(py,-78,-55)*(1-M.sm(py,-2,22))*0.22
            let uv=SIMD2(px,py)
            var edge=Double.infinity
            for j in outline.indices {
                let aa=SIMD2(outline[j].x*66/0.86,outline[j].y*95)
                let bb=SIMD2(outline[(j+1)%outline.count].x*66/0.86,outline[(j+1)%outline.count].y*95)
                let d=bb-aa,t=M.clamp(simd_dot(uv-aa,d)/max(0.001,simd_dot(d,d)))
                edge=min(edge,simd_length(uv-aa-d*t))
            }
            rims[y*materialW+x]=1-M.sm(edge,0.6,1.8)
        }}
        let w=328,h=424,light=simd_normalize(SIMD3(-0.5,-0.65,1.0))
        let halfLight=simd_normalize(light+SIMD3(0,0,1))
        var images:[Image]=[]
        for frame in 0..<14 {
            let yaw = -26.0+Double(frame)*4
            let cosine=cos(yaw * .pi / 180),sine=sin(yaw * .pi / 180)
            var bytes=[UInt8](repeating:0,count:w*h*4),depth=[Double](repeating:-Double.infinity,count:w*h)
            for tri in triangles {
                let verts=[tri.0,tri.1,tri.2],world=verts.map {M.rotate($0.p,SIMD3(yaw,0,0))}
                let screen=world.map {v->SIMD2<Double> in let k=760/(760-v.z);return SIMD2((v.x*k+82)*2,(v.y*k+106)*2)}
                let a=screen[0],b=screen[1],c=screen[2],det=(b.y-c.y)*(a.x-c.x)+(c.x-b.x)*(a.y-c.y)
                if abs(det)<0.001 {continue}
                let x0=max(0,Int(floor(min(a.x,min(b.x,c.x))))),x1=min(w-1,Int(ceil(max(a.x,max(b.x,c.x)))))
                let y0=max(0,Int(floor(min(a.y,min(b.y,c.y))))),y1=min(h-1,Int(ceil(max(a.y,max(b.y,c.y)))))
                if x1<x0 || y1<y0 {continue}
                for y in y0...y1 {for x in x0...x1 {
                    let sampleX = Double(x) + 0.5, sampleY = Double(y) + 0.5
                    let wa: Double = ((b.y-c.y)*(sampleX-c.x)+(c.x-b.x)*(sampleY-c.y))/det
                    let wb: Double = ((c.y-a.y)*(sampleX-c.x)+(a.x-c.x)*(sampleY-c.y))/det
                    let wc: Double = 1-wa-wb
                    if min(wa,min(wb,wc)) < -0.00001 {continue}
                    let z=world[0].z*wa+world[1].z*wb+world[2].z*wc,i=y*w+x
                    if z<depth[i] {continue};depth[i]=z
                    let uv=verts[0].uv*wa+verts[1].uv*wb+verts[2].uv*wc
                    let mx=min(materialW-1,max(0,Int((uv.x+72)*2))),my=min(materialH-1,max(0,Int((uv.y+100)*2)))
                    let materialIndex=my*materialW+mx
                    let localNormal=normals[materialIndex]
                    let normal=SIMD3(localNormal.x*cosine+localNormal.z*sine,localNormal.y,-localNormal.x*sine+localNormal.z*cosine)
                    let diffuse=0.20+0.90*max(0,simd_dot(normal,light))
                    var color=material[materialIndex]*diffuse
                    let specular=pow(max(0,simd_dot(normal,halfLight)),100)*0.38
                    let reflection=specular+curvedHighlights[materialIndex]
                    color=mix(color,rgb(0xFFE0A6),M.clamp(reflection,0,0.65))
                    let rim=rims[materialIndex]
                    color=mix(color,rgb(0xE0A02F)*(0.40+0.48*max(0,simd_dot(normal,light))),rim*0.62)
                    put(&bytes,i*4,color,1)
                }}
            }
            images.append(image(bytes,width:w,height:h))
        }
        return images
    }
}
