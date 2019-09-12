package com.cognifide.gradle.aem.instance.provision

import com.cognifide.gradle.aem.AemDefaultTask
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction

open class InstanceProvision : AemDefaultTask() {

    @Internal
    val provisioner = Provisioner(aem)

    @TaskAction
    fun provision() {
        val actions = provisioner.provision()

        val total = actions.count { it.status != Status.SKIPPED }
        val ended = actions.count { it.status == Status.ENDED }
        val failed = actions.count { it.status == Status.FAILED }
        val instances = actions.map { it.step.instance }.toSet()

        if (total > 0) {
            aem.notifier.notify("Instances provisioned", "Performed $total steps(s)" +
                    " ($ended ended, $failed failed) on ${instances.size} instance(s).")
        } else {
            aem.logger.info("No actions to perform / all instances provisioned.")
        }
    }

    fun provisioner(options: Provisioner.() -> Unit) {
        provisioner.apply(options)
    }

    fun step(id: String, options: Step.() -> Unit) = provisioner.step(id, options)

    init {
        description = "Configures instances only in concrete circumstances (only once, after some time etc)"
    }

    companion object {

        const val NAME = "instanceProvision"
    }
}
