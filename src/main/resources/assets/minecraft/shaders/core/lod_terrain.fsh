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

    // —— albedo 取色 ——
    // 关键修复：HIGH 档此前用「固定草方块纹理 × vertexColor(MapColor)」二次调制，
    // 导致水体(蓝)×草纹理→青色斑块、沙地(黄)×草纹理→黄绿怪色，且整体过饱和。
    // LOD 顶点色本身就是 MapColor（已含正确的地形固有色：草绿/沙黄/水蓝/雪白），
    // 因此所有档位统一直接用 vertexColor 作为 albedo，不再采样固定草纹理。
    // HIGH 档保留「方向光照」以增加立体感，颜色不再被错误纹理污染。
    vec3 albedo = vertexColor.rgb;

    // —— 去饱和（大气透视）——
    // MapColor 是满饱和度纯色，直接上屏会显得「荧光绿/刺眼」，与近景经过光影
    // 色调映射 + 大气散射后的偏灰自然色脱节。这里按距离做一次向灰度(Rec.709 luma)
    // 的轻微拉近，模拟远景大气透视的灰化，让 LOD 视觉上融入近景。
    float luma = dot(albedo, vec3(0.2126, 0.7152, 0.0722));
    // 饱和度混合：近处 0.75 保留较多色彩，远处随 fog 增强灰化，最终 0.45。
    float sat = mix(0.75, 0.45, fog);
    albedo = mix(vec3(luma), albedo, sat);

    // 光影下（ShaderMode==1）：deferred 已结束，光影已处理光照、大气与色调映射。
    // LOD 画在最终 sRGB framebuffer 上，不重复叠方向光照，但<b>仍需做雾效</b>把
    // 远景拉向天空色——否则满饱和 MapColor 直出会与光影色调严重脱节（荧光绿/刺眼）。
    // 雾按距离淡出到 FOG_COLOR，让 LOD 与地平线自然融合。
    if (ShaderMode == 1) {
        vec3 fogged = mix(albedo, FOG_COLOR, fog * 0.85);
        fragColor = vec4(fogged, 1.0);
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
