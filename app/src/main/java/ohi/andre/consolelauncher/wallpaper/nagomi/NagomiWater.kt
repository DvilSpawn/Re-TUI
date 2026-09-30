package ohi.andre.consolelauncher.wallpaper.nagomi

/** Water shader adapted from Nagomi src/water-surface.ts at 01e93a4.
 * Copyright 2026 Mayank Kadam. Original cellular currents and ripple refraction.
 * Changes: eight ripples, spawn/mouth profiles, Android bitmap Y orientation,
 * no separate depth-disturbance texture. See docs/nagomi.md for provenance.
 */
internal val NAGOMI_WATER_SHADER = """

  precision highp float;

  #define MAX_RIPPLES 8
  #define MAX_RIPPLE_TYPES 2

  uniform sampler2D uUnderwater;
  uniform sampler2D uDisturbance;
  uniform vec2 uResolution;
  uniform float uTime;
  uniform int uRippleCount;
  uniform vec4 uRipples[MAX_RIPPLES];
  uniform vec4 uRipplePhysics[MAX_RIPPLE_TYPES];
  uniform vec4 uRippleCurves[MAX_RIPPLE_TYPES];
  uniform vec3 uColorTint;
  uniform float uClarity;
  uniform float uShowCurrentEffect;
  uniform float uLargeCellSize;
  uniform float uLargeCurrentOpacity;
  uniform float uSecondaryLargeCellSize;
  uniform float uSecondaryLargeCurrentOpacity;
  uniform float uDetailCellSize;
  uniform float uDetailCurrentOpacity;
  uniform vec3 uLargeCurrentColor;
  uniform vec3 uLargeCurrentCoreColor;
  uniform vec3 uSecondaryLargeCurrentColor;
  uniform vec3 uSecondaryLargeCurrentCoreColor;
  uniform vec3 uDetailCurrentColor;
  uniform vec3 uDetailCurrentCoreColor;
  uniform float uCurrentAmplitude;
  uniform vec2 uWaveDirectionA;
  uniform vec2 uWaveDirectionB;
  uniform vec2 uWaveDirectionC;
  uniform vec3 uWaveFrequency;
  uniform vec3 uWaveSpeed;
  uniform vec3 uWaveStrength;
  uniform float uLargeCurrentTime;
  uniform float uSecondaryLargeCurrentTime;
  uniform float uDetailCurrentTime;
  varying vec2 vUv;

  vec4 ripplePhysics(float typeIndex) {
    vec4 profile = uRipplePhysics[0];
    if (typeIndex > 0.5) profile = uRipplePhysics[1];
    return profile;
  }

  vec4 rippleCurves(float typeIndex) {
    vec4 profile = uRippleCurves[0];
    if (typeIndex > 0.5) profile = uRippleCurves[1];
    return profile;
  }

  vec2 hash22(vec2 point) {
    vec2 value = vec2(
      dot(point, vec2(127.1, 311.7)),
      dot(point, vec2(269.5, 183.3))
    );
    return fract(sin(value) * 43758.5453);
  }

  float valueNoise(vec2 point) {
    vec2 cell = floor(point);
    vec2 local = fract(point);
    local = local * local * (3.0 - 2.0 * local);

    float bottomLeft = hash22(cell).x;
    float bottomRight = hash22(cell + vec2(1.0, 0.0)).x;
    float topLeft = hash22(cell + vec2(0.0, 1.0)).x;
    float topRight = hash22(cell + vec2(1.0, 1.0)).x;
    return mix(
      mix(bottomLeft, bottomRight, local.x),
      mix(topLeft, topRight, local.x),
      local.y
    );
  }

  vec2 warpWater(vec2 pixel) {
    float warpX = valueNoise(pixel * 0.010);
    float warpY = valueNoise(pixel * 0.012 + vec2(19.4, 7.8));
    float smallWarpX = valueNoise(
      pixel * 0.022 + vec2(31.8, -12.1)
    );
    float smallWarpY = valueNoise(
      pixel * 0.019 + vec2(-8.2, 26.6)
    );

    vec2 broadBend = vec2(
      sin(pixel.y * 0.025 + warpY * 5.2),
      cos(pixel.x * 0.022 + warpX * 5.6)
    );
    vec2 smallBend = vec2(
      sin((pixel.x + pixel.y) * 0.034 + smallWarpY * 4.8),
      cos((pixel.x - pixel.y) * 0.030 + smallWarpX * 5.1)
    );
    return pixel
      + (vec2(warpX, warpY) - 0.5) * 48.0
      + (vec2(smallWarpX, smallWarpY) - 0.5) * 18.0
      + broadBend * 11.0
      + smallBend * 4.5;
  }

  float cellularBorderDistance(vec2 point) {
    vec2 cell = floor(point);
    vec2 local = fract(point);
    float nearest = 10.0;
    float secondNearest = 10.0;

    for (int y = -1; y <= 1; y++) {
      for (int x = -1; x <= 1; x++) {
        vec2 neighbour = vec2(float(x), float(y));
        vec2 seed = hash22(cell + neighbour);
        vec2 animatedPoint = 0.5 + 0.32 * sin(6.2831853 * seed);
        vec2 pointDelta = neighbour + animatedPoint - local;
        float influence = mix(
          0.68,
          1.38,
          hash22(cell + neighbour + vec2(41.7, 13.2)).x
        );
        float distanceToPoint = length(pointDelta) / influence;
        if (distanceToPoint < nearest) {
          secondNearest = nearest;
          nearest = distanceToPoint;
        } else if (distanceToPoint < secondNearest) {
          secondNearest = distanceToPoint;
        }
      }
    }

    return secondNearest - nearest;
  }

  vec2 directionalWavePixel(vec2 pixel, vec2 resolution, float time) {
    float aspect = resolution.x / resolution.y;
    vec2 centered = (pixel / resolution - 0.5) * vec2(aspect, 1.0);

    float phaseA =
      dot(centered, uWaveDirectionA)
      * uWaveFrequency.x
      - time * uWaveSpeed.x;
    float phaseB =
      dot(centered, uWaveDirectionB)
      * uWaveFrequency.y
      - time * uWaveSpeed.y;
    float phaseC =
      dot(centered, uWaveDirectionC)
      * uWaveFrequency.z
      - time * uWaveSpeed.z;

    vec2 slope =
      uWaveDirectionA
      * sin(phaseA)
      * uWaveStrength.x
      + uWaveDirectionB
      * sin(phaseB)
      * uWaveStrength.y
      + uWaveDirectionC
      * sin(phaseC)
      * uWaveStrength.z;
    vec2 uvOffset =
      slope
      * vec2(1.0 / aspect, 1.0)
      * uCurrentAmplitude;
    return pixel + uvOffset * resolution;
  }

  void main() {
    vec2 pixel = vec2(vUv.x * uResolution.x, (1.0 - vUv.y) * uResolution.y);
    vec2 displacement = vec2(
      sin(pixel.y * 0.051 + uTime * 0.31) + sin(pixel.y * 0.017 - uTime * 0.19),
      cos(pixel.x * 0.043 - uTime * 0.23) + sin(pixel.x * 0.014 + uTime * 0.16)
    ) * 0.13 / uResolution;

    for (int index = 0; index < MAX_RIPPLES; index++) {
      if (index >= uRippleCount) break;

      vec4 ripple = uRipples[index];
      float typeIndex = floor(ripple.w * 0.5);
      float strength = mod(ripple.w, 2.0);
      vec4 physics = ripplePhysics(typeIndex);
      vec4 curves = rippleCurves(typeIndex);
      vec2 delta = pixel - ripple.xy;
      float distanceToCenter = length(delta);
      vec2 radial = delta / max(distanceToCenter, 0.001);
      float radius =
        physics.y
        + ripple.z * physics.z;
      float signedDistance = distanceToCenter - radius;
      float fade = 1.0 - smoothstep(
        physics.x * curves.y,
        physics.x,
        ripple.z
      );
      float life = clamp(ripple.z / max(physics.x, 0.001), 0.0, 1.0);
      float distortionBand =
        exp(-abs(signedDistance) * curves.x)
        * fade
        * strength
        * (1.0 - life * curves.z);
      float direction = signedDistance < 0.0 ? -1.0 : 1.0;

      displacement +=
        vec2(radial.x, -radial.y)
        * direction
        * distortionBand
        * physics.w
        / uResolution;
    }

    // Autonomous wallpaper omits the separate per-fish disturbance pass.
    vec2 localDisturbance = vec2(0.0);
    displacement +=
      vec2(localDisturbance.x, -localDisturbance.y) / uResolution;

    vec2 sampleUv = clamp(vUv + displacement, vec2(0.002), vec2(0.998));
    vec3 color = texture2D(uUnderwater, vec2(sampleUv.x, 1.0 - sampleUv.y)).rgb;

    vec2 distortedPixel = vec2(
      sampleUv.x * uResolution.x,
      (1.0 - sampleUv.y) * uResolution.y
    );

    color *= uColorTint;

    if (uShowCurrentEffect > 0.5) {
      vec2 largeWavePixel = directionalWavePixel(
        distortedPixel,
        uResolution,
        uLargeCurrentTime
      );
      vec2 warpedPixel = warpWater(largeWavePixel);
      vec2 secondaryLargeWavePixel = directionalWavePixel(
        distortedPixel,
        uResolution,
        uSecondaryLargeCurrentTime
      );
      vec2 detailWavePixel = directionalWavePixel(
        distortedPixel,
        uResolution,
        uDetailCurrentTime
      );
      vec2 secondaryLargePixel =
        warpedPixel + secondaryLargeWavePixel - largeWavePixel;
      vec2 detailPixel = warpedPixel + detailWavePixel - largeWavePixel;

      float largeBorder = cellularBorderDistance(
        warpedPixel / max(uLargeCellSize, 0.001)
      );
      float largeWidthNoise = valueNoise(warpedPixel * 0.018 + vec2(3.7, 11.2));
      float largeVein = 1.0 - smoothstep(
        0.032 + largeWidthNoise * 0.010,
        0.125 + largeWidthNoise * 0.022,
        largeBorder
      );
      float largeCore = 1.0 - smoothstep(0.010, 0.052, largeBorder);

      float secondaryLargeBorder = cellularBorderDistance(
        secondaryLargePixel / max(uSecondaryLargeCellSize, 0.001)
        + vec2(5.2, 8.4)
      );
      float secondaryLargeWidthNoise = valueNoise(
        secondaryLargePixel * 0.015 + vec2(17.6, -6.8)
      );
      float secondaryLargeVein = 1.0 - smoothstep(
        0.032 + secondaryLargeWidthNoise * 0.010,
        0.125 + secondaryLargeWidthNoise * 0.022,
        secondaryLargeBorder
      );
      float secondaryLargeCore =
        1.0 - smoothstep(0.010, 0.052, secondaryLargeBorder);

      float detailBorder = cellularBorderDistance(
        detailPixel / max(uDetailCellSize, 0.001) + vec2(9.6, 4.3)
      );
      float detailRegion = smoothstep(
        0.48,
        0.75,
        valueNoise(detailPixel * 0.008 + vec2(-5.1, 17.8))
      );
      float detailVein =
        (1.0 - smoothstep(0.030, 0.105, detailBorder)) * detailRegion;
      float detailCore =
        (1.0 - smoothstep(0.008, 0.043, detailBorder)) * detailRegion;

      color +=
        (largeVein * uLargeCurrentColor + largeCore * uLargeCurrentCoreColor)
        * uLargeCurrentOpacity * (1.0 - uClarity);
      color +=
        (
          secondaryLargeVein * uSecondaryLargeCurrentColor
          + secondaryLargeCore * uSecondaryLargeCurrentCoreColor
        ) * uSecondaryLargeCurrentOpacity * (1.0 - uClarity);
      color +=
        (detailVein * uDetailCurrentColor + detailCore * uDetailCurrentCoreColor)
        * uDetailCurrentOpacity * (1.0 - uClarity);
    }

    gl_FragColor = vec4(color, 1.0);
  }

""".trimIndent()
