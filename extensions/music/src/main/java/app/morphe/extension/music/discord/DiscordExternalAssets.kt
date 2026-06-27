package app.morphe.extension.music.discord

import kotlinx.serialization.json.Json
import kotlinx.serialization.encodeToString
import timber.log.Timber
import java.util.concurrent.ConcurrentHashMap
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.MediaType.Companion.toMediaType
import java.util.concurrent.TimeUnit
import kotlinx.coroutines.withContext
import kotlinx.coroutines.Dispatchers

object DiscordExternalAssets {

    private const val TAG = "DiscordSvc"
    private const val EXTERNAL_ASSETS_API =
        "https://discord.com/api/v9/applications/%s/external-assets"

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<String, String>()
    private const val CACHE_MAX_SIZE = 128

    private val client: OkHttpClient by lazy {
        OkHttpClient.Builder()
            .connectTimeout(5, TimeUnit.SECONDS)
            .readTimeout(10, TimeUnit.SECONDS)
            .writeTimeout(10, TimeUnit.SECONDS)
            .build()
    }

    suspend fun resolve(
        imageUrl: String,
        appId: String,
        token: String,
    ): String? = withContext(Dispatchers.IO) {
        if (imageUrl.isBlank()) return@withContext null
        if (imageUrl.startsWith("mp:")) return@withContext imageUrl

        cache[imageUrl]?.let {
            Timber.tag(TAG).d("resolve: cache hit for %s -> %s", imageUrl.take(60), it)
            return@withContext it
        }
        Timber.tag(TAG).d("resolve: cache miss for %s, calling API", imageUrl.take(60))

        return@withContext try {
            val mediaType = "application/json; charset=utf-8".toMediaType()
            val requestBody = json.encodeToString<ExternalAssetRequest>(
                ExternalAssetRequest(listOf(imageUrl))
            ).toRequestBody(mediaType)

            val request = Request.Builder()
                .url(EXTERNAL_ASSETS_API.format(appId))
                .header("Authorization", token)
                .header("User-Agent", DiscordSuperProperties.USER_AGENT)
                .header("X-Super-Properties", DiscordSuperProperties.base64)
                .post(requestBody)
                .build()

            val response = client.newCall(request).execute()
            response.use { resp ->
                val statusCode = resp.code
                val body = resp.body?.string().orEmpty()

                if (resp.isSuccessful && body.isNotBlank()) {
                    val parsed = json.decodeFromString<List<ExternalAssetResponse>>(body)
                    val assetPath = parsed.firstOrNull()?.externalAssetPath
                    if (assetPath != null) {
                        val result = "mp:$assetPath"
                        cache[imageUrl] = result
                        trimCache()
                        Timber.tag(TAG).i("external-assets: resolved %s -> %s", imageUrl.take(60), result)
                        result
                    } else {
                        Timber.tag(TAG).w("external-assets: no path in response for %s: %s", imageUrl.take(60), body.take(200))
                        null
                    }
                } else {
                    Timber.tag(TAG).w("external-assets: HTTP %d for %s: %s", statusCode, imageUrl.take(60), body.take(200))
                    null
                }
            }
        } catch (e: Exception) {
            Timber.tag(TAG).e(e, "external-assets: failed for %s", imageUrl.take(60))
            null
        }
    }

    private fun trimCache() {
        if (cache.size > CACHE_MAX_SIZE) {
            val toRemove = cache.size - CACHE_MAX_SIZE
            cache.keys.take(toRemove).forEach { cache.remove(it) }
        }
    }

    fun clearCache() {
        Timber.tag(TAG).d("clearCache: clearing %d entries", cache.size)
        cache.clear()
    }

    fun close() {
        // OkHttpClient does not need explicit close
    }

    @kotlinx.serialization.Serializable
    private data class ExternalAssetRequest(
        val urls: List<String>,
    )

    @kotlinx.serialization.Serializable
    private data class ExternalAssetResponse(
        val url: String? = null,
        @kotlinx.serialization.SerialName("external_asset_path")
        val externalAssetPath: String? = null,
    )
}
