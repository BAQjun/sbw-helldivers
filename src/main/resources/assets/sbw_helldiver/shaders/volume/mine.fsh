#version 150
uniform sampler2D SceneDepth;
uniform vec2 Viewport;
in vec3 color;
out vec4 fragColor;
void main(){
    if(gl_FragCoord.z>texture(SceneDepth,gl_FragCoord.xy/Viewport).r+.000002)discard;
    fragColor=vec4(color*2.4,1.);
}
