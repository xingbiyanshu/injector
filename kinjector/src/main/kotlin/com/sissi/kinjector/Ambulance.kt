package com.sissi.kinjector

import org.gradle.api.Action

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
        return patients.flatMap {
            it.focusSet
        }
    }
}


open class Patient {
    internal val focusSet = HashSet<Focus>()

    fun focus(action: Action<in Focus>){
        val focus = Focus()
        action.execute(focus)
        focusSet.add(focus)
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

    /**
     * 注入位置
     */
    var position=POS_REPLACE_BODY
}