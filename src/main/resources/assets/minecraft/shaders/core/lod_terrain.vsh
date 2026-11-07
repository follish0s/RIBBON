#version 150

// —— 严格对齐 DefaultVertexFormat.BLOCK 的 5 个顶点属性 ——
// 这是 Iris 的 GBuffer/Shadow Pass 唯一能正确识别的标准地形顶点布局。
in vec3 Position;   // Position (XYZ)
in vec4 Color;      // Color (RGBA, MapColor 归一化)
in vec2 UV0;        // UV0 (世界坐标平铺)
in ivec2 UV2;       // UV2 (光照贴图坐标, 满亮 240)
in vec3 Normal;     // Normal (面朝向, signed byte 归一化)

uniform mat4 ModelViewMat;
uniform mat4 ProjMat;

out float vertexDistance;
out vec4 vertexColor;
out vec3 vertexNormal;
out vec2 texCoord;

void main() {
    gl_Position = ProjMat * ModelViewMat * vec4(Position, 1.0);
    vec4 viewPos = ModelViewMat * vec4(Position, 1.0);
    vertexDistance = length(viewPos.xyz);
    vertexColor = Color;
    vertexNormal = Normal;
    // UV0 是世界坐标平铺（值域为世界坐标，可能远超 0~1），片元着色器里会
    // fract 成 0~1 再映射到 atlas sprite 范围，做纹理平铺采样。
    texCoord = UV0;
}
