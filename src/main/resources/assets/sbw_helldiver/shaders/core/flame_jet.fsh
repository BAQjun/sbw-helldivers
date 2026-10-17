#version 150
uniform vec4 ColorModulator;
uniform float FogStart;
uniform float FogEnd;
in vec4 vertexColor;
in vec2 effectUV;
in float viewDistance;
in vec3 localPosition;
out vec4 fragColor;
float hash(vec3 p){p=fract(p*.1031);p+=dot(p,p.yzx+33.33);return fract((p.x+p.y)*p.z);}
float noise(vec3 p){vec3 i=floor(p),f=fract(p);f=f*f*(3.-2.*f);return mix(mix(mix(hash(i),hash(i+vec3(1,0,0)),f.x),mix(hash(i+vec3(0,1,0)),hash(i+vec3(1,1,0)),f.x),f.y),mix(mix(hash(i+vec3(0,0,1)),hash(i+vec3(1,0,1)),f.x),mix(hash(i+vec3(0,1,1)),hash(i+vec3(1,1,1)),f.x),f.y),f.z);}
float fbm(vec3 p){return noise(p)*.57+noise(p*2.07+9.3)*.28+noise(p*4.11-7.4)*.15;}


void main(){
 vec2 p=effectUV*2.-1.;float age=vertexColor.r,seed=vertexColor.g;
 int kind=int(floor(vertexColor.b*3.+.5));
 vec3 q=vec3(p.x*2.2-age*5.5+seed*31.,p.y*3.7,seed*17.+age*1.5);
 float n=fbm(q);float fine=noise(q*2.9+7.);
 float bend=(n-.5)*.58+sin(p.x*3.+seed*12.+age*4.)*.10;
 float v=p.y+bend;
 // Long ragged tongues, not round disks. Motion comes from the world-space parcels.
 float edge=abs(v)+abs(p.x)*.24;
 float taper=mix(.72,.30,smoothstep(-.25,1.,p.x));
 float shape=1.-smoothstep(taper-.18+(n-.5)*.20,taper+.22+(n-.5)*.28,abs(v));
 float end=1.-smoothstep(.66,1.,abs(p.x));
 // One warped, closed envelope: separate X/Y ramps left straight carrier edges.
 float radial=length(vec2(p.x/.94,v/max(.23,taper)));
 float organic=1.-smoothstep(.50,.99,radial+(n-.5)*.34+(fine-.5)*.10);
 // Keep a continuous burning body; holes open mainly in the dying tail.
 float turbulence=smoothstep(.20,.61,n+(fine-.5)*.22);
 float fill=kind==1?mix(.42,.0,smoothstep(.48,.88,age)):0.;
 float density=organic*mix(fill,1.,turbulence);
 // Side tongues interrupt the parcel silhouette instead of leaving a smooth lozenge.
 float tongue=(1.-smoothstep(.07,.20,abs(v-sin(p.x*4.+seed*19.)*.43)))*smoothstep(.50,.75,fine)*end;
 density=max(density,tongue*.55*organic);
 vec3 color;
 if(kind==0){
  density=(1.-smoothstep(.22,.82,abs(p.y)))*end*(.96+.04*n);
  color=mix(vec3(.88,.94,1.),vec3(1.,1.,.96),smoothstep(0.,.15,age));
 }else if(kind==3){
  density=organic*smoothstep(.30,.68,n);
  float shade=.055+n*.11;
  color=mix(vec3(.32,.18,.055),vec3(shade,shade*.95,shade*.88),smoothstep(0.,.27,age));
 }else{
  float hot=clamp(.27+(1.-abs(v))*.42+n*.16-age*.19,0.,1.);
  color=mix(vec3(1.,.23,.008),vec3(1.,.64,.035),smoothstep(.20,.44,hot));
  color=mix(color,vec3(1.,.95,.24),smoothstep(.41,.62,hot));
  color=mix(color,vec3(1.,1.,.96),smoothstep(.59,.80,hot));
 }
 // Turbulent UV displacement must never expose an edge of the carrier quad.
 float carrierFade=(1.-smoothstep(.58,1.,abs(p.y)))*(1.-smoothstep(.68,1.,abs(p.x)));
 float alpha=(kind==1||kind==2?1.-exp(-density*2.3):density)*vertexColor.a*carrierFade;
 if(alpha<.003)discard;
 float fog=clamp((FogEnd-viewDistance)/max(.001,FogEnd-FogStart),0.,1.);
 fragColor=vec4(color*ColorModulator.rgb,alpha*fog*ColorModulator.a);
}
