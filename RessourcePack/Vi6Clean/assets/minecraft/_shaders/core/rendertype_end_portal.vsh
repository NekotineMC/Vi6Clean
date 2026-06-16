#version 330

#moj_import <minecraft:fog.glsl>
#moj_import <minecraft:projection.glsl>
#moj_import <minecraft:dynamictransforms.glsl>
#moj_import <minecraft:projection.glsl>

in vec3 Position;

out vec4 texProj0;
out float sphericalVertexDistance;
out float cylindricalVertexDistance;

const mat4 fixedProjMat = mat4(
	   0.76, 0.0, 0.0, 0.0,
	   0.0, 1.43, 0.0, 0.0,
	   0.0, 0.0, -1.0, -1.0,
	   0.0, 0.0, -0.1, 0.0
	);

void main() {
    gl_Position = fixedProjMat * ModelViewMat * vec4(Position, 1.0);

    texProj0 = projection_from_position(gl_Position);
    sphericalVertexDistance = fog_spherical_distance(Position);
    cylindricalVertexDistance = fog_cylindrical_distance(Position);
}
