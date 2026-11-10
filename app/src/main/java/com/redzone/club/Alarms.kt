package com.redzone.club

import android.app.AlarmManager
import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.media.AudioAttributes
import android.media.MediaPlayer
import android.net.Uri
import android.app.Notification
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import org.json.JSONObject

data class Tone(val id: String, val n: String = "", val uri: String? = null, val ch: String? = null) {
    fun toJson(): JSONObject = JSONObject().put("id", id).put("n", n).put("uri", uri ?: "").put("ch", ch ?: "")
    companion object {
        fun from(o: JSONObject) = Tone(
            o.optString("id", "alarm"), o.optString("n"),
            o.optString("uri").ifEmpty { null }, o.optString("ch").ifEmpty { null }
        )
    }
}

val TONES = listOf(
    Tone("alarm", "المنبه الكلاسيكي"), Tone("tone_bell", "جرس"), Tone("tone_chime", "رنين"),
    Tone("tone_digital", "ديجيتال"), Tone("tone_siren", "صفارة"), Tone("tone_soft", "نغمة هادئة"),
    Tone("tone_ring", "رنين هاتف")
)

object Alarms {
    fun chId(t: Tone) = if (t.id == "custom") (t.ch ?: "rz2_alarm") else "rz2_${t.id}"
    fun toneName(t: Tone) = if (t.id == "custom") "ملف: ${t.n}" else TONES.firstOrNull { it.id == t.id }?.n ?: ""

    fun rawUri(ctx: Context, name: String): Uri {
        val id = ctx.resources.getIdentifier(name, "raw", ctx.packageName)
        return Uri.parse("android.resource://${ctx.packageName}/$id")
    }

    fun toneUri(ctx: Context, t: Tone): Uri =
        if (t.id == "custom" && t.uri != null) Uri.parse(t.uri) else rawUri(ctx, if (t.id == "custom") "alarm" else t.id)

    fun attrs(): AudioAttributes = AudioAttributes.Builder()
        .setUsage(AudioAttributes.USAGE_NOTIFICATION)
        .setContentType(AudioAttributes.CONTENT_TYPE_SONIFICATION).build()

    /** قناة إشعار منبثق بنغمة محددة (نغمة القناة لا تتغير بعد إنشائها، لذلك لكل نغمة قناة). */
    fun ensureChannel(ctx: Context, t: Tone) {
        val ch = NotificationChannel(chId(t), "منبه انتهاء الوقت — ${toneName(t)}", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "تنبيه انتهاء وقت اللعب"
            enableVibration(true)
            vibrationPattern = longArrayOf(0, 400, 200, 400, 200, 400)
            enableLights(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
            setSound(toneUri(ctx, t), attrs())
        }
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).createNotificationChannel(ch)
    }

    private fun pi(ctx: Context, i: Int, extras: Intent?, flag: Int): PendingIntent? {
        val it = (extras ?: Intent()).setClass(ctx, AlarmReceiver::class.java)
        return PendingIntent.getBroadcast(ctx, i + 1, it, flag or PendingIntent.FLAG_IMMUTABLE)
    }

    fun schedule(ctx: Context, i: Int, atMs: Long, channel: String, text: String) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        val intent = Intent().putExtra("id", i + 1).putExtra("ch", channel).putExtra("text", text)
        val p = pi(ctx, i, intent, PendingIntent.FLAG_UPDATE_CURRENT) ?: return
        try {
            if (android.os.Build.VERSION.SDK_INT < 31 || am.canScheduleExactAlarms())
                am.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, p)
            else am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, p)
        } catch (e: SecurityException) {
            am.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, atMs, p)
        }
    }

    fun cancel(ctx: Context, i: Int) {
        val am = ctx.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        pi(ctx, i, null, PendingIntent.FLAG_NO_CREATE)?.let { am.cancel(it); it.cancel() }
        clear(ctx, i)
    }

    /** إشعار إنذار أمني (رمز خاطئ / تغيير الرمز على الجهاز الآخر) */
    fun notifySecurity(ctx: Context, text: String) {
        val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val chId = "rz2_security"
        nm.createNotificationChannel(NotificationChannel(chId, "تنبيهات أمنية", NotificationManager.IMPORTANCE_HIGH).apply {
            description = "إنذار عند إدخال رمز خاطئ أو تغيير الرمز على الجهاز الآخر"
            enableVibration(true)
            lockscreenVisibility = Notification.VISIBILITY_PUBLIC
        })
        val open = PendingIntent.getActivity(
            ctx, 900, Intent(ctx, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(ctx, chId)
            .setSmallIcon(R.drawable.ic_stat).setContentTitle("🚨 تنبيه أمني — Red Zone Club").setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(text))
            .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setAutoCancel(true).setContentIntent(open).build()
        try { NotificationManagerCompat.from(ctx).notify(9000 + (System.currentTimeMillis() % 1000).toInt(), n) } catch (e: SecurityException) { }
    }

    fun clear(ctx: Context, i: Int) {
        (ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(i + 1)
    }
}

class AlarmReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {
        val id = i.getIntExtra("id", 1)
        val ch = i.getStringExtra("ch") ?: return
        val text = i.getStringExtra("text") ?: "انتهى الوقت"
        val open = PendingIntent.getActivity(
            c, id, Intent(c, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_SINGLE_TOP),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT
        )
        val n = NotificationCompat.Builder(c, ch)
            .setSmallIcon(R.drawable.ic_stat).setContentTitle("Red Zone Club").setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_MAX).setCategory(NotificationCompat.CATEGORY_ALARM)
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC).setAutoCancel(true).setContentIntent(open).build()
        try { NotificationManagerCompat.from(c).notify(id, n) } catch (e: SecurityException) { }
    }
}

/** إعادة جدولة المؤقتات بعد إعادة تشغيل الهاتف: مجرد تشغيل العملية يكفي (RzApp.onCreate). */
class BootReceiver : BroadcastReceiver() {
    override fun onReceive(c: Context, i: Intent) {}
}

object Player {
    private var mp: MediaPlayer? = null
    fun play(ctx: Context, t: Tone, loop: Boolean = false): Boolean {
        stop()
        return try {
            val m = MediaPlayer()
            m.setAudioAttributes(Alarms.attrs())
            m.setDataSource(ctx, Alarms.toneUri(ctx, t))
            m.isLooping = loop
            m.setOnCompletionListener { x -> try { x.release() } catch (e: Exception) { }; if (mp === x) mp = null }
            m.prepare(); m.start(); mp = m
            true
        } catch (e: Exception) { false }
    }
    fun stop() {
        try { mp?.stop(); mp?.release() } catch (e: Exception) { }
        mp = null
    }
}

class RzApp : Application() {
    override fun onCreate() {
        super.onCreate()
        Rz.init(this)
    }
}
