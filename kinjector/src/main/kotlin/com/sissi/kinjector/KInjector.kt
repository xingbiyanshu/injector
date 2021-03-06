package com.sissi.kinjector

import com.android.build.gradle.BaseExtension
import org.gradle.api.Plugin
import org.gradle.api.Project
import org.gradle.kotlin.dsl.create

class KInjector : Plugin<Project> {
    override fun apply(target: Project) {
//        println("apply KInjector")
        target.extensions.create<Ambulance>("ambulance")
        target.extensions.create<TimeCostMonitor>("timeCostMonitor")
        target.extensions.create<Tracer>("tracer")
        val ext = target.extensions.getByName("android") as BaseExtension
        ext.registerTransform(InjectionTransform(target, ext))
    }
}