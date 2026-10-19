package osrsps

import io.github.oshai.kotlinlogging.KotlinLogging

private val logger = KotlinLogging.logger {}

fun main() {
    logger.info { "OSRSPS server skeleton, revision ${Revision.MAJOR}, Java ${Runtime.version()}" }
}
