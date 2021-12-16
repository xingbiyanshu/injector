package com.sissi.kinjector

abstract class Ambulance {
    /**
     * 是否启用
     */
    var enable = false

    /**
     * 目标类
     */
    var targetClass= ""

    /**
     * 目标方法
     */
    var targetMethod=""

    /**
     * 修复代码
     */
    var repairCode=""

    val POS_INSERT_BEGIN = -1
    val POS_INSERT_END = -2
    val POS_REPLACE_BODY = -3

    /**
     * 注入位置
     */
    var position=POS_REPLACE_BODY
}