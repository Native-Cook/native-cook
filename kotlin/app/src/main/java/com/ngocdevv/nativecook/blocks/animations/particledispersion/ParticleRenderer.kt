// Adapted from the original renderer/segmentation core without Expo.
package com.ngocdevv.nativecook.blocks.animations.particledispersion

import android.graphics.Bitmap
import android.opengl.GLES30
import android.opengl.GLUtils
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.sqrt

internal data class ParticleFrameIdentity(
  val frameUri: String,
  val maskUri: String
)

/** A complete immutable prop batch tagged with its image generation. */
internal data class ParticleRenderState(
  val generation: Long,
  val identity: ParticleFrameIdentity,
  val logicalWidth: Float,
  val logicalHeight: Float,
  val timeSeconds: Float,
  val progress: Float,
  val dustStartSeconds: Float,
  val dustDurationSeconds: Float,
  val seedBits: Int,
  val reduceMotion: Boolean,
  val backgroundColor: FloatArray,
  val subjectBounds: FloatArray,
  val edgeParticleStrength: Float
)

/** Ownership moves from the decode executor to the EGL thread. */
internal class ParticleBitmapPair(
  val generation: Long,
  val identity: ParticleFrameIdentity,
  val frame: Bitmap,
  val mask: Bitmap
) {
  fun recycle() {
    if (!frame.isRecycled) {
      frame.recycle()
    }
    if (mask !== frame && !mask.isRecycled) {
      mask.recycle()
    }
  }
}

internal class ParticleRendererException(message: String, cause: Throwable? = null) :
  RuntimeException(message, cause)

/**
 * Stateless GLES renderer. It owns only shader programs and the current image pair;
 * all animation state is supplied by the native display clock for every requested frame.
 */
internal class ParticleRenderer {
  companion object {
    const val MAXIMUM_PARTICLE_COUNT = 300_000
  }

  private val backgroundProgram = createProgram(
    ParticleShaders.BACKGROUND_VERTEX,
    ParticleShaders.BACKGROUND_FRAGMENT,
    "captured-frame backplate"
  )
  private val particleProgram = createProgram(
    ParticleShaders.PARTICLE_VERTEX,
    ParticleShaders.PARTICLE_FRAGMENT,
    "independent image dust"
  )

  private val backgroundLocations = BackgroundLocations(backgroundProgram)
  private val particleLocations = ParticleLocations(particleProgram)
  private val subjectProgram = createProgram(ParticleShaders.BACKGROUND_VERTEX, SubjectShader.FRAGMENT, "subject composition")
  private val subjectLocations = SubjectLocations(subjectProgram)

  private var frameTexture = 0
  private var maskTexture = 0

  init {
    GLES30.glDisable(GLES30.GL_DEPTH_TEST)
    GLES30.glDisable(GLES30.GL_STENCIL_TEST)
    GLES30.glDisable(GLES30.GL_CULL_FACE)
    GLES30.glDisable(GLES30.GL_SCISSOR_TEST)
    GLES30.glDisable(GLES30.GL_DITHER)
    checkGlError("initialize particle renderer")
  }

  fun uploadTextures(frame: Bitmap, mask: Bitmap) {
    if (frame.width <= 0 || frame.height <= 0 || mask.width <= 0 || mask.height <= 0) {
      throw ParticleRendererException("Particle textures must have positive dimensions.")
    }

    val maximumTextureSize = IntArray(1)
    GLES30.glGetIntegerv(GLES30.GL_MAX_TEXTURE_SIZE, maximumTextureSize, 0)
    if (
      frame.width > maximumTextureSize[0] || frame.height > maximumTextureSize[0] ||
      mask.width > maximumTextureSize[0] || mask.height > maximumTextureSize[0]
    ) {
      throw ParticleRendererException("A particle image exceeds the GPU texture-size limit.")
    }

    clearTextures()
    var newFrameTexture = 0
    var newMaskTexture = 0
    try {
      newFrameTexture = uploadTexture(frame)
      newMaskTexture = uploadTexture(mask)
      frameTexture = newFrameTexture
      maskTexture = newMaskTexture
    } catch (error: Throwable) {
      deleteTexture(newFrameTexture)
      deleteTexture(newMaskTexture)
      throw ParticleRendererException("Could not upload the particle images to the GPU.", error)
    }
  }

  fun clearTextures() {
    deleteTexture(frameTexture)
    deleteTexture(maskTexture)
    frameTexture = 0
    maskTexture = 0
  }

  fun render(state: ParticleRenderState, drawableWidth: Int, drawableHeight: Int) {
    validate(state, drawableWidth, drawableHeight)
    if (frameTexture == 0 || maskTexture == 0) {
      throw ParticleRendererException("Particle textures are not ready.")
    }

    val grid = particleGrid(state.logicalWidth, state.logicalHeight)
    val dustTime = state.timeSeconds - state.dustStartSeconds
    val drawsParticles = !state.reduceMotion &&
      state.timeSeconds >= 0.400f &&
      dustTime >= 0f &&
      dustTime < state.dustDurationSeconds
    val drawsBackplate = state.reduceMotion || state.timeSeconds < 0.400f || drawsParticles

    GLES30.glViewport(0, 0, drawableWidth, drawableHeight)
    GLES30.glDisable(GLES30.GL_BLEND)

    if (drawsBackplate) {
      drawBackground(state)
    } else {
      val color = state.backgroundColor
      GLES30.glClearColor(
        color[0].coerceIn(0f, 1f),
        color[1].coerceIn(0f, 1f),
        color[2].coerceIn(0f, 1f),
        1f
      )
      GLES30.glClear(GLES30.GL_COLOR_BUFFER_BIT)
    }

    if (drawsParticles) {
      drawParticles(state, drawableWidth, drawableHeight, grid)
    }
    drawSubject(state)
    checkGlError("render particle frame")
  }

  fun release() {
    clearTextures()
    GLES30.glDeleteProgram(backgroundProgram)
    GLES30.glDeleteProgram(particleProgram)
    GLES30.glDeleteProgram(subjectProgram)
  }

  private fun drawSubject(state: ParticleRenderState) {
    val locations = subjectLocations
    GLES30.glEnable(GLES30.GL_BLEND)
    GLES30.glBlendFunc(GLES30.GL_ONE, GLES30.GL_ONE_MINUS_SRC_ALPHA)
    GLES30.glUseProgram(subjectProgram)
    bindTexture(frameTexture, 0, locations.frame)
    bindTexture(maskTexture, 1, locations.mask)
    GLES30.glUniform2f(locations.logicalSize, state.logicalWidth, state.logicalHeight)
    GLES30.glUniform4fv(locations.subjectBounds, 1, state.subjectBounds, 0)
    GLES30.glUniform1f(locations.timeSeconds, state.timeSeconds)
    GLES30.glUniform1i(locations.reduceMotion, if (state.reduceMotion) 1 else 0)
    GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
  }

  private fun drawBackground(state: ParticleRenderState) {
    val locations = backgroundLocations
    GLES30.glUseProgram(backgroundProgram)
    bindTexture(frameTexture, 0, locations.frameTexture)
    GLES30.glUniform2f(locations.logicalSize, state.logicalWidth, state.logicalHeight)
    GLES30.glUniform4fv(locations.backgroundColor, 1, state.backgroundColor, 0)
    GLES30.glUniform1f(locations.timeSeconds, state.timeSeconds)
    GLES30.glUniform1f(locations.progress, state.progress)
    GLES30.glUniform1ui(locations.reduceMotion, if (state.reduceMotion) 1 else 0)
    GLES30.glDrawArrays(GLES30.GL_TRIANGLES, 0, 3)
  }

  private fun drawParticles(
    state: ParticleRenderState,
    drawableWidth: Int,
    drawableHeight: Int,
    grid: ParticleGrid
  ) {
    val locations = particleLocations
    GLES30.glEnable(GLES30.GL_BLEND)
    GLES30.glBlendEquationSeparate(GLES30.GL_FUNC_ADD, GLES30.GL_FUNC_ADD)
    GLES30.glBlendFuncSeparate(
      GLES30.GL_ONE,
      GLES30.GL_ONE_MINUS_SRC_ALPHA,
      GLES30.GL_ONE,
      GLES30.GL_ONE_MINUS_SRC_ALPHA
    )

    GLES30.glUseProgram(particleProgram)
    bindTexture(frameTexture, 0, locations.frameTexture)
    bindTexture(maskTexture, 1, locations.maskTexture)
    GLES30.glUniform2f(locations.logicalSize, state.logicalWidth, state.logicalHeight)
    GLES30.glUniform2f(
      locations.drawableSize,
      drawableWidth.toFloat(),
      drawableHeight.toFloat()
    )
    GLES30.glUniform4fv(locations.subjectBounds, 1, state.subjectBounds, 0)
    GLES30.glUniform1f(locations.timeSeconds, state.timeSeconds)
    GLES30.glUniform1f(locations.dustStartSeconds, state.dustStartSeconds)
    GLES30.glUniform1f(locations.dustDurationSeconds, state.dustDurationSeconds)
    GLES30.glUniform1ui(locations.seed, state.seedBits)
    GLES30.glUniform1ui(locations.columns, grid.columns)
    GLES30.glUniform1ui(locations.particleCount, grid.count)
    GLES30.glUniform1f(locations.cellSize, grid.cellSize)
    GLES30.glUniform1f(locations.edgeParticleStrength, state.edgeParticleStrength)
    GLES30.glDrawArrays(GLES30.GL_POINTS, 0, grid.count)
    GLES30.glDisable(GLES30.GL_BLEND)
  }

  private fun validate(state: ParticleRenderState, drawableWidth: Int, drawableHeight: Int) {
    val scalars = floatArrayOf(
      state.logicalWidth,
      state.logicalHeight,
      drawableWidth.toFloat(),
      drawableHeight.toFloat(),
      state.timeSeconds,
      state.progress,
      state.dustStartSeconds,
      state.dustDurationSeconds,
      state.edgeParticleStrength,
      *state.backgroundColor,
      *state.subjectBounds
    )
    if (
      scalars.any { !it.isFinite() } ||
      state.logicalWidth <= 0f || state.logicalHeight <= 0f ||
      state.logicalWidth > 32_768f || state.logicalHeight > 32_768f ||
      drawableWidth <= 0 || drawableHeight <= 0 ||
      state.dustDurationSeconds <= 0f ||
      state.backgroundColor.size != 4 || state.subjectBounds.size != 4
    ) {
      throw ParticleRendererException(
        "Particle rendering requires finite values and positive viewport dimensions."
      )
    }
  }

  private fun particleGrid(width: Float, height: Float): ParticleGrid {
    val viewportScale = (width / 390f).coerceIn(0.82f, 1.30f)
    val limit = MAXIMUM_PARTICLE_COUNT.toFloat()
    var cellSize = max(1.1f * viewportScale, sqrt(width * height / limit))
    var columns = ceil(width / cellSize).toInt().coerceAtLeast(1)
    var rows = ceil(height / cellSize).toInt().coerceAtLeast(1)

    while (columns.toLong() * rows.toLong() > MAXIMUM_PARTICLE_COUNT.toLong()) {
      val excess = (columns.toFloat() * rows.toFloat()) / limit
      cellSize *= max(1.001f, sqrt(excess))
      columns = ceil(width / cellSize).toInt().coerceAtLeast(1)
      rows = ceil(height / cellSize).toInt().coerceAtLeast(1)
    }
    return ParticleGrid(columns, columns * rows, cellSize)
  }

  private fun uploadTexture(bitmap: Bitmap): Int {
    val texture = IntArray(1)
    GLES30.glGenTextures(1, texture, 0)
    if (texture[0] == 0) {
      throw ParticleRendererException("The GPU could not allocate a particle texture.")
    }

    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture[0])
    GLES30.glTexParameteri(
      GLES30.GL_TEXTURE_2D,
      GLES30.GL_TEXTURE_MIN_FILTER,
      GLES30.GL_LINEAR
    )
    GLES30.glTexParameteri(
      GLES30.GL_TEXTURE_2D,
      GLES30.GL_TEXTURE_MAG_FILTER,
      GLES30.GL_LINEAR
    )
    GLES30.glTexParameteri(
      GLES30.GL_TEXTURE_2D,
      GLES30.GL_TEXTURE_WRAP_S,
      GLES30.GL_CLAMP_TO_EDGE
    )
    GLES30.glTexParameteri(
      GLES30.GL_TEXTURE_2D,
      GLES30.GL_TEXTURE_WRAP_T,
      GLES30.GL_CLAMP_TO_EDGE
    )
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_BASE_LEVEL, 0)
    GLES30.glTexParameteri(GLES30.GL_TEXTURE_2D, GLES30.GL_TEXTURE_MAX_LEVEL, 0)
    GLES30.glPixelStorei(GLES30.GL_UNPACK_ALIGNMENT, 1)
    GLUtils.texImage2D(GLES30.GL_TEXTURE_2D, 0, bitmap, 0)
    checkGlError("upload particle texture")
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, 0)
    return texture[0]
  }

  private fun bindTexture(texture: Int, unit: Int, samplerLocation: Int) {
    GLES30.glActiveTexture(GLES30.GL_TEXTURE0 + unit)
    GLES30.glBindTexture(GLES30.GL_TEXTURE_2D, texture)
    GLES30.glUniform1i(samplerLocation, unit)
  }

  private fun deleteTexture(texture: Int) {
    if (texture != 0) {
      GLES30.glDeleteTextures(1, intArrayOf(texture), 0)
    }
  }

  private data class ParticleGrid(
    val columns: Int,
    val count: Int,
    val cellSize: Float
  )

  private class BackgroundLocations(program: Int) {
    val frameTexture = uniform(program, "frameTexture")
    val logicalSize = uniform(program, "logicalSize")
    val backgroundColor = uniform(program, "backgroundColor")
    val timeSeconds = uniform(program, "timeSeconds")
    val progress = uniform(program, "progress")
    val reduceMotion = uniform(program, "reduceMotion")
  }

  private class SubjectLocations(program: Int) {
    val frame = uniform(program, "frame")
    val mask = uniform(program, "mask")
    val logicalSize = uniform(program, "logicalSize")
    val subjectBounds = uniform(program, "subjectBounds")
    val timeSeconds = uniform(program, "timeSeconds")
    val reduceMotion = uniform(program, "reduceMotion")
  }

  private class ParticleLocations(program: Int) {
    val frameTexture = uniform(program, "frameTexture")
    val maskTexture = uniform(program, "maskTexture")
    val logicalSize = uniform(program, "logicalSize")
    val drawableSize = uniform(program, "drawableSize")
    val subjectBounds = uniform(program, "subjectBounds")
    val timeSeconds = uniform(program, "timeSeconds")
    val dustStartSeconds = uniform(program, "dustStartSeconds")
    val dustDurationSeconds = uniform(program, "dustDurationSeconds")
    val seed = uniform(program, "seed")
    val columns = uniform(program, "columns")
    val particleCount = uniform(program, "particleCount")
    val cellSize = uniform(program, "cellSize")
    val edgeParticleStrength = uniform(program, "edgeParticleStrength")
  }
}

private fun createProgram(vertexSource: String, fragmentSource: String, label: String): Int {
  val vertexShader = compileShader(GLES30.GL_VERTEX_SHADER, vertexSource, "$label vertex")
  val fragmentShader = compileShader(GLES30.GL_FRAGMENT_SHADER, fragmentSource, "$label fragment")
  val program = GLES30.glCreateProgram()
  if (program == 0) {
    GLES30.glDeleteShader(vertexShader)
    GLES30.glDeleteShader(fragmentShader)
    throw ParticleRendererException("The GPU could not create the $label shader program.")
  }

  GLES30.glAttachShader(program, vertexShader)
  GLES30.glAttachShader(program, fragmentShader)
  GLES30.glLinkProgram(program)
  val status = IntArray(1)
  GLES30.glGetProgramiv(program, GLES30.GL_LINK_STATUS, status, 0)
  val log = GLES30.glGetProgramInfoLog(program)
  GLES30.glDetachShader(program, vertexShader)
  GLES30.glDetachShader(program, fragmentShader)
  GLES30.glDeleteShader(vertexShader)
  GLES30.glDeleteShader(fragmentShader)
  if (status[0] != GLES30.GL_TRUE) {
    GLES30.glDeleteProgram(program)
    throw ParticleRendererException(
      "Could not link the $label shader program${formatShaderLog(log)}"
    )
  }
  return program
}

private fun compileShader(type: Int, source: String, label: String): Int {
  val shader = GLES30.glCreateShader(type)
  if (shader == 0) {
    throw ParticleRendererException("The GPU could not create the $label shader.")
  }
  GLES30.glShaderSource(shader, source)
  GLES30.glCompileShader(shader)
  val status = IntArray(1)
  GLES30.glGetShaderiv(shader, GLES30.GL_COMPILE_STATUS, status, 0)
  if (status[0] != GLES30.GL_TRUE) {
    val log = GLES30.glGetShaderInfoLog(shader)
    GLES30.glDeleteShader(shader)
    throw ParticleRendererException("Could not compile the $label shader${formatShaderLog(log)}")
  }
  return shader
}

private fun uniform(program: Int, name: String): Int {
  val location = GLES30.glGetUniformLocation(program, name)
  if (location < 0) {
    throw ParticleRendererException("The particle shader uniform $name is unavailable.")
  }
  return location
}

private fun checkGlError(operation: String) {
  val error = GLES30.glGetError()
  if (error != GLES30.GL_NO_ERROR) {
    throw ParticleRendererException(
      "The GPU failed to $operation (OpenGL error 0x${error.toString(16)})."
    )
  }
}

private fun formatShaderLog(log: String?): String {
  return if (log.isNullOrBlank()) "." else ": ${log.trim()}"
}
