import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.tasks.Internal
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.time.Instant

/**
 * Fails when the staged repo CLI binary is older than the source it would bundle, so an IDE run
 * cannot silently execute a stale CLI. A CLI built before a schema or behavior change keeps
 * working but misrepresents current source — e.g. it drops unknown config fields on save, which
 * is how a provider clone once lost its `cloneOf` field.
 *
 * Only wired into the runIde and prepareSandbox paths in repo CLI mode (kilo.cli.pinned=false);
 * typecheck and tests are intentionally not gated because they never execute the bundled binary.
 */
abstract class CheckRepoCliFreshTask : DefaultTask() {
    @get:Internal
    abstract val bin: DirectoryProperty

    // Checkout root that contains packages/.
    @get:Internal
    abstract val workspaceRoot: DirectoryProperty

    @TaskAction
    fun run() {
        val root = workspaceRoot.orNull?.asFile ?: return
        val exe = File(bin.asFile.get(), exe())
        val built = exe.takeIf { it.isFile }?.lastModified() ?: return
        val watched = watched(root)
        val newest = watched.maxOfOrNull { it.lastModified() } ?: return
        if (newest <= built) return
        val newer = watched.asSequence()
            .filter { it.lastModified() > built }
            .sortedByDescending { it.lastModified() }
            .take(5)
            .joinToString("\n  ") { it.relativeTo(root).invariantSeparatorsPath }
        throw GradleException(
            "The bundled repo CLI is older than the source it would bundle.\n" +
                "CLI built at ${Instant.ofEpochMilli(built)}, newest source change ${Instant.ofEpochMilli(newest)}.\n" +
                "Newer files (newest first):\n  $newer\n" +
                "Rebuild the CLI, then rerun the command you were running:\n" +
                "  bun .kilo/skills/jetbrains-cli-pin/script/cli-pin.ts regen    (from the repo root)\n" +
                "  ./gradlew :backend:buildRepoCli                              (from packages/kilo-jetbrains/)",
        )
    }

    private fun exe() = if (System.getProperty("os.name").lowercase().contains("windows")) "kilo.exe" else "kilo"

    // Everything the CLI build bundles: all workspace packages except the IDE, webview, and docs
    // ones, plus the root manifest and lockfile. Build outputs and generated or churn-only paths
    // are skipped so cache writes cannot trigger false staleness.
    private fun watched(root: File): List<File> {
        val excluded = setOf("kilo-vscode", "kilo-ui", "kilo-jetbrains", "kilo-docs", "kilo-i18n")
        val skippedDirs = setOf(
            "node_modules", "dist", "build", "test", "tests", "gen", "generated",
            "fixtures", "testdata", "coverage", ".turbo", ".gradle", ".git", "target",
        )
        val skippedSuffixes = setOf(".md", ".map", ".snap", ".tsbuildinfo")
        val packages = File(root, "packages").takeIf { it.isDirectory } ?: return emptyList()
        val manifests = listOf("bun.lock", "bun.lockb", "bunfig.toml", "package.json").mapNotNull { name ->
            File(root, name).takeIf { it.isFile }
        }
        val sources = packages.listFiles { f -> f.isDirectory && f.name !in excluded }.orEmpty().flatMap { pkg ->
            pkg.walkTopDown()
                .onEnter { dir -> dir.name !in skippedDirs }
                .filter { it.isFile }
                .filter { f -> skippedSuffixes.none { f.name.endsWith(it) } }
                .toList()
        }
        return manifests + sources
    }
}
