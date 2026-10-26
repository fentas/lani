package si.lanisce.lani.tutor.live

import android.content.Context
import android.telephony.TelephonyManager
import si.lanisce.lani.tutor.Region

/** The phone's country ([Region.country]): the mobile network's, else the SIM's, else the locale's. No permission needed. */
object AndroidRegion {
    fun country(context: Context): String? {
        val tm = runCatching { context.getSystemService(TelephonyManager::class.java) }.getOrNull()
        return Region.country(runCatching { tm?.networkCountryIso }.getOrNull(), runCatching { tm?.simCountryIso }.getOrNull())
    }

    /** Whether the Gemini API's free tier may not be offered where the phone is ([Region.geminiPaidOnly]). */
    fun geminiPaidOnly(context: Context): Boolean = Region.geminiPaidOnly(country(context))
}
