#version 150

in vec3 Position;

void main() {
    // 【探针】顶点直接是裁剪空间坐标（w=1），不做任何 ModelView/Proj 变换，
    // 绕开 mesh 数据 / 剔除 / 矩阵 / 雾 / 光照 一切变量，纯粹验证
    // "时机 + shader + 当前绑定缓冲 + RenderSystem 状态" 能否把像素画上屏。
    gl_Position = vec4(Position, 1.0);
}
