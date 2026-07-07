package mihon.desktop.bridge

import kotlinx.coroutines.runBlocking

/**
 * Manual smoke test for [SuwayomiClient] against a running Suwayomi-Server on localhost:4567.
 * Run with: ./gradlew :desktop:smokeTest
 */
fun main() = runBlocking {
    val client = SuwayomiClient()
    println("→ getSources()")
    val sources = client.getSources()
    println("  sources: ${sources.size}")
    val src = sources.firstOrNull { it.name == "MangaDex" && it.lang == "en" }
        ?: sources.first { it.id != "0" }
    println("  using source: ${src.name} (${src.lang}) id=${src.id}")

    println("→ fetchSourceManga(POPULAR, page 1)")
    val page = client.fetchSourceManga(src.id, MangaFetchType.POPULAR, 1)
    println("  hasNextPage=${page.hasNextPage} count=${page.mangas.size}")
    page.mangas.take(3).forEach { println("    - ${it.id} ${it.title} thumb=${it.thumbnailUrl}") }

    val first = page.mangas.first()
    println("→ fetchManga(${first.id})")
    val details = client.fetchManga(first.id)
    println("  ${details.title} | author=${details.author} | status=${details.status}")

    println("✔ smoke test OK")
}
