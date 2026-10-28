package com.ngocdevv.nativecook.blocks.cards.prismaticgameprofile

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.TextButton
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.heading
import androidx.compose.ui.semantics.paneTitle
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Density
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import kotlin.math.min

@Composable
internal fun PrismaticGameProfileStatistics(controller: PrismaticGameProfileController,artwork: PrismaticGameProfileArtwork) {
    Canvas(Modifier.fillMaxSize().zIndex(3000f)) {
        val p=controller.statsProgress();if(p<=0)return@Canvas
        val s=controller.source.statistics
        val x=s.x+(70-s.x)*p;val y=s.y+(356-s.y)*p;val w=s.width+(253-s.width)*p;val h=s.height+(178-s.height)*p;val r=16+8*p
        drawRoundRect(Color(0xFF183F59).copy(alpha=.2f),Offset(x.dp.toPx(),(y+7).dp.toPx()),Size(w.dp.toPx(),h.dp.toPx()),CornerRadius(r.dp.toPx()))
        drawRoundRect(lerp(Color(0xFF00BCE9),Color(0xFFF4FBFF),p),Offset(x.dp.toPx(),y.dp.toPx()),Size(w.dp.toPx(),h.dp.toPx()),CornerRadius(r.dp.toPx()))
    }
    Column(Modifier.offset(70.dp,356.dp).requiredSize(253.dp,178.dp).zIndex(3001f).graphicsLayer {alpha=controller.frame().content}.padding(horizontal=12.dp).semantics {paneTitle="Your skills"}) {
        Row(Modifier.fillMaxWidth().height(43.dp),verticalAlignment=Alignment.CenterVertically) {
            PrismaticText("Your skills",19f,Color(0xFF123B55),FontWeight.ExtraBold,Modifier.weight(1f).padding(start=4.dp).semantics {heading()})
            PrismaticButton("Close profile details",controller::close,Modifier.size(44.dp),controller.reduced){Icon(Icons.Default.Close,null,Modifier.size(21.dp),Color(0xFF1A4B71))}
        }
        Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(7.dp)) {
            listOf(Triple("Level","75","stats_level"),Triple("Games","28","stats_games"),Triple("Time","9h23m","stats_time")).forEachIndexed {index,item->
                Column(Modifier.weight(1f).background(Color.White,RoundedCornerShape(16.dp)).padding(vertical=7.dp),horizontalAlignment=Alignment.CenterHorizontally,verticalArrangement=Arrangement.spacedBy(3.dp)) {
                    Image(artwork.image(item.third),null,Modifier.size(44.dp))
                    val density=LocalDensity.current
                    CompositionLocalProvider(LocalDensity provides Density(density.density,min(density.fontScale,1.3f))){PrismaticText(item.first,13f,Color(0xFF536B7D))}
                    CompositionLocalProvider(LocalDensity provides Density(density.density,min(density.fontScale,1.2f))){PrismaticText(item.second,18f,listOf(Color(0xFF0864B8),Color(0xFF9622CF),Color(0xFF587B00))[index],FontWeight.ExtraBold)}
                }
            }
        }
        PrismaticText("Profile statistics · Read-only demo",10f,Color(0xFF536B7D),modifier=Modifier.align(Alignment.CenterHorizontally).padding(top=5.dp))
    }
}

/** Local product previews. No purchases, requests or external game launch. */
@Composable
internal fun PrismaticGameProfilePanels(controller: PrismaticGameProfileController,artwork: PrismaticGameProfileArtwork?,reduced: Boolean,systemReduced: Boolean,localReduced: Boolean,onReduced: (Boolean)->Unit) {
    val surface=controller.surface
    var friendName by remember {mutableStateOf("")};var friends by remember {mutableStateOf(emptyList<String>())};var saved by remember {mutableStateOf(false)}
    var about by remember {mutableStateOf(false)}
    if(surface==PrismaticGameProfileController.Surface.None||surface==PrismaticGameProfileController.Surface.Player||surface==PrismaticGameProfileController.Surface.Statistics)return
    val title=when(surface){PrismaticGameProfileController.Surface.Settings->"Profile settings";PrismaticGameProfileController.Surface.Friend->"Local friends";PrismaticGameProfileController.Surface.Game->"Neon Run";PrismaticGameProfileController.Surface.Home->"Home preview";else->"Badge previews"}
    AlertDialog(onDismissRequest=controller::close,containerColor=Color(0xFFF4FBFF),title={PrismaticText(title,20f,Color(0xFF102E45),FontWeight.ExtraBold)},text={
        Column(Modifier.fillMaxWidth().heightIn(max=500.dp).verticalScroll(rememberScrollState()),verticalArrangement=Arrangement.spacedBy(14.dp)) {
            when(surface){
                PrismaticGameProfileController.Surface.Settings->{
                    Row(verticalAlignment=Alignment.CenterVertically){PrismaticText("Reduce motion",16f,modifier=Modifier.weight(1f));Switch(checked=reduced,onCheckedChange=onReduced,enabled=!systemReduced)}
                    PrismaticText("This local preference also respects your system’s Reduce Motion setting.",13f,Color(0xFF536B7D))
                }
                PrismaticGameProfileController.Surface.Friend->{
                    OutlinedTextField(friendName,{friendName=it},Modifier.fillMaxWidth(),label={PrismaticText("Username",14f)},singleLine=true)
                    TextButton(enabled=friendName.trim().isNotEmpty(),onClick={friends=(friends+friendName.trim()).distinct();friendName=""}){PrismaticText("Add locally",15f,Color(0xFF006BC8),FontWeight.SemiBold)}
                    friends.forEach {PrismaticText("$it · Saved locally",16f)}
                    PrismaticText("No friend request is sent from this demo.",13f,Color(0xFF536B7D))
                }
                PrismaticGameProfileController.Surface.Game->{
                    if(artwork!=null)Image(artwork.image("game"),null,Modifier.fillMaxWidth().height(160.dp))
                    PrismaticText("87% likes · 315.6K plays",16f)
                    PrismaticText("Local game preview. No external game is launched.",13f,Color(0xFF536B7D))
                    TextButton(onClick={saved=!saved}){PrismaticText(if(saved)"Saved locally"else "Save locally",15f,Color(0xFF006BC8),FontWeight.SemiBold)}
                }
                PrismaticGameProfileController.Surface.Store->{
                    PrismaticText("Preview an object without spending rubies or coins.",13f,Color(0xFF536B7D))
                    listOf("Crown","Shield","Diamond").forEachIndexed {index,name->
                        TextButton(onClick={controller.close();controller.select(index)},modifier=Modifier.fillMaxWidth().heightIn(min=60.dp)){
                            Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically,horizontalArrangement=Arrangement.spacedBy(12.dp)){
                                if(artwork!=null)Image(artwork.image(name.lowercase()),null,Modifier.size(44.dp));PrismaticText(name,16f,modifier=Modifier.weight(1f));PrismaticText("Preview",14f,Color(0xFF0063B8),FontWeight.Bold)
                            }
                        }
                    }
                }
                PrismaticGameProfileController.Surface.Home->{
                    PrismaticText("Everen’s local profile",16f)
                    PrismaticText("Skills 1.500 · Ruby balance 72,498 · Coin balance 15,380",13f,Color(0xFF536B7D))
                    TextButton(onClick=controller::close){PrismaticText("View profile",15f,Color(0xFF006BC8),FontWeight.SemiBold)}
                    TextButton(onClick={about=true}){PrismaticText("About this preview",15f,Color(0xFF006BC8),FontWeight.SemiBold)}
                }
                else->Unit
            }
        }
    },confirmButton={TextButton(onClick=controller::close){PrismaticText("Done",15f,Color(0xFF006BC8),FontWeight.Bold)}})
    if(about)AlertDialog(onDismissRequest={about=false},title={PrismaticText("Local profile",20f,weight=FontWeight.Bold)},text={PrismaticText("All previews stay on this device. No purchases or external game launch occur.",15f)},confirmButton={TextButton(onClick={about=false}){PrismaticText("Done",15f)}})
}
