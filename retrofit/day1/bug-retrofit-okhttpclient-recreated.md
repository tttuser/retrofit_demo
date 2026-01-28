# BUG 报告：每次请求重复创建 Retrofit/OkHttpClient 导致连接无法复用、服务器连接压力暴涨

## 背景
项目使用 Retrofit + OkHttp 访问后端 API。正常情况下应复用同一个 `OkHttpClient`（连接池/线程池等）以及按 `baseUrl` 复用 `Retrofit`/`Service`，每次请求只创建一次性的 `Call`（或协程调用）。

## 问题描述
代码存在缺陷：在每次发起网络请求时，都会新建 `Retrofit`，且未显式设置共享的 `OkHttpClient`，从而导致 Retrofit 在 `build()` 时隐式创建新的默认 `OkHttpClient`。

结果是：**每次请求都会产生一个新的 OkHttpClient 实例**（以及新的连接池/调度器等），连接无法复用，导致客户端与服务器侧连接相关压力异常升高。

## 影响范围 / 风险
- **连接池无法复用**：keep-alive/HTTP2 复用效果丢失
- **TCP 连接数暴涨**：每个请求更倾向于新建连接
- **TLS 握手次数暴涨**（HTTPS 场景）：CPU 开销显著增加
- **TIME_WAIT 堆积**：客户端本机 ephemeral port 与系统资源压力增大
- **NAT/SLB/网关连接表压力**：可能触发限流或连接耗尽
- **服务端 accept/握手/加解密开销上升**：严重时可能造成服务不稳定甚至被打垮

> 注：仅仅“每次 `retrofit.create()` 创建 Service 代理”通常不会导致连接压力暴涨；关键问题是“每次请求都 new OkHttpClient / new Retrofit”。

## 复���步骤（示例）
1. 在高频路径中调用如下逻辑（例如循环/列表滚动/定时轮询）：
   - 每次请求都 `Retrofit.Builder().build()`
   - 且未 `.client(sharedOkHttpClient)`
2. 并发或持续请求一段时间
3. 观察现象：
   - 客户端频繁建立新连接/TLS 握手
   - 服务端连接数、握手数、CPU 飙升

## 可疑代码示例（伪代码）
```kotlin
fun requestOnce() {
  val retrofit = Retrofit.Builder()
    .baseUrl(BASE_URL)
    // BUG：未设置 .client(sharedClient)
    .addConverterFactory(...)
    .build()

  val api = retrofit.create(Api::class.java)
  api.getUser(...).execute() // 或 enqueue / suspend
}
```

## 预期行为
- `OkHttpClient` 应为进程级单例（或按策略少量实例），复用连接池/dispatcher
- `Retrofit` 应按 `baseUrl` 复用（缓存为单例）
- 每次请求只创建一次性的 `Call` / 协程调用，不重复构建网络栈

## 实际行为
- 每次请求构建新的 Retrofit
- Retrofit 内部隐式创建新的默认 OkHttpClient
- 每次请求使用新的连接池，连接无法复用，导致连接/握手激增

## 根因分析
- Retrofit 的 `Retrofit.Builder` 如果不设置 `.client(okHttpClient)`，在 `build()` 时会创建并持有一个默认的 `OkHttpClient` 实例
- 当业务代码在请求路径中反复执行 `Retrofit.Builder().build()`，等价于反复创建 `OkHttpClient`
- 多个 `OkHttpClient` 实例之间**不会共享连接池**（除非刻意共享 `ConnectionPool`，通常不这么做）
- 因此连接复用失效，引发连接压力问题

## 修复建议
### 方案 A（推荐）：复用单例 OkHttpClient + 按 baseUrl 缓存 Retrofit
- 创建全局单例 `OkHttpClient`
- 针对每个 `baseUrl` 构建一个 Retrofit 并缓存（Map）
- Service 也可按 Retrofit 创建后缓存为单例

要点：
- 禁止在高频业务路径中 `new OkHttpClient()` 或 `Retrofit.Builder().build()`
- 所有 Retrofit.Builder 都显式 `.client(sharedOkHttpClient)`

### 方案 B：使用依赖注入（Hilt/Dagger/Koin）
- 用 DI 提供 `@Singleton OkHttpClient`
- 按 baseUrl 提供/区分不同 Retrofit（Qualifier）
- 业务层只依赖 Service 接口，不接触 Builder

## 验收标准（建议）
- 代码中不再出现高频路径构建 `Retrofit.Builder()`/`OkHttpClient()`
- 在压测/灰度期间：
  - 服务端新建连接数、TLS 握手数显著下降
  - 同等 QPS 下服务端 CPU/连接数回落到正常水平
  - 客户端 TIME_WAIT、端口占用明显减少

## 备注
- 断网恢复不要求重建 Retrofit/OkHttpClient；多数情况下只需重新发起请求
- 若不同 `baseUrl` 需要不同网络策略（证书 pinning/代理/超时/缓存），可按策略拆分少量 OkHttpClient 实例