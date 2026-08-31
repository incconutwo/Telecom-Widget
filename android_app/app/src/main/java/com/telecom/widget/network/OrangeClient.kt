package com.telecom.widget.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.FormBody
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.ResponseBody
import org.jsoup.Jsoup
import org.jsoup.nodes.Element
import java.io.IOException

class OrangeClient(
    private val loginId: String,
    private val pass: String,
    private val targetPhone: String = loginId,
    cookieStrings: List<String> = emptyList()
) : TelecomClient {

    private var activeMsisdn: String = targetPhone

    private val cookieJar = InMemoryCookieJar(parseCookies("https://espace-client.orange.ma", cookieStrings))

    private val client = OkHttpClient.Builder()
        .followRedirects(false)
        .cookieJar(cookieJar)
        .build()

    private val redirectingClient = OkHttpClient.Builder()
        .followRedirects(true)
        .cookieJar(cookieJar)
        .build()

    // Serialize cookies exactly like APK: it.toString()
    override val currentCookies: List<String>
        get() = cookieJar.getAllCookies().map { it.toString() }

    private fun req(url: String, referer: String? = null): Request.Builder {
        val b = Request.Builder()
            .url(url)
            .header("User-Agent", "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36")
            .header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,image/avif,image/webp,*/*;q=0.8")
            .header("Accept-Language", "fr-FR,fr;q=0.9,en-US;q=0.8,en;q=0.7,ar;q=0.6")
        if (referer != null) b.header("Referer", referer)
        return b
    }

    // Exactly as in APK: iterate all elements and collect ownText()
    private fun getTextWithNewlines(html: String): String {
        val doc = Jsoup.parse(html)
        val lines = mutableListOf<String>()
        for (el in doc.select("*")) {
            val own = el.ownText().trim()
            if (own.isNotEmpty()) lines.add(own)
        }
        return lines.joinToString("\n")
    }

    private fun executeConsumptionFetch(html: String): ConsumptionData {
        val soup = Jsoup.parse(html)
        // APK selects: div.mbm, then keeps only those containing "Solde de recharge"
        val matchingDivs: List<String> = soup.select("div.mbm").mapNotNull { div: Element ->
            val text = getTextWithNewlines(div.outerHtml())
            if (text.contains("Solde de recharge")) text else null
        }

        if (matchingDivs.isEmpty()) {
            throw IOException("Could not parse Orange consumption. Login may have failed.")
        }

        val fullText = matchingDivs.first()
            .lines().map { it.trim() }.filter { it.isNotBlank() }
            .joinToString("\n")

        val dhRegex = Regex("""\b(\d+(?:\.\d+)?)\s*Dh\b""", RegexOption.IGNORE_CASE)
        val internetRegex = Regex("""\b(\d+(?:\.\d+)?)\s*(MO|GO|MB|GB)\b""", RegexOption.IGNORE_CASE)
        val callsRegex = Regex("""\b(\d+h\s*\d+min(?:\s*\d+s)?)\b""", RegexOption.IGNORE_CASE)
        val smsRegex = Regex("""\b(\d+)\s*SMS\b""", RegexOption.IGNORE_CASE)

        val soldeDh = dhRegex.find(fullText)?.value ?: "0.00 Dh"
        val internet = internetRegex.find(fullText)?.value ?: "0 GO"
        val callsMatches = callsRegex.findAll(fullText).toList()
        val callsNational = callsMatches.getOrNull(0)?.value ?: "0h 0min"
        val callsOrange = callsMatches.getOrNull(1)?.value ?: "0h 0min"
        val callsInter = callsMatches.getOrNull(2)?.value ?: "0h 0min"
        val sms = smsRegex.find(fullText)?.value ?: "0 SMS"

        val phoneMatch = Regex("""\b(0[67]\d{8})\b""").find(html)
        val displayPhone = if (activeMsisdn.contains("@")) phoneMatch?.value ?: activeMsisdn else activeMsisdn

        val structuredDetails = listOf(
            ConsumptionDetail("Solde principal", soldeDh, "wallet"),
            ConsumptionDetail("Internet", internet, "internet"),
            ConsumptionDetail("Appels Nationaux", callsNational, "calls"),
            ConsumptionDetail("Appels Orange", callsOrange, "orange"),
            ConsumptionDetail("Appels Internationaux", callsInter, "global"),
            ConsumptionDetail("SMS", sms, "sms")
        )

        return ConsumptionData(
            operator = "Orange",
            phoneNumber = displayPhone,
            callsRemaining = callsNational,
            internetRemaining = internet,
            extraDetails = soldeDh,
            structuredDetails = structuredDetails
        )
    }

    suspend fun submitLineSelection(line: String, token: String): ConsumptionData = withContext(Dispatchers.IO) {
        activeMsisdn = line
        redirectingClient.newCall(
            req("https://espace-client.orange.ma/select-msisdn", "https://espace-client.orange.ma/sso/login")
                .header("Origin", "https://espace-client.orange.ma")
                .post(FormBody.Builder()
                    .add("select_msisdn_form[msisdn]", line)
                    .add("select_msisdn_form[ezxform_token]", token)
                    .build())
                .build()
        ).execute()

        val body: ResponseBody? = redirectingClient.newCall(
            req("https://espace-client.orange.ma/mon-solde", "https://espace-client.orange.ma/").build()
        ).execute().body
        val html = body?.string() ?: ""
        executeConsumptionFetch(html)
    }

    override suspend fun fetchConsumption(): ConsumptionData = withContext(Dispatchers.IO) {
        // Try with existing cookies first
        if (cookieJar.getAllCookies().isNotEmpty()) {
            try {
                val fastRes = redirectingClient.newCall(
                    req("https://espace-client.orange.ma/mon-solde").build()
                ).execute()
                val urlStr = fastRes.request.url.toString()
                if (fastRes.isSuccessful && !urlStr.contains("sso/login")) {
                    val html = fastRes.body?.string() ?: ""
                    if (html.contains("Solde de recharge")) {
                        return@withContext executeConsumptionFetch(html)
                    }
                }
            } catch (_: Exception) {}
        }

        // Full login flow - Clear stale cookies first
        cookieJar.clear()

        // 1. Get login page for CSRF token
        val loginUrl = "https://espace-client.orange.ma/sso/login"
        val tokenHtml = redirectingClient.newCall(
            req(loginUrl).build()
        ).execute().body?.string() ?: ""
        
        var token = Jsoup.parse(tokenHtml).select("input[name=login_form[ezxform_token]]").`val`()

        // 2. POST Phone Number
        val post1Res = redirectingClient.newCall(
            req(loginUrl, loginUrl)
                .header("Origin", "https://espace-client.orange.ma")
                .post(FormBody.Builder()
                    .add("login_form[login]", loginId)
                    .add("login_form[ezxform_token]", token)
                    .build())
                .build()
        ).execute()
        
        val pwdHtml = post1Res.body?.string() ?: ""
        val token2 = Jsoup.parse(pwdHtml).select("input[name=login_form[ezxform_token]]").`val`()
        if (token2.isNotEmpty()) token = token2

        // 3. POST Password to /sso/check
        val checkUrl = "https://espace-client.orange.ma/sso/check"
        val checkRes = redirectingClient.newCall(
            req(checkUrl, post1Res.request.url.toString())
                .header("X-Requested-With", "XMLHttpRequest")
                .post(FormBody.Builder()
                    .add("login_form[_username]", loginId)
                    .add("login_form[_password]", pass)
                    .add("login_form[_remember_me]", "1")
                    .add("login_form[ezxform_token]", token)
                    .build())
                .build()
        ).execute()

        val checkBody = checkRes.body?.string() ?: ""
        
        if (checkRes.isSuccessful && checkBody.contains("\"status\":\"popup\"")) {
            val json = Gson().fromJson(checkBody, JsonObject::class.java)
            val popupHtml = json.get("message")?.asString ?: ""
            val popupDoc = Jsoup.parse(popupHtml)
            val ezxToken = popupDoc.select("input[name=select_msisdn_form[ezxform_token]]").`val`()
            val lines = popupDoc.select("input[name=select_msisdn_form[msisdn]]")
                .map { it.attr("value").trim() }
                .filter { it.isNotEmpty() }
                
            val cleanTarget = targetPhone.replace(" ", "")
            if (lines.contains(cleanTarget)) {
                return@withContext submitLineSelection(cleanTarget, ezxToken)
            } else if (lines.isNotEmpty()) {
                throw MultiLineException(lines, ezxToken, currentCookies)
            }
        } else if (!checkRes.isSuccessful || checkBody.contains("identifiants sont incorrects")) {
             throw IOException("Identifiant ou mot de passe incorrect")
        }

        // 4. Fetch consumption page
        val soldeHtml = redirectingClient.newCall(
            req("https://espace-client.orange.ma/mon-solde", "https://espace-client.orange.ma/").build()
        ).execute().body?.string() ?: ""

        executeConsumptionFetch(soldeHtml)
    }
}
