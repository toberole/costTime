package com.cat.zeus.plugin

import com.android.SdkConstants
import com.android.build.api.transform.Format
import com.android.build.api.transform.QualifiedContent
import com.android.build.api.transform.Transform
import com.android.build.api.transform.TransformInvocation
import com.android.build.gradle.internal.pipeline.TransformManager
import javassist.ClassPool
import javassist.CtClass
import javassist.CtMethod
import org.apache.commons.codec.digest.DigestUtils
import org.apache.commons.io.FileUtils
import org.gradle.api.Project

import javax.xml.crypto.dsig.TransformException
import java.text.MessageFormat

class CostTimeTransform extends Transform {
    CostTime costTime
    def pool = ClassPool.default
    def project

    CostTimeTransform(Project project) {
        this.project = project
        this.costTime = project.extensions.create('CostTime', CostTime)
        pool.appendClassPath(project.android.bootClasspath[0].toString())
    }

    @Override
    String getName() {
        return CostTimeTransform.simpleName
    }

    @Override
    Set<QualifiedContent.ContentType> getInputTypes() {
        return TransformManager.CONTENT_CLASS
    }

    @Override
    Set<? super QualifiedContent.Scope> getScopes() {
        return TransformManager.SCOPE_FULL_PROJECT
    }

    @Override
    boolean isIncremental() {
        return false
    }

    @Override
    void transform(TransformInvocation transformInvocation) throws TransformException, InterruptedException, IOException {
        super.transform(transformInvocation)

        project.android.bootClasspath.each {
            pool.appendClassPath(it.absolutePath)
        }

        transformInvocation.inputs.each {
            it.jarInputs.each {
                pool.insertClassPath(it.file.absolutePath)
                def jarName = it.name
                def md5Name = DigestUtils.md5Hex(it.file.getAbsolutePath())
                if (jarName.endsWith(".jar")) {
                    jarName = jarName.substring(0, jarName.length() - 4)
                }
                def dest = transformInvocation.outputProvider.getContentLocation(
                        jarName + md5Name, it.contentTypes, it.scopes, Format.JAR)
                FileUtils.copyFile(it.file, dest)
            }

            it.directoryInputs.each {
                def preFileName = it.file.absolutePath
                pool.insertClassPath(preFileName)
                findTarget(it.file, preFileName)
                def dest = transformInvocation.outputProvider.getContentLocation(
                        it.name,
                        it.contentTypes,
                        it.scopes,
                        Format.DIRECTORY)
                FileUtils.copyDirectory(it.file, dest)
            }
        }
    }

    private void findTarget(File dir, String fileName) {
        if (dir.isDirectory()) {
            dir.listFiles().each {
                findTarget(it, fileName)
            }
        } else {
            modify(dir, fileName)
        }
    }

    private void modify(File dir, String fileName) {
        def filePath = dir.absolutePath

        if (!filePath.endsWith(SdkConstants.DOT_CLASS)) {
            return
        }
        if (filePath.contains('R$') || filePath.contains('R.class')
                || filePath.contains("BuildConfig.class")) {
            return
        }

        def className = filePath.replace(fileName, "")
                .replace("\\", ".")
                .replace("/", ".")
        def name = className.replace(SdkConstants.DOT_CLASS, "")
                .substring(1)

        if (costTime.costTimePackageName != null && costTime.costTimePackageName.trim() != "") {
            if (name != null && name.trim() != "" && name.startsWith(costTime.costTimePackageName)) {
                CtClass ctClass = pool.get(name)
                if (ctClass.isFrozen()) {
                    ctClass.defrost()
                }
                insertCode(ctClass, fileName)
            }
        }
    }

    void insertCode(CtClass ctClass, String fileName) {
        CtMethod[] methods = ctClass.getDeclaredMethods()
        if (methods != null) {
            for (int i = 0; i < methods.length; i++) {
                CtMethod method = methods[i]
                String name = method.getName()
                method.addLocalVariable("startTime", CtClass.longType)
                method.addLocalVariable("endTime", CtClass.longType)
                method.insertBefore("startTime = System.currentTimeMillis();")
                method.insertAfter("endTime = System.currentTimeMillis();")
                String s = """android.util.Log.i("xxxx-plugin","{0} total time: " + (endTime - startTime));"""
                method.insertAfter(MessageFormat.format(s, name))
            }
        }

        ctClass.writeFile(fileName)
        ctClass.detach()

        System.out.println("insertCode ......")
    }
}