#version 150
in vec3 Center;
in vec3 Axis;
in vec4 Shape;
in vec2 Style;
uniform mat4 ViewProjection;
flat out vec3 flameCenter,flameAxis,flameRight,flameUp;
flat out vec4 flameShape;
flat out vec2 flameStyle;
const vec3 cube[8]=vec3[8](vec3(-1,-1,-1),vec3(1,-1,-1),vec3(1,1,-1),vec3(-1,1,-1),vec3(-1,-1,1),vec3(1,-1,1),vec3(1,1,1),vec3(-1,1,1));
const int indices[36]=int[36](0,2,1,0,3,2,4,5,6,4,6,7,0,4,7,0,7,3,1,2,6,1,6,5,0,1,5,0,5,4,3,7,6,3,6,2);
void main(){
    flameCenter=Center;flameAxis=normalize(Axis);flameShape=Shape;flameStyle=Style;
    vec3 reference=abs(flameAxis.y)<0.92?vec3(0,1,0):vec3(1,0,0);
    flameRight=normalize(cross(reference,flameAxis));flameUp=cross(flameAxis,flameRight);
    vec3 p=cube[indices[gl_VertexID]];
    vec3 world=Center+flameRight*p.x*Shape.y+flameUp*p.y*Shape.y+flameAxis*p.z*Shape.x;
    gl_Position=ViewProjection*vec4(world,1.0);
}
