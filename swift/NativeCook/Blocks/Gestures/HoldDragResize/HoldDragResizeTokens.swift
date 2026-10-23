import Foundation

enum HoldDragResizeTokens {
    static let loop: Double = 12.0
    static let loopMilliseconds = 12_000
    static let resumeMilliseconds = 2_000
    static let maximumFrameDelta = 0.1
    static let referenceWidth = 402.0
    static let aspect = 402.0 / 874.0
    static let colors: [String: String] = [
        "background": "#000000", "chrome": "#1C1C1E", "chromeHi": "#2C2C2E",
        "ink": "#FFFFFF", "mute": "#8E8E93", "divider": "#2A2A2C", "toolDim": "#5C5C60",
        "faint": "#5A5A5E", "blue": "#1E8FFF", "strip": "#151517", "playhead": "#FFFFFF",
        "scrim": "rgba(0,0,0,0.72)", "touch": "rgba(255,255,255,0.35)",
        "outline": "rgba(255,255,255,0.9)", "caption": "#FFFFFF", "captionShadow": "rgba(0,0,0,0.6)",
        "finger": "rgba(255,255,255,0.35)", "fingerEdge": "rgba(255,255,255,0.7)",
        "sticker": "#3FA34D", "stickerRing": "#B8E986", "stickerInk": "#FFFFFF",
        "overlayButton": "rgba(28,28,30,0.72)", "badge": "rgba(20,20,22,0.55)",
        "badgeDot": "rgba(255,255,255,0.35)", "rim": "#FFFFFF"
    ]
    static let sizes = [401.0, 476.0, 874.0]
    static let frames = [(width: 227.0, top: 61.0), (width: 268.0, top: 61.0), (width: 402.0, top: 0.0)]
    static let layout: [String: Double] = ["gutter":20,"barY":14,"barButton":32,"previewTop":6,"previewRadius":14,"controlsGap":29,"control":32,"dividerGap":29,"rulerGap":46,"rulerPitch":85,"rulerLabelW":30,"ruler":12,"stripGap":24,"strip":60,"track":34,"trackFirstGap":4,"trackGap":8,"trackIndent":83,"hintGap":51,"hint":16,"thumb":340.0/12.0,"hintBottom":118,"tools":78,"tool":52,"toolH":41,"toolGap":6.8,"toolInset":11,"toolRadius":10,"finger":44,"fullIcon":40,"pillHeight":44]
    static let trimHandle = (x: 73.0, width: 10.0, height: 30.0)
    static let sticker = (x: 125.0, size: 26.0)
    static let coachHand = (x: 169.0, y: 267.0, width: 44.0, height: 70.0)
    static let coachTouch = (x: 10.0, y: -7.0, size: 24.0)
    static let coachTextTop = 489.0
    static let coachButton = (x: 160.0, y: 571.0, width: 79.0, height: 30.0)
    static let space = (xxs: 2.0, xs: 4.0, sm: 8.0, md: 12.0, lg: 16.0, xl: 20.0, xxl: 24.0)
    static let badge = (height: 26.0, bottom: 12.0, dot: 5.0, padX: 12.0, gap: 8.0, blur: 30.0)
    static let squeeze = (controls: -2.0, ruler: -3.0, below: -14.0, hint: -22.0, tools: -23.0)
    static let type = ["time": (13.0,16.0,500), "ruler":(9.0,12.0,600), "track":(13.0,16.0,500), "hint":(12.0,16.0,400), "tool":(10.5,13.0,600), "caption":(17.0,22.0,800), "coach":(22.0,27.0,700), "coachButton":(13.0,16.0,600), "pill":(15.0,20.0,600)]
    static let resize = (holdMilliseconds: 260, gain: 1.15, rubber: 0.3, rubberMax: 60.0, project: 0.18, lift: 0.97)
    static let springs = ["lift":(damping:18.0,stiffness:340.0,mass:0.7),"drop":(damping:22.0,stiffness:460.0,mass:0.6),"snap":(damping:24.0,stiffness:210.0,mass:1.0),"coach":(damping:20.0,stiffness:200.0,mass:1.0)]
    static let timing = (fadeChrome:160, coachFade:240, reduced:200, clip:3000, crossfade:400, kenBurns:0.06, stripSpeed:340.0/12.0)
    static let upgrade = (buttonPress:0.96, press:0.99, pressInMilliseconds:100, pressOutMilliseconds:160, magnet:18.0, magnetPull:0.45, follow:16.0, shadowOpacity:0.55, shadowRadius:28.0, shadowY:14.0, rim:0.22, badgeInMilliseconds:140, badgeOutMilliseconds:220, badgeRise:6.0, stagger:0.025)
    static let qaSlowmo = 1.0
    static let qaState = -1
    static let qaT = 0.0
    static let demo = (fingerFade:0.15, coachDown:0.4, coachBottom:1.1, coachUp:1.9, coachTravel:36.0, coachGrow:44.0, gotItShow:2.15, gotItTap:2.45, growDown:3.0, growLifted:3.26, growMid:3.9, growHold:4.5, growEnd:5.0, growUp:5.15, shrinkDown:7.1, shrinkLifted:7.36, shrinkEnd:8.5, shrinkUp:8.6, coachBack:11.6, tidy:0.6)
    static let clips = [(asset:"HoldDragResize-clip-shelves.jpg", caption:""),(asset:"HoldDragResize-clip-cocktail.jpg", caption:"and a lot of"),(asset:"HoldDragResize-clip-lamps.jpg", caption:"small tight spaces,"),(asset:"HoldDragResize-clip-lounge.jpg", caption:"casual drinking den.")]
    static let sizeNames = ["Compact", "Large", "Full screen"]
    static let previewOrder = [1, 2, 3, 0]
    static let rulerOffset = 139.0
    static let repeatCount = 4
    static let thumbsPerClip = 3
    static let handPath = "M14 40V9a5 5 0 0 1 10 0v22a4.5 4.5 0 0 1 9 0v3a4.5 4.5 0 0 1 9 0v4a4 4 0 0 1 0.5 0v14c0 10-7 17-17 17h-4c-8 0-12-4-16-10L3 47c-2-3 0-7 4-6 3 0 5 2 7 5"
    static let copy = ["coach": "Hold and drag to change\npreview size.", "gotIt": "Got it", "audio": "Tap to add audio", "text": "Tap to add text", "hint": "Tap on a track to trim. Pinch to zoom.", "time": "0:00  /  0:12"]
}
