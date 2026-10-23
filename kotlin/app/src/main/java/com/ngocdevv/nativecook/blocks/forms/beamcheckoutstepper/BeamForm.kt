package com.ngocdevv.nativecook.blocks.forms.beamcheckoutstepper

import java.time.LocalDate

internal data class BeamError(val field: Int, val message: String)
internal data class BeamForm(
    val values: List<String> = List(7) { "" },
    val step: Int = 0,
    val furthest: Int = 0,
    val express: Boolean = false,
    val error: BeamError? = null,
    val paid: Boolean = false,
    val newOrder: Boolean = false,
    val restarting: Boolean = false
)
internal data class BeamAction(val type: String, val field: Int = 0,
    val value: String = "", val target: Int = 0, val express: Boolean = false)
internal data class BeamCommand(val type: String, val a: Int = 0, val b: Int = 0)
internal data class BeamChange(val form: BeamForm, val commands: List<BeamCommand>)

internal fun beamFormat(field: Int, value: String): String {
    if (field == 0) return value.trim()
    if (field < 3) return value
    val digits = value.filter { it.isDigit() }
    return when(field) {
        3 -> digits.take(5)
        4 -> digits.take(16).chunked(4).joinToString(" ")
        5 -> digits.take(4).let { if(it.length >= 2) it.take(2)+"/"+it.drop(2) else it }
        else -> digits.take(4)
    }
}
internal fun beamLuhn(value: String): Boolean {
    val digits = value.filter { it.isDigit() }
    if (digits.length != 16 || digits.all { it == '0' }) return false
    var sum = 0
    digits.forEachIndexed { i, c ->
        var d = c.digitToInt()
        if (i % 2 == 0) { d *= 2
        if (d > 9) d -= 9 }
        sum += d
    }
    return sum % 10 == 0
}
internal fun beamValidate(step: Int, v: List<String>, now: LocalDate = LocalDate.now()): BeamError? {
    if (step == 0 && !Regex("^[^\\s@]+@[^\\s@]+\\.[^\\s@]+$").matches(v[0].trim()))
        return BeamError(0,"Enter a valid email")
    if (step == 1) {
        if (v[1].trim().length < 4) return BeamError(1,"Enter your street address")
        if (v[2].trim().length < 2) return BeamError(2,"Enter your city")
        if (!Regex("^\\d{5}$").matches(v[3])) return BeamError(3,"Enter a 5-digit ZIP")
    }
    if (step == 2) {
        if (!beamLuhn(v[4])) return BeamError(4,"Check the card number")
        val date = Regex("^(\\d{2})/(\\d{2})$").matchEntire(v[5])
        val month = date?.groupValues?.get(1)?.toInt() ?: 0
        val year = 2000 + (date?.groupValues?.get(2)?.toInt() ?: 0)
        if (month !in 1..12 || year*12+month < now.year*12+now.monthValue)
            return BeamError(5,"Check the expiry date")
        if (!Regex("^\\d{3,4}$").matches(v[6])) return BeamError(6,"Enter the 3-digit code")
    }
    return null
}
internal fun beamTotal(express:Boolean) = if(express) 184 else 172
internal fun beamTotalLabel(express:Boolean) = "$"+beamTotal(express)+".00"
internal fun beamSummaries(f:BeamForm) = listOf(
    f.values[0],
    "${f.values[2]} · ${if(f.express)"Express" else "Standard"}",
    "•••• ${f.values[4].filter { it.isDigit() }.takeLast(4)}",
    beamTotalLabel(f.express),
    "#NC-20481"
)
internal fun beamReview(f:BeamForm) = listOf(
    "Contact" to f.values[0],
    "Ship to" to "${f.values[1]}, ${f.values[2]}",
    "Delivery" to if(f.express)"Express · 2–3 days" else "Standard · 5–7 days",
    "Card" to beamSummaries(f)[2]
)
internal fun beamCta(f:BeamForm):String = when {
    f.step < 3 || f.restarting -> "Continue"
    f.step == 3 -> "${if(f.paid)"Paid" else "Pay"} ${beamTotalLabel(f.express)}"
    f.newOrder -> "New order"
    else -> "Paid ${beamTotalLabel(f.express)}"
}
internal fun beamAction(f: BeamForm, a: BeamAction, now:LocalDate = LocalDate.now()):BeamChange {
    var next=f
    val commands=mutableListOf<BeamCommand>()
    when(a.type){
        "edit" -> {
            val v=f.values.toMutableList()
            v[a.field]=beamFormat(a.field,a.value)
            next=f.copy(values=v,error=if(f.error?.field==a.field)null else f.error)
            commands+=BeamCommand("filled",a.field,if(v[a.field].isNotEmpty())1 else 0)
            if(f.error?.field==a.field)commands+=BeamCommand("clearError")
        }
        "focus" -> commands+=BeamCommand("focus",a.field)
        "delivery" -> {
            next=f.copy(express=a.express)
            commands+=BeamCommand("delivery",if(a.express)1 else 0)
        }
        "continue" -> {
            if(f.step==3&&f.paid)return BeamChange(f,commands)
            if(f.step==4){
                if(f.restarting)return BeamChange(f,commands)
                next=f.copy(restarting=true)
                commands+=BeamCommand("restart")
            } else {
                val err=beamValidate(f.step,f.values,now)
                next=f.copy(error=err)
                commands+=when {
                    err!=null -> BeamCommand("reject",err.field)
                    f.step==3 -> BeamCommand("pay")
                    f.step<f.furthest -> BeamCommand("go",f.furthest)
                    else -> BeamCommand("advance")
                }
            }
        }
        "go" -> if(!f.paid&&a.target<=f.furthest)commands+=BeamCommand("go",a.target)
        "settled" -> next=f.copy(step=a.target,furthest=maxOf(f.furthest,a.target),
            error=null,paid=a.target==4)
        "cleared","restarted" -> next=BeamForm()
        "newOrder" -> next=f.copy(newOrder=true)
        "paid" -> next=f.copy(paid=true)
    }
    return BeamChange(next,commands)
}

internal data class BeamDemoEvent(val at: Double, val action: BeamAction? = null,
    val pressIndex: Int = -1, val down: Boolean = false)
internal object BeamDemo {
    const val PERIOD = 22.0
    val events: List<BeamDemoEvent> = buildList {
        fun add(at:Double,type:String,field:Int=0,value:String="",express:Boolean=false) {
            add(BeamDemoEvent(at,BeamAction(type,field,value,express=express)))
        }
        fun type(at:Double,field:Int,value:String) {
            value.indices.forEach { add(at+it*.07,"edit",field,value.take(it+1)) }
        }
        fun tap(at:Double,type:String) {
            val p=if(type=="continue")BF.CTA_PRESS else BF.SEG_PRESS
            add(BeamDemoEvent(at,pressIndex=p,down=true))
            val end=at+if(type=="continue").12 else .1
            add(BeamDemoEvent(end,pressIndex=p,down=false))
            add(end,type,express=type=="delivery")
        }
        add(.5,"focus",0)
        type(.7,0,"mira@northlane.co")
        tap(2.2,"continue")
        add(3.4,"focus",1)
        type(3.5,1,"18 Alder Lane")
        add(4.55,"focus",2)
        type(4.65,2,"Portland")
        add(5.35,"focus",3)
        type(5.45,3,"97209")
        add(6.0,"focus",-1)
        tap(6.3,"continue")
        add(7.5,"focus",4)
        type(7.6,4,"4242424242424241")
        add(8.85,"focus",5)
        type(8.95,5,"0832")
        add(9.35,"focus",6)
        type(9.45,6,"314")
        add(9.8,"focus",-1)
        tap(10.0,"continue")
        add(11.3,"edit",4,"424242424242424")
        add(11.5,"edit",4,"4242424242424242")
        add(11.75,"focus",-1)
        tap(11.9,"continue")
        tap(15.1,"delivery")
        tap(15.7,"continue")
        tap(17.3,"continue")
        tap(20.6,"continue")
    }.sortedBy { it.at }
    fun bezier(t:Double):Double {
        if(t<=0)return 0.0
        if(t>=1)return 1.0
        var lo=0.0
        var hi=1.0
        var u=t
        repeat(18) {
            val q=1-u
            val x=3*q*q*u*.23+3*q*u*u*.32+u*u*u
            if(x<t)lo=u else hi=u
            u=(lo+hi)/2
        }
        val q=1-u
        return 3*q*q*u+3*q*u*u+u*u*u
    }
    fun finger(t:Double):Triple<Boolean,Float,Float> {
        val local=(t-13.6).coerceIn(0.0,.7)
        if(local<.6){
            val u=local/.6
            val value=-1.975*bezier(u)
            val before=-1.975*bezier(maxOf(0.0,u-.0001))
            return Triple(t>=13.6&&t<14.3,value.toFloat(),((value-before)/.00006).toFloat())
        }
        return Triple(t>=13.6&&t<14.3,(-1.975-.25*(local-.6)).toFloat(),-.25f)
    }
}
