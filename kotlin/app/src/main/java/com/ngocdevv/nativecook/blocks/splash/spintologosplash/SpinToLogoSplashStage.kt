package com.ngocdevv.nativecook.blocks.splash.spintologosplash

import android.content.Context
import com.google.android.filament.Camera

internal const val SPIN_ASSET_DIR = "spintologosplash"

internal suspend fun loadSpinAssets(context: Context): F3Assets =
    loadF3Assets(context, SPIN_ASSET_DIR, "spintologosplash_arca.glb", "spintologosplash_arca-studio-ibl.ktx")

/**
 * The Filament stage (SpinStage3D.tsx): the Arca sculpture alone, lit by the studio IBL, a warm key and a cool fill,
 * with a touch of bloom. The paper, the floor track and the shadows are drawn in Compose underneath, the flat mark over
 * it. It never runs a clock of its own: [pose] shows what the block's clock says.
 */
internal class SpinWorld(assets: F3Assets, refreshRate: Float) : F3World(
    assets,
    F3Options(
        iblIntensity = IBL_INTENSITY,
        acesExposure = EXPOSURE,
        bloomStrength = 0.05f,
        bloomLevels = 5,
        bloomThreshold = true,
        bloomHighlight = 1000f,
    ),
    refreshRate,
) {
    private val rig = entity("ArcaRig")
    private val arches = intArrayOf(entity("ArchOuter"), entity("ArchMiddle"), entity("ArchInner"))
    private val pearl = entity("Pearl")
    private val m = FloatArray(16)
    private val cam = FloatArray(6)

    init {
        addDirectional(-0.45f, -0.8f, -0.4f, KEY_LUX, 5400f)
        addDirectional(0.6f, -0.25f, -0.75f, FILL_LUX, 7200f)
    }

    override fun setViewport(width: Int, height: Int) {
        super.setViewport(width, height)
        camera.setProjection(
            HFOV_DEG.toDouble(), width.toDouble() / height, CAMERA_NEAR.toDouble(), CAMERA_FAR.toDouble(),
            Camera.Fov.HORIZONTAL,
        )
    }

    fun pose(t: Float, yaw: Float) {
        cameraAt(t, cam)
        lookAt(cam[0], cam[1], cam[2], cam[3], cam[4], cam[5])
        setLocal(rig, M4.rotation(Math.toRadians(yaw.toDouble()), 0f, 1f, 0f, m))
        for (i in 1 until 3) setLocal(arches[i], M4.rotation(Math.toRadians(archYaw(t, i).toDouble()), 0f, 1f, 0f, m))
        setLocal(pearl, M4.translation(0f, pearlY(t), 0f, m))
    }
}
