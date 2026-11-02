precision highp float;
            varying vec2 v;
            uniform sampler2D image;
            uniform int gba;
            void main() {
                vec3 c=texture2D(image,v).rgb;
                // Adapted from gba-color.glsl, hunterk / Pokefan531, public domain.
                if(gba==1) {
                    c=pow(c,vec3(3.2))*0.94;
                    c=mat3(0.82,0.125,0.195, 0.24,0.665,0.075, -0.06,0.21,0.73)*c;
                    c=pow(clamp(c,0.0,1.0),vec3(1.0/2.2));
                }
                gl_FragColor=vec4(c,1.);
            }

