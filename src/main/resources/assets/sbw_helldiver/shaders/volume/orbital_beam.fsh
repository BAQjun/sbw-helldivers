#version 150
uniform sampler2D SceneDepth;
uniform vec2 Viewport;
uniform mat4 InverseProjection;
uniform mat3 InverseViewRotation;
uniform vec3 Contact;
uniform vec3 Origin;
uniform float Strength;
uniform bool Trail;
out vec4 fragColor;
void main(){
    vec2 uv=gl_FragCoord.xy/Viewport;
    vec4 view=InverseProjection*vec4(uv*2.0-1.0,1.0,1.0);
    vec3 ray=normalize(InverseViewRotation*(view.xyz/view.w));
    vec3 segment=Origin-Contact;
    float len=length(segment);
    if(len<.001)discard;
    vec3 axis=segment/len;
    if(Trail){
        // Closest points of a forward camera ray and the finite projectile tail.
        // Keeping endpoints handles views almost parallel to the firing line.
        float d=dot(ray,axis),a=dot(ray,Contact),b=dot(axis,Contact);
        float denominator=max(1e-7,1.0-d*d);
        float along=clamp((d*a-b)/denominator,0.0,len);
        float distance=max(0.0,a+d*along);
        along=clamp(d*distance-b,0.0,len);
        distance=max(0.0,a+d*along);
        vec3 delta=ray*distance-(Contact+axis*along);
        float radial=length(delta);
        // At ship distance a physical ribbon is subpixel. Retain a small,
        // antialiased footprint without stretching the trail's world length.
        float pixel=distance*abs(InverseProjection[1][1])*2.0/Viewport.y;
        float radius=max(.24,pixel*.8),aa=max(pixel*.45,.01);
        float core=1.0-smoothstep(radius*.3,radius+aa,radial);
        float halo=1.0-smoothstep(radius*.6,radius*2.6+aa,radial);
        if(halo<.001)discard;
        float scene=texture(SceneDepth,uv).r;
        if(scene<.9999999){
            vec4 surface=InverseProjection*vec4(uv*2.0-1.0,scene*2.0-1.0,1.0);
            if(distance>length(surface.xyz/surface.w)+.06)discard;
        }
        float taper=1.0-smoothstep(.5,1.0,along/len)*.8;
        fragColor=vec4((vec3(1.3,1.05,.65)*core+vec3(.18,.065,.008)*halo)*Strength*taper,0.0);
        return;
    }
    // Intersect a cylinder clipped by the contact/ship planes. Clamping the
    // closest segment point instead would round the tip into a visible capsule.
    float d=dot(ray,axis),a=dot(ray,Contact),b=dot(axis,Contact);
    float qa=-b, A=max(0.0,1.0-d*d), B=-a+b*d;
    float C=dot(Contact,Contact)-b*b-1.21;
    float nearT=0.0,farT=1e20;
    if(A<1e-7){
        if(C>0.0)discard;
    }else{
        float discriminant=B*B-A*C;
        if(discriminant<0.0)discard;
        float root=sqrt(discriminant);
        nearT=max(nearT,(-B-root)/A);farT=min(farT,(-B+root)/A);
    }
    if(abs(d)<1e-7){
        if(qa<0.0||qa>len)discard;
    }else{
        float cap0=-qa/d,cap1=(len-qa)/d;
        nearT=max(nearT,min(cap0,cap1));farT=min(farT,max(cap0,cap1));
    }
    if(farT<=nearT)discard;
    float scene=texture(SceneDepth,uv).r;
    if(scene<.9999999){
        vec4 surface=InverseProjection*vec4(uv*2.0-1.0,scene*2.0-1.0,1.0);
        float sceneDistance=length(surface.xyz/surface.w);
        // Keep the previously fixed real-world depth, including foreground walls.
        if(nearT>sceneDistance+.06)discard;
        farT=min(farT,sceneDistance+.06);
    }
    float closest=A<1e-7?nearT:clamp(-B/A,nearT,farT);
    vec3 point=ray*closest-Contact;
    float along=max(0.0,dot(point,axis));
    float radial=length(point-axis*along);
    // The warm sheath resolves into the white impact core over the last two
    // metres, rather than ending as a broad, flat orange sleeve.
    float merge=smoothstep(.10,2.1,along);
    float sheathRadius=mix(.40,1.1,merge);
    float aa=max(.001,fwidth(radial));
    float halo=(1.0-smoothstep(sheathRadius*.24,sheathRadius+aa,radial))*merge;
    float mid=(1.0-smoothstep(.23,mix(.36,.65,merge)+aa,radial))*mix(.18,1.0,merge);
    float core=1.0-smoothstep(.24,.34+aa,radial);
    if(max(halo,max(mid,core))<.001)discard;
    vec3 hot=mix(vec3(1.15,1.12,.97),vec3(1.0,.85,.42),merge);
    vec3 light=vec3(.09,.025,.001)*halo+vec3(.35,.15,.01)*mid+hot*core;
    fragColor=vec4(light*Strength,0.0);
}
