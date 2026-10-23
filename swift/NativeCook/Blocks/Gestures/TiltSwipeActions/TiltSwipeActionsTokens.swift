import CoreGraphics

enum TiltSwipeActionsTokens {
    static let LOOP_MS = 10_000
    // The native block contract resumes two seconds after the last real touch.
    static let RESUME_MS = 2_000
    static let QA_HOLD_AT: Double? = nil
    static let QA_STATE: String? = nil

    enum Color {
        static let gradient = ["#A8448F", "#C65F9E", "#D977A5", "#DD81A8", "#DF86A7", "#E290A7", "#E89CA7"]
        static let desk = ["#3F0F46", "#4E1C54"]
        static let gradientStops: [Double] = [0, 0.596, 0.694, 0.75, 0.816, 0.861, 1]
        static let plum = "#3B1D5E"; static let plumInk = "#2A1638"; static let cardInk = "#2D2B35"
        static let white = "#FFFFFF"; static let pink = "#F26293"; static let pinkDeep = "#E8447A"; static let title = "#FFFFFF"
        static let subtitle = "rgba(255,214,236,0.85)"; static let cardLabel = "#E0679E"; static let cardCurrency = "#B9B2BE"
        static let slab = "rgba(255,255,255,0.18)"; static let slabEdge = "rgba(255,255,255,0.36)"; static let slabShadow = "#5A1F55"
        static let panel = "#F9C0C4"; static let panelEdge = "#E494A7"; static let deskEdge = "#49184F"; static let panelIcon = "#D08AA2"; static let panelIconActive = "#FFFFFF"
        static let menuInk = "#141414"; static let menuPlus = "#8E6A9A"; static let menuScrim = "rgba(250,215,230,0.06)"
        static let income = "#2FBF71"; static let transfer = "#8B6CF6"; static let scrim = "rgba(59,29,94,0.18)"; static let finger = "rgba(255,255,255,0.5)"
        static let lens = "rgba(255,255,255,0.32)"; static let lensRim = "rgba(255,255,255,0.7)"; static let glassEdge = "rgba(255,255,255,0.9)"
        static let pinkGlow = "rgba(242,98,147,0.35)"; static let sheen = ["rgba(255,255,255,0)", "rgba(255,255,255,0.45)", "rgba(255,255,255,0)"]; static let fingerEdge = "rgba(255,255,255,0.8)"
    }
    enum IconTint { static let yellow = "#F2C14E"; static let lilac = "#B9A4FF"; static let peach = "#FFB38A"; static let mint = "#8FE3C0"; static let sky = "#8CC8FF"; static let rose = "#FF9DBB" }
    enum TiltView { static let p0 = CGPoint(x: 275, y: 543); static let u = CGPoint(x: 1.112, y: -0.699); static let v = CGPoint(x: 1.085, y: 0.717); static let cropX = 262.0; static let scale = 1.4567; static let screenRadius = 44.0 }
    enum Flat { static let card = (left: 27.0, top: 345.0, right: 27.0, height: 150.0, radius: 18.0); static let section = (left: 58.0, centreY: 598.0); static let rowsTop = 628.7; static let rowLeft = 61.0; static let rowRight = 26.8; static let panel = (left: 14.0, top: 808.0, radiusTop: 30.0, radiusBottom: 23.0); static let tabs = [85.0, 167.0, 247.0]; static let tabY = 850.2; static let fab = (left: 318.0, top: 787.0, size: 80.0, radius: 22.0); static let menu = (left: 126.0, top: 583.3, width: 270.0, height: 220.7, radius: 33.0, padTop: 13.1, padLeft: 41.0, padRight: 27.0, item: 65.0, icon: 24.0); static let cards = (left: 60.0, pitch: 250.0, width: 210.0, top: 463.5, height: 219.3, radius: 18.0, badge: 100.0, badgeLeft: 28.0, badgeTop: 440.0) }
    enum Space { static let xs = 4.0; static let s = 8.0; static let m = 12.0; static let l = 16.0; static let xl = 24.0; static let xxl = 32.0 }
    enum Layout { static let gutter = 20.0; static let cardTop = 12.0; static let cardHeight = 112.0; static let cardRadius = 28.0; static let cardBadge = 56.0; static let cardBadgeRadius = 18.0; static let sectionGap = 32.0; static let sectionTitle = 30.0; static let sectionBottom = 8.0; static let row = 82.0; static let circle = 58.0; static let textLeft = 135.95; static let rowRadius = 22.0; static let tile = 60.0; static let tileHeight = 56.0; static let tileRadius = 18.0; static let tileGap = 8.0; static let panel = 84.0; static let panelRadius = 32.0; static let fab = 64.0; static let fabRadius = 22.0; static let fabRight = 24.0; static let pillHeight = 52.0; static let pillGap = 16.0; static let menuItem = 48.0; static let lens = 52.0; static let menuWidth = 248.0; static let menuRadius = 28.0; static let finger = 44.0 }
    static let OPEN_X = -(Layout.tile * 2 + Layout.tileGap + Layout.gutter + Layout.tileGap)
    enum Font {
        static let medium = "TiltSwipe-Montserrat-Medium"
        static let semibold = "TiltSwipe-Montserrat-SemiBold"
        static let bold = "TiltSwipe-Montserrat-Bold"
        static let postScriptMedium = "Montserrat-Medium"
        static let postScriptSemibold = "Montserrat-SemiBold"
        static let postScriptBold = "Montserrat-Bold"
    }
    enum Typography { static let cardLabel = (fontSize: 14.0, lineHeight: 18.0, fontFamily: Font.semibold); static let balance = (fontSize: 44.0, lineHeight: 52.0, fontFamily: Font.semibold, letterSpacing: -0.5); static let currency = (fontSize: 40.0, lineHeight: 52.0, fontFamily: Font.medium); static let section = (fontSize: 24.3, lineHeight: 30.0, fontFamily: Font.semibold, letterSpacing: 0.2); static let rowTitle = (fontSize: 18.5, lineHeight: 24.0, fontFamily: Font.semibold); static let rowSub = (fontSize: 16.6, lineHeight: 21.0, fontFamily: Font.medium); static let amount = (fontSize: 18.5, lineHeight: 24.0, fontFamily: Font.semibold); static let tile = (fontSize: 11.0, lineHeight: 13.0, fontFamily: Font.semibold, letterSpacing: 0.2); static let pill = (fontSize: 15.0, lineHeight: 20.0, fontFamily: Font.medium); static let pillAction = (fontSize: 15.0, lineHeight: 20.0, fontFamily: Font.bold); static let menu = (fontSize: 20.0, lineHeight: 24.0, fontFamily: Font.semibold) }
    static let EASE_OUT = (0.23, 1.0, 0.32, 1.0); static let EASE_IN_OUT = (0.77, 0.0, 0.175, 1.0)
    enum Swipe { static let activeOffsetX = 12.0; static let failOffsetY = 10.0; static let fullAt = 0.55; static let fullRubber = 0.35; static let rightRubber = 0.25; static let openAt = 78.0; static let openFlick = -500.0; static let deleteFlick = -1400.0; static let deleteFlickAt = 0.4 }
    enum Tilt { static let perVelocity = 0.018; static let max = 14.0; static let perspective = 700.0; static let lift = 1.02 }
    enum Spring { static let follow = (damping: 18.0, stiffness: 220.0, mass: 0.6); static let snap = (damping: 26.0, stiffness: 300.0, mass: 1.0); static let lift = (damping: 20.0, stiffness: 320.0, mass: 0.8); static let tileStretch = (damping: 22.0, stiffness: 420.0, mass: 0.7); static let collapse = (damping: 28.0, stiffness: 260.0, mass: 1.0); static let restore = (damping: 24.0, stiffness: 230.0, mass: 1.0); static let pill = (damping: 20.0, stiffness: 260.0, mass: 1.0); static let lens = (damping: 18.0, stiffness: 260.0, mass: 0.8); static let menu = (damping: 24.0, stiffness: 300.0, mass: 0.8) }
    enum Timing { static let flyOut = 260; static let tileFade = 120; static let colour = 160; static let undoVisible = 4000; static let reduced = 180; static let reducedGap = 200; static let pressScale = 0.94; static let pressIn = 100; static let pressOut = 160; static let restoreDelay = 120; static let menuStagger = 0.12; static let tab = 260; static let roll = 700; static let breath = 3.33; static let sheen = 0.6; static let tabSlide = 24.0 }
    enum Demo { static let fingerFade = 0.15; static let open = (row: 1, down: 0.6, moveEnd: 1.5, up: 1.7, x: 300.0, to: -170.0); static let remove = (row: 0, down: 2.5, moveEnd: 2.95, up: 3.25, x: 320.0, to: -290.0); static let undo = (down: 5.5, up: 5.62, fabGap: 12.0, xInset: 50.0); static let lean = (row: 2, down: 7.2, turn: 7.55, moveEnd: 7.9, up: 7.95, x: 300.0, to: -96.0); static let tidy = 0.8 }
    static let flatWidth = 402.0; static let flatHeight = 874.0; static let planeWidth = 406.0; static let planeHeight = 888.0
    static let menuFade = 0.68; static let menuLift = 6.0; static let balance = 1148.9
    static let ring = 24.0; static let pillBottomOffset = 12.0
    enum BalanceCard { static let horizontalPadding = 24.0; static let gap = 16.0; static let ring = 24.0; static let ringWidth = 4.0; static let shadowOpacity = 0.18; static let shadowRadius = 24.0; static let shadowY = 12.0 }
    enum Cards { static let labelSize = 15.0; static let balanceSize = 36.0; static let balanceLineHeight = 44.0; static let currencySize = 34.0; static let amountInset = 2.4; static let leftInset = 30.0; static let bottomInset = 31.0; static let shadowOpacity = 0.2; static let shadowRadius = 20.0; static let shadowY = 12.0; static let badgeRadius = 22.0; static let ring = 48.0; static let ringWidth = 5.0 }
    enum Row { static let limit = 0.85; static let flyPast = 1.15; static let releaseRightVelocity = 300.0; static let slabInsetX = 8.0; static let slabInsetY = 4.0; static let slabShadowOpacity = 0.25; static let slabShadowRadius = 16.0; static let slabShadowY = 8.0; static let sheenWidth = 90.0; static let sheenStartX = 120.0; static let sheenSkew = -20.0; static let sheenBaseOpacity = 0.5; static let circleY = 7.0; static let contentIcon = 24.0; static let actionIcon = 20.0; static let flagDot = 8.0; static let flagGap = 8.0; static let amountY = -2.5; static let tileContentGap = 4.0; static let tileStartScale = 0.6; static let tileFullFade = 0.3 }
    enum Panel { static let fadeHeight = 10.0; static let fadeSpan = 78.0; static let rowsBottomGap = 8.0; static let listFade = 0.68; static let listLift = 6.0; static let tabHit = 44.0; static let lensHighlightInset = 10.0; static let lensHighlightY = 1.0; static let lensHighlightHeight = 1.5; static let lensShadowOpacity = 0.18; static let lensShadowRadius = 10.0; static let lensShadowY = 4.0; static let glowOffsetX = 14.0; static let glowOffsetY = 6.0; static let glowExtra = 28.0; static let glowRadiusExtra = 14.0; static let glowOpacityBase = 0.35; static let glowOpacityGain = 0.35; static let glowScaleBase = 0.9; static let glowScaleGain = 0.15; static let glowShadowRadius = 26.0; static let glowShadowY = 10.0; static let fabShadowOpacity = 0.22; static let fabShadowRadius = 18.0; static let fabShadowY = 8.0; static let menuScaleClosed = 0.8; static let menuScaleRange = 0.2; static let menuShadowOpacity = 0.22; static let menuShadowRadius = 24.0; static let menuShadowY = 12.0; static let menuGlyphAlpha = 0.10; static let menuGlyphSize = 14.0; static let menuRise = 8.0; static let fabGlyph = 38.0; static let tabPressScale = 0.88; static let menuPressScale = 0.97 }
    enum Undo { static let trailingFabGap = 12.0; static let leftPadding = 24.0; static let rightPadding = 8.0; static let actionHeight = 44.0; static let actionPadding = 12.0; static let actionGap = 8.0; static let shadowOpacity = 0.3; static let shadowRadius = 18.0; static let shadowY = 8.0; static let startScaleX = 0.22; static let startScaleY = 1.08; static let opacityGain = 3.0; static let icon = 16.0 }
    enum Finger { static let pressScale = 0.15; static let border = 1.0 }
    enum Touch { static let retention = 20.0; static let tapMaximum = 0.5; static let velocityWindow = 0.1; static let sampleWindow = 0.2; static let minimumVelocitySpan = 0.01 }
    static let transactions: [(key: String, title: String, when: String, amount: String, icon: String, tint: String)] = [
        ("groceries", "Groceries", "Today", "-34$", "shopping-cart", IconTint.yellow), ("rent", "Rent", "Today", "-114$", "home", IconTint.lilac),
        ("coffee", "Corner Café", "Yesterday", "-4.80$", "coffee", IconTint.peach), ("metro", "Metro card", "Yesterday", "-25$", "navigation", IconTint.sky),
        ("cinema", "Cinema", "Sat", "-18$", "film", IconTint.rose), ("gym", "Climbing gym", "Fri", "-45$", "activity", IconTint.mint),
        ("books", "Bookshop", "Thu", "-22$", "book-open", IconTint.yellow), ("pharmacy", "Pharmacy", "Wed", "-12.60$", "plus-square", IconTint.mint)
    ]
    static let rngSeed: Int? = nil
}
