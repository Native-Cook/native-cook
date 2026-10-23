package com.ngocdevv.nativecook.blocks.charts.metricdropdownlinescrub

import java.time.LocalDate
import java.util.Locale
import kotlin.math.*

data class Metric(val label: String,val caption: String,val unit: String,val min: Double,val max: Double,val decimals: Int,val values: List<Double>)
val Metrics = listOf(
    Metric("1 Rep Max","Theoretical max for 1 rep","kg",40.0,75.0,1,listOf(47.5, 44.8, 47.1, 46.2, 46.8, 46.4, 48.9, 49.8, 50.8, 52.6, 52.8, 52.9, 51.4, 53.9, 50.6, 53.1, 50.9, 51.2, 55.5, 54.1, 57.4, 56.2, 58.0, 59.9, 57.5, 58.4, 58.3, 58.1, 56.9, 57.9, 60.0, 62.6, 61.7, 63.8, 65.6, 63.0, 66.4, 62.6, 63.4, 65.9)),
    Metric("Heaviest weight","Heaviest weight used","kg",35.0,60.0,1,listOf(36.7, 37.3, 37.9, 38.4, 38.8, 39.5, 39.9, 40.4, 40.8, 41.2, 41.3, 42.0, 42.2, 42.8, 43.0, 43.5, 44.3, 44.6, 45.3, 45.9, 46.0, 46.6, 46.7, 47.2, 47.6, 48.0, 48.4, 49.3, 49.6, 50.1, 50.6, 51.2, 51.4, 51.8, 52.1, 52.6, 52.9, 53.3, 54.0, 54.6)),
    Metric("Total weight","Total weight lifted","t",1.0,5.0,2,listOf(2.15, 2.17, 2.5, 2.44, 2.23, 2.21, 2.42, 2.19, 2.24, 2.38, 2.41, 2.49, 2.96, 3.04, 2.7, 3.02, 3.06, 2.69, 2.9, 2.87, 3.04, 3.03, 3.22, 3.36, 3.26, 3.66, 3.69, 3.57, 3.35, 3.31, 3.31, 3.54, 3.34, 3.67, 3.69, 3.74, 3.76, 3.78, 4.25, 4.04)),
    Metric("Reps","Most reps in one set","reps",0.0,16.0,0,listOf(6.0, 6.0, 5.0, 6.0, 5.0, 6.0, 7.0, 5.0, 8.0, 9.0, 8.0, 9.0, 9.0, 7.0, 8.0, 8.0, 8.0, 8.0, 8.0, 8.0, 9.0, 9.0, 9.0, 9.0, 8.0, 8.0, 8.0, 9.0, 8.0, 9.0, 10.0, 10.0, 12.0, 12.0, 11.0, 11.0, 11.0, 11.0, 9.0, 11.0)),
)
data class Range(val label: String,val days: Int?)
val Ranges = listOf(Range("1 month",30), Range("3 months",90), Range("1 year",365), Range("All time",null))
val Sessions = listOf("2025-09-18T11:33", "2025-09-28T07:10", "2025-10-08T18:45", "2025-10-18T12:05", "2025-10-28T19:20", "2025-11-07T06:55", "2025-11-17T17:30", "2025-11-27T11:33", "2025-12-07T07:10", "2025-12-17T18:45", "2025-12-27T12:05", "2026-01-06T19:20", "2026-01-16T06:55", "2026-01-26T17:30", "2026-02-05T11:33", "2026-02-15T07:10", "2026-02-25T18:45", "2026-03-07T12:05", "2026-03-17T19:20", "2026-03-27T06:55", "2026-04-06T17:30", "2026-04-16T11:33", "2026-04-26T07:10", "2026-05-06T18:45", "2026-05-16T12:05", "2026-05-26T19:20", "2026-06-05T06:55", "2026-06-15T17:30", "2026-06-25T11:33", "2026-07-05T07:10", "2026-07-15T18:45", "2026-07-25T12:05", "2026-08-04T19:20", "2026-08-14T06:55", "2026-08-24T17:30", "2026-09-03T11:33", "2026-09-13T07:10", "2026-09-23T18:45", "2026-10-03T12:05", "2026-10-13T19:20")
internal object Data {
    const val day=86400000L;const val sessionCount=40;const val sessionGapDays=10;val sessionTimes=listOf("11:33","07:10","18:45","12:05","19:20","06:55","17:30")
    val months=listOf("January","February","March","April","May","June","July","August","September","October","November","December");val weekdays=listOf("Sunday","Monday","Tuesday","Wednesday","Thursday","Friday","Saturday")
    fun date(session: String)=LocalDate.parse(session.take(10))
    fun rangeStart(sessions: List<String>,range: Range): Int { if(range.days==null||sessions.isEmpty())return 0;val from=date(sessions.last()).minusDays(range.days.toLong());return sessions.indexOfFirst{date(it)>=from}.let{if(it<0)sessions.lastIndex else it} }
    fun shortDate(session: String): String { val d=date(session);return "${d.dayOfMonth} ${months[d.monthValue-1].take(3)}" }
    fun longDate(session: String): String { val d=date(session);return "${session.substring(11,16)} · ${weekdays[d.dayOfWeek.value%7]} ${d.dayOfMonth} ${months[d.monthValue-1]}" }
    fun axisValue(value: Double): String { val v=floor(value*10+0.5)/10;return if(v==floor(v))v.toInt().toString()else String.format(Locale.US,"%.1f",v) }
    fun layoutPoints(metric: Metric,start: Int,slots: Int): List<Double> { val values=metric.values.drop(start);val count=values.size;return (0 until slots).flatMap{slot->val i=if(count<=1)0 else floor(slot.toDouble()*(count-1)/max(1,slots-1)+0.5).toInt();listOf(if(count<=1)0.5 else i.toDouble()/(count-1),((values[i]-metric.min)/(metric.max-metric.min)).coerceIn(0.0,1.0))} }
    fun format(value: Double,decimals: Int): String { val bits=abs(value).toBits().toULong();val e=((bits shr 52)and 0x7ffu).toInt()-1023-52;val scale=10.0.pow(decimals).toULong();val n=((bits and 0x000fffffffffffffu)or(1uL shl 52))*scale;val units=if(e>=0)n shl e else{val shift=-e;if(shift>=64)0uL else(n shr shift)+if((n and((1uL shl shift)-1u))>=(1uL shl(shift-1)))1u else 0u};return(if(value<0)"-" else "")+(units/scale).toString()+if(decimals==0)"" else "."+String.format(Locale.US,"%0${decimals}d",(units%scale).toInt()) }
}
