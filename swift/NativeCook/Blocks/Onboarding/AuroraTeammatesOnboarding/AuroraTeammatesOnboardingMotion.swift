import Foundation

// Value-for-value translation of ato-motion.ts. No animation state is hidden in the drawing.
enum AuroraTeammatesOnboardingMotion {
    typealias L = AuroraTeammatesOnboardingTheme.Layout
    static let LOOP = 9.0, HOLD = 1.5, TRANS = 1.5, SEG = 3.0, RESUME = 2.0
    static let STRIDE = 13, G = 169, SIZE = 206
    enum GL {
        static let phase=169,w0=170,w1=171,w2=172,flare=173,coreX=174,coreY=175,sheetTop=176,dip=177
        static let dotsY=178,dotPos=179,dotStretch=180,buttonBottom=181,sheetMix=182,from=183,to=184,u=185
        static let lightAngle=186,lightIntensity=187,fingerX=188,fingerY=189,touch=190,touched=191,tiltX=192,tiltY=193
        static let dotDim=194,dotFill=195,dotFillA=196,fin=197,finGlow=198,finPop=199,finCheck=200,finSheen=201
        static let sinkX=202,sinkY=203,sinkMix=204,burst=205
    }
    struct Input {
        var vx=0.0,x=0.0,y=0.0,down=0.0,drag=0.0,tiltX=0.0,tiltY=0.0,drift=1.0,holdQ=0.0,autoVis=0.0,fin=0.0
    }
    static func clamp01(_ x: Double) -> Double { min(1,max(0,x)) }
    static func mix(_ a: Double,_ b: Double,_ t: Double) -> Double { a+(b-a)*t }
    static func smooth(_ e0: Double,_ e1: Double,_ x: Double) -> Double {
        let t=clamp01((x-e0)/(e1-e0)); return t*t*(3-2*t)
    }
    static func spring(_ q: Double,_ zeta: Double,_ omega: Double) -> Double {
        if q<=0 { return 0 }; if q>=1 { return 1 }
        let wd=omega*sqrt(1-zeta*zeta),e=exp(-zeta*omega*q)
        let s=1-e*(cos(wd*q)+(zeta*omega/wd)*sin(wd*q)),q8=pow(q,8)
        return s*(1-q8)+q8
    }
    static func wrapPos(_ p: Double) -> Double { ((p.truncatingRemainder(dividingBy:3))+3).truncatingRemainder(dividingBy:3) }
    static func posAt(_ t: Double) -> Double {
        let tt=((t.truncatingRemainder(dividingBy:9))+9).truncatingRemainder(dividingBy:9),k=floor(tt/SEG),tau=tt-k*SEG
        return tau<HOLD ? k:k+(tau-HOLD)/TRANS
    }
    static func clockAt(_ pos: Double) -> Double {
        let p=wrapPos(pos),k=floor(p+1e-6),u=p-k
        return u<1e-4 ? k*SEG:k*SEG+HOLD+u*TRANS
    }
    static func dragToPos(_ dx: Double,_ width: Double) -> Double { -dx/(width*0.85) }
    static func pageOf(_ i: Int) -> Int { i<2 ? 0:i<7 ? 1:2 }
    static func exitRank(_ i: Int) -> Int {
        if i<2 { return i==0 ? 0:1 };if i<7 { return 6-i }
        return i>=11 ? 12-i:i>=9 ? 2+(10-i):4+(8-i)
    }
    static func enterRank(_ i: Int) -> Int { i<2 ? (i==1 ? 0:1):i<7 ? i-2:i-7 }
    static func depthOf(_ i: Int) -> Double {
        if i<2 { return i==1 ? 1:0.72 };if i<7 { return 1-AuroraTeammatesOnboardingTheme.rest[i][3]*0.6 }
        return 0.6+0.2*floor(Double(i-7)/2)
    }
    static func computeScene(_ pos: Double,_ amb: Double,_ l: L,_ reduced: Bool,_ finger: Input) -> [Double] {
        var out=Array(repeating:0.0,count:SIZE)
        let p=wrapPos(pos)
        var from=Int(floor(p+1e-6)),u=p-Double(from)
        if u<1e-5 { u=0 };if from>=3 { from=0 }
        let to=(from+1)%3
        let sheetQ=reduced ? smooth(0,1,u):spring(clamp01((u-0.22)/0.55),0.78,10)
        let sheetTop=mix(l.sheetTop[from],l.sheetTop[to],sheetQ)
        let flareRaw=exp(-pow((u-0.34)/0.14,2))
        let flare=reduced ? 0:flareRaw*smooth(0,0.08,u)*(1-smooth(0.92,1,u))
        let blend=smooth(0.22,0.78,u)
        let reach=l.W/2-l.half-l.sheetCorner-6
        let pull=max(-reach,min(reach,(finger.x-l.cx)*0.6))
        let nx=reduced ? l.cx:l.cx+pull*finger.drag,ny=sheetTop+l.depth+18*l.S
        let tX=reduced ? 0:finger.tiltX+finger.drift*0.45*sin(2 * .pi*amb/LOOP)
        let tY=reduced ? 0:finger.tiltY+finger.drift*0.3*sin(4 * .pi*amb/LOOP)
        let finOn=from==2 && u==0 && finger.fin>0
        var touched = -1
        if finger.down>0.001 && u<0.04 {
            for i in 0..<13 where pageOf(i)==from {
                let r=AuroraTeammatesOnboardingTheme.rest[i]
                let hw0=r[5]>0 ? r[5]*l.S/2:26*l.S,hh0=r[6]>0 ? r[6]*l.S/2:26*l.S
                let rot=r[2] * .pi/180,c=cos(-rot),sn=sin(-rot),dx=finger.x-r[0]*l.S,dy=finger.y-r[1]*l.S
                if abs(dx*c-dy*sn)<=hw0 && abs(dx*sn+dy*c)<=hh0 { touched=i }
            }
        }
        let seed=40*l.S
        for i in 0..<13 {
            let o=i*STRIDE,r=AuroraTeammatesOnboardingTheme.rest[i],page=pageOf(i)
            let rx=r[0]*l.S,ry=r[1]*l.S,rr=r[2] * .pi/180,ra=r[4],rw=r[5]*l.S,rh=r[6]*l.S
            let a=i<2 ? 3.2:i<7 ? 1.4:1.8,ph=Double(i)*1.7
            let by=a*l.S*(sin(2 * .pi*3*amb/LOOP+ph)-sin(ph))
            let sw=((i<2 ? 1.6:0.7) * .pi/180)*(sin(2 * .pi*2*amb/LOOP+Double(i)*2.3)-sin(Double(i)*2.3))
            var tx=rx,ty=ry+by,rot=rr+sw,rotX=0.0,scale=1.0,alpha=ra,hw=rw/2,hh=rh/2,content=1.0,unfold=1.0,exitQ=0.0,enterQ=0.0
            if page==from && u>0 {
                if reduced { alpha=ra*(1-smooth(0,0.45,u));content=alpha }
                else {
                    let q=clamp01((u-0.03*Double(exitRank(i)))/0.26),path=q*q*(2.4*q-1.4),down=max(0,path)
                    exitQ=q;tx=mix(rx,nx,path);ty=mix(ry+by,ny,path);rot=(rr+sw)*(1-0.7*down)
                    rotX=62 * .pi/180*down;scale=mix(1,0.45,down);alpha=ra*(1-smooth(0.78,1,q))
                    let fold=smooth(0.05,0.6,q)
                    unfold=1-fold;hw=mix(rw/2,seed/2,fold);hh=mix(rh/2,seed/2,fold);content=1-smooth(0,0.3,q)
                }
            } else if page==to && u>0 {
                if reduced { alpha=ra*smooth(0.45,1,u);content=alpha }
                else {
                    let s0=0.22+0.055*Double(enterRank(i)),d=min(0.52,1-s0),q=clamp01((u-s0)/d)
                    enterQ=q
                    let m=spring(q,0.74,11),arc=sin(.pi*clamp01(m))
                    tx=mix(nx,rx,m)+(rx-nx)*0.18*arc;ty=mix(ny,ry+by,m);rot=(rr+sw)*spring(q,0.7,11)
                    rotX=72 * .pi/180*(1-spring(q,0.52,12));scale=mix(0.5,1,spring(q,0.7,11));alpha=ra*smooth(0,0.12,q)
                    let f=spring(clamp01((q-0.08)/0.92),0.78,10)
                    unfold=f;hw=mix(seed/2,rw/2,f);hh=mix(seed/2,rh/2,f);content=smooth(0.42,0.85,q)
                }
            } else if page != from {
                alpha=0;content=0;tx=nx;ty=ny;scale=0.5;hw=seed/2;hh=seed/2;unfold=0
            }
            if i==touched && !reduced {
                let lift=finger.down*(1-smooth(0,0.04,u)),hwT=hw>0 ? hw:40*l.S,hhT=hh>0 ? hh:40*l.S
                rotX+=(clamp01((finger.y-ty)/hhT*0.5+0.5)-0.5)*2*9 * .pi/180*lift
                scale*=1+0.07*lift;ty-=9*l.S*lift
                out[o+9]=(clamp01((finger.x-tx)/hwT*0.5+0.5)-0.5) * -2*11 * .pi/180*lift
            } else if touched>=0 && page==from && !reduced {
                let back=finger.down*(1-smooth(0,0.04,u));scale*=1-0.025*back;alpha*=1-0.3*back;content*=1-0.2*back
            }
            var drain=0.0
            if finOn && page==2 && !reduced {
                let q=clamp01((finger.fin-0.05*Double(12-i))/0.42)
                exitQ=q;drain=sin(.pi*q);scale*=1-0.035*drain;content*=1-0.45*drain;alpha*=1-0.12*drain;ty+=4*l.S*drain
            }
            let dp=depthOf(i)
            tx+=tX*7*l.S*dp;ty+=tY*5*l.S*dp;rotX+=(-tY*7 * .pi/180)*dp
            out[o]=tx;out[o+1]=ty;out[o+2]=rot;out[o+3]=rotX;out[o+4]=scale;out[o+5]=alpha;out[o+6]=hw;out[o+7]=hh;out[o+8]=content
            let lean=reduced ? 0:clamp01(abs(finger.vx)/1800)*(finger.vx<0 ? -1:1)
            if i != touched { out[o+9]=lean*16 * .pi/180 };out[o+9]+=(tX*9 * .pi/180)*dp
            let stretch=1-unfold
            out[o+10]=reduced ? 4:4+46*stretch*(1-stretch)*4*(u>0 ? 1:0)+36*drain
            out[o+11]=exitQ;out[o+12]=enterQ
        }
        out[GL.phase]=2 * .pi*amb/LOOP
        out[GL.w0]=from==0 ? 1-blend:to==0 ? blend:0
        out[GL.w1]=from==1 ? 1-blend:to==1 ? blend:0
        out[GL.w2]=from==2 ? 1-blend:to==2 ? blend:0
        out[GL.flare]=flare;out[GL.coreX]=nx;out[GL.coreY]=sheetTop+l.coreDy;out[GL.sheetTop]=sheetTop
        let speed=clamp01(abs(finger.vx)/1500)
        out[GL.dip]=l.depth+8*l.S*flare+(reduced ? 0:(4+10*speed)*l.S*max(0,finger.drag))
        out[GL.dotsY]=sheetTop+mix(l.dotsCentre[from],l.dotsCentre[to],sheetQ)
        let dq=reduced ? smooth(0,1,u):clamp01((u-0.3)/0.45),dm=reduced ? dq:spring(dq,0.8,11)
        out[GL.dotPos]=mix(Double(from),Double(to),dm);out[GL.dotStretch]=reduced ? 0:sin(.pi*dq)
        let av=reduced ? 0:finger.autoVis
        out[GL.dotDim]=av*(u>0 ? dm:1);out[GL.dotFill]=u>0 ? 1:finger.holdQ
        out[GL.dotFillA]=u>0 ? 0:av*smooth(0,0.06,finger.holdQ)
        let buttonBottom=mix(l.buttonBottom[from],l.buttonBottom[to],sheetQ)
        out[GL.buttonBottom]=buttonBottom;out[GL.sheetMix]=sheetQ;out[GL.from]=Double(from);out[GL.to]=Double(to);out[GL.u]=u
        let toward=atan2(finger.x-l.cx,-(finger.y-l.H*0.3))*180 / .pi
        let delta=(((toward-180+540).truncatingRemainder(dividingBy:360)+360).truncatingRemainder(dividingBy:360))-180
        var angle=180+delta*finger.down+40*tX
        if angle>180 { angle-=360 }
        out[GL.lightAngle]=angle;out[GL.lightIntensity]=42+30*finger.down+20*flare
        out[GL.fingerX]=finger.x;out[GL.fingerY]=finger.y;out[GL.touch]=finger.down;out[GL.touched]=Double(touched)
        out[GL.tiltX]=tX;out[GL.tiltY]=tY
        let page3=from==2 && finger.fin>0,fin=page3 ? finger.fin:0,after=1-smooth(0,0.3,u)
        out[GL.fin]=fin
        out[GL.finGlow]=reduced ? 0:(exp(-pow((fin-0.5)/0.13,2))+0.3*smooth(0.5,0.75,fin))*after
        out[GL.finPop]=reduced || !page3 ? 0:0.055*exp(-pow((fin-0.52)/0.07,2))
        out[GL.finCheck]=smooth(0.46,0.6,fin)*after
        out[GL.finSheen]=reduced ? 0:clamp01((fin-0.5)/0.3)*(u>0 ? 0:1)
        out[GL.sinkX]=l.cx;out[GL.sinkY]=l.H-buttonBottom-l.buttonH/2;out[GL.sinkMix]=finOn && !reduced ? 1:0
        out[GL.burst]=finOn && !reduced ? clamp01((fin-0.5)/0.45):0
        return out
    }
}
