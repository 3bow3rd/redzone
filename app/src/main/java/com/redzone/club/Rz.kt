package com.redzone.club

import android.content.ContentValues
import android.content.Context
import android.content.SharedPreferences
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import android.widget.Toast
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.app.NotificationManagerCompat
import kotlinx.coroutines.*
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import kotlin.math.min
import kotlin.math.roundToLong

data class Rt(val url: String, val sec: String)
data class Pend(val m: String, val p: String, val v: String?)
data class Form(val mode: Int = 2, val type: String = "open", val v: String = "")
data class InvLive(val i: Int, val end: Long)
data class Lock(val h: String, val bio: Boolean)
class HttpErr(val code: Int) : Exception("HTTP $code")

/** كل حالة التطبيق ومنطقه (مقابل المتغيرات العامة في نسخة الويب). */
object Rz {
    lateinit var app: Context
    private lateinit var sp: SharedPreferences
    private var inited = false
    private val main = Handler(Looper.getMainLooper())
    private val io = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    var S by mutableStateOf(defaultState())
    var logs by mutableStateOf<List<LogEntry>>(emptyList())
    var nowMs by mutableStateOf(System.currentTimeMillis())
    var live by mutableStateOf(false)
    var cloud by mutableStateOf(false)
    var ringing by mutableStateOf<Int?>(null)
    var tone by mutableStateOf(Tone("alarm"))
    var custom by mutableStateOf<Tone?>(null)
    var lock by mutableStateOf<Lock?>(null)
    var setOK by mutableStateOf(false)
    var rt by mutableStateOf<Rt?>(null)
    var inv by mutableStateOf<InvLive?>(null)
    var viewLog by mutableStateOf<LogEntry?>(null)
    var editDev by mutableStateOf<Int?>(null)
    var saleOpen by mutableStateOf(false)
    var day0 by mutableStateOf(System.currentTimeMillis())
    var logView by mutableStateOf("day")
    var tab by mutableStateOf("dev")
    var delAsk by mutableStateOf<String?>(null)      // معرّف العملية المطلوب حذفها (ينتظر الرمز/البصمة)
    var alertMsg by mutableStateOf<String?>(null)    // إنذار أمني وارد من الجهاز الآخر
    val saleCart = mutableStateMapOf<Int, Int>()     // سلة البيع المباشر (تبقى عند التنقل بين التبويبات)
    var oldOpen by mutableStateOf(false)             // نافذة إضافة جلسة قديمة
    var toneOpen by mutableStateOf(false)            // نافذة خيارات المنبه
    private var alertSeen = 0L
    private var pendingXlsx: ByteArray? = null
    var saveLauncher: ((String) -> Unit)? = null
    val forms = mutableStateMapOf<Int, Form>()
    private var offset = 0L
    private val dismissed = mutableSetOf<Long>()
    private var jobs = mutableListOf<Job>()

    fun now() = System.currentTimeMillis() + offset
    fun post(f: () -> Unit) { main.post(f) }
    fun toast(m: String) { main.post { Toast.makeText(app, m, Toast.LENGTH_SHORT).show() } }

    /* ---------------- تهيئة وحفظ محلي ---------------- */
    fun init(ctx: Context) {
        if (inited) return
        inited = true
        app = ctx.applicationContext
        sp = app.getSharedPreferences("rz", Context.MODE_PRIVATE)
        offset = sp.getLong("rz_off", 0L)
        alertSeen = sp.getLong("rz_aseen", 0L)
        runCatching { sp.getString("ps_state", null)?.let { S = stateFrom(JSONObject(it)) } }
        runCatching {
            sp.getString("ps_logs", null)?.let { s ->
                val a = JSONArray(s)
                logs = (0 until a.length()).mapNotNull { a.optJSONObject(it) }.map { logFrom(it) }
            }
        }
        runCatching { sp.getString("rz_tone", null)?.let { tone = Tone.from(JSONObject(it)) } }
        runCatching { sp.getString("rz_cust", null)?.let { custom = Tone.from(JSONObject(it)) } }
        runCatching { sp.getString("rz_lock", null)?.let { val o = JSONObject(it); lock = Lock(o.getString("h"), o.optBoolean("bio")) } }
        runCatching {
            sp.getString("rz_rt", null)?.let { val o = JSONObject(it); rt = Rt(o.getString("url"), o.getString("sec")) }
        }
        runCatching { Alarms.ensureChannel(app, tone) }
        resched()
        if (rt != null) { cloud = true; startCloud() }
    }

    private fun saveLocalState() { sp.edit().putString("ps_state", S.toJson().toString()).apply() }
    private fun saveLogs() {
        val a = JSONArray(); logs.forEach { a.put(it.toJson()) }
        sp.edit().putString("ps_logs", a.toString()).apply()
    }

    fun saveS() {
        S = S.copy(ts = now())
        saveLocalState()
        resched()
        if (cloud) rtWrite("PUT", "lounge/state", JSONObject().put("ts", S.ts).put("json", S.toJson().toString()).toString())
    }

    private fun setDev(i: Int, d: Dev) { S = S.copy(devices = S.devices.toMutableList().also { it[i] = d }) }

    /* ---------------- المنبه والإشعارات ---------------- */
    fun resched() {
        val n = now()
        S.devices.forEachIndexed { i, d ->
            if (d.on && d.type != "open" && !d.cont) {
                val c = calc(d, n, S.rates)
                if (!c.reached) Alarms.schedule(app, i, System.currentTimeMillis() + c.rem.toLong(), Alarms.chId(tone), "انتهى وقت الجهاز ${i + 1}")
            } else Alarms.cancel(app, i)
        }
    }

    fun tick() {
        val n = now()
        nowMs = n
        S.devices.forEachIndexed { i, d ->
            if (d.on && ringing == null && !d.cont && !dismissed.contains(d.start) && calc(d, n, S.rates).reached) {
                ringing = i
                // إن كانت الإشعارات معطلة نشغّل الصوت داخل التطبيق
                if (!NotificationManagerCompat.from(app).areNotificationsEnabled()) Player.play(app, tone, true)
            }
        }
        ringing?.let { r ->
            val d = S.devices.getOrNull(r)
            if (d == null || !d.on || d.cont) stopAlarm()
        }
    }

    fun stopAlarm() { ringing = null; Player.stop() }
    fun dismiss(i: Int) { S.devices.getOrNull(i)?.let { dismissed.add(it.start) }; stopAlarm(); Alarms.clear(app, i) }
    fun cont(i: Int) {
        setDev(i, S.devices[i].copy(cont = true))
        if (ringing == i) stopAlarm()
        Alarms.clear(app, i); saveS()
    }

    /* ---------------- الجلسات ---------------- */
    fun startDev(i: Int, f: Form): String? {
        val v = num(f.v)
        if (f.type == "timed" && !(v != null && v > 0)) return "أدخل المدة بالدقائق"
        if (f.type == "amount" && !(v != null && v > 0)) return "أدخل المبلغ"
        setDev(i, Dev(on = true, start = now(), mode = f.mode, type = f.type,
            mins = if (f.type == "timed") v!! else 0.0, amount = if (f.type == "amount") v!! else 0.0))
        saveS(); return null
    }

    fun addDrink(i: Int, n: String, p: Double) {
        val d = S.devices[i]
        val l = d.drinks.toMutableList()
        val k = l.indexOfFirst { it.n == n }
        if (k >= 0) l[k] = l[k].copy(q = l[k].q + 1) else l.add(DrinkQty(n, p, 1))
        setDev(i, d.copy(drinks = l)); saveS()
    }

    fun subDrink(i: Int, n: String) {
        val d = S.devices[i]
        val l = d.drinks.toMutableList()
        val k = l.indexOfFirst { it.n == n }
        if (k < 0) return
        if (l[k].q <= 1) l.removeAt(k) else l[k] = l[k].copy(q = l[k].q - 1)
        setDev(i, d.copy(drinks = l)); saveS()
    }

    fun swMode(i: Int) {
        val d = S.devices[i]; val n = now()
        val base = if (d.seg != 0L) d.seg else d.start
        val seg = n - base
        var nd = d.copy(acc = d.acc + seg * rateOf(d, S.rates), seg = n, m0 = if (d.m0 != 0) d.m0 else d.mode)
        nd = if (d.mode == 4) nd.copy(t4 = d.t4 + seg, mode = 2) else nd.copy(t2 = d.t2 + seg, mode = 4)
        setDev(i, nd); saveS()
        toast("تم التحويل إلى " + (if (nd.mode == 4) "٤ لاعبين" else "لاعبين") + " — يُحسب الوقت القادم بالسعر الجديد")
    }

    fun moveTo(i: Int, j: Int) {
        if (S.devices[j].on) return
        val l = S.devices.toMutableList(); l[j] = l[i]; l[i] = Dev(); S = S.copy(devices = l)
        if (ringing == i) stopAlarm()
        Alarms.cancel(app, i); saveS()
        toast("تم نقل اللاعب من جهاز ${i + 1} إلى جهاز ${j + 1}")
    }

    /** يغيّر وقت البدء. يرجع نص الخطأ أو null عند النجاح. لا يحفظ (المستدعي يحفظ). */
    fun setStart(i: Int, ns: Long): String? {
        val d = S.devices[i]
        if (!d.on) return "الجهاز غير مشغول"
        if (ns > now() + 1000) return "وقت البدء لا يمكن أن يكون في المستقبل"
        if (d.seg != 0L && ns >= d.seg) return "وقت البدء يجب أن يسبق وقت تحويل النظام"
        val dl = d.start - ns
        var nd = d
        if (d.seg != 0L) {
            val m0 = if (d.m0 != 0) d.m0 else d.mode
            nd = if (m0 == 4) nd.copy(t4 = d.t4 + dl, acc = d.acc + dl * S.rates.p4 / 3600000.0)
            else nd.copy(t2 = d.t2 + dl, acc = d.acc + dl * S.rates.p2 / 3600000.0)
        }
        setDev(i, nd.copy(start = ns))
        dismissed.remove(d.start); dismissed.remove(ns)
        return null
    }

    fun applyEdit(i: Int, ns: Long, type: String, v: Double?, fromNow: Boolean): String? {
        var d = S.devices[i]
        if (!d.on) return null
        if (Math.abs(ns - d.start) >= 60000) { setStart(i, ns)?.let { return it }; d = S.devices[i] }
        val n = now()
        if (type != d.type || v != null) {
            if (type == "open") d = d.copy(type = "open", mins = 0.0, amount = 0.0)
            else {
                if (v == null || !(v > 0)) return if (type == "timed") "أدخل المدة بالدقائق" else "أدخل المبلغ"
                d = if (type == "timed") d.copy(mins = if (fromNow) (n - d.start) / 60000.0 + v else v, amount = 0.0)
                else {
                    val base = if (d.seg != 0L) d.seg else d.start
                    val raw = d.acc + (n - base) * rateOf(d, S.rates)
                    d.copy(amount = if (fromNow) up(raw + v) else v, mins = 0.0)
                }
                d = d.copy(type = type)
            }
            d = d.copy(cont = false)
            dismissed.remove(d.start)
            if (ringing == i) stopAlarm()
        }
        setDev(i, d); saveS(); return null
    }

    fun openInv(i: Int, end: Long) {
        dismissed.add(S.devices[i].start)
        if (ringing == i) stopAlarm()
        Alarms.clear(app, i)
        inv = InvLive(i, end)
    }

    fun stopAt(i: Int) {
        val d = S.devices[i]; val n = now(); val c = calc(d, n, S.rates)
        openInv(i, min(n, n + c.rem.toLong()))
    }

    fun applyInvTimes(s: Long, e: Long): String? {
        val x = inv ?: return null
        val d = S.devices[x.i]
        if (!d.on) return null
        if (e <= s || e <= d.seg) return "الوقت غير صحيح: الانتهاء يجب أن يكون بعد البدء"
        if (Math.abs(s - d.start) >= 60000) setStart(x.i, s)?.let { return it }
        inv = x.copy(end = e); saveS(); return null
    }

    fun confirmInv() {
        val x = inv ?: return
        inv = null
        val e = entryFrom(S.devices[x.i], x.i, x.end, S.rates)
        logPut(e)
        setDev(x.i, Dev())
        if (ringing == x.i) stopAlarm()
        Alarms.cancel(app, x.i)
        saveS()
    }

    fun addDev() { S = S.copy(devices = S.devices + Dev()); saveS(); toast("تمت إضافة جهاز ${S.devices.size}") }
    fun delDev(): Boolean {
        val n = S.devices.size - 1
        if (n < 1) { toast("يجب بقاء جهاز واحد على الأقل"); return false }
        if (S.devices[n].on) { toast("الجهاز ${n + 1} مشغول، أنهِ جلسته أولاً"); return false }
        S = S.copy(devices = S.devices.dropLast(1)); Alarms.cancel(app, n); saveS(); return true
    }

    /* ---------------- السجل والبيع ---------------- */
    fun logPut(e: LogEntry) {
        if (logs.none { it.id == e.id }) logs = logs + e
        saveLogs()
        if (cloud) rtWrite("PUT", "logs/${e.id}", e.toJson().toString())
    }

    fun delLog(id: String) {
        logs = logs.filter { it.id != id }; saveLogs()
        if (cloud) rtWrite("DELETE", "logs/$id", null)
        toast("تم الحذف")
    }

    fun saleDone(cart: Map<Int, Int>): Boolean {
        val dr = cart.entries.filter { it.value > 0 }.mapNotNull { (j, q) -> S.drinks.getOrNull(j)?.let { DrinkQty(it.n, it.p, q) } }
        if (dr.isEmpty()) { toast("اختر مشروباً أولاً"); return false }
        val n = now(); val t = dr.sumOf { it.p * it.q }
        logPut(LogEntry("s${n}_${(1000..9999).random()}", 0, 2, 2, "sale", n, n, 0.0, 0.0, 0.0, 0.0, 0.0, dr, t, t, dkey(n)))
        toast("تم البيع: ${fmt(t)}"); return true
    }

    fun ratePerMs(mode: Int) = (if (mode == 4) S.rates.p4 else S.rates.p2) / 3600000.0

    /** إضافة جلسة سابقة. المبلغ والمشروبات محسوبة مسبقاً من الواجهة. */
    fun addOld(dev: Int, mode: Int, startMs: Long, endMs: Long, cost: Double, drinks: List<DrinkQty>): LogEntry {
        val len = (endMs - startMs).toDouble()
        val dt = drinks.sumOf { it.p * it.q }
        val x = LogEntry("m${now()}_${(100..999).random()}", dev, mode, mode, "open", startMs, endMs,
            if (mode == 4) 0.0 else len, if (mode == 4) len else 0.0,
            if (mode == 4) 0.0 else cost, if (mode == 4) cost else 0.0, cost, drinks, dt, cost + dt, dkey(startMs))
        logPut(x); return x
    }

    fun saveRates(p2: Double, p4: Double) { S = S.copy(rates = Rates(p2, p4)); saveS() }
    fun addDrinkDef(n: String, p: Double) { S = S.copy(drinks = S.drinks + DrinkDef(n, p)); saveS() }
    fun delDrinkDef(j: Int) { S = S.copy(drinks = S.drinks.toMutableList().also { it.removeAt(j) }); saveS() }

    /* ---------------- النغمات ---------------- */
    fun selTone(t: Tone) {
        tone = t
        sp.edit().putString("rz_tone", t.toJson().toString()).apply()
        runCatching { Alarms.ensureChannel(app, t) }
        resched()
        if (!Player.play(app, t)) toast("تعذر تشغيل النغمة")
    }

    fun preview(t: Tone) { if (!Player.play(app, t)) toast("تعذر تشغيل النغمة") }

    fun addToneFromUri(uri: Uri, name: String) {
        if (Build.VERSION.SDK_INT < 29) { toast("تعذر إضافة النغمة (تتطلب أندرويد 10 أو أحدث)"); return }
        io.launch {
            try {
                val bytes = app.contentResolver.openInputStream(uri)!!.use { it.readBytes() }
                if (bytes.size > 6_000_000) { toast("الملف كبير (الحد الأقصى 6 ميغا)"); return@launch }
                val ext = name.substringAfterLast('.', "mp3").lowercase().filter { it.isLetterOrDigit() }.ifEmpty { "mp3" }
                val v = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, "redzone_${System.currentTimeMillis()}.$ext")
                    put(MediaStore.MediaColumns.MIME_TYPE, app.contentResolver.getType(uri) ?: "audio/mpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, "Notifications/RedZone")
                    put(MediaStore.Audio.Media.IS_NOTIFICATION, 1)
                }
                val out = app.contentResolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, v)!!
                app.contentResolver.openOutputStream(out)!!.use { it.write(bytes) }
                val t = Tone("custom", name, out.toString(), "rz2_c${System.currentTimeMillis()}")
                post {
                    custom = t
                    sp.edit().putString("rz_cust", t.toJson().toString()).apply()
                    selTone(t); toast("تم اختيار النغمة")
                }
            } catch (e: Exception) { toast("تعذر إضافة النغمة") }
        }
    }

    /* ---------------- قفل الإعدادات ---------------- */
    fun setPin(pin: String) {
        val had = lock != null
        lock = Lock(pinHash(pin), lock?.bio ?: false); saveLock(); setOK = true
        sendAlert("change", if (had) "🔐 تم تغيير الرمز السري" else "🔒 تم تفعيل قفل الإعدادات")
    }
    fun pinOff() { lock = null; saveLock(); setOK = false; sendAlert("off", "🔓 تم إيقاف قفل الإعدادات") }
    fun setBio(on: Boolean) { lock = lock?.copy(bio = on); saveLock() }
    private fun saveLock() {
        val l = lock
        if (l == null) sp.edit().remove("rz_lock").apply()
        else sp.edit().putString("rz_lock", JSONObject().put("h", l.h).put("bio", l.bio).toString()).apply()
    }
    fun checkPin(p: String) = lock?.h == pinHash(p)

    /** يتحقق من الرمز؛ عند الخطأ يُرسل إنذاراً للجهاز الآخر. where: settings | delete */
    fun tryPin(p: String, where: String): Boolean {
        if (checkPin(p)) return true
        sendAlert("wrong", if (where == "delete") "⚠️ رمز خاطئ عند محاولة حذف عملية من السجل" else "⚠️ رمز خاطئ عند محاولة فتح الإعدادات")
        return false
    }

    /* ---------------- تنبيهات أمنية بين الجهازين ---------------- */
    private fun devId(): String =
        sp.getString("rz_id", null) ?: ("d" + java.util.UUID.randomUUID().toString().take(6)).also { sp.edit().putString("rz_id", it).apply() }

    fun devName(): String = (Build.MANUFACTURER + " " + Build.MODEL).trim()

    fun sendAlert(kind: String, msg: String) {
        if (rt == null) return
        val o = JSONObject().put("t", now()).put("dev", devId()).put("name", devName()).put("kind", kind).put("msg", msg)
        rtWrite("PUT", "alerts/a${now()}_${(1000..9999).random()}", o.toString())
    }

    private fun onAlertsVal(v: Any?) {
        val o = v as? JSONObject
        val me = devId()
        val list = ArrayList<Triple<Long, String, String>>()
        if (o != null) o.keys().forEach { k ->
            val a = o.optJSONObject(k)
            if (a != null && a.optString("dev") != me) list.add(Triple(a.optLong("t"), a.optString("name", "جهاز آخر"), a.optString("msg")))
        }
        post {
            if (alertSeen == 0L) { alertSeen = now(); sp.edit().putLong("rz_aseen", alertSeen).apply(); return@post }
            val fresh = list.filter { it.first > alertSeen }.sortedBy { it.first }
            if (fresh.isNotEmpty()) {
                alertSeen = maxOf(alertSeen, fresh.last().first)
                sp.edit().putLong("rz_aseen", alertSeen).apply()
                val text = fresh.joinToString("\n\n") { "${it.third}\n(من: ${it.second})" }
                alertMsg = (alertMsg?.let { it + "\n\n" } ?: "") + text
                runCatching { Alarms.notifySecurity(app, fresh.last().third) }
                if (ringing == null) Player.play(app, Tone("tone_siren"), false)
            }
        }
    }

    /* ---------------- تصدير Excel ---------------- */
    fun exportXlsx(name: String, bytes: ByteArray) {
        val f = saveLauncher
        if (f == null) { toast("تعذر فتح نافذة الحفظ"); return }
        pendingXlsx = bytes
        f(name)
    }

    fun writeXlsx(uri: Uri) {
        val b = pendingXlsx ?: return
        io.launch {
            try {
                app.contentResolver.openOutputStream(uri)!!.use { it.write(b) }
                toast("تم حفظ ملف Excel")
            } catch (e: Exception) { toast("تعذر حفظ الملف") }
        }
    }

    /* ---------------- المزامنة: Firebase Realtime Database (REST + SSE) ---------------- */
    private fun rtUrl(c: Rt, p: String) = c.url + "/" + p + ".json?auth=" + URLEncoder.encode(c.sec, "UTF-8")

    private fun http(c: Rt, m: String, p: String, body: String?): String {
        val con = URL(rtUrl(c, p)).openConnection() as HttpURLConnection
        try {
            con.requestMethod = m; con.connectTimeout = 10000; con.readTimeout = 15000
            if (body != null) { con.doOutput = true; con.outputStream.use { it.write(body.toByteArray()) } }
            val code = con.responseCode
            if (code !in 200..299) throw HttpErr(code)
            return con.inputStream.bufferedReader().use { it.readText() }
        } finally { con.disconnect() }
    }

    private fun pend(): List<Pend> = runCatching {
        val a = JSONArray(sp.getString("rz_pend", "[]"))
        (0 until a.length()).map { val o = a.getJSONObject(it); Pend(o.getString("m"), o.getString("p"), o.optString("v").ifEmpty { null }) }
    }.getOrDefault(emptyList())

    private fun setPend(q: List<Pend>) {
        val a = JSONArray(); q.forEach { a.put(JSONObject().put("m", it.m).put("p", it.p).put("v", it.v ?: "")) }
        sp.edit().putString("rz_pend", a.toString()).apply()
    }

    private fun queue(m: String, p: String, v: String?) {
        if (!p.startsWith("logs/")) return
        synchronized(this) { setPend(pend().filter { it.p != p } + Pend(m, p, v)) }
    }

    fun rtWrite(m: String, p: String, v: String?) {
        io.launch {
            val c = rt ?: return@launch
            try { http(c, m, p, v) }
            catch (e: HttpErr) {
                if (e.code == 401 || e.code == 403) toast("كلمة السر غير صحيحة") else queue(m, p, v)
            } catch (e: Exception) { queue(m, p, v) }
        }
    }

    private fun flush() {
        if (rt == null) return
        val q = synchronized(this) { val x = pend(); setPend(emptyList()); x }
        q.forEach { rtWrite(it.m, it.p, it.v) }
    }

    private class Tree {
        var root: Any? = null
        fun apply(path: String, d: Any?) {
            val data = if (d === JSONObject.NULL) null else d
            val k = path.split("/").filter { it.isNotEmpty() }
            if (k.isEmpty()) { root = data; return }
            if (root !is JSONObject) root = JSONObject()
            var o = root as JSONObject
            for (i in 0 until k.size - 1) {
                val c = o.opt(k[i])
                if (c is JSONObject) o = c else { val n = JSONObject(); o.put(k[i], n); o = n }
            }
            if (data == null) o.remove(k.last()) else o.put(k.last(), data)
        }
    }

    private fun listen(path: String, onVal: (Any?) -> Unit, onErr: (String) -> Unit): Job = io.launch {
        val tree = Tree()
        while (isActive) {
            var con: HttpURLConnection? = null
            try {
                val c = rt ?: break
                con = URL(rtUrl(c, path)).openConnection() as HttpURLConnection
                con.setRequestProperty("Accept", "text/event-stream")
                con.connectTimeout = 10000; con.readTimeout = 90000
                val code = con.responseCode
                if (code == 401 || code == 403) { post { onErr("auth") }; break }
                if (code !in 200..299) throw HttpErr(code)
                post { live = true; flush() }
                val rd = con.inputStream.bufferedReader()
                var ev = ""; val data = StringBuilder()
                while (isActive) {
                    val line = rd.readLine() ?: break
                    when {
                        line.startsWith("event:") -> ev = line.substring(6).trim()
                        line.startsWith("data:") -> { if (data.isNotEmpty()) data.append('\n'); data.append(line.substring(5).trim()) }
                        line.isEmpty() -> {
                            when (ev) {
                                "put" -> { val m = JSONObject(data.toString()); tree.apply(m.getString("path"), m.opt("data")); onVal(tree.root) }
                                "patch" -> {
                                    val m = JSONObject(data.toString()); val dd = m.optJSONObject("data"); val p = m.getString("path").trimEnd('/')
                                    if (dd != null) dd.keys().forEach { k -> tree.apply("$p/$k", dd.opt(k)) }; onVal(tree.root)
                                }
                                "cancel", "auth_revoked" -> post { onErr("auth") }
                            }
                            ev = ""; data.setLength(0)
                        }
                    }
                }
            } catch (e: Exception) { } finally { con?.disconnect() }
            post { live = false }
            delay(3000)
        }
    }

    private fun onStateVal(v: Any?) {
        val r = v as? JSONObject
        val js = r?.optString("json", "") ?: ""
        if (r != null && js.isNotEmpty()) {
            val n = runCatching { stateFrom(JSONObject(js)) }.getOrNull() ?: return
            post {
                if (n.ts >= S.ts) { S = n; saveLocalState(); resched() }
                else rtWrite("PUT", "lounge/state", JSONObject().put("ts", S.ts).put("json", S.toJson().toString()).toString())
            }
        } else post { saveS() }
    }

    private fun onLogsVal(v: Any?) {
        val o = v as? JSONObject ?: JSONObject()
        val a = o.keys().asSequence().mapNotNull { o.optJSONObject(it) }
            .mapNotNull { runCatching { logFrom(it) }.getOrNull() }.toMutableList()
        pend().forEach { q ->
            val id = q.p.removePrefix("logs/")
            if (q.m == "DELETE") a.removeAll { it.id == id }
            else if (a.none { it.id == id }) q.v?.let { s -> runCatching { a.add(logFrom(JSONObject(s))) } }
        }
        post { logs = a; saveLogs() }
    }

    private fun rtErr(w: String) { live = false; if (w == "auth") toast("كلمة السر غير صحيحة أو تم إلغاؤها") }

    fun startCloud() {
        syncClock()
        jobs.forEach { it.cancel() }; jobs.clear()
        jobs.add(listen("lounge/state", ::onStateVal, ::rtErr))
        jobs.add(listen("logs", ::onLogsVal, ::rtErr))
        jobs.add(listen("alerts", ::onAlertsVal, ::rtErr))
    }

    fun syncClock() {
        val c = rt ?: return
        io.launch {
            val id = devId()
            var bestRtt = Long.MAX_VALUE; var bestOff = 0.0; var ok = false
            repeat(3) {
                val t0 = System.currentTimeMillis()
                try {
                    val body = http(c, "PUT", "clock/$id", "{\".sv\":\"timestamp\"}")
                    val sv = body.trim().toDoubleOrNull() ?: http(c, "GET", "clock/$id", null).trim().toDouble()
                    val t1 = System.currentTimeMillis(); val rtt = t1 - t0
                    if (rtt < bestRtt) { bestRtt = rtt; bestOff = sv - (t0 + t1) / 2.0; ok = true }
                } catch (e: Exception) { }
                delay(250)
            }
            if (ok) { offset = bestOff.roundToLong(); sp.edit().putLong("rz_off", offset).apply(); post { resched() } }
        }
    }

    fun resume() {
        if (!cloud) return
        syncClock()
        if (jobs.isEmpty() || jobs.any { !it.isActive }) startCloud() else flush()
    }

    fun rtNorm(u0: String): String {
        var u = u0.trim().replace(Regex("\\.json.*$"), "").trimEnd('/')
        if (!Regex("^https?://", RegexOption.IGNORE_CASE).containsMatchIn(u)) u = "https://$u"
        return u.replaceFirst(Regex("^http://", RegexOption.IGNORE_CASE), "https://")
    }

    fun link(url: String, sec: String, cb: (String?) -> Unit) {
        val c = Rt(rtNorm(url), sec.trim())
        io.launch {
            val err: String? = try { http(c, "GET", "lounge/state/ts", null); null }
            catch (e: HttpErr) { when (e.code) { 401 -> "كلمة السر غير صحيحة"; 404 -> "رابط قاعدة البيانات غير صحيح"; else -> "خطأ من الخادم (${e.code})" } }
            catch (e: Exception) { "تعذر الاتصال — تأكد من الرابط ومن الإنترنت" }
            post {
                if (err == null) {
                    sp.edit().putString("rz_rt", JSONObject().put("url", c.url).put("sec", c.sec).toString()).apply()
                    rt = c; cloud = true; startCloud()
                }
                cb(err)
            }
        }
    }

    fun unlink() {
        jobs.forEach { it.cancel() }; jobs.clear()
        sp.edit().remove("rz_rt").apply()
        rt = null; cloud = false; live = false
    }
}
