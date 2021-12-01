package com.sissi.kinjector

import com.android.build.api.transform.Format
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformInvocation
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

class InjectionTransform(private val project:Project) : Transform() {
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
        transformInvocation.inputs.forEach{ input ->
            input.directoryInputs.forEach { dir ->
                val dirDest = transformInvocation.outputProvider.getContentLocation(
                    dir.file.absolutePath,
                    outputTypes,
                    scopes,
                    Format.DIRECTORY
                )
                dir.file.copyRecursively(dirDest, true)
            }

            input.jarInputs.forEach { jar ->
                val jarDest = transformInvocation.outputProvider.getContentLocation(
                    jar.file.absolutePath,
                    jar.contentTypes,
                    jar.scopes,
                    Format.JAR
                )

                if (jar.file.name.contains("log")) {
                    val jarFile = JarFile(jar.file)
                    val entries: Enumeration<JarEntry> = jarFile.entries()
                    while (entries.hasMoreElements()) {
                        val entry = entries.nextElement()
//                        println("----jarEntry=$jarEntry, jarEntry.name=${jarEntry.name}, \njarInput.file.absolutePath=${jarInput.file.absolutePath}")
                        if (entry.name == "com/kedacom/vconf/sdk/log/KLog.class"){
                            val pool = ClassPool.getDefault()
                            pool.insertClassPath(jar.file.absolutePath)
                            val tmpDir = File("${project.buildDir}/intermediates/transforms/tmp/")
                            if (!tmpDir.exists()){
                                tmpDir.mkdirs()
                            }

                            val classname = entry.name.toClassname()
                            val clazz = pool.get(classname)
                            println("##clazz=${clazz.name}, tmpDir=${tmpDir.absolutePath}")
                            transformKLog(clazz)
                            clazz.writeFile(tmpDir.absolutePath)
                            clazz.detach() // 及时释放

                            val env: MutableMap<String, String> = HashMap()
                            env["create"] = "true"
                            val jarUri = URI.create("jar:" + jar.file.toURI())
                            FileSystems.newFileSystem(jarUri, env).use { zipfs ->
                                val classToInject = Paths.get(tmpDir.absolutePath+"/"+entry.name)
                                val pathInJarfile = zipfs.getPath(entry.name)
                                // copy a file into the zip file
                                Files.copy(classToInject, pathInJarfile, StandardCopyOption.REPLACE_EXISTING)
                            }
                        }
                    }
                }

                jar.file.copyTo(jarDest, true)
            }
        }
    }


    private fun transformKLog(clazz: CtClass) {
        clazz.declaredMethods.forEach { method ->
            val name = method.name
            val expectedParas = listOf("java.lang.String", "java.lang.Object[]")
            println("method name=$name")
            if (name=="p" && method.parameterTypes.size==expectedParas.size) {
                var parasMatched = true
                val paras = method.parameterTypes
                paras.forEach {
                    println("para=${it.name}")
                }
                for (i in paras.indices){
                    if (paras[i].name != expectedParas[i]){
                        parasMatched = false
                        break
                    }
                }
                if (parasMatched){
                    method.insertBefore(
                        """System.out.println("Inject KLog IN");"""
                    )
                    method.insertAfter(
                        """System.out.println("Inject KLog OUT");"""
                    )
                    return
                }
            }

        }
    }

    private fun String.toClassname() =
        replace("/", ".")
            .replace("\\", ".")
            .replace(".class", "")

}