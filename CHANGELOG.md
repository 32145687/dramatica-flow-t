# Dramatica Flow Android - 变更日志

## v1.0.0 - 初始版本
- 项目解压和基础配置
- 添加INTERNET权限
- 创建应用启动图标

## v1.1.0 - 创作流程重构
- 重构7步创作流程架构
- 添加DramaticaStep枚举和状态机
- 实现自动跳转（世界观→角色→大纲→章节）
- 添加进度条显示（stepProgress/stepLoadingMessage）
- 实现防重复生成锁（_isGenerating）

## v1.2.0 - AI集成
- 添加AiRepository（支持MiMo/DeepSeek双模型）
- 实现真实AI调用（替代硬编码文本）
- 添加MiMo后处理（postProcessForMimo）
- 添加写后验证（PostWriteValidator）
- API设置界面（URL/Key/模型配置+测试）

## v1.3.0 - 界面优化
- 重写7个流程步骤界面
- 修复章节创作界面（章节号/标题可见、字数目标选择）
- 优化进度指示器（圆点+连接线+状态文字）
- 添加MiMo质量检查面板（后改为自动修复）

## v1.4.0 - Bug修复
- 修复HTTP连接泄漏（添加conn.disconnect()）
- 修复_isGenerating卡死（添加try-catch-finally）
- 修复JSON注入（用JSONObject构建请求体）
- 修复JSON解析截断（用org.json替代正则）
- 修复章节导航偏移（移除+1/-1）
- 修复无readTimeout（添加15秒超时）
- 修复!!强制解包（改为?. ?: ""）
- 修复completedSteps重组重建（用remember缓存）
- 修复FlowScreen步骤无法点击
- 修复创建新作品FilterChip溢出
- 修复章节号/标题不显示
- 修复进度不保存（selectBook时加载loadProject）
- 修复各界面数据不同步（生成时同步写入数据库表）
