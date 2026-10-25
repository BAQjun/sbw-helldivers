#version 150
uniform sampler2D SceneDepth;
uniform sampler2D Fog;
uniform mat4 InverseProjection;
in vec2 texCoord;
out vec4 fragColor;
float viewDepth(vec2 uv) {
    float d=texture(SceneDepth,uv).r;
    vec4 p=InverseProjection*vec4(uv*2.0-1.0,d*2.0-1.0,1.0);
    return abs(p.z/p.w);
}
void main() {
    vec2 size=vec2(textureSize(Fog,0));
    vec2 grid=texCoord*size-0.5,cell=floor(grid),f=fract(grid);
    float here=viewDepth(texCoord),total=0.0,bestDelta=1e30;
    vec4 sum=vec4(0.0),closest=vec4(0.0);
    for(int y=0;y<2;y++)for(int x=0;x<2;x++) {
        vec2 uv=(cell+vec2(x,y)+0.5)/size;
        float dz=abs(viewDepth(uv)-here);
        float bilinear=(x==0?1.0-f.x:f.x)*(y==0?1.0-f.y:f.y);
        // Reject samples from the other side of a foreground silhouette.
        float weight=bilinear*exp(-dz/max(0.12,here*0.002));
        vec4 fog=texture(Fog,uv);
        sum+=fog*weight;total+=weight;
        if(dz<bestDelta){bestDelta=dz;closest=fog;}
    }
    fragColor=total>0.00001?sum/total:closest;
}
