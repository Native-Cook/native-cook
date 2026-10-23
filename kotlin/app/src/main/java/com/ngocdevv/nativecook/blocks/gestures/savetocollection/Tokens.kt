package com.ngocdevv.nativecook.blocks.gestures.savetocollection

internal object SaveToCollectionTokens {
    const val Page = 0xFF0A0A0B.toInt()
    const val Ink = 0xFFF4F4F2.toInt()
    const val Muted = 0x8FF4F4F2.toInt()
    const val Tile = 0xFF18181A.toInt()
    const val Hairline = 0x1FFFFFFF
    const val Rim = 0x38FFFFFF
    const val GlassFill = 0x6B1E1E20
    const val SheetFill = 0xB8161618.toInt()
    const val Scrim = 0x80000000.toInt()
    const val Finger = 0x29FFFFFF
    const val FingerEdge = 0x66FFFFFF

    const val Edge = 16f
    const val Gap = 8f
    const val Radius = 14f
    const val TitleHeight = 44f
    const val ChipsTop = 52f
    const val ChipHeight = 32f
    const val GridTop = 100f
    const val BarHeight = 60f
    const val BarGap = 8f
    const val SaveSize = 60f
    const val SheetTop = 72f
    const val SheetRadius = 32f
    const val FingerSize = 44f
    const val SheetCaption = 48f

    val ColumnHeights = listOf(
        listOf(236f, 196f, 252f),
        listOf(196f, 268f, 224f),
    )

    val Photos = listOf(
        SaveToCollectionPhoto("boardwalk", "Boardwalk to the dunes", "Sylt"),
        SaveToCollectionPhoto("village", "Last light on the cliff", "Oia"),
        SaveToCollectionPhoto("road", "Road into the valley", "Utah"),
        SaveToCollectionPhoto("pool", "Pool under the palms", "Bali"),
        SaveToCollectionPhoto("lake", "Still water, early", "Alberta"),
        SaveToCollectionPhoto("shore", "Shoreline from above", "Maldives"),
    )
    val CollectionBefore = listOf(5, 1, 3)
    const val CollectionName = "Slow coast"

    const val HoldSeconds = 0.300
    const val LiftScale = 0.04f
    const val LiftRise = 4f
    const val MagnetFar = 150f
    const val MagnetNear = 36f
    const val MagnetPull = 0.38f
    const val MagnetShrink = 0.55f
    const val DropRadius = 64f
    const val LeanPerVelocity = 0.012f
    const val LeanMaximum = 10f
    const val CountBefore = 12

    const val LiftDuration = 0.380
    const val LiftDamping = 1f
    const val HomeDuration = 0.420
    const val HomeDamping = 0.82f
    const val GulpDuration = 0.360
    const val GulpDamping = 0.55f
    const val SheetDuration = 0.480
    const val SheetDamping = 0.88f
    const val ReducedDuration = 0.200
    const val DropDuration = 0.260
    const val RollDuration = 0.220
    const val ToastInDuration = 0.220
    const val ToastOutDuration = 0.160
    const val SheetOutDuration = 0.300
    const val ArriveDelay = 0.380
    const val ArriveDuration = 0.420
    const val MarkDuration = 0.200
    const val FingerDuration = 0.150
    const val PressOutDuration = 0.150

    const val LoopSeconds = 10.0
    const val ResumeSeconds = 2.0
    const val TidySeconds = 0.6
    const val DemoTile = 4
    const val DemoFingerIn = 0.9
    const val DemoDown = 1.0
    const val DemoLift = 1.35
    const val DemoCarryStart = 1.45
    const val DemoCarryEnd = 2.75
    const val DemoRelease = 2.85
    const val DemoTapIn = 3.85
    const val DemoTapDown = 3.95
    const val DemoTap = 4.05
    const val DemoClose = 7.3
    const val DemoReset = 8.6

    const val AssetRoot = "savetocollection"
}
