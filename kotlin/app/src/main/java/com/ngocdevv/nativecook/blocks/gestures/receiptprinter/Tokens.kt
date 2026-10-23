package com.ngocdevv.nativecook.blocks.gestures.receiptprinter

/** Exact design and motion values ported from the React Native receipt-printer theme and motion modules. */
object Tokens {
    private fun color(value: Long) = value.toInt()

    object SOURCE {
        const val width = 402f
        const val height = 874f
        const val safeTop = 62f
        const val maxFrameDeltaMs = 250f
    }

    object COLOR {
        val bg = color(0xFFEFEFEF)
        val card = color(0xFFE5E5E5)
        val cardPressed = color(0xFFFFFFFF)
        val ink = color(0xFF1F2226)
        val inkSoft = color(0xFF8A8A8A)
        val date = color(0xFF767676)
        val inkFaint = color(0xFFA8A8A8)
        val icon = color(0xFF3C3C3C)
        val slot = color(0xFF1E1E1E)
        val slotSheen = color(0xFF3A3A3A)
        val slit = color(0xFF8E8E8E)
        val paper = color(0xFFFAF8F2)
        val paperShade = color(0x0D000000)
        val dash = color(0xFFBDBDBD)
        val grid = color(0xFFD9D9D9)
        val gridFaint = color(0xFFF4F4F4)
        val axis = color(0xFF3A3A3A)
        val zone = color(0xFF7EE06E)
        val tag = color(0xFF111111)
        val tagInk = color(0xFFFFFFFF)
        val menu = color(0xFFFFFFFF)
        val shadow = color(0xFF000000)
        val buttonDark = color(0xFF111111)
        val buttonLight = color(0xFFFFFFFF)
        val buttonInk = color(0xFF333333)
        val led = color(0xFF7EE06E)
        val develop = color(0xE6FFFFFF)
        val indicator = color(0x47000000)

    }
    const val CAP_TOP = 0.29f
    object TYPE {
        const val title=27.5f
        const val subtitle=12.5f
        const val pill=16.5f
        const val cardDate=12f
        const val cardKm=16.5f
        const val menu=12.5f
        const val rcTitle=22f
        const val body=13.3f
        const val head=12.5f
        const val total=19f
        const val small=11.2f
        const val tiny=10f
        const val chartNote=8.6f
        const val button=11.5f
    }
    object SPACE {
        const val xs=4f
        const val sm=8f
        const val md=12f
        const val lg=16f
        const val xl=24f
        const val xxl=32f
    }
    object GRID {
        const val gutter=16f
        const val gap=14f
        const val cardW=114f
        const val titleCap=78.5f
        const val subtitleCap=111.5f
        data class Circle(val cx:Float,val cy:Float,val r:Float)
        val info=Circle(372f,88f,12f)
        const val pillTop=144.7f
        const val pillH=40.6f
        const val rowTop=203f
        const val rowPitch=158.3f
        const val cardH=140.7f
        const val radius=12f
        const val dateInset=12f
        const val dateCap=14.5f
        const val iconTop=42f
        const val iconW=78f
        const val iconH=44f
        const val kmCapFromBottom=29f
        data class Menu(
            val x: Float,
            val y: Float,
            val w: Float,
            val h: Float,
            val radius: Float,
            val rowH: Float,
            val iconX: Float,
            val textX: Float,
        )

        val menu = Menu(16f, 203f, 254f, 93f, 18f, 38f, 22f, 52f)
        const val menuPaddingVertical = 8f
        const val menuIconSize = 15f
        const val menuShadowOpacity = 0.14f
        const val menuShadowRadius = 22f
        const val menuShadowOffsetY = 10f
        const val menuPressedOpacity = 0.06f
        const val menuRuleOpacity = 0.08f
        const val menuRuleHeight = 0.5f
        const val menuStartScale = 0.95f

    }
    object SLOT {
        const val x0=16f
        const val x1=386f
        const val y0=70.5f
        const val y1=111f
        const val ring=14.5f
        const val slitY0=85.5f
        const val slitY1=96.5f
        const val paperX0=35f
        const val paperX1=368f
        const val paperTop=86f
        const val shadowH=16f
        const val outerRadius = 40f
        const val slitRadius = 8f
        const val shadeOpacity = 0.22f
        data class Led(val x:Float,val y:Float,val r:Float,val glow:Float)
        val led=Led(362f,78f,2.6f,9f)

    }
    object ISLAND {
        const val top = 14f
        const val w = 94f
        const val h = 36f
        const val noIslandGap = 6f
        const val openW = SLOT.paperX1 - SLOT.paperX0 + 28f
        const val openH = 46f
        const val tuck = 4f
        const val ledInset = 22f
        val color = color(0xFF000000)
    }
    data class ReceiptRow(val cap:Float,val text:String,val align:String,val x:Float?=null,val size:Float,val bold:Boolean=false,val color:Int?=null)
    data class Chart(val top:Float,val bottom:Float,val base:Float,val baseW:Float,val columns:Int,val dotted:Int)
    data class Fast(val x:Float,val top:Float,val w:Float,val h:Float,val radius:Float,val size:Float)
    data class Zone(val x:Float,val ys:List<Float>,val r:Float)
    data class Edge(val period:Float,val depth:Float)
    data class Logo(val top:Float,val w:Float,val h:Float)
    object RECEIPT {
        const val length=1281f
        const val left=60f
        const val right=340f
        const val center=201.5f
        val logo=Logo(153f,73f,44f)
        val dashes=listOf(363f,410f,505f,751f,947f,1181f)
        val rows=listOf(
        ReceiptRow(222.3f,"RUN RECEIPT","center",size=TYPE.rcTitle,bold=true),
        ReceiptRow(272f,"RUN #0418","center",size=TYPE.body),
        ReceiptRow(296.3f,"WED, JANUARY 28, 2026","center",size=TYPE.body),
        ReceiptRow(321.3f,"RIVERSIDE LOOP – TEMPO","center",size=TYPE.body),
        ReceiptRow(381.7f,"ITEM","left",60f,TYPE.head,true),
        ReceiptRow(381.7f,"PACE","left",172f,TYPE.head,true),
        ReceiptRow(381.7f,"ZONE","left",217f,TYPE.head,true),
        ReceiptRow(381.7f,"TIME","left",262f,TYPE.head,true),
        ReceiptRow(381.7f,"HR","right",340f,TYPE.head,true),
        ReceiptRow(435.3f,"5 KM","left",60f,TYPE.body),
        ReceiptRow(435.3f,"05'28\"","left",163f,TYPE.body),
        ReceiptRow(435.3f,"27:21","left",258f,TYPE.body),
        ReceiptRow(435.3f,"154","right",340f,TYPE.body),
        ReceiptRow(470.5f,"1.21 KM","left",60f,TYPE.body),
        ReceiptRow(470.5f,"05'11\"","left",163f,TYPE.body),
        ReceiptRow(470.5f,"06:17","left",258f,TYPE.body),
        ReceiptRow(470.5f,"168","right",340f,TYPE.body),
        ReceiptRow(558.7f,"PACE","left",60f,TYPE.head,true),
        ReceiptRow(561f,"05'04\"–05'42\"/KM | AVG 05'25\"","right",340f,TYPE.chartNote,color=COLOR.inkSoft),
        ReceiptRow(715f,"0KM","left",60f,TYPE.tiny,color=COLOR.inkSoft),
        ReceiptRow(715f,"6.2KM","right",340f,TYPE.tiny,color=COLOR.inkSoft),
        ReceiptRow(789f,"TOTAL:","left",60f,TYPE.total,true),
        ReceiptRow(789f,"6.21 KM","right",340f,TYPE.total,true),
        ReceiptRow(827f,"DURATION","left",60f,TYPE.body),
        ReceiptRow(827f,"00:33:38","right",340f,TYPE.body),
        ReceiptRow(851.7f,"AVG PACE","left",60f,TYPE.body),
        ReceiptRow(851.7f,"05'25\"/KM","right",340f,TYPE.body),
        ReceiptRow(876.3f,"BEST 1K","left",60f,TYPE.body),
        ReceiptRow(876.3f,"05'09\"/KM","right",340f,TYPE.body),
        ReceiptRow(901.3f,"AVG HR","left",60f,TYPE.body),
        ReceiptRow(901.3f,"156 BPM","right",340f,TYPE.body),
        ReceiptRow(983f,"*** DUPLICATE COPY ***","center",size=TYPE.body,bold=true),
        ReceiptRow(1018.3f,"KEEP FOR YOUR RECORDS","center",size=TYPE.small,color=COLOR.inkSoft),
        ReceiptRow(1210.3f,"PRINTED","center",size=TYPE.small,bold=true),
        ReceiptRow(1223.7f,"WED 28 JAN 18:51","center",size=TYPE.small,bold=true),
        ReceiptRow(1261.7f,"EVEN PACE, STRONG FINISH.","center",size=TYPE.small,bold=true),
        ReceiptRow(1294.7f,"GENERATED LOCALLY VIA STRIDE OS.","center",size=TYPE.tiny,color=COLOR.inkFaint)
        )
        const val paragraphCap=1053f
        const val paragraphPitch=15.6f
        val paragraph=listOf("Every run leaves a trace. Run Receipt","turns it into something you can keep and","share, capturing distance, pace, and","effort as a simple receipt of movement –","a small record of where you ran and how","the moment felt. Learn more at","receipt.strideos.run.")
        val fast=Fast(120f,466.5f,34f,17.7f,3.5f,9.5f)
        val zone=Zone(231.3f,listOf(440.2f,475.3f),4.2f)
        val chart=Chart(585f,705f,689.6f,2.4f,6,4)
        val edge=Edge(12.8f,6f)

    }
    object BAR {
        const val top=792f
        const val h=56f
        const val x=33.7f
        const val w=106.3f
        const val gap=13f
        const val icon=18f
        const val iconCap=10f
        const val labelCap=37f
        const val shadowOpacity = 0.08f
        const val shadowRadius = 10f
        const val shadowOffsetY = 3f
    }
    data class RunCard(val date:List<String>,val km:String,val seed:Int)
    val CARDS=listOf(
        RunCard(listOf("WED","28 JAN"),"6.21 KM",3),
        RunCard(listOf("SUN","25 JAN"),"12.40 KM",11),
        RunCard(listOf("THU","22 JAN"),"5.02 KM",5),
        RunCard(listOf("TUE","20 JAN"),"8.15 KM",8),
        RunCard(listOf("SAT","17 JAN"),"10.33 KM",2),
        RunCard(listOf("WED","14 JAN"),"6.18 KM",3),
        RunCard(listOf("MON","12 JAN"),"4.40 KM",14),
        RunCard(listOf("SAT","10 JAN"),"9.87 KM",6),
        RunCard(listOf("THU","8 JAN"),"5.55 KM",9),
        RunCard(listOf("TUE","6 JAN"),"7.20 KM",12),
        RunCard(listOf("SUN","4 JAN"),"11.06 KM",4),
        RunCard(listOf("FRI","2 JAN"),"5.00 KM",7),
        RunCard(listOf("WED","31 DEC"),"6.42 KM",10),
        RunCard(listOf("MON","29 DEC"),"8.90 KM",1),
        RunCard(listOf("SAT","27 DEC"),"3.25 KM",13),
    )
    val PILLS=listOf("91.4 KM","12 RUNS","5'29\"/KM")
    object COPY {
        const val title="RUN RECEIPT"
        const val subtitle="2026 so far · tap a run to print it"
        const val long="LONG RECEIPT"
        const val short="SHORT RECEIPT"
        const val save="SAVE"
        const val tear="TEAR OFF"
        const val sign="SIGNATURE"
    }
    object PRESS {
        const val longPressMs=380
        data class Spring(val damping:Float,val stiffness:Float,val mass:Float)
        val menuIn=Spring(20f,320f,1f)
        const val menuOutMs=140f
        const val cardLift=1.03f
    }
    object SCREEN {
        const val gridOutMs=260f
        const val slotInMs=220f
    }
    val ISLAND_SPRING = PRESS.Spring(damping = 17f, stiffness = 230f, mass = 1f)
    object FEED {
        const val ms=2300f
        const val steps=14
        const val push=0.55f
        const val swing=0.35f
        const val swingDamping=7f
        const val swingFreq=9f
    }
    object SCROLL {
        const val rubber=0.55f
        const val deceleration=0.997f
        const val dismissBy=90f
        const val dismissVelocity=1000f
        const val barWithin=40f
        const val barMs=220f
    }
    object RETRACT {
        const val ms=520f
        const val gridLeadMs=130f
        const val gridInMs=680f
        const val reducedMs=200f
    }
    const val LOOP_MS=10000f
    object AUTOPLAY {
        const val resumeDelayMs=2000f
        const val pressAt=500f
        const val pickAt=1300f
        data class Scroll(val start:Float,val end:Float,val by:Float)
        val scrolls=listOf(Scroll(4100f,5300f,700f),Scroll(5500f,6000f,-200f),Scroll(6100f,6600f,220f))
        const val tearAt=7600f
    }
    object UPGRADE {
        const val morphMs=420f
        const val developH=72f
        const val developOpacity=0.7f
        const val developFadeMs=260f
        const val cutDrop=5f
        const val cutDownMs=90f
        val cutSpring = PRESS.Spring(11f, 420f, 0.6f)
        const val ledBlinkHz=6f
        const val ledIdle=0.35f
        val liftShadow=listOf(0.16f,0.3f)
        const val liftMs=160f
        const val swayDeg=0.12f
        const val swayMs=5000f
        const val tug=10f
        const val tugMs=120f

    }
    const val QA_SLOWMO=1f
    const val DEBUG_FREEZE_MS=-1f
    object TOUCH {
        const val retention = 16f
        const val movementSlop = 8f
        const val paperSemanticHeight = 650f
        const val accessibilityScroll = 160f
        const val dismissFlingOffset = 30f
        const val rubberSoftening = 400f
    }
    object INDICATOR {
        const val right = 3f
        const val width = 3f
        const val radius = 1.5f
        const val showAfterScroll = 2f
        const val fadeMs = 150f
        const val minimumHeight = 36f
        const val contentExtra = 120f
    }
    object PAPER {
        const val rasterScale = 3f
        const val grainFrequency = 0.9f
        const val shadowOpacity = 0.16f
        const val liftedShadowOpacity = 0.30f
        const val shadowRadius = 14f
        const val shadowOffsetY = 4f
        const val maxScrollFootGap = 48f
    }
    object PACE {
        const val avg = 325f
        const val fast = 280f
        const val slow = 360f
        const val km = 6.21f
        const val samples = 63
    }
    object POLISH {
        val easeOut=listOf(0.23f,1f,0.32f,1f)
        val easeInOut=listOf(0.77f,0f,0.175f,1f)
        const val holdScale=0.97f
        data class ReleaseSpring(val duration:Float,val dampingRatio:Float)
        val releaseSpring=ReleaseSpring(300f,0.8f)
        const val pressScale=0.97f
        const val pressInMs=100f
        const val pressOutMs=160f
        const val armedLed=1f
    }
}
