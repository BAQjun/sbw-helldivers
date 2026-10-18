#version 150
uniform sampler2D Flame;
uniform sampler2D Bloom;
uniform int Mode;
uniform float BloomStrength;
uniform vec2 Step;
in vec2 texCoord;
out vec4 fragColor;
void main(){
    if(Mode==0){
        // Average a 4x4 footprint before thresholding to retain narrow fire cores.
        vec2 pixel=1./vec2(textureSize(Flame,0));vec3 light=vec3(0.);
        for(int y=0;y<4;y++)for(int x=0;x<4;x++){
            vec3 c=texture(Flame,texCoord+(vec2(x,y)-1.5)*pixel).rgb;
            float energy=max(c.r,max(c.g,c.b));
            light+=c*smoothstep(.18,.85,energy);
        }
        fragColor=vec4(light/16.,0.);
    }else if(Mode==1){
        vec3 c=texture(Flame,texCoord).rgb*.227027;
        c+=(texture(Flame,texCoord+Step*1.384615).rgb+texture(Flame,texCoord-Step*1.384615).rgb)*.316216;
        c+=(texture(Flame,texCoord+Step*3.230769).rgb+texture(Flame,texCoord-Step*3.230769).rgb)*.070270;
        fragColor=vec4(c,0.);
    }else{
        vec4 flame=texture(Flame,texCoord);
        // Preserve amber edges rather than whitening the whole plume. Additive
        // light has zero coverage outside the flame and cannot darken the scene.
        vec3 bloom=texture(Bloom,texCoord).rgb*BloomStrength;
        fragColor=vec4(min(flame.rgb,vec3(flame.a*1.12))+bloom,flame.a);
    }
}
