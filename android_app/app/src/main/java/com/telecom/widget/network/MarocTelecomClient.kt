package com.telecom.widget.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import org.jsoup.Jsoup
import java.io.IOException
import java.util.LinkedHashMap

class MarocTelecomClient(
    private val email: String,
    private val pass: String,
    private val phone: String,
    cookieStrings: List<String> = emptyList()
) : TelecomClient {

    private val cookieJar = InMemoryCookieJar(parseCookies("https://selfcare.iam.ma", cookieStrings))

    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .cookieJar(cookieJar)
        .build()

    private val redirectingClient = OkHttpClient.Builder()
        .followRedirects(true)
        .cookieJar(cookieJar)
        .build()

    private val commonHeaders = mapOf(
        "User-Agent" to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36",
        "Accept" to "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8",
        "Accept-Language" to "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7,ar;q=0.6"
    )

    // Serialize cookies as full cookie strings (exactly like APK: it.toString())
    override val currentCookies: List<String>
        get() = cookieJar.getAllCookies().map { it.toString() }

    private fun extractHiddenFields(html: String): MutableMap<String, String> {
        val doc = Jsoup.parse(html)
        val fields: MutableMap<String, String> = LinkedHashMap()
        for (input in doc.select("input[type=hidden]")) {
            val name = input.attr("name")
            if (name.isNotEmpty()) {
                fields[name] = input.attr("value")
            }
        }
        return fields
    }

    private fun executeConsumptionFetch(dashHtml: String): ConsumptionData {
        val fields = extractHiddenFields(dashHtml)
        fields["ctl00\$ScriptManager"] = "ctl00\$PlaceHolderMain\$g_4ee8a3c9_eb60_46c5_b6be_5c44e2de7eb0\$ctl00\$upTimer|ctl00\$PlaceHolderMain\$g_4ee8a3c9_eb60_46c5_b6be_5c44e2de7eb0\$ctl00\$LoadTimerConsommation"
        fields["__EVENTTARGET"] = "ctl00\$PlaceHolderMain\$g_4ee8a3c9_eb60_46c5_b6be_5c44e2de7eb0\$ctl00\$LoadTimerConsommation"
        fields["__EVENTARGUMENT"] = ""
        fields["__ASYNCPOST"] = "true"

        val formattedPhone = if (phone.startsWith("0")) "212${phone.substring(1)}" else phone

        fields["ctl00\$PlaceHolderHeaderNav\$g_3f9f9e4a_13a8_42c6_80fd_ef2c3b395c76\$ctl00\$RtListeNumero\$ctl00\$HfNumeroAppel"] = formattedPhone
        fields["ctl00\$PlaceHolderHeaderNav\$g_3f9f9e4a_13a8_42c6_80fd_ef2c3b395c76\$ctl00\$RtListeNumero\$ctl00\$produitID"] = "2"
        fields["ctl00\$PlaceHolderMain\$g_4ee8a3c9_eb60_46c5_b6be_5c44e2de7eb0\$ctl00\$RtListeContrat\$ctl00\$HfNumeroAppel"] = formattedPhone
        fields["ctl00\$PlaceHolderMain\$g_4ee8a3c9_eb60_46c5_b6be_5c44e2de7eb0\$ctl00\$RtListeContrat\$ctl00\$HfProduitId"] = "2"
        fields["ctl00\$PlaceHolderMain\$g_4ee8a3c9_eb60_46c5_b6be_5c44e2de7eb0\$ctl00\$DdlProduit"] = "2"
        fields["ctl00\$PlaceHolderMain\$g_4ee8a3c9_eb60_46c5_b6be_5c44e2de7eb0\$ctl00\$ddlProduitConsommation"] = "2"
        fields["ctl00\$PlaceHolderMain\$g_4ee8a3c9_eb60_46c5_b6be_5c44e2de7eb0\$ctl00\$ddlProduitFidelio"] = "2"

        val formBuilder = FormBody.Builder()
        fields.forEach { (k, v) -> formBuilder.add(k, v) }

        val reqBuilder = Request.Builder().url("https://selfcare.iam.ma/Particulier/Pages/Index.aspx")
        commonHeaders.forEach { (k, v) -> reqBuilder.header(k, v) }
        reqBuilder.header("X-Requested-With", "XMLHttpRequest")
        reqBuilder.header("X-MicrosoftAjax", "Delta=true")
        reqBuilder.header("Referer", "https://selfcare.iam.ma/")
        reqBuilder.post(formBuilder.build())

        val html = client.newCall(reqBuilder.build()).execute().body?.string() ?: ""

        val callsMatch = Regex("""lblCommunicationReste[^>]*>([^<]+)</label>""").find(html)
        val intMatch = Regex("""lblInternetReste[^>]*>([^<]+)</label>""").find(html)
        val callsPct = Regex("""DivProgressCommunication[^>]*data-percent="([^"]+)"""").find(html)
            ?.groupValues?.get(1)?.toFloatOrNull()
        val intPct = Regex("""DivProgressInternet[^>]*data-percent="([^"]+)"""").find(html)
            ?.groupValues?.get(1)?.toFloatOrNull()

        if (callsMatch == null || intMatch == null) {
            throw IOException("UpdatePanel parse failed. Snippet: ${html.take(120)}")
        }

        return ConsumptionData(
            operator = "Maroc Telecom",
            phoneNumber = formattedPhone,
            callsRemaining = callsMatch.groupValues[1].trim(),
            callsPercent = callsPct,
            internetRemaining = intMatch.groupValues[1].trim(),
            internetPercent = intPct
        )
    }

    private fun attemptFullLogin(): ConsumptionData {
        val getReqBuilder = Request.Builder().url("https://selfcare.iam.ma/Pages/Login.aspx")
        commonHeaders.forEach { (k, v) -> getReqBuilder.header(k, v) }
        val getHtml = redirectingClient.newCall(getReqBuilder.build()).execute().body?.string() ?: ""

        val doc = Jsoup.parse(getHtml)
        val fields = extractHiddenFields(getHtml)
        for (input in doc.select("input[type=text]")) {
            val name = input.attr("name")
            if (name.isNotEmpty()) fields[name] = input.attr("value")
        }

        var targetEvent = "ctl00\$ctl50\$g_d8fa90bd_8360_4dd7_bea6_22fc238511fe\$ctl00\$lnkBtnConnex"
        for (a in doc.select("a[href]")) {
            val text = a.text().trim().uppercase()
            val href = a.attr("href")
            if (text.contains("CONNEXION") || href.contains("lnkBtnConnex")) {
                if (href.contains("PostBack")) {
                    val m = Regex("""'([^']+lnkBtnConnex[^']*)'|"([^"]+lnkBtnConnex[^"]*)"""").find(href)
                    if (m != null) targetEvent = m.groupValues[1].ifEmpty { m.groupValues[2] }.trimStart('\'', '"')
                }
            }
        }

        fields["__EVENTTARGET"] = targetEvent
        fields["__EVENTARGUMENT"] = ""
        fields["ctl00\$ctl50\$g_d8fa90bd_8360_4dd7_bea6_22fc238511fe\$ctl00\$txtEmail"] = email
        fields["ctl00\$ctl50\$g_d8fa90bd_8360_4dd7_bea6_22fc238511fe\$ctl00\$txtPassword"] = pass

        val formBuilder = FormBody.Builder()
        fields.forEach { (k, v) -> formBuilder.add(k, v) }

        val postReqBuilder = Request.Builder().url("https://selfcare.iam.ma/Pages/Login.aspx")
        commonHeaders.forEach { (k, v) -> postReqBuilder.header(k, v) }
        postReqBuilder.header("Referer", "https://selfcare.iam.ma/Pages/Login.aspx")
        postReqBuilder.post(formBuilder.build())
        redirectingClient.newCall(postReqBuilder.build()).execute()

        val dashReqBuilder = Request.Builder().url("https://selfcare.iam.ma/Particulier/Pages/Index.aspx")
        commonHeaders.forEach { (k, v) -> dashReqBuilder.header(k, v) }
        dashReqBuilder.header("Referer", "https://selfcare.iam.ma/Pages/Login.aspx")
        val dashHtml = redirectingClient.newCall(dashReqBuilder.build()).execute().body?.string() ?: ""

        if (dashHtml.contains("LoadTimerConsommation")) {
            return executeConsumptionFetch(dashHtml)
        }
        throw IOException("Login failed — could not reach dashboard. Check credentials.")
    }

    override suspend fun fetchConsumption(): ConsumptionData = withContext(Dispatchers.IO) {
        if (cookieJar.getAllCookies().isNotEmpty()) {
            try {
                val fastReqBuilder = Request.Builder().url("https://selfcare.iam.ma/Particulier/Pages/Index.aspx")
                commonHeaders.forEach { (k, v) -> fastReqBuilder.header(k, v) }
                val fastRes = client.newCall(fastReqBuilder.build()).execute()
                val urlStr = fastRes.request.url.toString()
                if (fastRes.isSuccessful && !urlStr.contains("Login.aspx")) {
                    val html = fastRes.body?.string() ?: ""
                    if (html.contains("LoadTimerConsommation")) {
                        return@withContext executeConsumptionFetch(html)
                    }
                }
            } catch (_: Exception) {}
        }

        var lastError: Exception? = null
        for (i in 0..2) {
            try {
                cookieJar.saveFromResponse(
                    okhttp3.HttpUrl.Builder().scheme("https").host("selfcare.iam.ma").build(),
                    emptyList()
                )
                return@withContext attemptFullLogin()
            } catch (e: Exception) {
                lastError = e
                if (e.message?.contains("Check credentials") == true) throw e
            }
        }
        throw lastError ?: IOException("Unknown error")
    }
}
