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
              "name" = "ODB_SERVICE_JWT";
              "value" = "eyJ0eXAiOiJKV1QiLCJhbGciOiJSUzUxMiJ9.eyJpc3MiOiJsdWN1bWEtc3NvIiwic3ViIjoiMjAxMiIsImF1ZCI6Imx1Y3VtYSIsImV4cCI6MjMyNjQ0OTkwNCwibmJmIjoxNjk1NzI5ODk0LCJpYXQiOjE2OTU3Mjk4OTQsCiAgImx1Y3VtYS11c2VyIiA6IHsKICAgICJ0eXBlIiA6ICJzZXJ2aWNlIiwKICAgICJpZCIgOiAidS03ZGMiLAogICAgIm5hbWUiIDogIm9ic2VydmUiCiAgfQp9.VAEkJCo9_oZdoJi8G6nUBr0Lupi8BXnkT1buKG7sNRIxk2nuQqE_641vZp1-ho8Eq8vbw7arQ1rIi4pAhxgAygq-Ylw-RaTFWhrLNcjKGCK23lLu0V5ITLiED1VViP_nciDtxsrb9My_-nohPMuEdljZkqep5DVo8aDZ2OAzop8m1e8aoPDfSwAOVTyI5oRJl0SjpK1kQ8EHFjcfQQuevd8UIV9f2L5_Wyl_dfHGkONk0DMiwF40rzWZang6xfxvtIIzaS_oBhzbNoPTUCuV4UuvG9AYnIVYK6595L4up2dvF9i4k1SZpElkNOuo9v7USSmaa1AKqEHF41NgMveabQ6LxD3b1NuTQZXR4LL44vPX5pWgDPwUC9bUDUm53ecXY_J94khMhuk1rP1xRU7z6qqfrerDvZUEx98VAaGpvsqs1oXAW_T7Dm5jAxf7VIKtMMIFXrhmiP3yQXKSIDsef3NdK9S5drr_j7Ec_GSV2tAtD54wvajZfucmxdKW3EobrdmoD-V7aCfI8tQyP_Ux6QdfeXG0LluGbIWBiUbsDtS0btdHDadLqJ6ihytoFiNF6YsHR_RjwJkc339ivhiN5dGhy5iC2hs1SP7y0m_BqzNWJr0WK6Xlt7G6syS_iw2cB6e9h3kc0r8vhy_IyKIGe3bE80YAE07nS2VDr66miTk";
            }
            {
              name = "ODB_SSO_PUBLIC_KEY";
              value = "-----BEGIN PGP PUBLIC KEY BLOCK-----

mQINBGQ1w9IBEAC8Td3AlypimgzF2/isMZSD3z63sUKpd/Lc3XZHjpKwbgNHA7/3
7ddE7VB8526Cn8DJwArL39DlKdCV5VB1VguLjnSfYD1C6GEHMmhGB5T2QiVBFVZD
3/XvMTF/9akrwPp4Y6CxUNOWej9Bube+pvUQZ4e5gz4yCduIMwU/zODpy4BJVc1u
86l3Xrt1FmCIgRzpD4coVrhtjAtsuXVH8eZvgMfgFY2c8whBBv8upTHxCLKfxbCN
pS9nOaZE+3ujI/+xoVw6RiOwrMR683Rs46TZGOo7IfPmpLwxtQt+XwZUHeEC5bMT
7wG9jebPPc0Ro0wrkwf9N6J0Fnp+gdcIT2AruxtR5hjVcwckORM26RYnCJ+sirpU
Tu0kw754d7Uvwrr15cSMjvSA/qlvdmqaquOGXS+aqM/OPecAVpcUJADG4H2KAXGq
d79OuspC/CCBoA6HJb+TBneP6UflKRVnZrdlhKc001yGiHS4X19HaJCu5Co6PNbN
G7H2Z0+NVBHR/GIYGZ2DS/yjE0R07WhC4mCbehC01InWARNzDqmF5zcVZUi0Kmb7
YHlJPURCG4+9qi1SBgYhVmPmPASy/vjsBVadPp5aGQFjYupv8gW3LTeq/uW+CZUw
gbPA5SKTk0VIUxwH9qqkbod98S67fuTP9ryFRJEo5wZrWsPx7pgE7E2V8QARAQAB
tCdMdWN1bWEgU1NPIERldiA8cm9iLm5vcnJpc0Bub2lybGFiLmVkdT6JAlcEEwEI
AEEWIQS0yfZiKQanqInSO1pcW28wo0EWRAUCZDXD0gIbAwUJA8JnAAULCQgHAgIi
AgYVCgkICwIEFgIDAQIeBwIXgAAKCRBcW28wo0EWRLBPEAC3T2c5BhgJ++RahDbt
f3gPpq2nAbVJyn2VI37uFXIfNFKkYdFTQh/wlb+qprNqQrbPNnRWKebq9qzcubTd
sADSwrM4imbBeyOEJsceyPeP6yAfaWcSpFXwtuLTVMb+eB9u985qNmu7kIC7gnak
SjkBdbKsM3HQvr3PrsNCZsy9ysGRBdIfDc/DDwoGhCU0Bqd5ORjzsS4u7SNiRrkc
+Dw3siX4cskwiDbCr21Bz4XJxpU86cx+idhSS7naiX6rN6KqZRhAO2FZOhL8/11u
sQPshz45m1mvQ4367fams8N2gtpX+1RKuVY6xcSvfa7rK6aWpjGC7u0tr2hcK0G5
NCiI6DPYllC2lyZPonycHHRaGLIQWIipJkP9cdu8ph+O/7qshEtb7nX3JlyRIxcW
kxQnqROrVqJALogmzmF+4OP8gTjY2ph8OmaPU8ATjdql5da1iHlDT5M/0oatZ6J2
lmYdT0LxnSMlMGFb9xOo1xeYK0/a5kR4fRET4m4g+x5N9UUPSJjfFhDa6iO89X0V
d/EKiM3//ukkw7RcwGLWw4hnqqxPdHvLM0yTKajc79pAQR3rOEcW1SrV5PECFSxD
HMeMka0SYzCqqtl0XWI1dlC0JXKnVfuDHOKVY523EKnEAcHqZ8oAZB//2Puj4qfO
yMvjw3Rl9GQnMoTGYsNsunNy4Q==
=8OhQ
-----END PGP PUBLIC KEY BLOCK----- ";
            }
          ];
        };
      }
    );
}
