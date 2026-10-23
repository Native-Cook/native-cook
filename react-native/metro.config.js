const { getDefaultConfig } = require("expo/metro-config");
const path = require("node:path");

const config = getDefaultConfig(__dirname);

// glowknob-console plays a .wav.
if (!config.resolver.assetExts.includes("wav")) {
  config.resolver.assetExts.push("wav");
}

// choose-your-ball loads its model (.glb) and its baked environment (.ktx) with Filament.
for (const extension of ["glb", "ktx", "filamat"]) {
  if (!config.resolver.assetExts.includes(extension)) {
    config.resolver.assetExts.push(extension);
  }
}

// glowknob-console lifts its console with three.js on WebGPU. Three r186's
// CommonJS shim calls Node-only process.emitWarning, so `three` resolves to its
// ESM build, and `three/webgpu` and `three/tsl` to the builds next to it, which
// share three.core.js with it: every import gets the same core classes.
const threeESM = path.join(path.dirname(require.resolve("three")), "three.module.js");
const threeBuild = path.dirname(threeESM);
config.resolver.resolveRequest = (context, moduleName, platform) => {
  if (moduleName === "three") return { type: "sourceFile", filePath: threeESM };
  if (moduleName === "three/webgpu") return { type: "sourceFile", filePath: path.join(threeBuild, "three.webgpu.js") };
  if (moduleName === "three/tsl") return { type: "sourceFile", filePath: path.join(threeBuild, "three.tsl.js") };
  return context.resolveRequest(context, moduleName, platform);
};

module.exports = config;
