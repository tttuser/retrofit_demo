# Retrofit 核心对象职责图谱（关键对象关系与创建时机）

> 目标：展示 Retrofit 关键对象的**静态关系（职责/依赖）**与**动态创建时机（生命周期/缓存/每次调用）**。

---

## 1) 核心对象关系图谱（静态职责图）

```text
┌────────────────────────────────────────────────────────────────────┐
│                          应用初始化阶段                             │
└────────────────────────────────────────────────────────────────────┘

 OkHttpClient  (HTTP执行器/连接池/拦截器/超时/缓存/调度)
    ▲
    │ Retrofit 持有 callFactory = OkHttpClient(或其他 Call.Factory)
    │
┌───┴───────────────────────────────────────────────────────────────┐
│ Retrofit                                                          │
│  - baseUrl                                                        │
│  - callFactory (OkHttpClient / Call.Factory)                      │
│  - converterFactories (Gson/Moshi/Scalars/Proto...)               │
│  - callAdapterFactories (RxJava/Coroutine/Future...)              │
│  - serviceMethodCache (Method -> ServiceMethod)                   │
└───┬───────────────────────────────────────────────────────────────┘
    │ create(ServiceInterface)
    ▼
 动态代理 Proxy + InvocationHandler
    │ invoke(method, args)
    ▼
 ServiceMethod（接口方法解析后的“执行计划”，通常缓存）
    ├─ HttpServiceMethod（常见实现）
    │   ├─ RequestFactory（把注解+参数映射为 Request 生成规则）
    │   │   ├─ HTTP 方法/相对路径/headers/是否 multipart 等
    │   │   └─ ParameterHandler[]（逐参数把 args 写入 request）
    │   ├─ CallAdapter（把 Call 适配为声明的返回类型）
    │   └─ Converter（请求体/响应体转换）
    │       ├─ RequestBodyConverter
    │       └─ ResponseBodyConverter
    └─（其他实现/分支：本质仍是 解析 + 适配 + 转换）

    │ toCall(args) / invoke(...)
    ▼
 OkHttpCall（Retrofit 对一次调用的封装；可取消；懒创建 rawCall）
    │ createRawCall()
    ▼
 okhttp3.Call（真实网络调用对象）
    │ execute() / enqueue()
    ▼
 Network I/O
    │
    ▼
 ResponseBody -> Converter -> Java/Kotlin 对象
    │
    ▼
 通过 CallAdapter 交付结果（Call / suspend / Observable / Future ...）
```

---

## 2) 核心对象职责速览（按层）

### 2.1 传输层（HTTP 执行）
- **OkHttpClient**
  - 职责：真实 HTTP 调用底座（连接池、拦截器链、重试、缓存、TLS、调度等）。
  - 特征：强复用（通常全局单例/按域名多例）。

- **okhttp3.Call**
  - 职责：一次真实的网络调用实例。
  - 特征：一次请求对应一个 call；可同步/异步；可取消。

### 2.2 Retrofit 运行时组装层
- **Retrofit**
  - 职责：将“接口方法”转换为“可执行的调用”；管理工厂链与缓存。
  - 关键成员：
    - `baseUrl`
    - `callFactory`（通常是 OkHttpClient）
    - `converterFactories`
    - `callAdapterFactories`
    - `serviceMethodCache`（Method -> ServiceMethod）

- **Proxy / InvocationHandler**
  - 职责：拦截你对接口方法的调用，将 `method + args` 交给 Retrofit 的解析产物执行。

### 2.3 方法模型层（解析与缓存）
- **ServiceMethod / HttpServiceMethod**
  - 职责：对一个接口方法的“解析结果”，即执行计划：
    - 怎么构建 Request（RequestFactory + ParameterHandler）
    - 用哪个 CallAdapter（决定返回类型形态）
    - 用哪个 Converter（序列化/反序列化）

- **RequestFactory**
  - 职责：把注解（@GET/@POST/@Headers/...）与参数规则编译成“Request 构建模板”。

- **ParameterHandler[]**
  - 职责：逐个参数处理（@Path/@Query/@Header/@Body/@Part 等），把运行时 `args` 写入请求。

### 2.4 适配与转换层（返回值 & 序列化）
- **CallAdapter.Factory / CallAdapter**
  - 职责：把 Retrofit 的 Call（或内部执行体）适配成你方法声明的返回类型：
    - `Call<T>` / `Deferred<T>` / `suspend` / `Observable<T>` / `Future<T>` 等。

- **Converter.Factory / Converter**
  - 职责：请求体与响应体转换：
    - 对请求：`T -> RequestBody`
    - 对响应：`ResponseBody -> T`

### 2.5 调用封装层
- **OkHttpCall**
  - 职责：Retrofit 对一次调用的封装（管理执行、取消、错误映射、延迟创建 rawCall）。
  - 特征：**每次方法调用创建一个新的 OkHttpCall**。

---

## 3) 创建时机与生命周期（动态视角）

> 以“初始化 → create() → 首次方法调用解析 → 每次网络调用”四阶段说明。

### A. 应用初始化/DI 阶段（通常一次或少量）
创建/确定：
- **OkHttpClient**：通常全局复用（单例或按域名多例）。
- **Retrofit**：通常按 baseUrl 创建 1~N 个实例，长期复用。
- **converterFactories / callAdapterFactories**：在 `Retrofit.Builder` 阶段配置并固化到 Retrofit 中。

### B. `retrofit.create(Api::class.java)` 阶段（每个 service 接口）
创建：
- **Proxy（动态代理实例）**：为接口生成实现对象（轻量，通常被 DI 复用）。

### C. 首次调用某个接口方法（每个 Method 首次）
创建并缓存：
- **ServiceMethod（HttpServiceMethod）**：解析注解、参数、返回类型，生成执行计划。
- **RequestFactory / ParameterHandler[]**：同时在解析过程中生成。
- **CallAdapter / Converter**：从工厂链中选择并绑定到该方法模型中。
- **缓存点**：`serviceMethodCache.put(method, serviceMethod)`，后续同一方法直接复用解析结果。

### D. 每次实际发起调用（每次业务调用）
每次都会创建：
- **OkHttpCall**：一次业务调用对应一个实例。
- **okhttp3.Request**：由 RequestFactory + ParameterHandler 使用本次 args 生成。
- **okhttp3.Call**：通常在 `enqueue/execute` 时懒创建，每次调用一个。
- **转换过程**：响应到达后 `ResponseBody -> Converter -> T`（每次一次）。

---

## 4) 调用时序图（从接口调用到发包）

```text
调用方
  │ api.getUser(id)
  ▼
Proxy.invoke(method,args)
  │  ServiceMethodCache.get(method)
  │   ├─ miss -> parse annotations -> build HttpServiceMethod -> put cache
  │   └─ hit  -> reuse
  ▼
HttpServiceMethod.invoke(args)
  │  RequestFactory.create(args) -> okhttp Request
  │  new OkHttpCall(...)
  │  CallAdapter.adapt(OkHttpCall) -> 返回你声明的类型
  ▼
执行(同步/异步/协程等)
  │  OkHttpCall -> okhttp3.Call -> network
  ▼
响应 -> Converter<ResponseBody,T> -> T
```

---

## 5) 常见复用/不复用总结（速查）

- **强复用（长期持有）**：OkHttpClient、Retrofit、Factories 列表
- **按接口复用**：Proxy（service 实例）
- **按方法复用（首次解析后缓存）**：ServiceMethod、RequestFactory、ParameterHandler、绑定好的 Converter/CallAdapter
- **每次调用创建**：OkHttpCall、Request、okhttp3.Call、响应转换过程