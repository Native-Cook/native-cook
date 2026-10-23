package com.ngocdevv.nativecook.blocks.progress.stitchweavepreview

/** Frozen material equations; static data and constant-index styles also compile as ES2 SkSL. */
internal const val STITCH_WEAVE_PREVIEW_AGSL = """
uniform shader uAtlas;
uniform float4 phase;
uniform float4 shape;
uniform float4 light;
uniform float4 selection;
uniform float matrix[18];
uniform float4 styles[48];
const float StitchWeavePreviewPi = 3.141592653589793;
const half3 cream = half3(0.8705882353,0.8627450980,0.8274509804);
const half3 moss = half3(0.5333333333,0.6039215686,0.5490196078);
const half3 slate = half3(0.4901960784,0.5490196078,0.5725490196);
const half3 recess = half3(0.4980392157,0.5215686275,0.5058823529);
const half3 ink = half3(0.2039215686,0.2392156863,0.2392156863);
const half3 highlight = half3(0.9568627451,0.9490196078,0.9137254902);
float swpSaturate(float x) { return clamp(x, 0.0, 1.0); }
half4 sampleData(float2 uv) { return uAtlas.eval(uv * float2(512.0,256.0)); }
float4 rowStyle(int row) {
    if (row < 24) {
        if (row < 12) {
            if (row < 6) {
                if (row < 3) {
                    if (row < 1) {
                        return styles[0];
                    } else {
                        if (row < 2) {
                            return styles[1];
                        } else {
                            return styles[2];
                        }
                    }
                } else {
                    if (row < 4) {
                        return styles[3];
                    } else {
                        if (row < 5) {
                            return styles[4];
                        } else {
                            return styles[5];
                        }
                    }
                }
            } else {
                if (row < 9) {
                    if (row < 7) {
                        return styles[6];
                    } else {
                        if (row < 8) {
                            return styles[7];
                        } else {
                            return styles[8];
                        }
                    }
                } else {
                    if (row < 10) {
                        return styles[9];
                    } else {
                        if (row < 11) {
                            return styles[10];
                        } else {
                            return styles[11];
                        }
                    }
                }
            }
        } else {
            if (row < 18) {
                if (row < 15) {
                    if (row < 13) {
                        return styles[12];
                    } else {
                        if (row < 14) {
                            return styles[13];
                        } else {
                            return styles[14];
                        }
                    }
                } else {
                    if (row < 16) {
                        return styles[15];
                    } else {
                        if (row < 17) {
                            return styles[16];
                        } else {
                            return styles[17];
                        }
                    }
                }
            } else {
                if (row < 21) {
                    if (row < 19) {
                        return styles[18];
                    } else {
                        if (row < 20) {
                            return styles[19];
                        } else {
                            return styles[20];
                        }
                    }
                } else {
                    if (row < 22) {
                        return styles[21];
                    } else {
                        if (row < 23) {
                            return styles[22];
                        } else {
                            return styles[23];
                        }
                    }
                }
            }
        }
    } else {
        if (row < 36) {
            if (row < 30) {
                if (row < 27) {
                    if (row < 25) {
                        return styles[24];
                    } else {
                        if (row < 26) {
                            return styles[25];
                        } else {
                            return styles[26];
                        }
                    }
                } else {
                    if (row < 28) {
                        return styles[27];
                    } else {
                        if (row < 29) {
                            return styles[28];
                        } else {
                            return styles[29];
                        }
                    }
                }
            } else {
                if (row < 33) {
                    if (row < 31) {
                        return styles[30];
                    } else {
                        if (row < 32) {
                            return styles[31];
                        } else {
                            return styles[32];
                        }
                    }
                } else {
                    if (row < 34) {
                        return styles[33];
                    } else {
                        if (row < 35) {
                            return styles[34];
                        } else {
                            return styles[35];
                        }
                    }
                }
            }
        } else {
            if (row < 42) {
                if (row < 39) {
                    if (row < 37) {
                        return styles[36];
                    } else {
                        if (row < 38) {
                            return styles[37];
                        } else {
                            return styles[38];
                        }
                    }
                } else {
                    if (row < 40) {
                        return styles[39];
                    } else {
                        if (row < 41) {
                            return styles[40];
                        } else {
                            return styles[41];
                        }
                    }
                }
            } else {
                if (row < 45) {
                    if (row < 43) {
                        return styles[42];
                    } else {
                        if (row < 44) {
                            return styles[43];
                        } else {
                            return styles[44];
                        }
                    }
                } else {
                    if (row < 46) {
                        return styles[45];
                    } else {
                        if (row < 47) {
                            return styles[46];
                        } else {
                            return styles[47];
                        }
                    }
                }
            }
        }
    }
}
float data24(float x) { float3 rgb = sampleData(float2((x + 0.5)/512.0,200.5/256.0)).rgb; return dot(rgb,float3(16711680.0,65280.0,255.0))/16777215.0; }
half3 toScreen(float3 c) {
  c=max(c,float3(0.0));
  float3 lin=mix(c/12.92,pow((c+0.055)/1.055,float3(2.4)),step(0.04045,c));
  return fromLinearSrgb(half3(lin));
}
float StitchWeavePreviewSmooth(float x) { x = swpSaturate(x); return x * x * (3.0 - 2.0 * x); }
float StitchWeavePreviewEase(float x) {
    if (x <= 0.0) return 0.0;
    if (x >= 1.0) return 1.0;
    float u = x;
    for (int i = 0; i < 6; ++i) {
        float a = 1.0 - u;
        float value = 1.95 * a * a * u + 1.05 * a * u * u + u * u * u;
        float derivative = 1.95 * a * a - 1.8 * a * u + 1.95 * u * u;
        u = clamp(u - (value - x) / max(derivative, 0.1), 0.0, 1.0);
    }
    return u * u * (3.0 - 2.0 * u);
}
float StitchWeavePreviewArc(float u, float amplitude) {
    float2 at = float2(swpSaturate(u) * 128.0 + 0.5, 128.5 + swpSaturate(amplitude / 17.0) * 64.0) / float2(512.0, 256.0);
    float2 packedRG = float2(sampleData(at).rg);
    float normalized = dot(packedRG, float2(65280.0, 255.0)) / 65535.0;
    // Extrapolate the plane outside the selvage; only the small correction lookup clamps.
    return (u - 0.5) * 322.4 + (normalized - 0.5) * 16.0;
}
float3 StitchWeavePreviewRotate(float3 p, float v) {
    float lag = swpSaturate(1.0 - v);
    return float3(dot(float3(matrix[0], matrix[1], matrix[2]) + lag * float3(matrix[9], matrix[10], matrix[11]), p),
                  dot(float3(matrix[3], matrix[4], matrix[5]) + lag * float3(matrix[12], matrix[13], matrix[14]), p),
                  dot(float3(matrix[6], matrix[7], matrix[8]) + lag * float3(matrix[15], matrix[16], matrix[17]), p));
}
float2 StitchWeavePreviewProject(float2 uv) {
    float amplitude = (8.0 + 9.0 * phase.x) * sin(StitchWeavePreviewPi * uv.y);
    float x = StitchWeavePreviewArc(uv.x, max(0.0, amplitude));
    float y = 245.6 + uv.y * 326.4 - 420.0 + shape.y;
    float leading = 1.0 - (3.0 + 45.0 * phase.x) / 48.0;
    float lift = shape.x * StitchWeavePreviewSmooth(1.0 - max(0.0, (uv.y - leading) * 326.4) / 256.0);
    float z = amplitude * sin(2.0 * StitchWeavePreviewPi * uv.x + 0.6) + lift;
    float3 rotated = StitchWeavePreviewRotate(float3(x, y, z), uv.y);
    return float2(196.5, 420.0) + rotated.xy * (950.0 / (950.0 - rotated.z));
}
float2 StitchWeavePreviewInverse(float2 pixel) {
    float2 screen = pixel - float2(196.5, 420.0);
    float a = 950.0 * matrix[0] + screen.x * matrix[6], b = 950.0 * matrix[1] + screen.x * matrix[7];
    float c = 950.0 * matrix[3] + screen.y * matrix[6], d = 950.0 * matrix[4] + screen.y * matrix[7];
    float determinant = a * d - b * c;
    float2 plane = 950.0 * float2(d * screen.x - b * screen.y, a * screen.y - c * screen.x) / determinant;
    float2 uv = float2(plane.x / 322.4 + 0.5, (plane.y + 420.0 - shape.y - 245.6) / 326.4);
    // A near-planar estimate plus two bounded Newton corrections; no ray marching or cell scan.
    for (int iteration = 0; iteration < 2; ++iteration) {
        float2 p = StitchWeavePreviewProject(uv);
        float2 du = (StitchWeavePreviewProject(uv + float2(0.0006, 0.0)) - p) / 0.0006;
        float2 dv = (StitchWeavePreviewProject(uv + float2(0.0, 0.0006)) - p) / 0.0006;
        float2 error = p - pixel;
        float det = du.x * dv.y - dv.x * du.y;
        if (abs(det) > 1.0) uv -= clamp(float2(dv.y * error.x - dv.x * error.y, du.x * error.y - du.y * error.x) / det, float2(-0.15), float2(0.15));
    }
    return uv;
}
half3 StitchWeavePreviewTile(float2 local, float frame) {
    frame = clamp(frame, 0.0, 15.0);
    float a = floor(frame), b = min(15.0, a + 1.0);
    float2 point = (local + float2(2.2, 2.4)) * 3.0;
    float2 first = (float2(mod(a, 8.0) * 32.0, floor(a / 8.0) * 40.0) + point) / float2(512.0, 256.0);
    float2 second = (float2(mod(b, 8.0) * 32.0, floor(b / 8.0) * 40.0) + point) / float2(512.0, 256.0);
    return mix(sampleData(first).rgb, sampleData(second).rgb, half(fract(frame)));
}
half3 StitchWeavePreviewFibers(float2 local, float frame) {
    frame = clamp(frame, 0.0, 15.0);
    float a = floor(frame), b = min(15.0, a + 1.0);
    float2 point = (local + float2(2.2, 2.4)) * 3.0 + float2(256.0, 0.0);
    float2 first = (float2(mod(a, 8.0) * 32.0, floor(a / 8.0) * 40.0) + point) / float2(512.0, 256.0);
    float2 second = (float2(mod(b, 8.0) * 32.0, floor(b / 8.0) * 40.0) + point) / float2(512.0, 256.0);
    return mix(sampleData(first).rgb, sampleData(second).rgb, half(fract(frame)));
}
float StitchWeavePreviewNoise(float2 p) {
    p = fract(p * float2(123.34, 456.21)); p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}
half4 StitchWeavePreviewCell(float2 materialPoint, float2 uv, int column, int row, float3 surface, float3 localLight) {
    if (column < 0 || column >= 52 || row < 0 || row >= 48) return half4(0.0);
    float2 local = materialPoint - float2(35.3 + float(column) * 6.2, 572.0 - float(row + 1) * 6.8);
    local.y = (local.y - 3.4) / (1.0 + 0.03 * shape.z) + 3.4;
    if (local.x < -1.8 || local.x > 8.05 || local.y < -2.0 || local.y > 10.3) return half4(0.0);
    float formation = 1.0;
    float delay = data24(64.0 + float(column)) * 0.0155 - 0.004;
    if (row >= 3 && phase.x < 0.999999) {
        if (phase.x <= 0.0 || float(row) > 4.0 + phase.x * 45.0) return half4(0.0);
        float rows = 3.0 + 45.0 * StitchWeavePreviewEase((phase.y - delay) / 1.9);
        formation = swpSaturate((rows - float(row + 1) + 0.45) / 0.45);
    }
    if (formation <= 0.0) return half4(0.0);
    float elapsed = (phase.y - (data24(float(row)) * 1.9) - delay) / max(0.05, phase.w) + phase.z;
    if (row >= 3 && formation >= 0.999 && elapsed > 0.0 && elapsed < 0.16) {
        float settle = -1.8 * sin(StitchWeavePreviewPi * elapsed / 0.16) * exp(-elapsed / 0.06);
        local.y -= settle * swpSaturate((6.3 - local.y) / 5.8);
    }
    half3 yarnData = StitchWeavePreviewTile(local, formation * 15.0);
    half coverage = yarnData.r;
    half underside = StitchWeavePreviewTile(local - float2(0.45, 0.6), formation * 15.0).r * 0.22;
    if (coverage < 0.001 && underside < 0.001) return half4(0.0);

    float4 style = rowStyle(row);
    float2 patterns = float2(mod(style.a,4096.0),floor(style.a/4096.0))/4095.0;
    float2 motifs = float2(sampleData(float2(float(column) + 0.5, 208.5 + float(row)) / float2(512.0, 256.0)).rg);
    half marked = half(dot(patterns, motifs));
    half3 base = mix(half3(style.rgb), ink, marked);
    float2 nxy = float2(yarnData.gb) * 2.0 - 1.0;
    float nz = sqrt(max(0.02, 1.0 - dot(nxy, nxy)));
    float3 normal = normalize(float3(nxy, nz) + 0.3 * surface);
    float diffuse = swpSaturate(dot(normal, localLight));
    float broadLight = swpSaturate(dot(surface, localLight));
    half shade = half((0.86 + 0.14 * diffuse) * (0.66 + 0.34 * broadLight * broadLight));
    half3 lit = base * shade;
    // Broad light wraps the cotton bundle; two or three fine fibers stay attached to its curve.
    lit = mix(lit, base, 0.22);
    half3 fibers = StitchWeavePreviewFibers(local, formation * 15.0);
    lit *= 0.965 + 0.050 * fibers.r + 0.020 * fibers.b;
    half ridge = half(pow(nz, 7.0) * 0.11);
    lit = mix(lit, mix(highlight, half3(0.19, 0.24, 0.23), marked), ridge);
    float band = exp(-pow((materialPoint.x - shape.w) / 27.0, 2.0));
    lit += base * half(light.x * band) * (1.0 - marked);
    lit *= half(0.975 + 0.05 * StitchWeavePreviewNoise(floor(materialPoint * 6.0)));
    half alpha = coverage + underside * (1.0 - coverage);
    half3 rgb = lit * coverage + recess * (0.50 * underside * (1.0 - coverage));
    return half4(rgb, alpha);
}

half4 main(float2 pixel) {
    if (pixel.x < 22.0 || pixel.x > 371.0 || pixel.y < 184.0 || pixel.y > 595.0) return half4(0.0);
    float2 uv = StitchWeavePreviewInverse(pixel);
    if (uv.x < -0.025 || uv.x > 1.025 || uv.y < -0.03 || uv.y > 1.04) return half4(0.0);
    if (distance(StitchWeavePreviewProject(uv), pixel) > 0.75) return half4(0.0);
    float2 point = float2(35.3 + uv.x * 322.4, 245.6 + uv.y * 326.4);
    int column = int(floor(uv.x * 52.0)), row = int(floor((572.0 - point.y) / 6.8));
    float amplitude = (8.0 + 9.0 * phase.x);
    float slopeX = amplitude * (2.0 * StitchWeavePreviewPi / 322.4) * cos(2.0 * StitchWeavePreviewPi * uv.x + 0.6) * sin(StitchWeavePreviewPi * uv.y);
    float slopeY = amplitude * (StitchWeavePreviewPi / 326.4) * sin(2.0 * StitchWeavePreviewPi * uv.x + 0.6) * cos(StitchWeavePreviewPi * uv.y);
    float3 surface = normalize(float3(-slopeX, -slopeY, 1.0));
    float3 worldLight = normalize(float3(-0.42, -0.57, 1.0));
    float3 localLight = normalize(float3(dot(StitchWeavePreviewRotate(float3(1.0, 0.0, 0.0), uv.y), worldLight),
                                        dot(StitchWeavePreviewRotate(float3(0.0, 1.0, 0.0), uv.y), worldLight),
                                        dot(StitchWeavePreviewRotate(float3(0.0, 0.0, 1.0), uv.y), worldLight)));
    half4 result = half4(0.0);
    // Only overlapping local yarn cells are sampled; all other 2,487 stitches cost nothing here.
    for (int dy = -1; dy <= 1; ++dy) for (int dx = -1; dx <= 1; ++dx) {
        half4 fiber = StitchWeavePreviewCell(point, uv, column + dx, row + dy, surface, localLight);
        result = fiber + result * (1.0 - fiber.a);
    }
    if (result.a < 0.00001) return half4(0.0);
    return half4(toScreen(float3(result.rgb / result.a)) * result.a, result.a);
}

"""
