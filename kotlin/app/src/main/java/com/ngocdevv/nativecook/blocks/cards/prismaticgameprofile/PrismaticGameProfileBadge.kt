package com.ngocdevv.nativecook.blocks.cards.prismaticgameprofile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.unit.dp
import kotlin.math.abs
import kotlin.math.min

@Composable
internal fun PrismaticGameProfileBadge(index: Int, controller: PrismaticGameProfileController, materials: PrismaticGameProfileMaterials) {
    val density=LocalDensity.current
    val colors=when(index){0->listOf(Color(0xFFB766F7),Color(0xFFC780FA));1->listOf(Color(0xFFFFCA4C),Color(0xFFFFD866));else->listOf(Color(0xFFD5E43B),Color(0xFFE3EC67))}
    Box(Modifier.offset(117.5.dp,376.dp).size(158.dp).graphicsLayer {
        val f=controller.frame();val p=if(f.index==index)f.progress else 0f;val phase=PrismaticGameProfileMotion.seal(p,f.reduced)
        val current=PrismaticGameProfileMotion.badgeOrigin(f.rail,index,controller.source)
        val source=if(p>0)controller.cardOrigin()else current
        val x=source.x+source.width/2;val y=source.y+source.height/2
        val layout=controller.identity
        translationX=(x-196.5f+(layout.sealX-x)*phase.first)*density.density
        translationY=(y-455+(587-y)*phase.second-PrismaticGameProfileMotion.sealLift(p,f.reduced))*density.density
        scaleX=source.width/158+(layout.sealScale-source.width/158)*phase.third;scaleY=scaleX
        alpha=1-.3f*min(1f,abs(index-f.rail))*(1-phase.second)
    }.clearAndSetSemantics {}) {
        Canvas(Modifier.fillMaxSize().graphicsLayer {val f=controller.frame();alpha=if(f.index==index)1-PrismaticGameProfileMotion.step(f.progress,.015f,.17f)else 1f}) {
            repeat(52){i->drawArc(colors[i%2],startAngle=i*360f/52,sweepAngle=360f/52+.1f,useCenter=true)}
        }
        Canvas(Modifier.fillMaxSize()) {
            val f=controller.frame()
            drawIntoCanvas { target -> val native=target.nativeCanvas;native.save();native.scale(density.density,density.density);materials.drawCaustic(native,index,f);native.restore() }
        }
        Box(Modifier.offset(22.dp,16.dp).requiredSize(114.dp,126.dp).graphicsLayer {
            val f=controller.frame();val p=PrismaticGameProfileMotion.seal(if(f.index==index)f.progress else 0f,f.reduced).third
            val distance=min(1f,abs(index-f.rail));val source=PrismaticGameProfileMotion.badgeOrigin(f.rail,index,controller.source)
            val ratio=when(index){1->728f/768;0->512f/410;else->512f/408}
            val fitWidth=min(132-52*distance,(140-68*distance)*ratio)*controller.source.railSize/164
            val factor=fitWidth/(114*source.width/158)
            scaleX=1+(factor-1)*(1-p);scaleY=scaleX
        }) {
            Canvas(Modifier.fillMaxSize()) {val f=controller.frame();drawIntoCanvas { target ->val native=target.nativeCanvas;native.save();native.scale(density.density,density.density);materials.drawBadge(native,index,f);native.restore()}}
        }
    }
}
