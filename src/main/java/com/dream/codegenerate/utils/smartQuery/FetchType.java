package com.dream.codegenerate.utils.smartQuery;

/**
 * 抓取策略枚举
 */
public enum FetchType {
    /**
     * 自动策略 (默认)
     * 规则：
     * 1. 1:1 关联 -> EAGER (Join)
     * 2. 第一层 1:N 集合 -> EAGER (Join)
     * 3. 第二层及更深层 1:N 集合 -> LAZY (分步查询，防笛卡尔积)
     */
    AUTO,

    /**
     * 贪婪模式 (强制 Join)
     * 无论嵌套多深，都使用 Left Join 一次性拉取。
     * 慎用：在多层 1:N 时会导致结果集爆炸。
     */
    EAGER,

    /**
     * 懒加载模式 (强制分步)
     * 强制将该节点拆分为第二条 SQL 查询。
     * 使用 "WHERE foreign_key IN (...)" 批量抓取并组装。
     */
    LAZY
}
