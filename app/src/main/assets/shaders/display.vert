precision highp float; attribute vec2 pos; attribute vec2 uv; varying vec2 v; void main(){v=uv;gl_Position=vec4(pos,0.,1.);}
