package com.ngocdevv.nativecook.blocks.gestures.raffledrumspin

/** Constant-fold the coarse seam samples without changing their order or the refinements. */
internal object RaffleDrumSpinShaderCompile {
    private val coarseSearch = Regex(
        """for\s*\(\s*int\s+([ik])\s*=\s*0\s*;\s*\1\s*<\s*(40|48)\s*;\s*\1\+\+\s*\)\s*\{\s*float\s+t\s*=\s*float\(\1\)\s*\*\s*([0-9.]+)\s*(?:;\s*float\s+|,\s*)dd\s*=\s*distance\(o,\s*seam\(t\)\)\s*;\s*if\s*\(\s*dd\s*<\s*best\s*\)\s*\{\s*best\s*=\s*dd\s*;\s*bt\s*=\s*t\s*;\s*\}\s*\}""",
    )

    fun unrollCoarseSeam(source: String): String = coarseSearch.replace(source) { match ->
        val count = match.groupValues[2].toInt()
        val step = match.groupValues[3]
        (0 until count).joinToString("\n") { index ->
            "{ float t = float($index) * $step; float dd = distance(o, seam(t)); if (dd < best) { best = dd; bt = t; } }"
        }
    }
}
