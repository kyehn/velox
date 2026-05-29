{
  inputs = {
    nixpkgs.url = "github:NixOS/nixpkgs/nixpkgs-unstable";
    flake-parts = {
      url = "github:hercules-ci/flake-parts";
      inputs.nixpkgs-lib.follows = "nixpkgs";
    };
    fenix = {
      url = "github:nix-community/fenix";
      inputs.nixpkgs.follows = "nixpkgs";
    };
  };

  outputs =
    {
      nixpkgs,
      fenix,
      flake-parts,
      ...
    }@inputs:
    flake-parts.lib.mkFlake { inherit inputs; } {
      systems = builtins.filter (system: system != "x86_64-darwin") nixpkgs.lib.systems.flakeExposed;

      perSystem =
        {
          pkgs,
          system,
          ...
        }:
        let
          rustToolchain = pkgs.fenix.stable.withComponents [
            "cargo"
            "clippy"
            "rust-src"
            "rustc"
            "rustfmt"
          ];
          rustToolchainAndroid = pkgs.fenix.combine [
            pkgs.fenix.targets.aarch64-linux-android.stable.rust-std
            pkgs.fenix.targets.x86_64-linux-android.stable.rust-std
          ];
          androidSdkPkgs = pkgs.androidenv.composeAndroidPackages.override { licenseAccepted = true; } {
            buildToolsVersions = [ "36.0.0" ];
            cmdLineToolsVersion = "16.0";
            platformToolsVersion = "36.0.0";
            platformVersions = [
              "36"
              "33"
            ];
            ndkVersions = [ "29.0.14206865" ];
            includeNDK = true;
            includeEmulator = false;
            includeSystemImages = false;
            includeSources = false;
          };
          androidEmuSdkPkgs = pkgs.androidenv.composeAndroidPackages.override { licenseAccepted = true; } {
            buildToolsVersions = [ "36.0.0" ];
            cmdLineToolsVersion = "16.0";
            platformToolsVersion = "36.0.0";
            platformVersions = [
              "36"
              "33"
            ];
            ndkVersions = [ "29.0.14206865" ];
            includeNDK = true;
            includeEmulator = true;
            includeSystemImages = true;
            systemImageTypes = [ "default" ];
            abiVersions = [ "x86_64" ];
            includeSources = false;
          };
          jdk = pkgs.javaPackages.compiler.temurin-bin.jdk-25;
          nativeDeps = with pkgs; [
            pkg-config
            openssl
          ];
          rustDeps = [
            rustToolchainAndroid
            pkgs.cargo-nextest
            pkgs.cargo-fuzz
            pkgs.cargo-geiger
            pkgs.cargo-audit
            pkgs.cargo-ndk
            pkgs.rust-analyzer
          ];
          androidDeps = with pkgs; [
            jdk
            kotlin
            gradle_9
            ktfmt
            ktlint
            android-tools
            androidSdkPkgs.androidsdk
          ];
          lintDeps = with pkgs; [
            nushell
            taplo
            yamlfmt
            shfmt
            typos
            nodePackages.markdownlint-cli
          ];
        in
        {
          _module.args.pkgs = import nixpkgs {
            inherit system;
            config = {
              allowUnfree = true;
              allowAliases = false;
              warnUndeclaredOptions = true;
            };
            overlays = [
              fenix.overlays.default
            ];
          };

          # 多语言格式化器
          # 使用: nix fmt (格式化) 或 nix fmt -- --check (检查)
          formatter = pkgs.nixfmt-tree.override {
            nixfmtPackage = pkgs.nixfmt-rs;
            runtimeInputs = with pkgs; [
              taplo
              yamlfmt
              shfmt
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
              rust = {
                command = "rustfmt";
                options = [
                  "--config"
                  "skip_children=true"
                  "--edition"
                  "2024"
                  "--style-edition"
                  "2024"
                ];
                includes = [ "*.rs" ];
              };
            };
          };

          # 质量检查 (nix flake check)
          checks =
            let
              clippyCheck =
                pkgs.runCommand "clippy"
                  {
                    nativeBuildInputs = [
                      rustToolchain
                      pkgs.cargo-nextest
                      nativeDeps
                    ];
                    RUST_SRC_PATH = "${rustToolchain}/lib/rustlib/src/rust/library";
                    LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath nativeDeps;
                  }
                  ''
                    cd ${../.}
                    cargo clippy -- -D warnings
                    touch $out
                  '';

              fmtCheck =
                pkgs.runCommand "fmt"
                  {
                    nativeBuildInputs = [ rustToolchain ];
                  }
                  ''
                    cd ${../.}
                    cargo fmt --check
                    touch $out
                  '';

              testCheck =
                pkgs.runCommand "tests"
                  {
                    nativeBuildInputs = [
                      rustToolchain
                      pkgs.cargo-nextest
                      nativeDeps
                    ];
                    RUST_SRC_PATH = "${rustToolchain}/lib/rustlib/src/rust/library";
                    LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath nativeDeps;
                  }
                  ''
                    cd ${../.}
                    cargo nextest run --workspace
                    touch $out
                  '';

              typosCheck =
                pkgs.runCommand "typos"
                  {
                    nativeBuildInputs = [ pkgs.typos ];
                  }
                  ''
                    cd ${../.}
                    typos
                    touch $out
                  '';

              nixfmtCheck =
                pkgs.runCommand "nixfmt"
                  {
                    nativeBuildInputs = [ pkgs.nixfmt-rs ];
                  }
                  ''
                    cd ${../.}
                    find . -name "*.nix" -not -path "./target/*" -not -path "./.git/*" \
                      -exec nixfmt --check {} +
                    touch $out
                  '';
            in
            {
              inherit
                clippyCheck
                fmtCheck
                testCheck
                typosCheck
                nixfmtCheck
                ;
            };

          devShells = {
            default = pkgs.mkShell {
              name = "torvox-dev";
              packages = nativeDeps ++ rustDeps ++ androidDeps ++ lintDeps;
              env = {
                LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath nativeDeps;
                RUST_SRC_PATH = "${rustToolchain}/lib/rustlib/src/rust/library";
                ANDROID_HOME = "${androidSdkPkgs.androidsdk}/libexec/android-sdk";
                ANDROID_SDK_ROOT = "${androidSdkPkgs.androidsdk}/libexec/android-sdk";
                ANDROID_NDK_ROOT = "${androidSdkPkgs.androidsdk}/libexec/android-sdk/ndk/29.0.14206865";
                JAVA_HOME = jdk;
              };
              shellHook = ''
                echo "=== Torvox Dev Shell ==="
                echo "Rust: $(rustc --version)"
                echo "Cargo: $(cargo --version)"
                echo "Nextest: $(cargo nextest --version 2>/dev/null || echo N/A)"
                echo "Kotlin: $(kotlin -version 2>&1 | head -1 || echo N/A)"
                echo "Gradle: $(gradle --version 2>/dev/null | grep '^Gradle' || echo N/A)"
                echo "JDK: $(java -version 2>&1 | head -1)"
                echo "Nushell: $(nu --version 2>/dev/null || echo N/A)"
                echo "Typos: $(typos --version 2>/dev/null || echo N/A)"
                echo "ANDROID_HOME: $ANDROID_HOME"
                echo ""
                echo "格式化: nix fmt"
                echo "检查: nix flake check"
                echo "构建: cargo build --workspace"
                echo "测试: cargo nextest run --workspace"
              '';
            };
            emulator = pkgs.mkShell {
              name = "torvox-emulator";
              packages =
                nativeDeps
                ++ rustDeps
                ++ androidDeps
                ++ lintDeps
                ++ [
                  androidEmuSdkPkgs.androidsdk
                  pkgs.qemu
                ];
              env = {
                LD_LIBRARY_PATH = pkgs.lib.makeLibraryPath nativeDeps;
                RUST_SRC_PATH = "${rustToolchain}/lib/rustlib/src/rust/library";
                ANDROID_HOME = "${androidEmuSdkPkgs.androidsdk}/libexec/android-sdk";
                ANDROID_SDK_ROOT = "${androidEmuSdkPkgs.androidsdk}/libexec/android-sdk";
                ANDROID_NDK_ROOT = "${androidEmuSdkPkgs.androidsdk}/libexec/android-sdk/ndk/29.0.14206865";
                JAVA_HOME = jdk;
              };
              shellHook = ''
                echo "=== Torvox Emulator Shell ==="
                echo "Emulator SDK: $ANDROID_HOME"
                echo "Run: emulator -avd torvox_api36 -no-window -no-boot-anim -noaudio"
                echo "Setup AVD: avdmanager create avd -n torvox_api36 -k 'system-images;android-36;default;x86_64' -d pixel_7_pro"
              '';
            };
          };
        };
    };
}
