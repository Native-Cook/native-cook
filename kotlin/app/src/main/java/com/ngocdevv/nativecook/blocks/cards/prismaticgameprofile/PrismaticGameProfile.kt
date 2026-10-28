package com.ngocdevv.nativecook.blocks.cards.prismaticgameprofile

import android.view.HapticFeedbackConstants
import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.CubicBezierEasing
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.util.VelocityTracker
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.onClick
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.PlatformTextStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.ngocdevv.nativecook.shared.BlockSystemBars
import com.ngocdevv.nativecook.shared.rememberLiveReducedMotion
import kotlin.math.*

private val LocalPrismaticBaseEnabled=staticCompositionLocalOf { true }

/** Approved layout B + reversible Peel. All copy and artwork stay local to this block. */
@Composable
fun PrismaticGameProfile(modifier: Modifier=Modifier,autoPlay: Boolean=false,previewTime: Double?=null,previewReducedMotion: Boolean=false) {
    BlockSystemBars(darkIcons=false)
    val density=LocalDensity.current;val view=LocalView.current;val scope=rememberCoroutineScope()
    val systemReduced=rememberLiveReducedMotion();var localReduced by remember { mutableStateOf(false) }
    val reduced=systemReduced||localReduced||previewReducedMotion
    val controller=remember(autoPlay,previewTime) { PrismaticGameProfileController(scope,autoPlay,previewTime,reduced) {view.performHapticFeedback(HapticFeedbackConstants.CLOCK_TICK);Unit} }
    val source=remember(density.fontScale) {PrismaticGameProfileMotion.source(density.fontScale)}
    LaunchedEffect(source){controller.source=source}
    LaunchedEffect(reduced){controller.updateReduced(reduced)}
    val lifecycle=LocalLifecycleOwner.current.lifecycle
    DisposableEffect(controller,lifecycle){
        val observer=LifecycleEventObserver { _,event ->if(event==Lifecycle.Event.ON_STOP)controller.updateForeground(false)else if(event==Lifecycle.Event.ON_START)controller.updateForeground(true) }
        lifecycle.addObserver(observer);controller.updateForeground(lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED))
        onDispose {lifecycle.removeObserver(observer);controller.dispose()}
    }
    val artwork=PrismaticGameProfileArtwork.rememberArtwork()
    LaunchedEffect(controller,artwork){if(artwork!=null){controller.syncPreview();controller.runDemo()}}
    val player=controller.surface==PrismaticGameProfileController.Surface.Player||controller.demoPlayer
    BackHandler(controller.surface!=PrismaticGameProfileController.Surface.None||controller.demoPlayer){controller.close()}
    BoxWithConstraints(modifier.fillMaxSize().background(Color.White)) {
        val scale=min(maxWidth.value/393,maxHeight.value/852)
        if(artwork!=null) {
            val materials=remember(artwork){PrismaticGameProfileMaterials(artwork)}
            Box(Modifier.align(Alignment.Center).requiredSize(393.dp,852.dp).graphicsLayer {scaleX=scale;scaleY=scale}.pointerInput(controller,density.density) {
                awaitEachGesture {
                    val down=awaitFirstDown(requireUnconsumed=false)
                    val start=down.position/density.density;val f=controller.frame();val bounds=PrismaticGameProfileMotion.surface(f.progress,controller.cardOrigin(),f.reduced)
                    val origin=PrismaticGameProfileMotion.badgeOrigin(f.rail,f.rail.roundToInt().coerceIn(0,2),controller.source)
                    val inSelected=(start.x-origin.x-origin.width/2).pow(2)+(start.y-origin.y-origin.height/2).pow(2)<=(origin.width/2+8).pow(2)
                    val inRail=start.y>=controller.source.railTop-8&&start.y<=controller.source.railTop+controller.source.railSize+8
                    val inPlayer=f.progress>0&&bounds.contains(start.x,start.y,10f)
                    val tracker=VelocityTracker();tracker.addPosition(down.uptimeMillis,down.position)
                    var mode=0;var finished=false;                    try {
                        while(true){
                            val event=awaitPointerEvent();val change=event.changes.firstOrNull {it.id==down.id}?:break
                            tracker.addPosition(change.uptimeMillis,change.position)
                            val delta=(change.position-down.position)/density.density
                            if(mode==0&&change.pressed&&!change.isConsumed&&max(abs(delta.x),abs(delta.y))>=6){
                                if(inPlayer&&!controller.reduced){
                                    mode=if(f.progress>=.97f&&(abs(delta.x)>=abs(delta.y)*.8f||delta.y<0))3 else 2
                                    if(mode==3)controller.beginTilt()else controller.beginPull(false)
                                } else if(f.progress<=0&&inRail){
                                    if(abs(delta.x)>=abs(delta.y)){mode=1;controller.beginRail()}
                                    else if(delta.y<0&&inSelected&&!controller.reduced){mode=2;controller.beginPull(true)}
                                    else mode=-1
                                } else mode=-1
                            }
                            when(mode){1->{controller.moveRail(delta.x);change.consume()};2->{controller.movePull(delta.y);change.consume()};3->{controller.moveTilt(delta.x,delta.y);change.consume()}}
                            if(!change.pressed){
                                val velocity=tracker.calculateVelocity()
                                when(mode){1->controller.releaseRail(velocity.x/density.density,false);2->controller.releasePull(velocity.y/density.density,false);3->controller.releaseTilt(false)}
                                if(mode==0&&!change.isConsumed){
                                    if(controller.surface==PrismaticGameProfileController.Surface.Statistics)controller.close()
                                    else if(f.progress>0&&!bounds.contains(start.x,start.y,16f))controller.close()
                                    else if(f.progress<=0&&inRail){
                                        val index=(f.rail+(start.x-196.5f)/146).roundToInt().coerceIn(0,2)
                                        val hit=PrismaticGameProfileMotion.badgeOrigin(f.rail,index,controller.source)
                                        if(hit.contains(start.x,start.y,8f)){if(abs(f.rail-index)<.04f)controller.open(PrismaticGameProfileController.Surface.Player)else controller.select(index)}
                                    }
                                }
                                finished=true;break
                            }
                        }
                    } finally {
                        if(!finished){when(mode){1->controller.releaseRail(0f,true);2->controller.releasePull(0f,true);3->controller.releaseTilt(true)}}
                    }
                }
            }) {
                CompositionLocalProvider(LocalPrismaticBaseEnabled provides (controller.surface==PrismaticGameProfileController.Surface.None&&!controller.demoPlayer)) {
                    Box(Modifier.fillMaxSize().then(if(controller.surface!=PrismaticGameProfileController.Surface.None||controller.demoPlayer)Modifier.clearAndSetSemantics {}else Modifier)) { PrismaticBase(controller,artwork,source,reduced) }
                }
                repeat(3){index->key(index){
                    Box(Modifier.fillMaxSize().zIndex(if(controller.selected==index)1002f else 0f).graphicsLayer {
                        val f=controller.frame();val p=if(f.index==index&&!f.reduced)f.progress else 0f
                        transformOrigin=TransformOrigin(.5f,380f/852);cameraDistance=1000*density.density
                        rotationX=f.tiltX*p;rotationY=f.tiltY*p;rotationZ=-1.25f*p
                        alpha=if(f.reduced&&f.index==index&&f.progress>0)f.content else 1f
                    }) {PrismaticGameProfileBadge(index,controller,materials)}
                }}
                Canvas(Modifier.fillMaxSize().zIndex(10f).clearAndSetSemantics {}) {val f=controller.frame();drawRect(Color(0xFF161127).copy(alpha=.62f*f.progress))}
                Box(Modifier.fillMaxSize().zIndex(1001f).graphicsLayer {
                    val f=controller.frame();val p=if(f.reduced)0f else f.progress
                    transformOrigin=TransformOrigin(.5f,380f/852);cameraDistance=1000*density.density
                    rotationX=f.tiltX*p;rotationY=f.tiltY*p;rotationZ=-1.25f*p
                    alpha=if(f.reduced&&f.progress>0)f.content else 1f
                }) {
                    Canvas(Modifier.fillMaxSize().clearAndSetSemantics {}) {
                        val f=controller.frame()
                        drawIntoCanvas {target->val canvas=target.nativeCanvas;canvas.save();canvas.scale(density.density,density.density);materials.drawCard(canvas,f,controller.cardOrigin(),controller.source,controller.identity);canvas.restore()}
                    }
                    PrismaticGameProfileIdentity(controller,artwork)
                }
                // The interaction target is semantic; the source/selected artwork has one owner.
                Box(Modifier.offset(0.dp,source.railTop.dp).requiredSize(393.dp,source.railSize.dp).zIndex(3f).then(if(!controller.carouselAccessible())Modifier.clearAndSetSemantics {}else Modifier.semantics {
                    onClick(label="Open player card"){controller.open(PrismaticGameProfileController.Surface.Player);true}
                    role=Role.Button;contentDescription="${listOf("Crown","Shield","Diamond")[controller.selected]} badge, ${controller.selected+1} of 3"
                    stateDescription="Pull upward to form your player card"
                    customActions=listOf(CustomAccessibilityAction("Previous badge"){controller.select(controller.selected-1);true},CustomAccessibilityAction("Next badge"){controller.select(controller.selected+1);true},CustomAccessibilityAction("Open player card"){controller.open(PrismaticGameProfileController.Surface.Player);true})
                }))
                if(player){
                    val backWidth=min(345f,208+max(0f,density.fontScale-1)*137)
                    PrismaticButton("Back to profile",controller::close,Modifier.offset(((393-backWidth)/2).dp,704.dp).requiredSize(backWidth.dp,48.dp).zIndex(2000f).graphicsLayer {val f=controller.frame();alpha=f.content;translationY=if(f.reduced)0f else 2*(1-f.content)*density.density}.background(Brush.verticalGradient(listOf(Color(0xFF2DC5EE),Color(0xFF0875D4))),RoundedCornerShape(24.dp)).border(1.dp,Color(0xFF8EE5FD),RoundedCornerShape(24.dp)),reduced,controller.contentReady){
                        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(7.dp)){Icon(Icons.Default.KeyboardArrowDown,null,Modifier.size(22.dp),Color.White);PrismaticText("Back to profile",15f,Color.White,FontWeight.Bold)}
                    }
                }
                if(controller.surface==PrismaticGameProfileController.Surface.Statistics)PrismaticGameProfileStatistics(controller,artwork)
            }
        }
    }
    PrismaticGameProfilePanels(controller,artwork,reduced,systemReduced||previewReducedMotion,localReduced,{localReduced=it})
}

@Composable
private fun PrismaticBase(controller: PrismaticGameProfileController,art: PrismaticGameProfileArtwork,g: PrismaticGameProfileMotion.Source,reduced: Boolean) {
    Image(art.image("landscape"),null,Modifier.requiredSize(393.dp,218.dp),contentScale=ContentScale.Crop,alignment=Alignment.Center)
    Box(Modifier.offset(0.dp,g.whiteTop.dp).requiredSize(393.dp,(852-g.whiteTop).dp).background(Color.White,RoundedCornerShape(topStart=32.dp,topEnd=32.dp)))
    PrismaticButton("Profile settings",{controller.open(PrismaticGameProfileController.Surface.Settings)},Modifier.offset(24.dp,66.dp).size(44.dp).background(Color.White.copy(alpha=.24f),CircleShape).border(1.dp,Color.White.copy(alpha=.36f),CircleShape),reduced){Icon(Icons.Default.Settings,null,Modifier.size(24.dp),Color.White)}
    PrismaticButton("Add a local friend",{controller.open(PrismaticGameProfileController.Surface.Friend)},Modifier.offset(325.dp,66.dp).size(44.dp).background(Color.White.copy(alpha=.24f),CircleShape).border(1.dp,Color.White.copy(alpha=.36f),CircleShape),reduced){Icon(Icons.Default.PersonAdd,null,Modifier.size(24.dp),Color.White)}
    Box(Modifier.offset(24.dp,g.balanceTop.dp).requiredSize(345.dp,g.balanceHeight.dp).background(Color(0xFFF0F7FA),RoundedCornerShape(20.dp))) {
        Row(Modifier.fillMaxSize().padding(horizontal=16.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(16.dp)) {
            listOf(Triple("ruby","72,498",Color(0xFFD72D7A)),Triple("coin","15,380",Color(0xFFA96B00))).forEach {item->
                if(g.stackedBalance)Column(Modifier.weight(1f),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)){Image(art.image(item.first),null,Modifier.size(28.dp));PrismaticText(item.second,19f,item.third,FontWeight.ExtraBold,tracking=-.5f)}
                else Row(Modifier.weight(1f),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.Center){Image(art.image(item.first),null,Modifier.size(32.dp));Spacer(Modifier.width(8.dp));PrismaticText(item.second,19f,item.third,FontWeight.ExtraBold,tracking=-.5f)}
            }
        }
        Box(Modifier.offset(172.5.dp,12.dp).requiredSize(1.dp,(g.balanceHeight-24).dp).background(Color(0xFFD8E7EE)))
    }
    PrismaticText("Collection",20f,Color(0xFF092D3D),FontWeight.ExtraBold,Modifier.offset(24.dp,g.collectionTop.dp).semantics {heading()},tracking=-.55f)
    PrismaticButton("Statistics. Open profile statistics",{controller.open(PrismaticGameProfileController.Surface.Statistics)},Modifier.offset(g.statistics.x.dp,g.statistics.y.dp).requiredSize(g.statistics.width.dp,g.statistics.height.dp).graphicsLayer {alpha=if(controller.statsProgress()>0)0f else 1f}.background(Color(0xFF00BCE9),RoundedCornerShape(16.dp)),reduced) {
        Row(verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(8.dp)){Icon(Icons.Default.DonutLarge,null,Modifier.size(18.dp),Color.White);PrismaticText("Statistics",13f,Color.White,FontWeight.ExtraBold)}
    }
    PrismaticButton("Previous badge",{controller.select(controller.selected-1)},Modifier.offset(56.dp,g.arrowTop.dp).size(44.dp).background(Color(0xFFF0F7FA),CircleShape),reduced,controller.selected>0){Icon(Icons.Default.ChevronLeft,null,Modifier.size(24.dp),Color(0xFF8BA8B7))}
    PrismaticButton("Next badge",{controller.select(controller.selected+1)},Modifier.offset(293.dp,g.arrowTop.dp).size(44.dp).background(Color(0xFFF0F7FA),CircleShape),reduced,controller.selected<2){Icon(Icons.Default.ChevronRight,null,Modifier.size(24.dp),Color(0xFF8BA8B7))}
    Box(Modifier.offset(24.dp,g.divider.dp).requiredSize(345.dp,1.dp).background(Color(0xFFE7F0F4)))
    PrismaticText("Last played",20f,Color(0xFF092D3D),FontWeight.ExtraBold,Modifier.offset(24.dp,g.lastTop.dp).semantics {heading()},tracking=-.55f)
    Row(Modifier.offset(24.dp,g.gameTop.dp).requiredSize(345.dp,g.gameHeight.dp),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)) {
        Image(art.image("game"),null,Modifier.size(64.dp).clip(RoundedCornerShape(18.dp)))
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(4.dp)) {
            PrismaticText("Neon Run",16f,Color(0xFF092D3D),FontWeight.ExtraBold,tracking=-.3f)
            Row(horizontalArrangement=Arrangement.spacedBy(8.dp)) {Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.ThumbUp,null,Modifier.size(12.dp),Color(0xFF7C96A4));PrismaticText("87%",12f,Color(0xFF7C96A4),FontWeight.SemiBold)};Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.PlayArrow,null,Modifier.size(12.dp),Color(0xFF7C96A4));PrismaticText("315.6K",12f,Color(0xFF7C96A4),FontWeight.SemiBold)}}
        }
        PrismaticButton("Play Neon Run local preview",{controller.open(PrismaticGameProfileController.Surface.Game)},Modifier.requiredSize(max(74f,28*g.fontScale+45).dp,max(44f,ceil((20*g.fontScale+24)/4)*4).dp).background(Color(0xFF00BCE9),RoundedCornerShape(16.dp)),reduced){Row(verticalAlignment=Alignment.CenterVertically){Icon(Icons.Default.PlayArrow,null,Modifier.size(17.dp),Color.White);PrismaticText("Play",14f,Color.White,FontWeight.ExtraBold)}}
    }
    Row(Modifier.offset(24.dp,g.tabsTop.dp).requiredSize(345.dp,g.tabsHeight.dp).background(Color.White),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(4.dp)) {
        listOf("Home","Profile","Games","Store").forEachIndexed {i,title->
            PrismaticButton(if(i==1)"Profile, current tab"else "$title, local preview",{when(i){0->controller.open(PrismaticGameProfileController.Surface.Home);1->controller.close();2->controller.open(PrismaticGameProfileController.Surface.Game);else->controller.open(PrismaticGameProfileController.Surface.Store)}},Modifier.requiredSize(83.25.dp,(g.tabsHeight-8).dp),reduced){
                Column(horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(4.dp)) {
                    Box(Modifier.requiredSize(48.dp,32.dp).background(if(i==1)Color(0xFFDCF6FC)else Color.Transparent,RoundedCornerShape(12.dp)),contentAlignment=Alignment.Center){Icon(listOf(Icons.Default.Home,Icons.Default.Person,Icons.Default.SportsEsports,Icons.Default.ShoppingCart)[i],null,Modifier.size(24.dp),if(i==1)Color(0xFF00ABD9)else Color(0xFF8BA1AD))}
                    PrismaticText(title,12f,if(i==1)Color(0xFF082F43)else Color(0xFF8BA1AD),FontWeight.Bold)
                }
            }
        }
    }
    Box(Modifier.offset(24.dp,g.tabsTop.dp).requiredSize(345.dp,1.dp).background(Color(0xFFE7F0F4)))
}

@Composable
internal fun PrismaticButton(label: String,onClick: ()->Unit,modifier: Modifier=Modifier,reduced: Boolean=false,enabled: Boolean=true,content: @Composable ()->Unit) {
    val effectiveEnabled=enabled&&LocalPrismaticBaseEnabled.current
    val interaction=remember {MutableInteractionSource()};val pressed by interaction.collectIsPressedAsState()
    val scale by animateFloatAsState(if(pressed&&effectiveEnabled&&!reduced).97f else 1f,tween(120,easing=CubicBezierEasing(.23f,1f,.32f,1f)),label="Prismatic press")
    Box(modifier.graphicsLayer {scaleX=scale;scaleY=scale;alpha=if(!enabled).35f else if(pressed&&reduced).78f else 1f}.clickable(interactionSource=interaction,indication=null,enabled=effectiveEnabled,role=Role.Button,onClick=onClick).semantics {contentDescription=label},contentAlignment=Alignment.Center){content()}
}

@Composable
internal fun PrismaticText(text: String,size: Float,color: Color=Color(0xFF183E55),weight: FontWeight=FontWeight.Normal,modifier: Modifier=Modifier,tracking: Float=0f) {
    Text(text,modifier,fontSize=size.sp,lineHeight=(size*1.25f).sp,fontWeight=weight,color=color,letterSpacing=tracking.sp,style=TextStyle(platformStyle=PlatformTextStyle(includeFontPadding=false)))
}
