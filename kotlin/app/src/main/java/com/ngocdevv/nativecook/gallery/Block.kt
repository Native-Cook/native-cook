package com.ngocdevv.nativecook.gallery

import androidx.compose.runtime.Composable

/** One row of the gallery. The list is in `BlockRegistry.kt`, written from `catalog/`. */
class Block(
    val slug: String,
    val name: String,
    val category: String,
    val spec: String,
    val demo: @Composable () -> Unit,
)
