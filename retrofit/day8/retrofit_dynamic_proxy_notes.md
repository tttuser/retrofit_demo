# Retrofit 动态代理机制与 JDK Proxy.newProxyInstance 工作原理（本聊天整理）

> 日期：2026-01-29  
> Retrofit 版本：2.11.0  
> 主题：Retrofit `create()` 如何把接口变成可调用对象；以及 `Proxy.newProxyInstance` 内部如何实现。

---

## 1. Retrofit `create()`：接口如何变成“可调用对象”

Retrofit 的“动态代理”含义是：你只定义一个带注解的接口（无实现），Retrofit 通过 `create()` 返回一个实现了该接口的对象；当你调用接口方法时，调用会被 `InvocationHandler` 拦截，把“方法 + 注解 + 参数”解释成一次 HTTP 请求，并返回 `Call`/`suspend`/Rx 等你声明的返回类型。

### 1.1 `Retrofit.create(Service::class.java)` 核心做法

主要三件事：

1. **校验入参必须是接口**  
   Retrofit 只支持 interface，因为依赖 JDK 动态代理生成实现类。

2. **通过 JDK 动态代理创建接口实现对象**  
   典型形式是 `Proxy.newProxyInstance(...)`，传入：
   - `ClassLoader`
   - `interfaces = new Class<?>[]{serviceInterface}`
   - `InvocationHandler`

3. **InvocationHandler 负责把“方法调用”翻译成“HTTP 调用”**  
   - `Object` 自带方法（`toString/equals/hashCode`）通常直接走默认逻辑  
   - Java 8 default 方法走 `MethodHandles`/平台兼容逻辑  
   - 其它方法按 Retrofit API 方法处理：解析注解、构建请求、执行网络、解析响应、适配返回值

---

## 2. Retrofit 2.11.0 更贴近源码的执行链路

调用接口方法时，InvocationHandler 会将 `Method` 解析成一个可复用的执行模型并缓存，然后用实参生成 OkHttp Request，执行并适配返回类型。

### 2.1 `loadServiceMethod(Method)`：方法解析与缓存

- key：`java.lang.reflect.Method`
- value：`ServiceMethod<?>`（抽象层）
- 典型逻辑：
  - 先查缓存 `serviceMethodCache.get(method)`
  - 没有则 `ServiceMethod.parseAnnotations(retrofit, method)` 创建
  - 放入缓存再返回

> 注解解析和泛型解析开销大，因此必须缓存。

### 2.2 `ServiceMethod.parseAnnotations(...)`：两层解析

#### A) `RequestFactory`：怎么拼 Request

`RequestFactory` 会从方法/参数注解中提取：

- HTTP method：GET/POST/PUT...
- relativeUrl：如 `"users/{id}"`
- 静态 headers、content-type
- 是否允许/必须 body、form、multipart
- `parameterHandlers[]`：每个参数对应一个 `ParameterHandler`，负责把实参写入 RequestBuilder

典型入口：`RequestFactory.parseAnnotations(retrofit, method)`。

#### B) `HttpServiceMethod`：怎么执行 + 怎么适配返回值

`HttpServiceMethod` 会组合：

- `RequestFactory`（构建请求）
- `Converter<ResponseBody, ResponseT>`（解析响应体）
- `CallAdapter<ResponseT, ReturnT>`（把 Retrofit Call 适配成声明的返回类型）
- 底层执行体一般是 `OkHttpCall<T>`

典型入口：`HttpServiceMethod.parseAnnotations(retrofit, method, requestFactory)`。

---

## 3. RequestFactory / ParameterHandler：请求如何由实参拼出来

在执行阶段（`invoke(args)`）通常是：

1. 创建 `RequestBuilder`
2. 对每个参数执行：
   - `parameterHandlers[i].apply(requestBuilder, args[i])`
3. `requestBuilder.build()` 生成 `okhttp3.Request`

### 3.1 常见 ParameterHandler 类型（概念）

- `Path`：处理 `@Path`
- `Query` / `QueryMap`：处理 `@Query`/`@QueryMap`
- `Header` / `HeaderMap`：处理 `@Header`/`@HeaderMap`
- `Body`：处理 `@Body`（走 `Converter<T, RequestBody>`）
- `Field` / `FieldMap`：`@FormUrlEncoded` 场景
- `Part` / `PartMap`：`@Multipart` 场景
- `Url`：处理 `@Url` 覆盖整个 url
- `Tag`：OkHttp tag

每个 handler 的本质：把一个实参值按注解语义写入 URL/Query/Header/Body 等槽位。

---

## 4. CallAdapter 与 Converter：返回类型与序列化如何确定

### 4.1 `CallAdapter`：决定“方法返回类型怎么得到”

- 从 `method.getGenericReturnType()` 推导 returnType
- `retrofit.callAdapter(returnType, annotations)` 遍历注册的 `CallAdapter.Factory` 找可处理者
- `CallAdapter` 提供：
  - `responseType()`：告诉 Retrofit body 需要解析成什么类型
  - `adapt(Call<R>)`：把 Retrofit 的 `Call<R>` 转为声明的返回类型

`suspend` 返回值在 Retrofit 2.6+ 属于内建支持的一类适配路径（最终也会形成类似“从 Call 到挂起结果”的适配过程）。

### 4.2 `Converter`：决定 ResponseBody / RequestBody 如何转换

- 响应体：`retrofit.responseBodyConverter(responseType, annotations)`
- 请求体（如 `@Body`）：`retrofit.requestBodyConverter(bodyType, parameterAnnotations, methodAnnotations)`
- 遍历注册的 `Converter.Factory`（如 Gson/Moshi/Scalars），找到能转换的 converter。

---

## 5. OkHttpCall：真正发起网络请求

执行阶段大致是：

1. `RequestFactory + args` -> `okhttp3.Request`
2. `okhttp3.Call.Factory`（通常 OkHttpClient）创建 `okhttp3.Call`
3. Retrofit 用内部 `OkHttpCall<T>` 包装：
   - 提供 `execute/enqueue/cancel/...`
   - 在拿到响应后调用 `Converter<ResponseBody, T>` 转为业务对象
4. 交给 `CallAdapter.adapt(...)` 输出为接口声明的返回类型

---

## 6. JDK `Proxy.newProxyInstance` 内部如何实现

`Proxy.newProxyInstance(loader, interfaces, handler)` 属于 JDK（`java.lang.reflect.Proxy`），核心过程是：

1. **参数校验**
   - `handler != null`
   - `interfaces` 非空
   - 每个都是 interface、无重复、对 classloader 可见
   - 权限/模块访问检查（JDK 9+ 更严格）

2. **查缓存：是否已为这组接口生成代理类**
   - 典型 key：`(ClassLoader, interfaces列表)`  
   - 命中则复用代理类，避免重复生成字节码

3. **未命中则生成代理类字节码**
   - 生成一个类名类似：
     - 旧：`com.sun.proxy.$Proxy0`
     - 新：`jdk.proxy1.$Proxy0`（取决于 JDK 版本）
   - 生成的类：
     - `extends java.lang.reflect.Proxy`
     - `implements interfaces...`
   - 为每个接口方法生成 override 方法，方法体大体是：
     - 组装 `Object[] args`
     - 调用 `h.invoke(this, method, args)`
     - 返回值强转/拆箱
     - 异常按受检异常规则处理（必要时包装 `UndeclaredThrowableException`）

4. **定义并加载该代理类**
   - 通过 JDK 内部机制 define class（实现细节随 JDK 版本变化）

5. **构造实例并返回**
   - 代理类通常有构造器：`(InvocationHandler h)`，内部 `super(h)`
   - `newInstance(handler)` 得到代理对象

### 6.1 代理类的典型形态（伪代码）

```java
public final class $Proxy0 extends Proxy implements IFoo, IBar {
  private static Method m1; // IFoo.someMethod
  static {
    m1 = IFoo.class.getMethod("someMethod", String.class);
  }

  public $Proxy0(InvocationHandler h) { super(h); }

  @Override
  public String someMethod(String x) {
    try {
      return (String) this.h.invoke(this, m1, new Object[]{x});
    } catch (RuntimeException | Error e) {
      throw e;
    } catch (Throwable t) {
      throw new UndeclaredThrowableException(t);
    }
  }
}
```

### 6.2 为什么只能代理接口

因为生成的代理类是：

- `extends Proxy`（单继承）
- `implements 接口列表`

它不会 `extends` 你的具体类。要代理类一般用 CGLIB/ByteBuddy（子类化/字节码增强）等机制。

---

## 7. 总结：Retrofit 与 Proxy 的分工

- **Proxy.newProxyInstance**：负责“生成一个实现接口的对象”，并把所有方法调用统一转发给 `InvocationHandler`
- **Retrofit InvocationHandler**：负责“把 method + 注解 + args 解释成 HTTP 请求”，并通过 `RequestFactory`/`OkHttpCall`/`Converter`/`CallAdapter` 完成执行与返回值适配

---