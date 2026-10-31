package com.ngocdevv.nativecook.blocks.inputs.clayprompt

import androidx.compose.ui.graphics.Color

internal object ClayPromptTheme {
    val page=Color(0xFF121018);val surface=Color(0xFF1E1B26);val line=Color(0xFF2E2A3A)
    val ink=Color(0xFFF6F3FA);val mute=Color(0xFF9792A6);val faint=Color(0xFF5E596D)
    val accent=Color(0xFFFFB86B);val accentInk=Color(0xFF1E1206)
    val tints=listOf(Color(0xFFFFA15A),Color(0xFF6FA4FF),Color(0xFFFFD86A),Color(0xFF73D883))
    const val PROMPT="a cat on a sofa under the moon"
    private val words=listOf(listOf("cat","cats","kitten","kitty"),listOf("sofa","couch","sofas"),listOf("moon","crescent"),listOf("plant","plants","cactus","fern"))
    fun kind(word:String)=words.indexOfFirst{word.lowercase().replace(Regex("[^a-z]"),"") in it}
    fun kinds(text:String)=text.split(Regex("\\s+")).map(::kind).filter{it>=0}.distinct().take(4)
    data class Layout(val w:Double,val h:Double,val bar:Double,val x:Double,val y:Double,val cw:Double,val ch:Double,val caption:Double,val ty:Double,val composer:Double){val cx get()=x+cw/2;val cy get()=y+ch*0.52}
    fun layout(w:Double,h:Double,top:Double,bottom:Double):Layout{
        val bar=top+8;val composer=h-bottom-132;val ty=composer-56;val y=bar+56;val cw=w-32;val ch=minOf(cw*1.18,ty-60-y)
        return Layout(w,h,bar,16.0,y,cw,ch,y+ch+16,ty,composer)
    }
}
