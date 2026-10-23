package com.ngocdevv.nativecook.blocks.animations.emberchargewidget

import android.graphics.Path
import androidx.core.graphics.PathParser

// Original SVG coordinates are path data on a 24 × 24 glyph grid and a 56 × 52 heart box.
// Keep these control points verbatim; they are not independent layout dimensions.
internal object EmberPaths {
    val walkFill = listOf<Path>(
        PathParser.createPathFromPathData(
            "M 11.55 3.75 C11.55 2.34 12.69 1.2 14.1 1.2 C15.51 1.2 16.65 2.34 16.65 3.75 " +
                    "C16.65 5.16 15.51 6.3 14.1 6.3 C12.69 6.3 11.55 5.16 11.55 3.75 Z",
        ),
        PathParser.createPathFromPathData(
            "M10.9 7.05 C12.45 6.82 13.7 7.32 14.95 8.43 C16.18 9.52 16.98 9.89 18.48 9.12 " +
                    "C19.22 8.73 19.95 9.02 20.28 9.72 C20.62 10.45 20.3 11.12 19.62 11.52 " +
                    "C17.66 12.65 16.13 12.25 14.42 11.35 L13.82 14.9 " +
                    "C15.67 15.98 17.05 17.23 17.66 19.16 L18.42 21.79 " +
                    "C18.69 22.72 18.18 23.38 17.32 23.5 C16.46 23.62 15.86 23.17 15.61 22.3 L14.93 20.05 " +
                    "C14.5 18.67 13.49 18 12.13 17.38 L10.54 19.93 C9.53 21.47 7.28 22.49 5.14 22.9 " +
                    "C4.17 23.09 3.46 22.61 3.35 21.79 C3.23 20.95 3.69 20.31 4.58 20.09 " +
                    "C6.24 19.69 7.6 19.05 8.12 18.12 L9.26 15.28 L9.79 10.52 " +
                    "C8.49 11.1 8.14 12.19 7.75 13.88 C7.57 14.7 7.02 15.08 6.31 14.95 " +
                    "C5.56 14.82 5.25 14.18 5.37 13.4 C5.89 9.99 7.64 7.65 10.9 7.05 Z",
        )
    )
    val walkStroke = listOf<Path>()
    val routeFill = listOf<Path>(
        PathParser.createPathFromPathData(
            "M16.95 4.15 C16.95 2.69 18.14 1.5 19.6 1.5 C21.06 1.5 22.25 2.69 22.25 4.15 " +
                    "C22.25 5.61 21.06 6.8 19.6 6.8 C18.14 6.8 16.95 5.61 16.95 4.15 Z",
        ),
        PathParser.createPathFromPathData(
            "M2.29 18.38 C2.05 18.11 2.23 17.7 2.6 17.7 L4.05 17.7 L4.05 16.75 L5.85 16.75 " +
                    "L5.85 17.7 L7.3 17.7 C7.67 17.7 7.85 18.11 7.61 18.38 L5.28 21.02 " +
                    "C5.1 21.22 4.8 21.22 4.62 21.02 Z",
        )
    )
    val routeStroke = listOf<Path>(
        PathParser.createPathFromPathData(
            "M4.95 18.1 V6.2 C4.95 3.9 6.46 2.2 8.55 2.2 C10.64 2.2 12.15 3.9 12.15 6.2 V17.5 " +
                    "C12.15 19.84 13.77 21.5 15.87 21.5 C17.97 21.5 19.6 19.84 19.6 17.5 V5.5",
        )
    )
    val clockFill = listOf<Path>()
    val clockStroke = listOf<Path>(
        PathParser.createPathFromPathData("M21.65 12 A9.65 9.65 0 1 1 2.35 12 A9.65 9.65 0 1 1 21.65 12 Z"),
        PathParser.createPathFromPathData("M12 5.35 V12.05 L15.35 15.35")
    )
    val boltFill = listOf<Path>(
        PathParser.createPathFromPathData(
            "M13.61 1.25 C13.72 1.08 13.94 1.16 13.92 1.36 L13.05 10.32 L19.3 9.81 " +
                    "C19.49 9.79 19.58 9.96 19.46 10.13 L10.4 22.75 C10.28 22.91 10.07 22.83 10.1 22.62 " +
                    "L10.75 13.65 L4.7 14.05 C4.51 14.07 4.42 13.89 4.54 13.73 Z",
        )
    )
    val boltStroke = listOf<Path>()
    val heart = PathParser.createPathFromPathData(
        "M28 9.5 C26.1 9.5 25.65 8.15 24 6.8 C21.36 4.64 18.15 3 14 3 " +
                "C6.41 3 0.25 9.16 0.25 16.75 C0.25 29 14 40.5 25.6 49.2 C26.32 49.74 27.17 50 28 50 " +
                "C28.83 50 29.68 49.74 30.4 49.2 C42 40.5 55.75 29 55.75 16.75 " +
                "C55.75 9.16 49.59 3 42 3 C37.85 3 34.64 4.64 32 6.8 C30.35 8.15 29.9 9.5 28 9.5 Z",
    )
}
