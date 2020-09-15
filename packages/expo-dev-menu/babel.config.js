module.exports = function(api) {
  api.cache(true);

  const moduleResolverConfig = {
    alias: {
      'react-native-gesture-handler': require.resolve(
        './vendored/react-native-gesture-handler/src/index.js'
      ),
      'react-native-reanimated': require.resolve(
        './vendored/react-native-reanimated/src/Animated.js'
      ),
    },
  };

  return {
    presets: ['babel-preset-expo'],
    plugins: [['babel-plugin-module-resolver', moduleResolverConfig]],
  };
};
