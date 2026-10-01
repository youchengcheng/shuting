package com.quanxiaoha.framework.biz.idempotent.enums;

/**
 * 幂等策略
 */
public enum IdempotentPolicyEnum {

    /**
     * 令牌策略：业务请求头携带 Idempotent-Token，服务端基于 Redis 单条 DEL 命令原子消费，
     * 删除成功即为首次请求（放行），删除失败即为重复请求（拒绝）
     */
    TOKEN,

    /**
     * 参数策略：基于 SpEL 表达式（如手机号）生成幂等 Key，服务端基于 Redis 单条 SET NX EX 命令
     * 原子占位，占位成功即为窗口期内首次请求（放行），占位失败即为重复请求（拒绝）
     */
    PARAM

}
