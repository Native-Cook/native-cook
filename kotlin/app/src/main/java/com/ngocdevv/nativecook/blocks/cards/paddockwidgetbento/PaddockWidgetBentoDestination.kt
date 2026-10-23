package com.ngocdevv.nativecook.blocks.cards.paddockwidgetbento

import androidx.compose.material.icons.automirrored.filled.DirectionsWalk
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.LibraryAdd
import androidx.compose.material.icons.filled.Public
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.Map

internal enum class PaddockWidgetBentoDestination {
    Laps,
    Reflex,
    HomeCards,
    Pitcast,
    Stats,
    Glossary,
    Education,
    WidgetStudio,
    Guide,
    CloseGuide,
    Banner,
    Home,
    Calendar,
    Results,
    Circuits,
    Customize,
}

internal fun PaddockWidgetBentoCardID.cardDestination(): PaddockWidgetBentoDestination {
    return when (this) {
        PaddockWidgetBentoCardID.Laps -> PaddockWidgetBentoDestination.Laps
        PaddockWidgetBentoCardID.Reflex -> PaddockWidgetBentoDestination.Reflex
        PaddockWidgetBentoCardID.HomeCards -> PaddockWidgetBentoDestination.HomeCards
    }
}

internal fun PaddockWidgetBentoCardID.cardName(): String {
    return when (this) {
        PaddockWidgetBentoCardID.Laps -> "Every lap counts"
        PaddockWidgetBentoCardID.Reflex -> "Race day reflex test"
        PaddockWidgetBentoCardID.HomeCards -> "Custom home cards"
    }
}

internal fun PaddockWidgetBentoDestination.dockIcon(): androidx.compose.ui.graphics.vector.ImageVector {
    return when (this) {
        PaddockWidgetBentoDestination.Calendar -> androidx.compose.material.icons.Icons.Default.CalendarMonth
        PaddockWidgetBentoDestination.Results -> androidx.compose.material.icons.Icons.Default.EmojiEvents
        PaddockWidgetBentoDestination.Circuits -> androidx.compose.material.icons.Icons.Default.Public
        PaddockWidgetBentoDestination.Customize -> androidx.compose.material.icons.Icons.Default.LibraryAdd
        else -> androidx.compose.material.icons.Icons.Default.Home
    }
}

internal fun paddockWidgetIcon(widget: String): androidx.compose.ui.graphics.vector.ImageVector {
    return when (widget) {
        "Lap count" -> androidx.compose.material.icons.Icons.AutoMirrored.Filled.DirectionsWalk
        "Race weekend" -> androidx.compose.material.icons.Icons.Default.CalendarMonth
        else -> androidx.compose.material.icons.Icons.Default.Map
    }
}
