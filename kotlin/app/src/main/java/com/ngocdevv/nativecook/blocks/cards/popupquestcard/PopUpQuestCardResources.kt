package com.ngocdevv.nativecook.blocks.cards.popupquestcard

import android.content.Context
import android.graphics.*
import org.json.JSONArray
import org.json.JSONObject

internal class PopUpQuestCardResources(context: Context) {
    data class Quest(val title: String,val price: String,val bg: String,val tint: String,val cats: List<Pair<String,String>>,val acts: List<Triple<String,String,String>>,val hinges: List<Double>,val art: List<Bitmap>)
    val bold=Typeface.createFromAsset(context.assets,"popupquestcard/popupquestcard_Nunito-ExtraBold.ttf")
    val black=Typeface.createFromAsset(context.assets,"popupquestcard/popupquestcard_Nunito-Black.ttf")
    private val iconFont=Typeface.createFromAsset(context.assets,"popupquestcard/popupquestcard_Ionicons.ttf")
    private val json=JSONObject(context.assets.open("popupquestcard/popupquestcard_art.json").bufferedReader().use { it.readText() })
    val quests: List<Quest>
    private val cache=mutableMapOf<String,Bitmap>()
    init {
        val qs=json.getJSONArray("quests")
        quests=(0 until qs.length()).map { i ->
            val q=qs.getJSONObject(i); val layers=q.getJSONArray("layers")
            val arts=(0 until layers.length()).map { j ->
                bitmap(1200,900) { cv ->
                    cv.translate(100f,100f)
                    val commands=layers.getJSONObject(j).getJSONArray("pic")
                    for(k in 0 until commands.length())drawCommand(cv,commands.getJSONObject(k))
                }
            }
            val cats=q.getJSONArray("cats"); val acts=q.getJSONArray("acts")
            Quest(q.getString("title"),q.getString("price"),q.getString("bg"),q.getString("tint"),
                (0 until cats.length()).map { cats.getJSONArray(it).let { a -> a.getString(0) to a.getString(1) } },
                (0 until acts.length()).map { acts.getJSONObject(it).let { a -> Triple(a.getString("icon"),a.getString("title"),a.getString("sub")) } },
                (0 until layers.length()).map { layers.getJSONObject(it).getDouble("hinge") },arts)
        }
    }
    private fun drawCommand(cv: Canvas, command: JSONObject) {
        val args=command.getJSONArray("args"); val paint=args.getJSONObject(args.length()-1)
        val p=Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color=Color.parseColor(paint.optString("color","#000000")); alpha=(paint.optDouble("alpha",1.0)*255).toInt()
            if(paint.optInt("style",0)==1) { style=Paint.Style.STROKE; strokeWidth=paint.optDouble("width",1.0).toFloat(); strokeCap=Paint.Cap.ROUND }
            if(paint.has("gradient")) {
                val g=paint.getJSONObject("gradient"); val a=g.getJSONObject("a"); val b=g.getJSONObject("b"); val cs=g.getJSONArray("colors")
                shader=LinearGradient(a.getDouble("x").toFloat(),a.getDouble("y").toFloat(),b.getDouble("x").toFloat(),b.getDouble("y").toFloat(),Color.parseColor(cs.getString(0)),Color.parseColor(cs.getString(1)),Shader.TileMode.CLAMP)
            }
        }
        fun n(i: Int)=args.getDouble(i).toFloat()
        fun rect(a: JSONArray)=RectF(a.getDouble(0).toFloat(),a.getDouble(1).toFloat(),(a.getDouble(0)+a.getDouble(2)).toFloat(),(a.getDouble(1)+a.getDouble(3)).toFloat())
        when(command.getString("op")) {
            "drawRect" -> cv.drawRect(rect(args.getJSONArray(0)),p)
            "drawOval" -> cv.drawOval(rect(args.getJSONArray(0)),p)
            "drawRRect" -> args.getJSONArray(0).let { a -> cv.drawRoundRect(rect(a),a.getDouble(4).toFloat(),a.getDouble(5).toFloat(),p) }
            "drawCircle" -> cv.drawCircle(n(0),n(1),n(2),p)
            "drawLine" -> cv.drawLine(n(0),n(1),n(2),n(3),p)
            "drawPath" -> {
                val path=Path(); val ops=args.getJSONArray(0)
                for(i in 0 until ops.length()) { val a=ops.getJSONArray(i); fun d(j: Int)=a.getDouble(j).toFloat()
                    when(a.getString(0)) { "m" -> path.moveTo(d(1),d(2)); "l" -> path.lineTo(d(1),d(2)); "q" -> path.quadTo(d(1),d(2),d(3),d(4)); else -> path.close() }
                }
                cv.drawPath(path,p)
            }
        }
    }
    fun bitmap(w: Int,h: Int,draw: (Canvas)->Unit): Bitmap = Bitmap.createBitmap(w.coerceAtLeast(1),h.coerceAtLeast(1),Bitmap.Config.ARGB_8888).also { draw(Canvas(it)) }
    fun cached(key: String,w: Int,h: Int,draw: (Canvas)->Unit)=cache.getOrPut(key) { bitmap(w,h,draw) }
    fun paint(color: String)=Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color=Color.parseColor(color) }
    fun font(size: Double,isBlack: Boolean=false)=Paint(Paint.ANTI_ALIAS_FLAG).apply { typeface=if(isBlack)black else bold; textSize=size.toFloat(); color=Color.parseColor("#2A2633") }
    fun line(size: Double,isBlack: Boolean=false): Double { val f=font(size,isBlack).fontMetrics; return kotlin.math.ceil((f.descent-f.ascent+f.leading).toDouble()) }
    fun text(cv: Canvas,s: String,x: Double,y: Double,size: Double,isBlack: Boolean=false,color: String="#2A2633",width: Double=0.0,center: Boolean=false) {
        val p=font(size,isBlack); p.color=Color.parseColor(color)
        val dx=if(center)x+(width-p.measureText(s))/2 else x
        cv.drawText(s,dx.toFloat(),(y-p.fontMetrics.ascent).toFloat(),p)
    }
    fun icon(cv: Canvas,name: String,x: Double,y: Double,size: Double,color: String) {
        val p=font(size); p.typeface=iconFont; p.color=Color.parseColor(color)
        cv.drawText(String(Character.toChars(json.getJSONObject("icons").getInt(name))),x.toFloat(),(y-p.fontMetrics.ascent).toFloat(),p)
    }
    fun round(cv: Canvas,x: Double,y: Double,w: Double,h: Double,r: Double,c: String,alpha: Double=1.0) { val p=paint(c); p.alpha=(255*alpha).toInt(); cv.drawRoundRect(RectF(x.toFloat(),y.toFloat(),(x+w).toFloat(),(y+h).toFloat()),r.toFloat(),r.toFloat(),p) }
}
