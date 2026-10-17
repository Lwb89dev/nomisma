package com.nomisma.app.ui.theme

import android.content.Context
import android.graphics.BitmapFactory
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.nomisma.app.theme.ThemeRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject

internal data class ThemeAssetPack(
    val background: ImageBitmap,
    val pitcher: ImageBitmap? = null,
    val woodlandCharacters: Map<String, ImageBitmap> = emptyMap(),
    val woodlandContainer: ImageBitmap? = null,
    val minerLeft: ImageBitmap? = null,
    val minerRight: ImageBitmap? = null,
    val mineCart: ImageBitmap? = null
)

private object ThemeAssetRepository {
    private var cachedKey: String? = null
    private var cachedPack: ThemeAssetPack? = null

    @Synchronized
    fun load(context: Context, themeId: String, quality: ThemeRenderQuality): ThemeAssetPack? {
        val cacheKey = "$themeId:${quality.name}"
        if (cachedKey == cacheKey) return cachedPack
        val sampleSize = when (quality) {
            ThemeRenderQuality.LOW -> 4
            ThemeRenderQuality.MEDIUM -> 2
            ThemeRenderQuality.HIGH -> 1
        }
        val loaded = runCatching {
            val manifest = context.loadManifest(themeId)
            val root = "themes/$themeId/"
            when (themeId) {
                ThemeRegistry.WOODLAND -> ThemeAssetPack(
                    background = context.decodeAsset(root + manifest.getString("background"), sampleSize),
                    woodlandContainer = context.decodeAsset(
                        root + manifest.getString("container"),
                        sampleSize.coerceAtMost(2)
                    ),
                    woodlandCharacters = manifest.getJSONArray("characters").let { characters ->
                        val directory = manifest.getString("characterDirectory")
                        (0 until characters.length()).associate { index ->
                            val identity = characters.getString(index)
                            identity to context.decodeAsset(
                                "$root$directory/$identity.png",
                                sampleSize.coerceAtMost(2)
                            )
                        }
                    }
                )
                ThemeRegistry.MEMPOOL -> ThemeAssetPack(
                    background = context.decodeAsset(root + manifest.getString("background"), sampleSize),
                    minerLeft = context.decodeAsset(root + manifest.getString("minerLeft"), sampleSize.coerceAtMost(2)),
                    minerRight = context.decodeAsset(root + manifest.getString("minerRight"), sampleSize.coerceAtMost(2)),
                    mineCart = context.decodeAsset(root + manifest.getString("cart"), sampleSize.coerceAtMost(2))
                )
                else -> ThemeAssetPack(
                    background = context.decodeAsset(root + manifest.getString("background"), sampleSize),
                    pitcher = context.decodeAsset(root + manifest.getString("pitcher"), sampleSize.coerceAtMost(2))
                )
            }
        }.getOrNull()
        cachedKey = cacheKey
        cachedPack = loaded
        return loaded
    }

    private fun Context.loadManifest(themeId: String): JSONObject =
        assets.open("themes/$themeId/scene.json").bufferedReader().use { reader ->
            JSONObject(reader.readText()).also { manifest ->
                require(manifest.getString("id") == themeId) { "Theme manifest ID mismatch: $themeId" }
                require(manifest.getInt("version") >= 1) { "Unsupported theme manifest version: $themeId" }
            }
        }

    private fun Context.decodeAsset(path: String, sampleSize: Int): ImageBitmap =
        assets.open(path).use { stream ->
            requireNotNull(
                BitmapFactory.decodeStream(
                    stream,
                    null,
                    BitmapFactory.Options().apply {
                        inScaled = false
                        inSampleSize = sampleSize
                    }
                )
            ) { "Unable to decode theme asset: $path" }.asImageBitmap()
        }
}

@Composable
internal fun rememberThemeAssetPack(
    context: Context,
    themeId: String,
    quality: ThemeRenderQuality
): ThemeAssetPack? {
    val applicationContext = context.applicationContext
    val assets by produceState<ThemeAssetPack?>(null, applicationContext, themeId, quality) {
        value = withContext(Dispatchers.IO) {
            ThemeAssetRepository.load(applicationContext, themeId, quality)
        }
    }
    return assets
}
