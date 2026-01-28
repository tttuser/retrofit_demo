# Retrofit 核心对象职责图谱：2 张“可落地”图（Mermaid）

> 使用 Mermaid：可直接粘贴到支持 Mermaid 的 Markdown 渲染器（GitHub、语雀、Obsidian、Notion 部分模式、Mermaid Live Editor 等）。

---

## 图 1：静态职责/对象关系图（Class / Dependency Map）

```mermaid
classDiagram
direction LR

class OkHttpClient {
  +interceptors
  +connectionPool
  +dispatcher
  +timeouts
  +cache
}

class Retrofit {
  +HttpUrl baseUrl
  +CallFactory callFactory
  +List~ConverterFactory~ converterFactories
  +List~CallAdapterFactory~ callAdapterFactories
  +Map~Method, ServiceMethod~ serviceMethodCache
  +create(Service): Service
}

class ProxyService {
  <<dynamic proxy>>
  +invoke(Method, args)
}

class ServiceMethod {
  <<cached per Method>>
  +invoke(args)
}

class HttpServiceMethod {
  +RequestFactory requestFactory
  +CallAdapter callAdapter
  +Converter responseConverter
}

class RequestFactory {
  +HttpMethod method
  +String relativeUrl
  +Headers headers
  +ParameterHandler[] handlers
  +create(args): Request
}

class ParameterHandler {
  +apply(builder, arg)
}

class OkHttpCall {
  +Request request
  +createRawCall(): okhttp3.Call
  +execute()
  +enqueue()
  +cancel()
}

class OkHttpCallFactory {
  <<Call.Factory>>
  +newCall(Request): okhttp3.Call
}

class okhttp3Call {
  <<okhttp3.Call>>
  +execute()
  +enqueue()
  +cancel()
}

class ConverterFactory {
  +responseBodyConverter(...)
  +requestBodyConverter(...)
}

class Converter {
  +convert(...)
}

class CallAdapterFactory {
  +get(returnType,...)
}

class CallAdapter {
  +adapt(OkHttpCall): ReturnType
}

Retrofit --> OkHttpCallFactory : callFactory
OkHttpClient ..|> OkHttpCallFactory : implements

Retrofit --> ConverterFactory : holds list
Retrofit --> CallAdapterFactory : holds list
Retrofit --> ServiceMethod : caches per Method

Retrofit --> ProxyService : create()
ProxyService --> ServiceMethod : lookup/parse per Method
ServiceMethod <|-- HttpServiceMethod

HttpServiceMethod --> RequestFactory
RequestFactory --> ParameterHandler : handlers[*]
HttpServiceMethod --> CallAdapter
HttpServiceMethod --> Converter : responseConverter

HttpServiceMethod --> OkHttpCall : creates per invocation
OkHttpCall --> OkHttpCallFactory : newCall(Request)
OkHttpCall --> okhttp3Call : rawCall
```

---

## 图 2：动态创建时机/调用时序图（Lifecycle + Cache + Per-call）

```mermaid
sequenceDiagram
autonumber
participant App as App/DI
participant OC as OkHttpClient
participant R as Retrofit
participant S as Service Proxy(Api)
participant Cache as serviceMethodCache
participant SM as ServiceMethod(HttpServiceMethod)
participant RF as RequestFactory/ParameterHandlers
participant OHC as OkHttpCall
participant C as okhttp3.Call
participant Net as Network
participant Conv as Converter
participant CA as CallAdapter

rect rgba(230, 245, 255, 0.6)
note over App,R: A. 初始化阶段（通常一次或少量）
App->>OC: new OkHttpClient()
App->>R: new Retrofit(baseUrl, OC, factories...)
end

rect rgba(235, 255, 235, 0.6)
note over App,S: B. create(Api)（每个接口一次）
App->>R: create(Api.class)
R-->>S: return dynamic proxy
end

rect rgba(255, 245, 230, 0.6)
note over S,SM: C. 首次调用某 Method（解析并缓存）
App->>S: api.getUser(id)
S->>Cache: get(Method)
alt cache miss
  Cache-->>S: null
  S->>R: parse annotations/returnType
  R->>RF: build RequestFactory + ParameterHandlers
  R->>CA: pick CallAdapter (from factories)
  R->>Conv: pick Converter (from factories)
  R-->>SM: new HttpServiceMethod(RF, CA, Conv)
  S->>Cache: put(Method, SM)
else cache hit
  Cache-->>S: SM
end
end

rect rgba(255, 235, 245, 0.6)
note over SM,Net: D. 每次调用（都会新建 call/request）
S->>SM: invoke(args)
SM->>RF: create(Request) using args
SM->>OHC: new OkHttpCall(Request, callFactory, Conv)
SM->>CA: adapt(OHC) -> returnType

App->>OHC: execute()/enqueue()/await...
OHC->>C: callFactory.newCall(Request) (lazy create)
OHC->>Net: execute/enqueue
Net-->>C: response
C-->>OHC: ResponseBody
OHC->>Conv: convert(ResponseBody) -> T
OHC-->>App: T / failure
end
```

---
## 使用说明
- **图 1**：用于“职责/依赖/对象边界”讨论（架构评审最常用）。
- **图 2**：用于“性能/创建次数/缓存命中/调用链路”讨论（定位慢启动与首次调用开销）。

如果你希望我按你们的技术栈把图里的返回类型细化（例如 `suspend`、`Flow`、RxJava2/3、Kotlin Serialization、Moshi、Gson），告诉我你使用的组合，我可以把 `CallAdapter/Converter` 分支在图上展开。