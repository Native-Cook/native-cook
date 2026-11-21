import Foundation

struct ShapeShiftViewfinderFrame: Codable, Equatable {
    var w=ShapeShiftViewfinderGeometry.rest(0)
    var tapA=300.0*Double.pi/180,dent=0.0,press=1.0,x=0.0,y=0.0,k=1.0,rot=0.0
    var jx=0.0,jy=0.0,dx=1.0,dy=0.0,lift=0.0
    var fr=[0.473,0.42,615.0,0.0]
    var sx=0.0,sy=0.0,sr=0.0,lx=0.0,ly=0.0,shx=0.0,shy=12.0,shr=0.0
    var flip=0.0,iris=1.0,twist=0.0,flash=0.0,blinkD=0.0,blinkA=0.0,dim=1.0,disc=1.0
    var base:UInt32=0x118169,f1c:UInt32=0x118169,f1u = -1.0,f2c:UInt32=0x118169,f2u = -1.0,dot:UInt32=0x118169
    var sheet=0.0,segment=0.0,ring=1.0,dring=0.0,pressIdx = -1.0,pressS=1.0
    var hint=[1.0,0,0],rings=[0.0,0,1,0,0,1],phase=0.0
    var scale:Double {k*press*(1+0.03*(lift-1).ssvClamp(0,1))}
    var lean:Double {(jx*5600*0.012).ssvClamp(-9,9)}
}

enum ShapeShiftViewfinderMath {
    static func seg(_ t:Double,_ a:Double,_ b:Double)->Double {((t-a)/(b-a)).ssvClamp(0,1)}
    static func mix(_ a:Double,_ b:Double,_ k:Double)->Double {a+(b-a)*k}
    static func out(_ k:Double)->Double {1-pow(1-k,3)}
    static func ease(_ k:Double)->Double {k<0.5 ? 4*k*k*k:1-pow(-2*k+2,3)/2}
    static func spring(_ t:Double,_ from:Double,_ to:Double,_ v:Double=0,_ d:Double=19,_ k:Double=195)->Double {
        if t<=0 {return from};let x=from-to,w=sqrt(k),z=d/(2*w)
        if z<1 {let wd=w*sqrt(1-z*z);return to+exp(-z*w*t)*(x*cos(wd*t)+(v+z*w*x)/wd*sin(wd*t))}
        return to+exp(-w*t)*(x+(v+w*x)*t)
    }
    static func step(_ x:Double,_ v:Double,_ target:Double,_ dt:Double,_ d:Double,_ k:Double)->(Double,Double) {
        let a=x-target,w=sqrt(k),z=min(d/(2*w),0.999),wd=w*sqrt(1-z*z),e=exp(-z*w*dt),c=cos(wd*dt),s=sin(wd*dt)
        return (target+e*(a*c+(v+z*w*a)/wd*s),e*(v*c-(k*a+z*w*v)/wd*s))
    }
    static func color(_ a:UInt32,_ b:UInt32,_ k:Double)->UInt32 {
        var out:UInt32=0
        for shift in [16,8,0] {let x=Double((a>>shift)&255),y=Double((b>>shift)&255);out |= UInt32(mix(x,y,k).ssvClamp(0,255).rounded())<<shift};return out
    }
    static func iris(_ age:Double)->[Double] {
        if age<0{return [1,0,0,1,1,0,0]}
        let cu=seg(age,0.11,0.37),contour=age<0.11 ? 0:70*out(cu),alpha=age<0.11 || age>0.37 ? 0:0.85*(1-cu)
        if age<0.11 {let k=pow(age/0.11,3);return [1-0.76*k,40*k,0,1-0.4*out(age/0.11),mix(1,0.86,out(age/0.11)),0,0]}
        if age<0.16 {return [0.24,40,0.85,0.6,0.86,contour,alpha]}
        let o=age-0.16
        return [0.24+0.76*spring(o,0,1),spring(o,40,0),0,mix(0.6,1,out(seg(o,0,0.18))),spring(o,0.86,1,0,30.5,322.3),contour,alpha]
    }
}

enum ShapeShiftViewfinderScore {
    static let loop=10.0,morphs=[0.7,1.5,2.3,6.0,7.7,8.8],taps=[300.0,60,180,240,120,0].map{$0*Double.pi/180}
    static func shape(_ t:Double)->Int {var out=0;for (i,m) in morphs.enumerated() {if t>=m {out=i==5 ? 0:i+1}};return out}
    private static func rebound(_ t:Double)->[Double] {
        let length=hypot(117.0,-52),c=137.9,w=2*Double.pi/0.3,d=0.7*w,k=w*w
        return [ShapeShiftViewfinderMath.spring(t-4.35,117/length*c,61,0,d,k),ShapeShiftViewfinderMath.spring(t-4.35,-52/length*c,-42,0,d,k)]
    }
    static func drag(_ t:Double)->[Double] {
        let M=ShapeShiftViewfinderMath.self
        if t<4 || t>=5.55 {return [0,0]}
        if t<4.35 {
            let s:Double
            if t<4.1 {s = -12*M.ease(M.seg(t,4,4.1))}
            else if t<4.27 {s = -12+112.7*pow(M.seg(t,4.1,4.27),1.69)}
            else if t<4.3 {s=100.7+37.2*M.seg(t,4.27,4.3)}else{s=137.9}
            let l=hypot(117.0,-52);return [117/l*s,-52/l*s]
        }
        if t<4.85 {return rebound(t)}
        if t<5.25 {let p=rebound(4.85),k=M.ease(M.seg(t,4.85,5.25));return [M.mix(p[0],-71,k),M.mix(p[1],88,k)]}
        let k=M.ease(M.seg(t,5.25,5.55));return [-71*(1-k),88*(1-k)]
    }
    private static let jelly:[Double] = {
        var out=[Double](),jx=0.0,jy=0.0,vx=0.0,vy=0.0,dx=1.0,dy=0.0,previous=drag(0)
        let dt=1/2400.0,w=2*Double.pi/0.3,d=0.7*w,k=w*w
        for i in 0...24000 {
            if i%20==0 {out += [jx,jy,dx,dy]}
            let p=drag(Double(i)*dt+dt)
            var tx=(p[0]-previous[0])/dt/5600,ty=(p[1]-previous[1])/dt/5600
            previous=p;let m=hypot(tx,ty)
            if m>0.16 {tx=tx/m*0.16;ty=ty/m*0.16}
            if m>0.01 {dx=tx/m;dy=ty/m}
            vx += (k*(tx-jx)-d*vx)*dt;vy += (k*(ty-jy)-d*vy)*dt;jx += vx*dt;jy += vy*dt
        };return out
    }()
    static func frame(_ raw:Double)->ShapeShiftViewfinderFrame {
        let M=ShapeShiftViewfinderMath.self,phase=((raw.truncatingRemainder(dividingBy:10))+10).truncatingRemainder(dividingBy:10),t=phase>=9.6 ? 0:phase
        var f=ShapeShiftViewfinderFrame();f.phase=t
        for q in 0..<12 {
            let tt=t-Double(q)*0.2/11
            for (m,start) in morphs.enumerated() where tt>=start {
                let from=m%6,to=m==5 ? 0:m+1
                for b in 0..<6 {let w=2*Double.pi/(0.42/(1+0.35*Double(b))),s=M.spring(tt-start,0,1,0,w,w*w);f.w[q*36+b*6+from] -= s;f.w[q*36+b*6+to] += s}
            }
        }
        var tap = -1
        for (i,start) in morphs.enumerated() {
            if t>=start-0.1 {tap=i}
            if t>=start-0.1 && t<start {f.press -= 0.04*M.out(M.seg(t,start-0.1,start))}
            if t>=start {f.press -= M.spring(t-start,0.04,0)}
        }
        if tap>=0 {let start=morphs[tap];f.tapA=taps[tap];f.dent=10*(t<start ? M.out(M.seg(t,start-0.1,start-0.01)):max(0,M.spring(t-start,1,0,0,30.5,322.3)));f.rings[0]=t<start ? 0.45:0.45*(1-M.seg(t,start,start+0.24));f.rings[1]=f.tapA;f.rings[2]=M.mix(0.6,1,M.out(M.seg(t,start-0.1,start-0.04)))}
        if t>=3.1 && t<4 {let a=t<3.75 ? 0.45:0.45*(1-M.seg(t,3.75,3.99));f.rings[3]=a;f.rings[4]=Double.pi/4;f.rings[5]=M.mix(0.6,1,M.out(M.seg(t,3.1,3.16)));if f.rings[0]<=0 {f.rings[0]=a;f.rings[1]=5*Double.pi/4;f.rings[2]=f.rings[5]}}
        if t>=3.1 && t<3.7 {f.k=M.mix(1,0.7,M.ease(M.seg(t,3.1,3.7)))}
        else if t>=3.7 && t<5.5 {f.k=M.spring(t-3.7,0.7,0.72)}
        else if t>=5.5 && t<7.6 {f.k=M.spring(t-5.5,0.72,1)}
        else if t>=7.6 && t<8.8 {f.k=M.mix(1,1.26,M.ease(M.seg(t,7.6,7.95)))}
        else if t>=8.8 && t<9.15 {f.k=M.mix(1.26,1.36,M.ease(M.seg(t,8.8,8.98)))}
        else if t>=9.15 {f.k=M.mix(1.36,1,M.ease(M.seg(t,9.15,9.55)))}
        if t>=3.1 && t<4 {f.lift=M.out(M.seg(t,3.1,3.22))}
        else if t>=4 && t<5.3 {f.lift=1+M.out(M.seg(t,4,4.12))}
        else if t>=5.3 && t<5.9 {f.lift=2*(1-M.ease(M.seg(t,5.3,5.9)))}
        let p=drag(t),pa=drag(t+0.004),pb=drag(t-0.004),vx=(pa[0]-pb[0])/0.008,vy=(pa[1]-pb[1])/0.008,m=hypot(vx,vy)
        f.x=p[0];f.y=p[1];if m>1e-3 {let lag=min(m*0.06,10);f.lx = -vx/m*lag;f.ly = -vy/m*lag}
        let jf=t*120,i=min(Int(jf),1199),u=jf-Double(i)
        var j=(0..<4).map{M.mix(jelly[i*4+$0],jelly[(i+1)*4+$0],u)}
        let dm=max(hypot(j[2],j[3]),1e-12),strength=(j[0]*j[2]+j[1]*j[3])/dm,cap=min(1,0.16/max(abs(strength),0.16))
        j[0] *= cap;j[1] *= cap;f.jx=j[0];f.jy=j[1];f.dx=j[2]/dm;f.dy=j[3]/dm
        let a=[0.473,0.42,615.0,0],b=[0.6,0.62,500.0,0],c=[0.43,0.5,560.0,-35]
        let from:[Double],to:[Double],fk:Double
        if t<1.5 {from=a;to=a;fk=0}else if t<4.4 {from=a;to=b;fk=M.ease(M.seg(t,1.5,3))}else if t<8.8 {from=b;to=c;fk=M.ease(M.seg(t,4.4,5.4))}else{from=c;to=a;fk=M.ease(M.seg(t,8.8,9.6))}
        f.fr=(0..<4).map{M.mix(from[$0],to[$0],fk)}
        let angle=2*Double.pi*t/10,envelope=1-M.ease(M.seg(t,9.4,9.6))
        f.sx=(3*sin(2*angle)+sin(5*angle))*envelope;f.sy=(3*sin(3*angle)+0.8*sin(4*angle))*envelope;f.sr=0.4*sin(2*angle)*envelope
        let sh=2*Double.pi*phase/10;f.shx=26*sin(sh)+9*sin(2*sh+1.3);f.shy=12*cos(sh);f.shr=2.5*sin(sh+0.9)
        let iris=M.iris(t-8.3);f.iris=iris[0];f.twist=iris[1];f.flash=iris[2];f.dim=iris[3];f.disc=iris[4];f.blinkD=iris[5];f.blinkA=iris[6]
        for (start,from,to):(Double,UInt32,UInt32) in [(2.4,0x118169,0x2E6E54),(4.27,0x2E6E54,0xB9836B),(7.15,0xB9836B,0xF02FA6),(8.95,0xF02FA6,0x118169)] {
            if t<start {break};let u=(t-start)/0.58
            if u<1 {f.base=from;f.f1c=to;f.f1u=u}else{f.base=to}
            f.dot=M.color(from,to,M.seg(t,start+0.2,start+0.4))
        }
        f.f2c=f.base
        if t>=6.5 && t<7.6 {f.sheet=M.spring(t-6.5,0,1,0,22.1,157.9)}else if t>=7.6 {f.sheet=max(0,M.spring(t-7.6,1,0,0,22.1,157.9))}
        f.segment=t>=6.9 && t<8.6 ? M.spring(t-6.9,0,1,0,30.5,322.3):0
        f.ring=t<7.1 || t>=8.6 ? 1:M.spring(t-7.1,1,0,0,30.5,322.3)
        if t>=7.1 && t<7.18 {f.pressS=M.mix(1,0.92,M.out(M.seg(t,7.1,7.18)))}else if t>=7.18 && t<7.8 {f.pressS=M.spring(t-7.18,0.92,1,0,30.5,322.3)}
        f.pressIdx=t>=7.1 && t<7.8 ? 0:-1
        f.hint=[t<3 ? 1:t<3.2 ? 1-M.out(M.seg(t,3,3.2)):t<9.3 ? 0:M.out(M.seg(t,9.3,9.5)),t<3 ? 0:t<3.9 ? M.out(M.seg(t,3,3.2)):1-M.out(M.seg(t,3.9,4.1)),t<3.9 ? 0:t<5.5 ? M.out(M.seg(t,3.9,4.1)):1-M.out(M.seg(t,5.5,5.7))]
        return f
    }
}
