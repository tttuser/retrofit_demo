# Retrofit 两周强化学习每日计划（14 天 × 每天 12 小时）
日期：2026-01-27  
学习者：tttuser  
目标：**熟悉 Retrofit 原理与架构**（严格映射到 L1–L5 分层节点）

> 学习产出建议：建一个练习工程 `retrofit-lab`（Kotlin + JUnit + OkHttp MockWebServer）。  
> 每天结束必须提交：`README-YYYYMMDD.md`（当天结论 + 截图/日志 + 代码链接）+ 当天单测全绿。  
> 所有示例尽量用 **MockWebServer** 可重复验证；涉及 HTTPS 用本地证书或抓包工具复现。

---

## 通用每日节奏（12h 模板）
- **1h** 预热：读当天目标与前置依赖，列“验收标准”
- **4h** 代码示例 A：实现 + 单测（MockWebServer）
- **2h** 讲解与复盘：把代码对应到分层节点（写到 README）
- **3h** 代码示例 B：扩展/对照实验（修改一个变量验证差异）
- **1h** Debug/抓包：用日志、抓包或断点证明结论
- **1h** 总结：产出“可复现结论清单”+ 明天预习问题

---

# Week 1：把 L1 + L2 打牢（能用、用得稳）

## Day 1（12h）——从 0 到能发起请求（L1-1、L1-7）
**目标**：能写 Service 接口并成功调用；理解 Retrofit/Service 实例复用边界。  
**代码示例 A（核心）**：`HelloRetrofit`
- 建一个最小 Retrofit：baseUrl、OkHttpClient、一个 Service
- Service 方法覆盖：`GET + Query`、`GET + Path`
- 用 MockWebServer：
  - 断言请求路径、Query 拼接、Header 默认值
  - 断言响应能解析成 data class
**代码示例 B（对照）**：`CreateTwiceBenchmark`
- 同一 Retrofit `create()` 两次 vs 复用一个 Service
- 记录首次调用与后续调用耗时（简单计时即可）
**验收**：
- 能用单测证明：URL、方法、header 正确（L1-1）
- 能解释：Retrofit 与 Service 是否应复用以及原因（L1-7）

---

## Day 2（12h）——请求体三件套：JSON/表单/Multipart（L1-2）
**代码示例 A**：`JsonBodyPost`
- `POST + @Body`（使用你项目里的 JSON 序列化器，比如 Moshi/Gson/Kotlinx 任意选一种）
- MockWebServer 校验 Content-Type 与 body JSON 字符串
**代码示例 B**：`FormAndMultipart`
- `@FormUrlEncoded + @Field`：登录接口
- `@Multipart + @Part`：上传文本 part + 文件 part（用临时文件）
- MockWebServer 断言 boundary、字段名
**验收**：
- 三种请求体都能抓包/日志证明格式正确（L1-2）

---

## Day 3（12h）——返回类型语义：Call、挂起函数、Response 包装（L1-3、L2-8）
**代码示例 A**：`ReturnTypesComparison`
- 同一 endpoint 写三份方法：
  - 返回 `Call<Foo>`
  - `suspend fun` 返回 `Foo`
  - `suspend fun` 返回 `Response<Foo>`
- 分别在成功/404 时记录行为差异（是否抛异常、能否取 code）
**代码示例 B**：`ThreadProof`
- 在主线程上下文（或测试里模拟）调用挂起函数
- 打印线程名：请求发起、响应解析、协程恢复分别在哪
**验收**：
- 一张表写清三种返回方式的行为边界（L1-3）
- 能解释为什么“挂起函数不等于在主线程跑网络”（L2-8）

---

## Day 4（12h）——失败分类可复现：HTTP/网络/解析（L1-4、L4-7 预埋）
**代码示例 A**：`FailureMatrix`
- 场景1：MockWebServer 返回 404 + 错误体
- 场景2：断网模拟（用不可达地址或 MockWebServer shutdown）
- 场景3：返回 200 但 body 非法 JSON
- 对每种场景输出：异常类型、消息、是否可取到 code、是否可取到错误体
**代码示例 B**：`ErrorBodyReader`
- 对 4xx/5xx：读取 errorBody（注意只能读一次），写辅助函数安全读取并记录日志
**验收**：
- 你能“只看日志”就判断属于三类哪一类（L1-4）

---

## Day 5（12h）——取消与超时（L1-5、L2-3）
**代码示例 A**：`CancelPropagation`
- MockWebServer 模拟慢响应（延迟 body）
- 发请求后取消：
  - Call 方式：`call.cancel()`
  - 协程方式：取消 Job
- 观察：OkHttp 是否终止、回调/异常行为
**代码示例 B**：`TimeoutTuning`
- 配置 connect/read/write/call timeout
- 分别触发并记录异常类型与耗时
**验收**：
- 能解释取消是如何传递到 OkHttp 的（L1-5）
- 能针对“慢”选择合适 timeout 参数（L2-3）

---

## Day 6（12h）——拦截器链与日志可观测（L2-1、L2-2）
**代码示例 A**：`InterceptorOrderLab`
- 写 2 个应用拦截器 + 1 个网络拦截器
- 在每个拦截器打印：进入/退出、request url、response code
- 复现重定向或重试时的调用次数差异
**代码示例 B**：`RequestIdAndRedaction`
- 每个请求注入 `X-Request-Id`
- 日志中对 token、手机号等字段脱敏
**验收**：
- 能用日志证明：应用拦截器与网络拦截器差异（L2-1）
- 能在日志串起完整链路（L2-2）

---

## Day 7（12h）——缓存与多环境基址（L2-4、L2-7）
**代码示例 A**：`OkHttpCacheLab`
- 配置磁盘 Cache
- MockWebServer 返回带 Cache-Control 的响应
- 断网/重启 server 后验证缓存命中（包含强缓存与协商缓存）
**代码示例 B**：`BaseUrlSwitch`
- 实现 dev/staging/prod 切换：
  - 方案1：重建 Retrofit
  - 方案2：拦截器改 host（为 Week2 的 L5-4 做铺垫）
- 验证 baseUrl 末尾斜杠规则与报错
**验收**：
- 能在断网下证明缓存策略是否按预期工作（L2-4）
- 能清楚描述多环境切换方案取舍（L2-7）

---

# Week 2：走进 L3 + L4 + L5（调用链、源码点、可定制）

## Day 8（12h）——动态代理与方法解析缓存（L3-1、L3-2、L4-4）
**代码示例 A**：`ProxyIntrospection`
- 打印 service 实例的运行时类型（通常是 Proxy）
- 对方法调用前后打断点（或日志）观察 InvocationHandler
**代码示例 B**：`FirstCallSlowTest`
- 做一个“首次调用 vs 第二次调用”计时实验
- 目标：证明解析与缓存存在（哪怕只是宏观耗时差异）
**验收**：
- 能口述：create 生成代理对象并转发调用（L3-1）
- 能解释“为何首次调用才解析”以及利弊（L3-2、L4-4）

---

## Day 9（12h）——请求构建工厂：把注解变成 Request（L3-3、L4-3）
**代码示例 A**：`RequestShapeSuite`
- 设计一个“最复杂方法”：
  - Path + Query + Header + Body（或 Multipart）
- 用 OkHttp 拦截器打印最终 Request：
  - url、headers、body content-type、body 内容摘要
**代码示例 B**：`AnnotationMisuseCases`
- 故意写错若干接口：
  - 缺少 HTTP 方法注解
  - 表单与 body 冲突
  - Multipart 缺 part
- 记录错误抛出时机与异常信息
**验收**：
- 能从最终 Request 反推注解如何生效（L3-3）
- 能解释常见注解冲突错误（L4-3）

---

## Day 10（12h）——转换器：响应体到对象的边界（L3-4、L4-6）
**代码示例 A**：`ConverterSwapLab`
- 注册两个 Converter（例如：一个只处理 String，一个处理 JSON）
- 调整注册顺序，观察最终解析结果变化（为 L3-8 铺垫）
**代码示例 B**：`BodyCloseProof`
- 自定义 Converter：读取 body 后故意不关闭/或不完全读取
- 观察是否出现资源警告或连接复用异常（不同环境可能表现不同）
- 再写“正确版本”对照
**验收**：
- 能解释 converter 的选择与执行点（L3-4）
- 能说明 ResponseBody 关闭的重要性与正确做法（L4-6）

---

## Day 11（12h）——调用适配器：返回类型如何被适配（L3-5、L3-8、L4-2）
**代码示例 A**：`CallAdapterDetect`
- 写一个自定义 CallAdapterFactory：只做“打印被适配的返回类型”
- 让不同方法返回不同类型，输出匹配过程
**代码示例 B**：`GenericTypeExtractor`
- 写一个小工具，对 `Method.genericReturnType` 做解析：
  - 从 `Call<Foo>`、`Response<Foo>`、`ApiResult<Foo>` 提取 Foo
- 把结果打印出来，作为后续自定义适配器的前置
**验收**：
- 能解释“返回类型不同导致适配器不同”并能观测（L3-5）
- 能自己做泛型提取并说清楚为什么需要它（L4-2）
- 能证明 Factory 注册顺序影响匹配（L3-8）

---

## Day 12（12h）——OkHttpCall 封装与错误边界（L3-6、L3-7、L4-5）
**代码示例 A**：`SyncVsAsyncVsSuspend`
- 同一接口分别走：
  - execute 同步
  - enqueue 异步
  - suspend 挂起
- 打印线程名、耗时、异常/返回差异
**代码示例 B**：`Non2xxBehaviorMatrix`
- 非 2xx：
  - Call<T> 方式：是否进入 onResponse，body 是否为 null
  - suspend 返回 T：是否抛异常
  - suspend 返回 Response<T>：如何拿 code/errorBody
**验收**：
- 能说清 Retrofit 对 OkHttp 的封装层做了什么（L3-6）
- 能说清“非 2xx 到底如何传播”在不同返回形式下的差异（L3-7）
- 能解释线程模型：网络在哪、回调在哪、挂起恢复在哪（L4-5）

---

## Day 13（12h）——统一错误模型 + 自定义适配器（L5-1、L5-3）
**代码示例 A**：`ApiResultDesign`
- 设计：
  - `Success(data, code, headers)`
  - `HttpError(code, errorBody, parsedError)`
  - `NetworkError(ioe)`
  - `ParseError(exception, rawBodySnippet)`
- 写单测覆盖四分支（复用 Day4 的三类失败 + 成功）
**代码示例 B**：`ApiResultCallAdapter`
- 自定义 CallAdapter：让接口直接返回 `ApiResult<T>`
- 要求：调用方不需要 try/catch；非 2xx 也能拿到错误体
**验收**：
- 单测全覆盖且可复现（L5-1）
- 你能解释适配器如何拦截与包装结果（L5-3）

---

## Day 14（12h）——自定义转换器/注解驱动/多实例治理/测试体系（L5-2、L5-4、L5-5、L5-6、L5-7）
> 最后一天做“集成式改造”，把 Week2 的成果串成一个可用网络层。

**代码示例 A（定制组合）**：`CustomHostAndNoAuth`
- 自定义注解（示例命名，避免符号可用 `Host`、`NoAuth`）：
  - `Host("api2")`：走另一个域名
  - `NoAuth`：跳过鉴权 header
- 实现方式：
  - 解析注解（可在 InvocationHandler 侧拿 Method，或在拦截器里借助 tag/请求头传递信息）
  - 在拦截器中根据标记改写 host 或跳过 token
**代码示例 B（转换器与测试体系）**：`DecryptThenParse + ContractTests`
- Converter：先对响应体做“伪解密”（例如 Base64 decode）再 JSON 解析
- 用 MockWebServer 做契约测试：
  - 断言请求格式
  - 回放成功与失败样例
- 性能小实验：大 JSON 响应（比如 2MB）测解析耗时与内存（粗略统计即可）
**验收**：
- 自定义注解确实影响请求（L5-4）
- 多 baseUrl 场景可维护：两个 Retrofit 或共享 OkHttpClient（L5-5）
- 每个 endpoint 都有契约测试（L5-6）
- 至少给出一条性能/稳定改进建议并用数据佐证（L5-7）

---

# 每日“必须交付”的验收清单（建议粘到你工程 README 顶部）
- [ ] 当天对应的 L 节点列表写清楚（例如：L3-4、L4-6）  
- [ ] 至少 2 个可运行代码示例（带单测或可重复脚本）  
- [ ] 至少 1 份“对照实验”日志（改一个变量看差异）  
- [ ] 能用 5–10 句话解释当天最关键的调用链位置（发生在哪一层、为什么）  
- [ ] 次日预习问题 3 条（用问句写出）  

---

## 你需要我补的内容（任选其一，我可以继续细化）
1. 你当前项目使用的序列化库是 **Moshi / Gson / Kotlinx Serialization** 哪个？（我可以把 Day2/Day10 的 Converter 示例写得更贴近你）  
2. 你是偏 Android 还是纯 Kotlin（Ktor/桌面）？（我会调整线程与调试工具安排）  
3. 你希望最终产出是一个“可复用网络层模块”还是“源码阅读笔记为主”？（我会调整 Week2 比重）