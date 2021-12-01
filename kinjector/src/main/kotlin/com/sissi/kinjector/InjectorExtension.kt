package com.sissi.kinjector

import org.gradle.api.provider.Property

interface InjectorExtension {
    val clz: Property<String>
    val method: Property<String>
    val body: Property<String>
}