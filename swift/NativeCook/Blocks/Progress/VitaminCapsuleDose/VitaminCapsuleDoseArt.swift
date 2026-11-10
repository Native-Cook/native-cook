import CoreText
import SwiftUI
import UIKit

final class VitaminCapsuleDoseResources {
  typealias H=VitaminCapsuleDoseTheme
  static let shared=VitaminCapsuleDoseResources()
  struct Label {let image:UIImage;let width,height:Double}
  private var labels:[String:Label]=[:]
  private var pages:[String:Image]=[:]
  private var nativePages:[String:UIImage]=[:]
  struct Control {let image:Image;let rect,crop:CGRect}
  private var controls:[String:Control]=[:]
  private var logLabels:[String:Image]=[:]
  init() {
    for weight in ["Regular","Medium","SemiBold","Bold"] {
      if let u=Bundle.main.url(forResource:"VitaminCapsuleDose-Poppins-\(weight)",withExtension:"ttf") {CTFontManagerRegisterFontsForURL(u as CFURL,.process,nil)}
    }
  }
  func label(_ text:String,_ weight:String,_ size:Double,_ line:Double)->Label {
    let key="\(text)|\(weight)|\(size)|\(line)"
    if let r=labels[key] {return r}
    let font=UIFont(name:"Poppins-\(weight)",size:size) ?? .systemFont(ofSize:size)
    let paragraph=NSMutableParagraphStyle();paragraph.minimumLineHeight=line;paragraph.maximumLineHeight=line
    var attrs:[NSAttributedString.Key:Any]=[.font:font,.foregroundColor:UIColor.white,.paragraphStyle:paragraph]
    if line>=font.lineHeight {attrs[.baselineOffset]=(line-font.lineHeight)/2}
    let storage=NSTextStorage(string:text,attributes:attrs),manager=NSLayoutManager(),container=NSTextContainer(size:CGSize(width:10000,height:1000))
    manager.usesFontLeading=false;container.lineFragmentPadding=0;manager.addTextContainer(container);storage.addLayoutManager(manager);manager.ensureLayout(for:container)
    let rect=manager.usedRect(for:container),width=ceil(rect.width*3)/3,height=ceil(rect.height*3)/3
    let fmt=UIGraphicsImageRendererFormat();fmt.scale=3
    let image=UIGraphicsImageRenderer(size:CGSize(width:width+16,height:height+16),format:fmt).image { _ in
      manager.drawGlyphs(forGlyphRange:manager.glyphRange(for:container),at:CGPoint(x:8,y:8))
    }
    let r=Label(image:image,width:width,height:height);labels[key]=r;return r
  }
  private func text(_ c:CGContext,_ value:String,_ weight:String,_ size:Double,_ line:Double,_ x:Double,_ y:Double,_ hex:Int,_ center:Bool=false) {
    let r=label(value,weight,size,line)
    let rect=CGRect(x:x-(center ? r.width/2:0)-8,y:y-8,width:r.width+16,height:r.height+16)
    c.saveGState();c.translateBy(x:rect.minX,y:rect.maxY);c.scaleBy(x:1,y:-1)
    c.clip(to:CGRect(origin:.zero,size:rect.size),mask:r.image.cgImage!)
    c.setFillColor(ui(hex).cgColor);c.fill(CGRect(origin:.zero,size:rect.size));c.restoreGState()
  }
  private func ui(_ hex:Int,_ a:Double=1)->UIColor {UIColor(red:Double((hex>>16)&255)/255,green:Double((hex>>8)&255)/255,blue:Double(hex&255)/255,alpha:a)}
  private func round(_ c:CGContext,_ rect:CGRect,_ r:Double,_ color:Int) {c.setFillColor(ui(color).cgColor);c.addPath(UIBezierPath(roundedRect:rect,cornerRadius:r).cgPath);c.fillPath()}
  private func stroke(_ c:CGContext,_ p:CGPath,_ width:Double,_ color:Int) {c.setStrokeColor(ui(color).cgColor);c.setLineWidth(width);c.setLineCap(.round);c.setLineJoin(.round);c.addPath(p);c.strokePath()}
  private func path(_ points:[CGPoint])->CGPath {let p=CGMutablePath();if let first=points.first {p.move(to:first);for pt in points.dropFirst(){p.addLine(to:pt)}};return p}
  func page(_ l:VitaminCapsuleDoseLayout,_ med:Int)->Image {
    let key="\(l)|\(med)";if let p=pages[key]{return p}
    let m=H.meds[med],sx=l.sx,k=l.k
    let fmt=UIGraphicsImageRendererFormat();fmt.scale=3;fmt.opaque=true
    let image=UIGraphicsImageRenderer(size:CGSize(width:l.w,height:l.h),format:fmt).image { r in
      let c=r.cgContext;c.setFillColor(UIColor.black.cgColor);c.fill(CGRect(x:0,y:0,width:l.w,height:l.h))
      round(c,CGRect(x:14*sx,y:l.y(72),width:374*sx,height:120),26,0xB8B9C0)
      let sheet=UIBezierPath(roundedRect:CGRect(x:0,y:l.y(88),width:l.w,height:l.h-l.y(88)),byRoundingCorners:[.topLeft,.topRight],cornerRadii:CGSize(width:26,height:26))
      c.setFillColor(UIColor.white.cgColor);c.addPath(sheet.cgPath);c.fillPath()
      c.saveGState();c.translateBy(x:35*sx-4,y:l.y(124)-8)
      stroke(c,path([CGPoint(x:8,y:2),CGPoint(x:2,y:8),CGPoint(x:8,y:14)]),2.2,0xEA4D70);c.restoreGState()
      text(c,"Back","SemiBold",16,22,35*sx+16,l.y(124)-11,0xEA4D70)
      text(c,m.name,"Bold",32,42,25*sx,l.y(338)-20,0x0B0C14)
      text(c,"\(m.kind)  •  \(m.dose)","Regular",14.3,20,25*sx,l.y(338)+26,0x5B6280)
      c.saveGState();c.setShadow(offset:CGSize(width:0,height:6),blur:18,color:ui(0x1E2246,0.10).cgColor)
      c.setFillColor(UIColor.white.cgColor);c.fillEllipse(in:CGRect(x:353*sx-24,y:l.y(349)-24,width:48,height:48));c.restoreGState()
      c.saveGState();c.translateBy(x:353*sx-11,y:l.y(349)-11)
      let edit=CGMutablePath();edit.move(to:CGPoint(x:4,y:15.5));edit.addLine(to:CGPoint(x:4.8,y:12));edit.addLine(to:CGPoint(x:13.8,y:3));edit.addCurve(to:CGPoint(x:16.6,y:5.8),control1:CGPoint(x:15.35,y:1.45),control2:CGPoint(x:18.15,y:4.25));edit.addLine(to:CGPoint(x:7.6,y:14.8));edit.closeSubpath();edit.move(to:CGPoint(x:12.3,y:4.6));edit.addLine(to:CGPoint(x:15.2,y:7.5));edit.move(to:CGPoint(x:3.5,y:19));edit.addLine(to:CGPoint(x:18,y:19));stroke(c,edit,1.7,0xEA4D70);c.restoreGState()
      for i in 0..<2 {
        let x=(i==0 ? 25.0:210.0)*sx,y=l.chipTop,ch=l.chipH
        round(c,CGRect(x:x,y:y,width:168*sx,height:ch),14,0xEEF0F4)
        c.saveGState();c.translateBy(x:x+16,y:y+ch/2-11)
        if i==1 {
          c.setFillColor(ui(0x1D74DB).cgColor);c.fillEllipse(in:CGRect(x:1,y:1,width:20,height:20))
          stroke(c,path([CGPoint(x:11,y:5.5),CGPoint(x:11,y:11),CGPoint(x:14,y:13)]),1.6,0xFFFFFF)
        } else {
          // Two separate SVG capsule halves, cut on the diagonal.
          let p=CGMutablePath();p.move(to:CGPoint(x:8.2,y:17.8));p.addCurve(to:CGPoint(x:1.7,y:17.8),control1:CGPoint(x:6.405,y:19.595),control2:CGPoint(x:3.495,y:19.595));p.addCurve(to:CGPoint(x:1.7,y:11.3),control1:CGPoint(x:-0.095,y:16.005),control2:CGPoint(x:-0.095,y:13.095));p.addLine(to:CGPoint(x:6,y:7));p.addLine(to:CGPoint(x:12.5,y:13.5));p.closeSubpath()
          c.setFillColor(ui(0xEE3A8F).cgColor);c.addPath(p);c.fillPath()
          let q=CGMutablePath();q.move(to:CGPoint(x:6,y:7));q.addLine(to:CGPoint(x:10.2,y:2.8));q.addCurve(to:CGPoint(x:16.7,y:2.8),control1:CGPoint(x:11.995,y:1.005),control2:CGPoint(x:14.905,y:1.005));q.addCurve(to:CGPoint(x:16.7,y:9.3),control1:CGPoint(x:18.495,y:4.595),control2:CGPoint(x:18.495,y:7.505));q.addLine(to:CGPoint(x:12.5,y:13.5));q.closeSubpath();c.setFillColor(ui(0xEE3A8F,0.55).cgColor);c.addPath(q);c.fillPath()
        }
        c.restoreGState()
        text(c,i==0 ? m.daily:m.each,"SemiBold",18,26,x+51,y+9+l.chipPad,0x0B0C14)
        text(c,i==0 ? "Daily Dosage":"Each Day","Regular",14.4,22,x+51,y+35+l.chipPad,0x5B6280)
      }
      text(c,"Schedule","SemiBold",20,30,25*sx,l.y(512)-15,0x0B0C14)
      let logs=label("All Logs","SemiBold",16,22),logsX=380*sx-(logs.width+8+22)
      text(c,"All Logs","SemiBold",16,22,logsX,l.y(512)-11,0xEA4D70)
      c.saveGState();c.translateBy(x:380*sx-22,y:l.y(512)-11)
      let p=UIBezierPath(roundedRect:CGRect(x:3.5,y:3.5,width:15,height:15),cornerRadius:2.5).cgPath
      stroke(c,p,1.6,0xEA4D70);stroke(c,path([CGPoint(x:7.5,y:11),CGPoint(x:10,y:13.5),CGPoint(x:14.5,y:8.5)]),1.6,0xEA4D70)
      stroke(c,path([CGPoint(x:7.5,y:2),CGPoint(x:7.5,y:5)]),1.6,0xEA4D70);stroke(c,path([CGPoint(x:14.5,y:2),CGPoint(x:14.5,y:5)]),1.6,0xEA4D70);c.restoreGState()
      round(c,CGRect(x:25*sx,y:l.y(540),width:353*sx,height:132*k),16,0xEEF0F4)
      for r in 0..<3 {
        text(c,m.times[r],"SemiBold",16,26,41*sx,l.rowsY[r]-13,0xEA4D70)
        c.setStrokeColor(ui(0x474F74).cgColor);c.setLineWidth(1.6);c.strokeEllipse(in:CGRect(x:l.checkX-9.2,y:l.rowsY[r]-9.2,width:18.4,height:18.4))
      }
      round(c,CGRect(x:25*sx,y:l.y(689),width:353*sx,height:49*k),14,0xEEF0F4)
      text(c,"Export to PDF","SemiBold",16,22,(25+353/2.0)*sx,l.y(689)+(49*k-22)/2,0x0B0C14,true)
    }
    let result=Image(uiImage:image);pages[key]=result;nativePages[key]=image;return result
  }
  func controlRect(_ l:VitaminCapsuleDoseLayout,_ index:Int)->CGRect {
    switch index {
    case 0:return CGRect(x:35*l.sx-14,y:l.y(124)-20,width:40+label("Back","SemiBold",16,22).width,height:40)
    case 1:return CGRect(x:353*l.sx-24,y:l.y(349)-24,width:48,height:48)
    case 2:
      let w=label("All Logs","SemiBold",16,22).width+30
      return CGRect(x:380*l.sx-w,y:l.y(512)-16,width:w,height:32)
    default:return CGRect(x:25*l.sx,y:l.y(689),width:353*l.sx,height:49*l.k)
    }
  }
  func control(_ l:VitaminCapsuleDoseLayout,_ med:Int,_ index:Int)->Control {
    let key="\(l)|\(med)|\(index)"
    if let r=controls[key] {return r}
    _=page(l,med)
    let rect=controlRect(l,index),pad=index==1 ? 30.0:0.0
    var crop=rect.insetBy(dx:-pad,dy:-pad).intersection(CGRect(x:0,y:0,width:l.w,height:l.h))
    let pixels=CGRect(x:floor(crop.minX*3),y:floor(crop.minY*3),width:ceil(crop.maxX*3)-floor(crop.minX*3),height:ceil(crop.maxY*3)-floor(crop.minY*3))
    crop=CGRect(x:pixels.minX/3,y:pixels.minY/3,width:pixels.width/3,height:pixels.height/3)
    let image=nativePages["\(l)|\(med)"]!.cgImage!.cropping(to:pixels)!
    let value=Control(image:Image(uiImage:UIImage(cgImage:image,scale:3,orientation:.up)),rect:rect,crop:crop)
    controls[key]=value;return value
  }
  func logLabel()->Image {let r=label("Log Medication","SemiBold",16,22);return Image(uiImage:r.image)}
  var logLabelSize:CGSize {let r=label("Log Medication","SemiBold",16,22);return CGSize(width:r.width+16,height:r.height+16)}
  static let ring:Path=Path(ellipseIn:CGRect(x:-9.2,y:-9.2,width:18.4,height:18.4))
  static let tick:Path = {var p=Path();p.move(to:CGPoint(x:1.5,y:4.6));p.addLine(to:CGPoint(x:4.2,y:7.2));p.addLine(to:CGPoint(x:9.5,y:1.6));return p}()
}
struct VitaminCapsuleDoseArt {
  typealias C=VitaminCapsuleDoseTheme
  let l:VitaminCapsuleDoseLayout
  func controls(_ c:GraphicsContext,_ f:VitaminCapsuleDoseMotion.Frame,_ clock:VitaminCapsuleDoseClock,_ pinned:Bool) {
    for i in 0..<4 where !pinned && clock.pressLevels[i]>0 {
      let p=clock.pressLevels[i],control=VitaminCapsuleDoseResources.shared.control(l,f.med,i)
      c.fill(Path(control.crop),with:.color(.white))
      var pressed=c
      if clock.reduced {pressed.opacity *= 1-0.28*p}
      else {
        let scale=1-(i==3 ? 0.02:0.04)*p
        pressed.translateBy(x:control.rect.midX,y:control.rect.midY);pressed.scaleBy(x:scale,y:scale);pressed.translateBy(x:-control.rect.midX,y:-control.rect.midY)
      }
      pressed.draw(control.image,in:control.crop)
    }
    for r in 0..<3 {
      let p=pinned ? 0:clock.pressLevels[4+r]
      var check=c
      if p>0 {
        check.fill(Path(CGRect(x:l.checkX-12,y:l.rowsY[r]-12,width:24,height:24)),with:.color(C.fill))
        check.translateBy(x:l.checkX,y:l.rowsY[r]);check.scaleBy(x:1-0.04*p,y:1-0.04*p)
        check.stroke(VitaminCapsuleDoseResources.ring,with:.color(C.ring),lineWidth:1.6)
        check.translateBy(x:-l.checkX,y:-l.rowsY[r])
      }
      let level=pinned ? (r==0 || (r==1 && f.taken>0.5) ? 1.0:0.0):clock.checkLevel(f.med*3+r)
      if level>0 {
        var c=check;c.opacity *= level
        c.translateBy(x:l.checkX,y:l.rowsY[r]);c.scaleBy(x:0.4+0.6*level,y:0.4+0.6*level);c.translateBy(x:-l.checkX,y:-l.rowsY[r])
        c.fill(Path(ellipseIn:CGRect(x:l.checkX-10.5,y:l.rowsY[r]-10.5,width:21,height:21)),with:.color(C.green))
        var tick=c;tick.translateBy(x:l.checkX-5.5,y:l.rowsY[r]-4.5)
        tick.stroke(VitaminCapsuleDoseResources.tick,with:.color(.white),style:StrokeStyle(lineWidth:1.8,lineCap:.round,lineJoin:.round))
      }
    }
    let sx=l.sx,rect=CGRect(x:25*sx,y:l.y(777),width:353*sx,height:56*l.k),scale=1-0.02*min(1,f.hold*8)
    var log=c;log.translateBy(x:rect.midX,y:rect.midY);log.scaleBy(x:scale,y:scale);log.translateBy(x:-rect.midX,y:-rect.midY)
    let capsule=Path(roundedRect:rect,cornerRadius:rect.height/2)
    log.clip(to:capsule);log.fill(capsule,with:.color(C.pink))
    if f.hold>0 {log.fill(Path(CGRect(x:rect.minX,y:rect.minY,width:rect.width*f.hold,height:rect.height)),with:.color(C.pinkDeep))}
    let resources=VitaminCapsuleDoseResources.shared,size=resources.logLabelSize
    log.draw(resources.logLabel(),in:CGRect(x:rect.midX-size.width/2,y:rect.midY-11-8,width:size.width,height:size.height))
  }
  // Entering/exiting RN Text groups are cached glyph bitmaps; only opacity and position move.
  func transitions(_ c:GraphicsContext,_ f:VitaminCapsuleDoseMotion.Frame,_ clock:VitaminCapsuleDoseClock) {
    let age=clock.time-clock.medChangedAt
    guard !clock.reduced,age>=0,age<0.36,clock.previousMed != f.med else{return}
    let resources=VitaminCapsuleDoseResources.shared
    let hasOld=clock.previousMed>=0
    let old=C.meds[max(0,clock.previousMed)],new=C.meds[f.med]
    func text(_ value:String,_ weight:String,_ size:Double,_ line:Double,_ x:Double,_ y:Double,_ color:Color,_ opacity:Double) {
      guard opacity>0 else{return}
      let label=resources.label(value,weight,size,line)
      var ctx=c;ctx.opacity *= opacity
      var image=ctx.resolve(Image(uiImage:label.image));image.shading = .color(color)
      ctx.draw(image,in:CGRect(x:x-8,y:y-8,width:label.width+16,height:label.height+16))
    }
    func progress(_ start:Double,_ duration:Double)->Double {
      let p=VitaminCapsuleDoseMotion.seg(age,start,start+duration)
      return p<0.5 ? 2*p*p:1-pow(-2*p+2,2)/2
    }
    let entering=progress(0,0.32),exiting=progress(0,0.18)
    let titleWidth=max(resources.label(old.name,"Bold",32,42).width,resources.label(new.name,"Bold",32,42).width)
    let subtitleWidth=max(resources.label("\(old.kind)  •  \(old.dose)","Regular",14.3,20).width,resources.label("\(new.kind)  •  \(new.dose)","Regular",14.3,20).width)
    c.fill(Path(CGRect(x:25*l.sx-1,y:l.y(338)-22,width:max(titleWidth,subtitleWidth)+2,height:70)),with:.color(.white))
    for (med,alpha,dy) in [(old,hasOld ? 1-exiting:0,-25*exiting),(new,entering,25*(1-entering))] {
      text(med.name,"Bold",32,42,25*l.sx,l.y(338)-20+dy,C.ink,alpha)
      text("\(med.kind)  •  \(med.dose)","Regular",14.3,20,25*l.sx,l.y(338)+26+dy,C.sub,alpha)
    }
    for i in 0..<2 {
      let x=(i==0 ? 25.0:210.0)*l.sx+51,y=l.chipTop
      c.fill(Path(CGRect(x:x-1,y:y+8+l.chipPad,width:115,height:52)),with:.color(C.fill))
      let enter=progress(Double(i)*0.04,0.3),exit=progress(0,0.16)
      for (med,alpha,dy) in [(old,hasOld ? 1-exit:0,-25*exit),(new,enter,25*(1-enter))] {
        text(i==0 ? med.daily:med.each,"SemiBold",18,26,x,y+9+l.chipPad+dy,C.ink,alpha)
        text(i==0 ? "Daily Dosage":"Each Day","Regular",14.4,22,x,y+35+l.chipPad+dy,C.sub,alpha)
      }
    }
    for r in 0..<3 {
      let x=41*l.sx,y=l.rowsY[r]-13
      c.fill(Path(CGRect(x:x-1,y:y-1,width:140,height:28)),with:.color(C.fill))
      let enter=progress(Double(r)*0.03,0.28),exit=progress(0,0.15)
      text(old.times[r],"SemiBold",16,26,x,y-25*exit,C.pinkText,hasOld ? 1-exit:0)
      text(new.times[r],"SemiBold",16,26,x,y+25*(1-enter),C.pinkText,enter)
    }
  }
  func finger(_ c:GraphicsContext,_ f:VitaminCapsuleDoseMotion.Frame) {
    guard f.finger>0 else{return}
    let p=VitaminCapsuleDoseMotion.finger(f,l),r=22*(1-0.12*f.press)
    let path=Path(ellipseIn:CGRect(x:p.x-r,y:p.y-r,width:2*r,height:2*r))
    c.fill(path,with:.color(C.color(0x14183C,0.08*f.finger)))
    c.stroke(path,with:.color(C.color(0x14183C,0.35*f.finger)),lineWidth:1.5)
  }
}
