package com.nomisma.app.ui.theme

import android.graphics.RuntimeShader
import android.os.Build
import androidx.annotation.RequiresApi
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ShaderBrush

internal interface SceneMaterials {
    fun liquid(
        width: Float,
        height: Float,
        time: Float,
        ratioA: Float,
        energy: Float,
        colorA: Color,
        colorB: Color
    ): Brush

    fun woodland(width: Float, height: Float, time: Float, colorA: Color, colorB: Color): Brush

    fun mempool(
        width: Float,
        height: Float,
        time: Float,
        energy: Float,
        colorA: Color,
        colorB: Color
    ): Brush
}

internal object SceneMaterialsFactory {
    fun create(): SceneMaterials? {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return null
        return runCatching { Api33SceneMaterials() }.getOrNull()
    }
}

@RequiresApi(Build.VERSION_CODES.TIRAMISU)
private class Api33SceneMaterials : SceneMaterials {
    private val liquidShader = RuntimeShader(LIQUID_SHADER)
    private val liquidBrush = ShaderBrush(liquidShader)
    private val woodlandShader = RuntimeShader(WOODLAND_SHADER)
    private val woodlandBrush = ShaderBrush(woodlandShader)
    private val mempoolShader = RuntimeShader(MEMPOOL_SHADER)
    private val mempoolBrush = ShaderBrush(mempoolShader)

    override fun liquid(
        width: Float,
        height: Float,
        time: Float,
        ratioA: Float,
        energy: Float,
        colorA: Color,
        colorB: Color
    ): Brush {
        liquidShader.setFloatUniform("resolution", width, height)
        liquidShader.setFloatUniform("time", time)
        liquidShader.setFloatUniform("ratioA", ratioA.coerceIn(0f, 1f))
        liquidShader.setFloatUniform("energy", energy.coerceIn(0f, 1f))
        liquidShader.setColorUniform("colorA", colorA.toAndroidColor())
        liquidShader.setColorUniform("colorB", colorB.toAndroidColor())
        return liquidBrush
    }

    override fun woodland(width: Float, height: Float, time: Float, colorA: Color, colorB: Color): Brush {
        woodlandShader.setFloatUniform("resolution", width, height)
        woodlandShader.setFloatUniform("time", time)
        woodlandShader.setColorUniform("colorA", colorA.toAndroidColor())
        woodlandShader.setColorUniform("colorB", colorB.toAndroidColor())
        return woodlandBrush
    }

    override fun mempool(
        width: Float,
        height: Float,
        time: Float,
        energy: Float,
        colorA: Color,
        colorB: Color
    ): Brush {
        mempoolShader.setFloatUniform("resolution", width, height)
        mempoolShader.setFloatUniform("time", time)
        mempoolShader.setFloatUniform("energy", energy.coerceIn(0f, 1f))
        mempoolShader.setColorUniform("colorA", colorA.toAndroidColor())
        mempoolShader.setColorUniform("colorB", colorB.toAndroidColor())
        return mempoolBrush
    }
}

private fun Color.toAndroidColor(): android.graphics.Color =
    android.graphics.Color.valueOf(red, green, blue, alpha)

private const val LIQUID_SHADER = """
    uniform float2 resolution;
    uniform float time;
    uniform float ratioA;
    uniform float energy;
    layout(color) uniform half4 colorA;
    layout(color) uniform half4 colorB;

    float hash(float2 p) {
        return fract(sin(dot(p, float2(127.1, 311.7))) * 43758.5453);
    }

    float noise(float2 p) {
        float2 i = floor(p);
        float2 f = fract(p);
        float2 u = f * f * (3.0 - 2.0 * f);
        return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x),
                   mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
    }

    float fbm(float2 p) {
        float value = 0.0;
        float amplitude = 0.5;
        for (int i = 0; i < 4; i++) {
            value += noise(p) * amplitude;
            p = p * 2.03 + float2(13.7, 9.2);
            amplitude *= 0.5;
        }
        return value;
    }

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / resolution;
        float t = time * 6.2831853;
        float warpA = fbm(uv * float2(4.2, 5.8) + float2(t * 0.055, -t * 0.025));
        float warpB = fbm(uv.yx * float2(7.4, 3.6) + float2(-t * 0.035, t * 0.045));
        float boundary = ratioA + (warpA - 0.5) * (0.16 + energy * 0.12) + sin(uv.y * 18.0 + t) * 0.025;
        float blend = smoothstep(boundary - 0.12, boundary + 0.12, uv.x);
        half4 base = mix(colorA, colorB, half(blend));

        float depth = smoothstep(0.0, 0.24, uv.y) * (1.0 - smoothstep(0.78, 1.0, uv.y));
        float causticA = abs(sin((uv.x * 31.0 + warpB * 8.0) + t * 0.7));
        float causticB = abs(sin((uv.y * 37.0 - warpA * 9.0) - t * 0.45));
        float caustic = pow(causticA * causticB, 7.0) * (0.34 + energy * 0.34);
        float micro = fbm(uv * 18.0 + float2(t * 0.04, -t * 0.08));
        float edgeLight = pow(abs(uv.x - 0.5) * 2.0, 5.0) * 0.26;
        float luminance = 0.57 + depth * 0.32 + caustic + (micro - 0.5) * 0.10 + edgeLight;
        half3 rgb = base.rgb * half(luminance) + half3(caustic * 0.34);
        return half4(rgb, 0.72);
    }
"""

private const val WOODLAND_SHADER = """
    uniform float2 resolution;
    uniform float time;
    layout(color) uniform half4 colorA;
    layout(color) uniform half4 colorB;

    float hash(float2 p) {
        return fract(sin(dot(p, float2(41.3, 289.1))) * 45758.5453);
    }

    float noise(float2 p) {
        float2 i = floor(p);
        float2 f = fract(p);
        float2 u = f * f * (3.0 - 2.0 * f);
        return mix(mix(hash(i), hash(i + float2(1.0, 0.0)), u.x),
                   mix(hash(i + float2(0.0, 1.0)), hash(i + float2(1.0, 1.0)), u.x), u.y);
    }

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / resolution;
        float fog = noise(uv * 5.0 + float2(time * 0.12, -time * 0.035));
        half3 top = half3(0.055, 0.105, 0.072);
        half3 bottom = half3(0.012, 0.025, 0.018);
        half3 base = mix(top, bottom, half(smoothstep(0.0, 0.94, uv.y)));
        float shaftPattern = abs(sin((uv.x + fog * 0.08) * 19.0 - 1.4));
        float shafts = pow(max(0.0, 1.0 - shaftPattern), 12.0) * (1.0 - uv.y) * 0.42;
        float sun = exp(-distance(uv, float2(0.50, 0.04)) * 5.8);
        half3 warm = half3(1.0, 0.68, 0.31);
        half3 side = mix(colorA.rgb, colorB.rgb, half(uv.x));
        base += warm * half(shafts + sun * 0.18);
        base += side * half((1.0 - uv.y) * 0.045);
        base += half3((fog - 0.5) * 0.035);
        return half4(base, 1.0);
    }
"""

private const val MEMPOOL_SHADER = """
    uniform float2 resolution;
    uniform float time;
    uniform float energy;
    layout(color) uniform half4 colorA;
    layout(color) uniform half4 colorB;

    float lineMask(float value, float width) {
        float d = abs(fract(value) - 0.5);
        return 1.0 - smoothstep(width, width + 0.018, d);
    }

    half4 main(float2 fragCoord) {
        float2 uv = fragCoord / resolution;
        float2 perspective = float2((uv.x - 0.5) / (0.42 + uv.y * 0.9) + 0.5, uv.y);
        float gridX = lineMask(perspective.x * 16.0, 0.472);
        float gridY = lineMask((perspective.y + time * 0.035) * 12.0, 0.474);
        float grid = max(gridX, gridY) * smoothstep(0.12, 0.92, uv.y);
        float scan = pow(max(0.0, sin((uv.y - time * 0.16) * 95.0)), 18.0);
        float core = exp(-distance(uv, float2(0.5, 0.48)) * (7.0 - energy * 2.0));
        float vignette = smoothstep(0.92, 0.24, distance(uv, float2(0.5, 0.52)));
        half3 base = mix(half3(0.008, 0.013, 0.021), half3(0.027, 0.047, 0.070), half(uv.y));
        half3 side = mix(colorA.rgb, colorB.rgb, half(uv.x));
        half3 orange = half3(1.0, 0.31, 0.055);
        base += side * half(grid * 0.13);
        base += orange * half(scan * (0.025 + energy * 0.11));
        base += orange * half(core * (0.055 + energy * 0.28));
        base *= half(0.62 + vignette * 0.54);
        return half4(base, 1.0);
    }
"""
