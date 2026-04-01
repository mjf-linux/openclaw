package ai.openclaw.app.node

import android.content.Context
import android.os.Build
import ai.openclaw.app.gateway.GatewaySession
import androidx.health.connect.client.HealthConnectClient
import androidx.health.connect.client.permission.HealthPermission
import androidx.health.connect.client.records.WeightRecord
import androidx.health.connect.client.records.BodyFatRecord
import androidx.health.connect.client.records.NutritionRecord
import androidx.health.connect.client.records.StepsRecord
import androidx.health.connect.client.request.ReadRecordsRequest
import androidx.health.connect.client.time.TimeRangeFilter
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.put
import java.time.Instant
import java.time.temporal.ChronoUnit

internal data class HealthRecordsRequest(
  val startISO: String?,
  val endISO: String?,
  val limit: Int,
)

internal interface HealthConnectDataSource {
  fun isAvailable(context: Context): Boolean
  suspend fun hasPermissions(context: Context): Boolean
  suspend fun readWeight(context: Context, request: HealthRecordsRequest): List<WeightEntry>
  suspend fun readBodyFat(context: Context, request: HealthRecordsRequest): List<BodyFatEntry>
  suspend fun readNutrition(context: Context, request: HealthRecordsRequest): List<NutritionEntry>
  suspend fun readSteps(context: Context, request: HealthRecordsRequest): List<StepsEntry>
}

internal data class WeightEntry(
  val timeISO: String,
  val weightKg: Double,
  val weightLbs: Double,
)

internal data class BodyFatEntry(
  val timeISO: String,
  val percentage: Double,
)

internal data class NutritionEntry(
  val startISO: String,
  val endISO: String,
  val name: String?,
  val calories: Double?,
  val proteinG: Double?,
  val fatG: Double?,
  val carbsG: Double?,
)

internal data class StepsEntry(
  val startISO: String,
  val endISO: String,
  val count: Long,
)

private object SystemHealthConnectDataSource : HealthConnectDataSource {
  private val PERMISSIONS = setOf(
    HealthPermission.getReadPermission(WeightRecord::class),
    HealthPermission.getReadPermission(BodyFatRecord::class),
    HealthPermission.getReadPermission(NutritionRecord::class),
    HealthPermission.getReadPermission(StepsRecord::class),
  )

  override fun isAvailable(context: Context): Boolean {
    return HealthConnectClient.getSdkStatus(context) == HealthConnectClient.SDK_AVAILABLE
  }

  override suspend fun hasPermissions(context: Context): Boolean {
    if (!isAvailable(context)) return false
    val client = HealthConnectClient.getOrCreate(context)
    val granted = client.permissionController.getGrantedPermissions()
    // Return true if ANY health permission is granted (partial access is okay)
    return granted.any { it in PERMISSIONS }
  }

  override suspend fun readWeight(context: Context, request: HealthRecordsRequest): List<WeightEntry> {
    val client = HealthConnectClient.getOrCreate(context)
    val (start, end) = resolveTimeRange(request)
    val response = client.readRecords(
      ReadRecordsRequest(
        recordType = WeightRecord::class,
        timeRangeFilter = TimeRangeFilter.between(start, end),
      ),
    )
    return response.records
      .sortedByDescending { it.time }
      .take(request.limit)
      .map { record ->
        val kg = record.weight.inKilograms
        WeightEntry(
          timeISO = record.time.toString(),
          weightKg = Math.round(kg * 100.0) / 100.0,
          weightLbs = Math.round(kg * 2.20462 * 100.0) / 100.0,
        )
      }
  }

  override suspend fun readBodyFat(context: Context, request: HealthRecordsRequest): List<BodyFatEntry> {
    val client = HealthConnectClient.getOrCreate(context)
    val (start, end) = resolveTimeRange(request)
    val response = client.readRecords(
      ReadRecordsRequest(
        recordType = BodyFatRecord::class,
        timeRangeFilter = TimeRangeFilter.between(start, end),
      ),
    )
    return response.records
      .sortedByDescending { it.time }
      .take(request.limit)
      .map { record ->
        BodyFatEntry(
          timeISO = record.time.toString(),
          percentage = Math.round(record.percentage.value * 100.0) / 100.0,
        )
      }
  }

  override suspend fun readNutrition(context: Context, request: HealthRecordsRequest): List<NutritionEntry> {
    val client = HealthConnectClient.getOrCreate(context)
    val (start, end) = resolveTimeRange(request)
    val response = client.readRecords(
      ReadRecordsRequest(
        recordType = NutritionRecord::class,
        timeRangeFilter = TimeRangeFilter.between(start, end),
      ),
    )
    return response.records
      .sortedByDescending { it.startTime }
      .take(request.limit)
      .map { record ->
        NutritionEntry(
          startISO = record.startTime.toString(),
          endISO = record.endTime.toString(),
          name = record.name,
          calories = record.energy?.inKilocalories,
          proteinG = record.protein?.inGrams,
          fatG = record.totalFat?.inGrams,
          carbsG = record.totalCarbohydrate?.inGrams,
        )
      }
  }

  override suspend fun readSteps(context: Context, request: HealthRecordsRequest): List<StepsEntry> {
    val client = HealthConnectClient.getOrCreate(context)
    val (start, end) = resolveTimeRange(request)
    val response = client.readRecords(
      ReadRecordsRequest(
        recordType = StepsRecord::class,
        timeRangeFilter = TimeRangeFilter.between(start, end),
      ),
    )
    return response.records
      .sortedByDescending { it.startTime }
      .take(request.limit)
      .map { record ->
        StepsEntry(
          startISO = record.startTime.toString(),
          endISO = record.endTime.toString(),
          count = record.count,
        )
      }
  }

  private fun resolveTimeRange(request: HealthRecordsRequest): Pair<Instant, Instant> {
    val end = if (request.endISO != null) Instant.parse(request.endISO) else Instant.now()
    val start = if (request.startISO != null) Instant.parse(request.startISO) else end.minus(30, ChronoUnit.DAYS)
    return Pair(start, end)
  }
}

class HealthConnectHandler private constructor(
  private val appContext: Context,
  private val dataSource: HealthConnectDataSource,
) {
  constructor(appContext: Context) : this(appContext = appContext, dataSource = SystemHealthConnectDataSource)

  suspend fun handleHealthWeight(paramsJson: String?): GatewaySession.InvokeResult {
    if (!dataSource.isAvailable(appContext)) {
      return GatewaySession.InvokeResult.error(
        code = "HEALTH_CONNECT_UNAVAILABLE",
        message = "HEALTH_CONNECT_UNAVAILABLE: Health Connect not available on this device",
      )
    }
    if (!dataSource.hasPermissions(appContext)) {
      return GatewaySession.InvokeResult.error(
        code = "HEALTH_CONNECT_PERMISSION_REQUIRED",
        message = "HEALTH_CONNECT_PERMISSION_REQUIRED: grant Health Connect permissions in Settings",
      )
    }
    val request = parseRequest(paramsJson)
      ?: return GatewaySession.InvokeResult.error(
        code = "INVALID_REQUEST",
        message = "INVALID_REQUEST: expected JSON object",
      )
    return try {
      val entries = dataSource.readWeight(appContext, request)
      GatewaySession.InvokeResult.ok(
        buildJsonObject {
          put("records", buildJsonArray {
            entries.forEach { entry ->
              add(buildJsonObject {
                put("timeISO", JsonPrimitive(entry.timeISO))
                put("weightKg", JsonPrimitive(entry.weightKg))
                put("weightLbs", JsonPrimitive(entry.weightLbs))
              })
            }
          })
          put("count", JsonPrimitive(entries.size))
        }.toString(),
      )
    } catch (err: Throwable) {
      GatewaySession.InvokeResult.error(
        code = "HEALTH_CONNECT_ERROR",
        message = "HEALTH_CONNECT_ERROR: ${err.message ?: "failed to read weight"}",
      )
    }
  }

  suspend fun handleHealthBodyFat(paramsJson: String?): GatewaySession.InvokeResult {
    if (!dataSource.isAvailable(appContext)) {
      return GatewaySession.InvokeResult.error(
        code = "HEALTH_CONNECT_UNAVAILABLE",
        message = "HEALTH_CONNECT_UNAVAILABLE: Health Connect not available on this device",
      )
    }
    if (!dataSource.hasPermissions(appContext)) {
      return GatewaySession.InvokeResult.error(
        code = "HEALTH_CONNECT_PERMISSION_REQUIRED",
        message = "HEALTH_CONNECT_PERMISSION_REQUIRED: grant Health Connect permissions in Settings",
      )
    }
    val request = parseRequest(paramsJson)
      ?: return GatewaySession.InvokeResult.error(
        code = "INVALID_REQUEST",
        message = "INVALID_REQUEST: expected JSON object",
      )
    return try {
      val entries = dataSource.readBodyFat(appContext, request)
      GatewaySession.InvokeResult.ok(
        buildJsonObject {
          put("records", buildJsonArray {
            entries.forEach { entry ->
              add(buildJsonObject {
                put("timeISO", JsonPrimitive(entry.timeISO))
                put("percentage", JsonPrimitive(entry.percentage))
              })
            }
          })
          put("count", JsonPrimitive(entries.size))
        }.toString(),
      )
    } catch (err: Throwable) {
      GatewaySession.InvokeResult.error(
        code = "HEALTH_CONNECT_ERROR",
        message = "HEALTH_CONNECT_ERROR: ${err.message ?: "failed to read body fat"}",
      )
    }
  }

  suspend fun handleHealthNutrition(paramsJson: String?): GatewaySession.InvokeResult {
    if (!dataSource.isAvailable(appContext)) {
      return GatewaySession.InvokeResult.error(
        code = "HEALTH_CONNECT_UNAVAILABLE",
        message = "HEALTH_CONNECT_UNAVAILABLE: Health Connect not available on this device",
      )
    }
    if (!dataSource.hasPermissions(appContext)) {
      return GatewaySession.InvokeResult.error(
        code = "HEALTH_CONNECT_PERMISSION_REQUIRED",
        message = "HEALTH_CONNECT_PERMISSION_REQUIRED: grant Health Connect permissions in Settings",
      )
    }
    val request = parseRequest(paramsJson)
      ?: return GatewaySession.InvokeResult.error(
        code = "INVALID_REQUEST",
        message = "INVALID_REQUEST: expected JSON object",
      )
    return try {
      val entries = dataSource.readNutrition(appContext, request)
      GatewaySession.InvokeResult.ok(
        buildJsonObject {
          put("records", buildJsonArray {
            entries.forEach { entry ->
              add(buildJsonObject {
                put("startISO", JsonPrimitive(entry.startISO))
                put("endISO", JsonPrimitive(entry.endISO))
                entry.name?.let { put("name", JsonPrimitive(it)) }
                entry.calories?.let { put("calories", JsonPrimitive(it)) }
                entry.proteinG?.let { put("proteinG", JsonPrimitive(it)) }
                entry.fatG?.let { put("fatG", JsonPrimitive(it)) }
                entry.carbsG?.let { put("carbsG", JsonPrimitive(it)) }
              })
            }
          })
          put("count", JsonPrimitive(entries.size))
        }.toString(),
      )
    } catch (err: Throwable) {
      GatewaySession.InvokeResult.error(
        code = "HEALTH_CONNECT_ERROR",
        message = "HEALTH_CONNECT_ERROR: ${err.message ?: "failed to read nutrition"}",
      )
    }
  }

  suspend fun handleHealthSteps(paramsJson: String?): GatewaySession.InvokeResult {
    if (!dataSource.isAvailable(appContext)) {
      return GatewaySession.InvokeResult.error(
        code = "HEALTH_CONNECT_UNAVAILABLE",
        message = "HEALTH_CONNECT_UNAVAILABLE: Health Connect not available on this device",
      )
    }
    if (!dataSource.hasPermissions(appContext)) {
      return GatewaySession.InvokeResult.error(
        code = "HEALTH_CONNECT_PERMISSION_REQUIRED",
        message = "HEALTH_CONNECT_PERMISSION_REQUIRED: grant Health Connect permissions in Settings",
      )
    }
    val request = parseRequest(paramsJson)
      ?: return GatewaySession.InvokeResult.error(
        code = "INVALID_REQUEST",
        message = "INVALID_REQUEST: expected JSON object",
      )
    return try {
      val entries = dataSource.readSteps(appContext, request)
      GatewaySession.InvokeResult.ok(
        buildJsonObject {
          put("records", buildJsonArray {
            entries.forEach { entry ->
              add(buildJsonObject {
                put("startISO", JsonPrimitive(entry.startISO))
                put("endISO", JsonPrimitive(entry.endISO))
                put("count", JsonPrimitive(entry.count))
              })
            }
          })
          put("count", JsonPrimitive(entries.size))
        }.toString(),
      )
    } catch (err: Throwable) {
      GatewaySession.InvokeResult.error(
        code = "HEALTH_CONNECT_ERROR",
        message = "HEALTH_CONNECT_ERROR: ${err.message ?: "failed to read steps"}",
      )
    }
  }

  fun isAvailable(): Boolean = dataSource.isAvailable(appContext)

  private fun parseRequest(paramsJson: String?): HealthRecordsRequest? {
    if (paramsJson.isNullOrBlank()) {
      return HealthRecordsRequest(startISO = null, endISO = null, limit = 100)
    }
    val params = try {
      Json.parseToJsonElement(paramsJson).asObjectOrNull()
    } catch (_: Throwable) {
      null
    } ?: return null
    val limit = ((params["limit"] as? JsonPrimitive)?.content?.toIntOrNull() ?: 100).coerceIn(1, 1000)
    return HealthRecordsRequest(
      startISO = (params["startISO"] as? JsonPrimitive)?.content?.trim()?.ifEmpty { null },
      endISO = (params["endISO"] as? JsonPrimitive)?.content?.trim()?.ifEmpty { null },
      limit = limit,
    )
  }

  companion object {
    fun isHealthConnectAvailable(context: Context): Boolean = SystemHealthConnectDataSource.isAvailable(context)

    internal fun forTesting(
      appContext: Context,
      dataSource: HealthConnectDataSource,
    ): HealthConnectHandler = HealthConnectHandler(appContext = appContext, dataSource = dataSource)
  }
}
