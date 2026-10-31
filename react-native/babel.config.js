// babel-preset-expo is what Expo uses without a config; unplugin-typegpu compiles the TypeScript
// shaders marked 'use gpu' (TypeGPU) so they can be turned into WGSL at runtime.
module.exports = function (api) {
  api.cache(true);
  return {
    presets: ['babel-preset-expo'],
    plugins: ['unplugin-typegpu/babel'],
  };
};
