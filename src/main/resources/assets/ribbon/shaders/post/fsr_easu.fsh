#version 330 core

in vec2 v_texCoord;
out vec4 fragColor;

uniform sampler2D s_input;
uniform vec2 u_inputSize;
uniform vec2 u_outputSize;

void main() {
    // 因为我们的输入和输出分辨率目前是一致的，直接做硬件插值即可
    // 如果以后要做真正的超分降分辨率，这里才需要写复杂的 EASU 算法
    fragColor = texture(s_input, v_texCoord);
}