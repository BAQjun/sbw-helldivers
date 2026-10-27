#version 150
uniform sampler2D SceneDepth;
uniform vec2 Viewport;
uniform mat4 InverseProjection;
uniform mat3 InverseViewRotation;
uniform vec3 Contact;
uniform vec3 Origin;
uniform float Strength;
out vec4 fragColor;
void main(){
    vec2 uv=gl_FragCoord.xy/Viewport;
    vec4 view=InverseProjection*vec4(uv*2.0-1.0,1.0,1.0);
    vec3 ray=normalize(InverseViewRotation*(view.xyz/view.w));
    vec3 segment=Origin-Contact;
    float len=length(segment);
    if(len<.001)discard;
    vec3 axis=segment/len;
    // Closest points on a forward camera ray and a finite beam segment.
    float d=dot(ray,axis),a=dot(ray,Contact),b=dot(axis,Contact);
    float along=clamp((d*a-b)/max(1.0-d*d,1e-7),0.0,len);
    vec3 point=Contact+axis*along;
    float distance=max(0.0,dot(ray,point));
    float radial=length(point-ray*distance);
    float aa=max(.001,fwidth(radial));
    float halo=1.0-smoothstep(1.1-aa,1.1+aa,radial);
    float mid=1.0-smoothstep(.65-aa,.65+aa,radial);
    float core=1.0-smoothstep(.34-aa,.34+aa,radial);
    if(halo<.001||dot(ray,point)<0.0)discard;
    float scene=texture(SceneDepth,uv).r;
    if(scene<.9999999){
        vec4 surface=InverseProjection*vec4(uv*2.0-1.0,scene*2.0-1.0,1.0);
        float sceneDistance=length(surface.xyz/surface.w);
        // Compare in real world units, never in projected/compressed sky depth.
        float front=distance-sqrt(max(0.0,1.21-radial*radial))/sqrt(max(1e-7,1.0-d*d));
        if(front>sceneDistance+.06)discard;
    }
    vec3 light=vec3(.09,.025,.001)*halo+vec3(.35,.15,.01)*mid+vec3(1.0,.85,.42)*core;
    fragColor=vec4(light*Strength,0.0);
}
