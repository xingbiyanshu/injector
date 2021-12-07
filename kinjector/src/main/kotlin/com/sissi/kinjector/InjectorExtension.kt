package com.sissi.kinjector

import org.gradle.api.provider.Property

abstract class InjectorExtension {
    val METHOD_BEGIN = -1
    val METHOD_END = -2
    abstract val clz: Property<String>
    abstract val method: Property<String>
    abstract val body: Property<String>
    abstract val at: Property<Int>
}