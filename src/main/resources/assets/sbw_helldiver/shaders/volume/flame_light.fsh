#version 150
uniform sampler2D SceneDepth;
uniform sampler2D SceneColor;
uniform mat4 InverseProjection;
uniform mat4 ProjectionView;
uniform mat3 InverseViewRotation;
uniform vec4 Lights[48];
uniform int LightCount;
in vec2 texCoord;
out vec4 fragColor;
vec3 position(vec2 uv,float depth){
    vec4 p=InverseProjection*vec4(uv*2.-1.,depth*2.-1.,1.);
    return InverseViewRotation*(p.xyz/p.w);
}
float visible(vec3 p,vec3 light,vec3 normal){
    vec3 origin=p+normal*.075;
    for(int s=1;s<=6;s++){
        vec3 q=mix(origin,light,float(s)/7.);
        vec4 clip=ProjectionView*vec4(q,1.);
        if(clip.w<=0.)continue;
        vec2 uv=clip.xy/clip.w*.5+.5;
        if(any(lessThan(uv,vec2(0.)))||any(greaterThan(uv,vec2(1.))))continue;
        float depth=texture(SceneDepth,uv).r;
        if(depth<.999999&&length(position(uv,depth))+.16<length(q))return 0.;
    }
    return 1.;
}
void main(){
    vec4 base=texture(SceneColor,texCoord);float depth=texture(SceneDepth,texCoord).r;
    if(depth>=.999999){fragColor=base;return;}
    vec3 p=position(texCoord,depth);
    vec3 normal=normalize(cross(dFdx(p),dFdy(p)));
    if(dot(normal,-p)<0.)normal=-normal;
    vec3 energy=vec3(0.);
    for(int i=0;i<48;i++){
        if(i>=LightCount)break;
        vec3 delta=Lights[i].xyz-p;float d=length(delta);
        if(d>=5.8||d<.001)continue;
        float diffuse=max(0.,dot(normal,delta/d));
        if(diffuse<=.015)continue;
        float falloff=pow(1.-d/5.8,2.);
        energy+=vec3(1.,.37,.055)*falloff*diffuse*Lights[i].w*visible(p,Lights[i].xyz,normal)*1.6;
    }
    // Relight the underlying material in linear space; the fire is composited
    // afterwards and retains its independently controlled emission/bloom.
    vec3 reflectance=clamp(base.rgb*.75+.055,0.,.9);
    vec3 linear=pow(max(base.rgb,vec3(0.)),vec3(2.2))+reflectance*energy;
    fragColor=vec4(pow(linear,vec3(1./2.2)),base.a);
}
