precision highp float;
varying vec2 v;
uniform sampler2D image;
uniform int gba;
uniform int crisp;
uniform vec2 texSize;
uniform vec2 outSize;
uniform vec2 coreSize;
uniform float brightness;
uniform float contrast;
uniform float saturation;
uniform float temperature;
uniform float tint;
uniform float gamma;
uniform float sharpness;

vec3 nearestOf(sampler2D src, vec2 uv) {
    if (texSize.x < 1.0 || texSize.y < 1.0) return texture2D(src, uv).rgb;
    return texture2D(src, (floor(uv * texSize) + 0.5) / texSize).rgb;
}

vec3 sampleNow(vec2 uv) { return nearestOf(image, uv); }

void main() {
    vec3 c = sampleNow(v);
    if (sharpness > 0.001 && coreSize.x > 1.0) {
        vec2 t = 1.0 / coreSize;
        vec3 blur = (
            texture2D(image, v + vec2(t.x, 0.0)).rgb +
            texture2D(image, v - vec2(t.x, 0.0)).rgb +
            texture2D(image, v + vec2(0.0, t.y)).rgb +
            texture2D(image, v - vec2(0.0, t.y)).rgb
        ) * 0.25;
        c = clamp(c + (c - blur) * sharpness * 2.0, 0.0, 1.0);
    }
    // Same grade as NX Redux pixel-perfect.glsl. Temperature trades red for
    // blue, tint trades green for magenta, then brightness, contrast, and
    // saturation fold into one step. Gamma above 1 deepens the midtones.
    c *= 1.0 + temperature * vec3(1.0, 0.0, -1.0) + tint * vec3(-0.5, 1.0, -0.5);
    float gain = brightness * contrast;
    float lift = 0.5 - 0.5 * contrast;
    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
    c = c * (gain * saturation) + luma * (gain * (1.0 - saturation)) + lift;
    if (abs(gamma - 1.0) > 0.001) c = pow(max(c, 1e-8), vec3(gamma));
    gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
