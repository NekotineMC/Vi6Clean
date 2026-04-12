{
  description = "Install dependencies to build and run the docker image";
  inputs = {
    nixpkgs.url = "github:nixos/nixpkgs?rev=68994fbdc450f70230c7b97ab3536b6061057462";
    systems.url = "github:nix-systems/default";
    flake-utils = {
      url = "github:numtide/flake-utils";
      inputs.systems.follows = "systems";
    };
  };
  outputs = {
    nixpkgs,
    flake-utils,
    ...
  }:
    flake-utils.lib.eachDefaultSystem (
      system: let
        pkgs = nixpkgs.legacyPackages.${system};
      in {
        devShells.default = pkgs.mkShell {
          packages = with pkgs; [
            gradle
            jdt-language-server
          ];
        };
      }
    );
}
