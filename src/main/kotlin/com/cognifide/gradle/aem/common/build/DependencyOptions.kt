package com.cognifide.gradle.aem.common.build

import com.cognifide.gradle.aem.AemExtension
import com.cognifide.gradle.aem.common.utils.Patterns
import com.cognifide.gradle.aem.common.utils.Utils
import org.gradle.api.artifacts.Dependency
import java.io.File

/**
 * Based on: org.gradle.kotlin.dsl.accessors.runtime
 */
@Suppress("LongParameterList")
class DependencyOptions {

    lateinit var group: String

    lateinit var name: String

    var version: String? = null

    var configuration: String? = null

    var classifier: String? = null

    var ext: String? = null

    fun dependency(
        group: String,
        name: String,
        version: String? = null,
        configuration: String? = null,
        classifier: String? = null,
        ext: String? = null
    ) {
        this.group = group
        this.name = name
        this.version = version
        this.configuration = configuration
        this.classifier = classifier
        this.ext = ext
    }

    val notation: String get() = listOfNotNull(group, name, version, classifier).joinToString(":") +
            classifier?.run { "@$this" }.orEmpty()

    companion object {

        private val NOTATION_PATTERNS = listOf(
                "*:*:*@*", // TODO regex [\w-_.]+
                "*:*:*"
        )

        fun isNotation(text: String) = Patterns.wildcard(text, NOTATION_PATTERNS)

        fun create(aem: AemExtension, notation: Any): Dependency {
            return aem.project.dependencies.create(notation)
        }

        fun create(aem: AemExtension, options: DependencyOptions.() -> Unit): Dependency {
            return DependencyOptions().apply(options).run {
                aem.project.dependencies.create(Utils.mapOfNonNullValues(
                        "group" to group,
                        "name" to name,
                        "version" to version,
                        "configuration" to configuration,
                        "classifier" to classifier,
                        "ext" to ext
                ))
            }
        }

        fun add(aem: AemExtension, configuration: String, options: DependencyOptions.() -> Unit) {
            aem.project.dependencies.add(configuration, create(aem, options))
        }

        fun add(aem: AemExtension, configuration: String, notation: Any) {
            aem.project.dependencies.add(configuration, notation)
        }

        fun resolve(aem: AemExtension, notation: Any): File {
            val dependency = aem.project.dependencies.create(notation)
            val config = aem.project.configurations.detachedConfiguration(dependency).apply {
                isTransitive = false
            }

            return config.singleFile
        }
    }
}
