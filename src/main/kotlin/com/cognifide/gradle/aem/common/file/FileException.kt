package com.cognifide.gradle.aem.common.file

import com.cognifide.gradle.aem.AemException

open class FileException : AemException {

    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(message: String) : super(message)
}
