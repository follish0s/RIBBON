#version 150

// —— LOD 远景地形片元着色器 ——
// 三档质量（QualityMode uniform 控制）：
//   0 LOW    : 纯 MapColor 顶点色（最省）
//   1 MEDIUM : 纯色 + 方向光照（顶面亮/侧面暗，有立体感，贴合原版明暗）
//   2 HIGH   : 采样原版 terrain atlas 纹理（糊化平铺）x 顶点色 x 光照，
//              颜色 100% 与原版一致
// 雾效硬编码（不读 RenderSystem 的 Fog uniform，避免"地平线硬切"）。

in float vertexDistance;
in vec4 vertexColor;
in vec3 vertexNormal;
in vec2 texCoord;

uniform sampler2D Sampler0;

uniform int QualityMode;
// 是否光影渲染（1=光影，0=无光影）。光影下 deferred 已结束，LOD 画在最终
// sRGB framebuffer 上，不能重复叠方向光照/固定雾色，否则颜色双重处理→偏暗偏色。
uniform int ShaderMode;
// HIGH 档：顶面 sprite（草顶）与侧面 sprite（草侧/土）的 atlas 归一化 UV 范围。
uniform float TopU0;
uniform float TopV0;
uniform float TopU1;
uniform float TopV1;
uniform float SideU0;
uniform float SideV0;
uniform float SideU1;
uniform float SideV1;

out vec4 fragColor;

const float FOG_START = 512.0;
const float FOG_END = 1800.0;
const vec3 FOG_COLOR = vec3(0.78, 0.84, 0.92);

// 简单半球方向光照：顶面(+Y)最亮，侧面中亮，底面较暗。方向取斜上方，
// 让朝上的面更亮、竖直面次之、朝下面最暗，模拟原版天空光/漫反射的明暗层次。
float directionalLight(vec3 n) {
    vec3 lightDir = normalize(vec3(0.35, 1.0, 0.25));
    float ndl = max(0.0, dot(normalize(n + 1e-5), lightDir));
    // 基础环境光 0.82 + 漫反射贡献，使侧面保持较高亮度，顶面最亮约 1.2。
    // 之前 0.62 让侧面/远景偏暗发灰，与原版鲜亮地形脱节。
    return 0.82 + 0.40 * ndl;
}

void main() {
    float fog = clamp((vertexDistance - FOG_START) / (FOG_END - FOG_START), 0.0, 1.0);
    fog = fog * fog * (3.0 - 2.0 * fog);

    vec3 n = normalize(vertexNormal + 1e-5);

    vec3 albedo;
    if (QualityMode >= 2) {
        // HIGH：采样原版 atlas。texCoord 是世界坐标，fract 成 0~1 平铺。
        // 顶面用草顶 sprite，其余（侧/底）用草侧 sprite，颜色由 vertexColor 调制。
        vec2 tile = fract(texCoord / 16.0);
        if (n.y > 0.5) {
            vec2 uv = mix(vec2(TopU0, TopV0), vec2(TopU1, TopV1), tile);
            albedo = texture(Sampler0, uv).rgb * vertexColor.rgb;
        } else {
            vec2 uv = mix(vec2(SideU0, SideV0), vec2(SideU1, SideV1), tile);
            albedo = texture(Sampler0, uv).rgb * vertexColor.rgb;
        }
    } else {
        albedo = vertexColor.rgb;
    }

    // 光影下（ShaderMode==1）：deferred 已结束，光影已处理光照、大气与色调映射，
    // LOD 画在最终 sRGB framebuffer 上。此时若再叠方向光照/固定雾色，颜色会被
    // 双重处理 → 偏暗/偏色/灰黑。正确做法是<b>直接输出纯顶点色</b>（顶点色本身
    // 就是 sRGB MapColor，与最终 framebuffer 的颜色空间一致），不混雾、不调光照，
    // 把亮度/雾/色调全部交给光影的 final pass 已经做过的结果。
    if (ShaderMode == 1) {
        fragColor = vec4(albedo, 1.0);
        return;
    }

    float light = 1.0;
    if (QualityMode >= 1) {
        light = directionalLight(n);
    }

    vec3 color = albedo * light;
    vec3 fogged = mix(color, FOG_COLOR, fog);

    fragColor = vec4(fogged, 1.0);
}
