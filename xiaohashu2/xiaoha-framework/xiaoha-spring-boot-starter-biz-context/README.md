# 内部接口凭证

该组件自动注册 servlet 过滤器，保护 `/internal` 和 `/internal/**`。服务端和调用方使用相同的 `INTERNAL_API_TOKEN` 环境变量，也可以在受保护的外部配置中设置 `internal.api.token=${INTERNAL_API_TOKEN:}`。

未配置凭证时内部接口返回 503，缺失、错误或重复凭证返回 401。配置值不应写入源码、日志或提交到仓库。普通 HTTP 接口不受该凭证过滤器限制。

存在 Feign 时，组件只为 `/internal` 和 `/internal/**` 请求添加 `X-Internal-Token`；其他请求移除该头。内部调用未配置凭证会在发送前失败。`feign-core` 是可选依赖，使用方应由自身的 OpenFeign starter 提供运行时依赖。

业务服务仍需由受信网络入口保护，网关必须清除外部 `userId` 与 `X-Internal-Token`。本凭证只授予内部元数据访问权，不替代业务接口的用户鉴权。
