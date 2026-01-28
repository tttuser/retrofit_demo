# 从 Retrofit `Call.cancel()` 到 OkHttp 实际终止网络：过程梳理

本文整理上下文中关于 **Retrofit 的 `Call.cancel()` 如何一路触发 OkHttp 层取消并终止网络 I/O** 的讨论，按“对象关系 → 调用链 → 分阶段行为 → 上层感知 → 常见延迟原因”组织。

---

## 1. Retrofit 的 `Call` 与 OkHttp 的 `Call`：封装关系

- 业务代码中常见的是 `retrofit2.Call<T>`。
- Retrofit 内部实现（常见是 `OkHttpCall<T>`）会**封装**一个 `okhttp3.Call`（通常运行时是 OkHttp 的 `RealCall`）。
- `OkHttpCall` 往往是**懒创建**底层 OkHttp Call：只有在 `enqueue/execute` 时才真正创建并启动。

因此：  
Retrofit 的 `cancel()` 本身不直接操作 socket，它的职责通常是把取消“转交”到底层 OkHttp Call。

---

## 2. Retrofit `Call.cancel()`：做了什么？

从语义上看，Retrofit `Call.cancel()` 主要做两件事：

1. **标记自身为已取消**
   - 用于避免后续继续执行/继续回调，或用于状态判断。

2. **把取消传递给底层 OkHttp Call**
   - 如果底层 `okhttp3.Call` 已创建：直接调用 `okhttpCall.cancel()`
   - 如果还没创建：记录取消状态，待创建时再让其处于取消/短路失败的路径

要点：  
Retrofit 负责“转发取消意图”，真正停止网络的是 OkHttp。

---

## 3. OkHttp `Call.cancel()` 的核心动作（RealCall 语义）

OkHttp 的执行实体通常是 `RealCall`。调用 `RealCall.cancel()` 的关键语义是：

1. **设置取消标志（canceled = true）**
   - 后续流程（拦截器链、交换过程等）会检查该状态并尽早失败返回。

2. **尝试取消正在进行的交换（exchange）**
   - 如果请求已经进入网络阶段，会触发对当前“请求/响应交换”的终止：
     - 关闭 socket / 关���流
     - 对 HTTP/2 可能是取消对应 stream（RST_STREAM），尽量不影响同连接其他 stream
   - 目标是让阻塞中的读/写尽快失败返回（通常抛出 `IOException`）

3. **事件通知（可选）**
   - 若安装了 `EventListener` 等机制，会产生对应的 canceled 事件，用于监控与调试。

关键点：  
OkHttp 的取消一般不是通过 `Thread.interrupt()` “硬停线程”，而是通过 **关闭底层 I/O 对象 + 标志检查** 让阻塞点尽快退出。

---

## 4. 取消发生在不同阶段时，OkHttp 如何终止？

一次 HTTP 调用大致经历（简化）：

> Dispatcher 排队 → 拦截器链 → DNS → 建连 → TLS → 写请求 → 读响应头 → 读响应体

取消发生在不同阶段，表现不同：

### A) 还在 Dispatcher 队列里（未开始执行）
- 此时没有建立连接、没有写入/读取。
- cancel 会设置 canceled 标志；任务开始时检查后直接失败/短路。
- 优点：最干净，几乎不产生网络流量。

### B) 正在连接 / TLS 握手 / 写请求中
- 都属于 socket I/O 阶段。
- cancel 通常通过关闭 socket/交换，让：
  - connect/handshake 阻塞尽快失败
  - 写请求的输出流抛 `IOException`

### C) 正在读响应体（最常见）
- 线程常阻塞在响应体读取（如 `Source.read()`）。
- cancel 关闭 socket/stream，使读取抛 `IOException`（例如“Canceled / Socket closed”等形式，依版本和场景不同）。

### D) HTTP/2 多路复用场景
- 一个 TCP 连接承载多个 stream。
- cancel 常优先取消“当前请求对应的 stream”，尽量不杀整个连接。
- 某些状态下也可能关闭连接（更粗暴，但能保证终止）。

---

## 5. Retrofit/调用者如何感知“已取消”？

OkHttp 因 cancel 导致的终止通常以“失败”形式回到调用端：

- **异步 `enqueue`：** 触发 `Callback.onFailure(call, IOException)`
- **同步 `execute`：** 抛出 `IOException`

Retrofit 会把这些失败回传到上层：
- 回调式调用通常走 `onFailure`
- 协程 suspend 适配下，可能表现为异常或协程取消（取决于适配/竞态）

---

## 6. 为什么有时取消看起来“不立即生效”？

即使调用了 `Call.cancel()`，仍可能出现“体感延迟”，常见原因：

- **还没进入可被 I/O 影响的阻塞点**
  - 例如拦截器内有较长的 CPU 计算或同步逻辑，取消只能等它运行到检查点/下一阶段。

- **响应体由用户代码慢慢读**
  - 如果把 `ResponseBody` 交给其他逻辑异步读取，取消要等读操作触发/下一次读取报错才体现。

- **网络栈与调度的微小延迟**
  - 关闭 socket 到阻塞返回通常很快，但极端情况下仍可能有瞬时延迟。

- **Retrofit 层未正确把取消传递给 OkHttp**
  - 例如你把 `Call.enqueue` 包了一层，但协程取消时没有调用 `call.cancel()`（这就是 `awaitCancellable` 这类桥接要解决的问题）。

---

## 7. 结论（要点汇总）

- Retrofit 的 `Call.cancel()` 主要负责：**记录/转发取消**。
- OkHttp 的 `RealCall.cancel()` 才负责：**实际终止网络 I/O**（关闭 socket 或取消 HTTP/2 stream）。
- 取消在不同阶段表现不同，但总体目标一致：让阻塞读写尽快失败返回，并把失败以 `IOException` 等形式回传到上层。

---