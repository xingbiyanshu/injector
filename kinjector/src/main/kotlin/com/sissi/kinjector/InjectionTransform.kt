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

    private lateinit var ambulance: Ambulance

    private lateinit var classPool:ClassPool

    private var outputDirSet = HashSet<File>()

    private var outputJarSet = HashSet<File>()

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

        ambulance = project.extensions.findByName("ambulance") as Ambulance

        ambulance.check()

        classPool = ClassPool.getDefault()
        classPool.insertClassPath(
            "${android.sdkDirectory.absolutePath}/platforms/${android.compileSdkVersion}/android.jar"
        )
        transformInvocation.inputs.forEach { input ->
            input.directoryInputs.forEach {dir->
                val outputDir = transformInvocation.outputProvider.getContentLocation(
                    dir.file.absolutePath,
                    outputTypes,
                    scopes,
                    Format.DIRECTORY
                )
                dir.file.copyRecursively(outputDir, true)
//                println("copied ${dir.file.absolutePath} to ${outputDir.absolutePath}")

                classPool.insertClassPath(outputDir.absolutePath)
                outputDirSet.add(outputDir)
            }
            input.jarInputs.forEach { jar ->
                val outputJar = transformInvocation.outputProvider.getContentLocation(
                    jar.file.absolutePath,
                    jar.contentTypes,
                    jar.scopes,
                    Format.JAR
                )
                jar.file.copyTo(outputJar, true)
//                println("copied ${jar.file.absolutePath} to ${outputJar.absolutePath}")

                classPool.insertClassPath(outputJar.absolutePath)
                outputJarSet.add(outputJar)
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

        outputDirSet.forEach {
            transformDir(it)
        }

        outputJarSet.forEach {
            transformJar(it)
        }

    }


    private fun transformDir(dir:File){
//        println("transformDir: dir=$dir")
        val patientList = ambulance.patients
        val ambulanceNeedProcess = ambulance.enable && patientList.isNotEmpty()
        val packageScopes = timeCostMonitor.parsePackageScopes()
        val timeCostMonitorNeedProcess = timeCostMonitor.run {
            enable && (scope == SCOPE_ALL || scope == SCOPE_SOURCE || packageScopes.isNotEmpty())
        }

//        println("timeCostMonitorNeedProcess=$timeCostMonitorNeedProcess\nambulanceNeedProcess=$ambulanceNeedProcess")

        val needProcess = timeCostMonitorNeedProcess || ambulanceNeedProcess
        if (needProcess){
            dir.walk().forEach { file ->
                if (!file.isClassfile()) {
                    return@forEach
                }

                val classname = file.relativeTo(dir).path.toClassname()
//                    println("file=${file.absolutePath}")

                val clazz:CtClass
                try {
                    clazz = classPool.get(classname)
                }catch (e:Exception){
                    println("ERROR: read class $classname failed!")
                    return@forEach
                }

                if (ambulanceNeedProcess){
                    injectAmbulance(clazz, ambulance)
                }

                if (timeCostMonitorNeedProcess) {
                    if (packageScopes.isEmpty() || packageScopes.contains(classname.getPackage())) {
                        injectTimeCostMonitor(clazz, timeCostMonitor)
                    }
                }

                clazz.writeFile(dir.absolutePath)
                clazz.detach() // 及时释放
            }
        }
    }


    private fun transformJar(jar : File){
//        println("transformJar: jar=${jar.absolutePath}")

        val ambulanceNeedProcess = ambulance.enable && ambulance.patients.isNotEmpty()

        val packageScopes = timeCostMonitor.parsePackageScopes()
        val timeCostMonitorNeedProcess = timeCostMonitor.run {
            enable && (scope == SCOPE_ALL || scope == SCOPE_LIB || packageScopes.isNotEmpty())
        }
        val needProcess = timeCostMonitorNeedProcess || ambulanceNeedProcess
        if (needProcess){
            val jarFile = JarFile(jar)
            val entries: Enumeration<JarEntry> = jarFile.entries()
            while (entries.hasMoreElements()) {
                val entry = entries.nextElement()
                if (entry.isDirectory || !entry.name.endsWith(".class")){
                    continue
                }

                val classname = entry.name.toClassname()
//                println("entry=$entry, entry.attributes=${entry.attributes}")
                val clazz:CtClass
                try {
                    clazz = classPool.get(classname)
                }catch (e:Exception){
                    println("ERROR: read class $classname failed!")
                    continue
                }

                if (ambulanceNeedProcess){
                    injectAmbulance(clazz, ambulance)
                }

                if (timeCostMonitorNeedProcess) {
                    if (packageScopes.isEmpty() || packageScopes.contains(classname.getPackage())){
                        injectTimeCostMonitor(clazz, timeCostMonitor)
                    }
                }

                val tmpDir = File("${project.buildDir}/intermediates/transforms/tmp/")
                if (!tmpDir.exists()){
                    tmpDir.mkdirs()
                }
                clazz.writeFile(tmpDir.absolutePath)
                clazz.detach() // 及时释放

                packIntoJar(tmpDir, entry.name, jar)

            }
        }

    }


    private fun packIntoJar(srcDir:File, filename:String, jar:File){
        val env: MutableMap<String, String> = HashMap()
        env["create"] = "true"
        val jarUri = URI.create("jar:" + jar.toURI())
        FileSystems.newFileSystem(jarUri, env).use { zipfs ->
            val classToInject = Paths.get(srcDir.absolutePath + "/" + filename)
            val pathInJarfile = zipfs.getPath(filename)
//                        println("classToInject=$classToInject, pathInJarfile=$pathInJarfile")
            // copy a file into the zip file
            Files.copy(
                classToInject,
                pathInJarfile,
                StandardCopyOption.REPLACE_EXISTING
            )
        }
    }


    private fun injectTimeCostMonitor(clazz:CtClass, timeCostMonitor : TimeCostMonitor) : Boolean{
        if (clazz.name == "META-INF.versions.9.module-info"){
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

        return true
    }


    private fun injectAmbulance(clazz:CtClass, ambulance:Ambulance):Boolean {
        val focusList = ambulance.getFocusList(clazz.name)
        if (focusList.isEmpty()){
            return false
        }

        clazz.declaredMethods.forEach { method ->
            focusList.forEach focus@{focus->
//                println("focus.methodName=${focus.methodName()}, focus.methodParas=${focus.methodParas()}")
                if (method.name!=focus.methodName()){
                    return@focus
                }
                val methodParas = focus.methodParas()
                if (methodParas.size != method.parameterTypes.size){
                    return@focus
                }
                method.parameterTypes.forEachIndexed { index, ctClass ->
//                    println("method=${method.name}, para[$index]=${ctClass.name}, " +
//                            "focus.methodName=${focus.methodName()}, focus.methodPara[$index]=${methodParas[index]}")
                    if (methodParas[index] != ctClass.name){
                        return@focus
                    }
                }

                when (focus.position) {
                    focus.POS_INSERT_BEGIN -> {
                        method.insertBefore(focus.repairCode)
                    }
                    focus.POS_INSERT_END -> {
                        method.insertAfter(focus.repairCode)
                    }
                    focus.POS_REPLACE_BODY -> {
                        method.setBody(focus.repairCode)
                    }
                    else -> {
                        method.insertAt(focus.position, focus.repairCode)
                    }
                }
            }
        }

        return true
    }


    private fun String.toClassname() =
        replace("/", ".")
            .replace("\\", ".")
            .replace(".class", "")

    private fun File.isClassfile(): Boolean = isFile && path.endsWith(".class")

    private fun String.getPackage() = substringBeforeLast(".")

}