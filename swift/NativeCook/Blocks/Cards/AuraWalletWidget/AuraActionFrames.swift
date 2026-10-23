// Fixed-size vectors keep the pillow trade allocation-free.
struct AuraActionFrames {
  let xs: SIMD4<Double>
  let widths: SIMD4<Double>
  let kiss: Double
}
