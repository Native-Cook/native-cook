package com.ngocdevv.nativecook.blocks.onboarding.aurorateammatesonboarding

internal object AuroraTeammatesOnboardingTheme {
    const val ASSETS = "aurorateammatesonboarding/aurorateammatesonboarding_"
    data class Layout(val w: Double, val h: Double) {
        val s = w / 280.0
        val cx = w / 2
        val tops = doubleArrayOf(238.5,305.5,305.5).map { it*s }
        val half = 78*s
        val c1 = 26*s
        val c2 = 50*s
        val depth = 21*s
        val corner = 11.7*s
        val titleSize = 28.85*s
        val titleTop = 54.5*s
        val titleCap = 21*s
        val titleStep = 30*s
        val subSize = 9.06*s
        val subStep = 16*s
        val subBase = doubleArrayOf(166.4,136.4,136.4).map { it*s }
        val dotCentre = doubleArrayOf(223.0,176.5,176.5).map { it*s }
        val buttonBottom = doubleArrayOf(35.0,22.0,22.0).map { it*s }
        val buttonW = 229*s
        val buttonH = 39*s
        val label = 9.75*s
        val trackW = (2*6.75+4*4+9+3*5.8)*s
    }
    data class Rest(val x:Double,val y:Double,val rot:Double,val alpha:Double,val w:Double,val h:Double)
    val rest = arrayOf(
        Rest(100.8,173.3,0.0,1.0,0.0,0.0), Rest(180.9,144.3,0.0,1.0,0.0,0.0),
        Rest(135.5,108.8,2.4,.24,218.75,43.0), Rest(152.1,143.9,-2.6,.82,218.75,43.0),
        Rest(126.5,184.3,3.1,.9,218.75,43.0), Rest(153.0,228.6,-1.3,.95,218.75,43.0), Rest(128.0,274.4,8.4,1.0,218.75,43.0),
        Rest(83.4,128.9,-13.0,.55,116.5,75.0), Rest(203.9,131.4,0.0,.52,116.5,75.0),
        Rest(81.4,196.4,14.5,.78,116.5,75.0), Rest(198.9,193.4,23.0,.82,116.5,75.0),
        Rest(78.0,266.6,-12.0,1.0,116.5,75.0), Rest(201.0,263.1,0.0,1.0,116.5,75.0)
    )
    val tokens = arrayOf("RUN","MEET","CONNECT","YOUR","BUSINESS","WITH","AI TEAMMATES","WORKSPACE")
    val lines = arrayOf(arrayOf(intArrayOf(0,3),intArrayOf(4,5),intArrayOf(6)),arrayOf(intArrayOf(1,3),intArrayOf(6)),arrayOf(intArrayOf(2,3),intArrayOf(7)))
    val subtitles = arrayOf(arrayOf("Projects, clients, meetings, tasks, & growth managed","from one AI-powered workspace."),arrayOf("A specialized AI for every part of your business."),arrayOf("Sync the tools your team already uses."))
    val descriptions = arrayOf("Run your business with AI teammates. Projects, clients, meetings, tasks and growth managed from one AI-powered workspace.","Meet your AI teammates. A specialized AI for every part of your business.","Connect your workspace. Sync the tools your team already uses.")
    val agents = arrayOf("AI Chat Assistant","AI Project Manager","AI Meeting Assistant","AI Content Assistant","AI Finance Manager")
    val agentSubs = arrayOf("Chats, writes, plans","Plans sprints & tracks delivery","Qualifies leads & writes outreach","Drafts proposals & reports","Invoices, expenses & forecasts")
    val icons = arrayOf(
        "M13 4.6A7.6 7.6 0 0 0 5.4 16.1L4.4 19.6l3.6-1A7.6 7.6 0 0 0 19.6 11.4 M9 12h.01 M12.5 12h.01",
        "M14 4.5H6.2A1.7 1.7 0 0 0 4.5 6.2v11.6a1.7 1.7 0 0 0 1.7 1.7h11.6a1.7 1.7 0 0 0 1.7-1.7V10.5 M12 8.4a2.4 2.4 0 1 1 0 4.8a2.4 2.4 0 1 1 0-4.8z M7.9 16.6c.9-1.7 2.3-2.5 4.1-2.5s3.2.8 4.1 2.5",
        "M12.5 7H6.2A1.7 1.7 0 0 0 4.5 8.7v7.6A1.7 1.7 0 0 0 6.2 18h7.6a1.7 1.7 0 0 0 1.7-1.7V11 M15.5 12.2l4-2.4v6.4l-4-2.4",
        "M13.5 4.5H7.2A1.7 1.7 0 0 0 5.5 6.2v11.6a1.7 1.7 0 0 0 1.7 1.7h5.3 M13.5 4.5l5 5v3 M13.5 4.5v3.8a1.2 1.2 0 0 0 1.2 1.2h3.8",
        "M14.5 6H6.2A1.7 1.7 0 0 0 4.5 7.7v8.6A1.7 1.7 0 0 0 6.2 18h6.3 M19.5 10.5v2 M4.9 7.3l7.1 5.2 3.6-2.6"
    )
    val sparkTop = "M18.5 2c.35 1.9 1.6 3.15 3.5 3.5-1.9.35-3.15 1.6-3.5 3.5-.35-1.9-1.6-3.15-3.5-3.5 1.9-.35 3.15-1.6 3.5-3.5z"
    val sparkBottom = "M18.5 15c.35 1.9 1.6 3.15 3.5 3.5-1.9.35-3.15 1.6-3.5 3.5-.35-1.9-1.6-3.15-3.5-3.5 1.9-.35 3.15-1.6 3.5-3.5z"
    val integrations = arrayOf("Daybook","Huddle","Parley","Inkwell","Postbox","Leadflow")
    val rainbow = intArrayOf(0xff9f5aee.toInt(),0xffa966eb.toInt(),0xffca9fbe.toInt(),0xffdfc89a.toInt(),0xfff3f274.toInt(),0xffedf877.toInt(),0xffb8e698.toInt(),0xff88d2ba.toInt(),0xff49b3e5.toInt(),0xff0180f7.toInt())
    val rainbowStops = floatArrayOf(0f,.17f,.27f,.38f,.5f,.57f,.65f,.77f,.85f,1f)
}
