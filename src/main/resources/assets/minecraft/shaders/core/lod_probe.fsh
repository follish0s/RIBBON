#version 150

out vec4 fragColor;

void main() {
    // 【探针】写死纯红，绕开 Color / ColorModulator / 雾 一切变量。
    fragColor = vec4(1.0, 0.0, 0.0, 1.0);
}
