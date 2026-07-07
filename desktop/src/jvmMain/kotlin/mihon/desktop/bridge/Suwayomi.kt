package mihon.desktop.bridge

/**
 * Process-wide access to the Suwayomi bridge. A minimal stand-in for DI (Injekt on Android) until
 * a proper desktop DI/preferences layer lands; the base URL is configurable here.
 */
object Suwayomi {
    @Volatile
    var config: SuwayomiConfig = SuwayomiConfig()
        private set

    @Volatile
    var client: SuwayomiClient = SuwayomiClient(config)
        private set

    fun configure(newConfig: SuwayomiConfig) {
        config = newConfig
        client = SuwayomiClient(newConfig)
    }
}
