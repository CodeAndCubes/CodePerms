import org.gradle.api.tasks.bundling.AbstractArchiveTask

plugins {
	id("com.gtnewhorizons.gtnhconvention")
	id("com.mrleonardos.codesides") version "1.0.0"
}

codeSides {
	archiveBaseName.set(project.property("modName") as String)
	inputJar.set(
		tasks.named<AbstractArchiveTask>("reobfJar")
			.flatMap { it.archiveFile })
	client.set(false)
}
