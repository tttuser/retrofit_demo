# Retrofit 三种声明方式对比（上下文梳理版）

本文对比 Retrofit 常见三种 API 声明方式：

1. `fun api(...): Call<T>`
2. `suspend fun api(...): T`
3. `suspend fun api(...): Response<T>`

对比维度聚焦在：**调用流程（什么时候真正发请求）、线程/阻塞（谁阻塞、谁不阻塞）、成功/失败语义（2xx、非 2xx、网络异常）、上下文与 runBlocking 的影响**。

---

## 0. 共同前提：Retrofit 底层都用 OkHttp 发请求

- 不管你用 `Call<T>` 还是 `suspend`，底层最终都会委托 OkHttp 执行 HTTP 请求。
- “线程表现”需要分两层看：
  1) **你调用 API 的线程/协程是否被阻塞**  
  2) **网络 I/O 实际在哪个线程执行**（通常是 OkHttp Dispatcher 线程池）

---

## 1) `Call<T>`：显式请求对象（同步 execute / 异步 enqueue）

### 1.1 调用流程（何时发请求）
```kotlin
val call = service.getItemAsCall(id)  // 只是创建 Call，还没发网络
val response = call.execute()         // 此时才真正发起请求（同步）
```

- `getItemAsCall()` 返回的是一个“可执行请求对象” `Call<T>`。
- **真正发请求的时机**在你调用：
  - `call.execute()`（同步阻塞）
  - `call.enqueue(callback)`（异步回调）

### 1.2 线程/阻塞上下文
- `execute()`：
  - **阻塞当前调用线程**直到完成（不要在 UI 线程调用）。
- `enqueue()`：
  - **不阻塞**当前线程；
  - 网络执行在 OkHttp 线程池；
  - 回调线程由 Retrofit 的 callback executor/平台默认决定（Android 常见为主线程回调，JVM 环境常见为后台线程回调，具体取决于配置）。

### 1.3 成功/失败语义（HTTP/异常）
- HTTP 2xx：返回 `Response<T>`，`response.isSuccessful==true`，`response.body()` 取到 `T`
- HTTP 非 2xx：**不抛异常**，仍返回 `Response<T>`，`isSuccessful==false`，可读 `errorBody()`
- 网络错误/超时/取消/解析错误：
  - `execute()`：抛异常（常见 `IOException` / converter 异常）
  - `enqueue()`：进入 `onFailure`

### 1.4 典型使用场景
- 需要显式控制 `Call` 生命周期：`clone()`、`cancel()`、复用/封装自定义回调逻辑；
- 需要同步（非协程环境）或回调式集成。

---

## 2) `suspend fun … : T`：直接返回 body（把非 2xx 视为异常）

### 2.1 调用流程（何时发请求）
```kotlin
val item: T = service.getItemDirect(id) // 调用即触发请求；内部创建 Call 并挂起等待
```

- 对调用者而言：像普通函数一样“顺序调用得到结果”。
- 对 Retrofit 而言：内部仍会创建 `Call` 并执行；协程在等待期间**挂起**。

### 2.2 线程/阻塞上下文（尤其注意 runBlocking）
- 在协程中（推荐方式）：
  - **不会阻塞线程**（只是协程挂起等待）。
  - 网络 I/O 在 OkHttp 线程池。
  - 请求完成后，协程恢复在**调用者协程上下文**（例如 Main/IO）。
- 如果你用 `runBlocking { service.getItemDirect(id) }`：
  - **会阻塞当前线程**直到请求完成（`runBlocking` 把协程等待变成同步等待）。
  - 这常用于测试/命令行，不适合 UI 线程。

> 关键点：`suspend` 不等于自动切到 `Dispatchers.IO`。线程切换由你的协程上下文/`withContext` 决定；网络 I/O 本身由 OkHttp 线程池处理。

### 2.3 成功/失败语义（HTTP/异常）
- HTTP 2xx：直接返回 `T`
- HTTP 非 2xx：**抛 `HttpException`**
  - 如需 code / errorBody：从 `catch (e: HttpException)` 中取 `e.code()` / `e.response()?.errorBody()`
- 网络错误/超时/解析错误：抛异常（常见 `IOException` / converter 异常）
- 协程取消：取消会传播到底层请求（等价于取消内部 Call）

### 2.4 典型使用场景
- 你希望“非 2xx 即异常”，统一走 `try/catch`；
- 业务层只关心成功的 body，不关心 headers/status/errorBody；
- 代码最简洁，适合搭配 repository/usecase 层做统一异常映射。

---

## 3) `suspend fun … : Response<T>`：返回完整 Response（把非 2xx 视为普通分支）

### 3.1 调用流程（何时发请求）
```kotlin
val resp: Response<T> = service.getItemAsResponse(id) // 调用即触发请求；内部挂起等待
```
- 与 “`suspend : T`” 一样：调用即触发请求，内部挂起等待。

### 3.2 线程/阻塞上下文
- 与 `suspend : T` 完全一致：
  - 协程中调用：不阻塞线程；网络 I/O 在 OkHttp 线程池；恢复到调用者协程上下文。
  - `runBlocking` 调用：阻塞当前线程直到完成。

### 3.3 成功/失败语义（HTTP/异常）
- HTTP 2xx：返回 `Response<T>`，`isSuccessful==true`，`body()` 有值
- HTTP 非 2xx：**不抛异常**，仍返回 `Response<T>`，`isSuccessful==false`，可读：
  - `code()`、`headers()`、`errorBody()`
- 网络错误/超时/解析错误：仍会抛异常（`IOException` / converter 异常等）
- 协程取消：同样会取消底层请求

### 3.4 典型使用场景
- 需要精细化处理 HTTP 语义（例如 401/403/429/500 分支）；
- 需要 headers / 状态码 / 错误体来做业务映射或埋点；
- 想用协程写法，但不想用 `HttpException` 来表达非 2xx。

---

## 4) 总结对照表（调用流程 / 阻塞 / 语义）

| 维度 | `Call<T>` + `execute()` | `Call<T>` + `enqueue()` | `suspend : T` | `suspend : Response<T>` |
|---|---|---|---|---|
| 何时发请求 | `execute()` 时 | `enqueue()` 时 | 调用函数时 | 调用函数时 |
| 是否阻塞调用线程 | **是** | 否 | 否（但 `runBlocking` 会阻塞） | 否（但 `runBlocking` 会阻塞） |
| 网络 I/O 线程 | OkHttp（但调用线程被卡住等待） | OkHttp 线程池 | OkHttp 线程池 | OkHttp 线程池 |
| 2xx 返回 | `Response<T>` | 回调给 `Response<T>` | 直接 `T` | `Response<T>` |
| 非 2xx 行为 | 返回 `Response`，不抛 | 回调 `Response`，不抛 | **抛 `HttpException`** | 返回 `Response`，不抛 |
| 网络错误/超时 | 抛异常 | `onFailure` | 抛异常 | 抛异常 |
| code/headers/errorBody 获取 | 直接从 `Response` | 直接从 `Response` | 需从 `HttpException.response()` 取 | 直接从 `Response` |
| 取消 | `call.cancel()` | `call.cancel()` | 取消协程会联动取消请求 | 同左 |

---

## 5) 选择建议（按“上下文/语义”选）
- **偏异常流（非 2xx 当异常）**：优先 `suspend : T`
- **偏分支流（非 2xx 自己判断并读取错误体）**：优先 `suspend : Response<T>`
- **需要显式 Call 对象 / 回调式集成 / 手动 clone/cancel 控制**：使用 `Call<T>`

---

## 附：关于 `runBlocking` 的定位（与 Retrofit 三种方式的关系）
- `runBlocking` 的作用是：**把协程等待变成同步等待**，因此会**阻塞当前线程**。
- Retrofit 的 `suspend` 接口在正常协程里不会阻塞线程；但一旦外层包 `runBlocking`，效果就会接近 `execute()`：当前线程被卡住直到网络完成。
- 因此 `runBlocking` 适合测试/命令行，不适合 UI 主线程。