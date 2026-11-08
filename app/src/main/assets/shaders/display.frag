precision highp float;
varying vec2 v;
uniform sampler2D image;
uniform sampler2D previous;
uniform float frameMix;
uniform int gba;
uniform int crisp;
uniform vec2 texSize;
uniform vec2 outSize;
uniform vec2 coreSize;
uniform float brightness;
uniform float contrast;
uniform float saturation;
uniform float temperature;
uniform float sharpness;

vec3 sampleNow(vec2 uv) {
    if (crisp != 1 || outSize.x < texSize.x || texSize.x < 1.0 || outSize.x < 1.0) return texture2D(image, uv).rgb;
    vec2 coord = uv * texSize;
    vec2 scale = outSize / texSize;
    vec2 base = floor(coord);
    vec2 frac = coord - base;
    vec2 region = 0.5 - 0.5 / scale;
    vec2 dist = frac - 0.5;
    vec2 snapped = (dist - clamp(dist, -region, region)) * scale + 0.5;
    return texture2D(image, (base + snapped) / texSize).rgb;
}

vec3 sampleOld(vec2 uv) {
    if (crisp != 1 || outSize.x < texSize.x || texSize.x < 1.0 || outSize.x < 1.0) return texture2D(previous, uv).rgb;
    vec2 coord = uv * texSize;
    vec2 scale = outSize / texSize;
    vec2 base = floor(coord);
    vec2 frac = coord - base;
    vec2 region = 0.5 - 0.5 / scale;
    vec2 dist = frac - 0.5;
    vec2 snapped = (dist - clamp(dist, -region, region)) * scale + 0.5;
    return texture2D(previous, (base + snapped) / texSize).rgb;
}

void main() {
    vec3 now = sampleNow(v);
    vec3 c = frameMix < 0.999 ? mix(sampleOld(v), now, frameMix) : now;
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
    if (gba == 1) {
        vec3 s = pow(max(c, vec3(0.0)), vec3(2.2));
        s = mat3(0.84, 0.09, 0.15, 0.18, 0.67, 0.10, 0.00, 0.26, 0.73) * s;
        c = pow(clamp(s, 0.0, 1.0), vec3(1.0 / 2.2));
    }
    c.r += temperature * 0.10;
    c.b -= temperature * 0.10;
    c = (c - 0.5) * contrast + 0.5;
    float luma = dot(c, vec3(0.2126, 0.7152, 0.0722));
    c = mix(vec3(luma), c, saturation);
    c *= brightness;
    gl_FragColor = vec4(clamp(c, 0.0, 1.0), 1.0);
}
