@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.redzone.club

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.delay
import java.util.Calendar

/* ===================== سجل المبيعات ===================== */

@Composable
fun LogRow(e: LogEntry) {
    val sale = e.type == "sale"
    val title = if (sale) "🛒 بيع مباشر" else "جهاز ${e.dev}" +
        (if (e.m0 != e.mode && e.m0 != 0) " (${e.m0}→${e.mode})" else if (e.mode == 4) " (4)" else " (2)")
    val ds = e.drinks.joinToString("، ") { "${it.n}×${it.q}" }
    Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Row {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                Spacer(Modifier.width(8.dp))
                Text(if (sale) tm(e.end) else "${tm(e.start)} – ${tm(e.end)}", color = cMu, fontSize = 12.sp)
            }
            Muted((if (sale) "" else "لعب: ${fmt(e.timeCost)} · ") + "مشروبات: ${fmt(e.drinkTotal)}" + (if (ds.isNotEmpty()) "\n$ds" else ""))
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(fmt(e.total), color = cTot, fontWeight = FontWeight.Bold, fontSize = 15.sp)
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Btn("🧾", "", Modifier.width(48.dp)) { Rz.viewLog = e }
                Btn("🗑", "", Modifier.width(48.dp)) { Rz.delAsk = e.id }
            }
        }
    }
}

@Composable
fun LogsTab() {
    val ctx = LocalContext.current
    LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Btn("📅 حسب اليوم", if (Rz.logView == "day") "on" else "", Modifier.weight(1f)) { Rz.logView = "day" }
                Btn("📈 السجل الكامل", if (Rz.logView == "all") "on" else "", Modifier.weight(1f)) { Rz.logView = "all" }
            }
        }
        if (Rz.logView == "day") {
            val k = dkey(Rz.day0)
            val l = Rz.logs.filter { it.day == k }.sortedByDescending { it.end }
            item {
                Panel {
                    Btn("📅 $k", "", Modifier.fillMaxWidth()) { pickDate(ctx, Rz.day0) { Rz.day0 = it } }
                    Text("الإجمالي: ${fmt(l.sumOf { it.total })}", color = cTot, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Muted("لعب: ${fmt(l.sumOf { it.timeCost })} · مشروبات: ${fmt(l.sumOf { it.drinkTotal })} · عمليات: ${l.size}", Modifier.fillMaxWidth(), TextAlign.Center)
                }
            }
            item {
                Panel {
                    if (l.isEmpty()) Muted("لا توجد عمليات")
                    l.forEach { LogRow(it) }
                }
            }
        } else {
            val days = Rz.logs.groupBy { it.day }
            item {
                Panel {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Btn("➕ جلسة قديمة", "pri", Modifier.weight(1f)) { Rz.oldOpen = true }
                        Btn("📊 تصدير السجل (Excel)", "ok", Modifier.weight(1f)) { exportAllLogs() }
                    }
                }
            }
            item {
                Panel {
                    Text("إجمالي كل الأيام: ${fmt(Rz.logs.sumOf { it.total })}", color = cTot, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
                    Muted("${days.size} يوم عمل · ${Rz.logs.size} عملية", Modifier.fillMaxWidth(), TextAlign.Center)
                }
            }
            days.keys.sortedDescending().forEach { k ->
                val l = days[k]!!.sortedByDescending { it.end }
                item(key = k) {
                    Panel {
                        Row { Title(k, Modifier.weight(1f)); Text(fmt(l.sumOf { it.total }), color = cTx, fontWeight = FontWeight.Bold) }
                        l.forEach { LogRow(it) }
                    }
                }
            }
        }
    }
}

/* ===================== جلسة قديمة (تُفتح من السجل الكامل) ===================== */

private class OldCalc(val err: String?, val s: Long, val e: Long, val cost: Double, val dr: Double)

private fun evalOld(dateMs: Long, sp: Pair<Int, Int>?, ep: Pair<Int, Int>?, byAmt: Boolean, amt: String, mode: Int, cart: Map<Int, Int>): OldCalc {
    val dr = cart.entries.filter { it.value > 0 }.sumOf { (Rz.S.drinks.getOrNull(it.key)?.p ?: 0.0) * it.value }
    fun at(p: Pair<Int, Int>): Long = Calendar.getInstance().apply {
        timeInMillis = dateMs; set(Calendar.HOUR_OF_DAY, p.first); set(Calendar.MINUTE, p.second); set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
    }.timeInMillis
    if (sp == null) return OldCalc("اختر وقت البدء", 0L, 0L, 0.0, dr)
    val s = at(sp)
    if (byAmt) {
        val a = num(amt)
        if (a == null || a <= 0.0) return OldCalc("أدخل مبلغ اللعب", s, 0L, 0.0, dr)
        val r = Rz.ratePerMs(mode)
        if (r <= 0.0) return OldCalc("سعر الساعة غير صحيح (من الإعدادات)", s, 0L, 0.0, dr)
        return OldCalc(null, s, s + Math.round(a / r), a, dr)
    }
    if (ep == null) return OldCalc("اختر وقت الانتهاء", s, 0L, 0.0, dr)
    var en = at(ep)
    if (en <= s) en += 86400000L
    return OldCalc(null, s, en, up((en - s) * Rz.ratePerMs(mode)), dr)
}

@Composable
fun OldDialog() {
    val ctx = LocalContext.current
    var dv by remember { mutableIntStateOf(1) }
    var mode by remember { mutableIntStateOf(2) }
    var dateMs by remember { mutableLongStateOf(Rz.now()) }
    var s by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var e by remember { mutableStateOf<Pair<Int, Int>?>(null) }
    var byAmt by remember { mutableStateOf(true) }
    var amt by remember { mutableStateOf("") }
    val cart = remember { mutableStateMapOf<Int, Int>() }
    fun hm(p: Pair<Int, Int>?) = if (p == null) "اختر الوقت" else "${pad(p.first)}:${pad(p.second)}"
    val r = evalOld(dateMs, s, e, byAmt, amt, mode, cart)
    Modal({ Rz.oldOpen = false }) {
        Title("➕ إضافة جلسة سابقة")
        Muted("سجّل جلسة انتهت دون تشغيل المؤقت")
        Muted("الجهاز")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Rz.S.devices.indices.forEach { j -> Btn("${j + 1}", if (dv == j + 1) "on" else "", Modifier.width(52.dp)) { dv = j + 1 } }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Btn("لاعبان", if (mode == 2) "on" else "", Modifier.weight(1f)) { mode = 2 }
            Btn("٤ لاعبين", if (mode == 4) "on" else "", Modifier.weight(1f)) { mode = 4 }
        }
        Muted("التاريخ")
        Btn(dkey(dateMs), "", Modifier.fillMaxWidth()) { pickDate(ctx, dateMs) { dateMs = it } }
        Muted("وقت البدء")
        Btn(hm(s), "", Modifier.fillMaxWidth()) { pickTime(ctx, s?.first ?: 12, s?.second ?: 0) { h, m -> s = h to m } }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Btn("💰 حسب المبلغ", if (byAmt) "on" else "", Modifier.weight(1f)) { byAmt = true }
            Btn("⏱ حسب وقت الانتهاء", if (!byAmt) "on" else "", Modifier.weight(1f)) { byAmt = false }
        }
        if (byAmt) {
            Muted("مبلغ اللعب — يُحسب وقت الانتهاء تلقائياً من سعر الساعة")
            TF(amt, { amt = it }, "المبلغ بالليرة", KeyboardType.Number)
        } else {
            Muted("وقت الانتهاء — يُحسب المبلغ تلقائياً")
            Btn(hm(e), "", Modifier.fillMaxWidth()) { pickTime(ctx, e?.first ?: 12, e?.second ?: 0) { h, m -> e = h to m } }
        }
        Muted("المشروبات (اختياري) — اضغط لإضافة ما تم أخذه")
        DrinkGrid(Rz.S.drinks) { j -> cart[j] = (cart[j] ?: 0) + 1 }
        cart.entries.filter { it.value > 0 }.forEach { (j, q) ->
            val x = Rz.S.drinks.getOrNull(j) ?: return@forEach
            CartLine(x, q) { if (q <= 1) cart.remove(j) else cart[j] = q - 1 }
        }
        if (r.err != null) Muted(r.err)
        else Muted("ينتهي ${tm(r.e)} · المدة ${dur((r.e - r.s).toDouble())}\nاللعب ${fmt(r.cost)} + المشروبات ${fmt(r.dr)} = ${fmt(r.cost + r.dr)}")
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Btn("حفظ الجلسة", "pri", Modifier.weight(2f)) {
                val c = evalOld(dateMs, s, e, byAmt, amt, mode, cart)
                if (c.err != null) Rz.toast(c.err) else {
                    val dl = cart.entries.filter { it.value > 0 }.mapNotNull { (j, q) -> Rz.S.drinks.getOrNull(j)?.let { x -> DrinkQty(x.n, x.p, q) } }
                    val x = Rz.addOld(dv, mode, c.s, c.e, c.cost, dl)
                    Rz.toast("تمت إضافة الجلسة: ${fmt(x.total)}")
                    Rz.day0 = x.start; Rz.logView = "all"; Rz.tab = "log"; Rz.oldOpen = false
                }
            }
            Btn("إلغاء", "", Modifier.weight(1f)) { Rz.oldOpen = false }
        }
    }
}

/* ===================== خيارات المنبه (نافذة منبثقة) ===================== */

@Composable
fun ToneDialog() {
    val ctx = LocalContext.current
    val pick = rememberLauncherForActivityResult(ActivityResultContracts.GetContent()) { u -> if (u != null) Rz.addToneFromUri(u, fileName(ctx, u)) }
    if (!Rz.toneOpen) return
    Modal({ Rz.toneOpen = false }) {
        Title("🎵 خيارات المنبه")
        Muted("تعمل بصوت الإشعارات (مستوى صوت النظام) وليس صوت الوسائط.")
        val list = TONES + listOfNotNull(Rz.custom)
        list.forEach { t ->
            val on = Rz.tone.id == t.id && (t.id != "custom" || Rz.tone.ch == t.ch)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Btn((if (on) "✓ " else "") + Alarms.toneName(t), if (on) "on" else "", Modifier.weight(1f)) { Rz.selTone(t) }
                Btn("▶", "", Modifier.width(52.dp)) { Rz.preview(t) }
            }
        }
        Btn("➕ إضافة نغمة من الملفات", "", Modifier.fillMaxWidth()) { pick.launch("audio/*") }
        Btn("🔔 إعدادات إشعارات النظام", "", Modifier.fillMaxWidth()) {
            try {
                ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
            } catch (ex: Exception) { Rz.toast("تعذر فتح الإعدادات") }
        }
        Muted("لإشعار منبثق مثل واتساب: فعّل «الإشعارات المنبثقة/العائمة» والعرض على شاشة القفل من إعدادات الإشعارات، واجعل البطارية «بلا قيود».")
        Btn("تم", "pri", Modifier.fillMaxWidth()) { Rz.toneOpen = false }
    }
}

/* ===================== الإعدادات ===================== */

@Composable
fun LockScreen(act: MainActivity) {
    var pin by remember { mutableStateOf("") }
    var msg by remember { mutableStateOf("") }
    var forgot by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().padding(10.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Panel(Modifier.widthIn(max = 380.dp)) {
            Text("🔒 الإعدادات مقفلة", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Muted("أدخل الرمز السري للدخول", Modifier.fillMaxWidth(), TextAlign.Center)
            TF(pin, { if (it.length <= 8) pin = it.filter { c -> c.isDigit() } }, "الرمز", KeyboardType.NumberPassword, pw = true, ltr = true)
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Btn("دخول", "pri", Modifier.weight(1f)) { if (Rz.tryPin(pin, "settings")) Rz.setOK = true else { msg = "الرمز غير صحيح"; pin = "" } }
                if (Rz.lock?.bio == true && act.canBio()) Btn("👆 البصمة", "", Modifier.weight(1f)) { tryBio(act) }
            }
            if (msg.isNotEmpty()) Text(msg, color = cAc, fontSize = 13.sp)
            Btn("نسيت الرمز؟", "", Modifier.fillMaxWidth()) {
                if (Rz.rt == null) Rz.toast("لا يمكن استرجاع الرمز بدون ربط المزامنة. يمكنك مسح بيانات التطبيق من إعدادات الهاتف (تُحذف البيانات المحلية).")
                else { Rz.sendAlert("forgot", "🔐 طلب استرجاع/تغيير الرمز السري (نسيت الرمز)"); forgot = true }
            }
        }
    }
    if (forgot) {
        var sec by remember { mutableStateOf("") }
        Modal({ forgot = false }) {
            Title("استرجاع الدخول")
            Muted("أدخل كلمة سر قاعدة البيانات (Database secret) لإلغاء القفل")
            TF(sec, { sec = it }, "Database secret", KeyboardType.Password, pw = true, ltr = true)
            Btn("إلغاء القفل", "pri", Modifier.fillMaxWidth()) {
                if (Rz.rt?.sec == sec.trim()) { Rz.pinOff(); Rz.setOK = true; forgot = false; Rz.toast("تم إلغاء القفل") }
                else { Rz.sendAlert("wrong", "⚠️ محاولة استرجاع الرمز بكلمة سر قاعدة بيانات خاطئة"); Rz.toast("كلمة السر غير صحيحة") }
            }
        }
    }
}

fun fileName(ctx: Context, uri: Uri): String =
    ctx.contentResolver.query(uri, null, null, null, null)?.use { c ->
        val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
        if (i >= 0 && c.moveToFirst()) c.getString(i) else null
    } ?: "tone.mp3"

@Composable
fun SettingsTab(act: MainActivity) {
    if (Rz.lock != null && !Rz.setOK) { LockScreen(act); return }
    var delAsk by remember { mutableStateOf(false) }
    LaunchedEffect(delAsk) { if (delAsk) { delay(4000); delAsk = false } }
    var dlg by remember { mutableStateOf<String?>(null) }
    var p2 by remember(Rz.S.rates) { mutableStateOf(jsRound(Rz.S.rates.p2).toString()) }
    var p4 by remember(Rz.S.rates) { mutableStateOf(jsRound(Rz.S.rates.p4).toString()) }
    var dn by remember { mutableStateOf("") }
    var dp by remember { mutableStateOf("") }

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        /* الأجهزة */
        Panel {
            Row { Title("🎮 الأجهزة", Modifier.weight(1f)); Muted("${Rz.S.devices.size} أجهزة") }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Btn("➕ إضافة جهاز", "pri", Modifier.weight(1f)) { Rz.addDev() }
                Btn(if (delAsk) "تأكيد حذف جهاز ${Rz.S.devices.size}" else "🗑 حذف آخر جهاز", "", Modifier.weight(1f)) {
                    if (!delAsk) delAsk = true else { delAsk = false; Rz.delDev() }
                }
            }
        }
        /* الأسعار */
        Panel {
            Title("أسعار الساعة")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Column(Modifier.weight(1f)) { Muted("لاعبان"); TF(p2, { p2 = it }, "", KeyboardType.Number) }
                Column(Modifier.weight(1f)) { Muted("٤ لاعبين"); TF(p4, { p4 = it }, "", KeyboardType.Number) }
            }
            Muted("يُقرَّب مبلغ الوقت دائماً إلى مضاعفات 500 ل.س (الحد الأدنى 500).")
            Btn("حفظ", "pri", Modifier.fillMaxWidth()) { Rz.saveRates(num(p2) ?: 0.0, num(p4) ?: 0.0); Rz.toast("تم الحفظ") }
        }
        /* المشروبات */
        Panel {
            Title("المشروبات والمنتجات")
            Rz.S.drinks.forEachIndexed { j, x ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text("${Emo.of(x.n)} ${x.n} — ${fmt(x.p)}", color = cTx, modifier = Modifier.weight(1f))
                    Btn("حذف", "", Modifier.width(70.dp)) { Rz.delDrinkDef(j) }
                }
            }
            Muted("يُختار إيموجي تلقائياً حسب الاسم: نودلز 🍜، شاي 🍵، قهوة ☕ ...")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                TF(dn, { dn = it }, "الاسم", modifier = Modifier.weight(1f))
                TF(dp, { dp = it }, "السعر", KeyboardType.Number, modifier = Modifier.weight(1f))
            }
            Btn("إضافة", "pri", Modifier.fillMaxWidth()) {
                val p = num(dp)
                if (dn.isNotBlank() && p != null && p >= 0) { Rz.addDrinkDef(dn.trim(), p); dn = ""; dp = "" }
            }
        }
        /* المنبه: زر يفتح نافذة منبثقة */
        Panel {
            Row { Title("🎵 المنبه", Modifier.weight(1f)); Muted(Alarms.toneName(Rz.tone)) }
            Btn("🎵 خيارات المنبه", "pri", Modifier.fillMaxWidth()) { Rz.toneOpen = true }
        }
        /* القفل */
        Panel {
            Row { Title("🔒 قفل الإعدادات", Modifier.weight(1f)); Muted(if (Rz.lock != null) "مفعّل" else "غير مفعّل") }
            Muted("يُطلب الرمز السري أو البصمة عند فتح الإعدادات وعند حذف أي عملية من السجل. ويصل إنذار للجهاز الآخر عند إدخال رمز خاطئ أو طلب تغييره.")
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Btn(if (Rz.lock != null) "تغيير الرمز السري" else "تفعيل القفل برمز سري", "pri", Modifier.weight(1f)) {
                    if (Rz.lock != null) Rz.sendAlert("request", "🔐 طلب تغيير الرمز السري")
                    dlg = "pin"
                }
                if (Rz.lock != null) Btn("إيقاف القفل", "bad", Modifier.weight(1f)) { Rz.pinOff(); Rz.toast("تم إيقاف القفل") }
            }
            if (Rz.lock != null) {
                val bio = Rz.lock?.bio == true
                Btn(if (bio) "البصمة: مفعّلة (اضغط للإيقاف)" else "تفعيل فتح الإعدادات والحذف بالبصمة", if (bio) "on" else "", Modifier.fillMaxWidth()) {
                    if (bio) Rz.setBio(false)
                    else if (!act.canBio()) Rz.toast("لا توجد بصمة مسجلة على الهاتف")
                    else act.bioAuth("تأكيد البصمة", "إلغاء", { Rz.setBio(true); Rz.toast("تم تفعيل البصمة") }, { Rz.toast("تعذر تفعيل البصمة") })
                }
            }
        }
        /* المزامنة */
        Panel {
            Row { Title("☁️ المزامنة", Modifier.weight(1f)); Muted(if (Rz.cloud) (if (Rz.live) "✅ متصل" else "⏳ غير متصل الآن") else "غير مرتبط") }
            Btn("☁️ ربط / إعدادات المزامنة", "pri", Modifier.fillMaxWidth()) { dlg = "sync" }
        }
    }

    if (dlg == "pin") {
        var a by remember { mutableStateOf("") }
        var b by remember { mutableStateOf("") }
        var m by remember { mutableStateOf("") }
        Modal({ dlg = null }) {
            Title("رمز سري جديد")
            Muted("من 4 إلى 8 أرقام")
            TF(a, { a = it.filter { c -> c.isDigit() }.take(8) }, "الرمز", KeyboardType.NumberPassword, pw = true, ltr = true)
            TF(b, { b = it.filter { c -> c.isDigit() }.take(8) }, "تأكيد الرمز", KeyboardType.NumberPassword, pw = true, ltr = true)
            if (m.isNotEmpty()) Text(m, color = cAc, fontSize = 13.sp)
            Btn("حفظ", "pri", Modifier.fillMaxWidth()) {
                if (a.length < 4) m = "الرمز يجب أن يكون من 4 إلى 8 أرقام"
                else if (a != b) m = "الرمزان غير متطابقين"
                else { Rz.setPin(a); dlg = null; Rz.toast("تم حفظ الرمز السري") }
            }
        }
    }
    if (dlg == "sync") {
        var url by remember { mutableStateOf(Rz.rt?.url ?: "") }
        var sec by remember { mutableStateOf(Rz.rt?.sec ?: "") }
        var m by remember { mutableStateOf("") }
        var busy by remember { mutableStateOf(false) }
        Modal({ dlg = null }) {
            Title("☁️ المزامنة (Realtime Database)")
            Muted("الحالة: " + (if (Rz.cloud) (if (Rz.live) "✅ متصل" else "⏳ مرتبط (غير متصل الآن)") else "غير مرتبط"))
            Muted("رابط قاعدة البيانات (Realtime Database ← Data، الرابط في أعلى الصفحة):")
            TF(url, { url = it }, "https://xxxx-default-rtdb.firebaseio.com", KeyboardType.Uri, ltr = true)
            Muted("كلمة السر (Project settings ← Service accounts ← Database secrets):")
            TF(sec, { sec = it }, "Database secret", KeyboardType.Password, pw = true, ltr = true)
            if (m.isNotEmpty()) Text(m, color = if (busy) cMu else cAc, fontSize = 13.sp)
            Btn("ربط واختبار الاتصال", "pri", Modifier.fillMaxWidth()) {
                if (url.isBlank() || sec.isBlank()) m = "أدخل رابط قاعدة البيانات وكلمة السر"
                else { busy = true; m = "جاري اختبار الاتصال…"; Rz.link(url, sec) { err -> busy = false; if (err == null) { dlg = null; Rz.toast("تم الربط") } else m = err } }
            }
            if (Rz.cloud) Btn("قطع الربط", "bad", Modifier.fillMaxWidth()) { Rz.unlink(); dlg = null }
        }
    }
}
