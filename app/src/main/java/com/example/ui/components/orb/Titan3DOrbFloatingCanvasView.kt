package com.example.ui.components.orb

import android.content.Context
import android.graphics.SurfaceTexture
import android.opengl.GLES20
import android.os.SystemClock
import android.util.AttributeSet
import android.util.Log
import android.view.TextureView
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import javax.microedition.khronos.egl.EGL10
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.egl.EGLContext
import javax.microedition.khronos.egl.EGLDisplay
import javax.microedition.khronos.egl.EGLSurface

/**
 * Titan3DOrbFloatingCanvasView
 * True 60 FPS GPU-Accelerated OpenGL ES 2.0 Raymarched 3D Renderer on a transparent TextureView.
 * Renders cleanly with full alpha transparency (ZERO black background box) both inside Compose
 * and inside Android WindowManager floating overlays over other apps.
 *
 * Modes:
 *  - Mode 0 (NEURAL_3D_SPHERE): Raymarched 3D Liquid Plasma Orb + Z-occluded Orbiting Ring.
 *  - Mode 1 (ENERGY_GHOST): Raymarched 3D SDF Cute Ghost with wavy skirt, waving arms & blinking eyes.
 *  - Mode 2 (HOLO_JELLYFISH): Raymarched 3D Pastel Sunset Jellyfish (Soft Pink, Sky Blue, White & Warm Orange).
 */
class Titan3DOrbFloatingCanvasView @JvmOverloads constructor(
    context: Context,
    attrs: AttributeSet? = null,
    defStyleAttr: Int = 0
) : TextureView(context, attrs, defStyleAttr), TextureView.SurfaceTextureListener {

    @Volatile
    private var activeSkin: TitanOrbSkin = TitanOrbCatalog.TITAN_3D_ORB

    @Volatile
    private var audioLevel: Float = 0f

    @Volatile
    private var isListening: Boolean = false

    @Volatile
    private var isSpeaking: Boolean = false

    @Volatile
    private var surfaceWidth: Int = 0

    @Volatile
    private var surfaceHeight: Int = 0

    private var renderThread: OrbGlThread? = null

    init {
        isOpaque = false
        surfaceTextureListener = this
    }

    fun setSkin(skin: TitanOrbSkin) {
        activeSkin = skin
    }

    fun setAudioLevel(level: Float) {
        audioLevel = level.coerceIn(0f, 1f)
    }

    fun setVoiceState(listening: Boolean, speaking: Boolean) {
        isListening = listening
        isSpeaking = speaking
    }

    override fun onSurfaceTextureAvailable(surface: SurfaceTexture, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
        stopRenderThread()
        renderThread = OrbGlThread(surface).also { it.start() }
    }

    override fun onSurfaceTextureSizeChanged(surface: SurfaceTexture, width: Int, height: Int) {
        surfaceWidth = width
        surfaceHeight = height
    }

    override fun onSurfaceTextureDestroyed(surface: SurfaceTexture): Boolean {
        stopRenderThread()
        return true
    }

    override fun onSurfaceTextureUpdated(surface: SurfaceTexture) {
        // Frame presented to SurfaceTexture
    }

    override fun onDetachedFromWindow() {
        stopRenderThread()
        super.onDetachedFromWindow()
    }

    private fun stopRenderThread() {
        renderThread?.requestStop()
        renderThread = null
    }

    private inner class OrbGlThread(
        private val surfaceTexture: SurfaceTexture
    ) : Thread("Titan3DOrbGL") {

        @Volatile
        private var running = true

        fun requestStop() {
            running = false
            interrupt()
        }

        override fun run() {
            val egl = EGLContext.getEGL() as EGL10
            val display: EGLDisplay = egl.eglGetDisplay(EGL10.EGL_DEFAULT_DISPLAY)
            if (display == EGL10.EGL_NO_DISPLAY) return

            val version = IntArray(2)
            if (!egl.eglInitialize(display, version)) return

            val configAttribs = intArrayOf(
                EGL10.EGL_RENDERABLE_TYPE, 4,
                EGL10.EGL_RED_SIZE, 8,
                EGL10.EGL_GREEN_SIZE, 8,
                EGL10.EGL_BLUE_SIZE, 8,
                EGL10.EGL_ALPHA_SIZE, 8,
                EGL10.EGL_DEPTH_SIZE, 0,
                EGL10.EGL_STENCIL_SIZE, 0,
                EGL10.EGL_NONE
            )
            val configs = arrayOfNulls<EGLConfig>(1)
            val numConfigs = IntArray(1)
            if (!egl.eglChooseConfig(display, configAttribs, configs, 1, numConfigs) || numConfigs[0] <= 0) {
                egl.eglTerminate(display)
                return
            }
            val eglConfig = configs[0] ?: run {
                egl.eglTerminate(display)
                return
            }

            val contextAttribs = intArrayOf(0x3098, 2, EGL10.EGL_NONE)
            val eglContext = egl.eglCreateContext(display, eglConfig, EGL10.EGL_NO_CONTEXT, contextAttribs)
            if (eglContext == null || eglContext == EGL10.EGL_NO_CONTEXT) {
                egl.eglTerminate(display)
                return
            }

            val eglSurface: EGLSurface = egl.eglCreateWindowSurface(display, eglConfig, surfaceTexture, null)
            if (eglSurface == null || eglSurface == EGL10.EGL_NO_SURFACE) {
                egl.eglDestroyContext(display, eglContext)
                egl.eglTerminate(display)
                return
            }

            if (!egl.eglMakeCurrent(display, eglSurface, eglSurface, eglContext)) {
                egl.eglDestroySurface(display, eglSurface)
                egl.eglDestroyContext(display, eglContext)
                egl.eglTerminate(display)
                return
            }

            val program = createProgram(VERTEX_SHADER, FRAGMENT_SHADER)
            if (program == 0) {
                cleanupEgl(egl, display, eglSurface, eglContext)
                return
            }

            val quadCoords = floatArrayOf(
                -1f, -1f,
                 1f, -1f,
                -1f,  1f,
                 1f,  1f
            )
            val vertexBuffer: FloatBuffer = ByteBuffer.allocateDirect(quadCoords.size * 4)
                .order(ByteOrder.nativeOrder())
                .asFloatBuffer()
                .put(quadCoords)
            vertexBuffer.position(0)

            val aPosLoc = GLES20.glGetAttribLocation(program, "aPosition")
            val uTimeLoc = GLES20.glGetUniformLocation(program, "uTime")
            val uVoiceLoc = GLES20.glGetUniformLocation(program, "uVoice")
            val uModeLoc = GLES20.glGetUniformLocation(program, "uMode")

            val startMs = SystemClock.uptimeMillis()
            var smoothedVoice = 0.08f

            GLES20.glUseProgram(program)
            GLES20.glEnableVertexAttribArray(aPosLoc)
            GLES20.glVertexAttribPointer(aPosLoc, 2, GLES20.GL_FLOAT, false, 0, vertexBuffer)

            while (running) {
                val frameStart = SystemClock.uptimeMillis()
                val elapsedSec = (frameStart - startMs) / 1000.0f

                val targetVoice = when {
                    isSpeaking -> (0.45f + audioLevel * 0.55f).coerceIn(0.35f, 1.0f)
                    isListening -> (0.20f + audioLevel * 0.65f).coerceIn(0.18f, 0.95f)
                    else -> (0.06f + audioLevel * 0.45f).coerceIn(0.06f, 0.85f)
                }
                smoothedVoice += (targetVoice - smoothedVoice) * 0.18f

                val modeVal = when (activeSkin.styleType) {
                    OrbStyleType.ENERGY_GHOST -> 1.0f
                    OrbStyleType.HOLO_JELLYFISH -> 2.0f
                    else -> 0.0f
                }

                val w = surfaceWidth.coerceAtLeast(1)
                val h = surfaceHeight.coerceAtLeast(1)
                GLES20.glViewport(0, 0, w, h)
                GLES20.glClearColor(0f, 0f, 0f, 0f)
                GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT)

                GLES20.glUniform1f(uTimeLoc, elapsedSec)
                GLES20.glUniform1f(uVoiceLoc, smoothedVoice)
                GLES20.glUniform1f(uModeLoc, modeVal)

                GLES20.glDrawArrays(GLES20.GL_TRIANGLE_STRIP, 0, 4)
                egl.eglSwapBuffers(display, eglSurface)

                val frameMs = SystemClock.uptimeMillis() - frameStart
                val sleepMs = (16L - frameMs).coerceAtLeast(1L)
                try {
                    sleep(sleepMs)
                } catch (_: InterruptedException) {
                    if (!running) break
                }
            }

            GLES20.glDeleteProgram(program)
            cleanupEgl(egl, display, eglSurface, eglContext)
        }

        private fun cleanupEgl(
            egl: EGL10,
            display: EGLDisplay,
            surface: EGLSurface,
            context: EGLContext
        ) {
            try {
                egl.eglMakeCurrent(display, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_SURFACE, EGL10.EGL_NO_CONTEXT)
                egl.eglDestroySurface(display, surface)
                egl.eglDestroyContext(display, context)
                egl.eglTerminate(display)
            } catch (_: Exception) {
            }
        }
    }

    private fun compileShader(type: Int, source: String): Int {
        val shader = GLES20.glCreateShader(type)
        GLES20.glShaderSource(shader, source)
        GLES20.glCompileShader(shader)
        val compiled = IntArray(1)
        GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
        if (compiled[0] == 0) {
            Log.e(TAG, "GLSL compile error: ${GLES20.glGetShaderInfoLog(shader)}")
            GLES20.glDeleteShader(shader)
            return 0
        }
        return shader
    }

    private fun createProgram(vs: String, fs: String): Int {
        val vShader = compileShader(GLES20.GL_VERTEX_SHADER, vs)
        if (vShader == 0) return 0
        val fShader = compileShader(GLES20.GL_FRAGMENT_SHADER, fs)
        if (fShader == 0) {
            GLES20.glDeleteShader(vShader)
            return 0
        }
        val prog = GLES20.glCreateProgram()
        GLES20.glAttachShader(prog, vShader)
        GLES20.glAttachShader(prog, fShader)
        GLES20.glLinkProgram(prog)
        val linked = IntArray(1)
        GLES20.glGetProgramiv(prog, GLES20.GL_LINK_STATUS, linked, 0)
        GLES20.glDeleteShader(vShader)
        GLES20.glDeleteShader(fShader)
        if (linked[0] == 0) {
            Log.e(TAG, "GLSL link error: ${GLES20.glGetProgramInfoLog(prog)}")
            GLES20.glDeleteProgram(prog)
            return 0
        }
        return prog
    }

    companion object {
        private const val TAG = "Titan3DOrbGL"

        private const val VERTEX_SHADER = """
            attribute vec2 aPosition;
            varying vec2 vUv;
            void main() {
                vUv = aPosition;
                gl_Position = vec4(aPosition, 0.0, 1.0);
            }
        """

        private const val FRAGMENT_SHADER = """
            precision highp float;
            varying vec2 vUv;
            uniform float uTime;
            uniform float uVoice;
            uniform float uMode;

            float sq(float x) { return x * x; }

            float smin(float a, float b, float k) {
                float h = clamp(0.5 + 0.5 * (b - a) / k, 0.0, 1.0);
                return mix(b, a, h) - k * h * (1.0 - h);
            }

            float sdCapsule(vec3 p, vec3 a, vec3 b, float r) {
                vec3 pa = p - a;
                vec3 ba = b - a;
                float h = clamp(dot(pa, ba) / max(dot(ba, ba), 0.0001), 0.0, 1.0);
                return length(pa - ba * h) - r;
            }

            // === MODE 0: 3D RAYMARCHED LIQUID PLASMA ORB + 3D ORBITING RING ===
            vec4 render3DOrb(vec2 uv, float t, float voice) {
                float r2 = dot(uv, uv);
                float r = sqrt(r2 + 0.00001);

                float cycle = 0.5 + 0.5 * sin(t * 0.45);
                vec3 cDeep = mix(vec3(0.02, 0.10, 0.30), vec3(0.15, 0.04, 0.36), cycle);
                vec3 cMid  = mix(vec3(0.00, 0.68, 1.00), vec3(0.62, 0.22, 1.00), cycle);
                vec3 cRim  = mix(vec3(0.35, 0.96, 1.00), vec3(1.00, 0.40, 0.90), cycle);

                vec3 col = vec3(0.0);
                float alpha = 0.0;

                float aura = exp(-r2 * (4.2 - 0.8 * voice)) * (0.38 + 0.25 * voice) * smoothstep(0.98, 0.52, r);
                col += cMid * aura * 0.85;
                alpha = max(alpha, clamp(aura * 0.95, 0.0, 0.65));

                float ringTiltX = 0.52 + 0.14 * sin(t * 0.9);
                float ringTiltZ = 0.28 * cos(t * 0.7);
                float ca = cos(ringTiltZ);
                float sa = sin(ringTiltZ);
                vec2 ruv = vec2(uv.x * ca - uv.y * sa, uv.x * sa + uv.y * ca);
                float ryPlane = ruv.y / max(sin(ringTiltX), 0.1);
                float rRing = sqrt(ruv.x * ruv.x + ryPlane * ryPlane + 0.0001);
                float ringRadius = 0.74 + 0.04 * voice;
                float ringDist = abs(rRing - ringRadius);
                float ringZ = -ryPlane * cos(ringTiltX);
                float ringAng = atan(ryPlane, ruv.x) + t * 2.4;
                float ringSpark = 0.55 + 0.45 * sin(ringAng * 3.0);
                float ringGlow = exp(-ringDist * 36.0) * ringSpark * (0.65 + 0.40 * voice);

                if (ringZ < 0.0) {
                    col += cRim * ringGlow * 0.75;
                    alpha = max(alpha, clamp(ringGlow * 0.9, 0.0, 0.95));
                }

                float baseRad = 0.52 + 0.025 * sin(t * 2.2) + 0.045 * voice;
                float ang = atan(uv.y, uv.x);
                float disp =
                    0.020 * voice * sin(ang * 4.0 + t * 4.5) * cos(r * 8.0 - t * 3.0) +
                    0.012 * (0.4 + voice) * cos(ang * 3.0 - t * 3.2) +
                    0.010 * voice * sin(ang * 7.0 + t * 6.5);
                float effRad = baseRad + disp;

                if (r < effRad) {
                    vec3 n = vec3(uv / effRad, sqrt(max(0.0, 1.0 - dot(uv / effRad, uv / effRad))));
                    float ct = cos(t * 0.9);
                    float st = sin(t * 0.9);
                    vec3 rp = vec3(n.x * ct + n.z * st, n.y, -n.x * st + n.z * ct);

                    float p1 = sin(rp.x * 4.5 + t * 1.8) * cos(rp.y * 4.5 - t * 1.4) * sin(rp.z * 4.5 + t * 1.1);
                    float p2 = sin((rp.x + rp.y) * 7.0 - t * 2.6 + p1 * 2.2) * cos((rp.y - rp.z) * 6.5 + t * 2.1);
                    float p3 = sin(rp.z * 10.0 + p2 * 2.5 - t * 3.4);
                    float plasma = clamp(0.5 + 0.5 * (0.50 * p1 + 0.35 * p2 + 0.15 * p3), 0.0, 1.0);

                    float bump = 0.14 * (0.5 + voice);
                    vec3 bn = normalize(vec3(n.x + bump * p2 * 0.4, n.y + bump * p3 * 0.4, n.z + 0.05));
                    vec3 lDir = normalize(vec3(0.45, 0.62, 0.64));
                    vec3 hDir = normalize(lDir + vec3(0.0, 0.0, 1.0));

                    float ndotl = max(0.0, dot(bn, lDir));
                    float fresnel = pow(clamp(1.0 - n.z, 0.0001, 1.0), 2.6);
                    float spec = pow(max(0.0001, dot(bn, hDir)), 40.0) * 1.15;
                    float specSoft = pow(max(0.0001, dot(bn, hDir)), 8.0) * 0.28;
                    float coreGlow = pow(max(n.z, 0.0001), 2.2) * (0.45 + 0.45 * plasma) * (0.85 + 0.45 * voice);

                    vec3 sphereCol =
                        cDeep * (0.35 + 0.65 * ndotl) +
                        cMid * (plasma * 0.85 + coreGlow * 0.65 + specSoft) +
                        cRim * (fresnel * (1.25 + 0.45 * voice)) +
                        vec3(spec * 0.95);

                    float aa = smoothstep(effRad, effRad - 0.018, r);
                    col = mix(col, sphereCol, aa);
                    alpha = max(alpha, aa);
                }

                if (ringZ >= 0.0) {
                    col += (cRim * 0.95 + vec3(0.25)) * ringGlow;
                    alpha = max(alpha, clamp(ringGlow, 0.0, 0.98));
                }

                col = col / (vec3(1.0) + col * 0.52);
                col = pow(clamp(col, 0.0001, 1.0), vec3(1.0 / 1.12));
                alpha = clamp(alpha, 0.0, 1.0);
                return vec4(col * alpha, alpha);
            }

            // === MODE 1: 3D SDF RAYMARCHED CUTE ANIMATED GHOST ===
            vec3 ghostLocal(vec3 p, float t, float voice) {
                float bob = 0.07 * sin(t * 2.4) + 0.03 * voice * sin(t * 9.0);
                p.y -= bob;
                float yaw = 0.28 * sin(t * 1.15);
                float cy = cos(yaw); float sy = sin(yaw);
                p = vec3(p.x * cy + p.z * sy, p.y, -p.x * sy + p.z * cy);
                float tilt = 0.09 * sin(t * 1.7 + 0.5);
                float ct = cos(tilt); float st = sin(tilt);
                return vec3(p.x * ct - p.y * st, p.x * st + p.y * ct, p.z);
            }

            float ghostSdf(vec3 p, float t, float voice) {
                vec3 q = ghostLocal(p, t, voice);
                float taper = clamp((-q.y + 0.2) * 0.18, 0.0, 0.16);
                float ang = atan(q.z, q.x);
                float skirtWave = 0.075 * sin(ang * 5.0 + t * 3.8) + 0.03 * voice * cos(ang * 3.0 - t * 6.0);
                float cyClamped = clamp(q.y, -0.34, 0.22);
                float rBody = 0.44 + taper + 0.035 * sin(ang * 5.0 + t * 3.8) * clamp((-q.y - 0.05) * 2.2, 0.0, 1.0);
                float dCore = length(vec3(q.x, q.y - cyClamped, q.z)) - rBody;

                float bottomPlane = (-0.42 + skirtWave) - q.y;
                float kBot = 0.07;
                float hb = clamp(0.5 - 0.5 * (bottomPlane - dCore) / kBot, 0.0, 1.0);
                float dBody = mix(bottomPlane, dCore, hb) + kBot * hb * (1.0 - hb);

                float waveL = 0.07 * sin(t * 3.2) + 0.05 * voice * sin(t * 10.0);
                float waveR = 0.07 * sin(t * 3.2 + 1.2) + 0.05 * voice * cos(t * 10.0);
                float dArmL = sdCapsule(q, vec3(-0.36, -0.02, 0.08), vec3(-0.57, -0.08 + waveL, 0.18), 0.11);
                float dArmR = sdCapsule(q, vec3( 0.36, -0.02, 0.08), vec3( 0.57, -0.08 + waveR, 0.18), 0.11);

                float d = smin(dBody, dArmL, 0.10);
                d = smin(d, dArmR, 0.10);
                return d;
            }

            vec4 render3DGhost(vec2 uv, float t, float voice) {
                vec3 ro = vec3(0.0, 0.05, 2.55);
                vec3 rd = normalize(vec3(uv, -1.85));

                float r2 = dot(uv, uv);
                float aura = exp(-r2 * (4.0 - 0.6 * voice)) * (0.34 + 0.20 * voice) * smoothstep(0.98, 0.50, sqrt(r2));
                vec3 outCol = vec3(0.18, 0.58, 0.95) * aura;
                float outAlpha = clamp(aura * 0.85, 0.0, 0.55);

                float tRay = 1.35;
                bool hit = false;
                for (int i = 0; i < 32; i++) {
                    vec3 p = ro + rd * tRay;
                    float d = ghostSdf(p, t, voice);
                    if (d < 0.004) {
                        hit = true;
                        break;
                    }
                    if (tRay > 3.9) break;
                    tRay += max(d * 0.85, 0.008);
                }

                if (hit) {
                    vec3 p = ro + rd * tRay;
                    vec3 q = ghostLocal(p, t, voice);
                    float eps = 0.005;
                    vec3 n = normalize(vec3(
                        ghostSdf(p + vec3(eps, 0.0, 0.0), t, voice) - ghostSdf(p - vec3(eps, 0.0, 0.0), t, voice),
                        ghostSdf(p + vec3(0.0, eps, 0.0), t, voice) - ghostSdf(p - vec3(0.0, eps, 0.0), t, voice),
                        ghostSdf(p + vec3(0.0, 0.0, eps), t, voice) - ghostSdf(p - vec3(0.0, 0.0, eps), t, voice)
                    ));

                    vec3 vDir = -rd;
                    vec3 lDir = normalize(vec3(0.48, 0.65, 0.59));
                    vec3 hDir = normalize(lDir + vDir);
                    float ndotv = clamp(dot(n, vDir), 0.0, 1.0);
                    float ndotl = clamp(dot(n, lDir), 0.0, 1.0);
                    float fresnel = pow(max(1.0 - ndotv, 0.0001), 2.4);
                    float spec = pow(clamp(dot(n, hDir), 0.0001, 1.0), 34.0);

                    float botGrad = clamp((-q.y + 0.15) * 1.4, 0.0, 1.0);
                    vec3 baseCol = vec3(0.86 - 0.34 * botGrad, 0.94 - 0.12 * botGrad, 1.00);
                    float shade = 0.44 + 0.56 * ndotl;
                    vec3 col = baseCol * shade +
                        fresnel * vec3(0.28 + 0.35 * voice, 0.85 + 0.25 * voice, 1.15 + 0.30 * voice) +
                        vec3(spec * 0.58);

                    if (q.z > 0.12) {
                        float cycleT = mod(t, 3.4);
                        float blink = 1.0;
                        float dtBlink = abs(cycleT - 1.2);
                        if (dtBlink < 0.13) {
                            blink = clamp(sq(dtBlink / 0.13), 0.08, 1.0);
                        }

                        float cheekL = exp(-(sq((q.x + 0.24) / 0.085) + sq((q.y - 0.01) / 0.055)));
                        float cheekR = exp(-(sq((q.x - 0.24) / 0.085) + sq((q.y - 0.01) / 0.055)));
                        float cheeks = clamp(cheekL + cheekR, 0.0, 1.0);
                        col = mix(col, vec3(1.05, 0.45, 0.72), 0.38 * cheeks);

                        float eyeH = 0.095 * blink;
                        float eyeLD = sq((q.x + 0.145) / 0.068) + sq((q.y - 0.115) / eyeH);
                        float eyeRD = sq((q.x - 0.145) / 0.068) + sq((q.y - 0.115) / eyeH);
                        float eyeMask = clamp(max(1.0 - clamp((eyeLD - 0.85) * 6.0, 0.0, 1.0),
                                                  1.0 - clamp((eyeRD - 0.85) * 6.0, 0.0, 1.0)), 0.0, 1.0);
                        float hlY = 0.115 + 0.035 * blink;
                        float hlH = 0.022 * max(blink, 0.3);
                        float hlL = exp(-(sq((q.x + 0.125) / 0.022) + sq((q.y - hlY) / hlH)));
                        float hlR = exp(-(sq((q.x - 0.165) / 0.022) + sq((q.y - hlY) / hlH)));
                        float eyeHl = (blink > 0.35) ? clamp((hlL + hlR) * 1.4, 0.0, 1.0) : 0.0;
                        col = mix(col, vec3(0.04, 0.05, 0.10) + vec3(eyeHl * 0.94), eyeMask);

                        float mouthOpen = 0.018 + 0.048 * voice;
                        float mouthW = 0.052 + 0.015 * voice;
                        float mouthD = sq(q.x / mouthW) + sq((q.y - 0.005) / mouthOpen);
                        float mouthMask = clamp(1.0 - clamp((mouthD - 0.82) * 5.0, 0.0, 1.0), 0.0, 1.0);
                        col = mix(col, vec3(0.18, 0.08, 0.22), mouthMask);
                    }

                    outCol = col;
                    outAlpha = 1.0;
                }

                outCol = outCol / (vec3(1.0) + outCol * 0.40);
                outCol = pow(clamp(outCol, 0.0001, 1.0), vec3(1.0 / 1.08));
                outAlpha = clamp(outAlpha, 0.0, 1.0);
                return vec4(outCol * outAlpha, outAlpha);
            }

            // === MODE 2: 3D HOLOGRAPHIC PASTEL JELLYFISH (Soft Pink + Sky Blue + White + Warm Orange) ===
            vec3 jellyLocal(vec3 p, float t, float voice) {
                float bob = 0.07 * sin(t * 2.6 - 0.8) + 0.025 * voice * sin(t * 8.0);
                p.y -= bob;
                float yaw = 0.25 * sin(t * 0.85);
                float cy = cos(yaw); float sy = sin(yaw);
                p = vec3(p.x * cy + p.z * sy, p.y, -p.x * sy + p.z * cy);
                float tilt = 0.10 * sin(t * 1.1 + 0.4);
                float ct = cos(tilt); float st = sin(tilt);
                return vec3(p.x * ct - p.y * st, p.x * st + p.y * ct, p.z);
            }

            vec4 jellySdfAll(vec3 p, float t, float voice) {
                vec3 q = jellyLocal(p, t, voice);
                float swim = sin(t * 2.6);
                float pulse = 1.0 + 0.09 * swim + 0.06 * voice * sin(t * 9.0);
                float by = q.y - 0.20;
                float ang = atan(q.z, q.x);
                float rib = 0.024 * cos(ang * 8.0) * clamp((-by + 0.18) * 2.4, 0.0, 1.0);
                float bellR = (0.44 * pulse) + rib;
                vec3 e = vec3(q.x / bellR, by / (0.37 / sqrt(max(pulse, 0.2))), q.z / bellR);
                float dOuter = (length(e) - 1.0) * 0.36;
                float rimWave = -0.05 + 0.032 * cos(ang * 8.0) + 0.02 * sin(ang * 4.0 + t * 2.5);
                float dHollow = -(length(vec3(q.x, (by + 0.16) * 1.18, q.z)) - (0.39 * pulse));
                float dCut = rimWave - by;
                float dBell = max(dOuter, min(dHollow, dCut));
                dBell = max(dBell, rimWave - 0.08 - by);

                float dCore = length(vec3(q.x, (by + 0.04) * 1.25, q.z)) - (0.17 + 0.03 * voice);

                float dTent = 10.0;
                float yDrop = clamp(0.10 - q.y, 0.0, 1.05);
                for (int k = 0; k < 6; k++) {
                    float fk = float(k);
                    float a = fk * 1.0472;
                    float radA = (mod(fk, 2.0) < 0.5) ? 0.23 : 0.13;
                    float lenT = (mod(fk, 2.0) < 0.5) ? 0.74 : 0.86;
                    float ph = fk * 1.15;
                    float ax = cos(a) * radA * (0.85 + 0.15 * pulse);
                    float az = sin(a) * radA * (0.85 + 0.15 * pulse);
                    float sway = (0.04 + 0.11 * yDrop) * (1.0 + 0.25 * voice);
                    float tx = ax + sway * sin(yDrop * 6.4 - t * 3.5 + ph);
                    float tz = az + sway * cos(yDrop * 5.8 - t * 3.1 + ph * 1.3);
                    float rt = max(0.009, 0.026 * (1.0 - yDrop / (lenT + 0.05)));
                    float dxy = length(vec2(q.x - tx, q.z - tz)) - rt;
                    float dk = max(dxy, max(q.y - 0.12, (0.10 - lenT) - q.y));
                    dTent = min(dTent, dk);
                }

                float dAll = smin(dBell, dTent, 0.035);
                return vec4(dAll, dBell, dCore, dTent);
            }

            vec4 render3DJellyfish(vec2 uv, float t, float voice) {
                vec3 ro = vec3(0.0, -0.02, 2.70);
                vec3 rd = normalize(vec3(uv, -1.85));

                float r2 = dot(uv, uv);
                float aura = exp(-r2 * (4.0 - 0.6 * voice)) * (0.34 + 0.20 * voice) * smoothstep(0.98, 0.48, sqrt(r2));
                vec3 outCol = vec3(0.68, 0.62, 0.90) * aura;
                float outAlpha = clamp(aura * 0.78, 0.0, 0.50);

                float tRay = 1.40;
                bool hit = false;
                vec3 volCol = vec3(0.0);
                float volA = 0.0;

                for (int i = 0; i < 24; i++) {
                    vec3 p = ro + rd * tRay;
                    vec4 res = jellySdfAll(p, t, voice);
                    vec3 q = jellyLocal(p, t, voice);

                    // Warm Sunset Orange + Soft Pink glowing inner core
                    float coreW = exp(-max(res.z, 0.0) * 13.0) * (0.095 + 0.065 * voice);
                    volCol += vec3(1.25, 0.56, 0.38) * coreW;
                    volA += coreW * 0.75;

                    // Sky Blue -> Soft Pink -> Warm Orange tentacle luminescence
                    float wave = 0.5 + 0.5 * sin(q.y * 10.5 + t * 5.8);
                    float tentW = exp(-max(res.w, 0.0) * 24.0) * 0.080;
                    volCol += vec3(0.55 + 0.65 * wave, 0.82 - 0.18 * wave, 1.15 - 0.55 * wave) * tentW;
                    volA += tentW * 0.70;

                    if (res.x < 0.007) {
                        hit = true;
                        break;
                    }
                    if (tRay > 3.95) break;
                    tRay += max(res.x * 0.84, 0.015);
                }

                outCol += volCol;
                outAlpha = max(outAlpha, clamp(volA, 0.0, 0.88));

                if (hit) {
                    vec3 p = ro + rd * tRay;
                    vec4 res = jellySdfAll(p, t, voice);
                    vec3 q = jellyLocal(p, t, voice);
                    float ang = atan(q.z, q.x);
                    float eps = 0.006;
                    vec3 n = normalize(vec3(
                        jellySdfAll(p + vec3(eps, 0.0, 0.0), t, voice).x - jellySdfAll(p - vec3(eps, 0.0, 0.0), t, voice).x,
                        jellySdfAll(p + vec3(0.0, eps, 0.0), t, voice).x - jellySdfAll(p - vec3(0.0, eps, 0.0), t, voice).x,
                        jellySdfAll(p + vec3(0.0, 0.0, eps), t, voice).x - jellySdfAll(p - vec3(0.0, 0.0, eps), t, voice).x
                    ));

                    vec3 vDir = -rd;
                    vec3 lDir = normalize(vec3(0.42, 0.72, 0.55));
                    vec3 hDir = normalize(lDir + vDir);
                    float ndotv = clamp(dot(n, vDir), 0.0, 1.0);
                    float fresnel = pow(max(1.0 - ndotv, 0.0001), 2.1);
                    float spec = pow(clamp(dot(n, hDir), 0.0001, 1.0), 38.0);

                    float isTent = (res.w < res.y) ? 1.0 : 0.0;
                    float ribGlow = pow(max(0.5 + 0.5 * cos(ang * 8.0), 0.0001), 2.8) * (1.0 - isTent);
                    float waveGlow = (0.5 + 0.5 * sin(q.y * 12.0 + t * 6.0)) * isTent;
                    float topMix = clamp((q.y - 0.15) * 2.2, 0.0, 1.0);

                    vec3 surfCol = vec3(
                        (0.48 + 0.42 * topMix) + 0.52 * ribGlow + 0.62 * waveGlow + fresnel * (0.98 + 0.20 * voice) + spec * 1.05,
                        (0.82 + 0.16 * topMix) + 0.26 * ribGlow + 0.34 * waveGlow + fresnel * (0.64 + 0.15 * voice) + spec * 1.02,
                        (0.98 + 0.02 * topMix) + 0.36 * ribGlow + (0.55 - 0.35 * waveGlow) + fresnel * 0.86 + spec * 1.00
                    );

                    float glassA = clamp(0.42 + 0.55 * fresnel + 0.35 * ribGlow + 0.48 * isTent + 0.45 * spec, 0.0, 0.96);
                    outCol = mix(outCol, surfCol, glassA);
                    outAlpha = max(outAlpha, clamp(glassA + 0.22, 0.0, 1.0));
                }

                outCol = outCol / (vec3(1.0) + outCol * 0.48);
                outCol = pow(clamp(outCol, 0.0001, 1.0), vec3(1.0 / 1.06));
                outAlpha = clamp(outAlpha, 0.0, 1.0);
                return vec4(outCol * outAlpha, outAlpha);
            }

            void main() {
                if (uMode > 1.5) {
                    gl_FragColor = render3DJellyfish(vUv, uTime, uVoice);
                } else if (uMode > 0.5) {
                    gl_FragColor = render3DGhost(vUv, uTime, uVoice);
                } else {
                    gl_FragColor = render3DOrb(vUv, uTime, uVoice);
                }
            }
        """
    }
}
