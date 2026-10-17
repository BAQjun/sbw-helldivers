#version 150
uniform vec4 ColorModulator;
uniform float GameTime;
uniform float FogStart;
uniform float FogEnd;
in vec4 vertexColor;
in vec2 effectUV;
in float viewDistance;
out vec4 fragColor;
float fogFade() { return clamp((FogEnd-viewDistance)/max(FogEnd-FogStart,0.001),0.0,1.0); }

float hash(vec3 p) {
    p=fract(p*0.1031);p+=dot(p,p.yzx+33.33);
    return fract((p.x+p.y)*p.z);
}
float valueNoise(vec3 p) {
    vec3 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
    return mix(mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),
                   mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),
               mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),
                   mix(hash(i+vec3(0,1,1)),hash(i+vec3(1,1,1)),f.x),f.y),f.z);
}
void main() {
    vec2 p=effectUV*2.0-1.0;
    float r=length(p);
    if(r>=1.0)discard;
    float seconds=GameTime*1200.0;
    float depth=sqrt(max(0.0,1.0-r*r));
    float density=0.0;
    // Integrate a small animated volume instead of sampling a sphere sprite.
    for(int i=0;i<6;i++) {
        float z=mix(-depth,depth,(float(i)+0.5)/6.0);
        vec3 q=vec3(p,z);
        float n=valueNoise(q*5.0+vec3(seconds*1.8,-seconds*2.2,seconds*0.8));
        n+=0.45*valueNoise(q*11.0-vec3(seconds*3.1,seconds*0.4,seconds*2.7));
        float filament=pow(max(0.0,1.0-abs(n-0.72)*3.6),5.0);
        density+=exp(-dot(q,q)*7.5)*(0.22+filament*1.2)*depth/6.0;
    }
    float halo=exp(-r*r*7.0)*(1.0-smoothstep(0.74,1.0,r));
    float core=exp(-r*r*44.0);
    float brightness=clamp(density*4.0+core*0.7,0.0,1.0);
    vec3 color=mix(vec3(0.12,0.32,1.0),vec3(0.82,0.94,1.0),brightness);
    color=mix(color,vec3(1.0),core*0.8);
    fragColor=vec4(color*vertexColor.rgb*ColorModulator.rgb,
        clamp(density*3.2+core*0.65+halo*0.15,0.0,1.0)*vertexColor.a*ColorModulator.a*fogFade());
}
