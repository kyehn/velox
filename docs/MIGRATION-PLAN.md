# Torvox 大迁移计划

> 日期: 2026-05-30
> 状态: 5/5 全部完成

## 一、迁移清单

| # | 迁移 | 从 | 到 | 状态 | 复杂度 |
|---|------|----|----|------|--------|
| 1 | VT解析器 | vte 0.15 | libghostty-vt 0.1 | ✅ 完成 | 高 |
| 2 | 通道库 | crossbeam 0.8 | flume 0.11 | ✅ 完成 | 低 |
| 3 | 序列化 | postcard 1.1 | 已移除 | ✅ 完成 | 低 |
| 4 | 图集打包 | etagere 0.3 | guillotière 0.7 | ✅ 完成 | 低 |
| 5 | Rust-Kotlin绑定 | UniFFI 0.31 | boltffi 0.25 | ✅ 完成 | 高 |

## 二、已完成迁移详情

### 2.1 vte → libghostty-vt ✅

**变更范围**:
- `torvox-terminal/Cargo.toml`: vte → libghostty-vt
- `torvox-terminal/src/parser.rs`: 完全重写，使用 Ghostty VT Terminal
- `torvox-terminal/src/terminal.rs`: 完全重写，使用 Ghostty VT Terminal + RenderState
- `torvox-terminal/src/session.rs`: 更新为使用 TerminalState.process_bytes()
- `torvox-renderer/src/gpu.rs`: 新增 FlatGrid + build_cell_instances_from_flat
- `torvox-gui-android/src/surface.rs`: 使用 FlatGrid + Ghostty VT RenderState
- `torvox-fuzz/fuzz/fuzz_targets/`: 更新 fuzz targets
- `torvox-integration-tests/src/lib.rs`: 更新集成测试
- `flake.nix`: 添加 zig_0_15 构建依赖

**关键设计决策**:
- TerminalState 包装 Ghostty VT Terminal + RenderState
- 使用 raw pointer 解决 !Send + !Sync 的 borrow 检查问题
- FlatGrid 作为 Ghostty VT → GPU 渲染器的桥梁
- unsafe impl Send/Sync for AndroidSurface (保证 UI 线程访问)

### 2.2 crossbeam → flume ✅

**变更范围**:
- `torvox-terminal/src/session.rs`: flume::{bounded, Receiver}

### 2.3 postcard → 已移除 ✅

**变更范围**:
- `Cargo.toml`: 从 workspace dependencies 移除 postcard
- `torvox-core/Cargo.toml`: 移除 `[dev-dependencies]` section
- `torvox-core/src/`: 移除所有 postcard roundtrip 测试 (14 个测试函数)
- 类型保留 `serde::Serialize`/`Deserialize` derive，无需序列化库

### 2.4 etagere → guillotière ✅

**变更范围**:
- `torvox-renderer/src/font.rs`: guillotiere::AtlasAllocator

## 三、已完成迁移详情 — UniFFI → boltffi

**变更范围**:
- `torvox-gui-android/Cargo.toml`: uniffi → boltffi 0.25
- `torvox-gui-android/src/bridge.rs`: `#[uniffi::export]` → `#[boltffi::export]`, `uniffi::Record`/`uniffi::Enum` → `#[data]`, `uniffi::Error` → `#[error]`
- `torvox-gui-android/uniffi.toml`: 已移除 (boltffi 不需要配置文件)
- `setup_scaffolding!()`: 已移除 (boltffi 不需要)
- `uniffi-bindgen generate` → `boltffi pack android`

**关键差异**:
- boltffi 使用注解宏 (`#[data]`/`#[error]`/`#[boltffi::export]`) 而非 derive 宏
- boltffi 不需要 `uniffi.toml` 配置文件
- boltffi 不需要 `setup_scaffolding!()` 宏
- boltffi 使用 `boltffi pack android` 命令生成 Kotlin 绑定
