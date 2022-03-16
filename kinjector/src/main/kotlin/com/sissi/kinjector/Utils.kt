package com.sissi.kinjector

internal object Utils {
    private val red = "\u001b[31m"
    private val green = "\u001b[32m"
    private val yellow = "\u001b[33m"
    private val blue = "\u001b[34m"

    fun strDebug(str:String) = "${blue}$str"
    fun strInfo(str:String) = "${green}$str"
    fun strWarn(str:String) = "${yellow}$str"
    fun strError(str:String) = "${red}$str"
}