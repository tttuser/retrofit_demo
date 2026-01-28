# Retrofit 源码阅读笔记（建议边跑测试边读）
> 每次读源码只回答一个问题，并用单测/日志验证。

## 入口 1：create 到代理对象（L3-1）
- 观察：service 实例的 class 是否是 Proxy
- 源码跟踪：Retrofit.create -> InvocationHandler.invoke

## 入口 2：方法解析与缓存（L3-2 / L4-4）
- 问题：注解解析发生在什么时候？第一次调用才解析吗？
- 验证：Day8 的 first call slow test

## 入口 3：请求构建（L3-3）
- 问题：Path、Query、Header、Body 如何拼进 Request？
- 验证：request_shape_path_and_query

## 入口 4：Converter（L3-4 / L4-6）
- 问题：ResponseBody 谁来读？谁来 close？
- 验证：写一个自定义 Converter 做对照实验

## 入口 5：CallAdapter（L3-5 / L5-3 / L4-2）
- 问题：为什么返回类型决定了适配器？泛型如何提取？
- 验证：custom_call_adapter_wraps_http_error

## 入口 6：错误边界（L3-7 / L4-7）
- 问题：HTTP 非 2xx 何时抛异常？网络错误是如何进入 onFailure？
- 验证：non_2xx_is_not_exception_when_returning_Response