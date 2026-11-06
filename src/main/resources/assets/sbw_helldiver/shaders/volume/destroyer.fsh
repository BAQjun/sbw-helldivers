#version 150
uniform sampler2D Surface;
uniform sampler2D SceneDepth;
uniform mat4 InverseProjection;
uniform vec2 Viewport;
uniform float DistanceScale;
uniform vec4 Tint;
in vec2 texCoord;
in float brightness;
in vec3 viewPosition;
out vec4 fragColor;
void main(){
    vec2 screen=gl_FragCoord.xy/Viewport;
    float depth=texture(SceneDepth,screen).r;
    if(depth<.9999999){
        vec4 surface=InverseProjection*vec4(screen*2.0-1.0,depth*2.0-1.0,1.0);
        if(length(viewPosition)/DistanceScale>length(surface.xyz/surface.w)+.06)discard;
    }
    vec4 color=texture(Surface,texCoord);
    if(color.a<.1)discard;
    fragColor=vec4(color.rgb*brightness, color.a)*Tint;
}
