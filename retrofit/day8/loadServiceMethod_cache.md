# Retrofit 2.11.0：精确定位“注解解析发生点”与验证缓存差异（Android）

> 目标：在 Retrofit 2.11.0 中定位 **注解解析发生在哪些函数/入口**，以及证明 **缓存命中**会带来可测差异（只解析一次、后续复用）。

---

## 1. 定位：注解解析发生在哪，缓存在哪里

### 1.1 缓存容器：Method -> ServiceMethod 的缓存

Retrofit 2.x 会把每个接口方法（`java.lang.reflect.Method`）解析后的结果缓存到 **Retrofit 实例内部的 Map**（常见命名：`serviceMethodCache`）。

在 IDE 中可通过以下关键字定位：

- 全局搜索：`serviceMethodCache`
- 或搜索：`loadServiceMethod(`
- 或搜索：`ServiceMethod.parseAnnotations`

你通常会看到类似调用链：

1. `Retrofit.create(...)` 返回的动态代理（`InvocationHandler`）
2. 每次调用接口方法时进入：`loadServiceMethod(method)`
3. `cache.get(method)` 命中：直接返回缓存的 `ServiceMethod`
4. 未命中：调用 `ServiceMethod.parseAnnotations(this, method)` 解析并 `cache.put(method, result)`

**结论：**
- 缓存发生点：`loadServiceMethod(Method)`
- 缓存粒度：每个 Retrofit 实例内，每个接口方法（Method）解析一次

---

### 1.2 注解解析入口：`ServiceMethod.parseAnnotations(retrofit, method)`

注解解析“总入口”通常是：

- `ServiceMethod.parseAnnotations(...)`

它内部一般做两类解析：

#### A) 请求层解析：方法/参数注解 -> RequestFactory
你要找的“注解解析发生点”最典型就在：

- `RequestFactory.parseAnnotations(retrofit, method)`

解析内容通常包括：

- **方法注解解析**：扫描 `method.getAnnotations()`（或 `getDeclaredAnnotations()`）
- **参数注解解析**：扫描 `method.getParameterAnnotations()`  
  并为每个参数构造 `ParameterHandler`

#### B) 返回值层解析：返回类型 -> CallAdapter / Converter
常见在 `HttpServiceMethod.parseAnnotations(...)` 中会触发：

- `retrofit.callAdapter(...)`
- `retrofit.responseBodyConverter(...)`
- `retrofit.requestBodyConverter(...)`（如果有 `@Body`）

---

## 2. 验证：缓存带来的差异怎么量化/证明

你要证明两件事：

- **A：同一个 Method 的注解解析确实只发生一次（同一个 Retrofit 实例内）**
- **B：第一次调用 vs 后续调用存在可测差异，差异来源是缓存命中**

下面按验证强度从低到高给三种方法。

---

### 方法 1：打断点 / 计数（最直接、最确定）

断点位置建议：

- `Retrofit.loadServiceMethod(Method)`（观察 cache hit/miss 分支）
- `ServiceMethod.parseAnnotations(...)`
- `RequestFactory.parseAnnotations(...)`

运行最小 Demo：

- 构造 Retrofit
- `val api = retrofit.create(Api::class.java)`
- 连续调用 **同一个接口方法** 两次

预期现象：

- 第一次调用：进入 `parseAnnotations` / `RequestFactory.parseAnnotations`
- 第二次调用：只进入 `loadServiceMethod` 并走 cache hit，不再进入解析函数

优点：不改源码、定性但非常强  
缺点：偏“观察型”（不过结论很可靠）

---

### 方法 2：Profiler（JFR / async-profiler / Android Studio CPU Profiler）看热点

目标：在 profile 中看到首次调用时 `parseAnnotations` 栈明显；后续调用栈显著减少或消失。

思路：

- 先 warm up（降低 JIT 干扰）
- 分别观测：
  - 第 1 次调用（cold）
  - 第 N 次调用（warm）

预期：

- Cold：火焰图/调用树中出现（占比高）
  - `ServiceMethod.parseAnnotations`
  - `RequestFactory.parseAnnotations`
  - `retrofit.callAdapter`
  - `retrofit.responseBodyConverter`
- Warm：上述解析栈显著减少/消失；热点转移到
  - `ParameterHandler.apply`
  - `OkHttpCall` 创建（如果你只是创建 Call）

优点：定量、可展示  
缺点：需要一点 profiling 经验；cold 很短时采样可能抓不到（可配合 trace section）

---

### 方法 3：控制变量计时（给出明确“缓存差值”数字）

为了避免网络噪声，必须把“执行网络”剥离，只测：

- **方法解析 + 创建 Call**（不 execute/enqueue）

可行策略：

- 让接口方法返回 `Call<Void>` 或 `Call<ResponseBody>`
- 调用接口方法只拿到 `Call`，不执行网络

测试结构建议：

- Case A（cold）：new Retrofit -> create service -> 调用一次方法
- Case B（warm）：同一个 Retrofit & service，循环调用同一个方法 10 万次取平均

记录指标：

- 首次调用耗时（一次）
- 后续平均耗时（N 次平均）
- 二者比值（通常 cold 明显更高）

关键验证点：

- 如果你每次都 new Retrofit，那么每次都 cold，差异会变小或消失  
  （因为缓存挂在 Retrofit 实例上）

---

## 3. “验证结论”的判据（你应该看到什么）

同一个 Retrofit 实例 + 同一个接口 Method：

- **第一次调用**：发生 `ServiceMethod.parseAnnotations` / `RequestFactory.parseAnnotations`
- **后续调用**：不再发生解析（cache hit）

从耗时/Profiler 的视角：

- 首次调用成本显著高于后续调用
- 后续成本主要是：
  - 参数应用：`ParameterHandler.apply`
  - `OkHttpCall` 创建（如果只是创建 Call，不执行网络）

---

## 4. Android 上 profiler 时如何做到“不发送网络请求”

### 4.1 若接口返回 `Call<T>`（推荐用于验证）
只要你 **不调用**：

- `call.execute()`（同步）
- `call.enqueue(...)`（异步）

就不会发生网络 I/O。

可选：调用 `call.request()` 也不会发网络，只是拿到/构建 `Request`。

### 4.2 若接口是 `suspend fun ... : T`
`suspend` 一旦真实调用并挂起等待结果，通常会走网络。

想做“不发网络”的解析验证，建议加一个 profiling 专用的 `Call<T>` 方法，仅用于测试缓存解析。

---

## 5. 关键问题答复：`loadServiceMethod` 与 `parseAnnotations` 是否只有 cold 才调用？

- **`loadServiceMethod(method)`：cold 与 warm 都会调用**
  - 因为每次调用接口方法都需要拿到该 Method 对应的 ServiceMethod 模型
  - warm 只是 cache hit，快速返回

- **`parseAnnotations(...)`：同一个 Retrofit 实例 + 同一个 Method 下，通常只有 cold 才会调用**
  - 它仅在 cache miss 时触发，用于构建 `ServiceMethod`（含 RequestFactory/ParameterHandler/CallAdapter/Converter 等）

补充：

- 换另一个接口方法：它的第一次调用也会 parse 一次
- 重新创建 Retrofit：缓存清空，会再次 parse
- 极端并发下（实现细节相关）：可能观察到非常短暂的重复解析，但一般单线程场景只解析一次

---