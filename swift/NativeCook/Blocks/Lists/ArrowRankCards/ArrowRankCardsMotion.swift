import Foundation

/// The RN trivision timeline. Every pose is a pure function of the same nine-second clock.
enum ArrowRankCardsMotion {
    typealias Frame=ArrowRankCardsFrame
    static let loop=9.0
    static func clamp(_ v: Double,_ a: Double=0,_ b: Double=1)->Double { max(a,min(b,v)) }
    static func seg(_ t: Double,_ a: Double,_ b: Double)->Double { clamp((t-a)/(b-a)) }
    static func mix(_ a: Double,_ b: Double,_ p: Double)->Double { a+(b-a)*p }
    static func smooth(_ p: Double)->Double { p*p*(3-2*p) }
    static func wrap(_ i: Int)->Int { ((i%3)+3)%3 }
    static func heading(_ i: Int)->Double { [0.0,90,180][wrap(i)] }
    static func pop(_ p: Double)->Double { let u=clamp(p); if u==0 || u==1 { return u }; return 1+2.25*pow(u-1,3)+1.25*pow(u-1,2) }
    static func slats(_ p: Double)->[Double] { (0..<9).map { clamp(0.5+(-2+13*p-Double($0)-0.5)/5) } }
    static func rest(_ index: Int=0)->Frame {
        var f=Frame(); f.index=wrap(index); f.next=wrap(f.index+1); f.head=heading(index); f.tail=f.head
        f.axis=f.head==90 || f.head==270 ? 1 : 0; f.reverse=f.head==180 || f.head==270 ? 1 : 0; return f
    }
    static func shortest(_ from: Double,_ to: Double)->Double { let d=(to-from+540).truncatingRemainder(dividingBy: 360)-180; return from+(d == -180 ? 180 : d) }
    static func beckon(_ t: Double,_ from: Double=0)->Double { let u=t-from; return u>0 && u<0.56 ? 5*pow(sin(.pi*(u.truncatingRemainder(dividingBy: 0.28)/0.28)),2) : 0 }
    static func release(_ held: Frame,_ elapsed: Double,_ commit: Bool,_ velocity: Double=0)->Frame {
        var first=0,last=8
        while first<8 && held.q[first]>=1 { first+=1 }
        while last>0 && held.q[last]<=0 { last-=1 }
        var f=held
        f.q=held.q.enumerated().map { i,v in
            let order=Double(max(0,commit ? i-first : last-i)), k=pop(seg(elapsed,order*0.028,order*0.028+0.26))
            return mix(v,commit ? 1 : 0,k)
        }
        let roll=seg(elapsed,0.015,0.3), land=smooth(roll), angle=shortest(held.tail,heading(held.next))
        let handoff=clamp(velocity,-3,3)*elapsed*pow(1-seg(elapsed,0,0.3),3)
        f.p=mix(held.p,commit ? 1 : 0,land)+handoff; f.drum=mix(held.drum,commit ? 1 : 0,land)
        f.stretch=held.stretch*(1-pop(seg(elapsed,0,0.26)))
        f.head=commit ? mix(held.head,angle,pop(seg(elapsed,0.06,0.56))) : held.head
        f.tail=commit ? mix(held.tail,angle,pop(seg(elapsed,0.26,0.56))) : held.tail
        f.ticks=held.ticks-(commit ? 12*land : 0); f.beckon=0; f.shake=0; f.twitch=0
        f.blur=commit ? 24*roll*(1-roll) : 0; return f
    }
    static func turn(_ t: Double,_ index: Int,_ start: Double,_ half: Double,_ end: Double,_ angle: Double,_ nextAngle: Double)->Frame {
        var p=0.0
        if t<half { p=0.55*smooth(seg(t,start,half)) } else if t<end { p=mix(0.55,0.62,seg(t,half,end)) }
        var held=rest(index); held.p=p; held.q=slats(p); held.head=angle; held.tail=angle; held.stretch=36*clamp(p*2); held.ticks = -Double(index)*12
        if t<start { held.beckon=beckon(t,start-0.62); return held }
        if t<end { return held }
        held.p=0.62; held.q=slats(0.62); held.stretch=36
        var f=release(held,t-end,true)
        f.head=mix(angle,nextAngle,pop(seg(t,end+0.06,end+0.56))); f.tail=mix(angle,nextAngle,pop(seg(t,end+0.26,end+0.56))); return f
    }
    static func frame(_ time: Double)->Frame {
        let t=(time.truncatingRemainder(dividingBy: loop)+loop).truncatingRemainder(dividingBy: loop)
        if t<2.3 { return turn(t,0,0.7,1.25,1.5,0,90) }
        if t<4.85 { return turn(t,1,3,3.7,4.05,90,180) }
        if t<6.9 { return turn(t,2,5.6,6.1,6.3,180,360) }
        if t<8.3 {
            let r=(t-6.9)/(1.4/6), tick=min(5,Int(floor(r))), k=r-Double(tick), roll=seg(k,0,0.84), p=smooth(roll)
            var f=rest(tick); f.p=p; f.blur=24*roll*(1-roll)
            f.q=(0..<9).map { pop(seg(k,Double($0)*0.022,0.64+Double($0)*0.022)) }
            f.drum=p; f.head=360+720*smooth(seg(t,6.9,8.3)); f.tail=360+720*smooth(seg(t-0.07,6.9,8.23))
            f.stretch=10*sin(.pi*p); f.ticks = -36-12*r; return f
        }
        var f=rest(); f.beckon=beckon(t,8.3); return f
    }
}
