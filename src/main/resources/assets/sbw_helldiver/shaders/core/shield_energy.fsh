#version 150
uniform vec4 ColorModulator;
uniform float EffectTime;
uniform float Strength;
uniform float ShieldRadius;
uniform float BreakFlash;
uniform float EffectMode;
uniform vec3 ViewVector;
uniform vec4 Hit0;uniform vec4 Hit1;uniform vec4 Hit2;uniform vec4 Hit3;
uniform vec4 Hit4;uniform vec4 Hit5;uniform vec4 Hit6;uniform vec4 Hit7;
in vec2 effectUV;
in vec4 vertexColor;
out vec4 fragColor;
float hexline(vec2 p){vec2 r=vec2(1.,1.73205);vec2 a=mod(p,r)-r*.5;vec2 b=mod(p-r*.5,r)-r*.5;vec2 v=abs(dot(a,a)<dot(b,b)?a:b);float d=max(v.x,dot(v,vec2(.5,.866025)));return smoothstep(.438,.49,d);}
float ripple(vec3 n,vec4 h){if(h.w<0.||h.w>1.2||dot(h.xyz,h.xyz)<.5)return 0.;float d=acos(clamp(dot(n,h.xyz),-1.,1.))*ShieldRadius;float front=h.w*4.7;float ring=exp(-pow((d-front)/(.10+h.w*.13),2.));return ring*pow(1.-h.w/1.2,2.)+exp(-d*d*5.)*exp(-h.w*12.)*.7;}
void main(){
 if(EffectMode>.5){float core=exp(-pow((effectUV.x-.5)*4.8,2.));float flow=.8+.2*sin(effectUV.y*55.-EffectTime*38.);vec3 c=mix(vec3(1.,.57,.16),vec3(1.,.96,.76),core);fragColor=vec4(c,(.12+.82*core)*Strength*flow)*vertexColor*ColorModulator;return;}
 float a=effectUV.y*3.14159265;float b=effectUV.x*6.2831853;vec3 n=vec3(sin(a)*cos(b),cos(a),sin(a)*sin(b));vec3 w=pow(abs(n),vec3(8.));w/=max(w.x+w.y+w.z,.001);vec3 p=n*ShieldRadius*1.25;
 float grid=hexline(p.yz)*w.x+hexline(p.xz)*w.y+hexline(p.xy)*w.z;
 float rim=pow(1.-clamp(abs(dot(n,normalize(ViewVector-n*ShieldRadius))),0.,1.),3.4);
 float pulse=.82+.18*sin(EffectTime*2.5+n.y*6.);
 float r=ripple(n,Hit0)+ripple(n,Hit1)+ripple(n,Hit2)+ripple(n,Hit3)+ripple(n,Hit4)+ripple(n,Hit5)+ripple(n,Hit6)+ripple(n,Hit7);
 float energy=clamp(r,0.,1.5);float alpha=(.012+.22*grid*pulse+.36*rim+.85*energy+.32*BreakFlash)*Strength;
 vec3 color=mix(vec3(1.,.78,.32),vec3(1.,.96,.73),clamp(energy+rim*.5+BreakFlash,0.,1.));
 fragColor=vec4(color,clamp(alpha,0.,.9))*vertexColor*ColorModulator;
}
