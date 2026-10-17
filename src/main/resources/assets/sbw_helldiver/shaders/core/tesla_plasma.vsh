#version 150
in vec3 Position;
in vec4 Color;
in vec2 UV0;
uniform mat4 ModelViewMat;
uniform mat4 ProjMat;
out vec4 vertexColor;
out vec2 effectUV;
out float viewDistance;
void main() {
    vec4 view=ModelViewMat*vec4(Position,1.0);
    gl_Position=ProjMat*view;
    viewDistance=length(view.xyz);
    vertexColor=Color;
    effectUV=UV0;
}
