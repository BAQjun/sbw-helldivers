#version 150
uniform sampler2D SceneDepth;
uniform vec2 Viewport;
in vec3 coord;
in vec4 color;
out vec4 fragColor;
void main(){
    if(gl_FragCoord.z>texture(SceneDepth,gl_FragCoord.xy/Viewport).r+0.000002)discard;
    vec2 p=coord.xy*2.0-1.0;
    float edge=1.0-smoothstep(0.75,1.0,abs(p.y));
    float halo=exp(-p.y*p.y*6.0)*edge;
    float core=exp(-p.y*p.y*180.0)*edge;
    if(coord.z>0.5){float r=dot(p,p);halo=exp(-r*5.0)*(1.0-smoothstep(0.55,1.0,r));core=exp(-r*80.0);}
    vec3 light=color.rgb*halo*0.22+vec3(0.82,0.95,1.0)*core*1.5;
    fragColor=vec4(light*color.a,0.0);
}
