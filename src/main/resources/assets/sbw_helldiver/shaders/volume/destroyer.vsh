#version 150
in vec3 Position;
in vec2 UV;
in float Shade;
uniform mat4 ViewTransform;
uniform mat4 Projection;
out vec2 texCoord;
out float brightness;
out vec3 viewPosition;
void main(){
    vec4 view=ViewTransform*vec4(Position,1.0);
    viewPosition=view.xyz;texCoord=UV;brightness=Shade;
    gl_Position=Projection*view;
}
