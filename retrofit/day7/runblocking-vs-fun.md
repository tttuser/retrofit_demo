# `fun A() { ... }` vs `fun A() = runBlocking { ... }` vs `val A = runBlocking { ... }`

下面三种写法本质区别在于：**A 是“函数”还是“值/变量”**，以及 **runBlocking 的执行时机（每次调用 vs 定义时就执行一次）**，还有 **能否接收参数、能否有返回类型泛型等**。

---

## 1) `fun A() { ... }`

- **A 是一个函数**（可带参数、可重载、可声明返回类型）。
- `{ ... }` 里的代码在 **每次调用 `A()` 时执行**。
- 是否阻塞线程：取决于你 `{ ... }` 内部怎么写；本身不是 coroutine builder。

示例：

```kotlin
fun A() {
    println("run each call")
}
```

特点：

- 可以 `A()` 调用多次，每次都执行一遍函数体
- 可以有参数：`fun A(x: Int) { ... }`
- 可以返回：`fun A(): Int { ... }`

---

## 2) `fun A() = runBlocking { ... }`

- **A 仍然是一个函数**，只是它的实现是“调用 runBlocking 并返回其结果”。
- `{ ... }` 里的代码在 **每次调用 `A()` 时执行**。
- **每次调用都会 runBlocking，并阻塞当前线程直到协程完成**。
- `A()` 的返回值类型就是 `runBlocking { ... }` 最后一个表达式的类型（可以推断，也可显式写）。

示例：

```kotlin
fun A() = runBlocking {
    // 这里可以调用 suspend 函数
    42
}
// A() 的类型是 Int
```

特点：

- 仍可写成带参数：`fun A(x: Int) = runBlocking { ... }`
- 每次调用都会“进入一次 runBlocking”，因此**重复调用会重复阻塞**

---

## 3) `val A = runBlocking { ... }`

- **A 是一个值/变量（属性）**，不是函数。
- `runBlocking { ... }` 在 **这行代码被执行时就立刻运行一次**（通常是：顶层/对象初始化/类初始化时）。
- 初始化完成后，`A` 就是 runBlocking 的**结果值**，以后只是读这个值，不会再执行 block。
- 同样：初始化时会**阻塞当前线程直到完成**（在类/对象初始化阶段阻塞尤其要小心）。

示例：

```kotlin
val A = runBlocking {
    println("run once at init")
    42
}
// A 的类型是 Int
```

特点：

- 不能 `A()` 调用，因为它不是函数
- 只会执行一次（初始化时），之后只是拿结果

---

## 总结对比（最核心）

- `fun A() { ... }`：A 是函数；每次 `A()` 执行；不天然阻塞（看你写啥）
- `fun A() = runBlocking { ... }`：A 是函数；每次 `A()` 都 runBlocking；**每次调用都阻塞**
- `val A = runBlocking { ... }`：A 是值；**定义/初始化时就 runBlocking 一次并阻塞**；之后只是读取结果

---

## 额外建议

如果你把 `{ ... }` 里放了耗时/挂起调用：

- 想“每次调用都执行一次并阻塞当前线程” ⇒ `fun A() = runBlocking { ... }`
- 想“程序启动/初始化时只算一次结果” ⇒ `val A = runBlocking { ... }`

更推荐的协程风格通常是：**用 `suspend fun A()`**，让调用方决定在哪个 scope 调用，而不是在库代码里 `runBlocking`（除非是 main/test/桥接同步 API）。