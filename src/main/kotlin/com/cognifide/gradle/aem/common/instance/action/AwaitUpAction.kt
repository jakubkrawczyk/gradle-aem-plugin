package com.cognifide.gradle.aem.common.instance.action

import com.cognifide.gradle.aem.AemExtension
import com.cognifide.gradle.aem.common.instance.check.*
import com.cognifide.gradle.aem.common.instance.names
import java.util.concurrent.TimeUnit

/**
 * Awaits for stable condition of all instances of any type.
 */
class AwaitUpAction(aem: AemExtension) : AnyInstanceAction(aem) {

    private var timeoutOptions: TimeoutCheck.() -> Unit = {}

    fun timeout(options: TimeoutCheck.() -> Unit) {
        timeoutOptions = options
    }

    private var bundlesOptions: BundlesCheck.() -> Unit = {
        symbolicNamesIgnored = aem.props.list("instance.awaitUp.bundles.symbolicNamesIgnored")
                ?: listOf()
    }

    fun bundles(options: BundlesCheck.() -> Unit) {
        bundlesOptions = options
    }

    private var eventsOptions: EventsCheck.() -> Unit = {
        unstableTopics = aem.props.list("instance.awaitUp.event.unstableTopics") ?: listOf(
                "org/osgi/framework/ServiceEvent/*",
                "org/osgi/framework/FrameworkEvent/*",
                "org/osgi/framework/BundleEvent/*"
        )
        unstableAgeMillis = aem.props.long("instance.awaitUp.event.unstableAgeMillis")
                ?: TimeUnit.SECONDS.toMillis(5)
    }

    fun events(options: EventsCheck.() -> Unit) {
        eventsOptions = options
    }

    private var componentsOptions: ComponentsCheck.() -> Unit = {
        platformComponents = aem.props.list("instance.awaitUp.components.platform")
                ?: listOf("com.day.crx.packaging.*", "org.apache.sling.installer.*")
        specificComponents = aem.props.list("instance.awaitUp.components.specific")
                ?: aem.javaPackages.map { "$it.*" }
    }

    fun components(options: ComponentsCheck.() -> Unit) {
        componentsOptions = options
    }

    val runner = CheckRunner(aem).apply {
        delay = aem.props.long("instance.awaitUp.delay") ?: TimeUnit.SECONDS.toMillis(1)
        resume = aem.props.flag("instance.awaitUp.resume")

        checks = {
            listOf(
                    TimeoutCheck(this).apply(timeoutOptions),
                    BundlesCheck(this).apply(bundlesOptions),
                    EventsCheck(this).apply(eventsOptions),
                    ComponentsCheck(this).apply(componentsOptions)
            )
        }
    }

    override fun perform() {
        if (!enabled) {
            return
        }

        if (instances.isEmpty()) {
            aem.logger.info("No instances to await up.")
            return
        }

        aem.logger.info("Awaiting instance(s) up: ${instances.names}")

        runner.check(instances)
    }
}