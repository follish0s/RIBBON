#version 330 core

/**
 * FSR 全屏三角形顶点着色器
 * 
 * 使用 gl_VertexID 生成覆盖 [-1,1] NDC 的三角形，
 * 不需要 VBO 数据，只需绘制 3 个顶点（GL_TRIANGLES）。
 *
 * 布局：
 *   vertex 0 → (-1, -1)  左下
 *   vertex 1 → ( 3, -1)  右外
 *   vertex 2 → (-1,  3)  上外
 * 裁剪后恰好覆盖整个屏幕。
 */

out vec2 v_texCoord;

void main() {
    // 生成覆盖全屏的三角形顶点坐标
    float x = -1.0 + float((gl_VertexID & 1) << 2); // -1, 3, -1
    float y = -1.0 + float((gl_VertexID & 2) << 1); // -1, -1, 3
    gl_Position = vec4(x, y, 0.0, 1.0);

    // 纹理坐标：(0,0) 左下 → (1,1) 右上
    v_texCoord = gl_Position.xy * 0.5 + 0.5;
}
