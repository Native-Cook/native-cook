package com.ngocdevv.nativecook.blocks.cards.aurawalletwidget;

import android.graphics.RuntimeShader;
import androidx.annotation.RequiresApi;

/** Java's array call avoids the defensive copy Kotlin inserts for a vararg spread. */
@RequiresApi(33)
final class AuraShaderUniforms {
    private AuraShaderUniforms() {}
    static void set(RuntimeShader shader, String name, float[] values) {
        shader.setFloatUniform(name, values);
    }
}
