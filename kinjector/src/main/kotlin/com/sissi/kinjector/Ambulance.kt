package com.sissi.kinjector

import org.gradle.api.Action
import java.lang.RuntimeException

/**
 * 用于不改变问题源码的情况下修复bug
 */
open class Ambulance {
    /**
     * 是否启用
     */
    var enable = false

    internal val patients = HashSet<Patient>()

    fun patient(action: Action<in Patient>){
        val patient = Patient()
        action.execute(patient)
        patients.add(patient)
    }

    fun getFocusList(clz:String):List<Focus>{
        return patients.flatMap {it.focusSet}.filter { it.claz == clz }
    }

    internal fun check(){
        patients.forEach { it.check() }
    }

}

/**
 * 对应一个bug
 */
open class Patient {
    /**
     * 是否启用
     */
    var enable = true

    var name=""

    internal val focusSet = HashSet<Focus>()

    fun focus(action: Action<in Focus>){
        val focus = Focus()
        focus.patient = name
        action.execute(focus)
        focusSet.add(focus)
    }

    internal fun check(){
        focusSet.forEach { it.check() }
    }

}


/**
 * 对应一个修改点
 */
open class Focus{
    /**
     * 是否启用
     */
    var enable = true

    var name=""

    internal var patient=""

    /**
     * 目标类
     */
    var claz= ""

    /**
     * 新增字段
     */
    var newFields:List<String>? = null

    /**
     * 新增方法
     */
    var newMethods:List<String>? = null


    /**
     * 目标方法
     * 如果目标方法为空，则表示修复策略是新增方法
     */
    var method=""

    /**
     * 修复代码
     */
    var repairCode=""

    val POS_INSERT_BEGIN = -1
    val POS_INSERT_END = -2
    val POS_REPLACE_BODY = -3

    private val posSet = setOf(POS_INSERT_BEGIN, POS_INSERT_END, POS_REPLACE_BODY)

    /**
     * 注入位置
     */
    var position=POS_REPLACE_BODY

    internal fun methodName() = method.substringBefore("(")

    internal fun methodParas()= method.substring(method.indexOfFirst { it=='(' }+1, method.indexOfLast { it==')' })
        .split(Regex(",\\s*")).filter { it.trim().isNotEmpty() }

    internal fun check(){
        if (position !in posSet && position<0){
            throw RuntimeException("invalid position: $position")
        }
    }

}