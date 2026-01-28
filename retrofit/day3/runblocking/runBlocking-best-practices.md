# runBlocking 最佳实践（Kotlin 协程）

`runBlocking` 的定位：**把协程代码“同步化”**——在当前线程启动一个协程，并**阻塞当前线程**直到 block 完成后返回结果。  
因此它的最佳实践核心是：**只在“可以阻塞线程且需要同步结果”的边界位置使用**。

---

## 1. 什么时候用（推荐场景）

### 1.1 `main()` / CLI 工具入口（最典型）
在命令行程序里，你经常需要“顺序执行 + 同步退出”。

```kotlin
fun main() = runBlocking {
  val item = service.getItemDirect(1)
  println(item)
}
```

### 1.2 迁移/过渡层：同步 API 需要调用 suspend（谨慎使用）
当你必须在同步函数里调用 suspend（例如老代码接口不允许改成 suspend），可在最外层边界用一次 `runBlocking` 包起来。

```kotlin
fun loadDataSync(): Item = runBlocking {
  service.getItemDirect(1)
}
```

> 注意：这类写法应限制在“边界/适配层”，不要在业务深处层层 runBlocking。

### 1.3 单元测试（更推荐 runTest，但 runBlocking 仍常见）
- 若能用 `kotlinx-coroutines-test`，优先用 `runTest`。
- 若是简单测试或暂不引入 test 库，可以用 `runBlocking` 让测试同步等待。

```kotlin
@Test
fun testFetchItem() = runBlocking {
  val item = service.getItemDirect(1)
  assertNotNull(item)
}
```

---

## 2. 什么时候不要用（反例）

### 2.1 不要在 UI 线程用（Android 主线程）
- 会卡住 UI 事件循环，导致卡顿/ANR 风险。
- UI 层应该用 `lifecycleScope` / `viewModelScope` 启动协程。

**反例：**
```kotlin
// ❌ 不推荐：UI 线程阻塞
runBlocking {
  val item = service.getItemDirect(1)
  show(item)
}
```

**正确：**
```kotlin
// ✅ 推荐：不阻塞 UI
lifecycleScope.launch {
  val item = service.getItemDirect(1)
  show(item)
}
```

### 2.2 不要在 suspend 函数里再 runBlocking
在协程世界里再阻塞线程，通常会导致线程浪费甚至死锁风险。

---

## 3. 使用原则（Best Practices）

### 3.1 “一次性、在边界使用”
- 尽量只在应用/模块的边界用一次：`main()`、测试入口、同步接口适配层。
- 业务逻辑层尽量全部用 `suspend`/`launch`/`async` 贯穿。

### 3.2 block 内只做“必须同步等待”的事
- 把复杂业务拆成可测试的 suspend 函数；
- `runBlocking` 只负责“等待并返回”。

### 3.3 明确线程：不要让它跑在不该阻塞的线程上
- 你调用 `runBlocking` 的线程会被阻塞，所以要确保它是“允许阻塞”的线程。
- 如果你必须做耗时 I/O 或 CPU 工作，考虑在 `runBlocking` 内部显式切换到合适的 dispatcher（注意：这并不会让外层不阻塞，但能避免把工作塞到当前线程执行）。

```kotlin
fun main() = runBlocking {
  val item = withContext(Dispatchers.IO) {
    service.getItemDirect(1)
  }
  println(item)
}
```

### 3.4 结构化并发：避免使用 GlobalScope
- `runBlocking` 会等待其作用域内的子协程完成；这是优点。
- 不要在里面 `GlobalScope.launch` 让任务“逃逸”，否则 `runBlocking` 返回后仍可能有后台任务在跑，难以管理。

---

## 4. 推荐模板（可直接复用）

### 4.1 CLI / main 模板
```kotlin
fun main() = runBlocking {
  // 1) 解析参数
  // 2) 调用 suspend 业务函数
  // 3) 输出结果并结束
  val result = runCatching { doWork() }
  result.onSuccess { println(it) }
        .onFailure { it.printStackTrace(); exitProcess(1) }
}

suspend fun doWork(): String {
  // 这里写真正的协程逻辑
  return "OK"
}
```

### 4.2 同步适配层模板（仅边界）
```kotlin
class LegacyApiAdapter(
  private val repo: Repo
) {
  fun fetchSync(id: Int): Item = runBlocking {
    repo.fetch(id) // suspend
  }
}
```

---

## 5. 一句话总结
- **用**：测试、`main()`、同步接口适配层（边界）
- **不用**：UI 线程、业务层、suspend 里嵌套
- **原则**：少用、在边界用、避免 GlobalScope、保持结构化并发