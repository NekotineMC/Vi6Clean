#version 330

#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;
in vec2 UV0;

out vec2 texCoord0;

const mat4 fixedProjMat = mat4(
	   0.76, 0.0, 0.0, 0.0,
	   0.0, 1.43, 0.0, 0.0,
	   0.0, 0.0, -1.0, -1.0,
	   0.0, 0.0, -0.1, 0.0
	);

void main() {
    vec3 pos = Position + ModelOffset;
    gl_Position = fixedProjMat * ModelViewMat * vec4(pos, 1.0);

    texCoord0 = (TextureMat * vec4(UV0, 0.0, 1.0)).xy;
}
