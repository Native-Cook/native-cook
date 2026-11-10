package com.ngocdevv.nativecook.blocks.cards.goldemblemmembership

import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.sp
import androidx.compose.ui.unit.dp

@Composable
internal fun GoldEmblemMembershipPress(reduced: Boolean, modifier: Modifier, label: String,
                                      background: Color, ink: Color, font: FontFamily) {
    var down by remember { mutableStateOf(false) }
    val p by animateFloatAsState(if(down)1f else 0f,tween(150,easing=CubicBezierEasing(0.23f,1f,0.32f,1f)),label="Receive press")
    Box(modifier.graphicsLayer {
        alpha=if(reduced)1-0.28f*p else 1f
        scaleX=if(reduced)1f else 1-0.04f*p; scaleY=scaleX
    }.background(background,CircleShape).semantics(mergeDescendants=true) {
        role=Role.Button; contentDescription=label; onClick { true }
    }.pointerInput(Unit) {
        awaitEachGesture {
            val first=awaitFirstDown(); first.consume(); down=true
            try {
                do {
                    val e=awaitPointerEvent()
                    val change=e.changes.firstOrNull { it.id==first.id }
                    if(change!=null) {
                        val p=change.position
                        down=p.x>=-16.dp.toPx() && p.y>=-16.dp.toPx() && p.x<=size.width+16.dp.toPx() && p.y<=size.height+16.dp.toPx()
                    }
                    e.changes.forEach { it.consume() }
                } while(e.changes.any { it.pressed })
            } finally { down=false }
        }
    },contentAlignment=Alignment.Center) {
        Text("Receive",color=ink,fontFamily=font,fontSize=14.sp)
    }
}
