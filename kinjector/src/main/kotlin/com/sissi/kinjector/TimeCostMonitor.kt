package com.sissi.kinjector

import java.lang.RuntimeException

/**
 *（方法执行）耗时监控器
 */
abstract class TimeCostMonitor {

    /**
     * 是否启用该功能。默认关闭
     */
    var enable = false

    /**
     * 项目中所有方法。包括源码中的和库中的
     */
    val SCOPE_ALL = "ALL"
    /**
     * 仅库中的方法。包括libs目录下的以及外部引入的
     */
    @Deprecated("")
    val SCOPE_LIB = "LIB"
    /**
     * 仅项目源码中的方法
     */
    val SCOPE_SOURCE = "SOURCE"

    private val scopeSet = setOf(SCOPE_ALL, SCOPE_LIB, SCOPE_SOURCE)

    /**
     * 监控范围。
     * 可取值预定义的[SCOPE_ALL],[SCOPE_LIB],[SCOPE_SOURCE]，
     * 或者自定义，以包名为单位，多个包以空白字符为分隔符。如
     * scope = "com.kedacom.sdk.startup com.kedacom.sdk.login"
     * scope = "com.kedacom.sdk.startup
     *          com.kedacom.sdk.login"
     */
    var scope = SCOPE_SOURCE

    /**
     * 监控条件，仅满足该条件的方法才被监控。
     * 默认无条件，即监控范围内所有方法。
     * 该条件为一段代码完备的表达式。
     */
    var condition="true"

    /**
     * 耗时阈值（单位：毫秒）。
     * 耗时达到或超出该阈值时执行指定的行为[actionWhenReachLimit]。
     * 默认没有阈值。
     */
    var timeLimit=1000*1000

    /**
     * 打日志
     */
    val ACTION_LOG = "LOG"

    /**
     * 让程序崩溃
     */
    val ACTION_CRASH = "CRASH"

    private val actionSet = setOf(ACTION_LOG, ACTION_CRASH)

    /**
     * 耗时达到或超出该阈值时指定的行为
     * 可取值[ACTION_LOG],[ACTION_CRASH]，默认崩溃
     */
    var actionWhenReachLimit = ACTION_CRASH

    internal fun checkScope(){
        if (scope !in scopeSet && parsePackageScopes().isEmpty()){
            throw RuntimeException("invalid scope: $scope")
        }
    }

    internal fun parsePackageScopes() : List<String>{
        return if (scope !in scopeSet){
            scope.split(Regex("\\s+")).filter { it.trim().isNotEmpty() }
        }else{
            emptyList()
        }
    }

    internal fun checkAction(){
        if (actionWhenReachLimit !in actionSet){
            throw RuntimeException("invalid action: $actionWhenReachLimit")
        }
    }

    internal fun check(){
        checkScope()
        checkAction()
    }

}
