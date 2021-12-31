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

    private lateinit var tmpDir:File

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

        tmpDir = File("${project.buildDir}/intermediates/transforms/tmp/")
        if (!tmpDir.exists()){
            tmpDir.mkdirs()
        }

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

                val relativePath = file.relativeTo(dir).path
                val classname = relativePath.toClassname()
//                    println("file=${file.absolutePath}")

                val clazz:CtClass
                try {
                    clazz = classPool.get(classname)
                }catch (e:Exception){
                    println("ERROR: read class $classname failed!")
                    return@forEach
                }

                val resList = ArrayList<Boolean>()

                if (ambulanceNeedProcess){
                    resList.add(injectAmbulance(clazz, ambulance))
                }

                if (timeCostMonitorNeedProcess) {
                    if (packageScopes.isEmpty() || packageScopes.contains(classname.getPackage())) {
                        resList.add(injectTimeCostMonitor(clazz, timeCostMonitor))
                    }
                }

                if (resList.contains(true)) {
                    clazz.writeFile(tmpDir.absolutePath)
                    File(tmpDir, relativePath).copyTo(file, true)
                }
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

                val resList = ArrayList<Boolean>()

                if (ambulanceNeedProcess){
                    resList.add(injectAmbulance(clazz, ambulance))
                }

                if (timeCostMonitorNeedProcess) {
                    if (packageScopes.isEmpty() || packageScopes.contains(classname.getPackage())){
                        resList.add(injectTimeCostMonitor(clazz, timeCostMonitor))
                    }
                }

                if (resList.contains(true)){
                    clazz.writeFile(tmpDir.absolutePath)

                    packIntoJar(tmpDir, entry.name, jar)
                }

                clazz.detach() // 及时释放

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

//        println("-=-> trying inject time cost monitor")

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
                            Log.d("KInjector", "time cost "+costTime+"ms by "+fullMethodName+"("+parasStr+")");
                        }else if (warningLine < costTime && costTime < limit){
                            Log.w("KInjector", "heavy time cost "+costTime+"ms(limit "+limit+"ms) by "+fullMethodName+"("+parasStr+")");
                        }else{
                            if (actionWhenReachLimit.equals("${timeCostMonitor.ACTION_LOG}")){
                                Log.e("KInjector", "overspent time cost "+costTime+"ms(limit "+limit+"ms) by "+fullMethodName+"("+parasStr+")");
                            }else{
                                throw new RuntimeException("overspent time cost "+costTime+"ms(limit "+limit+"ms) by "+fullMethodName+"("+parasStr+")");
                            }
                        }
                    }
                """.trimIndent()
            )

        }

//        println("<-=-inject time cost monitor SUCCESS")

        return true
    }


    private fun injectAmbulance(clazz:CtClass, ambulance:Ambulance):Boolean {
        val focusList = ambulance.getFocusList(clazz.name)
        if (focusList.isEmpty()){
            return false
        }

        focusList.forEach {focus->
            val focusMethodName = focus.methodName()
            val focusMethodParas = focus.methodParas()
            println("-=-> trying inject ambulance, target: class=${clazz.name}, focusMethodName=$focusMethodName, focusMethodParas=$focusMethodParas")

            run {
                clazz.declaredMethods.forEach clzMethods@{ method ->
                    if (method.name != focusMethodName) {
                        return@clzMethods
                    }
                    if (focusMethodParas.size != method.parameterTypes.size) {
                        return@clzMethods
                    }
                    method.parameterTypes.forEachIndexed { index, ctClass ->
                        println("para[$index]=${ctClass.name}, focusMethodParas[$index]=${focusMethodParas[index]}")
                        if (focusMethodParas[index] != ctClass.name) {
                            return@clzMethods
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

                    println("<-=- inject ambulance SUCCESS")

                    return@run
                }
            }

        }

        return true
    }


    private fun injectTracer(clazz:CtClass, tracer:Tracer):Boolean{
        return true
    }


    private fun String.toClassname() =
        replace("/", ".")
            .replace("\\", ".")
            .replace(".class", "")

    private fun File.isClassfile(): Boolean = isFile && path.endsWith(".class")

    private fun String.getPackage() = substringBeforeLast(".")

}