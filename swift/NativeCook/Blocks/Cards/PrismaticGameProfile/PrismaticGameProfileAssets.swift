import UIKit

@MainActor
enum PrismaticGameProfileAssets {
    static let images: [String:UIImage] = {
        var result=[String:UIImage]()
        for name in ["avatar","landscape","crown","shield","diamond","game","ruby","coin","stats-level","stats-games","stats-time"] {
            if let url=Bundle.main.url(forResource:"PrismaticGameProfile-"+name,withExtension:"png"),let image=UIImage(contentsOfFile:url.path) {result[name]=image.preparingForDisplay() ?? image}
        }
        return result
    }()
    static func image(_ name:String)->UIImage? {images[name]}
    static func color(_ value:UInt32,_ alpha:Double=1)->UIColor {UIColor(red:CGFloat((value>>16)&255)/255,green:CGFloat((value>>8)&255)/255,blue:CGFloat(value&255)/255,alpha:alpha)}
}
