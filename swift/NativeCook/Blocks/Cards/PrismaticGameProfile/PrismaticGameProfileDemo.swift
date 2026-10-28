import SwiftUI

struct PrismaticGameProfileDemo:View {
    private var previewTime:Double? {
        guard let value=UserDefaults.standard.object(forKey:"prismaticTime") else{return nil}
        if let number=value as? NSNumber{return number.doubleValue}
        return Double(String(describing:value))
    }
    var body:some View {
        PrismaticGameProfile(autoPlay:!UserDefaults.standard.bool(forKey:"prismaticManual"),previewTime:previewTime,previewReducedMotion:UserDefaults.standard.bool(forKey:"prismaticReduced"))
    }
}

#Preview {PrismaticGameProfileDemo()}
