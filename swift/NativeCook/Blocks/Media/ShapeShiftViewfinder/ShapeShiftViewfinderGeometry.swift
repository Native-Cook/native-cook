import Foundation

struct ShapeShiftViewfinderPoint {var x:Double;var y:Double}

enum ShapeShiftViewfinderGeometry {
    static let names=["clover","heart","flower","cookie","squircle","circle"]
    static let heart:[Double]=[
  0.6704, 0.6864, 0.7255, 0.7687, 0.8061, 0.8391, 0.8685, 0.8946, 0.9175, 0.9377, 0.9555, 0.9708,
  0.9837, 0.9945, 1.0032, 1.0099, 1.0148, 1.0180, 1.0195, 1.0192, 1.0171, 1.0133, 1.0077, 1.0001,
  0.9906, 0.9790, 0.9651, 0.9489, 0.9301, 0.9085, 0.8839, 0.8561, 0.8254, 0.7920, 0.7561, 0.7185,
  0.6800, 0.6417, 0.6045, 0.5688, 0.5357, 0.5058, 0.4784, 0.4533, 0.4308, 0.4105, 0.3922, 0.3758,
  0.3610, 0.3477, 0.3359, 0.3254, 0.3157, 0.3069, 0.2990, 0.2917, 0.2849, 0.2787, 0.2734, 0.2687,
  0.2642, 0.2601, 0.2566, 0.2536, 0.2509, 0.2485, 0.2465, 0.2448, 0.2432, 0.2419, 0.2411, 0.2406,
  0.2404, 0.2405, 0.2409, 0.2417, 0.2427, 0.2439, 0.2457, 0.2480, 0.2504, 0.2531, 0.2563, 0.2599,
  0.2641, 0.2689, 0.2743, 0.2805, 0.2873, 0.2950, 0.3040, 0.3142, 0.3261, 0.3387, 0.3486, 0.3540,
  0.3556, 0.3540, 0.3487, 0.3392, 0.3271, 0.3152, 0.3047, 0.2956, 0.2877, 0.2807, 0.2746, 0.2693,
  0.2647, 0.2606, 0.2568, 0.2536, 0.2509, 0.2485, 0.2464, 0.2446, 0.2433, 0.2423, 0.2414, 0.2408,
  0.2406, 0.2409, 0.2416, 0.2424, 0.2435, 0.2452, 0.2471, 0.2491, 0.2514, 0.2540, 0.2572, 0.2608,
  0.2647, 0.2692, 0.2741, 0.2794, 0.2853, 0.2919, 0.2992, 0.3072, 0.3162, 0.3260, 0.3366, 0.3484,
  0.3616, 0.3764, 0.3928, 0.4111, 0.4313, 0.4537, 0.4788, 0.5062, 0.5363, 0.5696, 0.6052, 0.6424,
  0.6809, 0.7196, 0.7571, 0.7927, 0.8261, 0.8568, 0.8845, 0.9092, 0.9308, 0.9495, 0.9657, 0.9796,
  0.9913, 1.0007, 1.0081, 1.0138, 1.0175, 1.0193, 1.0196, 1.0183, 1.0151, 1.0100, 1.0032, 0.9945,
  0.9837, 0.9708, 0.9555, 0.9377, 0.9173, 0.8940, 0.8678, 0.8385, 0.8054, 0.7678, 0.7245, 0.6857,
]
    static func rest(_ shape:Int)->[Double] {(0..<432).map{($0%6)==shape ? 1:0}}
    static let bands:[[Double]] = {
        let n=192,tau=2*Double.pi,ranges=[(0,0),(1,1),(2,3),(4,7),(8,15),(16,96)]
        var bank=[[Double]]()
        for shape in 0..<6 {
            var r=[Double]()
            for j in 0..<n {
                let t=Double(j)/Double(n)*tau
                let sq=0.86/pow(pow(abs(sin(t)),5)+pow(abs(cos(t)),5),0.2)
                let value:Double
                switch shape {case 0:value=0.74+0.26*pow(abs(cos(2*(t-Double.pi/4))),0.55);case 1:value=heart[j];case 2:value=0.8+0.2*pow(abs(cos(3*t)),0.6);case 3:value=sq*(1+0.035*cos(12*t));case 4:value=sq;default:value=1}
                r.append(value)
            }
            var a=[Double](repeating:0,count:97),b=a
            for k in 0...96 {for j in 0..<n {let p=tau*Double(k*j)/Double(n);a[k] += r[j]*cos(p);b[k] += r[j]*sin(p)};let norm=(k==0 || k==96) ? 1/192.0:2/192.0;a[k] *= norm;b[k] *= norm}
            for (lo,hi) in ranges {var out=[Double](repeating:0,count:n);for k in lo...hi {for j in 0..<n {let p=tau*Double(k*j)/Double(n);out[j] += a[k]*cos(p)+b[k]*sin(p)}};bank.append(out)}
        };return bank
    }()
    private static func radiiOne(_ w:[Double],_ offset:Int)->[Double] {
        var out=[Double](repeating:0,count:192),deviation=0.0
        for b in 0..<6 {var squares=0.0;for s in 0..<6 {let weight=w[offset+b*6+s];squares += weight*weight;if abs(weight)<1e-5 {continue};let bank=bands[s*6+b];for j in 0..<192 {out[j] += weight*bank[j]}};deviation=max(deviation,abs(1-squares))}
        let strength=min(1,0.625*deviation)
        if strength<1e-8 {return out}
        return (0..<192).map{j in out[j]+strength*((out[(j+191)%192]+2*out[j]+out[(j+1)%192])/4-out[j])}
    }
    static func radii(_ f:ShapeShiftViewfinderFrame)->[Double] {
        var same=true
        for q in 1..<12 {for i in 0..<36 {if abs(f.w[q*36+i]-f.w[(q-1)*36+i])>2e-5 {same=false;break}};if !same {break}}
        let rq=(0..<(same ? 1:12)).map{radiiOne(f.w,$0*36)}
        let scale=f.scale
        return (0..<192).map { j in
            let angle=Double(j)/192*2*Double.pi,d=atan2(sin(angle-f.tapA),cos(angle-f.tapA)),q=abs(d)/Double.pi*11
            let i=min(Int(q),10),k=q-Double(i)
            var r=same ? rq[0][j]:rq[i][j]+(rq[i+1][j]-rq[i][j])*k
            if f.dent>1e-4 {r -= f.dent/(150*max(scale,0.05))*exp(-d*d/(2*pow(26*Double.pi/180,2)))}
            return r
        }
    }
    private static func wall(_ p:ShapeShiftViewfinderPoint,_ h:Double)->(ShapeShiftViewfinderPoint,Double) {
        let dx=p.x-195,dy=p.y-h/2,ax=abs(dx)-(195-4-40),ay=abs(dy)-(h/2-4-40)
        var nx=0.0,ny=0.0,sd=0.0
        if ax>0 && ay>0 {let l=hypot(ax,ay);sd=l-40;nx=(dx<0 ? -1:1)*ax/l;ny=(dy<0 ? -1:1)*ay/l}
        else if ax>ay {sd=ax-40;nx=dx<0 ? -1:1}else{sd=ay-40;ny=dy<0 ? -1:1}
        let soft=sd<=(-10) ? sd:(-10)*exp(-(sd+10)/10)
        return (ShapeShiftViewfinderPoint(x:p.x+nx*(soft-sd),y:p.y+ny*(soft-sd)),sd)
    }
    static func points(_ f:ShapeShiftViewfinderFrame,_ height:Double)->[ShapeShiftViewfinderPoint] {
        let radii=radii(f),angle=(f.rot+f.lean)*Double.pi/180,ca=cos(angle),sa=sin(angle),cx=195+f.x,cy=height/2+f.y,scale=f.scale
        let es=f.jx*f.dx+f.jy*f.dy,stretch=abs(es)<0.002 ? 0:es.ssvClamp(-0.16,0.16),dir=atan2(f.dx,-f.dy)
        var points=(0..<192).map { j -> ShapeShiftViewfinderPoint in
            let ph=Double(j)/192*2*Double.pi+f.twist*Double.pi/180,a=ph+angle-dir
            let r=radii[j]*150*f.iris*(1+stretch*cos(2*a)-0.5*stretch*cos(a)),x=r*sin(ph),y = -r*cos(ph)
            return ShapeShiftViewfinderPoint(x:cx+scale*(x*ca-y*sa),y:cy+scale*(x*sa+y*ca))
        }
        var deepest = -Double.infinity,nx=0.0,ny=0.0
        for p in points {let (q,sd)=wall(p,height);if sd>deepest {deepest=sd;let x=p.x-q.x,y=p.y-q.y,m=max(hypot(x,y),1e-10);nx=x/m;ny=y/m}}
        if deepest>40 {for j in 0..<192 {points[j].x -= nx*(deepest-40);points[j].y -= ny*(deepest-40)}}
        var out=[ShapeShiftViewfinderPoint](),near=0,hits=0,pushX=0.0,pushY=0.0
        for p in points {let (q,sd)=wall(p,height);out.append(q);if sd > -10 {near += 1};if sd>0 {hits += 1;pushX += q.x-p.x;pushY += q.y-p.y}}
        if near==0 {return points}
        var area0=0.0,area1=0.0
        for j in 0..<192 {let i=(j+1)%192;area0 += points[j].x*points[i].y-points[i].x*points[j].y;area1 += out[j].x*out[i].y-out[i].x*out[j].y}
        area0=abs(area0)/2;area1=abs(area1)/2
        let loss=area0-area1,pm=hypot(pushX,pushY)
        if hits>0 && loss>0.5 && pm>1e-6 {
            let bulge=1+0.6*loss/max(area0,1),nx=pushX/pm,ny=pushY/pm,tx = -ny,ty=nx
            for j in 0..<192 {let u=(out[j].x-cx)*tx+(out[j].y-cy)*ty,v=(out[j].x-cx)*nx+(out[j].y-cy)*ny;out[j]=wall(ShapeShiftViewfinderPoint(x:cx+tx*u*bulge+nx*v,y:cy+ty*u*bulge+ny*v),height).0}
        };return out
    }
}

extension Double {func ssvClamp(_ a:Double,_ b:Double)->Double {Swift.min(Swift.max(self,a),b)}}
