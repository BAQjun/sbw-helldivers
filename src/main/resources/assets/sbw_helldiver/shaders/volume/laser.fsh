#version 150
uniform sampler2D SceneDepth;
uniform sampler2D DisplayTexture;
uniform vec2 Viewport;
in vec3 coord;
in vec4 color;
out vec4 fragColor;
void main(){
    if(gl_FragCoord.z>texture(SceneDepth,gl_FragCoord.xy/Viewport).r+.000002)discard;
    if(coord.z>2.5){fragColor=vec4(texture(DisplayTexture,coord.xy).rgb,1.);return;}
    if(coord.z>1.5){fragColor=color;return;}
    vec2 p=coord.xy*2.-1.;
    float halo=exp(-p.y*p.y*7.)*(1.-smoothstep(.75,1.,abs(p.y)));
    float core=exp(-p.y*p.y*210.);
    if(coord.z>.5){float r=dot(p,p);halo=exp(-r*6.)*(1.-smoothstep(.55,1.,r));core=exp(-r*90.);}
    float alpha=clamp((core*.94+halo*.12)*color.a,0.,1.);
    vec3 light=(color.rgb*halo*.20+vec3(2.8,2.6,1.8)*core)*color.a;
    if(alpha<.001)discard;
    fragColor=vec4(light,alpha);
}
