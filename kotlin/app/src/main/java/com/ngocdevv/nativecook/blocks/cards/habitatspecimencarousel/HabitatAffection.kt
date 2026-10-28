package com.ngocdevv.nativecook.blocks.cards.habitatspecimencarousel

import android.graphics.Paint
import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import kotlin.math.max
import kotlin.math.min

/** Heart colours per habitat: ember, spray and petal. */
private val HEARTS = arrayOf(floatArrayOf(1f, 0.6f, 0.42f), floatArrayOf(0.62f, 0.89f, 1f), floatArrayOf(0.78f, 0.94f, 0.56f))

/** How far above the hero the hearts may rise before the layer ends. */
private const val HEADROOM = 70f

private fun alive(motion: HabitatMotion): Boolean =
    motion.pet.value > 0f || (motion.petUntil >= 0f && motion.petClock < motion.petUntil + 1.4f)

/** Hearts rising from the head of the specimen being petted; transparent and still otherwise. */
@Composable
internal fun HabitatAffection(motion: HabitatMotion, width: Float, heroTop: Float, heroHeight: Float, size: Float) {
    if (!supportsAgsl()) return
    val density = LocalDensity.current.density
    val top = max(0f, heroTop - HEADROOM)
    val height = heroTop + heroHeight - top
    val shader = remember { AffectionShader() }
    Box(
        Modifier
            .offset(0.dp, top.dp)
            .size(width.dp, height.dp)
            .drawBehind {
                motion.observe()
                if (Build.VERSION.SDK_INT < 33 || !alive(motion)) return@drawBehind
                val index = nearestIndex(motion.position)
                val head = RIGS[index].joints[0].center
                val k = size / 512f
                val color = HEARTS[index]
                shader.runtime.setFloatUniform("resolution", width, height)
                shader.runtime.setFloatUniform("source", (width - size) / 2 + head[0] * k, heroTop - top + (heroHeight - size) / 2 + head[1] * k)
                shader.runtime.setFloatUniform("clock", motion.petClock)
                shader.runtime.setFloatUniform("until", min(motion.petUntil, 1e6f))
                shader.runtime.setFloatUniform("unit", width / 393f)
                shader.runtime.setFloatUniform("color", color[0], color[1], color[2])
                shader.runtime.setFloatUniform("reduced", if (motion.reduced) 1f else 0f)
                drawIntoCanvas { canvas ->
                    val native = canvas.nativeCanvas
                    native.save()
                    native.scale(density, density)
                    native.drawRect(0f, 0f, width, height, shader.paint)
                    native.restore()
                }
            },
    )
}

@RequiresApi(33)
private class AffectionShader {
    val runtime = RuntimeShader(AFFECTION_AGSL)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).also { it.shader = runtime }
}
