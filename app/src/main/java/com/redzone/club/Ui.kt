@file:OptIn(ExperimentalLayoutApi::class, ExperimentalMaterial3Api::class)

package com.redzone.club

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDirection
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.delay
import java.util.Calendar
import kotlin.math.min

// ألوان الشعار: أسود/كحلي عميق + أحمر نيون + أزرق نيون
val cBg = Color(0xFF070B14)
val cCard = Color(0xFF0F1626)
val cTx = Color(0xFFF2F5FB)
val cMu = Color(0xFF8B93A7)
val cAc = Color(0xFFE5192D)
val cRed = Color(0xFFFF2D45)
val cOk = Color(0xFF2D8CFF)
val cTot = Color(0xFFFF4D63)
val cLine = Color(0x22FFFFFF)
val cBtn = Color(0xFF1A2338)

const val XLSX_MIME = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"

/* ===================== مكوّنات أساسية ===================== */

@Composable
fun Btn(text: String, kind: String = "", modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    val bg: Brush = when (kind) {
        "pri", "on" -> Brush.linearGradient(listOf(Color(0xFFFF3B52), Color(0xFFB3122A)))
        "ok" -> Brush.linearGradient(listOf(cOk, Color(0xFF1B5FD1)))
        "bad" -> SolidColor(Color(0xFFD81B30))
        else -> SolidColor(cBtn)
    }
    val glow = when (kind) { "pri", "on" -> cRed; "ok" -> cOk; else -> Color.Transparent }
    val base = if (glow == Color.Transparent) modifier else modifier.shadow(10.dp, shape, ambientColor = glow, spotColor = glow)
    Box(
        base.clip(shape).background(bg)
            .border(1.dp, if (kind.isEmpty()) cLine else Color(0x33FFFFFF), shape)
            .clickable(onClick = onClick).padding(horizontal = 10.dp, vertical = 11.dp),
        contentAlignment = Alignment.Center
    ) { Text(text, color = Color.White, fontWeight = FontWeight.SemiBold, fontSize = 14.sp, textAlign = TextAlign.Center) }
}

@Composable
fun Panel(modifier: Modifier = Modifier, run: Boolean = false, content: @Composable ColumnScope.() -> Unit) {
    val shape = RoundedCornerShape(22.dp)
    Column(
        modifier.fillMaxWidth().clip(shape)
            .background(Brush.linearGradient(listOf(Color(0xFF141C30), Color(0xFF0D1322))))
            .border(1.dp, if (run) cOk.copy(alpha = .7f) else Color(0x1AFFFFFF), shape).padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp), content = content
    )
}

@Composable fun Muted(t: String, modifier: Modifier = Modifier, align: TextAlign? = null) =
    Text(t, color = cMu, fontSize = 13.sp, modifier = modifier, textAlign = align)

@Composable fun Title(t: String, modifier: Modifier = Modifier) =
    Text(t, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold, modifier = modifier)

@Composable
fun TF(v: String, onCh: (String) -> Unit, hint: String, kt: KeyboardType = KeyboardType.Text,
       pw: Boolean = false, ltr: Boolean = false, modifier: Modifier = Modifier.fillMaxWidth()) {
    OutlinedTextField(
        value = v, onValueChange = onCh, singleLine = true, modifier = modifier,
        placeholder = { Text(hint, color = cMu, fontSize = 13.sp) },
        keyboardOptions = KeyboardOptions(keyboardType = kt),
        visualTransformation = if (pw) PasswordVisualTransformation() else VisualTransformation.None,
        textStyle = TextStyle(color = cTx, fontSize = 15.sp, textDirection = if (ltr) TextDirection.Ltr else TextDirection.Content),
        shape = RoundedCornerShape(14.dp),
        colors = OutlinedTextFieldDefaults.colors(
            focusedBorderColor = cOk, unfocusedBorderColor = Color(0x33FFFFFF), cursorColor = cOk
        )
    )
}

@Composable
fun Modal(onDismiss: () -> Unit, content: @Composable ColumnScope.() -> Unit) {
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Panel(Modifier.fillMaxWidth(.94f).widthIn(max = 440.dp).heightIn(max = 680.dp).verticalScroll(rememberScrollState()), content = content)
    }
}

@Composable
fun Line(a: String, b: String, bold: Boolean = false) {
    val w = if (bold) FontWeight.Bold else FontWeight.Normal
    Row(Modifier.fillMaxWidth().padding(vertical = 3.dp), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(a, color = cTx, fontWeight = w, fontSize = 14.sp, modifier = Modifier.weight(1f))
        Text(b, color = cTx, fontWeight = w, fontSize = 14.sp)
    }
}

/** زر منتج: إيموجي مناسب لاسمه + الاسم + السعر */
@Composable
fun DrinkBtn(n: String, p: Double, modifier: Modifier = Modifier, onClick: () -> Unit) {
    val shape = RoundedCornerShape(14.dp)
    Column(
        modifier.clip(shape).background(cBtn).border(1.dp, cLine, shape)
            .clickable(onClick = onClick).padding(horizontal = 2.dp, vertical = 7.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text(Emo.of(n), fontSize = 21.sp)
        Text(n, color = Color.White, fontSize = 11.sp, maxLines = 1, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
        Text(String.format("%,d", jsRound(p)), color = cMu, fontSize = 10.sp, maxLines = 1)
    }
}

/** شبكة منتجات: 4 أيقونات في كل صف */
@Composable
fun DrinkGrid(items: List<DrinkDef>, onClick: (Int) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items.indices.chunked(4).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                row.forEach { j -> DrinkBtn(items[j].n, items[j].p, Modifier.weight(1f)) { onClick(j) } }
                repeat(4 - row.size) { Spacer(Modifier.weight(1f)) }
            }
        }
    }
}

/** سطر في السلة: اسم × كمية، المبلغ، وزر ناقص */
@Composable
fun CartLine(x: DrinkDef, q: Int, onMinus: () -> Unit) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text("${Emo.of(x.n)} ${x.n} × $q", color = cTx, modifier = Modifier.weight(1f))
        Text(fmt(x.p * q), color = cTx)
        Spacer(Modifier.width(8.dp))
        Btn("−", "", Modifier.width(44.dp)) { onMinus() }
    }
}

/* ===================== أدوات اختيار الوقت ===================== */

fun pickDate(ctx: Context, ms: Long, cb: (Long) -> Unit) {
    val c = Calendar.getInstance().apply { timeInMillis = ms }
    DatePickerDialog(ctx, { _, y, m, d ->
        val r = Calendar.getInstance(); r.set(y, m, d, 12, 0, 0); r.set(Calendar.MILLISECOND, 0); cb(r.timeInMillis)
    }, c.get(Calendar.YEAR), c.get(Calendar.MONTH), c.get(Calendar.DAY_OF_MONTH)).show()
}

fun pickTime(ctx: Context, h: Int, m: Int, cb: (Int, Int) -> Unit) {
    TimePickerDialog(ctx, { _, hh, mm -> cb(hh, mm) }, h, m, false).show()
}

fun pickDateTime(ctx: Context, ms: Long, cb: (Long) -> Unit) {
    pickDate(ctx, ms) { dayMs ->
        val cur = Calendar.getInstance().apply { timeInMillis = ms }
        pickTime(ctx, cur.get(Calendar.HOUR_OF_DAY), cur.get(Calendar.MINUTE)) { h, m ->
            val r = Calendar.getInstance().apply {
                timeInMillis = dayMs; set(Calendar.HOUR_OF_DAY, h); set(Calendar.MINUTE, m)
                set(Calendar.SECOND, 0); set(Calendar.MILLISECOND, 0)
            }
            cb(r.timeInMillis)
        }
    }
}

fun dtStr(t: Long) = "${dkey(t)}  ${tm(t)}"

fun exportInv(e: LogEntry) {
    try { Rz.exportXlsx(invFile(e), invoiceXlsx(e)) } catch (ex: Exception) { Rz.toast("تعذر إنشاء الملف") }
}

fun exportAllLogs() {
    if (Rz.logs.isEmpty()) { Rz.toast("لا توجد عمليات للتصدير"); return }
    try { Rz.exportXlsx("RedZone-log-${dkey(Rz.now())}.xlsx", logXlsx(Rz.logs)) } catch (ex: Exception) { Rz.toast("تعذر إنشاء الملف") }
}

/* ===================== الجذر ===================== */

@Composable
fun RzRoot(act: MainActivity) {
    val scheme = darkColorScheme(primary = cAc, secondary = cOk, background = cBg, surface = cCard, onSurface = cTx, onBackground = cTx)
    MaterialTheme(colorScheme = scheme) {
        CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Rtl) {
            LaunchedEffect(Unit) { while (true) { Rz.tick(); delay(1000) } }
            val saver = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument(XLSX_MIME)) { uri ->
                if (uri != null) Rz.writeXlsx(uri)
            }
            SideEffect { Rz.saveLauncher = { name -> saver.launch(name) } }
            Box(
                Modifier.fillMaxSize().background(cBg)
                    .background(Brush.radialGradient(listOf(Color(0x40E5192D), Color.Transparent), radius = 1000f))
            ) {
                Scaffold(
                    containerColor = Color.Transparent,
                    topBar = { Header() },
                    bottomBar = { NavBar(act) }
                ) { pad ->
                    Box(Modifier.padding(pad).imePadding().fillMaxSize()) {
                        when (Rz.tab) {
                            "dev" -> DevicesTab()
                            "sale" -> SaleTab()
                            "log" -> LogsTab()
                            else -> SettingsTab(act)
                        }
                    }
                }
                Dialogs(act)
                AlarmBanner(Modifier.align(Alignment.BottomCenter))
            }
        }
    }
}

/** اسم النادي: RED ZONE CLUB — كل الحروف كبيرة ومتوهجة، وكلمة CLUB أصغر */
@Composable
fun BrandName() {
    CompositionLocalProvider(LocalLayoutDirection provides LayoutDirection.Ltr) {
        Text(
            buildAnnotatedString {
                withStyle(SpanStyle(fontSize = 27.sp, fontWeight = FontWeight.Black, color = Color(0xFFFF3349))) { append("RED ZONE") }
                withStyle(SpanStyle(fontSize = 15.sp, fontWeight = FontWeight.Bold, color = Color.White)) { append(" CLUB") }
            },
            style = TextStyle(shadow = Shadow(color = cRed, offset = Offset.Zero, blurRadius = 26f)),
            maxLines = 1
        )
    }
}

@Composable
fun Header() {
    val ok = Rz.cloud && Rz.live
    val c = if (ok) Color(0xFF22C55E) else Color(0xFFEF4444)
    Row(
        Modifier.fillMaxWidth().background(Color(0xB3070B14)).statusBarsPadding().padding(horizontal = 14.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Image(painterResource(R.drawable.logo), null, Modifier.size(50.dp).clip(RoundedCornerShape(14.dp)))
        Spacer(Modifier.width(10.dp))
        BrandName()
        Spacer(Modifier.weight(1f))
        Text("المزامنة", color = Color(0xFF7FB2FF), fontSize = 12.sp)
        Spacer(Modifier.width(6.dp))
        Box(Modifier.size(10.dp).shadow(8.dp, CircleShape, ambientColor = c, spotColor = c).clip(CircleShape).background(c))
    }
}

fun tryBio(act: MainActivity) {
    val k = Rz.lock
    if (k == null || !k.bio || Rz.setOK || !act.canBio()) return
    act.bioAuth("Red Zone Club — الإعدادات", "استخدام الرمز", { Rz.setOK = true })
}

/** الشريط السفلي: الأجهزة | البيع المباشر | السجل | الإعدادات */
@Composable
fun NavBar(act: MainActivity) {
    val items = listOf("dev" to ("🎮" to "الأجهزة"), "sale" to ("🛒" to "البيع المباشر"), "log" to ("🧾" to "السجل"), "set" to ("⚙️" to "الإعدادات"))
    val shape = RoundedCornerShape(26.dp)
    Box(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 12.dp, vertical = 8.dp)) {
        Row(
            Modifier.fillMaxWidth().widthIn(max = 560.dp).align(Alignment.Center).clip(shape)
                .background(Color(0xF20D1322)).border(1.dp, Color(0x1FFFFFFF), shape).padding(5.dp),
            horizontalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            items.forEach { (id, v) ->
                val on = Rz.tab == id
                val ish = RoundedCornerShape(21.dp)
                val m = Modifier.weight(1f).let { if (on) it.shadow(10.dp, ish, ambientColor = cRed, spotColor = cRed) else it }
                Column(
                    m.clip(ish)
                        .background(if (on) Brush.linearGradient(listOf(Color(0xFFFF3B52), Color(0xFFB3122A))) else SolidColor(Color.Transparent))
                        .clickable {
                            if (id != "set") Rz.setOK = false
                            Rz.tab = id
                            if (id == "set") tryBio(act)
                        }.padding(vertical = 8.dp),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Text(v.first, fontSize = 20.sp)
                    Text(v.second, fontSize = 10.5.sp, maxLines = 1, color = if (on) Color.White else cMu, fontWeight = if (on) FontWeight.Bold else FontWeight.Normal)
                }
            }
        }
    }
}

/* ===================== تبويب الأجهزة ===================== */

@Composable
fun DevicesTab() {
    LazyVerticalGrid(
        columns = GridCells.Adaptive(330.dp),
        contentPadding = PaddingValues(10.dp),
        horizontalArrangement = Arrangement.spacedBy(10.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        itemsIndexed(Rz.S.devices) { i, d ->
            if (d.on) RunCard(i, d) else IdleCard(i)
        }
    }
}

@Composable
fun IdleCard(i: Int) {
    val f = Rz.forms[i] ?: Form()
    Panel {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Title("🎮 جهاز ${i + 1}", Modifier.weight(1f))
            Text("متاح", color = Color(0xFF4ADE80), fontSize = 12.sp, fontWeight = FontWeight.Bold,
                modifier = Modifier.clip(RoundedCornerShape(99.dp)).background(Color(0x2222C55E)).padding(horizontal = 12.dp, vertical = 2.dp))
        }
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Btn("👥 لاعبان", if (f.mode == 2) "on" else "", Modifier.weight(1f)) { Rz.forms[i] = f.copy(mode = 2) }
            Btn("👥 ٤ لاعبين", if (f.mode == 4) "on" else "", Modifier.weight(1f)) { Rz.forms[i] = f.copy(mode = 4) }
        }
        Muted("سعر الساعة: " + fmt(if (f.mode == 4) Rz.S.rates.p4 else Rz.S.rates.p2))
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Btn("مفتوح", if (f.type == "open") "on" else "", Modifier.weight(1f)) { Rz.forms[i] = f.copy(type = "open") }
            Btn("مدة", if (f.type == "timed") "on" else "", Modifier.weight(1f)) { Rz.forms[i] = f.copy(type = "timed") }
            Btn("بمبلغ", if (f.type == "amount") "on" else "", Modifier.weight(1f)) { Rz.forms[i] = f.copy(type = "amount") }
        }
        if (f.type != "open")
            TF(f.v, { Rz.forms[i] = f.copy(v = it) }, if (f.type == "timed") "المدة بالدقائق (60 = ساعة)" else "المبلغ بالليرة السورية", KeyboardType.Number)
        Btn("▶ بدء", "ok", Modifier.fillMaxWidth()) { Rz.startDev(i, f)?.let { Rz.toast(it) } }
    }
}

@Composable
fun RunCard(i: Int, d: Dev) {
    val c = calc(d, Rz.nowMs, Rz.S.rates)
    var xf by remember(i) { mutableStateOf(false) }
    val tn = when (d.type) { "timed" -> "مدة ${jsRound(d.mins)} د"; "amount" -> "بمبلغ ${fmt(d.amount)}"; else -> "مفتوح" }
    Panel(run = true) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Title("🎮 جهاز ${i + 1}", Modifier.weight(1f))
            Box(Modifier.size(9.dp).clip(CircleShape).background(cOk))
        }
        Muted("${if (d.mode == 4) "٤ لاعبين" else "لاعبان"} · $tn · ${tm(d.start)}")
        val timer = if (c.kind) (if (c.rem > 0) "متبقي " + hms(c.rem) else "انتهى الوقت +" + hms(-c.rem)) else hms(c.el.toDouble())
        Text(timer, color = Color.White, fontSize = 34.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center,
            style = TextStyle(shadow = Shadow(color = cOk, offset = Offset.Zero, blurRadius = 18f)), modifier = Modifier.fillMaxWidth())
        if (c.kind) {
            val p = c.pr.takeIf { it.isFinite() } ?: 0.0
            LinearProgressIndicator(progress = { min(1f, p.toFloat().coerceAtLeast(0f)) }, color = cOk, trackColor = Color(0x22FFFFFF),
                modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)))
        }
        if (c.reached && !d.cont) Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Btn("▶ استمرار الحساب", "ok", Modifier.weight(1f)) { Rz.cont(i) }
            Btn("⏹ إيقاف", "bad", Modifier.weight(1f)) { Rz.stopAt(i) }
        }
        DrinkGrid(Rz.S.drinks) { j -> Rz.S.drinks.getOrNull(j)?.let { x -> Rz.addDrink(i, x.n, x.p) } }
        d.drinks.forEach { x ->
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text("${Emo.of(x.n)} ${x.n} × ${x.q}", color = cTx, modifier = Modifier.weight(1f))
                Text(fmt(x.p * x.q), color = cTx)
                Spacer(Modifier.width(8.dp))
                Btn("−", "", Modifier.width(44.dp)) { Rz.subDrink(i, x.n) }
            }
        }
        Text(fmt(c.total), color = cTot, fontSize = 23.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Btn(if (d.mode == 4) "👥 تحويل إلى لاعبين" else "👥 تحويل إلى ٤ لاعبين", "", Modifier.weight(1f)) { Rz.swMode(i) }
            Btn("🔁 نقل لجهاز آخر", "", Modifier.weight(1f)) {
                if (Rz.S.devices.withIndex().any { (j, x) -> j != i && !x.on }) xf = !xf else Rz.toast("لا يوجد جهاز فارغ")
            }
        }
        if (xf) FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Rz.S.devices.forEachIndexed { j, x -> if (j != i && !x.on) Btn("إلى جهاز ${j + 1}", "pri") { xf = false; Rz.moveTo(i, j) } }
        }
        Btn("✏️ تعديل الوقت / المبلغ", "", Modifier.fillMaxWidth()) { Rz.editDev = i }
        Btn("⏹ إنهاء وحساب", "bad", Modifier.fillMaxWidth()) { Rz.openInv(i, Rz.now()) }
    }
}

/* ===================== البيع المباشر ===================== */

@Composable
fun SaleTab() {
    val cart = Rz.saleCart
    val tot = cart.entries.sumOf { (j, q) -> (Rz.S.drinks.getOrNull(j)?.p ?: 0.0) * q }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(10.dp)) {
        Panel(Modifier.widthIn(max = 560.dp)) {
            Title("🛒 بيع مباشر للمشروبات")
            Muted("اضغط على المنتج لإضافته إلى الفاتورة")
            DrinkGrid(Rz.S.drinks) { j -> cart[j] = (cart[j] ?: 0) + 1 }
            cart.entries.filter { it.value > 0 }.forEach { (j, q) ->
                val x = Rz.S.drinks.getOrNull(j) ?: return@forEach
                CartLine(x, q) { if (q <= 1) cart.remove(j) else cart[j] = q - 1 }
            }
            Text(fmt(tot), color = cTot, fontSize = 24.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Btn("🛒 تأكيد البيع", "ok", Modifier.weight(2f)) { if (Rz.saleDone(cart.toMap())) cart.clear() }
                Btn("مسح", "", Modifier.weight(1f)) { cart.clear() }
            }
        }
    }
}

/* ===================== الفواتير والنوافذ ===================== */

@Composable
fun InvBody(e: LogEntry) {
    val sale = e.type == "sale"
    Text("Red Zone Club", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
    if (sale) {
        Muted("فاتورة — بيع مباشر", Modifier.fillMaxWidth(), TextAlign.Center)
        Muted("${dkey(e.end)} — ${tm(e.end)}", Modifier.fillMaxWidth(), TextAlign.Center)
    } else {
        Muted("فاتورة — جهاز ${e.dev}", Modifier.fillMaxWidth(), TextAlign.Center)
        Muted("${dkey(e.start)} — من ${tm(e.start)} إلى ${tm(e.end)}", Modifier.fillMaxWidth(), TextAlign.Center)
        Muted("وقت اللعب")
        if (e.t2 > 0 || e.t4 > 0) {
            if (e.t2 > 0) Line("لاعبان (${dur(e.t2)})", fmt(e.c2))
            if (e.t4 > 0) Line("٤ لاعبين (${dur(e.t4)})", fmt(e.c4))
            Line("إجمالي وقت اللعب (${dur(e.t2 + e.t4)})", fmt(e.timeCost), true)
        } else Line("المدة (${dur((e.end - e.start).toDouble())})", fmt(e.timeCost), true)
    }
    Muted("المشروبات")
    if (e.drinks.isEmpty() && e.drinkTotal == 0.0) Muted("لا توجد مشروبات")
    e.drinks.forEach { Line("${Emo.of(it.n)} ${it.n} × ${it.q} (${fmt(it.p)})", fmt(it.p * it.q)) }
    if (e.drinkTotal > 0 || e.drinks.isNotEmpty()) Line("مجموع المشروبات", fmt(e.drinkTotal), true)
    Text("الإجمالي النهائي: ${fmt(e.total)}", color = cTot, fontSize = 22.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth().padding(top = 8.dp))
}

@Composable
fun Dialogs(act: MainActivity) {
    val ctx = LocalContext.current
    /* فاتورة الإنهاء */
    Rz.inv?.let { x ->
        val d = Rz.S.devices.getOrNull(x.i)
        if (d == null || !d.on) { Rz.inv = null } else {
            val e = entryFrom(d, x.i, x.end, Rz.S.rates)
            var sMs by remember { mutableLongStateOf(e.start) }
            var eMs by remember { mutableLongStateOf(e.end) }
            Modal({ Rz.inv = null }) {
                InvBody(e)
                Muted("وقت البدء")
                Btn(dtStr(sMs), "", Modifier.fillMaxWidth()) { pickDateTime(ctx, sMs) { sMs = it } }
                Muted("وقت الانتهاء")
                Btn(dtStr(eMs), "", Modifier.fillMaxWidth()) { pickDateTime(ctx, eMs) { eMs = it } }
                Btn("✏️ تطبيق الأوقات", "", Modifier.fillMaxWidth()) { Rz.applyInvTimes(sMs, eMs)?.let { Rz.toast(it) } }
                Btn("📊 تصدير الفاتورة (Excel)", "", Modifier.fillMaxWidth()) { exportInv(e) }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Btn("⏹ تأكيد وإنهاء الجلسة", "ok", Modifier.weight(2f)) { Rz.confirmInv() }
                    Btn("رجوع", "", Modifier.weight(1f)) { Rz.inv = null }
                }
            }
        }
    }
    /* عرض فاتورة من السجل */
    Rz.viewLog?.let { e ->
        Modal({ Rz.viewLog = null }) {
            InvBody(e)
            Btn("📊 تصدير الفاتورة (Excel)", "", Modifier.fillMaxWidth()) { exportInv(e) }
            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                Btn("🧾 نسخ الفاتورة", "", Modifier.weight(1f)) {
                    val cm = ctx.getSystemService(Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
                    cm.setPrimaryClip(android.content.ClipData.newPlainText("invoice", invText(e)))
                    Rz.toast("تم نسخ الفاتورة")
                }
                Btn("إغلاق", "", Modifier.weight(1f)) { Rz.viewLog = null }
            }
        }
    }
    /* تعديل الجلسة */
    Rz.editDev?.let { i ->
        val d = Rz.S.devices.getOrNull(i)
        if (d == null || !d.on) { Rz.editDev = null } else {
            var type by remember { mutableStateOf(d.type) }
            var v by remember { mutableStateOf("") }
            var fromNow by remember { mutableStateOf(true) }
            var st by remember { mutableLongStateOf(d.start) }
            Modal({ Rz.editDev = null }) {
                Title("✏️ تعديل جهاز ${i + 1}")
                Muted("وقت البدء")
                Btn(dtStr(st), "", Modifier.fillMaxWidth()) { pickDateTime(ctx, st) { st = it } }
                Muted("نوع الحساب")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Btn("مفتوح", if (type == "open") "on" else "", Modifier.weight(1f)) { type = "open" }
                    Btn("مدة", if (type == "timed") "on" else "", Modifier.weight(1f)) { type = "timed" }
                    Btn("بمبلغ", if (type == "amount") "on" else "", Modifier.weight(1f)) { type = "amount" }
                }
                if (type != "open") {
                    TF(v, { v = it }, if (type == "timed") "المدة بالدقائق (30 = نصف ساعة، 45 = ثلاثة أرباع)" else "المبلغ (مثلاً 5000 أو 7000)", KeyboardType.Number)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Btn("يُحسب من الآن", if (fromNow) "on" else "", Modifier.weight(1f)) { fromNow = true }
                        Btn("من بداية الجلسة", if (!fromNow) "on" else "", Modifier.weight(1f)) { fromNow = false }
                    }
                    Muted(if (fromNow) "يُضاف على ما مضى" else "إجمالي من بداية الجلسة")
                }
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Btn("حفظ التعديل", "pri", Modifier.weight(1f)) {
                        val err = Rz.applyEdit(i, st, type, num(v), fromNow)
                        if (err != null) Rz.toast(err) else { Rz.editDev = null; Rz.toast("تم تعديل الجلسة") }
                    }
                    Btn("إلغاء", "", Modifier.weight(1f)) { Rz.editDev = null }
                }
            }
        }
    }
    /* حذف عملية: يحتاج نفس الرمز السري/البصمة التي تقفل الإعدادات */
    Rz.delAsk?.let { id ->
        val lk = Rz.lock
        var pin by remember(id) { mutableStateOf("") }
        var msg by remember(id) { mutableStateOf("") }
        LaunchedEffect(id) {
            if (lk != null && lk.bio && act.canBio())
                act.bioAuth("تأكيد حذف العملية", "استخدام الرمز", { Rz.delAsk = null; Rz.delLog(id) })
        }
        Modal({ Rz.delAsk = null }) {
            if (lk == null) {
                Title("🗑 حذف هذه العملية؟")
                Muted("لحماية الحذف برمز سري أو بصمة فعّل قفل الإعدادات من صفحة الإعدادات.")
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Btn("حذف", "bad", Modifier.weight(1f)) { Rz.delAsk = null; Rz.delLog(id) }
                    Btn("إلغاء", "", Modifier.weight(1f)) { Rz.delAsk = null }
                }
            } else {
                Title("🔒 أدخل الرمز السري لحذف العملية")
                TF(pin, { if (it.length <= 8) pin = it.filter { c -> c.isDigit() } }, "الرمز", KeyboardType.NumberPassword, pw = true, ltr = true)
                if (msg.isNotEmpty()) Text(msg, color = cAc, fontSize = 13.sp)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Btn("🗑 حذف", "bad", Modifier.weight(1f)) {
                        if (Rz.tryPin(pin, "delete")) { Rz.delAsk = null; Rz.delLog(id) } else { msg = "الرمز غير صحيح"; pin = "" }
                    }
                    if (lk.bio && act.canBio()) Btn("👆 البصمة", "", Modifier.weight(1f)) {
                        act.bioAuth("تأكيد حذف العملية", "إلغاء", { Rz.delAsk = null; Rz.delLog(id) })
                    }
                    Btn("إلغاء", "", Modifier.weight(1f)) { Rz.delAsk = null }
                }
            }
        }
    }
    /* إضافة جلسة قديمة (من السجل الكامل) */
    if (Rz.oldOpen) OldDialog()
    /* خيارات المنبه */
    ToneDialog()
    /* إنذار أمني من الجهاز الآخر */
    Rz.alertMsg?.let { m ->
        Modal({ Rz.alertMsg = null }) {
            Text("🚨 تنبيه أمني", color = cTot, fontSize = 20.sp, fontWeight = FontWeight.Bold, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Text(m, color = cTx, fontSize = 15.sp, textAlign = TextAlign.Center, modifier = Modifier.fillMaxWidth())
            Btn("حسناً", "pri", Modifier.fillMaxWidth()) { Rz.alertMsg = null }
        }
    }
}

@Composable
fun AlarmBanner(modifier: Modifier) {
    val i = Rz.ringing ?: return
    Column(
        modifier.fillMaxWidth().navigationBarsPadding().padding(10.dp).padding(bottom = 84.dp)
            .shadow(14.dp, RoundedCornerShape(18.dp), ambientColor = cRed, spotColor = cRed)
            .clip(RoundedCornerShape(18.dp)).background(cAc).padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp), horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Text("🔔 انتهى وقت الجهاز ${i + 1}", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 17.sp)
        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Btn("▶ استمرار الحساب", "", Modifier.weight(1f)) { Rz.cont(i) }
            Btn("⏹ إيقاف", "", Modifier.weight(1f)) { Rz.stopAt(i) }
        }
        Btn("كتم الصوت", "", Modifier.fillMaxWidth()) { Rz.dismiss(i) }
    }
}
