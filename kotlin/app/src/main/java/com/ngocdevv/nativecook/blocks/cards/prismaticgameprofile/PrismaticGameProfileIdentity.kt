package com.ngocdevv.nativecook.blocks.cards.prismaticgameprofile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** One mounted portrait and one of each native glyph; no card-copy crossfade. */
@Composable
internal fun PrismaticGameProfileIdentity(controller: PrismaticGameProfileController, artwork: PrismaticGameProfileArtwork) {
    val density=LocalDensity.current;val fs=density.fontScale
    val caption=when(controller.selected){0->"Crown";1->"Skills";else->"Diamond"}
    var metrics by remember(fs,caption) { mutableStateOf(mapOf(
        PrismaticGameProfileMotion.Kind.Name to (120f*fs to 38f*fs),
        PrismaticGameProfileMotion.Kind.Handle to (99f*fs to 18f*fs),
        PrismaticGameProfileMotion.Kind.Skills to ((if(caption=="Skills")30f else if(caption=="Crown")36f else 51f)*fs to 17f*fs),
        PrismaticGameProfileMotion.Kind.Score to (49f*fs to 26f*fs))) }
    val name=metrics.getValue(PrismaticGameProfileMotion.Kind.Name);val handle=metrics.getValue(PrismaticGameProfileMotion.Kind.Handle)
    val skills=metrics.getValue(PrismaticGameProfileMotion.Kind.Skills);val score=metrics.getValue(PrismaticGameProfileMotion.Kind.Score)
    val scoreScale=max(1f,min(2f,180/max(1f,score.first)))
    val scoreDefaultTop=max(578f,555+skills.second*1.15f+4)
    val footerShift=max(0f,scoreDefaultTop+score.second*scoreScale-657)
    val skillsTop=555-footerShift;val scoreTop=scoreDefaultTop-footerShift
    val handleTop=min(max(486f,449+name.second*1.1f+4),skillsTop-handle.second*1.08f-12)
    val nameTop=min(449f,handleTop-name.second*1.1f-4)
    val headingTop=109-8*(fs-1).coerceIn(0f,1f)
    val portraitTop=max(163f,headingTop+36*fs+14);val portraitHeight=max(140f,nameTop-16-portraitTop)
    val avatarScale=(portraitHeight-15.8f)/124
    val avatarY=portraitTop+portraitHeight/2+2.05f-avatarScale
    LaunchedEffect(metrics,fs) {
        val right=64.5f+score.first*scoreScale;val sealScale=min(1.05f,max(.3f,(346.5f-right-12)/114))
        val sealX=min(346.5f-57*sealScale,max(286.5f,right+12+57*sealScale))
        controller.identity=PrismaticGameProfileMotion.Identity(avatarY,avatarScale,portraitTop,portraitHeight,
            PrismaticGameProfileMotion.Glyph(name.first,name.second,64.5f+name.first*1.1f/2,nameTop+name.second*1.1f/2,1.1f),
            PrismaticGameProfileMotion.Glyph(handle.first,handle.second,64.5f+handle.first*1.08f/2,handleTop+handle.second*1.08f/2,1.08f),
            PrismaticGameProfileMotion.Glyph(skills.first,skills.second,64.5f+skills.first*1.15f/2,skillsTop+skills.second*1.15f/2,1.15f),
            PrismaticGameProfileMotion.Glyph(score.first,score.second,64.5f+score.first*scoreScale/2,scoreTop+score.second*scoreScale/2,scoreScale),sealX,sealScale)
    }
    Box(Modifier.offset(54.5.dp,portraitTop.dp).requiredSize(284.dp,portraitHeight.dp).graphicsLayer {
        val f=controller.frame();val layout=controller.identity
        val island=PrismaticGameProfileMotion.island(f.progress,f.reduced,controller.source,layout)
        val bounds=PrismaticGameProfileMotion.surface(f.progress,controller.cardOrigin(),f.reduced)
        val phase=if(f.reduced)if(f.progress>0)1f else 0f else PrismaticGameProfileMotion.step(f.progress,.46f,.62f)
        alpha=phase*PrismaticGameProfileMotion.coverage(bounds,island)
        translationX=(island.x+island.width/2-196.5f)*density.density
        translationY=(island.y+island.height/2-(portraitTop+portraitHeight/2))*density.density
        scaleX=island.width/284;scaleY=scaleX
    }.clip(RoundedCornerShape(28.dp)).background(Color(0xFFA66FC0)).border(1.5.dp,Color(0xFFD6AFE5),RoundedCornerShape(28.dp)).clearAndSetSemantics {}) {
        Image(artwork.image("landscape"),null,Modifier.fillMaxSize(),contentScale=ContentScale.Crop)
    }
    Box(Modifier.offset(126.5.dp,106.dp).size(140.dp).graphicsLayer {
        val f=controller.frame();val bounds=PrismaticGameProfileMotion.avatarBounds(f.progress,controller.source,controller.identity,f.reduced)
        val scale=bounds.width/118
        translationX=(bounds.x+59*scale-196.5f)*density.density
        translationY=(bounds.y+61*scale-176)*density.density
        scaleX=scale;scaleY=scale
    }.clearAndSetSemantics {}) {
        Canvas(Modifier.fillMaxSize().graphicsLayer { val f=controller.frame();alpha=PrismaticGameProfileMotion.sourcePortraitOpacity(f.progress,f.reduced) }) {
            drawCircle(Color(0xFF60D9EC));drawCircle(Color.White,radius=size.width*65.531915f/140)
        }
        Box(Modifier.offset(11.dp,9.dp).requiredSize(118.dp,124.dp).graphicsLayer {
            val f=controller.frame();val source=PrismaticGameProfileMotion.sourcePortraitOpacity(f.progress,f.reduced)
            clip=true;shape=RoundedCornerShape((14+48*source).dp)
        }) {
            Canvas(Modifier.fillMaxSize()) {val f=controller.frame();drawRect(Color(0xFFB68CD6).copy(alpha=PrismaticGameProfileMotion.sourcePortraitOpacity(f.progress,f.reduced)))}
            Image(artwork.image("avatar"),null,Modifier.wrapContentSize(Alignment.TopStart,unbounded=true).offset((-15).dp,(-16).dp).requiredSize(148.dp,156.dp).graphicsLayer {
                val f=controller.frame();val phase=1-PrismaticGameProfileMotion.sourcePortraitOpacity(f.progress,f.reduced)
                val sourceScale=94f/140;val factor=80*1.08f/(148*sourceScale)
                translationY=4*(1-phase)*density.density;scaleX=factor+(1-factor)*phase;scaleY=scaleX
            },contentScale=ContentScale.Fit)
        }
    }
    fun measured(kind: PrismaticGameProfileMotion.Kind,width: Float,height: Float) {
        val old=metrics.getValue(kind)
        if(width>0&&height>0&&(abs(old.first-width)>.1f||abs(old.second-height)>.1f))metrics=metrics+(kind to (width to height))
    }
    PrismaticGlyph(controller,PrismaticGameProfileMotion.Kind.Name,"Everen",60f,248f,273f,name.second,32f,38f,FontWeight.Black,Color(0xFF071E2C),true){w,h->measured(PrismaticGameProfileMotion.Kind.Name,w,h)}
    PrismaticGlyph(controller,PrismaticGameProfileMotion.Kind.Handle,"@evanoff92",70f,290f,253f,handle.second,13f,18f,FontWeight.SemiBold,Color(0xFF5C6A75),false){w,h->measured(PrismaticGameProfileMotion.Kind.Handle,w,h)}
    PrismaticGlyph(controller,PrismaticGameProfileMotion.Kind.Skills,caption,70f,546f,253f,skills.second,13f,17f,FontWeight.SemiBold,Color(0xFF5C6A75),false){w,h->measured(PrismaticGameProfileMotion.Kind.Skills,w,h)}
    PrismaticGlyph(controller,PrismaticGameProfileMotion.Kind.Score,"1.500",133f,567f,127f,score.second,20f,26f,FontWeight.Black,Color(0xFF071F2B),false){w,h->measured(PrismaticGameProfileMotion.Kind.Score,w,h)}
    val headingAccessible=controller.contentReady&&(controller.surface==PrismaticGameProfileController.Surface.Player||controller.demoPlayer)
    Column(Modifier.offset(64.5.dp,headingTop.dp).graphicsLayer {val f=controller.frame();alpha=if(f.progress>0)f.content else 0f;translationY=if(f.reduced)0f else 1.5f*(1-f.content)*density.density}.then(if(headingAccessible)Modifier else Modifier.clearAndSetSemantics {})) {
        Text("PLAYER CARD",Modifier.semantics {heading()},fontSize=14.sp,lineHeight=18.sp,fontWeight=FontWeight.Black,color=Color(0xFF111C42),letterSpacing=.7.sp,style=TextStyle(platformStyle=PlatformTextStyle(includeFontPadding=false)))
        Text("Profile preview",fontSize=12.sp,lineHeight=18.sp,fontWeight=FontWeight.SemiBold,color=Color(0xFF8B718C),style=TextStyle(platformStyle=PlatformTextStyle(includeFontPadding=false)))
    }
}

@Composable
private fun PrismaticGlyph(controller: PrismaticGameProfileController,kind: PrismaticGameProfileMotion.Kind,text: String,left: Float,top: Float,width: Float,height: Float,font: Float,line: Float,weight: FontWeight,color: Color,isHeading: Boolean,onMeasure: (Float,Float)->Unit) {
    val density=LocalDensity.current
    Box(Modifier.offset(left.dp,top.dp).requiredSize(width.dp,height.dp).graphicsLayer {
        val f=controller.frame();val pose=PrismaticGameProfileMotion.glyph(kind,f.progress,f.reduced,controller.source,controller.identity)
        val b=pose.first
        translationX=(b.x+b.width/2-196.5f)*density.density
        translationY=(b.y+b.height/2-(top+height/2))*density.density
        scaleX=pose.second;scaleY=scaleX
        alpha=if(kind==PrismaticGameProfileMotion.Kind.Score&&controller.selected!=1)0f else 1f
    }.then(if(kind==PrismaticGameProfileMotion.Kind.Score&&controller.selected!=1)Modifier.clearAndSetSemantics {}else Modifier).then(if(isHeading)Modifier.semantics { heading() }else Modifier),contentAlignment=Alignment.TopCenter) {
        Text(text,Modifier.fillMaxWidth(),fontSize=font.sp,lineHeight=line.sp,fontFamily=FontFamily.SansSerif,fontWeight=weight,color=color,letterSpacing=(if(kind==PrismaticGameProfileMotion.Kind.Name)-.7f else 0f).sp,textAlign=TextAlign.Center,maxLines=1,softWrap=false,overflow=TextOverflow.Visible,style=TextStyle(platformStyle=PlatformTextStyle(includeFontPadding=false)),onTextLayout={ result ->
            if(result.lineCount>0)onMeasure((result.getLineRight(0)-result.getLineLeft(0))/density.density,(result.getLineBottom(0)-result.getLineTop(0))/density.density)
        })
    }
}
