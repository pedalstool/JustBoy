package com.justgba.ui

import android.content.Context
import android.opengl.GLES20.*
import android.opengl.GLSurfaceView
import android.util.Log
import android.widget.Toast
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
    @Volatile var shaderFiles: List<String> = listOf("", "")
    @Volatile var shaderScales: List<Int> = listOf(1, 1)
    @Volatile var coreWidth = 240
    @Volatile var coreHeight = 160

    private var program = 0
    private var texture = 0
    private var texW = 240
    private var texH = 160
    private var width = 1
    private var height = 1
    private var frame = 0
    private var signature = ""
    private var locImage = -1
    private var locGba = -1
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
    private val vertices = ByteBuffer.allocateDirect(64).order(ByteOrder.nativeOrder()).asFloatBuffer().apply {
        put(floatArrayOf(-1f, -1f, 0f, 1f, 1f, -1f, 1f, 1f, -1f, 1f, 0f, 0f, 1f, 1f, 1f, 0f)); position(0)
    }

    init {
        setEGLContextClientVersion(3)
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
        locGba = glGetUniformLocation(program, "gba")
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
            texW = srcW; texH = srcH; texture = texture(texW, texH, true); signature = ""
        }
        rebuild(); frame++
        if (NativeBridge.nativeFrameReady()) {
            NativeBridge.nativeCopyFrame(pixels); pixels.position(0)
            glActiveTexture(GL_TEXTURE0); glBindTexture(GL_TEXTURE_2D, texture)
            glTexSubImage2D(GL_TEXTURE_2D, 0, 0, 0, srcW, srcH, GL_RGB, GL_UNSIGNED_SHORT_5_6_5, pixels)
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
        glViewport(x, y, w, h); glUseProgram(program); glBindTexture(GL_TEXTURE_2D, input)
        val filter = if (mode == 1) GL_LINEAR else GL_NEAREST
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MIN_FILTER, filter)
        glTexParameteri(GL_TEXTURE_2D, GL_TEXTURE_MAG_FILTER, filter)
        if (locImage >= 0) glUniform1i(locImage, 0)
        if (locGba >= 0) glUniform1i(locGba, if (gbaColors) 1 else 0)
        draw(locPos, locUv)
    }

    private fun outputViewport(): IntArray {
        val nw = coreWidth.coerceAtLeast(1); val nh = coreHeight.coerceAtLeast(1)
        val maxInteger = minOf(width / nw, height / nh).coerceAtLeast(1)
        val (w, h) = when (val requested = screenSize) {
            1 -> { val n = maxInteger; nw * n to nh * n }
            in 2..7 -> { val n = minOf(requested, maxInteger); nw * n to nh * n }
            else -> {
                val fitW = minOf(width, height * nw / nh)
                fitW to fitW * nh / nw
            }
        }
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
