package com.sissi.kinjector

import com.android.build.gradle.BaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project

class KInjector : Plugin<Project> {
    override fun apply(target: Project) {
        println("apply KInjector")
        val ext = target.extensions.getByName("android") as BaseExtension
        ext.registerTransform(InjectionTransform(target))
    }
}