{
  description = "Torvox — Android 终端模拟器";

  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    flake-parts.url = "github:hercules-ci/flake-parts";
    fenix.url = "github:nix-community/fenix";
  };

  outputs =
    inputs:
    inputs.flake-parts.lib.mkFlake { inherit inputs; } {
      systems = [
        "x86_64-linux"
        "aarch64-linux"
      ];

      perSystem =
        { pkgs, system, ... }:
        {
          _module.args.pkgs = import inputs.nixpkgs {
            inherit system;
            config.allowUnfree = true;
            overlays = [ inputs.fenix.overlays.default ];
          };

          packages.rust-toolchain = pkgs.fenix.stable.withComponents [
            "cargo"
            "clippy"
            "rust-src"
            "rustc"
            "rustfmt"
          ];

          # ── 格式化器 ──────────────────────────────────────
          formatter = pkgs.nixfmt-tree.override {
            nixfmtPackage = pkgs.nixfmt-rs;
            runtimeInputs = [
              pkgs.taplo
              pkgs.yamlfmt
              pkgs.shfmt
            ];
            settings.formatter = {
              toml = {
                command = "taplo";
                options = [ "format" ];
                includes = [ "*.toml" ];
              };
              yaml = {
                command = "yamlfmt";
                includes = [
                  "*.yaml"
                  "*.yml"
                ];
              };
              shell = {
                command = "shfmt";
                options = [
                  "-w"
                  "-i"
                  "2"
                  "-ci"
                ];
                includes = [
                  "*.sh"
                  "*.bash"
                ];
              };
            };
          };

          # ── 质量检查 ──────────────────────────────────────
          checks =
            let
              toolchain = pkgs.fenix.stable.withComponents [
                "cargo"
                "clippy"
                "rust-src"
                "rustc"
                "rustfmt"
              ];
            in
            {
              clippy =
                pkgs.runCommand "check-clippy"
                  {
                    nativeBuildInputs = [
                      toolchain
                      pkgs.cargo-nextest
                      pkgs.pkg-config
                      pkgs.openssl
                    ];
                    RUST_SRC_PATH = "${toolchain}/lib/rustlib/src/rust/library";
                    LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath [
                      pkgs.pkg-config
                      pkgs.openssl
                    ];
                  }
                  ''
                    cp -r ${./.} . && chmod -R u+w .
                    cargo clippy -- -D warnings
                    touch $out
                  '';

              fmt =
                pkgs.runCommand "check-fmt"
                  {
                    nativeBuildInputs = [ toolchain ];
                  }
                  ''
                    cp -r ${./.} . && chmod -R u+w .
                    cargo fmt --check
                    touch $out
                  '';

              tests =
                pkgs.runCommand "check-tests"
                  {
                    nativeBuildInputs = [
                      toolchain
                      pkgs.cargo-nextest
                      pkgs.pkg-config
                      pkgs.openssl
                    ];
                    RUST_SRC_PATH = "${toolchain}/lib/rustlib/src/rust/library";
                    LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath [
                      pkgs.pkg-config
                      pkgs.openssl
                    ];
                  }
                  ''
                    cp -r ${./.} . && chmod -R u+w .
                    cargo nextest run --workspace
                    touch $out
                  '';

              typos =
                pkgs.runCommand "check-typos"
                  {
                    nativeBuildInputs = [ pkgs.typos ];
                  }
                  ''
                    cp -r ${./.} . && chmod -R u+w .
                    typos
                    touch $out
                  '';

              nixfmt =
                pkgs.runCommand "check-nixfmt"
                  {
                    nativeBuildInputs = [ pkgs.nixfmt-rs ];
                  }
                  ''
                    cp -r ${./.} . && chmod -R u+w .
                    find . -name '*.nix' \
                      -not -path './target/*' \
                      -not -path './.git/*' \
                      -exec nixfmt --check {} +
                    touch $out
                  '';
            };

          # ── 开发环境 ──────────────────────────────────────
          devShells.default = pkgs.mkShell {
            name = "torvox-dev";
            packages = [
              # Rust
              (pkgs.fenix.stable.withComponents [
                "cargo"
                "clippy"
                "rust-src"
                "rustc"
                "rustfmt"
              ])
              pkgs.cargo-nextest
              pkgs.cargo-fuzz
              pkgs.cargo-geiger
              pkgs.cargo-audit
              pkgs.cargo-ndk
              pkgs.cargo-deny
              pkgs.cargo-machete
              pkgs.rust-analyzer

              # Android
              pkgs.kotlin
              pkgs.gradle_9
              pkgs.ktfmt
              pkgs.ktlint
              pkgs.android-tools

              # 代码质量
              pkgs.nushell
              pkgs.taplo
              pkgs.yamlfmt
              pkgs.shfmt
              pkgs.typos
              pkgs.nodePackages.markdownlint-cli

              # 原生依赖
              pkgs.pkg-config
              pkgs.openssl
            ];
            env = {
              LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath [
                pkgs.pkg-config
                pkgs.openssl
              ];
            };
          };
        };
    };
}
