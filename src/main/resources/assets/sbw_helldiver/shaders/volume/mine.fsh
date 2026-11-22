#version 150
uniform sampler2D SceneDepth;
uniform float DepthJitterScale;
in vec3 color;
out vec4 fragColor;

void main(){
    ivec2 pixel=ivec2(gl_FragCoord.xy);
    float lampDepth=gl_FragCoord.z;
    float sceneDepth=texelFetch(SceneDepth,pixel,0).r;
    float depthGap=lampDepth-sceneDepth;
    float tolerance=.000002;

    // Shader packs can jitter the scene depth before temporal AA. The lamp
    // layer is drawn after it, so compare against a one-pixel depth footprint.
    vec2 slope=vec2(dFdx(lampDepth),dFdy(lampDepth));
    float maximumMargin=DepthJitterScale*gl_FragCoord.w*gl_FragCoord.w;
    if(depthGap>tolerance+min(abs(slope.x)+abs(slope.y),maximumMargin)){
        if(depthGap>tolerance+maximumMargin)discard;
        ivec2 bounds=textureSize(SceneDepth,0)-1;
        bool visible=false;
        for(int y=-1;y<=1;y++)for(int x=-1;x<=1;x++){
            ivec2 offset=ivec2(x,y);
            float neighbor=texelFetch(SceneDepth,clamp(pixel+offset,ivec2(0),bounds),0).r;
            float neighborLampDepth=lampDepth+dot(vec2(offset),slope);
            visible=visible||neighborLampDepth<=neighbor+tolerance;
        }
        if(!visible)discard;
    }
    fragColor=vec4(color*2.4,1.);
}
