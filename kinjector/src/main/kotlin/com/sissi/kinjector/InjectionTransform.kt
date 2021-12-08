package com.sissi.kinjector

import com.android.build.api.transform.*
import com.android.build.gradle.BaseExtension
import com.android.build.gradle.internal.InternalScope
import com.android.build.gradle.internal.pipeline.TransformManager
import javassist.ClassPool
import javassist.CtClass
import org.gradle.api.Project
import java.io.File
import java.net.URI
import java.nio.file.FileSystems
import java.nio.file.Files
import java.nio.file.Paths
import java.nio.file.StandardCopyOption
import java.util.*
import java.util.jar.JarEntry
import java.util.jar.JarFile

class InjectionTransform(private val project:Project, private val android:BaseExtension) : Transform() {

    private lateinit var classPool:ClassPool

    override fun getName(): String {
        return javaClass.simpleName
    }

    override fun getInputTypes(): MutableSet<QualifiedContent.ContentType> {
        return mutableSetOf(
            QualifiedContent.DefaultContentType.CLASSES
        )
    }

    override fun getScopes(): MutableSet<in QualifiedContent.Scope> {
        return mutableSetOf(
            QualifiedContent.Scope.PROJECT,
            QualifiedContent.Scope.SUB_PROJECTS,
            QualifiedContent.Scope.EXTERNAL_LIBRARIES
        )
    }

    override fun isIncremental(): Boolean {
        return true
    }

    override fun transform(transformInvocation: TransformInvocation) {
        println("###transform...")
        classPool = ClassPool.getDefault()
        classPool.insertClassPath(
            "${android.sdkDirectory.absolutePath}/platforms/${android.compileSdkVersion}/android.jar"
        )
        transformInvocation.inputs.forEach { input ->
            input.directoryInputs.forEach {
                println("input.directoryInput=${it.file.absolutePath}")
                classPool.insertClassPath(it.file.absolutePath)
            }
            input.jarInputs.forEach {
                println("input.jarInput=${it.file.absolutePath}")
                classPool.insertClassPath(it.file.absolutePath)
            }
        }

        transformInvocation.referencedInputs.forEach { it ->
            it.jarInputs.forEach {it2->
                println("referenced.jarInput=${it2.file}")
            }
            it.directoryInputs.forEach { it3->
                println("referenced.directoryInput=${it3.file}")
            }
        }

        transformInvocation.secondaryInputs.forEach{
            println("secondaryInput=${it.secondaryInput}")
        }

        transformInvocation.inputs.forEach{ input ->
            input.directoryInputs.forEach { dir ->
                val outputDir = transformInvocation.outputProvider.getContentLocation(
                    dir.file.absolutePath,
                    outputTypes,
                    scopes,
                    Format.DIRECTORY
                )
                dir.file.copyRecursively(outputDir, true)
                transformDirectoryInput(dir, outputDir)
            }

            input.jarInputs.forEach { jar ->
                val outputJar = transformInvocation.outputProvider.getContentLocation(
                    jar.file.absolutePath,
                    jar.contentTypes,
                    jar.scopes,
                    Format.JAR
                )

//                if (jar.file.name.contains("log")) {
//                    val jarFile = JarFile(jar.file)
//                    val entries: Enumeration<JarEntry> = jarFile.entries()
//                    while (entries.hasMoreElements()) {
//                        val entry = entries.nextElement()
////                        println("----jarEntry=$jarEntry, jarEntry.name=${jarEntry.name}, \njarInput.file.absolutePath=${jarInput.file.absolutePath}")
//                        if (entry.name == "com/kedacom/vconf/sdk/log/KLog.class"){
//                            val pool = ClassPool.getDefault()
//                            pool.insertClassPath(jar.file.absolutePath)
//                            val tmpDir = File("${project.buildDir}/intermediates/transforms/tmp/")
//                            if (!tmpDir.exists()){
//                                tmpDir.mkdirs()
//                            }
////                            pool.importPackage("java.util.List")
////                            pool.importPackage("java.util.Arrays")
////                            pool.importPackage("android.os.Looper")
//
//                            val classname = entry.name.toClassname()
//                            val clazz = pool.get(classname)
////                            println("##clazz=${clazz.name}, tmpDir=${tmpDir.absolutePath}")
////                            transformKLog(clazz, pool)
//                            clazz.writeFile(tmpDir.absolutePath)
//                            clazz.detach() // 及时释放
//
////                            val compiler = Javac(clazz)
////                            compiler.compile()
//
//                            val env: MutableMap<String, String> = HashMap()
//                            env["create"] = "true"
//                            val jarUri = URI.create("jar:" + jar.file.toURI())
//                            FileSystems.newFileSystem(jarUri, env).use { zipfs ->
//                                val classToInject = Paths.get(tmpDir.absolutePath+"/"+entry.name)
//                                val pathInJarfile = zipfs.getPath(entry.name)
//                                // copy a file into the zip file
//                                Files.copy(classToInject, pathInJarfile, StandardCopyOption.REPLACE_EXISTING)
//                            }
//                        }
//                    }
//                }

                jar.file.copyTo(outputJar, true)
            }
        }
    }


//    private fun transformKLog(clazz: CtClass, pool:ClassPool) {
//        clazz.declaredMethods.forEach { method ->
//            val name = method.name
//            val expectedParas = listOf("java.lang.String", "java.lang.Object[]")
//            println("method name=$name")
//            if (name=="p" && method.parameterTypes.size==expectedParas.size) {
//                var parasMatched = true
//                val paras = method.parameterTypes
//                paras.forEach {
//                    println("para=${it.name}")
//                }
//                for (i in paras.indices){
//                    if (paras[i].name != expectedParas[i]){
//                        parasMatched = false
//                        break
//                    }
//                }
//                if (parasMatched){
////                    method.insertAt(135, """
////                    {
////                        System.out.println("paras1: "+$1);
////                        Class[] clzs = ${'$'}sig;
////                        System.out.println("paras1: "+clzs[0]);
////                        Object[] paras = ${'$'}args;
////                        for (int i=0; i<paras.length; ++i){
////                            System.out.println(i+"i="+paras[i]);
////                        }
////                    }
////                    """.trimIndent())
////
////                    method.insertBefore(
////                        """System.out.println("Inject KLog IN");"""
////                    )
////                    method.insertAfter(
////                        """System.out.println("Inject KLog OUT");"""
////                    )
//
////                    method.addLocalVariable("isMainThread", CtClass.booleanType)
//                    method.addLocalVariable("start", CtClass.longType)
////                    method.addLocalVariable("end", CtClass.longType)
////                    method.addLocalVariable("i", CtClass.longType)
//
////                    method.insertBefore(
////                        """
////                            {
////                                if (true){
////                                    System.out.println("Inject KLog IN");
////                                    start = System.currentTimeMillis();
////                                    for(i=0; i<1000000000L; ++i){}
////                                }
////                            }
////                            """
////                    )
//
//                    method.insertBefore(
//                    """
//                            start = System.currentTimeMillis();
//                        """
//                    )
//
//                    method.insertAfter(
//                    """
//                            if (Thread.currentThread().getName().equals("main")){
//                                Object[] paras = ${'$'}args;
//                                StringBuilder sb = new StringBuilder();
//                                for (int i=0; i<paras.length; ++i){
//                                    sb.append(paras[i]);
//                                    if (i != paras.length-1){
//                                        sb.append(", ");
//                                    }
//                                }
//                                String parasStr = sb.toString();
//                                long end = System.currentTimeMillis();
//                                System.out.println("${clazz.name}#${method.name}("+parasStr+") cost time: "+(end-start)+"ms");
//                            }
//                        """
//                    )
//
//                    return
//                }
//            }
//
//        }
//    }


    private fun transformDirectoryInput(dir:DirectoryInput, outputDir:File){
        println("DirectoryInput=${dir.file.absolutePath}, \noutputDir=${outputDir.absolutePath}")
        val methodTimeCostMonitor = project.extensions.findByName("methodTimeCostMonitor") as MethodTimeCostMonitor
        val methodTimeCostMonitorNeedProcess = methodTimeCostMonitor.run {enable && (scope == SCOPE_ALL || scope == SCOPE_SOURCE)}
        val needProcess = methodTimeCostMonitorNeedProcess
        if (true){
//            val classPool = ClassPool.getDefault()
//            val pool = ClassPool.getDefault()
//            classPool.insertClassPath("${android.sdkDirectory.absolutePath}/platforms/${android.compileSdkVersion}/android.jar")
//            classPool.insertClassPath(dir.file.absolutePath)
//            println("####dir=${dir.file.absolutePath}")
            dir.file.walk().forEach { file ->
                println("file=${file.absolutePath}")

                if (file.isClassfile()) {
                    val classname = file.relativeTo(dir.file).path.toClassname()
                    val clazz = classPool.get(classname)
                    clazz.declaredMethods.forEach { method ->
//                        println("method=$method")
                        method.addLocalVariable("start", CtClass.longType)
                        method.insertBefore(
                        """
                                System.out.println("${clazz.name}#${method.name} IN");
                                start = System.currentTimeMillis();
                            """
                        )

                        method.insertAfter(
                        """
                            if (${methodTimeCostMonitor.condition}){
                                Object[] paras = ${'$'}args;
                                StringBuilder sb = new StringBuilder();
                                for (int i=0; i<paras.length; ++i){
                                    sb.append(paras[i]);
                                    if (i != paras.length-1){
                                        sb.append(", ");
                                    }
                                }
                                String parasStr = sb.toString();
                                long costTime = System.currentTimeMillis()-start;
                                System.out.println("${clazz.name}#${method.name}("+parasStr+") cost time: "+costTime+"ms");
                                long limit = ${methodTimeCostMonitor.alertWhenReach};
                                if (costTime >= limit){
                                    throw new RuntimeException("cost time "+costTime+"ms reach the limit "+limit+"ms");
                                }
                            }
                            """
                        )

                    }

                    clazz.writeFile(outputDir.absolutePath)
                    clazz.detach() // 及时释放
                }
            }
        }
    }

    private fun String.toClassname() =
        replace("/", ".")
            .replace("\\", ".")
            .replace(".class", "")

    private fun File.isClassfile(): Boolean = isFile && path.endsWith(".class")

}