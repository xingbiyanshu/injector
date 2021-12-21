package com.sissi.kinjector

import org.gradle.api.Action
import java.lang.RuntimeException

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
        println(toString())
        patients.forEach { it.check() }
    }

    override fun toString(): String {
        return "Ambulance(enable=$enable, patients=$patients)"
    }

}


open class Patient {
    internal val focusSet = HashSet<Focus>()

    fun focus(action: Action<in Focus>){
        val focus = Focus()
        action.execute(focus)
        focusSet.add(focus)
    }

    internal fun check(){
        focusSet.forEach { it.check() }
    }

    override fun toString(): String {
        return "Patient(focusSet=$focusSet)"
    }

}


open class Focus{
    /**
     * 目标类
     */
    var claz= ""

    /**
     * 目标方法
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

    override fun toString(): String {
        return "Focus(claz='$claz', method='$method', repairCode='$repairCode', position=$position)"
    }

}