package mihon.desktop.bridge

import kotlinx.coroutines.delay
import java.io.File
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration

/**
 * Starts, monitors and stops an embedded Suwayomi-Server as a child JVM process.
 *
 * Packaging strategy: the Suwayomi jar and (optionally) a dedicated JRE are shipped inside the app's
 * resource directory (Compose sets `compose.application.resources.dir`). At launch we:
 *  1. Probe the GraphQL endpoint — if a server is already up (dev machine, or a previous instance),
 *     we reuse it instead of spawning a duplicate.
 *  2. Otherwise spawn `java -jar Suwayomi-Server.jar` with the data dir pinned to the app's local
 *     share folder, the web UI and browser auto-open disabled.
 *  3. Poll until the endpoint answers, then hand control back to the UI.
 * On shutdown the child process is destroyed.
 */
object SuwayomiServerManager {

    /** Where Suwayomi keeps its DB, extensions, downloads. Mirrors the mobile app's private storage. */
    val dataDir: File by lazy {
        val base = System.getenv("XDG_DATA_HOME")?.takeIf { it.isNotBlank() }
            ?: (System.getProperty("user.home") + "/.local/share")
        File(base, "mihon-desktop").also { it.mkdirs() }
    }

    @Volatile
    private var process: Process? = null

    @Volatile
    var reusedExisting: Boolean = false
        private set

    private val probeClient: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(2))
        .build()

    /** True when the GraphQL endpoint answers a trivial query. */
    fun isServerUp(baseUrl: String = SuwayomiConfig.DEFAULT_BASE_URL): Boolean = runCatching {
        val req = HttpRequest.newBuilder()
            .uri(URI.create("$baseUrl/api/graphql"))
            .timeout(Duration.ofSeconds(3))
            .header("Content-Type", "application/json")
            .POST(HttpRequest.BodyPublishers.ofString("""{"query":"{ aboutServer { name } }"}"""))
            .build()
        val res = probeClient.send(req, HttpResponse.BodyHandlers.ofString())
        res.statusCode() in 200..299
    }.getOrDefault(false)

    /**
     * Ensures a Suwayomi-Server is reachable at [baseUrl]. Reuses a running one, else spawns the
     * bundled jar and waits up to [timeoutSeconds] for it to come up.
     *
     * @param onLog receives child stdout/stderr lines for surfacing in the UI/logs.
     * @return true if a server is reachable when this returns.
     */
    suspend fun ensureRunning(
        baseUrl: String = SuwayomiConfig.DEFAULT_BASE_URL,
        timeoutSeconds: Long = 90,
        onLog: (String) -> Unit = ::println,
    ): Boolean {
        if (isServerUp(baseUrl)) {
            reusedExisting = true
            onLog("Suwayomi-Server ya está en ejecución — reutilizando.")
            return true
        }

        val jar = locateServerJar()
        if (jar == null) {
            onLog("No se encontró el jar de Suwayomi-Server (define MIHON_SUWAYOMI_JAR o colócalo en resources).")
            return false
        }
        val javaBin = locateJavaBinary()
        onLog("Arrancando Suwayomi-Server: $javaBin -jar ${jar.absolutePath}")
        onLog("Directorio de datos: ${dataDir.absolutePath}")

        val builder = ProcessBuilder(
            javaBin,
            "-Dsuwayomi.tachidesk.config.server.rootDir=${dataDir.absolutePath}",
            "-Dsuwayomi.tachidesk.config.server.webUIEnabled=false",
            "-Dsuwayomi.tachidesk.config.server.initialOpenInBrowserEnabled=false",
            "-Dsuwayomi.tachidesk.config.server.systemTrayEnabled=false",
            "-jar",
            jar.absolutePath,
        ).apply {
            redirectErrorStream(true)
            directory(dataDir)
        }

        val proc = runCatching { builder.start() }.getOrElse {
            onLog("No se pudo iniciar el proceso: ${it.message}")
            return false
        }
        process = proc

        // Drain output on a daemon thread so the child never blocks on a full pipe.
        Thread {
            proc.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line -> onLog("[suwayomi] $line") }
            }
        }.apply { isDaemon = true; name = "suwayomi-log" }.start()

        // Poll the endpoint until it answers or we exhaust the timeout.
        val deadline = System.nanoTime() + Duration.ofSeconds(timeoutSeconds).toNanos()
        while (System.nanoTime() < deadline) {
            if (!proc.isAlive) {
                onLog("El proceso de Suwayomi-Server terminó (código ${proc.exitValue()}).")
                return false
            }
            if (isServerUp(baseUrl)) {
                onLog("Suwayomi-Server listo.")
                return true
            }
            delay(1000)
        }
        onLog("Tiempo de espera agotado esperando a Suwayomi-Server.")
        return false
    }

    /** Stops the embedded process if we started one. No-op when a pre-existing server was reused. */
    fun stop() {
        val proc = process ?: return
        if (proc.isAlive) {
            proc.destroy()
            if (!proc.waitFor(10, java.util.concurrent.TimeUnit.SECONDS)) {
                proc.destroyForcibly()
            }
        }
        process = null
    }

    /**
     * Resolves the Suwayomi jar. Order: `MIHON_SUWAYOMI_JAR` env → bundled resources dir →
     * `~/suwayomi/Suwayomi-Server.jar` dev fallback.
     */
    private fun locateServerJar(): File? {
        System.getenv("MIHON_SUWAYOMI_JAR")?.let { p ->
            File(p).takeIf { it.isFile }?.let { return it }
        }
        resourcesDir()?.let { res ->
            res.listFiles { f -> f.name.startsWith("Suwayomi-Server") && f.name.endsWith(".jar") }
                ?.firstOrNull()?.let { return it }
            File(res, "Suwayomi-Server.jar").takeIf { it.isFile }?.let { return it }
        }
        File(System.getProperty("user.home"), "suwayomi/Suwayomi-Server.jar")
            .takeIf { it.isFile }?.let { return it }
        return null
    }

    /**
     * Finds a java binary to run the server. Order: `MIHON_JAVA` env → bundled runtime under the
     * resources dir → the JRE running this app → `java` on PATH.
     */
    private fun locateJavaBinary(): String {
        System.getenv("MIHON_JAVA")?.takeIf { File(it).canExecute() }?.let { return it }
        resourcesDir()?.let { res ->
            val bundled = File(res, "suwayomi-runtime/bin/java")
            if (bundled.canExecute()) return bundled.absolutePath
        }
        System.getProperty("java.home")?.let { home ->
            val jb = File(home, "bin/java")
            if (jb.canExecute()) return jb.absolutePath
        }
        return "java"
    }

    /** The app's bundled resource dir (set by Compose at runtime), or null in some dev layouts. */
    private fun resourcesDir(): File? =
        System.getProperty("compose.application.resources.dir")?.let { File(it) }?.takeIf { it.isDirectory }
}
