# OkHttpClient Cache 最佳实践（Android）

本文总结 OkHttp 的 HTTP 缓存原理与 Android 落地最佳实践，并整合了本次对话中关于 `Cache-Control`、离线缓存、风险分级、以及“资源型下载应独立管理”的结论。

---

## 1. 先明确：OkHttp Cache 是“HTTP 语义缓存”，不是业务缓存

OkHttp 的 `Cache`（磁盘缓存）遵循 HTTP 缓存规范：依据 **请求/响应头**（如 `Cache-Control`、`Expires`、`ETag`、`Last-Modified`、`Vary`）决定：

- **强缓存命中**：缓存仍新鲜（fresh），直接返回缓存，不走网络。
- **协商缓存**：缓存过期但可验证（带 `If-None-Match`/`If-Modified-Since`），服务器返回 **304** 则复用缓存体。
- **不使用缓存**：如 `no-store`、请求禁止、不可缓存等。

> 结论：OkHttp Cache 适合“低风险、资源型、可容忍短时不一致”的数据；强一致/高风险数据更适合业务层显式缓存或直接禁用网络缓存。

---

## 2. Cache-Control 关键解读（与 OkHttp 行为强相关）

### 2.1 响应头（Response）更关键
能否缓存、缓存多久，主要由 **服务端响应头**决定：
- `Cache-Control: public, max-age=60`：允许缓存，60 秒内新鲜（强缓存）。
- `Cache-Control: no-cache`：可以缓存，但每次使用前必须向服务器验证（可能 304）。
- `Cache-Control: no-store`：完全禁止缓存（不存、不用）。
- `s-maxage`：只对共享缓存（CDN/代理）生效。

### 2.2 请求头（Request）通常是“偏好/约束”
例如 `Cache-Control: max-age=60` 表示“客户端只接受 60 秒内的新鲜缓存”；但它 **不能把服务端不允许缓存的响应变成可缓存**。

---

## 3. 关于“先缓存了 public，再次请求改 no-store，会用旧缓存吗？”

**通常不会。**

如果第二次请求携带 `Cache-Control: no-store`（请求指令），OkHttp 会：
- 禁止使用已有缓存（不走强缓存命中，也不会用缓存去做协商验证）。
- 同时也禁止存储本次响应。

> `no-store` 与 `no-cache` 不同：  
> - `no-cache` 是“用之前必须验证”（可 304）；  
> - `no-store` 是“完全不要缓存”。

---

## 4. Android 启用 OkHttp 磁盘缓存（必做）

### 4.1 基础配置
- 缓存目录建议：`context.cacheDir/http_cache`
- 缓存大小：按业务与磁盘空间评估（常见 10MB~100MB）

```kotlin name=OkHttpCacheSetup.kt
import android.content.Context
import okhttp3.Cache
import okhttp3.OkHttpClient
import java.io.File

fun newOkHttpClient(context: Context): OkHttpClient {
    val cacheDir = File(context.cacheDir, "http_cache")
    val cacheSize = 50L * 1024L * 1024L // 50 MiB
    val cache = Cache(cacheDir, cacheSize)

    return OkHttpClient.Builder()
        .cache(cache)
        .build()
}
```

### 4.2 如何判断是否命中缓存
可以从 `Response` 判断这次响应来自哪里（缓存/网络/两者结合）：

- `response.cacheResponse()`：不为空说明使用了缓存（命中强缓存或复用了缓存体）
- `response.networkResponse()`：不为空说明走了网络
- 两者都不为空：常见于 **304 Not Modified** 场景（发起了网络验证，但响应体来自本地缓存）

实务建议：
- 在调试日志/埋点中记录：URL、状态码、`cacheResponse != null`、`networkResponse != null`，方便定位“为什么没命中/为什么走了网络”。

```kotlin name=OkHttpCacheHitCheck.kt
val response = client.newCall(request).execute()

val fromCache = response.cacheResponse != null
val fromNetwork = response.networkResponse != null

// 304 场景通常会出现：fromCache == true && fromNetwork == true
println("fromCache=$fromCache, fromNetwork=$fromNetwork, code=${response.code}")
```

---

## 5. 离线缓存最佳实践（only-if-cached）

### 5.1 `only-if-cached` 是否只在无网时加？
**是的**，推荐仅在“离线模式/无网络”时添加。原因：
- 在线时加 `only-if-cached`：没缓存会直接失败（通常 504），即使网络可用。
- 离线时加 `only-if-cached`：明确告诉 OkHttp 不要走网络，只读本地缓存兜底。

### 5.2 离线拦截器（只对 GET 生效）
离线时：
- `only-if-cached`：只用缓存
- `max-stale`：允许使用过期缓存多久（如 7 天）

```kotlin name=OfflineCacheInterceptor.kt
import okhttp3.Interceptor
import okhttp3.Response

class OfflineCacheInterceptor(
    private val isNetworkAvailable: () -> Boolean,
    private val maxStaleSeconds: Int = 7 * 24 * 60 * 60 // 7天
) : Interceptor {

    override fun intercept(chain: Interceptor.Chain): Response {
        var request = chain.request()

        if (!isNetworkAvailable() && request.method == "GET") {
            request = request.newBuilder()
                .header("Cache-Control", "public, only-if-cached, max-stale=$maxStaleSeconds")
                .build()
        }
        return chain.proceed(request)
    }
}
```

> 离线无缓存时：常见返回 504（only-if-cached 不可满足）。上层应将其转为“离线且无缓存”的 UI/错误码。

### 5.3 网络状态判断（示例）
```kotlin name=NetworkAvailable.kt
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities

fun Context.isNetworkAvailable(): Boolean {
    val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val network = cm.activeNetwork ?: return false
    val caps = cm.getNetworkCapabilities(network) ?: return false
    return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
}
```

---

## 6. “我在请求里 addHeader(Cache-Control: public, max-age=60)”为什么不一定生效？

因为缓存是否写入/可复用主要由 **响应头**决定。把 `Cache-Control: public, max-age=60` 加在请求上，通常只表示“客户端偏好”，并不保证：
- 服务端响应允许缓存
- OkHttp 会将响应落盘

### 6.1 如果后端没给缓存头，但你想让 GET 缓存 60 秒
应在 **Network Interceptor** 修改响应头（谨慎使用，务必做白名单，避免缓存高风险数据）：

```kotlin name=ForceCacheResponseInterceptor.kt
import okhttp3.Interceptor
import okhttp3.Response

class ForceCacheResponseInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val response = chain.proceed(chain.request())
        return response.newBuilder()
            .header("Cache-Control", "public, max-age=60")
            .build()
    }
}
```

并将其添加为 `addNetworkInterceptor(...)`（仅在走网络时触发；强缓存命中时不会触发）。

---

## 7. 缓存策略选型：网络层缓存 vs 业务层缓存（风险分级结论）

### 7.1 网络层缓存（OkHttp Cache）的定位
- 适合：**低风险数据**、资源型数据、可容忍短时不一致的数据
- 优点：遵循 HTTP 标准（max-age / ETag / 304 / Vary），成本低
- 风险：业务可能“无感命中缓存”拿到脏数据

> 结论（对话共识）：网络层缓存应只用于低风险数据；对高风险请求禁用或仅协商验证。

### 7.2 业务层缓存的定位
- 适合：**高风险/强一致**业务数据（订单/资产/权限/关键状态等），或需要复杂的失效/版本控制
- 优点：业务语义可控、可观测、可治理
- 成本：需要自己做版本、TTL、并发、清理、落盘与迁移等

---

## 8. 资源型请求：建议独立“资源管理/下载工具”，不要混入 Retrofit API 通道

对话结论：资源性质请求更适合单独的资源管理工具（而不是 Retrofit），因为资源下载更关注：
- 文件落盘与原子性
- 断点续传/重试/并发队列
- 校验（hash/签名/size）
- 多版本共存与清理策略

### 8.1 资源唯一性（Identity）是资源管理工具的核心
推荐同时具备两类唯一性：
- **内容唯一（强唯一）**：hash（如 sha256）或可靠 ETag，用于去重与正确性
- **业务唯一（稳定指向）**：resourceId/module+version，用于发布、灰度、回滚

> 不建议只用 URL 作为唯一键：URL 可能复用但内容更新，会导致缓存污染。

---

## 9. 实战清单（Checklist）

- [ ] OkHttpClient 配置了 `.cache(Cache(...))`
- [ ] 离线策略仅在无网时加 `only-if-cached` + `max-stale`
- [ ] 高风险接口默认禁用网络缓存（`no-store` 或至少 `no-cache`）
- [ ] 低风险接口优先让服务端返回正确的 `Cache-Control/ETag`
- [ ] 如需客户端“补缓存头”，必须做 **白名单** 且在 **network interceptor** 修改响应
- [ ] 资源下载使用独立资源管理工具（内容 hash + 业务 key），不要依赖 OkHttp Cache 作为资源仓库

---

## 10. 推荐的默认策略（可作为团队规范）

1. **API（高风险）**：`no-store`（或 `no-cache` + ETag）  
2. **API（低风险）**：允许短 TTL 强缓存或协商缓存（由服务端声明）  
3. **离线**：对明确允许离线的 GET，启用 `only-if-cached` + `max-stale`  
4. **资源**：独立下载与文件缓存体系（hash/版本），避免混用 HTTP cache

---