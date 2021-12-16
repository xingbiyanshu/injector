package com.sissi.kinjector

import com.android.build.api.transform.*
import com.android.build.gradle.BaseExtension
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
import kotlin.collections.HashMap

class InjectionTransform(private val project:Project, private val android:BaseExtension) : Transform() {

    private lateinit var timeCostMonitor:TimeCostMonitor

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
//        println("###transform...")
        timeCostMonitor = project.extensions.findByName("timeCostMonitor") as TimeCostMonitor

        timeCostMonitor.check()

        classPool = ClassPool.getDefault()
        classPool.insertClassPath(
            "${android.sdkDirectory.absolutePath}/platforms/${android.compileSdkVersion}/android.jar"
        )
        transformInvocation.inputs.forEach { input ->
            input.directoryInputs.forEach {
//                println("input.directoryInput=${it.file.absolutePath}")
                classPool.insertClassPath(it.file.absolutePath)
            }
            input.jarInputs.forEach {
//                println("input.jarInput=${it.file.absolutePath}")
                classPool.insertClassPath(it.file.absolutePath)
            }
        }

        transformInvocation.referencedInputs.forEach {
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

        classPool.importPackage("android.util.Log")

        transformInvocation.inputs.forEach{ input ->
            input.directoryInputs.forEach { dir ->
                val outputDir = transformInvocation.outputProvider.getContentLocation(
                    dir.file.absolutePath,
                    outputTypes,
                    scopes,
                    Format.DIRECTORY
                )

                transformDirectoryInput(dir, outputDir)
            }

            input.jarInputs.forEach { jar ->
                val outputJar = transformInvocation.outputProvider.getContentLocation(
                    jar.file.absolutePath,
                    jar.contentTypes,
                    jar.scopes,
                    Format.JAR
                )

                transformJarInput(jar, outputJar)
            }
        }
    }


    private fun transformDirectoryInput(inputDir:DirectoryInput, outputDir:File){
//        println("transformDirectoryInput: \ninputDir=${inputDir.file.absolutePath}, \noutputDir=${outputDir.absolutePath}")
        inputDir.file.copyRecursively(outputDir, true)

        val packageScopes = timeCostMonitor.parsePackageScopes()
//        println("packageScopes=$packageScopes")
        val timeCostMonitorNeedProcess = timeCostMonitor.run {
            enable && (scope == SCOPE_ALL || scope == SCOPE_SOURCE || packageScopes.isNotEmpty())
        }
        val needProcess = timeCostMonitorNeedProcess
        if (needProcess){
            outputDir.walk().forEach { file ->
                if (file.isClassfile()) {
                    val classname = file.relativeTo(outputDir).path.toClassname()
                    if (packageScopes.isNotEmpty() && !packageScopes.contains(classname.getPackage())){
                        return@forEach
                    }
//                    println("file=${file.absolutePath}")
                    if (timeCostMonitorNeedProcess) {
                        injectTimeCostMonitor(classname, timeCostMonitor, outputDir)
                    }
                }
            }
        }
    }


    private fun transformJarInput(jarInput:JarInput, outputJar : File){
//        println("transformJarInput: \njarInput=${jarInput.file.absolutePath}, \noutputJar=${outputJar.absolutePath}")
        jarInput.file.copyTo(outputJar, true)

        val packageScopes = timeCostMonitor.parsePackageScopes()
//        println("packageScopes=$packageScopes")
        val timeCostMonitorNeedProcess = timeCostMonitor.run {
            enable && (scope == SCOPE_ALL || scope == SCOPE_LIB || packageScopes.isNotEmpty())
        }
        val needProcess = timeCostMonitorNeedProcess
        if (needProcess){
            val jarFile = JarFile(outputJar)
            val entries: Enumeration<JarEntry> = jarFile.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory || !entry.name.endsWith(".class")){
                    continue
                }

                val classname = entry.name.toClassname()
                if (packageScopes.isNotEmpty() && !packageScopes.contains(classname.getPackage())){
                    continue
                }
//                println("entry=$entry, entry.attributes=${entry.attributes}")

                if (timeCostMonitorNeedProcess) {
                    val tmpDir = File("${project.buildDir}/intermediates/transforms/tmp/")
                    if (!tmpDir.exists()){
                        tmpDir.mkdirs()
                    }

                    if (injectTimeCostMonitor(classname, timeCostMonitor, tmpDir)) {
                        val env: MutableMap<String, String> = HashMap()
                        env["create"] = "true"
                        val jarUri = URI.create("jar:" + outputJar.toURI())
                        FileSystems.newFileSystem(jarUri, env).use { zipfs ->
                            val classToInject = Paths.get(tmpDir.absolutePath + "/" + entry.name)
                            val pathInJarfile = zipfs.getPath(entry.name)
//                            println("classToInject=$classToInject, pathInJarfile=$pathInJarfile")
                            // copy a file into the zip file
                            Files.copy(
                                classToInject,
                                pathInJarfile,
                                StandardCopyOption.REPLACE_EXISTING
                            )
                        }
                    }
                }
            }
        }

    }


    private fun injectTimeCostMonitor(classname:String, timeCostMonitor : TimeCostMonitor, outputDir:File) : Boolean{
        if (classname == "META-INF.versions.9.module-info"){
            return false
        }
        val clazz:CtClass
        try {
            clazz = classPool.get(classname)
        }catch (e:Exception){
            println("ERROR: read class $classname failed!")
            return false
        }

        clazz.declaredMethods.forEach { method ->

            val codeLen = method.methodInfo2?.codeAttribute?.codeLength
            if (method.isEmpty || codeLen==null || codeLen==0){
                return@forEach
            }
//            println("classname=$classname, method=$method, isEmpty= ${method.isEmpty}, codeLen=$codeLen")

            method.addLocalVariable("start", CtClass.longType)
            method.insertBefore(
                """
                    start = System.currentTimeMillis();
                """.trimIndent()
            )

            method.insertAfter(
                """
                    if (${timeCostMonitor.condition}){
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
                        String fullMethodName = "${clazz.name}#${method.name}";
                        long limit = ${timeCostMonitor.timeLimit};
                        long warningLine = limit*0.8;
                        String actionWhenReachLimit = "${timeCostMonitor.actionWhenReachLimit}";
                        if (costTime < warningLine){
                            Log.d("KInjector", fullMethodName+"("+parasStr+") cost time: "+costTime+"ms");
                        }else if (warningLine < costTime && costTime < limit){
                            Log.w("KInjector", fullMethodName+"("+parasStr+") cost time: "+costTime+"ms");
                        }else{
                            if (actionWhenReachLimit.equals("${timeCostMonitor.ACTION_LOG}")){
                                Log.e("KInjector", fullMethodName+"("+parasStr+") cost time: "+costTime+"ms");
                            }else{
                                throw new RuntimeException(fullMethodName+" cost time "+costTime+"ms reach the limit "+limit+"ms");
                            }
                        }
                    }
                """.trimIndent()
            )

        }

        clazz.writeFile(outputDir.absolutePath)
        clazz.detach() // 及时释放

        return true
    }


    private fun String.toClassname() =
        replace("/", ".")
            .replace("\\", ".")
            .replace(".class", "")

    private fun File.isClassfile(): Boolean = isFile && path.endsWith(".class")

    private fun String.getPackage() = substringBeforeLast(".")

}