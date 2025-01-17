package aQute.bnd.gradle

import static aQute.bnd.exporter.executable.ExecutableJarExporter.EXECUTABLE_JAR
import static aQute.bnd.exporter.runbundles.RunbundlesExporter.RUNBUNDLES
import static aQute.bnd.gradle.BndUtils.isGradleCompatible
import static aQute.bnd.gradle.BndUtils.logReport
import static aQute.bnd.gradle.BndUtils.unwrap

import org.gradle.api.GradleException
import org.gradle.api.file.Directory
import org.gradle.api.file.DirectoryProperty
import org.gradle.api.model.ObjectFactory
import org.gradle.api.model.ReplacedBy
import org.gradle.api.provider.Property
import org.gradle.api.provider.Provider
import org.gradle.api.tasks.Input
import org.gradle.api.tasks.OutputDirectory

import aQute.lib.io.IO

/**
 * Export task type for Gradle.
 *
 * <p>
 * This task type can be used to export a bndrun file.
 *
 * <p>
 * Here is examples of using the Export task type:
 * <pre>
 * import aQute.bnd.gradle.Export
 * tasks.register("exportExecutable", Export) {
 *   bndrun = file("my.bndrun")
 *   exporter = "bnd.executablejar"
 * }
 * tasks.register("exportRunbundles", Export) {
 *   bndrun = file("my.bndrun")
 *   exporter = "bnd.runbundles"
 * }
 * </pre>
 *
 * <p>
 * Properties:
 * <ul>
 * <li>bndrun - This is the bndrun file to be exported.
 * This property must be set.</li>
 * <li>bundles - The bundles to added to a FileSetRepository for non-Bnd Workspace builds. The default is
 * "sourceSets.main.runtimeClasspath" plus
 * "configurations.archives.artifacts.files".
 * This must not be used for Bnd Workspace builds.</li>
 * <li>ignoreFailures - If true the task will not fail if the export
 * fails. The default is false.</li>
 * <li>workingDirectory - This is the directory for the export operation.
 * The default for workingDirectory is temporaryDir.</li>
 * <li>destinationDirectory - This is the directory for the output.
 * The default for destinationDirectory is project.base.distsDirectory.dir("executable")
 * if the exporter is "bnd.executablejar", project.base.distsDirectory.dir("runbundles"/bndrun)
 * if the exporter is "bnd.runbundles", and project.base.distsDirectory.dir(task.name)
 * for all other exporters.</li>
 * <li>exporter - The name of the exporter plugin to use.
 * Bnd has two built-in exporter plugins. "bnd.executablejar"
 * exports an executable jar and "bnd.runbundles" exports the
 * -runbundles files. The default is "bnd.executablejar".</li>
 * </ul>
 */
public class Export extends AbstractBndrun {
	/**
	 * @deprecated Replaced by exporter.
	 */
	@ReplacedBy("exporter")
	@Deprecated
	boolean bundlesOnly = false

	/**
	 * The destination directory for the export.
	 *
	 * <p>
	 * The default for destinationDirectory is project.base.distsDirectory.dir("executable")
	 * if the exporter is "bnd.executablejar", project.base.distsDirectory.dir("runbundles"/bndrun)
	 * if the exporter is "bnd.runbundles", and project.base.distsDirectory.dir(task.name)
	 * for all other exporters.
	 */
	@OutputDirectory
	final DirectoryProperty destinationDirectory

	/**
	 * The name of the exporter for this task.
	 *
	 * <p>
	 * Bnd has two built-in exporter plugins. "bnd.executablejar"
	 * exports an executable jar and "bnd.runbundles" exports the
	 * -runbundles files. The default is "bnd.executablejar" unless
	 * bundlesOnly is false when the default is "bnd.runbundles".
	 */
	@Input
	final Property<String> exporter

	/**
	 * Create a Export task.
	 *
	 */
	public Export() {
		super()
		ObjectFactory objects = getProject().getObjects()
		exporter = objects.property(String.class).convention(getProject().provider({getBundlesOnly() ? RUNBUNDLES : EXECUTABLE_JAR}))
		Provider<Directory> distsDirectory = isGradleCompatible("7.1") ? getProject().base.getDistsDirectory()
				: getProject().distsDirectory
		destinationDirectory = objects.directoryProperty().convention(distsDirectory.flatMap({ distsDir ->
			return distsDir.dir(getExporter().map({ exporterName ->
				switch(exporterName) {
					case EXECUTABLE_JAR:
						return "executable"
					case RUNBUNDLES:
						File bndrunFile = unwrap(getBndrun())
						String bndrunName = bndrunFile.getName() - ".bndrun"
						return "runbundles/${bndrunName}"
					default:
						return exporterName
				}
			}))
		}))
	}

	/**
	 * Export the Run object.
	 */
	@Override
	protected void worker(def run) {
		String exporterName = unwrap(getExporter())
		File destinationDirFile = unwrap(getDestinationDirectory())
		getLogger().info("Exporting {} to {} with exporter {}", run.getPropertiesFile(), destinationDirFile, exporterName)
		getLogger().debug("Run properties: {}", run.getProperties())
		try {
			Map.Entry<String, ?> export = run.export(exporterName, Collections.emptyMap())
			if (Objects.nonNull(export)) {
				if (Objects.equals(exporterName, RUNBUNDLES)) {
					def jr = export.getValue()
					try {
						jr.getJar().writeFolder(destinationDirFile)
					} finally {
						IO.close(jr)
					}
				} else {
					def r = export.getValue()
					try {
						File exported = IO.getBasedFile(destinationDirFile, export.getKey())
						OutputStream out = IO.outputStream(exported)
						try {
							r.write(out)
						} finally {
							IO.close(out)
						}
						exported.setLastModified(r.lastModified())
					} finally {
						IO.close(r)
					}
				}
			}
		} finally {
			logReport(run, getLogger())
		}
		if (!isIgnoreFailures() && !run.isOk()) {
			throw new GradleException("${run.getPropertiesFile()} export failure")
		}
	}
}
