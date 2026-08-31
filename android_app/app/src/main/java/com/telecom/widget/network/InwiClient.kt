package com.telecom.widget.network

import com.google.gson.Gson
import com.google.gson.JsonObject
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.util.concurrent.TimeUnit

class InwiClient(
    private val phoneOrEmail: String,
    private val pass: String,
    private val selectedLine: String = phoneOrEmail,
    initialCookies: List<String> = emptyList()
) : TelecomClient {

    private val client = OkHttpClient.Builder()
        .connectTimeout(15, TimeUnit.SECONDS)
        .readTimeout(15, TimeUnit.SECONDS)
        .build()

    private val gson = Gson()

    private var accessToken: String = ""
    private var mdnToken: String = ""

    init {
        for (cookie in initialCookies) {
            when {
                cookie.startsWith("accessToken=") -> accessToken = cookie.substringAfter("accessToken=")
                cookie.startsWith("mdnToken=")    -> mdnToken    = cookie.substringAfter("mdnToken=")
            }
        }
    }

    override val currentCookies: List<String>
        get() = listOf("accessToken=$accessToken", "mdnToken=$mdnToken")

    private val baseHeaders = mapOf(
        "User-Agent"   to "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/150.0.0.0 Safari/537.36",
        "Content-Type" to "application/json",
        "Accept"       to "application/json, text/plain, */*",
        "Origin"       to "https://inwi.ma",
        "Referer"      to "https://inwi.ma/",
        "sdata"        to "eyJjaGFubmVsIjoid2ViIiwiYXBwbGljYXRpb25fb3JpZ2luIjoibXlpbndpIiwidXVpZCI6ImMzN2NiYmIzLTc1ZDgtNDhmYy05OWNkLWVjNjNlNTEzMzAwMCIsImxhbmd1YWdlIjoiZnIiLCJhcHBWZXJzaW9uIjoxfQ=="
    )

    private suspend fun doLogin() {
        val body = JsonObject().apply {
            addProperty("username", phoneOrEmail)
            addProperty("password", pass)
        }.toString()
        val req = Request.Builder()
            .url("https://ms-prod.inwi.ma/api/ms-iam/v1/signin")
            .post(body.toRequestBody("application/json".toMediaType()))
        baseHeaders.forEach { (k, v) -> req.addHeader(k, v) }
        val res  = client.newCall(req.build()).execute()
        val resBody = res.body?.string() ?: ""
        if (!res.isSuccessful) throw Exception("Identifiant ou mot de passe Inwi incorrect")
        accessToken = gson.fromJson(resBody, JsonObject::class.java).get("accessToken")?.asString ?: ""
    }

    override suspend fun fetchConsumption(): ConsumptionData {
        if (accessToken.isEmpty()) doLogin()

        val profileReq = Request.Builder()
            .url("https://ms-prod.inwi.ma/api/ms-client/v1/profile")
            .header("Authorization", "Bearer $accessToken")
            .header("Allow", "GET")
        baseHeaders.forEach { (k, v) -> profileReq.addHeader(k, v) }
        var profileRes = client.newCall(profileReq.build()).execute()
        if (profileRes.code == 401 || profileRes.code == 403) {
            doLogin()
            profileReq.header("Authorization", "Bearer $accessToken")
            profileRes = client.newCall(profileReq.build()).execute()
        }
        val profileJson = gson.fromJson(profileRes.body?.string() ?: "", JsonObject::class.java)
        val linesArray = profileJson.getAsJsonArray("lines") ?: throw Exception("Aucune ligne Inwi trouvée")
        if (linesArray.size() == 0) throw Exception("Aucune ligne Inwi trouvée")

        var targetLineObj: JsonObject? = null
        val availableLines = mutableListOf<String>()
        for (elem in linesArray) {
            val line      = elem.asJsonObject
            val mdn       = line.get("mdn")?.asString ?: ""
            val offerName = line.get("offer_name_fr")?.asString ?: ""
            availableLines.add("$mdn - $offerName")
            if (mdn == selectedLine || "$mdn - $offerName" == selectedLine) targetLineObj = line
        }
        if (targetLineObj == null) {
            targetLineObj = when {
                linesArray.size() == 1 -> linesArray[0].asJsonObject
                else -> {
                    val main = linesArray.firstOrNull { it.asJsonObject.get("isMain")?.asBoolean == true }?.asJsonObject
                    if (selectedLine == phoneOrEmail && !phoneOrEmail.contains("@") && main != null) main
                    else throw MultiLineException(availableLines, accessToken, currentCookies)
                }
            }
        }
        val mdn = targetLineObj!!.get("mdn")?.asString ?: ""
        mdnToken = targetLineObj.get("mdnSegmentationToken")?.asString ?: ""

        val balReq = Request.Builder()
            .url("https://ms-prod.inwi.ma/api/ms-balance/v1/balances")
            .header("Authorization", "Bearer $accessToken")
            .header("mdn-segmentation-token", "Bearer $mdnToken")
        baseHeaders.forEach { (k, v) -> balReq.addHeader(k, v) }
        val balBody = client.newCall(balReq.build()).execute().body?.string() ?: ""

        return parseBalances(balBody, mdn)
    }

    suspend fun submitLineSelection(lineString: String, token: String): ConsumptionData {
        accessToken = token
        return fetchConsumption()
    }

    private fun parseBalances(jsonStr: String, currentMdn: String): ConsumptionData {
        val root       = gson.fromJson(jsonStr, JsonObject::class.java)
        val categories = root?.getAsJsonArray("categorie")
            ?: return ConsumptionData(operator = "Inwi", phoneNumber = currentMdn, callsRemaining = "N/A", internetRemaining = "N/A")

        var internet  = "N/A"
        var calls     = "N/A"
        var soldeExtra = ""
        val structured = mutableListOf<ConsumptionDetail>()

        for (catElem in categories) {
            val cat     = catElem.asJsonObject
            val subCats = cat.getAsJsonArray("sub_categories")
            val itemsToProcess = if (subCats != null && subCats.size() > 0) subCats else com.google.gson.JsonArray().apply { add(cat) }

            for (subElem in itemsToProcess) {
                val sub     = subElem.asJsonObject
                val nameFr  = sub.get("name_fr")?.asString ?: cat.get("name_fr")?.asString ?: "Solde"
                val balVal  = sub.get("balance_value")?.asString ?: cat.get("balance_value")?.asString ?: "0"
                val unit    = sub.get("unit")?.asString ?: cat.get("unit")?.asString ?: ""
                val valStr  = "$balVal $unit".trim()

                when {
                    nameFr.contains("Dirham", ignoreCase = true) || unit.equals("dhs", ignoreCase = true) -> {
                        val raw = balVal.toDoubleOrNull() ?: 0.0
                        val dh  = if (raw > 100) raw / 100.0 else raw
                        val formattedSolde = "%.2f DH".format(dh)
                        soldeExtra = formattedSolde
                        structured.add(ConsumptionDetail(nameFr, formattedSolde, "solde"))
                    }
                    nameFr.contains("Internet", ignoreCase = true) || unit in listOf("Go", "Mo", "GB", "MB", "ko", "KB") -> {
                        if (internet == "N/A") internet = valStr
                        structured.add(ConsumptionDetail(nameFr, valStr, "internet"))
                    }
                    nameFr.contains("Appel", ignoreCase = true) || nameFr.contains("Voix", ignoreCase = true)
                            || unit in listOf("h", "min", "sec", "s") -> {
                        if (calls == "N/A") calls = valStr
                        structured.add(ConsumptionDetail(nameFr, valStr, "calls"))
                    }
                    else -> structured.add(ConsumptionDetail(nameFr, valStr, "general"))
                }
            }
        }

        return ConsumptionData(
            operator           = "Inwi",
            phoneNumber        = currentMdn,
            callsRemaining     = calls,
            internetRemaining  = internet,
            extraDetails       = if (soldeExtra.isNotEmpty()) "Solde: $soldeExtra" else null,
            structuredDetails  = structured
        )
    }
}
