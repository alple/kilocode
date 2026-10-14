import org.gradle.api.DefaultTask
import org.gradle.api.GradleException
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.file.RegularFileProperty
import org.gradle.api.tasks.InputDirectory
import org.gradle.api.tasks.OutputFile
import org.gradle.api.tasks.TaskAction
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

abstract class StageRepoCliTask : DefaultTask() {
    // Declared as an input so unchanged CLI trees skip the re-zip: the archive's content stamp
    // then stays stable across IDE runs and the backend skips re-extracting the binary.
    @get:InputDirectory
    abstract val bin: DirectoryProperty

    @get:OutputFile
    abstract val archive: RegularFileProperty

    @TaskAction
    fun run() {
        val dir = bin.asFile.get()
        val exe = File(dir, exe())
        if (!exe.isFile) {
            throw GradleException(
                "Repo CLI binary not found at ${exe.absolutePath}. Run ./gradlew :backend:buildRepoCli " +
                    "(or bun run script/build.ts --single --skip-install in packages/opencode) first."
            )
        }

        val out = archive.get().asFile
        val platform = platform()
        out.parentFile.mkdirs()
        ZipOutputStream(out.outputStream().buffered()).use { zip ->
            dir.walkTopDown()
                .filter { it.isFile }
                .forEach { file ->
                    val name = "$platform/bin/${file.relativeTo(dir).invariantSeparatorsPath}"
                    zip.putNextEntry(ZipEntry(name))
                    file.inputStream().use { it.copyTo(zip) }
                    zip.closeEntry()
                }
        }
    }

    private fun exe() = if (System.getProperty("os.name").lowercase().contains("windows")) "kilo.exe" else "kilo"

    private fun platform(): String {
        val os = System.getProperty("os.name").lowercase()
        val name = when {
            os.contains("mac") || os.contains("darwin") -> "darwin"
            os.contains("linux") -> "linux"
            os.contains("windows") -> "windows"
            else -> throw GradleException("Unsupported OS: ${System.getProperty("os.name")}")
        }
        val arch = when (System.getProperty("os.arch").lowercase()) {
            "aarch64", "arm64" -> "arm64"
            "x86_64", "amd64" -> "x64"
            else -> throw GradleException("Unsupported architecture: ${System.getProperty("os.arch")}")
        }
        return "$name-$arch"
    }
}
