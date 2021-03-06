package com.cognifide.gradle.aem.instance.tail.io

import com.cognifide.gradle.aem.instance.tail.Log
import com.cognifide.gradle.aem.instance.tail.LogDestination

class FileDestination(private val instanceName: String, private val logFiles: LogFiles) : LogDestination {

    init {
        logFiles.clearMain(instanceName)
        logFiles.clearIncidents(instanceName)
    }

    override fun dump(logs: List<Log>) {
        if (logs.isEmpty()) {
            return
        }

        logFiles.writeToMain(instanceName) { out ->
            logs.forEach { log ->
                out.append("${log.text}\n")
            }
        }
    }
}
