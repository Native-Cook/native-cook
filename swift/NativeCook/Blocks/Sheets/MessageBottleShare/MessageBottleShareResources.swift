import CoreText
import UIKit

// Register the bundled files once; no font construction or artwork decoding in the clock.
enum MessageBottleShareResources {
    static let registered: Void = {
        for name in ["InstrumentSerif-Regular", "Inter-Regular", "Inter-Medium", "Inter-SemiBold"] {
            if let url = Bundle.main.url(forResource: "MessageBottleShare-\(name)", withExtension: "ttf") {
                CTFontManagerRegisterFontsForURL(url as CFURL, .process, nil)
            }
        }
    }()
}
