#version 330 core

in vec2 v_texCoord;
out vec4 fragColor;

uniform sampler2D s_input;
uniform vec2 u_inputSize;
uniform float u_sharpness;

void main() {
    vec2 px = 1.0 / u_inputSize;

    // 采样中心及上下左右
    vec4 c = texture(s_input, v_texCoord);
    vec4 n = texture(s_input, v_texCoord + vec2(0, -px.y));
    vec4 s = texture(s_input, v_texCoord + vec2(0,  px.y));
    vec4 w = texture(s_input, v_texCoord + vec2(-px.x, 0));
    vec4 e = texture(s_input, v_texCoord + vec2( px.x, 0));

    // 🚨 核心：将色彩转换为亮度（Luma），这样就不会出现彩色噪点和断层
    vec3 lumaWeights = vec3(0.299, 0.587, 0.114);
    float lc = dot(c.rgb, lumaWeights);
    float ln = dot(n.rgb, lumaWeights);
    float ls = dot(s.rgb, lumaWeights);
    float lw = dot(w.rgb, lumaWeights);
    float le = dot(e.rgb, lumaWeights);

    // 计算纯亮度的拉普拉斯算子（边缘检测）
    float laplacian = 4.0 * lc - (ln + ls + lw + le);

    // 🚨 极大限制锐化强度，防止云层出现等高线。
    // 滑块拉到 100%，实际也只增加 20% 的极轻微边缘锐化
    float amount = u_sharpness * 0.2;

    // 计算边缘带来的亮度差值
    float delta = laplacian * amount;

    // 将亮度差等比例叠加回 RGB 原色上（不破坏色相）
    vec3 finalColor = c.rgb + vec3(delta);

    // 严格限制颜色边界，防止任何 HDR 溢出
    fragColor = vec4(clamp(finalColor, 0.0, 1.0), c.a);
}