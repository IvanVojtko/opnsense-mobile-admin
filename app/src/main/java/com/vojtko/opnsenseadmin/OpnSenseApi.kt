package com.vojtko.opnsenseadmin

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.net.HttpURLConnection
import java.net.URL
import java.text.DecimalFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import javax.net.ssl.HostnameVerifier
import javax.net.ssl.HttpsURLConnection
import javax.net.ssl.SSLContext
import javax.net.ssl.X509TrustManager

data class DashboardStat(
    val title: String,
    val value: String,
    val detail: String,
    val usage: Float
)

data class DashboardPoint(
    val label: String,
    val value: Float
)

data class InterfaceStat(
    val name: String,
    val device: String,
    val statusLabel: String,
    val address: String,
    val inRate: String,
    val outRate: String
)

data class InterfaceEntry(
    val identifier: String,
    val name: String,
    val description: String,
    val device: String,
    val statusLabel: String,
    val linkType: String,
    val ipv4: String,
    val ipv6: String,
    val inRate: String,
    val outRate: String,
    val isEnabled: Boolean
)

data class ServiceEntry(
    val id: String,
    val serviceId: String,
    val name: String,
    val description: String,
    val statusLabel: String,
    val isRunning: Boolean,
    val isEnabled: Boolean?,
    val isLocked: Boolean
)

data class FirewallRuleEntry(
    val uuid: String,
    val description: String,
    val action: String,
    val interfaceName: String,
    val protocol: String,
    val source: String,
    val destination: String,
    val isEnabled: Boolean
)

data class FirmwareStatus(
    val productVersion: String,
    val latestVersion: String,
    val statusMessage: String,
    val updatesAvailable: String,
    val updatePackages: List<String>,
    val downloadSize: String,
    val needsReboot: Boolean,
    val lastCheck: String,
    val upgradeStatus: String
)

data class SystemNotification(
    val subject: String,
    val title: String,
    val message: String,
    val age: String,
    val statusCode: String,
    val location: String
)

data class DashboardSnapshot(
    val endpoint: String,
    val systemName: String,
    val versionSummary: String,
    val updateHint: String,
    val uptime: String,
    val loadAverage: String,
    val refreshedAt: String,
    val cpuCurrentRatio: Float?,
    val trafficCurrentRatio: Float?,
    val stats: List<DashboardStat>,
    val cpuHistory: List<DashboardPoint>,
    val trafficHistory: List<DashboardPoint>,
    val trafficLabel: String,
    val interfaceStats: List<InterfaceStat>
)

object OpnSenseRepository {
    suspend fun fetchDashboard(
        endpoint: String,
        apiKey: String,
        apiSecret: String,
        ignoreInvalidSsl: Boolean
    ): DashboardSnapshot = withContext(Dispatchers.IO) {
        val api = OpnSenseApi(
            endpoint = endpoint,
            apiKey = apiKey,
            apiSecret = apiSecret,
            ignoreInvalidSsl = ignoreInvalidSsl
        )

        val systemInformation = api.getJson(
            "/api/diagnostics/system/system_information",
            "/api/diagnostics/system/systemInformation"
        )
        val systemTime = api.getJson(
            "/api/diagnostics/system/system_time",
            "/api/diagnostics/system/systemTime"
        )
        val systemResources = api.getJson(
            "/api/diagnostics/system/system_resources",
            "/api/diagnostics/system/systemResources"
        )
        val systemDisk = api.getJson(
            "/api/diagnostics/system/system_disk",
            "/api/diagnostics/system/systemDisk"
        )
        val systemSwap = api.getJson(
            "/api/diagnostics/system/system_swap",
            "/api/diagnostics/system/systemSwap"
        )
        val rrdList = api.getJson(
            "/api/diagnostics/systemhealth/get_rrd_list",
            "/api/diagnostics/systemhealth/getRrdList"
        )
        val interfaceOverview = api.getJson("/api/interfaces/overview/export")
        val liveCpuRatio = api.getCpuUsageSample()

        val cpuHealth = fetchHealthSeries(api, rrdList, HealthKind.Cpu)
        val trafficHealth = fetchHealthSeries(api, rrdList, HealthKind.Traffic)

        val memory = systemResources.optJSONObject("memory")
        val memoryTotalMb = memory?.optDouble("total_frmt").takeIf { it != null && !it.isNaN() } ?: 0.0
        val memoryUsedMb = memory?.optDouble("used_frmt").takeIf { it != null && !it.isNaN() } ?: 0.0
        val memoryUsage = ratioOrNull(memoryUsedMb, memoryTotalMb)

        val selectedDisk = selectDisk(systemDisk)
        val diskUsage = parsePercent(selectedDisk?.optString("used_pct"))
        val diskValue = formatPercentValue(diskUsage)
        val diskDetail = buildDiskDetail(selectedDisk)

        val swapAggregate = aggregateUsage(
            value = systemSwap,
            usedKeys = setOf("used"),
            totalKeys = setOf("1k-blocks", "blocks", "total", "size")
        )
        val swapUsage = if (swapAggregate.total > 0.0) {
            (swapAggregate.used / swapAggregate.total).toFloat()
        } else {
            null
        }

        val stats = listOf(
            DashboardStat(
                title = "CPU load",
                value = formatPercentValue(liveCpuRatio ?: cpuHealth.latestRatio),
                detail = cpuHealth.detail.ifBlank { "System health unavailable" },
                usage = liveCpuRatio ?: cpuHealth.latestRatio ?: 0f
            ),
            DashboardStat(
                title = "Memory",
                value = formatPercentValue(memoryUsage),
                detail = if (memoryTotalMb > 0.0) {
                    "${formatMegabytes(memoryUsedMb)} / ${formatMegabytes(memoryTotalMb)}"
                } else {
                    "Memory figures unavailable"
                },
                usage = memoryUsage ?: 0f
            ),
            DashboardStat(
                title = "Disk",
                value = diskValue,
                detail = diskDetail,
                usage = diskUsage ?: 0f
            ),
            DashboardStat(
                title = "Swap",
                value = formatPercentValue(swapUsage),
                detail = if (swapAggregate.total > 0.0) {
                    "${formatKilobytes(swapAggregate.used)} / ${formatKilobytes(swapAggregate.total)}"
                } else {
                    "Swap figures unavailable"
                },
                usage = swapUsage ?: 0f
            )
        )

        DashboardSnapshot(
            endpoint = endpoint.trim().trimEnd('/'),
            systemName = systemInformation.optString("name").ifBlank { endpoint },
            versionSummary = systemInformation.optJSONArray("versions")
                ?.takeIf { it.length() > 0 }
                ?.optString(0)
                .orEmpty(),
            updateHint = systemInformation.optString("updates"),
            uptime = systemTime.optString("uptime").ifBlank { "Unavailable" },
            loadAverage = systemTime.optString("loadavg").ifBlank { "Unavailable" },
            refreshedAt = SimpleDateFormat("HH:mm:ss", Locale.getDefault()).format(Date()),
            cpuCurrentRatio = liveCpuRatio ?: cpuHealth.latestRatio,
            trafficCurrentRatio = trafficHealth.latestRatio,
            stats = stats,
            cpuHistory = cpuHealth.points,
            trafficHistory = trafficHealth.points,
            trafficLabel = trafficHealth.detail.ifBlank { "Interface traffic" },
            interfaceStats = parseInterfaceStats(interfaceOverview)
        )
    }

    suspend fun streamCpuUsage(
        endpoint: String,
        apiKey: String,
        apiSecret: String,
        ignoreInvalidSsl: Boolean,
        onSample: (Float) -> Unit
    ) = withContext(Dispatchers.IO) {
        val api = OpnSenseApi(
            endpoint = endpoint,
            apiKey = apiKey,
            apiSecret = apiSecret,
            ignoreInvalidSsl = ignoreInvalidSsl
        )
        api.streamCpuUsage(onSample)
    }

    suspend fun fetchServices(
        endpoint: String,
        apiKey: String,
        apiSecret: String,
        ignoreInvalidSsl: Boolean
    ): List<ServiceEntry> = withContext(Dispatchers.IO) {
        val api = OpnSenseApi(
            endpoint = endpoint,
            apiKey = apiKey,
            apiSecret = apiSecret,
            ignoreInvalidSsl = ignoreInvalidSsl
        )
        parseServices(api.getJson("/api/core/service/search"))
    }

    suspend fun fetchInterfaces(
        endpoint: String,
        apiKey: String,
        apiSecret: String,
        ignoreInvalidSsl: Boolean
    ): List<InterfaceEntry> = withContext(Dispatchers.IO) {
        val api = OpnSenseApi(
            endpoint = endpoint,
            apiKey = apiKey,
            apiSecret = apiSecret,
            ignoreInvalidSsl = ignoreInvalidSsl
        )
        parseInterfaces(api.getJson("/api/interfaces/overview/export"))
    }

    suspend fun reloadInterface(
        endpoint: String,
        apiKey: String,
        apiSecret: String,
        ignoreInvalidSsl: Boolean,
        identifier: String
    ) = withContext(Dispatchers.IO) {
        val api = OpnSenseApi(
            endpoint = endpoint,
            apiKey = apiKey,
            apiSecret = apiSecret,
            ignoreInvalidSsl = ignoreInvalidSsl
        )
        val safeIdentifier = java.net.URLEncoder.encode(identifier, Charsets.UTF_8.name())
        api.postJson(
            "/api/interfaces/overview/reload_interface/$safeIdentifier",
            "/api/interfaces/overview/reloadInterface/$safeIdentifier"
        )
    }

    suspend fun controlService(
        endpoint: String,
        apiKey: String,
        apiSecret: String,
        ignoreInvalidSsl: Boolean,
        name: String,
        id: String,
        action: ServiceAction
    ) = withContext(Dispatchers.IO) {
        val api = OpnSenseApi(
            endpoint = endpoint,
            apiKey = apiKey,
            apiSecret = apiSecret,
            ignoreInvalidSsl = ignoreInvalidSsl
        )
        val safeName = java.net.URLEncoder.encode(name, Charsets.UTF_8.name())
        val safeId = java.net.URLEncoder.encode(id, Charsets.UTF_8.name())
        if (id.isBlank()) {
            api.postJson("/api/core/service/${action.command}/$safeName")
        } else {
            api.postJson(
                "/api/core/service/${action.command}/$safeName/$safeId",
                "/api/core/service/${action.command}/$safeName/"
            )
        }
    }

    suspend fun fetchFirewallRules(
        endpoint: String,
        apiKey: String,
        apiSecret: String,
        ignoreInvalidSsl: Boolean
    ): List<FirewallRuleEntry> = withContext(Dispatchers.IO) {
        val api = OpnSenseApi(
            endpoint = endpoint,
            apiKey = apiKey,
            apiSecret = apiSecret,
            ignoreInvalidSsl = ignoreInvalidSsl
        )
        parseFirewallRules(
            api.getJson(
                "/api/firewall/filter/search_rule?current=1&rowCount=200",
                "/api/firewall/filter/searchRule?current=1&rowCount=200"
            )
        )
    }

    suspend fun toggleFirewallRule(
        endpoint: String,
        apiKey: String,
        apiSecret: String,
        ignoreInvalidSsl: Boolean,
        uuid: String,
        enabled: Boolean
    ) = withContext(Dispatchers.IO) {
        val api = OpnSenseApi(
            endpoint = endpoint,
            apiKey = apiKey,
            apiSecret = apiSecret,
            ignoreInvalidSsl = ignoreInvalidSsl
        )
        val revision = api.postJson(
            "/api/firewall/filter/savepoint",
            "/api/firewall/filter/savePoint"
        ).optString("revision")
        val flag = if (enabled) "1" else "0"
        api.postJson(
            "/api/firewall/filter/toggle_rule/$uuid/$flag",
            "/api/firewall/filter/toggleRule/$uuid/$flag"
        )
        if (revision.isNotBlank()) {
            api.postJson(
                "/api/firewall/filter/apply/$revision",
                "/api/firewall/filter/apply/$revision/"
            )
            api.postJson(
                "/api/firewall/filter/cancel_rollback/$revision",
                "/api/firewall/filter/cancelRollback/$revision"
            )
        } else {
            api.postJson("/api/firewall/filter/apply")
        }
    }

    suspend fun fetchFirmwareStatus(
        endpoint: String,
        apiKey: String,
        apiSecret: String,
        ignoreInvalidSsl: Boolean
    ): FirmwareStatus = withContext(Dispatchers.IO) {
        val api = OpnSenseApi(
            endpoint = endpoint,
            apiKey = apiKey,
            apiSecret = apiSecret,
            ignoreInvalidSsl = ignoreInvalidSsl
        )
        val status = api.postJson("/api/core/firmware/status")
        val upgrade = api.getJson("/api/core/firmware/upgradestatus")
        parseFirmwareStatus(status, upgrade)
    }

    suspend fun checkForUpdates(
        endpoint: String,
        apiKey: String,
        apiSecret: String,
        ignoreInvalidSsl: Boolean
    ) = withContext(Dispatchers.IO) {
        val api = OpnSenseApi(
            endpoint = endpoint,
            apiKey = apiKey,
            apiSecret = apiSecret,
            ignoreInvalidSsl = ignoreInvalidSsl
        )
        api.postJson("/api/core/firmware/check")
    }

    suspend fun installUpdates(
        endpoint: String,
        apiKey: String,
        apiSecret: String,
        ignoreInvalidSsl: Boolean
    ) = withContext(Dispatchers.IO) {
        val api = OpnSenseApi(
            endpoint = endpoint,
            apiKey = apiKey,
            apiSecret = apiSecret,
            ignoreInvalidSsl = ignoreInvalidSsl
        )
        api.postJson("/api/core/firmware/update")
    }

    suspend fun fetchSystemNotifications(
        endpoint: String,
        apiKey: String,
        apiSecret: String,
        ignoreInvalidSsl: Boolean
    ): List<SystemNotification> = withContext(Dispatchers.IO) {
        val api = OpnSenseApi(
            endpoint = endpoint,
            apiKey = apiKey,
            apiSecret = apiSecret,
            ignoreInvalidSsl = ignoreInvalidSsl
        )
        parseSystemNotifications(api.getJson("/api/core/system/status"))
    }

    suspend fun dismissSystemNotification(
        endpoint: String,
        apiKey: String,
        apiSecret: String,
        ignoreInvalidSsl: Boolean,
        subject: String
    ) = withContext(Dispatchers.IO) {
        val api = OpnSenseApi(
            endpoint = endpoint,
            apiKey = apiKey,
            apiSecret = apiSecret,
            ignoreInvalidSsl = ignoreInvalidSsl
        )
        api.postFormJson(
            "/api/core/system/dismiss_status",
            mapOf("subject" to subject)
        )
    }
}

enum class ServiceAction(val command: String) {
    Start("start"),
    Stop("stop"),
    Restart("restart")
}

private class OpnSenseApi(
    endpoint: String,
    apiKey: String,
    apiSecret: String,
    private val ignoreInvalidSsl: Boolean
) {
    private val normalizedEndpoint = endpoint.trim().trimEnd('/')
    private val authHeader = "Basic " + java.util.Base64.getEncoder()
        .encodeToString("$apiKey:$apiSecret".toByteArray())

    fun getJson(vararg candidatePaths: String): JSONObject {
        return requestJson("GET", *candidatePaths)
    }

    fun postJson(vararg candidatePaths: String): JSONObject {
        return requestJson("POST", *candidatePaths)
    }

    fun postFormJson(path: String, params: Map<String, String>): JSONObject {
        val body = params.entries.joinToString("&") { (key, value) ->
            "${java.net.URLEncoder.encode(key, Charsets.UTF_8.name())}=${
                java.net.URLEncoder.encode(value, Charsets.UTF_8.name())
            }"
        }
        return request("POST", path, body = body, contentType = "application/x-www-form-urlencoded; charset=UTF-8")
    }

    private fun requestJson(method: String, vararg candidatePaths: String): JSONObject {
        var lastError: Exception? = null

        for (path in candidatePaths) {
            try {
                return request(method, path)
            } catch (exception: HttpException) {
                if (exception.statusCode == HttpURLConnection.HTTP_NOT_FOUND) {
                    lastError = exception
                    continue
                }
                throw exception
            } catch (exception: Exception) {
                lastError = exception
            }
        }

        throw lastError ?: IllegalStateException("No API endpoints were available.")
    }

    fun getCpuUsageSample(): Float? {
        return runCatching {
            requestServerSentEvent("/api/diagnostics/cpu_usage/stream")
        }.getOrNull()?.let(::parseCpuUsageEvent)
    }

    suspend fun streamCpuUsage(onSample: (Float) -> Unit) {
        val connection = (URL("$normalizedEndpoint/api/diagnostics/cpu_usage/stream").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", authHeader)
            setRequestProperty("Accept", "text/event-stream")
            connectTimeout = 10_000
            readTimeout = 0
        }
        configureSsl(connection)

        try {
            val status = connection.responseCode
            if (status !in 200..299) {
                val body = readBody(connection, status)
                throw HttpException(statusCode = status, message = extractErrorMessage(body))
            }

            val coroutineContext = currentCoroutineContext()
            connection.inputStream.bufferedReader().use { reader ->
                while (coroutineContext.isActive) {
                    val line = reader.readLine() ?: break
                    if (!line.startsWith("data:")) {
                        continue
                    }
                    parseCpuUsageEvent(line.removePrefix("data:").trim())?.let(onSample)
                }
            }
        } finally {
            connection.disconnect()
        }
    }

    private fun request(
        method: String,
        path: String,
        body: String? = null,
        contentType: String = "application/json; charset=UTF-8"
    ): JSONObject {
        val connection = (URL("$normalizedEndpoint$path").openConnection() as HttpURLConnection).apply {
            requestMethod = method
            setRequestProperty("Authorization", authHeader)
            setRequestProperty("Accept", "application/json")
            if (method == "POST") {
                doOutput = true
                setRequestProperty("Content-Type", contentType)
            }
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        configureSsl(connection)

        return try {
            if (method == "POST") {
                connection.outputStream.use { output ->
                    output.write((body ?: "{}").toByteArray())
                }
            }
            val status = connection.responseCode
            val body = readBody(connection, status)

            if (status !in 200..299) {
                throw HttpException(
                    statusCode = status,
                    message = extractErrorMessage(body)
                )
            }

            parseJson(body)
        } finally {
            connection.disconnect()
        }
    }

    private fun requestServerSentEvent(path: String): String {
        val connection = (URL("$normalizedEndpoint$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("Authorization", authHeader)
            setRequestProperty("Accept", "text/event-stream")
            connectTimeout = 10_000
            readTimeout = 10_000
        }
        configureSsl(connection)

        return try {
            val status = connection.responseCode
            if (status !in 200..299) {
                val body = readBody(connection, status)
                throw HttpException(statusCode = status, message = extractErrorMessage(body))
            }

            connection.inputStream.bufferedReader().use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    if (line.startsWith("data:")) {
                        return line.removePrefix("data:").trim()
                    }
                }
            }
            ""
        } finally {
            connection.disconnect()
        }
    }

    private fun configureSsl(connection: HttpURLConnection) {
        if (!ignoreInvalidSsl || connection !is HttpsURLConnection) {
            return
        }

        connection.hostnameVerifier = TRUST_ALL_HOSTS
        connection.sslSocketFactory = INSECURE_SSL_CONTEXT.socketFactory
    }

    private fun readBody(connection: HttpURLConnection, status: Int): String {
        val stream = if (status in 200..299) {
            connection.inputStream
        } else {
            connection.errorStream
        } ?: return ""

        return stream.bufferedReader().use(BufferedReader::readText)
    }

    companion object {
        private val INSECURE_SSL_CONTEXT: SSLContext by lazy {
            SSLContext.getInstance("TLS").apply {
                init(null, arrayOf(TRUST_ALL_CERTIFICATES), java.security.SecureRandom())
            }
        }

        private val TRUST_ALL_CERTIFICATES = object : X509TrustManager {
            override fun checkClientTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) = Unit

            override fun checkServerTrusted(chain: Array<out java.security.cert.X509Certificate>?, authType: String?) = Unit

            override fun getAcceptedIssuers(): Array<java.security.cert.X509Certificate> = emptyArray()
        }

        private val TRUST_ALL_HOSTS = HostnameVerifier { _, _ -> true }
    }
}

private sealed interface HealthKind {
    data object Cpu : HealthKind
    data object Traffic : HealthKind
}

private data class HealthSeries(
    val points: List<DashboardPoint>,
    val latestRatio: Float?,
    val detail: String
)

private data class UsageAggregate(
    val used: Double,
    val total: Double
)

private class HttpException(
    val statusCode: Int,
    override val message: String
) : Exception(message)

private fun fetchHealthSeries(
    api: OpnSenseApi,
    rrdList: JSONObject,
    kind: HealthKind
): HealthSeries {
    val selection = selectRrd(rrdList, kind) ?: return HealthSeries(
        points = emptyList(),
        latestRatio = null,
        detail = "Metric not exposed by this router"
    )
    val healthJson = api.getJson(
        "/api/diagnostics/systemhealth/get_system_health/${selection.identifier}/0",
        "/api/diagnostics/systemhealth/getSystemHealth/${selection.identifier}/0"
    )
    val records = healthJson.optJSONObject("set")
        ?.optJSONArray("data")
        ?: return HealthSeries(emptyList(), null, selection.label)

    val values = when (kind) {
        HealthKind.Cpu -> extractCpuSeries(records)
        HealthKind.Traffic -> extractTrafficSeries(records)
    }
    if (values.isEmpty()) {
        return HealthSeries(emptyList(), null, selection.label)
    }

    val labels = extractLabels(records)
    val sampledPoints = samplePoints(values, labels, kind)
    return HealthSeries(
        points = sampledPoints,
        latestRatio = latestRatio(values, kind),
        detail = selection.label
    )
}

private data class RrdSelection(
    val identifier: String,
    val label: String
)

private fun selectRrd(rrdList: JSONObject, kind: HealthKind): RrdSelection? {
    val data = rrdList.optJSONObject("data") ?: return null
    val topics = data.keys().asSequence().toList()
    val preferredTopics = when (kind) {
        HealthKind.Cpu -> listOf("system")
        HealthKind.Traffic -> listOf("traffic")
    }
    val preferredItems = when (kind) {
        HealthKind.Cpu -> listOf("processor", "cpu")
        HealthKind.Traffic -> listOf("wan", "pppoe", "igb0", "em0")
    }

    val orderedTopics = topics.sortedByDescending { topic ->
        scoreMatch(topic, preferredTopics)
    }

    for (topic in orderedTopics) {
        val items = extractItems(data.opt(topic))
        val selectedItem = items.maxByOrNull { scoreMatch(it, preferredItems) }
            ?.takeIf { scoreMatch(it, preferredItems) > 0 || kind == HealthKind.Traffic && items.isNotEmpty() }
            ?: if (kind == HealthKind.Traffic) items.firstOrNull() else null

        if (selectedItem != null && scoreMatch(topic, preferredTopics) > 0) {
            return RrdSelection(
                identifier = "$selectedItem-$topic",
                label = when (kind) {
                    HealthKind.Cpu -> "CPU history"
                    HealthKind.Traffic -> "${selectedItem.uppercase(Locale.getDefault())} traffic"
                }
            )
        }
    }

    return null
}

private fun extractItems(value: Any?): List<String> = when (value) {
    is JSONArray -> buildList {
        for (index in 0 until value.length()) {
            val item = value.optString(index)
            if (item.isNotBlank()) {
                add(item)
            }
        }
    }

    is JSONObject -> value.keys().asSequence().toList()
    else -> emptyList()
}

private fun extractCpuSeries(records: JSONArray): List<Double> {
    val idleSeries = extractSeriesByKey(records, listOf("idle"))
    if (idleSeries.isNotEmpty()) {
        return idleSeries.map { (100.0 - it).coerceIn(0.0, 100.0) }
    }

    val primarySeries = extractSeriesByKey(records, listOf("user", "usage", "value", "used", "cpu"))
    if (primarySeries.isNotEmpty()) {
        return primarySeries.map { it.coerceIn(0.0, 100.0) }
    }

    return mergeSeries(records, sumValues = true).map { it.coerceIn(0.0, 100.0) }
}

private fun extractTrafficSeries(records: JSONArray): List<Double> = mergeSeries(records, sumValues = true)

private fun extractSeriesByKey(records: JSONArray, preferredKeys: List<String>): List<Double> {
    for (keyHint in preferredKeys) {
        for (index in 0 until records.length()) {
            val record = records.optJSONObject(index) ?: continue
            val key = record.optString("key").lowercase(Locale.getDefault())
            if (key.contains(keyHint)) {
                return extractValues(record.optJSONArray("values"))
            }
        }
    }

    return emptyList()
}

private fun mergeSeries(records: JSONArray, sumValues: Boolean): List<Double> {
    val series = mutableListOf<List<Double>>()
    for (index in 0 until records.length()) {
        val record = records.optJSONObject(index) ?: continue
        val values = extractValues(record.optJSONArray("values"))
        if (values.isNotEmpty()) {
            series += values
        }
    }

    if (series.isEmpty()) {
        return emptyList()
    }

    val minSize = series.minOf { it.size }
    return List(minSize) { pointIndex ->
        val bucket = series.map { it[pointIndex] }
        if (sumValues) bucket.sum() else bucket.average()
    }
}

private fun extractValues(valuesArray: JSONArray?): List<Double> {
    if (valuesArray == null) {
        return emptyList()
    }

    return buildList {
        for (index in 0 until valuesArray.length()) {
            val row = valuesArray.optJSONArray(index) ?: continue
            val numericValue = row.optDouble(1)
            if (!numericValue.isNaN()) {
                add(numericValue)
            }
        }
    }
}

private fun extractLabels(records: JSONArray): List<String> {
    val first = records.optJSONObject(0)?.optJSONArray("values") ?: return emptyList()
    val formatter = SimpleDateFormat("HH:mm", Locale.getDefault())
    return buildList {
        for (index in 0 until first.length()) {
            val row = first.optJSONArray(index) ?: continue
            val timestamp = row.optLong(0)
            if (timestamp > 0L) {
                add(formatter.format(Date(timestamp)))
            }
        }
    }
}

private fun samplePoints(
    values: List<Double>,
    labels: List<String>,
    kind: HealthKind
): List<DashboardPoint> {
    if (values.isEmpty()) {
        return emptyList()
    }

    val normalized = normalizeValues(values, kind)
    val sampleSize = minOf(6, normalized.size)
    val startIndex = normalized.size - sampleSize
    return (startIndex until normalized.size).map { index ->
        DashboardPoint(
            label = labels.getOrElse(index) { "" },
            value = normalized[index]
        )
    }
}

private fun normalizeValues(values: List<Double>, kind: HealthKind): List<Float> {
    return when (kind) {
        HealthKind.Cpu -> values.map { normalizePercentValue(it) }
        HealthKind.Traffic -> {
            val maxValue = values.maxOrNull()?.takeIf { it > 0.0 } ?: return values.map { 0f }
            values.map { (it / maxValue).toFloat().coerceIn(0f, 1f) }
        }
    }
}

private fun latestRatio(values: List<Double>, kind: HealthKind): Float? {
    val latest = values.lastOrNull() ?: return null
    return when (kind) {
        HealthKind.Cpu -> (latest / 100.0).toFloat().coerceIn(0f, 1f)
        HealthKind.Traffic -> {
            val peak = values.maxOrNull()?.takeIf { it > 0.0 } ?: return 0f
            (latest / peak).toFloat().coerceIn(0f, 1f)
        }
    }
}

private fun selectDisk(systemDisk: JSONObject): JSONObject? {
    val devices = systemDisk.optJSONArray("devices") ?: return null
    var fallback: JSONObject? = null

    for (index in 0 until devices.length()) {
        val device = devices.optJSONObject(index) ?: continue
        if (device.optString("mountpoint") == "/") {
            return device
        }
        if (fallback == null) {
            fallback = device
        }
    }

    return fallback
}

private fun buildDiskDetail(device: JSONObject?): String {
    if (device == null) {
        return "Disk figures unavailable"
    }

    val mountpoint = device.optString("mountpoint").ifBlank { "unknown mount" }
    val available = parseDouble(device.optString("available"))
    val total = parseDouble(device.optString("blocks"))
    return if (total > 0.0) {
        "${formatKilobytes(total - available)} used on $mountpoint"
    } else {
        "Mounted at $mountpoint"
    }
}

private fun aggregateUsage(
    value: Any?,
    usedKeys: Set<String>,
    totalKeys: Set<String>
): UsageAggregate {
    var used = 0.0
    var total = 0.0

    fun walk(node: Any?) {
        when (node) {
            is JSONObject -> {
                val localUsed = firstNumericValue(node, usedKeys)
                val localTotal = firstNumericValue(node, totalKeys)
                if (localUsed != null && localTotal != null) {
                    used += localUsed
                    total += localTotal
                }
                node.keys().forEach { key -> walk(node.opt(key)) }
            }

            is JSONArray -> {
                for (index in 0 until node.length()) {
                    walk(node.opt(index))
                }
            }
        }
    }

    walk(value)
    return UsageAggregate(used = used, total = total)
}

private fun firstNumericValue(node: JSONObject, keys: Set<String>): Double? {
    for (key in keys) {
        if (node.has(key)) {
            return parseDouble(node.opt(key))
        }
    }
    return null
}

private fun parseJson(body: String): JSONObject {
    val trimmed = body.trim()
    return when {
        trimmed.isBlank() -> JSONObject()
        trimmed.startsWith("{") -> JSONObject(trimmed)
        trimmed.startsWith("[") -> JSONObject().put("items", JSONArray(trimmed))
        else -> JSONObject().put("raw", trimmed)
    }
}

private fun extractErrorMessage(body: String): String {
    val trimmed = body.trim()
    if (trimmed.isBlank()) {
        return "The router returned an empty response."
    }

    return runCatching {
        val json = parseJson(trimmed)
        json.optString("message")
            .ifBlank { json.optString("error") }
            .ifBlank { trimmed }
    }.getOrDefault(trimmed)
}

private fun parseCpuUsageEvent(rawEvent: String): Float? {
    if (rawEvent.isBlank()) {
        return null
    }

    val json = JSONObject(rawEvent)
    val idle = firstFiniteValue(json, listOf("idle"))
    if (idle != null) {
        return normalizePercentValue(100.0 - idle)
    }

    val total = firstFiniteValue(json, listOf("total", "usage", "used", "cpu"))
    if (total != null) {
        return normalizePercentValue(total)
    }

    val summed = listOf("user", "nice", "system", "sys", "interrupt", "intr")
        .mapNotNull { key ->
            firstFiniteValue(json, listOf(key))
        }
        .sum()
    return if (summed > 0.0) {
        normalizePercentValue(summed)
    } else {
        null
    }
}

private fun firstFiniteValue(json: JSONObject, keys: List<String>): Double? {
    for (key in keys) {
        val value = json.optDouble(key)
        if (!value.isNaN()) {
            return value
        }
    }
    return null
}

private fun normalizePercentValue(value: Double): Float {
    return if (value <= 1.0) {
        value.toFloat().coerceIn(0f, 1f)
    } else {
        (value / 100.0).toFloat().coerceIn(0f, 1f)
    }
}

private fun parseServices(json: JSONObject): List<ServiceEntry> {
    val rows = extractServiceRows(json)
    return rows.mapNotNull(::parseServiceEntry)
        .sortedWith(compareBy<ServiceEntry> { !it.isRunning }.thenBy { it.name.lowercase(Locale.getDefault()) })
}

private fun parseFirewallRules(json: JSONObject): List<FirewallRuleEntry> {
    val rows = json.optJSONArray("rows")?.let(::jsonArrayObjects).orEmpty()
    return rows.mapNotNull { row ->
        val uuid = firstNonBlank(row, listOf("uuid")) ?: return@mapNotNull null
        FirewallRuleEntry(
            uuid = uuid,
            description = firstNonBlank(row, listOf("description")).orEmpty().ifBlank { "Unnamed rule" },
            action = firstNonBlank(row, listOf("action")).orEmpty().ifBlank { "pass" },
            interfaceName = firstNonBlank(row, listOf("interface")).orEmpty().ifBlank { "floating" },
            protocol = firstNonBlank(row, listOf("protocol")).orEmpty().ifBlank { "any" },
            source = firstNonBlank(row, listOf("source_net", "source")).orEmpty().ifBlank { "any" },
            destination = firstNonBlank(row, listOf("destination_net", "destination")).orEmpty().ifBlank { "any" },
            isEnabled = inferEnabled(row) ?: true
        )
    }.sortedWith(compareBy<FirewallRuleEntry> { !it.isEnabled }.thenBy { it.interfaceName }.thenBy { it.description.lowercase(Locale.getDefault()) })
}

private fun parseFirmwareStatus(
    status: JSONObject,
    upgrade: JSONObject
): FirmwareStatus {
    val updatePackages = parseFirmwarePackageList(status)
    val updatesSummary = parseFirmwareUpdatesSummary(status, updatePackages)
    return FirmwareStatus(
        productVersion = firstNonBlank(status, listOf("product_version", "productVersion", "series")) ?: "Unknown",
        latestVersion = firstNonBlank(status, listOf("product_latest", "productLatest", "new_version")) ?: "Unknown",
        statusMessage = firstNonBlank(status, listOf("status_msg", "status", "message")) ?: "No firmware status returned.",
        updatesAvailable = updatesSummary,
        updatePackages = updatePackages,
        downloadSize = firstNonBlank(status, listOf("download_size", "downloadSize")) ?: "Unknown",
        needsReboot = firstNonBlank(status, listOf("upgrade_needs_reboot", "needs_reboot")) == "1",
        lastCheck = firstNonBlank(status, listOf("last_check")) ?: "Unknown",
        upgradeStatus = firstNonBlank(upgrade, listOf("status", "message", "log")) ?: "Idle"
    )
}

private fun parseFirmwareUpdatesSummary(
    status: JSONObject,
    updatePackages: List<String>
): String {
    val rawUpdates = status.opt("updates")
    val numericString = when (rawUpdates) {
        is Number -> rawUpdates.toInt().toString()
        is String -> rawUpdates.trim().takeIf { it.toIntOrNull() != null }
        else -> null
    }
    if (numericString != null) {
        return numericString
    }
    return if (updatePackages.isNotEmpty()) {
        updatePackages.size.toString()
    } else {
        "0"
    }
}

private fun parseFirmwarePackageList(status: JSONObject): List<String> {
    val upgradePackages = status.opt("upgrade_packages")
    val parsed = parsePackageEntries(upgradePackages)
    if (parsed.isNotEmpty()) {
        return parsed
    }
    val updates = status.opt("updates")
    if (updates is String && updates.isNotBlank()) {
        return parsePackageEntriesFromString(updates)
    }
    return emptyList()
}

private fun parsePackageEntries(value: Any?): List<String> = when (value) {
    is JSONArray -> (0 until value.length()).mapNotNull { index ->
        formatPackageEntry(value.opt(index))
    }
    is JSONObject -> {
        val packageRows = value.optJSONArray("packages")
        if (packageRows != null) {
            parsePackageEntries(packageRows)
        } else {
            value.keys().asSequence().mapNotNull { key ->
                val entry = value.opt(key)
                formatPackageEntry(entry, fallbackName = key)
            }.toList()
        }
    }
    is String -> parsePackageEntriesFromString(value)
    else -> emptyList()
}

private fun parsePackageEntriesFromString(raw: String): List<String> {
    val trimmed = raw.trim()
    if (trimmed.isEmpty()) {
        return emptyList()
    }
    return runCatching {
        when {
            trimmed.startsWith("{") -> parsePackageEntries(JSONObject(trimmed))
            trimmed.startsWith("[") -> parsePackageEntries(JSONArray(trimmed))
            else -> emptyList()
        }
    }.getOrDefault(emptyList())
}

private fun formatPackageEntry(
    value: Any?,
    fallbackName: String? = null
): String? = when (value) {
    is JSONObject -> {
        val name = firstNonBlank(value, listOf("name", "pkgname", "package", "product", "title"))
            ?: fallbackName
            ?: return null
        val current = firstNonBlank(value, listOf("current_version", "current", "old", "installed"))
        val next = firstNonBlank(value, listOf("new_version", "new", "target", "version"))
        when {
            !current.isNullOrBlank() && !next.isNullOrBlank() -> "$name $current -> $next"
            !next.isNullOrBlank() -> "$name -> $next"
            else -> name
        }
    }
    is JSONArray -> value.optString(0).takeIf { it.isNotBlank() }
    is String -> value.takeIf { it.isNotBlank() }
    else -> fallbackName
}

private fun parseSystemNotifications(json: JSONObject): List<SystemNotification> {
    val subsystems = json.optJSONObject("subsystems") ?: return emptyList()
    return subsystems.keys().asSequence().mapNotNull { subject ->
        val item = subsystems.optJSONObject(subject) ?: return@mapNotNull null
        SystemNotification(
            subject = subject,
            title = firstNonBlank(item, listOf("title")) ?: subject,
            message = firstNonBlank(item, listOf("message")) ?: "No details available.",
            age = firstNonBlank(item, listOf("age")) ?: "Unknown",
            statusCode = firstNonBlank(item, listOf("status")) ?: "unknown",
            location = firstNonBlank(item, listOf("location")) ?: ""
        )
    }.sortedBy { notificationPriority(it.statusCode) }
        .toList()
}

private fun notificationPriority(statusCode: String): Int = when (statusCode.lowercase(Locale.getDefault())) {
    "error", "danger", "2" -> 0
    "warning", "warn", "1" -> 1
    "info", "notice" -> 2
    else -> 3
}

private fun parseInterfaces(json: JSONObject): List<InterfaceEntry> {
    val rows = extractInterfaceRows(json)
    return rows.mapNotNull { row ->
        val identifier = firstNonBlank(row, listOf("identifier")) ?: return@mapNotNull null
        val stats = row.optJSONObject("statistics")
        InterfaceEntry(
            identifier = identifier,
            name = identifier.uppercase(Locale.getDefault()),
            description = firstNonBlank(row, listOf("description", "name")).orEmpty().ifBlank { identifier.uppercase(Locale.getDefault()) },
            device = firstNonBlank(row, listOf("device")).orEmpty(),
            statusLabel = firstNonBlank(row, listOf("status", "link state")).orEmpty().ifBlank { "Unknown" },
            linkType = firstNonBlank(row, listOf("link_type", "type")).orEmpty().ifBlank { "none" },
            ipv4 = firstNonBlank(row, listOf("addr4", "ipaddr")).orEmpty().ifBlank { "-" },
            ipv6 = firstNonBlank(row, listOf("addr6")).orEmpty().ifBlank { "-" },
            inRate = formatInterfaceCounter(
                bytesValue = firstNonBlank(stats, listOf("bytes received")),
                fallbackValue = firstNonBlank(stats, listOf("packets received", "input errors"))
            ),
            outRate = formatInterfaceCounter(
                bytesValue = firstNonBlank(stats, listOf("bytes transmitted")),
                fallbackValue = firstNonBlank(stats, listOf("packets transmitted", "output errors"))
            ),
            isEnabled = inferEnabled(row) ?: false
        )
    }.sortedBy { it.name.lowercase(Locale.getDefault()) }
}

private fun parseInterfaceStats(json: JSONObject): List<InterfaceStat> {
    val rows = extractInterfaceRows(json)

    return rows.mapNotNull { row ->
        val stats = row.optJSONObject("statistics")
        val name = firstNonBlank(row, listOf("identifier", "name", "description")) ?: return@mapNotNull null
        InterfaceStat(
            name = name,
            device = firstNonBlank(row, listOf("device", "interface", "if")).orEmpty(),
            statusLabel = firstNonBlank(row, listOf("status", "link_state")).orEmpty().ifBlank { "Unknown" },
            address = firstNonBlank(row, listOf("addr4", "addr6", "ipaddr", "address")).orEmpty().ifBlank { "No address" },
            inRate = formatInterfaceCounter(
                bytesValue = firstNonBlank(stats, listOf("bytes received")),
                fallbackValue = firstNonBlank(stats, listOf("packets received", "input errors"))
                    ?: firstNonBlank(row, listOf("inpkts", "inbytes_frmt", "inbytes", "inerrs"))
            ),
            outRate = formatInterfaceCounter(
                bytesValue = firstNonBlank(stats, listOf("bytes transmitted")),
                fallbackValue = firstNonBlank(stats, listOf("packets transmitted", "output errors"))
                    ?: firstNonBlank(row, listOf("outpkts", "outbytes_frmt", "outbytes", "outerrs"))
            )
        )
    }.take(4)
}

private fun extractInterfaceRows(json: JSONObject): List<JSONObject> {
    return when (val items = json.opt("items")) {
        is JSONArray -> jsonArrayObjects(items)
        else -> emptyList()
    }.ifEmpty {
        (json.optJSONArray("rows") ?: JSONArray()).let(::jsonArrayObjects)
    }.ifEmpty {
        (json.optJSONArray("interfaces") ?: JSONArray()).let(::jsonArrayObjects)
    }.ifEmpty {
        runCatching { JSONArray(json.toString()) }.getOrNull()?.let(::jsonArrayObjects).orEmpty()
    }
}

private fun extractServiceRows(json: JSONObject): List<JSONObject> {
    val candidates = listOf("rows", "row", "items", "data", "services")
    for (key in candidates) {
        when (val value = json.opt(key)) {
            is JSONArray -> return jsonArrayObjects(value)
            is JSONObject -> {
                for (nestedKey in candidates) {
                    val nested = value.optJSONArray(nestedKey)
                    if (nested != null) {
                        return jsonArrayObjects(nested)
                    }
                }
            }
        }
    }
    return emptyList()
}

private fun jsonArrayObjects(array: JSONArray): List<JSONObject> = buildList {
    for (index in 0 until array.length()) {
        array.optJSONObject(index)?.let(::add)
    }
}

private fun parseServiceEntry(row: JSONObject): ServiceEntry? {
    val name = firstNonBlank(row, listOf("name", "service", "daemon", "label")) ?: return null
    val id = firstNonBlank(row, listOf("id", "service_id")).orEmpty()
    val description = firstNonBlank(row, listOf("description", "desc", "summary", "label")).orEmpty()
    val statusLabel = firstNonBlank(row, listOf("status", "running", "state", "message")).orEmpty()
    val isRunning = inferRunning(row, statusLabel)
    val isEnabled = inferEnabled(row)
    val serviceId = when {
        id.isBlank() -> ""
        id == name -> ""
        id.startsWith("$name/") -> id.removePrefix("$name/")
        else -> id
    }
    val isLocked = when (val raw = row.opt("locked")) {
        is Boolean -> raw
        is Number -> raw.toInt() != 0
        is String -> raw == "1" || raw.equals("true", ignoreCase = true)
        else -> false
    }

    return ServiceEntry(
        id = id,
        serviceId = serviceId,
        name = name,
        description = description.ifBlank { name },
        statusLabel = statusLabel.ifBlank { if (isRunning) "Running" else "Stopped" },
        isRunning = isRunning,
        isEnabled = isEnabled,
        isLocked = isLocked
    )
}

private fun firstNonBlank(json: JSONObject?, keys: List<String>): String? {
    if (json == null) {
        return null
    }
    for (key in keys) {
        val value = json.optString(key)
        if (value.isNotBlank() && value.lowercase(Locale.getDefault()) != "null") {
            return value
        }
    }
    return null
}

private fun formatInterfaceCounter(
    bytesValue: String?,
    fallbackValue: String?
): String {
    bytesValue?.let { raw ->
        raw.toDoubleOrNull()?.let { numeric ->
            return formatBytes(numeric)
        }
        if (raw.isNotBlank()) {
            return raw
        }
    }
    return fallbackValue ?: "N/A"
}

private fun formatBytes(value: Double): String {
    val units = listOf("B", "KB", "MB", "GB", "TB", "PB")
    var size = value
    var unitIndex = 0
    while (size >= 1024.0 && unitIndex < units.lastIndex) {
        size /= 1024.0
        unitIndex++
    }
    val pattern = if (size >= 100) "#,##0" else "#,##0.0"
    return "${DecimalFormat(pattern).format(size)} ${units[unitIndex]}"
}

private fun inferRunning(json: JSONObject, statusLabel: String): Boolean {
    for (key in listOf("running", "is_running", "status")) {
        when (val raw = json.opt(key)) {
            is Boolean -> return raw
            is Number -> return raw.toInt() != 0
            is String -> when (raw.lowercase(Locale.getDefault())) {
                "1", "true", "yes", "running", "started", "active", "ok" -> return true
                "0", "false", "no", "stopped", "inactive", "down" -> return false
            }
        }
    }

    val normalized = statusLabel.lowercase(Locale.getDefault())
    return listOf("running", "started", "active", "up").any(normalized::contains)
}

private fun inferEnabled(json: JSONObject): Boolean? {
    for (key in listOf("enabled", "is_enabled")) {
        when (val raw = json.opt(key)) {
            is Boolean -> return raw
            is Number -> return raw.toInt() != 0
            is String -> when (raw.lowercase(Locale.getDefault())) {
                "1", "true", "yes", "enabled" -> return true
                "0", "false", "no", "disabled" -> return false
            }
        }
    }
    return null
}

private fun parsePercent(raw: String?): Float? {
    val numeric = parseDouble(raw)
    return if (numeric >= 1.0) {
        (numeric / 100.0).toFloat().coerceIn(0f, 1f)
    } else if (numeric >= 0.0) {
        numeric.toFloat().coerceIn(0f, 1f)
    } else {
        null
    }
}

private fun parseDouble(raw: Any?): Double {
    return when (raw) {
        is Number -> raw.toDouble()
        is String -> raw.replace("%", "").replace(",", "").trim().toDoubleOrNull() ?: -1.0
        else -> -1.0
    }
}

private fun ratioOrNull(used: Double, total: Double): Float? {
    return if (used >= 0.0 && total > 0.0) {
        (used / total).toFloat().coerceIn(0f, 1f)
    } else {
        null
    }
}

private fun formatPercentValue(ratio: Float?): String {
    return if (ratio == null) {
        "N/A"
    } else {
        val percent = ratio * 100f
        if (percent >= 99.95f) {
            "100%"
        } else {
            "${DecimalFormat("0.00").format(percent.toDouble())}%"
        }
    }
}

private fun formatMegabytes(value: Double): String = "${DecimalFormat("#,##0").format(value)} MB"

private fun formatKilobytes(value: Double): String {
    val bytes = value * 1024.0
    val units = listOf("B", "KB", "MB", "GB", "TB")
    var size = bytes
    var unitIndex = 0
    while (size >= 1024 && unitIndex < units.lastIndex) {
        size /= 1024
        unitIndex++
    }
    return "${DecimalFormat("#,##0.#").format(size)} ${units[unitIndex]}"
}

private fun scoreMatch(candidate: String, hints: List<String>): Int {
    val normalized = candidate.lowercase(Locale.getDefault())
    return hints.withIndex().maxOfOrNull { (index, hint) ->
        when {
            normalized == hint -> 100 - index
            normalized.contains(hint) -> 50 - index
            else -> 0
        }
    } ?: 0
}
