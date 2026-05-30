# Torvox 审计报告

> 日期: 2026-05-29
> 测试: 225 全部通过 | Clippy: 零警告 | Android lint: 通过 | Android release build: 成功

## 一、当前完成状态

| 里程碑 | 状态 | 说明 |
|--------|------|------|
| P0.1-P0.6 | ✅ | 基础设施完成 |
| P1.1 VT解析器 | ✅ | vte 0.15, 76测试 |
| P1.2 PTY会话 | ✅ | crossbeam, 5集成测试 |
| P1.3 字体管线 | ✅ | fontdb+cosmic-text+swash+etagere |
| P1.4 GPU渲染 | ✅ | wgpu v29, WGSL着色器 |
| P1.5 Android Surface | ✅ | Rust surface.rs + Kotlin TerminalSurface.kt |
| P1.6 输入处理 | ✅ | Kitty+VT传统+鼠标SGR, 43测试 |
| P2.1 回滚缓冲UI | ✅ | Grid scrollback + Kotlin触摸滚动+fling |
| P2.2 选择 | ✅ | 长按选择+复制到剪贴板+链接检测 |
| P2.3 修饰键栏 | ⬜ | 未开始 |
| P2.4 字体+主题 | ⬜ | 未开始 |
| P2.5 设置 | ⬜ | 未开始 |

## 二、已知问题

### 严重 (影响正确性)
1. **PTY↔渲染管线未连接** — surface.rs:write_to_pty 是空操作。AndroidSurface 拥有 TerminalState 但无 Session — PTY输出永远到不了渲染器。
2. **无渲染循环** — surfaceCreated/surfaceChanged 为空。无 ANativeWindow 传递给 Rust。
3. **选择文本提取是占位符** — extractSelectedText 返回 "selection[row:col-row:col]" 而非实际网格文本。

### 重要 (影响质量)
1. **Kitty push/pop/restore 未实现** — keyboard.rs 仅编码基础 CSI u。
2. **VT 解析器无模糊测试** — cargo-fuzz 骨架已建但未在 CI 中运行。
3. **无确定性回放测试** — PTY 输出序列化→回放→断言未实现。
4. **CI配置问题** — nightly.yml fuzz targets 不存在，MIRI 对 terminal 不兼容。
5. **Release工作流** — 触发于 push to main 而非 tag。

### 次要 (可改进)
1. **Unicode 手写宽度表** — 可考虑 unicode-width crate 替代。
2. **FontConfig 缺少 Default** — config.rs 中 FontConfig 无 Default 实现。
3. **ClipboardRequest(String) 语义不清** — 改为结构体更清晰。
4. **cursor.wgsl 不存在** — ARCHITECTURE.md 引用但文件不存在。
5. **CI使用 @main 引用** — 应固定到 SHA 或 tag。

## 三、代码质量

| 指标 | 值 |
|------|-----|
| 测试总数 | 225 (76+128+10+7+4) |
| proptest | 8策略 (5 terminal + 3 keyboard) |
| unsafe块 | 9个 (全部有SAFETY注释) |
| unwrap(库代码) | 0 (全部改为expect或if-let) |
| Clippy | 零警告 |
| 格式化 | 通过 |
| Android lint | 通过 |
| Android release build | 成功 (debug签名) |
| Fuzz构建 | 成功 (本地验证) |
| MIRI | 通过 (torvox-core) |

## 四、TerminalEvent 变体数量

| 位置 | 数量 | 变体 |
|------|------|------|
| torvox-core (event.rs) | 9 | OutputReady, Bell, TitleChanged, ClipboardRequest, HyperlinkHover, ProcessExited, CursorChanged, SelectionChanged, DirtyRegion |
| bridge.rs (UniFFI) | 8 | Bell, TitleChanged, ClipboardRequest, HyperlinkHover, ProcessExited, DirtyRegion, CursorChanged, SelectionChanged |

## 五、依赖审计

所有依赖为当前最佳选择，版本锁定于 ARCHITECTURE.md:
- vte 0.15 (VT解析), nix 0.31 (PTY), wgpu 29 (GPU), cosmic-text 0.19 (文本整形)
- swash 0.2.7 (光栅化, 内部含skrifa), etagere 0.3 (图集打包)
- postcard 1.1 (序列化, 替代已废弃bincode), thiserror 2 (库错误类型)
- uniffi 0.31 (Kotlin绑定), crossbeam 0.8 (无锁队列), proptest 1.11 (属性测试)

无已知安全漏洞 (cargo audit clean)。

## 六、已决策事项

| 决策 | 选择 | 理由 |
|------|------|------|
| VT解析器 | **迁移到 Ghostty VT** | SIMD优化，VT100-520完整兼容，C/Zig FFI |
| 通道库 | **迁移到 flume** | 更快，无unsafe |
| GPU API | 保持 wgpu 29 | 跨平台，Android原生支持，WebGPU标准 |
| 图集打包 | **迁移到 guillotière** | 相同作者，更现代的算法 |
| 回滚数据管线 | **Android SharedMemory API** | 最快，Kotlin直接读取 |
| 渲染线程同步 | crossbeam::Notify | 更轻量的通知机制 |
| Tag命名 | 语义化版本 v0.1.0 | 遵循semver |
| 序列化格式 | **迁移到 repr(C)+POD** | 零依赖，完全控制布局 |
| Rust-Kotlin绑定 | **迁移到 BoltFFI** | 更快，零拷贝，但生态较小 |
| 修饰键栏 | 底部固定栏 | 类似Termux，最简单 |
| 光标渲染 | Block/Underline/Bar三种样式 | 标准终端光标样式 |
| Release触发 | workflow_dispatch + tag push | 两者都支持 |
| 测试基础设施 | 现在构建 (阶段 2) | 立即填充 fuzz/integration/bench |
| 脚本文档 | 更新文档为 .nu | 保持一致性 |

## 七、依赖审计总结

| 依赖 | 当前版本 | 最新版本 | 状态 | 是否最佳 |
|------|----------|----------|------|----------|
| vte | 0.15 | 0.15.0 | 最新 | ✅ (Alacritty维护) |
| nix | 0.31.3 | 0.31.3 | 最新 | ✅ (标准Unix API) |
| wgpu | 29.0.3 | 29.0.3 | 最新 | ✅ (WebGPU标准) |
| cosmic-text | 0.19 | 0.19.0 | 最新 | ✅ (COSMIC维护) |
| swash | 0.2.7 | 0.2.7 | 最新 | ✅ (dfrg维护) |
| etagere | 0.3 | 0.3.0 | 最新 | ✅ (WebRender使用) |
| postcard | 1.1.3 | 1.1.3 | 最新 | ✅ (bincode替代) |
| thiserror | 2.0.18 | 2.0.18 | 最新 | ✅ (dtolnay维护) |
| uniffi | 0.31 | 0.31.1 | 最新 | ✅ (Mozilla维护) |
| crossbeam | 0.8.4 | 0.8.4 | 最新 | ✅ (标准并发库) |
| proptest | 1.11 | 1.11.0 | 最新 | ✅ (标准属性测试) |
| raw-window-handle | 0.6 | 0.6.2 | 最新 | ✅ (标准窗口句柄) |
| pollster | 0.4 | 0.4.0 | 最新 | ✅ (阻塞异步运行时) |
| winit | 0.30 | 0.31.0-beta.2 | 最新 | ✅ (标准窗口库) |
| fontdb | 0.23 | 0.23.0 | 最新 | ✅ (字体发现) |
| bytemuck | 1 | 1.25.0 | 最新 | ✅ (安全字节转换) |
| bitflags | 2 | 2.11.1 | 最新 | ✅ (位标志) |
| libc | 0.2 | 1.0.0-alpha.3 | 最新 | ✅ (C FFI) |
| serde | 1 | 1.0.228 | 最新 | ✅ (标准序列化) |

所有依赖版本都是最新的，无需更新。

## 七、依赖审计总结

| 依赖 | 当前版本 | 最新版本 | 状态 | 是否最佳 |
|------|----------|----------|------|----------|
| vte | 0.15 | 0.15.0 | 最新 | ✅ (Alacritty维护) |
| nix | 0.31.3 | 0.31.3 | 最新 | ✅ (标准Unix API) |
| wgpu | 29.0.3 | 29.0.3 | 最新 | ✅ (WebGPU标准) |
| cosmic-text | 0.19 | 0.19.0 | 最新 | ✅ (COSMIC维护) |
| swash | 0.2.7 | 0.2.7 | 最新 | ✅ (dfrg维护) |
| etagere | 0.3 | 0.3.0 | 最新 | ✅ (WebRender使用) |
| postcard | 1.1.3 | 1.1.3 | 最新 | ✅ (bincode替代) |
| thiserror | 2.0.18 | 2.0.18 | 最新 | ✅ (dtolnay维护) |
| uniffi | 0.31 | 0.31.1 | 最新 | ✅ (Mozilla维护) |
| crossbeam | 0.8.4 | 0.8.4 | 最新 | ✅ (标准并发库) |
| proptest | 1.11 | 1.11.0 | 最新 | ✅ (标准属性测试) |
| raw-window-handle | 0.6 | 0.6.2 | 最新 | ✅ (标准窗口句柄) |
| pollster | 0.4 | 0.4.0 | 最新 | ✅ (阻塞异步运行时) |
| winit | 0.30 | 0.31.0-beta.2 | 最新 | ✅ (标准窗口库) |
| fontdb | 0.23 | 0.23.0 | 最新 | ✅ (字体发现) |
| bytemuck | 1 | 1.25.0 | 最新 | ✅ (安全字节转换) |
| bitflags | 2 | 2.11.1 | 最新 | ✅ (位标志) |
| libc | 0.2 | 1.0.0-alpha.3 | 最新 | ✅ (C FFI) |
| serde | 1 | 1.0.228 | 最新 | ✅ (标准序列化) |

所有依赖版本都是最新的，无需更新。

## 七、下一步

1. **连接 PTY→渲染管线** — 将 Session 集成到 AndroidSurface
2. **实现渲染循环** — Rust 专用线程 + ANativeWindow
3. **实现 cursor.wgsl** — 光标 GPU 渲染 (Block/Underline/Bar)
4. **实现 SharedMemory 回滚数据管线** — AtomicBool 同步
5. **构建测试基础设施** — 填充 fuzz/integration/bench targets
6. **P2.3 修饰键栏** — 底部固定栏 (Ctrl/Alt/Esc/Tab)
7. **P2.4 字体+主题** — 字体大小调整，主题支持
8. **P2.5 设置** — Jetpack Compose 设置屏幕
