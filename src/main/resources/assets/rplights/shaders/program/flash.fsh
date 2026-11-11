#version 150

// RP Lights - screen pass for its lamps. Based on the Survivor's Arsenal flashlight pass (flash.fsh) and the
// RP Fixes copy (FlCone): the same three lobes, gain, lift, knee and saturation, but every beam has its own cones and
// colour, and a lamp can shine all round (kerosene lamp: BeamColor.w = 1). Up to 4 beams per pass; more - another pass.
//
// Packing (one column per beam):
//   BeamOrigin[i] = (position relative to camera, range)
//   BeamAxis[i]   = (direction, intensity)
//   BeamCone[i]   = (cos core inner, cos core outer, cos spill inner, cos spill outer)
//   BeamWash[i]   = (cos wash inner, cos wash outer, spill weight, wash weight)
//   BeamColor[i]  = (rgb, 1 = all-round lamp)

uniform sampler2D DiffuseSampler;
uniform sampler2D DiffuseDepthSampler;

uniform mat4 ProjMat;
uniform vec2 OutSize;

uniform mat4 InvProjMat;
uniform mat4 InvViewMat;

uniform mat4 BeamOrigin;
uniform mat4 BeamAxis;
uniform mat4 BeamCone;
uniform mat4 BeamWash;
uniform mat4 BeamColor;
uniform float BeamCount;

// 1 when FL Render lights the scene for real: then this pass only underlines the spot.
uniform float FlCone;
uniform float ShaderPackMode;

in vec2 texCoord;
out vec4 fragColor;

const float BEAM_GAIN = 6.40;
const float BEAM_LIFT = 0.09;
const float BEAM_TINT = 1.00;
const float NDL_FLOOR = 0.42;
const float BEAM_KNEE = 0.90;
const float BEAM_SAT  = 1.20;

const float CONE_GAIN = 0.55;
const float CONE_LIFT = 0.0;
const float CONE_KNEE = 0.85;
const float CONE_SAT  = 1.0;

const float CORE_HOT   = 0.62;
const float EDGE_NOISE = 0.0035;

const int MAX_BEAMS = 4;

const vec3 LUMA = vec3(0.2126, 0.7152, 0.0722);

float hash21(vec2 p) {
    p = fract(p * vec2(123.34, 456.21));
    p += dot(p, p + 45.32);
    return fract(p.x * p.y);
}

float valueNoise(vec2 p) {
    vec2 i = floor(p);
    vec2 f = fract(p);
    f = f * f * (3.0 - 2.0 * f);
    float a = hash21(i);
    float b = hash21(i + vec2(1.0, 0.0));
    float c = hash21(i + vec2(0.0, 1.0));
    float d = hash21(i + vec2(1.0, 1.0));
    return mix(mix(a, b, f.x), mix(c, d, f.x), f.y);
}

vec3 viewPosFromDepth(vec2 uv, float rawDepth) {
    vec4 ndc = vec4(uv * 2.0 - 1.0, rawDepth * 2.0 - 1.0, 1.0);
    vec4 view = InvProjMat * ndc;
    float w = abs(view.w) < 1e-6 ? 1e-6 : view.w;
    return view.xyz / w;
}

vec3 worldPosAt(vec2 uv) {
    float d = texture(DiffuseDepthSampler, uv).r;
    return (InvViewMat * vec4(viewPosFromDepth(uv, d), 0.0)).xyz;
}

float beamContribution(vec3 worldPos, vec3 normal, float normalConfidence, int i) {
    vec4 origin = BeamOrigin[i];
    vec4 axis = BeamAxis[i];
    vec4 cone = BeamCone[i];
    vec4 wash = BeamWash[i];
    float point = BeamColor[i].w;
    vec3 lampPos = origin.xyz;
    float range = origin.w;
    vec3 lampDir = axis.xyz;

    vec3 toFrag = worldPos - lampPos;
    float dist = length(toFrag);
    vec3 L = toFrag / max(dist, 1e-5);
    float cosA = dot(L, lampDir);
    if (cosA < wash.y - 0.05) {
        return 0.0;
    }

    float shaderMode = clamp(ShaderPackMode, 0.0, 1.0);
    float shape;
    if (point > 0.5) {
        shape = 1.0;
    } else {
        vec3 upRef = abs(lampDir.y) > 0.94 ? vec3(1.0, 0.0, 0.0) : vec3(0.0, 1.0, 0.0);
        vec3 tx = normalize(cross(upRef, lampDir));
        vec3 ty = cross(lampDir, tx);
        vec2 disc = vec2(dot(L, tx), dot(L, ty));
        float edgeNoise = (valueNoise(disc * 7.0) - 0.5) * EDGE_NOISE;
        edgeNoise += (valueNoise(disc * 19.0 + 4.0) - 0.5) * EDGE_NOISE * 0.45;
        edgeNoise *= 1.0 - shaderMode;

        float core = smoothstep(cone.y, cone.x, cosA + edgeNoise);
        core = core * core * (3.0 - 2.0 * core);
        float spill = smoothstep(cone.w, cone.z, cosA);
        spill *= 0.45 + 0.55 * spill;
        float wsh = smoothstep(wash.y, wash.x, cosA + edgeNoise * 0.6);
        wsh *= 0.55 + 0.45 * wsh;
        float hot = smoothstep(cone.x, mix(cone.x, 1.0, 0.65), cosA);
        float center = smoothstep(mix(cone.x, 1.0, 0.18), mix(cone.x, 1.0, 0.58), cosA);
        center = center * center * (3.0 - 2.0 * center);
        shape = min(core + hot * CORE_HOT + center * 0.18 * shaderMode + spill * wash.z + wsh * wash.w, 1.75);
    }

    float safeRange = max(range, 0.001);
    float atten = 1.0 / (1.0 + dist * 0.15 + dist * dist * 0.014);
    atten *= 1.0 - smoothstep(safeRange * 0.62, safeRange, dist);
    atten *= 1.55;
    atten *= smoothstep(0.05, 0.50, dist);

    float ndl = max(dot(normal, -L), 0.0);
    ndl = mix(0.80, mix(NDL_FLOOR, 1.0, ndl), normalConfidence);

    return shape * atten * ndl * axis.w;
}

void main() {
    vec4 source = texture(DiffuseSampler, texCoord);
    vec3 color = source.rgb;

    float rawDepth = texture(DiffuseDepthSampler, texCoord).r;
    float surfaceMask = 1.0 - smoothstep(0.99985, 1.0, rawDepth);

    vec3 viewPos = viewPosFromDepth(texCoord, rawDepth);
    vec3 worldPos = (InvViewMat * vec4(viewPos, 0.0)).xyz;

    vec3 viewRay = normalize(worldPos + vec3(1e-5));
    vec3 normal = -viewRay;
    float normalConfidence = 0.0;

    if (ShaderPackMode < 0.5) {
        vec2 texel = 1.0 / max(OutSize, vec2(1.0));
        vec3 pL = worldPosAt(texCoord - vec2(texel.x, 0.0));
        vec3 pR = worldPosAt(texCoord + vec2(texel.x, 0.0));
        vec3 pD = worldPosAt(texCoord - vec2(0.0, texel.y));
        vec3 pU = worldPosAt(texCoord + vec2(0.0, texel.y));
        float centerDist = length(worldPos);
        float gapL = abs(length(pL) - centerDist);
        float gapR = abs(length(pR) - centerDist);
        float gapD = abs(length(pD) - centerDist);
        float gapU = abs(length(pU) - centerDist);
        vec3 dx = gapR < gapL ? (pR - worldPos) : (worldPos - pL);
        vec3 dy = gapU < gapD ? (pU - worldPos) : (worldPos - pD);
        normal = normalize(cross(dx, dy));
        if (dot(normal, -viewRay) < 0.0) {
            normal = -normal;
        }
        float disagree = (min(gapL, gapR) + min(gapD, gapU)) / max(centerDist, 1.0);
        normalConfidence = 1.0 - smoothstep(0.012, 0.085, disagree);
    }

    // Light per beam, each in its own colour; the strength drives the curve, the colour mix tints it.
    vec3 lightC = vec3(0.0);
    float light = 0.0;
    for (int i = 0; i < MAX_BEAMS; i++) {
        if (float(i) >= BeamCount) {
            break;
        }
        float a = beamContribution(worldPos, normal, normalConfidence, i);
        light += a;
        lightC += a * BeamColor[i].rgb;
    }
    vec3 lightColor = light > 1e-5 ? lightC / light : vec3(1.0);

    float shaderMode = clamp(ShaderPackMode, 0.0, 1.0);
    float luminance = dot(color, LUMA);
    float darkness = 1.0 - smoothstep(0.10, 0.92, luminance);

    light *= surfaceMask;
    light *= mix(0.84, 1.05, darkness);

    float brightProtection = 1.0 - smoothstep(0.52, 0.90, luminance);
    light *= mix(1.0, brightProtection, shaderMode);
    light = clamp(light, 0.0, mix(1.6, 0.98, shaderMode));

    if (shaderMode > 0.5 && light > 0.001) {
        vec2 denoiseTexel = 1.0 / max(OutSize, vec2(1.0));
        vec3 filtered = color;
        float totalWeight = 1.0;
        vec2 offsets[4] = vec2[4](
            vec2(denoiseTexel.x, 0.0),
            vec2(-denoiseTexel.x, 0.0),
            vec2(0.0, denoiseTexel.y),
            vec2(0.0, -denoiseTexel.y)
        );
        for (int sampleIndex = 0; sampleIndex < 4; sampleIndex++) {
            vec2 sampleUv = clamp(texCoord + offsets[sampleIndex], vec2(0.0), vec2(1.0));
            float sampleDepth = texture(DiffuseDepthSampler, sampleUv).r;
            float depthWeight = 1.0 - smoothstep(0.00025, 0.0030, abs(sampleDepth - rawDepth));
            filtered += texture(DiffuseSampler, sampleUv).rgb * depthWeight;
            totalWeight += depthWeight;
        }
        vec3 denoised = filtered / max(totalWeight, 1e-4);
        color = mix(color, denoised, clamp(light * 0.20, 0.0, 0.18));
        luminance = dot(color, LUMA);
    }

    float flCone = clamp(FlCone, 0.0, 1.0) * (1.0 - shaderMode);
    float beamGain = mix(mix(BEAM_GAIN, 3.75, shaderMode), CONE_GAIN, flCone);
    float beamLift = mix(mix(BEAM_LIFT, 0.035, shaderMode), CONE_LIFT, flCone);
    float beamKnee = mix(mix(BEAM_KNEE, 0.82, shaderMode), CONE_KNEE, flCone);
    float beamSat = mix(mix(BEAM_SAT, 1.06, shaderMode), CONE_SAT, flCone);

    float peak = max(color.r, max(color.g, color.b));
    float tintConfidence = smoothstep(0.008, 0.090, peak);
    vec3 tint = color / max(peak, 1e-3);
    vec3 surfaceTint = mix(vec3(1.0), tint, BEAM_TINT * tintConfidence);

    vec3 lit = color * (vec3(1.0) + light * beamGain * lightColor);
    vec3 headroom = max(vec3(0.0), vec3(1.0) - lit);
    lit += headroom * surfaceTint * lightColor * light * beamLift;

    float litLum = dot(lit, LUMA);
    float rolled = litLum / (1.0 + max(0.0, litLum - beamKnee));
    lit *= rolled / max(litLum, 1e-4);

    float outLum = dot(lit, LUMA);
    lit = mix(vec3(outLum), lit, mix(1.0, beamSat, min(light * 1.6, 1.0)));

    fragColor = vec4(clamp(lit, 0.0, 1.0), source.a);
}
