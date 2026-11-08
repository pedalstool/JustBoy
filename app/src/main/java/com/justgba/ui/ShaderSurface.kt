package com.justgba.ui

import android.content.Context
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.util.Log
import android.view.Choreographer
import android.widget.Toast
import kotlin.math.roundToInt
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.findViewTreeLifecycleOwner
import com.justgba.emulator.NativeBridge
import java.io.File
import java.nio.ByteBuffer
import java.nio.ByteOrder
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

class ShaderSurface(context: Context) : GLSurfaceView(context), GLSurfaceView.Renderer {
    private var owner: LifecycleOwner? = null
    private val observer = object : DefaultLifecycleObserver {
        override fun onPause(owner: LifecycleOwner) { this@ShaderSurface.onPause() }
        override fun onResume(owner: LifecycleOwner) { this@ShaderSurface.onResume() }
    }
    override fun onAttachedToWindow() {
        super.onAttachedToWindow()
        owner = findViewTreeLifecycleOwner()
        owner?.lifecycle?.addObserver(observer)
    }
    override fun onDetachedFromWindow() {
        owner?.lifecycle?.removeObserver(observer)
        owner = null
        super.onDetachedFromWindow()
    }

    @Volatile var shaderMode = 0
    @Volatile var gbaColors = false
    @Volatile var screenSize = 0
    @Volatile var picture = com.justgba.settings.Picture()
    @Volatile var frameRate = 60
        set(value) {
            if (field == value) return
            field = value
            post { updatePresentation() }
        }
    @Volatile var shaderFiles: List<String> = listOf("", "")
    @Volatile var shaderScales: List<Int> = listOf(1, 1)
    @Volatile var coreWidth = 240
    @Volatile var coreHeight = 160

    private var program = 0
    private var texture = 0
    private var previousTexture = 0
    private var haveFrame = false
    private var havePrevious = false
    private var lastEmuNs = 0L
    private var intervalNs = 16_742_706L
    private var presentPosted = false
    private var presentAcc = 0
    private val frameCallback = object : Choreographer.FrameCallback {
        override fun doFrame(frameTimeNanos: Long) {
            if (!presentPosted) return
            val target = frameRate
            if (target <= 60) {
                presentPosted = false
                return
            }
            val hz = (display?.refreshRate ?: 60f).roundToInt().coerceAtLeast(30)
            presentAcc += target
            if (presentAcc >= hz) {
                presentAcc -= hz
                requestRender()
            }
            Choreographer.getInstance().postFrameCallback(this)
        }
    }
    private var texW = 240
    private var texH = 160
    private var width = 1
    private var height = 1
    private var frame = 0
    private var signature = ""
    private var locImage = -1
    private var locPrevious = -1
    private var locFrameMix = -1
    private var locGba = -1
    private var locCrisp = -1
    private var locTexSize = -1
    private var locOutSize = -1
    private var locCoreSize = -1
    private var locBrightness = -1
    private var locContrast = -1
    private var locSaturation = -1
    private var locTemperature = -1
    private var locSharpness = -1
    private var locPos = -1
    private var locUv = -1
    private val mvp = floatArrayOf(1f, 0f, 0f, 0f, 0f, -1f, 0f, 0f, 0f, 0f, 1f, 0f, 0f, 0f, 0f, 1f)
    private val passes = mutableListOf<Pass>()
    private data class Pass(
        val program: Int, val texture: Int, val fbo: Int, val width: Int, val height: Int,
        val position: Int, val uv: Int,
        val samplers: IntArray, val textureSize: Int, val inputSize: Int,
        val outputSize: Int, val frameCount: Int, val frameDirection: Int, val mvp: Int,
    )
    private val pixels = ByteBuffer.allocateDirect(256 * 224 * 2).order(ByteOrder.nativeOrder())
    private val previousPixels = ByteBuffer.allocateDirect(256 * 224 * 2).order(ByteOrder.nativeOrder())
    private val vertices = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 0f, 1f, 1f, -1f, 1f, 1f, -1f, 1f, 0f, 0f, 1f, 1f, 1f, 0f)); position(0)
    }

    init {
        setEGLContextClientVersion(3)
        setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        setRenderer(this)
        preserveEGLContextOnPause = true
    }

    private fun compile(type: Int, source: String): Int {
        val id = glCreateShader(type); glShaderSource(id, source); glCompileShader(id)
        val ok = IntArray(1); glGetShaderiv(id, GL_COMPILE_STATUS, ok, 0)
        if (ok[0] == 0) { val log = glGetShaderInfoLog(id); glDeleteShader(id); error(log) }
        return id
    }

    private fun link(vertex: String, fragment: String): Int {
        val v = compile(GL_VERTEX_SHADER, vertex)
        val f = try { compile(GL_FRAGMENT_SHADER, fragment) } catch (e: Exception) { glDeleteShader(v); throw e }
        val p = glCreateProgram(); glAttachShader(p, v); glAttachShader(p, f); glLinkProgram(p); glDeleteShader(v); glDeleteShader(f)
        val ok = IntArray(1); glGetProgramiv(p, GL_LINK_STATUS, ok, 0)
        if (ok[0] == 0) { val log = glGetProgramInfoLog(p); glDeleteProgram(p); error(log) }
        return p
    }

    private fun cacheDisplayLocations() {
        locImage = glGetUniformLocation(program, "image")
        locPrevious = glGetUniformLocation(program, "previous")
        locFrameMix = glGetUniformLocation(program, "frameMix")
        locGba = glGetUniformLocation(program, "gba")
        locCrisp = glGetUniformLocation(program, "crisp")
        locTexSize = glGetUniformLocation(program, "texSize")
        locOutSize = glGetUniformLocation(program, "outSize")
        locCoreSize = glGetUniformLocation(program, "coreSize")
        locBrightness = glGetUniformLocation(program, "brightness")
        locContrast = glGetUniformLocation(program, "contrast")
        locSaturation = glGetUniformLocation(program, "saturation")
        locTemperature = glGetUniformLocation(program, "temperature")
        locSharpness = glGetUniformLocation(program, "sharpness")
        locPos = glGetAttribLocation(program, "pos")
        locUv = glGetAttribLocation(program, "uv")
    }

    private fun asset(name: String) = context.assets.open("shaders/$name").bufferedReader().use { it.readText() }

    private fun texture(w: Int, h: Int, rgb565: Boolean = false): Int {
        val id = IntArray(1); glGenTextures(1, id, 0); glBindTexture(GL_TEXTURE_2D, id[0])
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_NEAREST)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_S, GL_CLAMP_TO_EDGE)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_WRAP_T, GL_CLAMP_TO_EDGE)
        glTexImage2D(
            GL_TEXTURE_2D, 0, if (rgb565) GL_RGB else GL_RGBA, w, h, 0,
            if (rgb565) GL_RGB else GL_RGBA, if (rgb565) GL_UNSIGNED_SHORT_5_6_5 else GL_UNSIGNED_BYTE, null
        )
        return id[0]
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        passes.clear(); signature = ""
        program = link(asset("display.vert"), asset("display.frag"))
        cacheDisplayLocations()
        texW = coreWidth.coerceAtLeast(1); texH = coreHeight.coerceAtLeast(1)
        texture = texture(texW, texH, true)
        previousTexture = texture(texW, texH, true)
        haveFrame = false
        havePrevious = false
    }

    private fun updatePresentation() {
        if (frameRate <= 60) {
            presentPosted = false
            setRenderMode(RENDERMODE_CONTINUOUSLY)
            return
        }
        setRenderMode(RENDERMODE_WHEN_DIRTY)
        if (!presentPosted) {
            presentPosted = true
            presentAcc = 0
            Choreographer.getInstance().postFrameCallback(frameCallback)
        }
    }

    override fun onResume() {
        super.onResume()
        updatePresentation()
    }

    override fun onPause() {
        presentPosted = false
        super.onPause()
    }

    override fun onSurfaceChanged(gl: GL10?, w: Int, h: Int) { width = w; height = h }

    private fun source(text: String, stage: String): String {
        var s = text.replace(Regex("(?m)^\\s*#version[^\\n]*"), "").replace(Regex("(?m)^\\s*#pragma parameter[^\\n]*"), "")
        s = s.replace(Regex("\\battribute\\b"), "in").replace(Regex("\\bvarying\\b"), if (stage == "VERTEX") "out" else "in")
            .replace(Regex("\\btexture2D\\b"), "texture")
        val namedOutput = Regex("\\bout\\s+vec4\\s+(\\w+)").find(s)?.groupValues?.get(1)
        if (stage == "FRAGMENT" && namedOutput != null) s = s.replace("gl_FragColor", namedOutput)
        val output = if (stage == "FRAGMENT" && namedOutput == null && s.contains("gl_FragColor"))
            "out vec4 appFragColor;\n#define gl_FragColor appFragColor\n" else ""
        return "#version 300 es\nprecision highp float;\n#define $stage\n" + output + s
    }

    private fun createPass(text: String, w: Int, h: Int): Pass {
        require(!text.contains("#include")) { "External #include is unsupported" }
        val combined = text.contains("VERTEX") && text.contains("FRAGMENT")
        val vertex = if (combined) source(text, "VERTEX") else {
            val varyings = Regex("(?:varying|in)\\s+(?:(?:lowp|mediump|highp)\\s+)?(vec[234])\\s+(\\w+)\\s*;")
                .findAll(text).map { it.groupValues[1] to it.groupValues[2] }.toList()
            val declarations = varyings.joinToString("\n") { (type, name) -> "out $type $name;" }
            val assignments = varyings.joinToString("\n") { (type, name) ->
                "$name=" + when (type) { "vec2" -> "TexCoord"; "vec3" -> "vec3(TexCoord,0.)"; else -> "vec4(TexCoord,0.,1.)" } + ";"
            }
            "#version 300 es\nprecision highp float;\nin vec2 VertexCoord; in vec2 TexCoord;\n$declarations\nvoid main(){gl_Position=vec4(VertexCoord.x,-VertexCoord.y,0.,1.);$assignments}"
        }
        val p = link(vertex, source(text, "FRAGMENT"))
        val t = texture(w, h)
        val id = IntArray(1); glGenFramebuffers(1, id, 0)
        glBindFramebuffer(GL_FRAMEBUFFER, id[0])
        glFramebufferTexture2D(GL_FRAMEBUFFER, GL_COLOR_ATTACHMENT0, GL_TEXTURE_2D, t, 0)
        if (glCheckFramebufferStatus(GL_FRAMEBUFFER) != GL_FRAMEBUFFER_COMPLETE) {
            glDeleteProgram(p); glDeleteTextures(1, intArrayOf(t), 0); glDeleteFramebuffers(1, id, 0)
            error("Shader framebuffer unavailable")
        }
        return Pass(
            p, t, id[0], w, h,
            glGetAttribLocation(p, "VertexCoord"), glGetAttribLocation(p, "TexCoord"),
            arrayOf("decal", "Texture", "Source", "image", "tex0").map { glGetUniformLocation(p, it) }.toIntArray(),
            glGetUniformLocation(p, "TextureSize"), glGetUniformLocation(p, "InputSize"),
            glGetUniformLocation(p, "OutputSize"), glGetUniformLocation(p, "FrameCount"),
            glGetUniformLocation(p, "FrameDirection"), glGetUniformLocation(p, "MVPMatrix"),
        )
    }

    private fun activeShaderMode() = if (shaderMode == 2 || shaderMode == 3) 0 else shaderMode

    private fun rebuild() {
        val mode = activeShaderMode(); val files = shaderFiles; val scales = shaderScales
        val srcW = coreWidth.coerceAtLeast(1); val srcH = coreHeight.coerceAtLeast(1)
        val next = "$mode|$srcW|$srcH|${files.joinToString("|")}|${scales.joinToString()}"
        if (signature == next) return
        signature = next
        passes.forEach {
            glDeleteProgram(it.program); glDeleteTextures(1, intArrayOf(it.texture), 0)
            glDeleteFramebuffers(1, intArrayOf(it.fbo), 0)
        }
        passes.clear()
        val stages = mutableListOf<Pair<String, Int>>()
        if (mode == 4) stages.add(asset("2xbr.glsl") to 2)
        files.forEachIndexed { i, path -> if (path.isNotEmpty()) stages.add(path to (scales.getOrNull(i) ?: 1)) }
        var w = srcW; var h = srcH
        for ((textOrPath, scale) in stages) try {
            val text = if (textOrPath.contains("\n")) textOrPath else File(textOrPath).readText()
            val nw = w * scale; val nh = h * scale
            require(nw <= 1920 && nh <= 1280) { "Shader chain exceeds size limit" }
            passes.add(createPass(text, nw, nh)); w = nw; h = nh
        } catch (e: Exception) {
            Log.e("JustBoy-Shaders", "Shader rejected", e)
            post { Toast.makeText(context, "Shader skipped: ${e.message?.take(220)}", Toast.LENGTH_LONG).show() }
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0)
    }

    override fun onDrawFrame(gl: GL10?) {
        val srcW = coreWidth.coerceAtLeast(1); val srcH = coreHeight.coerceAtLeast(1)
        if (srcW != texW || srcH != texH) {
            if (texture != 0) glDeleteTextures(1, intArrayOf(texture), 0)
            if (previousTexture != 0) glDeleteTextures(1, intArrayOf(previousTexture), 0)
            texW = srcW; texH = srcH
            texture = texture(texW, texH, true)
            previousTexture = texture(texW, texH, true)
            haveFrame = false
            havePrevious = false
            signature = ""
        }
        rebuild(); frame++
        if (NativeBridge.nativeFrameReady()) {
            val bytes = srcW * srcH * 2
            if (haveFrame && previousTexture != 0) {
                pixels.limit(bytes).position(0)
                previousPixels.limit(bytes).position(0)
                previousPixels.put(pixels)
                previousPixels.position(0)
                glActiveTexture(GL_TEXTURE0)
                glBindTexture(GL_TEXTURE_2D, previousTexture)
                glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, srcW, srcH, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, previousPixels)
                havePrevious = true
            }
            val now = System.nanoTime()
            if (lastEmuNs != 0L) {
                val delta = now - lastEmuNs
                if (delta in 2_000_000L..50_000_000L) intervalNs = delta
            }
            lastEmuNs = now
            pixels.clear()
            NativeBridge.nativeCopyFrame(pixels)
            pixels.position(0)
            glActiveTexture(GL_TEXTURE0)
            glBindTexture(GL_TEXTURE_2D, texture)
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, srcW, srcH, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, pixels)
            haveFrame = true
        }
        var input = texture; var iw = srcW; var ih = srcH
        for (pass in passes) {
            glBindFramebuffer(GL_FRAMEBUFFER, pass.fbo); glViewport(0, 0, pass.width, pass.height)
            glUseProgram(pass.program); glBindTexture(GL_TEXTURE_2D, input)
            for (loc in pass.samplers) if (loc >= 0) glUniform1i(loc, 0)
            if (pass.textureSize >= 0) glUniform2f(pass.textureSize, iw.toFloat(), ih.toFloat())
            if (pass.inputSize >= 0) glUniform2f(pass.inputSize, iw.toFloat(), ih.toFloat())
            if (pass.outputSize >= 0) glUniform2f(pass.outputSize, pass.width.toFloat(), pass.height.toFloat())
            if (pass.frameCount >= 0) glUniform1i(pass.frameCount, frame)
            if (pass.frameDirection >= 0) glUniform1i(pass.frameDirection, 1)
            if (pass.mvp >= 0) glUniformMatrix4fv(pass.mvp, 1, false, mvp, 0)
            draw(pass.position, pass.uv)
            input = pass.texture; iw = pass.width; ih = pass.height
        }
        glBindFramebuffer(GL_FRAMEBUFFER, 0); glViewport(0, 0, width, height)
        glClearColor(0f, 0f, 0f, 1f); glClear(GL_COLOR_BUFFER_BIT)
        val mode = activeShaderMode()
        val viewport = outputViewport()
        val x = viewport[0]; val y = viewport[1]; val w = viewport[2]; val h = viewport[3]
        val pic = picture
        val mix = if (frameRate <= 60 || !havePrevious || passes.isNotEmpty()) 1f else {
            val age = (System.nanoTime() - lastEmuNs).coerceAtLeast(0L)
            (age.toFloat() / intervalNs.toFloat()).coerceIn(0f, 1f)
        }
        glViewport(x, y, w, h); glUseProgram(program)
        glActiveTexture(GL_TEXTURE1)
        glBindTexture(GL_TEXTURE_2D, if (previousTexture != 0) previousTexture else input)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        glActiveTexture(GL_TEXTURE0)
        glBindTexture(GL_TEXTURE_2D, input)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, GL_LINEAR)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, GL_LINEAR)
        if (locImage >= 0) glUniform1i(locImage, 0)
        if (locPrevious >= 0) glUniform1i(locPrevious, 1)
        if (locFrameMix >= 0) glUniform1f(locFrameMix, mix)
        if (locGba >= 0) glUniform1i(locGba, if (gbaColors) 1 else 0)
        if (locCrisp >= 0) glUniform1i(locCrisp, if (mode == 1) 0 else 1)
        if (locTexSize >= 0) glUniform2f(locTexSize, iw.toFloat(), ih.toFloat())
        if (locOutSize >= 0) glUniform2f(locOutSize, w.toFloat(), h.toFloat())
        if (locCoreSize >= 0) glUniform2f(locCoreSize, srcW.toFloat(), srcH.toFloat())
        if (locBrightness >= 0) glUniform1f(locBrightness, pic.brightness)
        if (locContrast >= 0) glUniform1f(locContrast, pic.contrast)
        if (locSaturation >= 0) glUniform1f(locSaturation, pic.saturation)
        if (locTemperature >= 0) glUniform1f(locTemperature, pic.temperature)
        if (locSharpness >= 0) glUniform1f(locSharpness, pic.sharpness)
        draw(locPos, locUv)
    }

    private fun outputViewport(): IntArray {
        val (w, h) = VideoFrame.size(width, height, coreWidth, coreHeight, screenSize)
        return intArrayOf((width - w) / 2, (height - h) / 2, w, h)
    }

    private fun draw(pos: Int, uv: Int) {
        if (pos >= 0) { vertices.position(0); glVertexAttribPointer(pos, 2, GL_FLOAT, false, 16, vertices); glEnableVertexAttribArray(pos) }
        if (uv >= 0) { vertices.position(2); glVertexAttribPointer(uv, 2, GL_FLOAT, false, 16, vertices); glEnableVertexAttribArray(uv) }
        glDrawArrays(GL_TRIANGLE_STRIP, 0, 4)
        if (pos >= 0) glDisableVertexAttribArray(pos)
        if (uv >= 0) glDisableVertexAttribArray(uv)
    }
}
