{
  inputs = {
    typelevel-nix.url = "github:typelevel/typelevel-nix";
    nixpkgs.follows = "typelevel-nix/nixpkgs";
    flake-utils.follows = "typelevel-nix/flake-utils";
  };

  outputs = { self, nixpkgs, flake-utils, typelevel-nix }:
    flake-utils.lib.eachDefaultSystem (system:
      let
        pkgs-x86_64 = import nixpkgs {
            system = "x86_64-darwin";
        };
        scala-cli-overlay = final: prev: {
            scala-cli = pkgs-x86_64.scala-cli;
        };
        pkgs = import nixpkgs {
          inherit system;
          overlays = [ typelevel-nix.overlays.default scala-cli-overlay];
        };
      in
      {
        devShell = pkgs.devshell.mkShell {
          imports = [ typelevel-nix.typelevelShell ];
          packages = [
            pkgs.nodePackages.vscode-langservers-extracted
            pkgs.nodePackages.prettier
            pkgs.websocat
          ];
          typelevelShell = {
            nodejs.enable = true;
            jdk.package = pkgs.jdk17;
          };
          env = [
            {
              "name" = "SITE";
              "value" = "development";
            }
          ];
        };
      }
    );
}
