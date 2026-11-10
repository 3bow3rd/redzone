package com.redzone.club

import org.json.JSONArray
import org.json.JSONObject
import java.util.Calendar
import java.util.Locale
import kotlin.math.ceil
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min

data class DrinkDef(val n: String, val p: Double)
data class DrinkQty(val n: String, val p: Double, val q: Int)
data class Rates(val p2: Double, val p4: Double)

/** جهاز: on=false يعني متاح. نفس بنية البيانات في النسخة القديمة لتبقى المزامنة متوافقة. */
data class Dev(
    val on: Boolean = false,
    val start: Long = 0L,
    val mode: Int = 2,
    val type: String = "open",      // open | timed | amount
    val drinks: List<DrinkQty> = emptyList(),
    val mins: Double = 0.0,
    val amount: Double = 0.0,
    val acc: Double = 0.0,
    val seg: Long = 0L,
    val t2: Long = 0L,
    val t4: Long = 0L,
    val m0: Int = 0,
    val cont: Boolean = false
)

data class AppState(
    val rates: Rates,
    val drinks: List<DrinkDef>,
    val devices: List<Dev>,
    val ts: Long = 0L
)

data class Calc(
    val el: Long, val rem: Double, val kind: Boolean, val reached: Boolean,
    val tc: Double, val dr: Double, val total: Double, val pr: Double,
    val t2: Double, val t4: Double, val c2: Double, val c4: Double
)

data class LogEntry(
    val id: String, val dev: Int, val mode: Int, val m0: Int, val type: String,
    val start: Long, val end: Long, val t2: Double, val t4: Double,
    val c2: Double, val c4: Double, val timeCost: Double,
    val drinks: List<DrinkQty>, val drinkTotal: Double, val total: Double, val day: String
)

fun jsRound(x: Double): Long = floor(x + 0.5).toLong()

fun defaultState() = AppState(
    Rates(10000.0, 15000.0),
    listOf(DrinkDef("شاي", 2000.0), DrinkDef("قهوة", 3000.0), DrinkDef("عصير", 5000.0),
        DrinkDef("مياه", 1000.0), DrinkDef("كولا", 3000.0)),
    List(4) { Dev() }
)

fun rateOf(d: Dev, r: Rates) = (if (d.mode == 4) r.p4 else r.p2) / 3_600_000.0

/** أصغر وحدة للمبلغ: 500 ل.س. أي مبلغ وقت يُقرَّب للأعلى إلى مضاعفات 500 (والحد الأدنى 500). */
const val STEP = 500.0
fun up(x: Double): Double = max(STEP, ceil(x / STEP - 1e-6) * STEP)

/** نفس معادلات الحساب في النسخة الأصلية تماماً. */
fun calc(d: Dev, now: Long, rates: Rates): Calc {
    val r = rateOf(d, rates)
    val base = if (d.seg != 0L) d.seg else d.start
    val raw = d.acc + (now - base) * r
    val el = now - d.start
    val kind = d.type != "open"
    var rem = 0.0
    var reached = false
    var tc = raw
    if (d.type == "timed") {
        rem = d.mins * 60000.0 - el
        reached = rem <= 0
        tc = raw + max(0.0, rem) * r
    } else if (d.type == "amount") {
        rem = (d.amount - raw) / r
        reached = rem <= 0
    }
    tc = if (d.type == "amount") max(d.amount, up(raw)) else up(tc)
    var t2 = d.t2.toDouble()
    var t4 = d.t4.toDouble()
    val sg = (now - base).toDouble() + (if (kind && rem > 0) rem else 0.0)
    if (d.mode == 4) t4 += sg else t2 += sg
    var c2 = min(tc, jsRound(t2 * rates.p2 / 1800000000.0) * STEP)
    var c4 = tc - c2
    if (t4 <= 0) { c2 = tc; c4 = 0.0 } else if (t2 <= 0) { c2 = 0.0; c4 = tc }
    val dr = d.drinks.sumOf { it.p * it.q }
    val pr = when (d.type) {
        "timed" -> el / (d.mins * 60000.0)
        "amount" -> raw / d.amount
        else -> 0.0
    }
    return Calc(el, rem, kind, reached, tc, dr, tc + dr, pr, t2, t4, c2, c4)
}

fun entryFrom(d: Dev, i: Int, end: Long, rates: Rates): LogEntry {
    val c = calc(d, end, rates)
    return LogEntry(
        id = "l${end}_$i", dev = i + 1, mode = d.mode, m0 = if (d.m0 != 0) d.m0 else d.mode,
        type = d.type, start = d.start, end = end, t2 = c.t2, t4 = c.t4, c2 = c.c2, c4 = c.c4,
        timeCost = c.tc, drinks = d.drinks, drinkTotal = c.dr, total = c.total, day = dkey(end)
    )
}

/* ---------------- JSON ---------------- */

private fun truthy(v: Any?): Boolean = when (v) {
    is Boolean -> v
    is Number -> v.toInt() != 0
    is String -> v.isNotEmpty() && v != "0" && v != "false"
    else -> false
}

fun parseDrinks(a: Any?): List<DrinkQty> {
    val items: List<Any?> = when (a) {
        is JSONArray -> (0 until a.length()).map { a.opt(it) }
        is JSONObject -> a.keys().asSequence().map { a.opt(it) }.toList()
        else -> emptyList()
    }
    return items.mapNotNull { x ->
        if (x is JSONObject) DrinkQty(x.optString("n"), x.optDouble("p", 0.0), x.optInt("q", 1)) else null
    }
}

fun drinksJson(l: List<DrinkQty>) = JSONArray().also { a ->
    l.forEach { a.put(JSONObject().put("n", it.n).put("p", it.p).put("q", it.q)) }
}

fun Dev.toJson(): JSONObject {
    val o = JSONObject().put("on", on)
    if (!on) return o
    o.put("start", start).put("mode", mode).put("type", type).put("drinks", drinksJson(drinks))
    if (type == "timed") o.put("mins", mins)
    if (type == "amount") o.put("amount", amount)
    if (acc != 0.0) o.put("acc", acc)
    if (seg != 0L) o.put("seg", seg)
    if (t2 != 0L) o.put("t2", t2)
    if (t4 != 0L) o.put("t4", t4)
    if (m0 != 0) o.put("m0", m0)
    if (cont) o.put("cont", 1)
    return o
}

fun devFrom(o: JSONObject) = Dev(
    on = truthy(o.opt("on")),
    start = o.optLong("start"),
    mode = o.optInt("mode", 2),
    type = o.optString("type", "open").ifEmpty { "open" },
    drinks = parseDrinks(o.opt("drinks")),
    mins = o.optDouble("mins", 0.0),
    amount = o.optDouble("amount", 0.0),
    acc = o.optDouble("acc", 0.0),
    seg = o.optLong("seg"),
    t2 = o.optLong("t2"),
    t4 = o.optLong("t4"),
    m0 = o.optInt("m0"),
    cont = truthy(o.opt("cont"))
)

fun AppState.toJson(): JSONObject {
    val ds = JSONArray(); drinks.forEach { ds.put(JSONObject().put("n", it.n).put("p", it.p)) }
    val dv = JSONArray(); devices.forEach { dv.put(it.toJson()) }
    return JSONObject()
        .put("rates", JSONObject().put("p2", rates.p2).put("p4", rates.p4))
        .put("drinks", ds).put("devices", dv).put("ts", ts)
}

fun stateFrom(o: JSONObject): AppState {
    val def = defaultState()
    val r = o.optJSONObject("rates")
    val rates = if (r != null) Rates(r.optDouble("p2", def.rates.p2), r.optDouble("p4", def.rates.p4)) else def.rates
    val da = o.optJSONArray("drinks")
    val drinks = if (da != null) (0 until da.length()).mapNotNull { da.optJSONObject(it) }
        .map { DrinkDef(it.optString("n"), it.optDouble("p", 0.0)) } else def.drinks
    val va = o.optJSONArray("devices")
    val devs = if (va != null && va.length() > 0) (0 until va.length()).map { i ->
        va.optJSONObject(i)?.let { devFrom(it) } ?: Dev()
    } else def.devices
    return AppState(rates, drinks, devs, o.optLong("ts"))
}

fun LogEntry.toJson(): JSONObject = JSONObject()
    .put("id", id).put("dev", dev).put("mode", mode).put("m0", m0).put("type", type)
    .put("start", start).put("end", end).put("t2", t2).put("t4", t4)
    .put("c2", c2).put("c4", c4).put("timeCost", timeCost)
    .put("drinks", drinksJson(drinks)).put("drinkTotal", drinkTotal)
    .put("total", total).put("day", day)

fun logFrom(o: JSONObject): LogEntry {
    val end = o.optLong("end")
    val mode = o.optInt("mode", 2)
    return LogEntry(
        id = o.getString("id"), dev = o.optInt("dev"), mode = mode, m0 = o.optInt("m0", mode),
        type = o.optString("type", "open"), start = o.optLong("start"), end = end,
        t2 = o.optDouble("t2", 0.0), t4 = o.optDouble("t4", 0.0),
        c2 = o.optDouble("c2", 0.0), c4 = o.optDouble("c4", 0.0),
        timeCost = o.optDouble("timeCost", 0.0), drinks = parseDrinks(o.opt("drinks")),
        drinkTotal = o.optDouble("drinkTotal", 0.0), total = o.optDouble("total", 0.0),
        day = o.optString("day").ifEmpty { dkey(end) }
    )
}

/* ---------------- تنسيقات ---------------- */

fun pad(n: Int) = n.toString().padStart(2, '0')

fun dkey(t: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = t }
    return "${c.get(Calendar.YEAR)}-${pad(c.get(Calendar.MONTH) + 1)}-${pad(c.get(Calendar.DAY_OF_MONTH))}"
}

fun tm(t: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = t }
    var h = c.get(Calendar.HOUR_OF_DAY)
    val a = if (h >= 12) "PM" else "AM"
    h %= 12; if (h == 0) h = 12
    return "\u200E$h:${pad(c.get(Calendar.MINUTE))} $a\u200E"
}

fun hms(ms: Double): String {
    val s = max(0L, floor(ms / 1000.0).toLong())
    return "${pad((s / 3600).toInt())}:${pad((s % 3600 / 60).toInt())}:${pad((s % 60).toInt())}"
}

fun fmt(n: Double): String = String.format(Locale.US, "%,d", jsRound(n)) + " ل.س"

fun dur(ms: Double): String {
    val s = jsRound(ms / 1000.0)
    if (s < 60) return "$s ث"
    val m = jsRound(ms / 60000.0)
    val h = m / 60
    return (if (h > 0) "$h س " else "") + (if (m % 60 != 0L || h == 0L) "${m % 60} د" else "")
}

fun sha(s: String): String =
    java.security.MessageDigest.getInstance("SHA-256").digest(s.toByteArray())
        .joinToString("") { "%02x".format(it) }

fun pinHash(p: String) = sha("rz|$p|club")

/** نص الفاتورة للنسخ */
fun invText(e: LogEntry): String {
    val sb = StringBuilder("Red Zone Club\n")
    val sale = e.type == "sale"
    if (sale) sb.append("فاتورة — بيع مباشر\n${dkey(e.end)} — ${tm(e.end)}\n")
    else sb.append("فاتورة — جهاز ${e.dev}\n${dkey(e.start)} — من ${tm(e.start)} إلى ${tm(e.end)}\n")
    if (!sale) {
        sb.append("\nوقت اللعب\n")
        if (e.t2 > 0 || e.t4 > 0) {
            if (e.t2 > 0) sb.append("لاعبان (${dur(e.t2)}) : ${fmt(e.c2)}\n")
            if (e.t4 > 0) sb.append("٤ لاعبين (${dur(e.t4)}) : ${fmt(e.c4)}\n")
            sb.append("إجمالي وقت اللعب (${dur(e.t2 + e.t4)}) : ${fmt(e.timeCost)}\n")
        } else sb.append("المدة (${dur((e.end - e.start).toDouble())}) : ${fmt(e.timeCost)}\n")
    }
    sb.append("\nالمشروبات\n")
    if (e.drinks.isEmpty() && e.drinkTotal == 0.0) sb.append("لا توجد مشروبات\n")
    e.drinks.forEach { sb.append("${it.n} × ${it.q} (${fmt(it.p)}) : ${fmt(it.p * it.q)}\n") }
    if (e.drinkTotal > 0 || e.drinks.isNotEmpty()) sb.append("مجموع المشروبات : ${fmt(e.drinkTotal)}\n")
    sb.append("\nالإجمالي النهائي: ${fmt(e.total)}")
    return sb.toString()
}

fun tmPlain(t: Long): String {
    val c = Calendar.getInstance().apply { timeInMillis = t }
    var h = c.get(Calendar.HOUR_OF_DAY)
    val a = if (h >= 12) "PM" else "AM"
    h %= 12; if (h == 0) h = 12
    return "$h:${pad(c.get(Calendar.MINUTE))} $a"
}

fun modeName(e: LogEntry): String {
    if (e.type == "sale") return "—"
    fun nm(m: Int) = if (m == 4) "٤ لاعبين" else "لاعبان"
    return if (e.m0 != e.mode && e.m0 != 0) nm(e.m0) + " ← " + nm(e.mode) else nm(e.mode)
}

/* ---------------- تصدير Excel ---------------- */

fun invoiceXlsx(e: LogEntry): ByteArray {
    val sale = e.type == "sale"
    val s = XSheet("الفاتورة", listOf(30.0, 14.0, 10.0, 16.0, 18.0))
    s.add(X.t("Red Zone Club", X.TITLE))
    s.add(X.t(if (sale) "فاتورة — بيع مباشر" else "فاتورة — جهاز ${e.dev}", X.PLAIN))
    s.add(X.t("التاريخ", X.PLAIN), X.t(dkey(e.end), X.PLAIN))
    if (sale) s.add(X.t("الوقت", X.PLAIN), X.t(tmPlain(e.end), X.PLAIN))
    else { s.add(X.t("من", X.PLAIN), X.t(tmPlain(e.start), X.PLAIN)); s.add(X.t("إلى", X.PLAIN), X.t(tmPlain(e.end), X.PLAIN)) }
    s.add()
    s.add(X.t("البند", X.HEAD), X.t("المدة (دقيقة)", X.HEAD), X.t("الكمية", X.HEAD), X.t("سعر الوحدة", X.HEAD), X.t("المبلغ", X.HEAD))
    if (!sale) {
        if (e.t2 > 0 || e.t4 > 0) {
            if (e.t2 > 0) s.add(X.t("لاعبان"), X.n(jsRound(e.t2 / 60000.0)), X.t(""), X.t(""), X.n(e.c2))
            if (e.t4 > 0) s.add(X.t("٤ لاعبين"), X.n(jsRound(e.t4 / 60000.0)), X.t(""), X.t(""), X.n(e.c4))
        } else s.add(X.t("وقت اللعب"), X.n(jsRound((e.end - e.start) / 60000.0)), X.t(""), X.t(""), X.n(e.timeCost))
    }
    e.drinks.forEach { s.add(X.t(it.n), X.t(""), X.n(it.q), X.n(it.p), X.n(it.p * it.q)) }
    if (!sale) s.add(X.t("مجموع وقت اللعب", X.TOTL), X.t("", X.TOTL), X.t("", X.TOTL), X.t("", X.TOTL), X.n(e.timeCost, X.TOTN))
    s.add(X.t("مجموع المشروبات", X.TOTL), X.t("", X.TOTL), X.t("", X.TOTL), X.t("", X.TOTL), X.n(e.drinkTotal, X.TOTN))
    s.add(X.t("الإجمالي النهائي (ل.س)", X.TOTL), X.t("", X.TOTL), X.t("", X.TOTL), X.t("", X.TOTL), X.n(e.total, X.TOTN))
    return X.build(listOf(s))
}

fun logXlsx(logs: List<LogEntry>): ByteArray {
    val all = logs.sortedBy { it.end }
    val s1 = XSheet("السجل", listOf(13.0, 12.0, 9.0, 18.0, 11.0, 11.0, 11.0, 14.0, 34.0, 14.0, 15.0), true)
    s1.add(X.t("التاريخ", X.HEAD), X.t("النوع", X.HEAD), X.t("الجهاز", X.HEAD), X.t("نظام اللعب", X.HEAD), X.t("البدء", X.HEAD), X.t("الانتهاء", X.HEAD),
        X.t("المدة (د)", X.HEAD), X.t("مبلغ اللعب", X.HEAD), X.t("المشروبات", X.HEAD), X.t("مبلغ المشروبات", X.HEAD), X.t("الإجمالي", X.HEAD))
    for (e in all) {
        val sale = e.type == "sale"
        s1.add(X.t(e.day), X.t(if (sale) "بيع مباشر" else "جلسة"), if (sale) X.t("—") else X.n(e.dev), X.t(modeName(e)),
            X.t(if (sale) "" else tmPlain(e.start)), X.t(tmPlain(e.end)), if (sale) X.t("") else X.n(jsRound((e.end - e.start) / 60000.0)),
            X.n(e.timeCost), X.t(e.drinks.joinToString("، ") { "${it.n}×${it.q}" }), X.n(e.drinkTotal), X.n(e.total))
    }
    s1.add(X.t("الإجمالي", X.TOTL), X.t("", X.TOTL), X.t("", X.TOTL), X.t("", X.TOTL), X.t("", X.TOTL), X.t("", X.TOTL), X.t("", X.TOTL),
        X.n(all.sumOf { it.timeCost }, X.TOTN), X.t("", X.TOTL), X.n(all.sumOf { it.drinkTotal }, X.TOTN), X.n(all.sumOf { it.total }, X.TOTN))

    val s2 = XSheet("ملخص الأيام", listOf(14.0, 12.0, 16.0, 16.0, 16.0), true)
    s2.add(X.t("اليوم", X.HEAD), X.t("عدد العمليات", X.HEAD), X.t("مبلغ اللعب", X.HEAD), X.t("المشروبات", X.HEAD), X.t("الإجمالي", X.HEAD))
    all.groupBy { it.day }.toList().sortedByDescending { it.first }.forEach { (day, l) ->
        s2.add(X.t(day), X.n(l.size), X.n(l.sumOf { it.timeCost }), X.n(l.sumOf { it.drinkTotal }), X.n(l.sumOf { it.total }))
    }
    s2.add(X.t("الإجمالي", X.TOTL), X.n(all.size, X.TOTN), X.n(all.sumOf { it.timeCost }, X.TOTN), X.n(all.sumOf { it.drinkTotal }, X.TOTN), X.n(all.sumOf { it.total }, X.TOTN))

    val s3 = XSheet("المشروبات", listOf(24.0, 12.0, 16.0), true)
    s3.add(X.t("المشروب", X.HEAD), X.t("الكمية المباعة", X.HEAD), X.t("المبلغ", X.HEAD))
    all.flatMap { it.drinks }.groupBy { it.n }.toList().sortedByDescending { (_, l) -> l.sumOf { it.q } }.forEach { (name, l) ->
        s3.add(X.t(name), X.n(l.sumOf { it.q }), X.n(l.sumOf { it.p * it.q }))
    }
    return X.build(listOf(s1, s2, s3))
}

/** يحوّل الأرقام العربية/الفارسية إلى لاتينية ويزيل فواصل الآلاف. */
fun latin(s: String): String {
    val sb = StringBuilder(s.length)
    for (ch in s) {
        when (ch) {
            in '\u0660'..'\u0669' -> sb.append('0' + (ch - '\u0660'))
            in '\u06F0'..'\u06F9' -> sb.append('0' + (ch - '\u06F0'))
            '\u066B' -> sb.append('.')
            '\u066C', ',', '\u060C' -> {}
            else -> sb.append(ch)
        }
    }
    return sb.toString()
}

fun num(s: String): Double? = latin(s).trim().toDoubleOrNull()

fun invFile(e: LogEntry): String =
    if (e.type == "sale") "RedZone-sale-${dkey(e.end)}.xlsx" else "RedZone-invoice-dev${e.dev}-${dkey(e.end)}.xlsx"
