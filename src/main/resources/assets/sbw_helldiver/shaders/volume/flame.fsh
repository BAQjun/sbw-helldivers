#version 150
uniform sampler2D SceneDepth;
uniform sampler3D DensityNoise;
uniform mat4 InverseProjection;
uniform mat3 InverseViewRotation;
uniform vec2 Viewport;
uniform float Time;
uniform vec3 CameraPosition;
flat in vec3 flameCenter,flameAxis,flameRight,flameUp;
flat in vec4 flameShape;
flat in vec2 flameStyle;
out vec4 fragColor;
float noise(vec3 p){vec3 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);return texture(DensityNoise,(i+f+.5)/64.).r;}
float fbm(vec3 p){return noise(p)*.60+noise(p*2.03+17.1)*.28+noise(p*4.07+41.7)*.12;}
vec3 local(vec3 v){return vec3(dot(v,flameRight)/flameShape.y,dot(v,flameUp)/flameShape.y,dot(v,flameAxis)/flameShape.x);}
void main(){
    vec2 uv=gl_FragCoord.xy/Viewport;
    vec4 projected=InverseProjection*vec4(uv*2.-1.,1.,1.);
    vec3 dir=normalize(InverseViewRotation*normalize(projected.xyz/projected.w));
    vec3 eye=local(-flameCenter),ray=local(dir);
    float a=dot(ray,ray),b=dot(eye,ray),c=dot(eye,eye)-1.;
    float disc=b*b-a*c;if(disc<=0.)discard;
    float near=max(.035,(-b-sqrt(disc))/a),far=(-b+sqrt(disc))/a;
    float depth=texture(SceneDepth,uv).r;
    if(depth<.999999){vec4 scene=InverseProjection*vec4(uv*2.-1.,depth*2.-1.,1.);far=min(far,length(scene.xyz/scene.w));}
    if(far<=near)discard;
    float stepCount=clamp(ceil((far-near)/(.045+flameShape.y*.10)),8.,36.);
    float dt=(far-near)/stepCount;
    float jitter=fract(sin(dot(gl_FragCoord.xy,vec2(12.9898,78.233)))*43758.5453);
    float age=flameShape.z,kind=flameStyle.x,seed=flameStyle.y;
    vec3 sum=vec3(0.);float transmission=1.;
    for(int i=0;i<36;i++){
        if(float(i)>=stepCount||transmission<.015)break;
        float distance=near+(float(i)+jitter)*dt;
        vec3 p=eye+ray*distance;
        vec3 w=CameraPosition+dir*distance;
        // Shared turbulence survives overlap: tongues retain structure instead
        // of becoming a smooth tube as the fuel stream grows denser.
        vec3 flow=(w-flameAxis*Time*2.8-vec3(0.,Time*.8,0.))*vec3(2.2,3.5,2.2);
        float n=fbm(flow),fine=noise(flow*2.9+7.);
        float envelope=1.-smoothstep(.32,1.,length(p));
        float rho=envelope*smoothstep(.24,.67,n+(fine-.5)*.23);
        vec3 color;float extinction;
        if(kind<.5){rho=envelope*(.82+.18*n);color=mix(vec3(.83,.91,1.),vec3(1.,.98,.77),smoothstep(.04,.3,age))*2.2;extinction=8.;}
        else if(kind>2.5){
            // Smoke rises out of spent fuel. Coherent world-space billows avoid
            // the featureless wall produced by averaging independent clouds.
            float billow=fbm(w*1.6-vec3(0.,Time*.9,0.));
            rho=envelope*smoothstep(.32,.65,billow);
            color=vec3(.038,.032,.027)*(.75+.7*billow);
            color+=vec3(.07,.032,.009)*pow(1.-age,2.);
            extinction=1.15;
        }
        else{
            float heat=clamp((1.-length(p.xy))*.46+n*.40+(1.-age)*.22,0.,1.);
            color=mix(vec3(1.,.10,.004),vec3(1.,.46,.012),smoothstep(.18,.44,heat));
            color=mix(color,vec3(1.,.87,.17),smoothstep(.43,.69,heat));
            color=mix(color,vec3(1.,.98,.77),smoothstep(.73,.95,heat));
            // HDR emission is composed with its own bloom after shader-pack
            // tonemapping, so Iris cannot clamp it into a dull yellow overlay.
            color*=kind<1.5?1.65:1.25;
            extinction=kind<1.5?3.8:1.6;
        }
        float opacity=1.-exp(-rho*extinction*flameShape.w*dt);
        sum+=transmission*opacity*color;transmission*=1.-opacity;
    }
    if(1.-transmission<.002)discard;
    fragColor=vec4(sum,1.-transmission);
}
