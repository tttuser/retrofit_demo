# HTTP `Cache-Control` 解读（速查文档）

`Cache-Control` 是 HTTP 缓存控制的核心头字段，用来指导 **浏览器（私有缓存）**、**代理/CDN（共享缓存）** 如何缓存与复用资源。它既可出现在 **请求头**（Request）也可出现在 **响应头**（Response），但多数“能否缓存/缓存多久”的决定性信息来自 **响应头**。

---

## 1. 缓存参与方与基本概念

### 1.1 私有缓存 vs 共享缓存
- **私有缓存（private cache）**：通常指浏览器或客户端本地缓存（每个用户单独一份）。
- **共享缓存（shared cache）**：代理缓存、网关、CDN（多个用户可能共享同一份缓存）。

### 1.2 新鲜度（Freshness）与验证（Validation）
- **新鲜（fresh）**：资源在有效期内，可直接使用缓存，不请求源站（常称“强缓存”）。
- **过期（stale）**：超过有效期，通常需要向源站 **重新验证**（revalidate）。
- **条件请求（Conditional Request）**：携带 `If-None-Match`（ETag）或 `If-Modified-Since`（Last-Modified）去验证；若源站返回 **304**，可复用缓存响应体（常称“协商缓存/对比缓存”）。

---

## 2. 响应头（Response）常用指令：决定“能不能存、存多久、怎么用”

### 2.1 可缓存范围
- `public`
  - 含义：允许 **共享缓存**（CDN/代理）缓存该响应。
  - 典型：静态资源（JS/CSS/图片）可 `public`。
- `private`
  - 含义：只允许 **私有缓存**（客户端/浏览器）缓存；共享缓存不应缓存。
  - 典型：包含用户个性化信息的页面/API。
- `no-store`
  - 含义：**完全禁止缓存**（不应保存响应的任何副本）。
  - 典型：敏感信息（账户资料、支付结果页、一次性验证码等）。

### 2.2 新鲜度（有效期）
- `max-age=<seconds>`
  - 含义：从响应生成时间起，`<seconds>` 秒内为新鲜。
  - 示例：`Cache-Control: public, max-age=60` 表示 60 秒强缓存。
- `s-maxage=<seconds>`
  - 含义：仅对 **共享缓存**生效；覆盖共享缓存对 `max-age` 的使用。
  - 典型：希望 CDN 缓存 10 分钟，但浏览器只缓存 1 分钟。

### 2.3 过期后的处理（验证）
- `must-revalidate`
  - 含义：一旦过期，缓存 **必须** 向源站验证，不能直接使用过期副本。
- `proxy-revalidate`
  - 含义：类似 `must-revalidate`，主要约束共享缓存。

### 2.4 允许使用“过期副本”的扩展能力（常见于 CDN）
- `stale-while-revalidate=<seconds>`
  - 含义：过期后的一段时间内，允许先用旧缓存，同时后台异步刷新。
  - 目的：提升体验，降低尾延迟。
- `stale-if-error=<seconds>`
  - 含义：源站出错（如 5xx、超时）时，允许用旧缓存兜底一段时间。
  - 目的：容灾与可用性。

### 2.5 其���常见
- `immutable`
  - 含义：在有效期内资源不会变，客户端可避免不必要的重新验证。
  - 典型：带内容哈希的静态资源，如 `app.8f3a1c.js`。
- `no-cache`
  - **注意**：不是“不缓存”。
  - 含义：可以缓存，但每次使用前必须向源站重新验证（通常会走 304）。

---

## 3. 请求头（Request）常用指令：决定“要不要用缓存、怎么用缓存”

请求里的 `Cache-Control` 更像是客户端对缓存的“偏好/约束”，不一定能改变响应是否可缓存（响应的缓存策略仍以服务器响应头为主）。

- `no-cache`
  - 含义：客户端要求缓存必须验证后才能使用（倾向触发条件请求）。
- `no-store`
  - 含义：客户端要求不要存，也不要用本地缓存副本。
- `max-age=<seconds>`
  - 含义：客户端只接受“年龄”不超过 `<seconds>` 的缓存；否则需要验证/走网络。
- `max-stale[=<seconds>]`
  - 含义：客户端愿意接受过期缓存；可选指定最多接受过期多久。
- `min-fresh=<seconds>`
  - 含义：客户端希望拿到至少还能再新鲜 `<seconds>` 的响应（否则宁愿验证/重取）。
- `only-if-cached`
  - 含义：只使用缓存，不允许走网络；若无可用缓存通常返回 504（实现相关）。

---

## 4. 与 `ETag/Last-Modified/Expires` 的关系

- `ETag` / `Last-Modified`
  - 用于 **验证**：配合请求头 `If-None-Match` / `If-Modified-Since`，实现 304。
- `Expires`
  - 老式“绝对时间”过期机制；可与 `Cache-Control: max-age` 并存。
  - 一般以 `Cache-Control` 为准（更灵活、优先级更高）。
- `Date` / `Age`
  - 帮助缓存系统计算资源当前“年龄”和新鲜度。

---

## 5. 典型策略模板（可直接抄）

### 5.1 强缓存静态资源（推荐：带 hash 的文件名）
```
Cache-Control: public, max-age=31536000, immutable
```
适用：`/assets/app.8f3a1c.js`、`style.a1b2c3.css`

### 5.2 短时间缓存（接口/页面允许短暂过期）
```
Cache-Control: public, max-age=60
```

### 5.3 可缓存但每次必须验证（希望 304、避免传输 body）
```
Cache-Control: no-cache
```

### 5.4 完全禁止缓存（敏感数据）
```
Cache-Control: no-store
```

### 5.5 个性化内容（不让 CDN 缓存）
```
Cache-Control: private, max-age=0, must-revalidate
```
（或 `private, no-cache`，效果接近：每次使用前验证）

### 5.6 CDN 与浏览器分层缓存
```
Cache-Control: public, max-age=60, s-maxage=600
```
含义：浏览器最多 60s；CDN 最多 600s。

---

## 6. 读懂一行 `Cache-Control` 的快速步骤

1. 看有没有 **禁止缓存**：`no-store`（最强，基本一票否决）
2. 看是否要求 **每次验证**：`no-cache`
3. 看 **新鲜度**：`max-age`（以及共享缓存用的 `s-maxage`）
4. 看 **缓存范围**：`public` / `private`
5. 看过期后行为：`must-revalidate`、`stale-while-revalidate`、`stale-if-error`

---

## 7. 常见误区

- `no-cache` ≠ 不缓存  
  它是“可以缓存，但用之前必须验证”。
- 想让客户端缓存，**主要靠响应头**  
  请求头里的 `Cache-Control` 多数只能表达客户端意愿，不能强行改变服务器策略。
- `public/private` 主要是 **响应指令**  
  在请求里写 `public` 通常意义不大。

---