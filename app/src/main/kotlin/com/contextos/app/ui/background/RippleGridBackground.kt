package com.contextos.app.ui.background

import android.content.Context
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.util.AttributeSet
import android.view.MotionEvent
import android.widget.FrameLayout
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10
import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sin

class RippleGridView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0,
) : FrameLayout(context, attrs, defStyleAttr) {

    private val _renderer = RippleGridRenderer()
    private val glSurfaceView: GLSurfaceView = GLSurfaceView(context).apply {
        setEGLContextClientVersion(2)
        setZOrderMediaOverlay(true)
        holder.setFormat(android.graphics.PixelFormat.TRANSLUCENT)
        isFocusable = false
        isFocusableInTouchMode = false
        setRenderer(_renderer)
    }

    internal val gridRenderer: RippleGridRenderer
        get() = _renderer

    init {
        addView(
            glSurfaceView,
            LayoutParams(LayoutParams.MATCH_PARENT, LayoutParams.MATCH_PARENT),
        )
    }

    override fun onInterceptTouchEvent(ev: MotionEvent?): Boolean = false

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        glSurfaceView.dispatchTouchEvent(ev)
        return super.dispatchTouchEvent(ev)
    }

    fun onPause() = glSurfaceView.onPause()
    fun onResume() = glSurfaceView.onResume()

    fun configure(
        colorR: Float, colorG: Float, colorB: Float,
        rippleIntensity: Float, gridSize: Float, gridThickness: Float,
        fadeDistance: Float, vignetteStrength: Float,
        glowIntensity: Float, opacity: Float, gridRotation: Float,
        mouseInteractionRadius: Float,
    ) {
        val r = gridRenderer
        r.gridColorR = colorR; r.gridColorG = colorG; r.gridColorB = colorB
        r.rippleIntensity = rippleIntensity; r.gridSize = gridSize
        r.gridThickness = gridThickness; r.fadeDistance = fadeDistance
        r.vignetteStrength = vignetteStrength; r.glowIntensity = glowIntensity
        r.opacity = opacity; r.gridRotation = gridRotation
        r.mouseInteractionRadius = mouseInteractionRadius
    }
}

class RippleGridRenderer : GLSurfaceView.Renderer {

    private var program = 0
    private var positionHandle = 0
    private var iTimeHandle = 0
    private var iResolutionHandle = 0
    private var gridColorHandle = 0
    private var rippleIntensityHandle = 0
    private var gridSizeHandle = 0
    private var gridThicknessHandle = 0
    private var fadeDistanceHandle = 0
    private var vignetteStrengthHandle = 0
    private var glowIntensityHandle = 0
    private var opacityHandle = 0
    private var gridRotationHandle = 0
    private var mousePositionHandle = 0
    private var mouseInfluenceHandle = 0
    private var mouseInteractionRadiusHandle = 0

    @Volatile var gridColorR = 0.545f
    @Volatile var gridColorG = 0.361f
    @Volatile var gridColorB = 0.965f
    @Volatile var rippleIntensity = 0.05f
    @Volatile var gridSize = 10f
    @Volatile var gridThickness = 15f
    @Volatile var fadeDistance = 1.5f
    @Volatile var vignetteStrength = 2f
    @Volatile var glowIntensity = 0.1f
    @Volatile var opacity = 0.6f
    @Volatile var gridRotation = 0f
    @Volatile var mouseInteractionRadius = 1f

    private var width = 1
    private var height = 1
    private var startTime = 0L

    @Volatile private var mouseX = 0.5f
    @Volatile private var mouseY = 0.5f
    @Volatile private var mouseInfluence = 0f
    @Volatile private var targetMouseX = 0.5f
    @Volatile private var targetMouseY = 0.5f
    @Volatile private var targetMouseInfluence = 0f

    companion object {
        private const val VERTEX_SHADER = """
            attribute vec2 position;
            varying vec2 vUv;
            void main() {
                vUv = position * 0.5 + 0.5;
                gl_Position = vec4(position, 0.0, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision highp float;
            uniform float iTime;
            uniform vec2 iResolution;
            uniform vec3 gridColor;
            uniform float rippleIntensity;
            uniform float gridSize;
            uniform float gridThickness;
            uniform float fadeDistance;
            uniform float vignetteStrength;
            uniform float glowIntensity;
            uniform float opacity;
            uniform float gridRotation;
            uniform vec2 mousePosition;
            uniform float mouseInfluence;
            uniform float mouseInteractionRadius;
            varying vec2 vUv;

            float pi = 3.14159265359;

            mat2 rotate(float a) {
                float s = sin(a);
                float c = cos(a);
                return mat2(c, -s, s, c);
            }

            void main() {
                vec2 uv = vUv * 2.0 - 1.0;
                uv.x *= iResolution.x / iResolution.y;

                if (gridRotation != 0.0) {
                    uv = rotate(gridRotation * pi / 180.0) * uv;
                }

                float dist = length(uv);
                float func = sin(pi * (iTime - dist));
                vec2 rippleUv = uv + uv * func * rippleIntensity;

                if (mouseInfluence > 0.0) {
                    vec2 mouseUv = (mousePosition * 2.0 - 1.0);
                    mouseUv.x *= iResolution.x / iResolution.y;
                    float mouseDist = length(uv - mouseUv);
                    float influence = mouseInfluence * exp(-mouseDist * mouseDist / (mouseInteractionRadius * mouseInteractionRadius));
                    float mouseWave = sin(pi * (iTime * 2.0 - mouseDist * 3.0)) * influence;
                    rippleUv += normalize(uv - mouseUv) * mouseWave * rippleIntensity * 0.3;
                }

                vec2 a = sin(gridSize * 0.5 * pi * rippleUv - pi / 2.0);
                vec2 b = abs(a);
                float aaWidth = 0.5;
                vec2 smoothB = vec2(
                    smoothstep(0.0, aaWidth, b.x),
                    smoothstep(0.0, aaWidth, b.y)
                );

                vec3 color = vec3(0.0);
                color += exp(-gridThickness * smoothB.x * (0.8 + 0.5 * sin(pi * iTime)));
                color += exp(-gridThickness * smoothB.y);
                color += 0.5 * exp(-(gridThickness / 4.0) * sin(smoothB.x));
                color += 0.5 * exp(-(gridThickness / 3.0) * smoothB.y);
                color += glowIntensity * exp(-gridThickness * 0.5 * smoothB.x);
                color += glowIntensity * exp(-gridThickness * 0.5 * smoothB.y);

                float ddd = exp(-2.0 * clamp(pow(dist, fadeDistance), 0.0, 1.0));
                vec2 vignetteCoords = vUv - 0.5;
                float vignetteDistance = length(vignetteCoords);
                float vignette = 1.0 - pow(vignetteDistance * 2.0, vignetteStrength);
                vignette = clamp(vignette, 0.0, 1.0);

                float finalFade = ddd * vignette;
                float alpha = length(color) * finalFade * opacity;
                gl_FragColor = vec4(color * gridColor * finalFade * opacity, alpha);
            }
        """
    }

    private fun loadShader(type: Int, source: String): Int {
        return GLES20.glCreateShader(type).also {
            GLES20.glShaderSource(it, source)
            GLES20.glCompileShader(it)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(it, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                val info = GLES20.glGetShaderInfoLog(it)
                GLES20.glDeleteShader(it)
                throw RuntimeException("Shader compile failed: $info")
            }
        }
    }

    private fun createProgram() {
        val vs = loadShader(GLES20.GL_VERTEX_SHADER, VERTEX_SHADER)
        val fs = loadShader(GLES20.GL_FRAGMENT_SHADER, FRAGMENT_SHADER)
        program = GLES20.glCreateProgram().also {
            GLES20.glAttachShader(it, vs)
            GLES20.glAttachShader(it, fs)
            GLES20.glLinkProgram(it)
            val linked = IntArray(1)
            GLES20.glGetProgramiv(it, GLES20.GL_LINK_STATUS, linked, 0)
            if (linked[0] == 0) {
                val info = GLES20.glGetProgramInfoLog(it)
                GLES20.glDeleteProgram(it)
                throw RuntimeException("Program link failed: $info")
            }
        }
        GLES20.glDeleteShader(vs)
        GLES20.glDeleteShader(fs)

        positionHandle = GLES20.glGetAttribLocation(program, "position")
        iTimeHandle = GLES20.glGetUniformLocation(program, "iTime")
        iResolutionHandle = GLES20.glGetUniformLocation(program, "iResolution")
        gridColorHandle = GLES20.glGetUniformLocation(program, "gridColor")
        rippleIntensityHandle = GLES20.glGetUniformLocation(program, "rippleIntensity")
        gridSizeHandle = GLES20.glGetUniformLocation(program, "gridSize")
        gridThicknessHandle = GLES20.glGetUniformLocation(program, "gridThickness")
        fadeDistanceHandle = GLES20.glGetUniformLocation(program, "fadeDistance")
        vignetteStrengthHandle = GLES20.glGetUniformLocation(program, "vignetteStrength")
        glowIntensityHandle = GLES20.glGetUniformLocation(program, "glowIntensity")
        opacityHandle = GLES20.glGetUniformLocation(program, "opacity")
        gridRotationHandle = GLES20.glGetUniformLocation(program, "gridRotation")
        mousePositionHandle = GLES20.glGetUniformLocation(program, "mousePosition")
        mouseInfluenceHandle = GLES20.glGetUniformLocation(program, "mouseInfluence")
        mouseInteractionRadiusHandle = GLES20.glGetUniformLocation(program, "mouseInteractionRadius")
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)
        GLES20.glClearColor(0f, 0f, 0f, 0f)
        createProgram()
        startTime = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) {
        width = w.coerceAtLeast(1)
        height = h.coerceAtLeast(1)
        GLES20.glViewport(0, 0, w, h)
    }

    override fun onDrawFrame(gl: GL10?) {
        val time = (System.nanoTime() - startTime) / 1_000_000_000f

        mouseX += (targetMouseX - mouseX) * 0.1f
        mouseY += (targetMouseY - mouseY) * 0.1f
        mouseInfluence += (targetMouseInfluence - mouseInfluence) * 0.05f

        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)
        GLES20.glUseProgram(program)

        val verts = floatArrayOf(-1f, -1f, 3f, -1f, -1f, 3f)
        val buf = java.nio.ByteBuffer.allocateDirect(verts.size * 4)
            .order(java.nio.ByteOrder.nativeOrder())
            .asFloatBuffer().apply { put(verts); position(0) }

        GLES20.glEnableVertexAttribArray(positionHandle)
        GLES20.glVertexAttribPointer(positionHandle, 2, GLES20.GL_FLOAT, false, 0, buf)

        GLES20.glUniform1f(iTimeHandle, time)
        GLES20.glUniform2f(iResolutionHandle, width.toFloat(), height.toFloat())
        GLES20.glUniform3f(gridColorHandle, gridColorR, gridColorG, gridColorB)
        GLES20.glUniform1f(rippleIntensityHandle, rippleIntensity)
        GLES20.glUniform1f(gridSizeHandle, gridSize)
        GLES20.glUniform1f(gridThicknessHandle, gridThickness)
        GLES20.glUniform1f(fadeDistanceHandle, fadeDistance)
        GLES20.glUniform1f(vignetteStrengthHandle, vignetteStrength)
        GLES20.glUniform1f(glowIntensityHandle, glowIntensity)
        GLES20.glUniform1f(opacityHandle, opacity)
        GLES20.glUniform1f(gridRotationHandle, gridRotation)
        GLES20.glUniform2f(mousePositionHandle, mouseX, mouseY)
        GLES20.glUniform1f(mouseInfluenceHandle, mouseInfluence)
        GLES20.glUniform1f(mouseInteractionRadiusHandle, mouseInteractionRadius)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, 3)
    }

    fun onTouchEvent(event: MotionEvent) {
        when (event.actionMasked) {
            MotionEvent.ACTION_MOVE, MotionEvent.ACTION_DOWN -> {
                targetMouseX = event.x / width
                targetMouseY = 1f - event.y / height
                targetMouseInfluence = 1f
            }
            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                targetMouseInfluence = 0f
            }
        }
    }
}

@Composable
fun RippleGridBackground(
    modifier: Modifier = Modifier,
    gridColorHex: String = "#8B5CF6",
    rippleIntensity: Float = 0.05f,
    gridSize: Float = 10f,
    gridThickness: Float = 15f,
    fadeDistance: Float = 1.5f,
    vignetteStrength: Float = 2f,
    glowIntensity: Float = 0.1f,
    opacity: Float = 0.6f,
    gridRotation: Float = 0f,
    mouseInteractionRadius: Float = 1f,
) {
    val colorR = gridColorHex.removePrefix("#").substring(0, 2).toInt(16) / 255f
    val colorG = gridColorHex.removePrefix("#").substring(2, 4).toInt(16) / 255f
    val colorB = gridColorHex.removePrefix("#").substring(4, 6).toInt(16) / 255f

    var rippleView by remember { mutableStateOf<RippleGridView?>(null) }

    AndroidView(
        modifier = modifier,
        factory = { ctx ->
            RippleGridView(ctx).apply {
                configure(
                    colorR = colorR, colorG = colorG, colorB = colorB,
                    rippleIntensity = rippleIntensity, gridSize = gridSize,
                    gridThickness = gridThickness, fadeDistance = fadeDistance,
                    vignetteStrength = vignetteStrength, glowIntensity = glowIntensity,
                    opacity = opacity, gridRotation = gridRotation,
                    mouseInteractionRadius = mouseInteractionRadius,
                )
                rippleView = this
            }
        },
    )

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = object : DefaultLifecycleObserver {
            override fun onResume(owner: LifecycleOwner) {
                rippleView?.onResume()
            }
            override fun onPause(owner: LifecycleOwner) {
                rippleView?.onPause()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
            rippleView = null
        }
    }
}
