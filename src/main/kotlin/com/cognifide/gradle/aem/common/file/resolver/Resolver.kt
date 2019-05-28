package com.cognifide.gradle.aem.common.file.resolver

import com.cognifide.gradle.aem.AemExtension
import com.cognifide.gradle.aem.common.build.DependencyOptions
import com.cognifide.gradle.aem.common.file.FileException
import com.cognifide.gradle.aem.common.file.downloader.HttpFileDownloader
import com.cognifide.gradle.aem.common.file.downloader.SftpFileDownloader
import com.cognifide.gradle.aem.common.file.downloader.SmbFileDownloader
import com.cognifide.gradle.aem.common.file.downloader.UrlFileDownloader
import com.cognifide.gradle.aem.common.http.HttpClient
import com.cognifide.gradle.aem.common.utils.Formats
import com.google.common.hash.HashCode
import java.io.File
import java.util.*
import org.apache.commons.io.FilenameUtils
import org.apache.commons.lang3.builder.HashCodeBuilder
import org.gradle.api.artifacts.Configuration
import org.gradle.api.tasks.Internal
import org.gradle.util.GFileUtils

/**
 * File downloader with groups supporting files from multiple sources: local and remote (SFTP, SMB, HTTP).
 *
 * TODO use FileManager to download files, ensure overriding credentials support (cloning each particular transfer)
 */
abstract class Resolver<G : FileGroup>(
    @get:Internal
val aem: AemExtension,

    @get:Internal
val downloadDir: File
) {
    private val project = aem.project

    private val groupDefault = this.createGroup(GROUP_DEFAULT)

    private var groupCurrent = groupDefault

    private val groupsDefined = mutableListOf<G>().apply { add(groupDefault) }

    @get:Internal
    val groups: List<G>
        get() = groupsDefined.filter { it.resolutions.isNotEmpty() }

    protected open fun resolve(hash: Any, resolver: (FileResolution) -> File): FileResolution {
        val id = HashCode.fromInt(HashCodeBuilder().append(hash).toHashCode()).toString()

        return groupCurrent.resolve(id, resolver)
    }

    fun outputDirs(filter: G.() -> Boolean = { true }): List<File> {
        return groups.filter(filter).flatMap { it.dirs }
    }

    fun allFiles(filter: G.() -> Boolean = { true }): List<File> {
        return resolveGroups(filter).flatMap { it.files }
    }

    fun resolveGroups(filter: G.() -> Boolean = { true }): List<G> {
        return groups.filter(filter).onEach { it.files }
    }

    fun group(name: String): G {
        return groupsDefined.find { it.name == name }
                ?: throw FileException("File group '$name' is not defined.")
    }

    fun dependency(notation: Any): FileResolution {
        return resolve(notation) {
            val configName = "fileResolver_dependency_${UUID.randomUUID()}"
            val configOptions: (Configuration) -> Unit = { it.isTransitive = false }
            val config = project.configurations.create(configName, configOptions)

            project.dependencies.add(config.name, notation)
            config.singleFile
        }
    }

    fun dependency(dependencyOptions: DependencyOptions.() -> Unit): FileResolution {
        return dependency(DependencyOptions.of(project.dependencies, dependencyOptions))
    }

    fun url(url: String): FileResolution {
        return when {
// TODO fix satisfy etc
//            SftpFileDownloader.handles(url) -> downloadSftpAuth(url)
//            SmbFileDownloader.handles(url) -> downloadSmbAuth(url)
//            HttpFileDownloader.handles(url) -> downloadHttpAuth(url)
//            UrlFileDownloader.handles(url) -> downloadUrl(url)
            else -> local(url)
        }
    }

    fun downloadSftp(url: String): FileResolution {
        return resolve(url) { resolution ->
            download(url, resolution.dir) { file ->
                SftpFileDownloader(aem).download(url, file)
            }
        }
    }

    private fun download(url: String, targetDir: File, downloader: (File) -> Unit): File {
        GFileUtils.mkdirs(targetDir)

        val file = File(targetDir, FilenameUtils.getName(url))
        val lock = File(targetDir, DOWNLOAD_LOCK)
        if (!lock.exists() && file.exists()) {
            file.delete()
        }

        if (!file.exists()) {
            downloader(file)
            lock.printWriter().use { it.print(Formats.toJson(mapOf("downloaded" to Formats.date()))) }
        }

        return file
    }

    fun downloadSftp(url: String, sftpOptions: SftpFileDownloader.() -> Unit = {}): FileResolution {
        return resolve(url) { resolution ->
            download(url, resolution.dir) { file ->
                SftpFileDownloader(aem)
                        .apply(sftpOptions)
                        .download(url, file)
            }
        }
    }

//    fun downloadSftpAuth(url: String, sftpOptions: SftpFileDownloader.() -> Unit): FileResolution {
//        return downloadSftp(url) {
//            this.username = username ?: aem.fileTransfer.sftpUsername
//            this.password = password ?: aem.resolverOptions.sftpPassword
//            this.hostChecking = hostChecking ?: aem.resolverOptions.sftpHostChecking ?: false
//        }
//    }

    fun downloadSmb(url: String, smbOptions: SmbFileDownloader.() -> Unit = {}): FileResolution {
        return resolve(url) { resolution ->
            download(url, resolution.dir) { file ->
                SmbFileDownloader(aem)
                        .apply(smbOptions)
                        .download(url, file)
            }
        }
    }

//    fun downloadSmbAuth(url: String, domain: String? = null, username: String? = null, password: String? = null): FileResolution {
//        return downloadSmb(url) {
//            this.domain = domain ?: aem.resolverOptions.smbDomain
//            this.username = username ?: aem.resolverOptions.smbUsername
//            this.password = password ?: aem.resolverOptions.smbPassword
//        }
//    }

    fun downloadHttp(url: String, httpOptions: HttpClient.() -> Unit = {}): FileResolution {
        return resolve(url) { resolution ->
            download(url, resolution.dir) { file ->
                with(HttpFileDownloader(aem)) {
                    client(httpOptions)
                    download(url, file)
                }
            }
        }
    }

//    fun downloadHttpAuth(url: String, user: String? = null, password: String? = null, ignoreSsl: Boolean? = null): FileResolution {
//        return downloadHttp(url) {
//            basicUser = user ?: aem.resolverOptions.httpUsername ?: ""
//            basicPassword = password ?: aem.resolverOptions.httpPassword ?: ""
//            connectionIgnoreSsl = ignoreSsl ?: aem.resolverOptions.httpConnectionIgnoreSsl ?: true
//        }
//    }

    fun downloadUrl(url: String): FileResolution {
        return resolve(url) { resolution ->
            download(url, resolution.dir) { file ->
                UrlFileDownloader(aem).download(url, file)
            }
        }
    }

    fun local(path: String): FileResolution {
        return local(project.file(path))
    }

    fun local(sourceFile: File): FileResolution {
        return resolve(sourceFile.absolutePath) { sourceFile }
    }

    fun config(configurer: G.() -> Unit) {
        groupCurrent.apply(configurer)
    }

    @Synchronized
    fun group(name: String, configurer: Resolver<G>.() -> Unit) {
        groupCurrent = groupsDefined.find { it.name == name } ?: createGroup(name).apply { groupsDefined.add(this) }
        this.apply(configurer)
        groupCurrent = groupDefault
    }

    abstract fun createGroup(name: String): G

    companion object {
        const val GROUP_DEFAULT = "default"

        const val DOWNLOAD_LOCK = "download.lock"
    }
}