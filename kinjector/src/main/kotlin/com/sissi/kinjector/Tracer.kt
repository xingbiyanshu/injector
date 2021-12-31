package com.sissi.kinjector

import org.gradle.api.Action

/**
 * 用于记录方法调用
 */
open class Tracer {
    /**
     * 是否启用
     */
    var enable = false

    /**
     * 用于日志打印的类
     * 默认为[android.util.Log]
     */
    var logger = ""

    /**
     * 日志打印类中用于打印的方法
     * 仅需方法名
     * 该方法需符合原型 func(String format, Object[] paras)
     * 默认使用android.util.Log#d打印
     */
    var logMethod = ""

    /**
     * 目标范围
     */
    private val scopes = HashSet<Scope>()

    /**
     * 添加目标范围
     */
    fun scope(action: Action<in Scope>){
        val scope = Scope()
        action.execute(scope)
        scopes.add(scope)
    }

}


class Scope{
    /**
     * 是否启用
     */
    var enable = true

    /**
     * 可见性修饰符
     * */
    val PUBLIC      = 0b0001
    val PROTECTED   = 0b0010
    val INTERNAL    = 0b0100
    val PRIVATE     = 0b1000
    val ALL         = 0b1111


    val ALL_SOURCE = "ALL_SOURCE"
    /**
     * 目标类
     * 若为[ALL_SOURCE]表示项目中所有源码中（不包括引用的库）的所有类
     * 或者可以自定义，支持正则表达式，多个项以空白字符为分隔符。如
     * classes = "com.kedacom.sdk.startup.* com.kedacom.sdk.login.*"
     * classes = "com.kedacom.sdk.startup.*
     *          com.kedacom.sdk.login.*"
     */
    var classes= ""

    var classModifier = ALL

    var excludedClasses = ""

    var methodModifier = PUBLIC

    /**
     * 排除的方法
     * 若为空则不排除任何方法
     * 方法只需包含方法名字和形参列表，多个方法空白字符分隔，例如：
     * excludeMethods = """print(String, Object[])
     *      print(Int, String, Object[])
     */
    var excludeMethods=""


    internal fun parseClasses() : List<String>{
        return if (classes == ALL_SOURCE){
            listOf(ALL_SOURCE)
        }else{
            classes.split(Regex("\\s+")).filter { it.trim().isNotEmpty() }
        }
    }

    internal fun parseExcludeMethods() : List<String>{
        return excludeMethods.split(Regex("\\s+")).filter { it.trim().isNotEmpty() }
    }

}