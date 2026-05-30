# Torvox 大迁移计划

> 日期: 2026-05-30
> 目标: 执行所有已决策的依赖迁移，解决已知问题

## 一、迁移清单

| # | 迁移 | 从 | 到 | 状态 | 复杂度 |
|---|------|----|----|------|--------|
| 1 | VT解析器 | vte 0.15 | Ghostty VT | 待迁移 | 高 |
| 2 | 通道库 | crossbeam 0.8 | flume | 待迁移 | 中 |
| 3 | 序列化 | postcard 1.1 | repr(C)+POD | 待迁移 | 高 |
| 4 | Rust-Kotlin绑定 | UniFFI 0.31 | BoltFFI | 待迁移 | 高 |
| 5 | 图集打包 | etagere 0.3 | guillotière 0.7 | ✅已完成 | — |

## 二、迁移顺序

### 阶段 1: 基础依赖迁移 (低风险)
1. **crossbeam → flume** — 通道库迁移
   - 影响范围: torvox-terminal/src/session.rs
   - 风险: 低 (API 相似)
   - 验证: cargo test --workspace

### 阶段 2: 序列化迁移 (中风险)
2. **postcard → repr(C)+POD** — 序列化格式迁移
   - 影响范围: torvox-core/src/*.rs (所有类型)
   - 风险: 中 (需要手动实现序列化)
   - 验证: cargo test --workspace

### 阶段 3: VT解析器迁移 (高风险)
3. **vte → Ghostty VT** — VT解析器迁移
   - 影响范围: torvox-terminal/src/parser.rs, terminal.rs
   - 风险: 高 (C/Zig FFI，API 完全不同)
   - 验证: cargo test --workspace

### 阶段 4: 绑定迁移 (高风险)
4. **UniFFI → BoltFFI** — Rust-Kotlin绑定迁移
   - 影响范围: torvox-gui-android/src/bridge.rs, uniffi.toml
   - 风险: 高 (生态系统较小)
   - 验证: Android 构建 + 测试

## 三、每个迁移的规范

### 3.1 crossbeam → flume

**目标**: 将 torvox-terminal/src/session.rs 中的 crossbeam bounded channel 替换为 flume bounded channel

**规范**:
- 使用 `flume::bounded(64)` 替代 `crossbeam::channel::bounded(64)`
- API 基本相同: `send()`, `recv()`, `try_recv()`
- 移除 crossbeam 依赖，添加 flume 依赖
- 更新 Cargo.toml

**验收标准**:
- cargo test --workspace 通过
- cargo clippy -- -D warnings 通过
- 所有 session 相关测试通过

### 3.2 postcard → repr(C)+POD

**目标**: 将 torvox-core 中的所有类型从 postcard 序列化迁移到 repr(C)+POD 手动序列化

**规范**:
- 为所有需要序列化的类型添加 `#[repr(C)]`
- 手动实现 `Serialize` 和 `Deserialize` trait
- 使用固定大小数组替代 Vec (对于需要序列化的类型)
- 添加版本号字段用于前向/后向兼容

**验收标准**:
- cargo test --workspace 通过
- 所有序列化往返测试通过
- no_std 编译通过

### 3.3 vte → Ghostty VT

**目标**: 将 torvox-terminal 中的 vte 解析器替换为 Ghostty VT

**规范**:
- 使用 libghostty-vt crate 替代 vte crate
- 更新 parser.rs 和 terminal.rs 中的 API 调用
- 保持 Perform trait 的语义不变
- 验证 Android 交叉编译

**验收标准**:
- cargo test --workspace 通过
- Android 构建成功
- VT 解析功能正常

### 3.4 UniFFI → BoltFFI

**目标**: 将 torvox-gui-android 中的 UniFFI 绑定替换为 BoltFFI

**规范**:
- 使用 boltffi crate 替代 uniffi crate
- 更新 bridge.rs 中的导出宏
- 更新 uniffi.toml 为 boltffi 配置
- 重新生成 Kotlin 绑定

**验收标准**:
- Android 构建成功
- Kotlin 绑定生成正确
- UniFFI 桥接功能正常

## 四、其他问题修复

| # | 问题 | 严重度 | 修复方案 |
|---|------|--------|----------|
| 1 | PTY↔渲染管线已连接 | ✅已修复 | surface.rs 已集成 Session |
| 2 | 无渲染循环 | 高 | 实现 Rust 专用线程渲染循环 |
| 3 | 选择文本提取是占位符 | 中 | 需要 UniFFI 桥接支持 |
| 4 | cursor.wgsl 不存在 | 中 | 创建光标着色器 |
| 5 | CI配置问题 | 低 | 修复 nightly.yml fuzz targets |

## 五、执行计划

### 步骤 1: 阶段 1 - crossbeam → flume
1. 更新 Cargo.toml: 移除 crossbeam，添加 flume
2. 更新 torvox-terminal/Cargo.toml
3. 更新 torvox-terminal/src/session.rs
4. 运行测试验证
5. 提交

### 步骤 2: 阶段 2 - postcard → repr(C)+POD
1. 为所有类型添加 #[repr(C)]
2. 手动实现序列化逻辑
3. 更新 Cargo.toml: 移除 postcard
4. 运行测试验证
5. 提交

### 步骤 3: 阶段 3 - vte → Ghostty VT
1. 更新 Cargo.toml: 移除 vte，添加 libghostty-vt
2. 更新 parser.rs 和 terminal.rs
3. 运行测试验证
4. Android 构建验证
5. 提交

### 步骤 4: 阶段 4 - UniFFI → BoltFFI
1. 更新 Cargo.toml: 移除 uniffi，添加 boltffi
2. 更新 bridge.rs
3. 重新生成 Kotlin 绑定
4. Android 构建验证
5. 提交

### 步骤 5: 其他问题修复
1. 实现渲染循环
2. 创建 cursor.wgsl
3. 修复 CI 配置
4. 运行完整测试
5. 提交

## 六、风险评估

| 迁移 | 风险 | 缓解措施 |
|------|------|----------|
| crossbeam → flume | 低 | API 相似，测试覆盖充分 |
| postcard → repr(C)+POD | 中 | 手动实现序列化，需要仔细测试 |
| vte → Ghostty VT | 高 | C/Zig FFI，需要验证 Android 兼容性 |
| UniFFI → BoltFFI | 高 | 生态系统较小，可能不够成熟 |

## 七、时间估计

| 阶段 | 时间 |
|------|------|
| 阶段 1: crossbeam → flume | 1-2 小时 |
| 阶段 2: postcard → repr(C)+POD | 2-4 小时 |
| 阶段 3: vte → Ghostty VT | 4-8 小时 |
| 阶段 4: UniFFI → BoltFFI | 4-8 小时 |
| 其他问题修复 | 2-4 小时 |
| **总计** | **12-22 小时** |
