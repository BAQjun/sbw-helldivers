#version 150
uniform sampler2D SceneDepth;
uniform sampler2D DisplayTexture;
uniform vec2 Viewport;
in vec3 coord;
in vec4 color;
out vec4 fragColor;
void main(){
    if(gl_FragCoord.z>texture(SceneDepth,gl_FragCoord.xy/Viewport).r+.000002)discard;
    if(coord.z>5.5){
        float edge=abs(coord.y*2.-1.);
        float core=1.-smoothstep(.12,.40,edge);
        float halo=exp(-edge*edge*5.)*(1.-smoothstep(.75,1.,edge));
        float alpha=(core*.9+halo*.28)*color.a;
        vec3 light=(vec3(3.2,3.5,4.)*core+color.rgb*halo*.65)*color.a;
        fragColor=vec4(light,clamp(alpha,0.,1.));return;
    }
    if(coord.z>4.5){fragColor=vec4(color.rgb*color.a*1.6,color.a);return;}
    if(coord.z>3.5){
        float t=coord.y;
        float radial=abs(coord.x*2.-1.)/max(.015,pow(1.-t,.75));
        float shape=pow(max(0.,1.-pow(radial,2.5)),1.6);
        float alpha=shape*smoothstep(0.,.035,t)*(1.-smoothstep(.88,1.,t))*color.a;
        float core=max(0.,1.-radial*2.4)*max(0.,1.-t*1.05);
        // Cyan-blue outer flame and a white core; no red/violet tail.
        vec3 light=mix(vec3(.16,.65,1.4),vec3(2.4,2.7,3.),core);
        if(alpha<.001)discard;
        fragColor=vec4(light*alpha,alpha);return;
    }
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
