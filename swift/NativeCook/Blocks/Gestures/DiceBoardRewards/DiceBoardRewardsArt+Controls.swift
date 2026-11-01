import SwiftUI

extension DiceBoardRewardsArt {
  var rollWidth: Double { 56+20+8+resources.label("Roll",18,"FFFFFF").width }
  func controls(_ c: GraphicsContext,_ rolls: Int,_ pressed: Bool) {
    let y = g.topInset+8
    ellipse(c,16,y,44,44,"FFFFFF")
    text(c,String(UnicodeScalar(62026)!),22,"2E2A3D",38,y+11,center:true,icon:true)
    let coin = resources.label("2,480",14,"2E2A3D")
    let coinW = 24+16+6+coin.width
    let title = resources.label("Lucky Lap",26,"2E2A3D",title:true)
    let gap = (g.width-32-44-coinW-title.width)/2
    text(c,"Lucky Lap",26,"2E2A3D",16+44+gap,y+(44-title.height)/2,title:true)
    let coinX = g.width-16-coinW
    rect(c,coinX,y+5,coinW,34,"FFFFFF",17)
    ellipse(c,coinX+12,y+14,16,16,"FFD766")
    ellipse(c,coinX+15,y+17,10,10,"F5B731")
    text(c,"2,480",14,"2E2A3D",coinX+34,y+(44-coin.height)/2)
    text(c,"Land on a coupon to win it for your basket",14,"8A8098",g.cx,g.topInset+62,center:true)
    text(c,"Flick the die up to roll",13,"A196B0",g.cx,g.trayY+52,center:true)
    let by = g.height-g.bottomInset-16-56
    let counter = resources.label("\(rolls) rolls left today",15,"5B5270")
    text(c,"\(rolls) rolls left today",15,"5B5270",20,by+(56-counter.height)/2)
    let bx = g.width-20-rollWidth
    var button = c
    if pressed {
      button.translateBy(x:bx+rollWidth/2,y:by+28); button.scaleBy(x:0.96,y:0.96)
      button.translateBy(x:-bx-rollWidth/2,y:-by-28)
    }
    rect(button,bx,by,rollWidth,56,"D94A3A",28)
    // A bottom border occupies the last 4 pt of the rounded button, as in RN.
    var face = button
    face.clip(to:Path(CGRect(x:bx,y:by,width:rollWidth,height:52)))
    rect(face,bx,by,rollWidth,56,"FF6B5A",28)
    text(button,String(UnicodeScalar(62116)!),20,"FFFFFF",bx+28,by+16-1.0/3,icon:true)
    let roll = resources.label("Roll",18,"FFFFFF")
    text(button,"Roll",18,"FFFFFF",bx+56,by+(52-roll.height)/2-1.0/3)
  }
  func toast(_ c: GraphicsContext,_ o: M.Out) {
    let current = M.rollAt(o.th,o.u).toast
    let previous = o.uPrev > 0 ? M.rollAt(o.prev,o.uPrev).toast : 0
    let v = max(current,previous)
    guard v > 0 else { return }
    let index = M.toastIndex(o)
    let fallback = current > 0 ? (o.th.from+o.th.steps)%12 : (o.prev.from+o.prev.steps)%12
    let label = M.prizes[index >= 0 ? index : fallback]
    let l = resources.label(label,15,"2E2A3D")
    let w = 36+18+8+l.width, y = g.topInset+96
    var cv = c
    cv.opacity = v
    cv.translateBy(x:g.cx,y:y+22+(1-v)*(-16))
    cv.scaleBy(x:0.94+0.06*v,y:0.94+0.06*v)
    cv.translateBy(x:-w/2,y:-22)
    var shadow = cv
    shadow.addFilter(.shadow(color:R.color("2D2550",0.12),radius:14,x:0,y:6))
    rect(shadow,0,0,w,44,"FFFFFF",22)
    text(cv,String(UnicodeScalar(62698)!),18,"FF6B5A",18,13,icon:true)
    text(cv,label,15,"2E2A3D",44,(44-l.height)/2)
  }
}
