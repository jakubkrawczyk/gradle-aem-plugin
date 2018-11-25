package com.cognifide.gradle.aem.pkg.resolver

import com.cognifide.gradle.aem.api.AemException

class PackageException : AemException {

    constructor(message: String, cause: Throwable) : super(message, cause)

    constructor(message: String) : super(message)
}