package com.sissi.kinjector

/**
 * 方法执行耗时监控
 */
abstract class MethodTimeCostMonitor {

    /**
     * 是否启用该功能。默认关闭
     */
    var enable: Boolean = false

    /**
     * 项目中所有方法。包括源码中的和库中的
     */
    val SCOPE_ALL = "ALL"
    /**
     * 仅库中的方法。包括libs目录下的以及外部引入的
     */
    val SCOPE_LIB = "LIB"
    /**
     * 仅项目源码中的方法
     */
    val SCOPE_SOURCE = "SOURCE"

    /**
     * 监控范围。
     * 可取值预定义的[SCOPE_ALL],[SCOPE_LIB],[SCOPE_SOURCE]，
     * 或者自定义，以包名为单位，多个包以";"为分隔符。如
     * scope = "com.kedacom.sdk.startup;com.kedacom.sdk.login"
     */
    var scope: String = SCOPE_SOURCE

    /**
     * 监控条件，仅满足该条件的方法才被监控。
     * 默认无条件，即监控范围内所有方法。
     * 该条件为一段代码完备的表达式。
     */
    var condition: String="true"

    /**
     * 耗时阈值（单位：毫秒）。
     * 耗时达到或超出该阈值时执行指定的行为[actionWhenReachLimit]。
     * 默认没有阈值。
     */
    var timeLimit: Int=1000*1000

    /**
     * 打日志
     */
    val ACTION_LOG = "LOG"

    /**
     * 让程序崩溃
     */
    val ACTION_CRASH = "CRASH"

    /**
     * 耗时达到或超出该阈值时指定的行为
     * 可取值[ACTION_LOG],[ACTION_CRASH]，默认崩溃
     */
    var actionWhenReachLimit = ACTION_CRASH

}
