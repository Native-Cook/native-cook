// Reuse uniform arrays; Java avoids Kotlin's defensive allocation for vararg spreads.
package com.ngocdevv.nativecook.blocks.progress.horizonupload;

import android.graphics.RuntimeShader;
import androidx.annotation.RequiresApi;

@RequiresApi(33)
final class HorizonShaderUniforms {
    private final float[] scalar = new float[1];
    private final float[] pair = new float[2];
    private final float[] triple = new float[3];
    private final float[] quad = new float[4];
    void set(RuntimeShader shader, String name, float x) {
        scalar[0] = x; shader.setFloatUniform(name, scalar);
    }
    void set(RuntimeShader shader, String name, float x, float y) {
        pair[0] = x; pair[1] = y; shader.setFloatUniform(name, pair);
    }
    void set(RuntimeShader shader, String name, float x, float y, float z) {
        triple[0] = x; triple[1] = y; triple[2] = z; shader.setFloatUniform(name, triple);
    }
    void set(RuntimeShader shader, String name, float x, float y, float z, float w) {
        quad[0] = x; quad[1] = y; quad[2] = z; quad[3] = w; shader.setFloatUniform(name, quad);
    }
}
