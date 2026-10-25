#version 150
uniform sampler2D SceneDepth;
uniform sampler3D DensityNoise;
uniform mat4 InverseProjection;
uniform mat3 InverseViewRotation;
uniform vec2 Viewport;
uniform vec3 Eye;
uniform float Radius,Time,Seed,Extinction;
uniform vec3 FogColor;
in vec2 texCoord;
out vec4 fragColor;
uniform float Height;
uniform bool Smoke;
uniform vec3 SmokeNoiseOrigin;
float noise(vec3 p) {
    // Smooth 3D value noise, with no surface-oriented UV coordinates.
    vec3 cell=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
    return texture(DensityNoise,(cell+f+0.5)/64.0).r;
}
float density(vec3 p) {
    float radial=length(p.xz);
    if(radial>=Radius||p.y<=0.0||p.y>=Height)return 0.0;
    vec3 flow=p*vec3(0.65,0.85,0.65)+vec3(Seed-Time*0.16,-Time*0.20,Time*0.09);
    float detail=noise(flow),fine=noise(flow*2.03+17.1);
    if(Smoke) {
        // A billowing half-ellipsoid, warped at two scales. Density dies before
        // the integration/scissor bounds, so neither a flat lid nor box edges show.
        // Keep three noise lookups per sample, including the coarse shape field.
        // Shared world-space shape keeps overlapping canisters from filling
        // each other's height variation into a level, flat bank.
        float coarse=noise(p*vec3(0.22,0.30,0.22)+SmokeNoiseOrigin+vec3(9.1,0.0,42.7));
        float n=detail*0.60+fine*0.28+coarse*0.12;
        float rim=Radius*(0.82+0.12*coarse);
        float cap=Height*(0.60+0.32*coarse);
        float shape=length(vec2(radial/rim,p.y/cap));
        float edge=smoothstep(0.0,0.18,1.0-shape+(n-0.5)*0.12);
        return edge*smoothstep(0.0,0.30,p.y)*smoothstep(0.18,0.72,n);
    }
    float n=detail*0.60+fine*0.28+noise(flow*4.07+41.7)*0.12;
    float edge=smoothstep(0.0,min(1.4,Radius*0.3),Radius-radial);
    float bottom=smoothstep(0.0,0.30,p.y);
    float top=smoothstep(0.0,0.35,Height-p.y-(1.0-n)*0.15);
    return edge*bottom*top*smoothstep(0.22,0.73,n);
}
void main() {
    vec2 uv=gl_FragCoord.xy/Viewport;
    vec4 ray=InverseProjection*vec4(uv*2.0-1.0,1.0,1.0);
    vec3 viewDir=normalize(ray.xyz/ray.w);
    vec3 dir=normalize(InverseViewRotation*viewDir);
    // Ray/slab interval bounds work from outside and inside the volume.
    vec3 safeDir=mix(dir,vec3(1e-6),lessThan(abs(dir),vec3(1e-6)));
    vec3 nearBox=(vec3(-Radius,0.0,-Radius)-Eye)/safeDir;
    vec3 farBox=(vec3(Radius,Height,Radius)-Eye)/safeDir;
    vec3 lo=min(nearBox,farBox),hi=max(nearBox,farBox);
    float start=max(0.06,max(lo.x,max(lo.y,lo.z)));
    float end=min(hi.x,min(hi.y,hi.z));
    float sceneDepth=texture(SceneDepth,uv).r;
    vec4 scene=InverseProjection*vec4(uv*2.0-1.0,sceneDepth*2.0-1.0,1.0);
    if(sceneDepth<0.999999)end=min(end,length(scene.xyz/scene.w));
    if(end<=start){fragColor=vec4(0.0);return;}
    float steps=clamp(ceil((end-start)/0.16),1.0,160.0);
    float dt=(end-start)/steps;
    float jitter=fract(sin(dot(gl_FragCoord.xy,vec2(12.9898,78.233)))*43758.5453);
    vec3 sum=vec3(0.0);float transmission=1.0;
    for(int i=0;i<160;i++) {
        if(float(i)>=steps||transmission<0.012)break;
        vec3 p=Eye+dir*(start+(float(i)+jitter)*dt);
        float rho=density(p);
        float opacity=1.0-exp(-rho*Extinction*dt);
        // A broad height gradient gives the body a shaded underside rather
        // than a uniformly luminous layer; no billboard or slice edges exist.
        vec3 scatter=FogColor*mix(0.73,1.07,clamp(p.y/Height,0.0,1.0));
        sum+=transmission*opacity*scatter;
        transmission*=1.0-opacity;
    }
    fragColor=vec4(sum,1.0-transmission);
}
