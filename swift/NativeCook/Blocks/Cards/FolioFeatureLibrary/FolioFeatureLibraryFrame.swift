import SwiftUI

/// Every animated number of one frame: the animated styles of FolioFeatureLibrary.tsx (lines 439-479) and the loading
/// lens' styles (lines 103-107), evaluated at the engine's clock.
struct FolioFeatureLibraryFrame {
    let time: Double
    let width: Double
    let height: Double
    let insetTop: Double
    let insetBottom: Double
    let reduced: Bool
    let reduceTransparency: Bool
    let type: FolioFeatureLibraryType

    // The app surface grows from the card (`appStyle`, `appContentStyle`, `cardBridgeStyle`).
    let appOpacity: Double
    let appTX: Double
    let appTY: Double
    let appSX: Double
    let appSY: Double
    let appContentOpacity: Double
    let bridgeOpacity: Double
    let bridgeTX: Double
    let bridgeTY: Double
    let bridgeScale: Double
    let detailOpacity: Double
    let detailTX: Double
    let libraryTX: Double

    // The library's header card contracts into a tile (`headerCard`, `headerOrb`, `headerContent`, `listShift`).
    let collapse: Double
    let headerWidth: Double
    let headerLeft: Double
    let headerHeight: Double
    let headerTop: Double
    let headerRadius: Double
    let orbLeft: Double
    let orbTop: Double
    let headerContentOpacity: Double
    let headerContentTY: Double
    let listShift: Double

    // The loading placeholder's sweep (`shimmerStyle`).
    let shimmerX: Double
    let shimmerOpacity: Double

    // The loading lens (`OpeningCollection`).
    let shellOpacity: Double
    let greenOpacity: Double
    let iconOpacity: Double
    let loaderTitleOpacity: Double

    // The sheet and the notice (`Modal`).
    let sheetProgress: Double
    let noticeProgress: Double

    init(engine e: FolioFeatureLibraryEngine) {
        let t = e.clock
        let w = e.width
        let h = e.height
        let reduced = e.reduced
        time = t
        width = w
        height = h
        insetTop = e.insetTop
        insetBottom = e.insetBottom
        self.reduced = reduced
        reduceTransparency = e.reduceTransparency
        type = e.textType

        let p = e.appProgress.value(at: t)
        let dp = e.detailProgress.value(at: t)
        let o = e.origin
        let widthRatio = o.width / w
        let heightRatio = o.height / h
        appOpacity = reduced ? p : FolioFeatureLibraryMath.interpolate(p, [0, 0.025, 1], [0, 1, 1])
        appTX = reduced ? 0 : (o.x + o.width / 2 - w / 2) * (1 - p)
        appTY = reduced ? 0 : (o.y + o.height / 2 - h / 2) * (1 - p)
        appSX = reduced ? 1 : widthRatio + (1 - widthRatio) * p
        appSY = reduced ? 1 : heightRatio + (1 - heightRatio) * p
        appContentOpacity = reduced ? 1 : FolioFeatureLibraryMath.interpolate(p, [0.35, 0.75], [0, 1])
        bridgeOpacity = reduced ? 0 : FolioFeatureLibraryMath.interpolate(p, [0, 0.025, 0.18, 0.6, 1], [0, 1, 1, 0, 0])
        bridgeTX = (w / 2 - o.x - o.width / 2) * p
        bridgeTY = (h / 2 - o.y - o.height / 2) * p
        bridgeScale = 1 + p * 0.35
        detailOpacity = reduced ? dp : 1
        detailTX = reduced ? 0 : (1 - dp) * w
        libraryTX = reduced ? 0 : -dp * w * 0.22

        let headerValue = e.headerCollapse.value(at: t)
        let scrolled = min(1, max(0, e.scrollY / 90))
        let collapseValue = max(headerValue, scrolled)
        let k = reduced ? (collapseValue > 0.5 ? 1.0 : 0.0) : FolioFeatureLibraryCurve.outBezier.value(collapseValue)
        let tile = 76.0
        let tileTop = e.insetTop - 6
        let fullWidth = w - 20
        let cardWidth = fullWidth + (tile - fullWidth) * k
        collapse = collapseValue
        headerWidth = cardWidth
        headerLeft = (w - cardWidth) / 2
        headerHeight = e.introHeight + (tile - e.introHeight) * k
        headerTop = tileTop * k
        headerRadius = 30 - 8 * k
        orbLeft = (cardWidth - 60) / 2
        orbTop = e.insetTop + 2 - tileTop * k
        headerContentOpacity = 1 - min(1, collapseValue * 2.6)
        headerContentTY = -14 * collapseValue
        let shift = reduced ? (headerValue > 0.5 ? 1.0 : 0.0) : FolioFeatureLibraryCurve.outBezier.value(headerValue)
        listShift = -(e.introHeight - (tileTop + tile)) * shift

        shimmerX = FolioFeatureLibraryMath.lerp(-100, 160, e.shimmerValue(at: t))
        shimmerOpacity = reduced ? 0 : 0.5

        // Source f669 changes the empty neutral lens to green; the book then resolves.
        let seconds = e.loaderProgress(at: t) * 2.75
        let bookAlpha = reduced ? 1.0 : FolioFeatureLibraryMath.interpolate(seconds, [1.4667, 1.5, 1.6, 1.75, 2], [0, 0.1, 0.45, 0.85, 1])
        let late = reduced || seconds >= 1.4
        shellOpacity = late ? 0 : 1
        greenOpacity = (reduced || seconds < 1.4) ? 0 : 1 - bookAlpha
        iconOpacity = bookAlpha
        loaderTitleOpacity = late ? 1 : 0

        sheetProgress = e.sheet.value(at: t)
        noticeProgress = e.noticeFade.value(at: t)
    }
}
