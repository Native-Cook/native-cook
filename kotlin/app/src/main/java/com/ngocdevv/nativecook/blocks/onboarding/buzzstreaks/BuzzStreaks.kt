package com.ngocdevv.nativecook.blocks.onboarding.buzzstreaks

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Typeface
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.border
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.GenericShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.drawscope.scale
import androidx.compose.ui.graphics.painter.BitmapPainter
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.rememberReducedMotion
import org.json.JSONObject
import kotlin.math.*

private val BuzzPanel = arrayOf(0.0 to 780.0,4.15 to 780.0,4.35 to 550.0,4.65 to 445.0,7.25 to 445.0,7.5 to 295.0,7.75 to 246.0,10.7 to 246.0)
private val BuzzHeader = arrayOf(0.0 to 0.0,7.25 to 0.0,7.75 to -20.0,10.7 to -20.0)
private val BuzzBonus = arrayOf(0.0 to 0.0,7.55 to 0.0,7.85 to 1.0,10.7 to 1.0)
private val BuzzCaption = arrayOf(0.0 to 1.0,4.0 to 1.0,4.25 to 0.0,10.7 to 0.0)
private val BuzzRibbon = GenericShape { size, _ ->
    moveTo(0f,0f)
    lineTo(size.width,0f)
    lineTo(size.width*.94f,size.height)
    lineTo(size.width*.06f,size.height)
    close()
}

/** Exact-reference study. One clock; mascot artwork is separate from native controls and copy. */
@Composable
fun BuzzStreaks(
    autoPlay: Boolean = false,
    enhancedText: Boolean = true,
    motionProfile: BuzzStreaksMotionProfile = BuzzStreaksMotionProfile.Approved,
) {
    val context=LocalContext.current
    val density=LocalDensity.current
    val reduced=rememberReducedMotion()
    val haptics=LocalHapticFeedback.current
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    var foreground by remember { mutableStateOf(lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED)) }
    DisposableEffect(lifecycle) {
        val observer=LifecycleEventObserver { _, _ -> foreground=lifecycle.currentState.isAtLeast(Lifecycle.State.RESUMED) }
        lifecycle.addObserver(observer);onDispose { lifecycle.removeObserver(observer) }
    }
    val images=remember(context) {
        val artwork=JSONObject(context.assets.open("buzzstreaks/buzzstreaks_artwork.json").bufferedReader().use { it.readText() })
        listOf("background","wordmark","invite","phone","coin","daycoin","start-label","day-1","day-2","day-3","day-4").associateWith { name ->
            val bitmap=context.assets.open("buzzstreaks/buzzstreaks_$name.png").use { stream ->
                requireNotNull(BitmapFactory.decodeStream(stream)) { "Cannot decode the $name artwork" }
            }
            val rect=artwork.optJSONObject(name)?.getJSONArray("rect")
            val cropped=if(rect==null)bitmap else Bitmap.createBitmap(bitmap,
                rect.getInt(0),rect.getInt(1),rect.getInt(2),rect.getInt(3))
            cropped.asImageBitmap()
        }
    }
    val mascots=remember(context) { BuzzStreaksMascots(context.assets) }
    val rewards=remember(context) { BuzzStreaksRewards(context.assets) }
    val illustrations=remember(context) { BuzzStreaksIllustrations(context.assets) }
    val textMotion=remember(context) { BuzzStreaksTextMotion(context.assets) }
    val signatureLoader=remember(context) { lazy { BuzzStreaksSignature(context.assets) } }
    val signature=if(motionProfile==BuzzStreaksMotionProfile.Signature&&!reduced)signatureLoader.value else null
    val poseData=remember { JSONObject(context.assets.open("buzzstreaks/buzzstreaks_poses.json").bufferedReader().use { it.readText() }) }
    val layout=remember {listOf("panel","header","bonusY","daysX","invite_opacity","invite_scale","invite_y","back_opacity","back_scale","back_y").associateWith {name ->val a=poseData.getJSONObject("layout").getJSONArray(name);Array(a.length()) {i ->val b=a.getJSONArray(i);b.getDouble(0) to b.getDouble(1)}}}
    val medium=remember { FontFamily(Typeface.createFromAsset(context.assets,"buzzstreaks/buzzstreaks_Gilroy-Medium.ttf")) }
    val semi=remember { FontFamily(Typeface.createFromAsset(context.assets,"buzzstreaks/buzzstreaks_Gilroy-SemiBold.ttf")) }
    val heavy=remember { FontFamily(Typeface.createFromAsset(context.assets,"buzzstreaks/buzzstreaks_Gilroy-ExtraBold.ttf")) }
    val bonusCopy=remember {
        buildAnnotatedString {
            append("Get ")
            withStyle(SpanStyle(color=Color.White,background=Color(0xFFF16CA4))) {append("₹5 bonus")}
            append(" each\nfor your first 5 friends")
        }
    }
    val clock=remember { mutableDoubleStateOf(if(reduced)10.699 else 0.0) }
    var playing by remember { mutableStateOf(autoPlay) }
    var replay by remember { mutableIntStateOf(0) }
    fun play(from:Double=0.0) { haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove);clock.doubleValue=if(reduced)10.699 else from;playing=!reduced;replay++ }
    LaunchedEffect(foreground,playing,replay,reduced) {
        if(reduced) {clock.doubleValue=10.699;playing=false;return@LaunchedEffect}
        if(!foreground||!playing)return@LaunchedEffect
        var last=withFrameNanos {it}
        while(clock.doubleValue<10.699) {val now=withFrameNanos {it};clock.doubleValue=min(10.699,clock.doubleValue+(now-last)/1e9);last=now}
        playing=false
    }
    BoxWithConstraints(Modifier.fillMaxSize().background(Color(0xFF6623A9))) {
        val fit=min(maxWidth.value/360f,maxHeight.value/780f)
        Box(Modifier.requiredSize(360.dp,780.dp).align(Alignment.Center).graphicsLayer {scaleX=fit;scaleY=fit}.clip(RoundedCornerShape(0.dp))) {
            Canvas(Modifier.fillMaxSize()) {
                val unit=size.width/360f
                scale(unit,unit,Offset.Zero) {
                    fun image(name:String,x:Float,y:Float,w:Float,h:Float) {images[name]?.let {drawImage(it,dstOffset=IntOffset(x.roundToInt(),y.roundToInt()),dstSize=IntSize(w.roundToInt(),h.roundToInt()),filterQuality=FilterQuality.High)} }
                    val t=clock.doubleValue
                    image("background",0f,0f,360f,780f)
                    drawIntoCanvas {
                        val canvas=it.nativeCanvas
                        rewards.drawBackground(t,canvas)
                        mascots.draw(t,canvas,signature)
                        signature?.drawRelay(t,canvas)
                        rewards.drawForeground(t,canvas,signature)
                    }
                }
            }
            Box(Modifier.offset(x=86.dp,y=79.dp).size(190.dp,163.dp).graphicsLayer {translationY=buzzFlow(clock.doubleValue,layout.getValue("header")).toFloat()*density.density}.semantics {heading();contentDescription="Buzz Streaks. Win up to ₹1000."}) {
                Image(BitmapPainter(images.getValue("wordmark")),null,Modifier.offset(x=40.dp).size(107.dp,51.dp))
                Canvas(Modifier.offset(y=64.dp).size(190.dp,99.dp)) {
                    val unit=size.width/190f
                    scale(unit,unit,Offset.Zero) {drawIntoCanvas {illustrations.drawPrize(clock.doubleValue,enhancedText&&!reduced,it.nativeCanvas)}}
                }
            }
            val inviteCaption=Modifier.offset(y=634.dp).width(360.dp).height(24.dp).graphicsLayer {alpha=buzzFlow(clock.doubleValue,layout.getValue("invite_opacity")).toFloat();scaleX=buzzFlow(clock.doubleValue,layout.getValue("invite_scale")).toFloat();scaleY=scaleX;translationY=buzzFlow(clock.doubleValue,layout.getValue("invite_y")).toFloat()*density.density}
            val backCaption=Modifier.offset(y=634.dp).width(360.dp).height(24.dp).graphicsLayer {alpha=buzzFlow(clock.doubleValue,layout.getValue("back_opacity")).toFloat();scaleX=buzzFlow(clock.doubleValue,layout.getValue("back_scale")).toFloat();scaleY=scaleX;translationY=buzzFlow(clock.doubleValue,layout.getValue("back_y")).toFloat()*density.density}
            if(enhancedText&&!reduced) {
                Canvas(inviteCaption.semantics {contentDescription="Invite your friends by buzzing"}) {
                    val t=clock.doubleValue;val unit=size.width/360f
                    scale(unit,unit,Offset.Zero) {drawIntoCanvas {textMotion.drawCaption("Invite your friends by buzzing",t,.05,autoPlay||playing||t>0,it.nativeCanvas)}}
                }
                Canvas(backCaption.semantics {contentDescription="They buzz you back"}) {
                    val t=clock.doubleValue;val unit=size.width/360f
                    scale(unit,unit,Offset.Zero) {drawIntoCanvas {textMotion.drawCaption("They buzz you back",t,1.52,autoPlay||playing||t>0,it.nativeCanvas)}}
                }
            } else {
                Text("Invite your friends by buzzing",inviteCaption,color=Color.White,fontSize=19.6.sp,fontFamily=semi,letterSpacing=0.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
                Text("They buzz you back",backCaption,color=Color.White,fontSize=19.6.sp,fontFamily=semi,letterSpacing=0.sp,textAlign=androidx.compose.ui.text.style.TextAlign.Center)
            }
            Box(Modifier.fillMaxWidth().height(674.dp).clipToBounds()) {
                Box(Modifier.offset(x=15.dp).size(330.dp,224.dp).graphicsLayer {translationY=buzzFlow(clock.doubleValue,layout.getValue("panel")).toFloat()*density.density}.clip(RoundedCornerShape(25.dp)).background(Color(0xFF7A3BBB)).border(1.dp,Brush.verticalGradient(listOf(Color(0xFFAA65D3),Color.Transparent)),RoundedCornerShape(25.dp))) {
                    Canvas(Modifier.fillMaxSize()) {
                        val d=size.width/330
                        drawLine(Color(0xFFBD84E3),Offset(33*d,60*d),Offset(33*d,185*d),d,pathEffect=PathEffect.dashPathEffect(floatArrayOf(2*d,3*d)))
                    }
                    Column(Modifier.offset(y=37.dp)) {
                        BuzzRow(images.getValue("invite"),"Buzz friends by inviting","Send them a link to join the game",semi,medium,4,4,32,33)
                        BuzzRow(images.getValue("phone"),"They “Buzz” you back","By opening the link",semi,medium,1,-4,36,44)
                        BuzzRow(images.getValue("coin"),"Both earn Free Cash!","After buzzing each other",semi,medium,5,6,28,30)
                    }
                    Box(Modifier.offset(x=98.dp).size(132.dp,20.dp).clip(BuzzRibbon).background(Brush.verticalGradient(listOf(Color(0xFFE875FF),Color(0xFFAA00F0)))),contentAlignment=Alignment.TopCenter) {Text("HOW BUZZING WORKS",Modifier.padding(top=1.dp),color=Color.White,fontSize=9.5.sp,lineHeight=16.sp,fontFamily=heavy,letterSpacing=0.sp)}
                }
                if(signature!=null)Canvas(Modifier.offset(x=15.dp).size(330.dp,224.dp).graphicsLayer {translationY=buzzFlow(clock.doubleValue,layout.getValue("panel")).toFloat()*density.density}.clip(RoundedCornerShape(25.dp))) {
                    val unit=size.width/330f
                    scale(unit,unit,Offset.Zero) {drawIntoCanvas {signature.drawCardLips(clock.doubleValue,it.nativeCanvas)}}
                }
                Row(Modifier.offset(x=15.dp,y=511.dp).size(330.dp,158.dp).graphicsLayer {alpha=if(clock.doubleValue>=7.5)1f else 0f;translationY=(buzzFlow(clock.doubleValue,layout.getValue("bonusY"))-511).toFloat()*density.density},horizontalArrangement=Arrangement.spacedBy(8.dp)) {
                    Box(Modifier.size(157.dp,158.dp).clip(RoundedCornerShape(21.dp)).background(Color(0xFF7A3BBB))) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {Text("Buzz more friends",color=Color.White,fontSize=15.sp,lineHeight=20.sp,fontFamily=semi,letterSpacing=0.sp,maxLines=1,softWrap=false);Text(bonusCopy,color=Color(0xFFC9A5E6),fontSize=12.8.sp,lineHeight=16.sp,fontFamily=medium,letterSpacing=0.sp)}
                        Canvas(Modifier.fillMaxSize()) {
                            val unit=size.width/157f
                            scale(unit,unit,Offset.Zero) {drawIntoCanvas {illustrations.drawBonus(clock.doubleValue,it.nativeCanvas)}}
                        }
                    }
                    Box(Modifier.size(165.dp,158.dp).clip(RoundedCornerShape(21.dp)).background(Color(0xFF7A3BBB))) {
                        Column(Modifier.padding(16.dp),verticalArrangement=Arrangement.spacedBy(8.dp)) {Text("Daily streaks",color=Color.White,fontSize=15.sp,lineHeight=20.sp,fontFamily=semi,letterSpacing=0.sp,maxLines=1,softWrap=false);Text("Keep the streak alive\nto earn more",color=Color(0xFFC9A5E6),fontSize=12.8.sp,lineHeight=16.sp,fontFamily=medium,letterSpacing=0.sp)}
                        Row(Modifier.offset(x=5.dp,y=89.dp).wrapContentWidth(Alignment.Start,unbounded=true).requiredWidth(340.dp).graphicsLayer {transformOrigin=TransformOrigin(0f,0f);translationX=buzzFlow(clock.doubleValue,layout.getValue("daysX")).toFloat()*density.density},horizontalArrangement=Arrangement.spacedBy(17.dp)) {
                            for(day in 1..7)Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(7.dp)) {
                                val imageModifier=if(signature!=null&&day>=5)Modifier.size(34.dp).graphicsLayer {
                                    val weight=signature.dominoWeight(day,clock.doubleValue)
                                    translationY=-signature.dominoTranslation*weight*density.density
                                    rotationZ=-signature.dominoRotation*weight
                                    scaleX=1f+signature.dominoScale*weight
                                    scaleY=scaleX
                                } else Modifier.size(34.dp)
                                Image(BitmapPainter(images.getValue(if(day>=5)"daycoin" else "day-$day")),"₹${min(day,5)}",imageModifier,contentScale=ContentScale.FillBounds)
                                Text("DAY $day",color=Color.White,fontSize=11.sp,lineHeight=12.sp,fontFamily=heavy,letterSpacing=0.sp)
                            }
                        }
                    }
                }
            }
            Box(Modifier.offset(x=14.dp,y=674.dp).size(332.dp,57.dp).clip(RoundedCornerShape(7.dp)).background(Brush.horizontalGradient(listOf(Color(0xFFB4D61A),Color(0xFFB1D214)))).clickable(role=Role.Button,onClickLabel="Start buzzing") {play()}.semantics {contentDescription="Start buzzing"},contentAlignment=Alignment.Center) {
                Box(Modifier.fillMaxSize().padding(start=6.dp,end=6.dp,top=4.dp,bottom=6.dp).clip(RoundedCornerShape(4.dp)).background(Brush.horizontalGradient(listOf(Color(0xFFC9E73C),Color(0xFFD9F650)))))
                Box(Modifier.size(106.dp,23.dp)) {
                    Image(BitmapPainter(images.getValue("start-label")),null,Modifier.fillMaxSize())
                    if(enhancedText&&!reduced) Canvas(Modifier.fillMaxSize()) {
                        val unit=size.width/106f
                        scale(unit,unit,Offset.Zero) {drawIntoCanvas {textMotion.drawCtaShine(clock.doubleValue,it.nativeCanvas)}}
                    }
                }
            }
            BuzzNavigation("←","Replay introduction",Modifier.offset(x=9.dp,y=66.dp)) {play()}
            BuzzNavigation("ⓘ","How buzzing works",Modifier.offset(x=307.dp,y=66.dp)) {play(7.25)}
        }
    }
}
@Composable
private fun BuzzRow(art:ImageBitmap,title:String,body:String,semi:FontFamily,medium:FontFamily,x:Int,y:Int,w:Int,h:Int) {
    Row(Modifier.height(66.dp).padding(start=16.dp),horizontalArrangement=Arrangement.spacedBy(10.dp)) {
        Box(Modifier.size(42.dp,44.dp),contentAlignment=Alignment.TopStart) {Image(BitmapPainter(art),null,Modifier.offset(x=x.dp,y=y.dp).size(w.dp,h.dp),contentScale=ContentScale.FillBounds)}
        Column(Modifier.padding(top=3.dp)) {Text(title,color=Color.White,fontSize=15.25.sp,lineHeight=20.sp,fontFamily=semi,letterSpacing=0.sp);Text(body,color=Color(0xFFC9A5E6),fontSize=12.8.sp,lineHeight=18.sp,fontFamily=medium,letterSpacing=0.sp)}
    }
}
@Composable
private fun BuzzNavigation(icon:String,label:String,modifier:Modifier,action:()->Unit) {
    Box(modifier.size(45.dp).clickable(role=Role.Button,onClickLabel=label,onClick=action).semantics {contentDescription=label},contentAlignment=Alignment.Center) {Box(Modifier.size(33.dp).background(Color.White,CircleShape),contentAlignment=Alignment.Center) {Text(icon,color=Color(0xFF17221B),fontSize=if(icon=="ⓘ")20.sp else 23.sp)}}
}
/** Rossark monotone cubic tracks preserve holds and avoid stop-and-go between acting poses. */
private fun buzzFlow(t:Double,k:Array<Pair<Double,Double>>):Double {
    if(t<=k.first().first)return k.first().second;if(t>=k.last().first)return k.last().second
    var i=0;while(k[i+1].first<t)i++
    fun slope(j:Int)=(k[j+1].second-k[j].second)/(k[j+1].first-k[j].first)
    fun tangent(j:Int):Double {if(j==0||j==k.lastIndex)return 0.0;val a=slope(j-1);val b=slope(j);if(a*b<=0)return 0.0;val h0=k[j].first-k[j-1].first;val h1=k[j+1].first-k[j].first;val w0=2*h1+h0;val w1=h1+2*h0;return(w0+w1)/(w0/a+w1/b)}
    val h=k[i+1].first-k[i].first;val s=(t-k[i].first)/h;val s2=s*s;val s3=s2*s
    return(2*s3-3*s2+1)*k[i].second+(s3-2*s2+s)*h*tangent(i)+(-2*s3+3*s2)*k[i+1].second+(s3-s2)*h*tangent(i+1)
}
