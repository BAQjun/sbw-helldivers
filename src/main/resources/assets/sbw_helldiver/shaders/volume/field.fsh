#version 150
uniform sampler2D SceneDepth;
uniform sampler3D DensityNoise;
uniform sampler2D TerrainFloor;
uniform vec2 TerrainOrigin,TerrainSize;
uniform float DownwardDepth;
uniform mat4 InverseProjection;
uniform mat3 InverseViewRotation;
uniform vec2 Viewport;
uniform vec3 Eye;
uniform float Radius,Time,Seed,Extinction;
uniform vec3 FogColor;
in vec2 texCoord;
out vec4 fragColor;
uniform float Height,CanopyLift;
uniform bool Smoke;
uniform int SprayMode;
uniform vec3 SprayAxis,SprayBoxMin,SprayBoxMax;
uniform float SprayLength;
uniform vec3 SmokeNoiseOrigin;
float noise(vec3 p) {
    // Smooth 3D value noise, with no surface-oriented UV coordinates.
    vec3 cell=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
    return texture(DensityNoise,(cell+f+0.5)/64.0).r;
}
float sprayDensity(vec3 p) {
    float along=dot(p,SprayAxis);
    float t=along/SprayLength+0.5;
    if(t<=0.0||t>=1.0)return 0.0;

    // The fine jet feeds an increasingly wide, irregular veil that sags as it travels.
    vec3 across=p-SprayAxis*along;
    across.y+=0.30*t*t;
    float width=mix(0.08,min(0.72,0.31+SprayLength*0.052),pow(t,0.83));
    if(length(across)>width+0.13)return 0.0;
    vec3 flow=p*vec3(2.2,2.8,2.2)+SmokeNoiseOrigin-SprayAxis*(Time*9.0);
    float roll=noise(flow);
    float detail=roll*0.70+noise(flow*2.3+12.3)*0.30;
    float veil=smoothstep(-0.12,0.80,1.0-length(across)/width+(roll-0.5)*0.85);
    return veil*(0.18+0.82*detail)*smoothstep(0.0,0.18,t)*(1.0-smoothstep(0.84,1.0,t));
}
float settledMist(vec3 p) {
    float floorY=texture(TerrainFloor,(floor(p.xz-TerrainOrigin)+0.5)/TerrainSize).r;
    if(p.y<=floorY)return 0.0;

    // Each emission drifts away from its own spray endpoint. Contact with the
    // ground limits the fall and redirects expansion sideways.
    float fall=0.42*Time+1.03*Time*Time;
    float freeCenter=0.16-fall;
    float contact=smoothstep(-0.22,0.45,floorY+0.33-freeCenter);
    float centerY=mix(freeCenter,max(freeCenter,floorY+0.33),contact);
    vec3 q=p-SprayAxis*(0.50*(1.0-exp(-3.0*Time)));
    q.y-=centerY;
    float spread=1.0-exp(-3.0*Time);
    float width=0.36+0.70*spread+0.86*contact*spread;
    float above=max(0.0,q.y);
    float height=mix(0.66+0.62*spread,0.74+0.36*spread,contact);
    float top=above/height;
    float across=length(q.xz)/(width*mix(1.0,0.57,smoothstep(0.0,1.0,top)));
    float vertical=q.y/(q.y>0.0?height:mix(0.52,0.38,contact));
    float shape=length(vec2(across,vertical));
    if(shape>=1.27)return 0.0;

    vec3 flow=q*vec3(0.94,1.10,0.94)+SmokeNoiseOrigin+vec3(-Time*0.32,Time*0.24,Time*0.21);
    float detail=noise(flow)*0.72+noise(flow*2.4+17.1)*0.28;
    float edge=smoothstep(0.0,0.58,1.0-shape+(detail-0.5)*0.52);
    return edge*(0.40+0.60*detail)*smoothstep(0.0,0.22,p.y-floorY)
        *(1.0-0.70*smoothstep(0.05,0.95,top));
}
float density(vec3 p) {
    if(SprayMode==2)return sprayDensity(p);
    float radial=length(p.xz);
    if(radial>=Radius||p.y<=-DownwardDepth||p.y>=Height)return 0.0;
    if(SprayMode==1)return settledMist(p);
    // Reject solid terrain before sampling the more expensive 3D density.
    // One texel represents one block column: a solid ledge cannot be averaged
    // down into a neighboring pit by texture filtering.
    float floorY=p.y<1.01?texture(TerrainFloor,(floor(p.xz-TerrainOrigin)+0.5)/TerrainSize).r:0.0;
    if(p.y<=floorY)return 0.0;
    vec3 flow=p*vec3(0.65,0.85,0.65)+vec3(Seed-Time*0.16,-Time*0.20,Time*0.09);
    float detail=noise(flow),fine=noise(flow*2.03+17.1);
    if(Smoke) {
        // A billowing asymmetric ellipsoid, warped at two scales. Density dies before
        // the integration/scissor bounds, so neither a flat lid nor box edges show.
        // Keep three noise lookups per sample, including the coarse shape field.
        // Shared world-space shape keeps overlapping canisters from filling
        // each other's height variation into a level, flat bank.
        float coarse=noise(p*vec3(0.22,0.30,0.22)+SmokeNoiseOrigin+vec3(9.1,0.0,42.7));
        float n=detail*0.60+fine*0.28+coarse*0.12;
        float rim=Radius*(0.82+0.12*coarse);
        // Small gas fields need a visible four-block body, not merely a
        // four-block bounding box whose cap and feathering erase its top.
        // Add headroom above the body; large clouds retain their original shape.
        float cap=(Height-CanopyLift)*(0.60+0.32*coarse)+CanopyLift;
        float underside=max(0.001,DownwardDepth*(0.55+0.40*coarse));
        float vertical=p.y/(p.y>=0.0?cap:underside);
        // A fourth-power upper profile gives small gas clouds broad rounded
        // shoulders instead of stretching their centre into a narrow peak.
        if(p.y>0.0)vertical=mix(vertical,vertical*vertical,clamp(CanopyLift*0.5,0.0,1.0));
        float shape=length(vec2(radial/rim,vertical));
        float edge=smoothstep(0.0,0.18,1.0-shape+(n-0.5)*0.12);
        return edge*smoothstep(0.0,0.30,p.y-floorY)*smoothstep(0.18,0.72,n);
    }
    float n=detail*0.60+fine*0.28+noise(flow*4.07+41.7)*0.12;
    float edge=smoothstep(0.0,min(1.4,Radius*0.3),Radius-radial);
    float underside=DownwardDepth*sqrt(max(0.0,1.0-radial*radial/(Radius*Radius)))*(0.60+0.35*n);
    float bottom=smoothstep(0.0,0.30,p.y-max(floorY,-underside));
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
    vec3 boxMin=SprayMode==2?SprayBoxMin:vec3(-Radius,-DownwardDepth,-Radius);
    vec3 boxMax=SprayMode==2?SprayBoxMax:vec3(Radius,Height,Radius);
    vec3 nearBox=(boxMin-Eye)/safeDir;
    vec3 farBox=(boxMax-Eye)/safeDir;
    vec3 lo=min(nearBox,farBox),hi=max(nearBox,farBox);
    float start=max(0.06,max(lo.x,max(lo.y,lo.z)));
    float end=min(hi.x,min(hi.y,hi.z));
    float sceneDepth=texture(SceneDepth,uv).r;
    vec4 scene=InverseProjection*vec4(uv*2.0-1.0,sceneDepth*2.0-1.0,1.0);
    if(sceneDepth<0.999999)end=min(end,length(scene.xyz/scene.w));
    if(end<=start){fragColor=vec4(0.0);return;}
    float steps=clamp(ceil((end-start)/0.24),1.0,112.0);
    float dt=(end-start)/steps;
    float jitter=fract(sin(dot(gl_FragCoord.xy,vec2(12.9898,78.233)))*43758.5453);
    vec3 sum=vec3(0.0);float transmission=1.0;
    for(int i=0;i<112;i++) {
        if(float(i)>=steps||transmission<0.012)break;
        vec3 p=Eye+dir*(start+(float(i)+jitter)*dt);
        float rho=density(p);
        if(rho<=0.0)continue;
        float opacity=1.0-exp(-rho*Extinction*dt);
        // A broad height gradient gives the body a shaded underside rather
        // than a uniformly luminous layer; no billboard or slice edges exist.
        vec3 scatter=FogColor*mix(0.73,1.07,clamp(p.y/Height,0.0,1.0));
        sum+=transmission*opacity*scatter;
        transmission*=1.0-opacity;
    }
    fragColor=vec4(sum,1.0-transmission);
}
