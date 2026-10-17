#version 150
uniform vec4 ColorModulator;
uniform float GameTime;
uniform float FogStart;
uniform float FogEnd;
in vec4 vertexColor;
in vec2 effectUV;
in float viewDistance;
in vec3 localPosition;
out vec4 fragColor;
float fogFade() { return clamp((FogEnd-viewDistance)/max(FogEnd-FogStart,0.001),0.0,1.0); }
float hash(vec3 p) { p=fract(p*0.1031);p+=dot(p,p.yzx+33.33);return fract((p.x+p.y)*p.z); }
float noise(vec3 p) {
 vec3 i=floor(p),f=fract(p);f=f*f*(3.0-2.0*f);
 return mix(mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),
 mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),mix(hash(i+vec3(0,1,1)),hash(i+vec3(1,1,1)),f.x),f.y),f.z);
}
float fbm(vec3 p) { return noise(p)*0.58+noise(p*2.03+17.1)*0.28+noise(p*4.07+41.7)*0.14; }
void main() {
 vec2 p=effectUV*2.0-1.0;float r2=dot(p,p);if(r2>=1.0)discard;
 float t=GameTime*1200.0;float depth=sqrt(1.0-r2);float density=0.0;
 // Sample an animated three-dimensional density through the visible ellipsoid.
 for(int i=0;i<6;i++) {
  float z=mix(-depth,depth,(float(i)+0.5)/6.0);
  vec3 q=vec3(p,z);
  vec3 advect=vec3(t*0.12,-t*0.19,t*0.09);
  float n=fbm(q*2.8+localPosition*0.37+advect);
  density+=smoothstep(0.23,0.72,n)*max(0.0,1.0-dot(q,q))*depth/6.0;
 }
 float alpha=(1.0-exp(-density*3.1))*vertexColor.a*(1.0-smoothstep(0.65,1.0,r2));
 if(alpha<0.003)discard;
 vec3 color=vertexColor.rgb*mix(0.80,1.10,clamp(density*2.0,0.0,1.0));
 fragColor=vec4(color*ColorModulator.rgb,alpha*ColorModulator.a*fogFade());
}
