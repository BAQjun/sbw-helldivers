#version 150
#ifdef SBW_ENERGY_POST
uniform sampler2D SceneDepth;
uniform vec2 Viewport;
#endif
uniform vec4 ColorModulator;
uniform float GameTime;
uniform float FogStart;
uniform float FogEnd;
in vec4 vertexColor;
in vec2 effectUV;
in float viewDistance;
out vec4 fragColor;
float fogFade() { return clamp((FogEnd-viewDistance)/max(FogEnd-FogStart,0.001),0.0,1.0); }

void main() {
#ifdef SBW_ENERGY_POST
 if(gl_FragCoord.z>texture(SceneDepth,gl_FragCoord.xy/Viewport).r+0.000002)discard;
#endif
    float seconds=GameTime*1200.0;
    float along=effectUV.x+vertexColor.g*32.0;
    float extent=exp2(vertexColor.r*12.0-5.0);
    float cap=max(0.0,max(1.0-effectUV.x,effectUV.x-extent-1.0));
    float across=length(vec2(effectUV.y*2.0-1.0,cap));
    float pulse=0.86+0.09*sin(along*19.0-seconds*63.0)+0.05*sin(along*43.0+seconds*97.0);
    float aa=clamp(fwidth(across),0.001,0.075);
    float radius=0.11*pulse;
    float core=1.0-smoothstep(radius-aa,radius+aa,across);
    float corona=exp(-across*across/0.04);
    float halo=exp(-across*across/0.25)*(1.0-smoothstep(0.75,1.0,across));
    vec3 color=mix(vec3(0.13,0.37,1.0),vec3(0.69,0.87,1.0),corona);
    color=mix(color,vec3(0.96,0.99,1.0),core);
    float joint=smoothstep(-0.15,0.15,min(effectUV.x-1.0,extent+1.0-effectUV.x));
    float alpha=(core*0.88+corona*0.42+halo*0.12)*pulse*joint;
    fragColor=vec4(color*ColorModulator.rgb,
        min(alpha,1.0)*vertexColor.a*ColorModulator.a*fogFade());

#ifdef SBW_ENERGY_POST
 // Add light without dark rectangles or alpha accumulation.
 fragColor=vec4(fragColor.rgb*fragColor.a,0.0);
#endif
}
