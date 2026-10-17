#version 150
uniform mat4 ViewProjection;
in vec3 Position;
in vec3 Coord;
in vec4 Color;
out vec3 coord;
out vec4 color;
void main(){coord=Coord;color=Color;gl_Position=ViewProjection*vec4(Position,1.0);}
