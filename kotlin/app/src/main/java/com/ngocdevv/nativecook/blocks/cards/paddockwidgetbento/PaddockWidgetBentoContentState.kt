package com.ngocdevv.nativecook.blocks.cards.paddockwidgetbento

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

internal class PaddockWidgetBentoContentState {
    var selectedTab by mutableStateOf(PaddockWidgetBentoDestination.Home)
    var destination by mutableStateOf<PaddockWidgetBentoDestination?>(null)
    var guideVisible by mutableStateOf(true)
    val pinnedWidgets = mutableStateListOf<String>()
    var selectedWidget by mutableStateOf("Lap count")
    var lapCount by mutableStateOf(3)
    var savedWeekends by mutableStateOf(setOf<String>())
    var selectedCircuit by mutableStateOf("Harbor")
    var latestStats by mutableStateOf(true)
    var expandedLesson by mutableStateOf("Find the apex")
    var searchQuery by mutableStateOf("")
    var reactionText by mutableStateOf("Ready")
}
