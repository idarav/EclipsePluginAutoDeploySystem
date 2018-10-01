import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.CopyOption;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.GZIPInputStream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * The following are valid if you are on Windows 10 x64:<br>
 * To compile, use the javac ./ReleaseBuilder.java command.<br>
 * To run, use the java -cp . ReleaseBuilder command.
 * 
 * In order to be able to build linux packages, you have to have Ubuntu bash on
 * your Windows, and an installed JDK in it (sudo apt-get install default-jdk).
 * 
 * @version 1.0.0
 * @author Endre Váradi
 */
public class ReleaseBuilder {
	private static final String projectImporterPlugin = "com.seeq.eclipse.importprojects_1.4.0.jar";

	// Parsed arguments
	private static String version = null;
	private static Path updatesite = null;
	private static Path document = null;
	private static boolean dontZip = false;

	public static void main(String[] args) throws Exception {
		System.out.println("---------------------------");
		System.out.println("| Eclipse Release builder |");
		System.out.println("| Created by Endre Varadi |");
		System.out.println("---------------------------");
		System.out.println();
		
		try {
			parseArguments(args);
		} catch (Exception e) {
			System.err.println(e.getMessage());
			return;
		}

		Path localPath = new File(ReleaseBuilder.class.getProtectionDomain().getCodeSource().getLocation().getPath()).toPath();
		Path runPath = new File(System.getProperty("user.dir")).toPath();
		Path config = localPath.resolve("config/");
		Path eclipses = localPath.resolve("eclipses/");
		Path releases = localPath.resolve("releases/");
		Path projects = localPath.resolve("projects/");
		Path tmp = localPath.resolve("tmp/");

		if (tmp.toFile().exists()) {
			buildStep("Cleaning after previous build");
			deleteFileRecursively(tmp.toFile());
		}

		Path versionRelease = releases.resolve(version);
		if (versionRelease.toFile().exists()) {
			buildStep("Delete previous builds with version " + version);
			deleteFileRecursively(versionRelease.toFile());
		}

		for (File zippedEclipse : eclipses.toFile().listFiles()) {
			if (zippedEclipse.getName().startsWith("_") || !zippedEclipse.isDirectory()) {
				buildStep("Skip " + zippedEclipse.getName());
				continue;
			}

			/*
			 * if( zippedEclipse.getName().endsWith(".zip") ) { buildStep("Unzipping " +
			 * zippedEclipse.getName()); unzip(zippedEclipse.toPath(), tmp); }else if(
			 * zippedEclipse.getName().endsWith(".gz")) { buildStep("Ungzipping " +
			 * zippedEclipse.getName()); ungzip(zippedEclipse.toPath(), tmp); }
			 */
			buildStep("Copying " + zippedEclipse.getName());
			copyRecursively(zippedEclipse.toPath(), tmp);

			buildStep("Determining platform of package");
			PackageType packageType = determinePackageType(zippedEclipse);
			if (packageType == PackageType.UNKNOWN) {
				System.err.println("Could not determine the platform of package.");
				return;
			}

			buildStep("Editing eclipse.ini");
			Path eclipse = tmp.resolve("eclipse/");
			Path ini = eclipse.resolve("eclipse.ini");
			editEclipseIni(ini);

			buildStep("Installing project importer plugin");
			Path pluginsFolder = eclipse.resolve("plugins/");
			Path importerPluginSource = config.resolve(projectImporterPlugin);
			installPlugin(importerPluginSource, pluginsFolder);

			buildStep("Create eclipse-workspace directory if not-exists");
			Path workspace = eclipse.resolve("eclipse-workspace/");
			createDir(workspace);

			buildStep("Copy content of prepared workspace");
			Path preparedWorkspace = config.resolve("eclipse-workspace/");
			for (File file : preparedWorkspace.toFile().listFiles()) {
				copyRecursively(file.toPath(), workspace.resolve(file.getName()));
			}

			buildStep("Copying projects into the workspace directory");
			for (File project : projects.toFile().listFiles()) {
				buildSubStep("Copy project " + project.getName());
				copyRecursively(project.toPath(), workspace.resolve(project.getName()));
			}

			Path eclipseLauncher = eclipse.resolve(packageType.getLauncherFile());

			buildStep("Headless run to finish installation");
			String firstLaunchCommand = preparePath(eclipseLauncher, packageType)
					+ " -nosplash -application com.seeq.eclipse.importprojects.headlessimport -data \""
					+ preparePath(workspace, packageType) + "\"";
			executeCommandLine(firstLaunchCommand, packageType);

			buildStep("Headless run to import projects");
			String importProjectsCommand = preparePath(eclipseLauncher, packageType)
					+ " -nosplash -application com.seeq.eclipse.importprojects.headlessimport -data \""
					+ preparePath(workspace, packageType) + "\" -import \"" + preparePath(workspace, packageType)
					+ "\"";
			executeCommandLine(importProjectsCommand, packageType);

			buildStep("Uninstalling project importer plugin");
			Path importerPluginInstalled = pluginsFolder.resolve(projectImporterPlugin);
			Files.delete(importerPluginInstalled);

			if (updatesite != null) {
				buildStep("Installing updatesite ");
				Path updatesiteSource = runPath.resolve(updatesite);
				Path updatesitePluginsSource = updatesiteSource.resolve("plugins");

				for (File file : updatesitePluginsSource.toFile().listFiles()) {
					installPlugin(file.toPath(), pluginsFolder);
				}
			}

			if (document != null) {
				buildStep("Copying document " + document.getFileName());
				Path documentSource = runPath.resolve(document);
				Path documentDestination = eclipse.resolve(document.getFileName());
				Files.copy(documentSource, documentDestination);
			}

			String releaseName = "XM_plugin_" + packageType.getBuildName() + "_v" + version;
			if (dontZip) {
				buildStep("Copy release " + releaseName);
				Path copiedRelease = versionRelease.resolve(releaseName);
				copiedRelease.toFile().mkdirs();
				for (File file : eclipse.toFile().listFiles()) {
					copyRecursively(file.toPath(), copiedRelease.resolve(file.getName()));
				}
			} else {
				String zippedName = releaseName + ".zip";
				buildStep("Zipping " + zippedName);
				Path zippedRelease = versionRelease.resolve(zippedName);
				zipDirectory(eclipse, zippedRelease);
			}

			buildStep("Cleaning after build");
			deleteFileRecursively(tmp.toFile());

			buildStep("Done");
		}
	}

	private static void buildStep(String message) {
		System.out.print("* ");
		System.out.println(message);
	}

	private static void buildSubStep(String message) {
		System.out.print("  ");
		System.out.println(message);
	}

	private static void parseArguments(String[] args) throws Exception {
		// basic check
		if (args.length == 0 || args.length % 2 != 0 || (args.length == 1 && args[0].equals("-h"))) {
			StringBuilder sb = new StringBuilder();
			sb.append("Possible arguments:\n");
			sb.append("-v\tbuild version number *REQUIRED*\n");
			sb.append("\tIt should match the following pattern: 1.0.0.rev0\n");
			sb.append("-u\tpath to updatesite\n");
			sb.append("\tIt can be a relative or an absolute path.\n");
			sb.append("-d\tpath to document to be added to the build\n");
			sb.append("\tIt can be a relative or an absolute path.\n");
			sb.append("-z\twhether should the releases be zipped\n");
			sb.append("\tIt can be 'y' for yes or 'n' for no\n");
			throw new Exception(sb.toString());
		}

		// parse arguments
		for (int i = 0; i < args.length; i += 2) {
			String key = args[i].substring(1);
			String value = args[i + 1];

			switch (key) {
			case "v":
				if (!value.matches("\\d+\\.\\d+\\.\\d+\\.rev\\d+")) {
					throw new Exception(
							"ERROR: Not accepted version number. Version must be something like this: 1.0.0.rev0");
				}
				version = value;
				break;
			case "u":
				Path dir = Paths.get(value);
				if (!dir.toFile().exists()) {
					throw new Exception("ERROR: Updatesite directory not exists");
				}
				if (!dir.toFile().isDirectory()) {
					throw new Exception("ERROR: The given path to updatesite is not pointing to a directory");
				}
				updatesite = dir;
				break;
			case "d":
				Path doc = Paths.get(value);
				if (!doc.toFile().exists()) {
					throw new Exception("ERROR: Document does not exists");
				}
				if (doc.toFile().isDirectory()) {
					throw new Exception("ERROR: The given path to document is pointing to a directory");
				}
				document = doc;
				break;
			case "z":
				if (value.equals("n")) {
					dontZip = true;
				} else if (!value.equals("y")) {
					throw new Exception("ERROR: Not accepted value for -z argument. It can only be 'y' or 'n'");
				}
				break;
			default:
				throw new Exception("ERROR: Unrecognizable launch argument: " + key);
			}
		}

		// check if requeired fileds are set
		if (version == null) {
			throw new Exception("ERROR: Version must be set. For more information launch with the -h argument.");
		}
	}

	private static PackageType determinePackageType(File eclipsePackage) throws IOException {
		PackageType packageType = PackageType.UNKNOWN;
		String name = eclipsePackage.getName();

		if (name.contains("win32-x86_64")) {
			packageType = PackageType.WINDOWS64;
		} else if (name.contains("win32")) {
			packageType = PackageType.WINDOWS32;
		} else if (name.contains("linux-gtk-x86_64")) {
			packageType = PackageType.LINUX64;
		} else if (name.contains("linux-gtk")) {
			packageType = PackageType.LINUX32;
		}

		return packageType;
	}

	private enum PackageType {
		UNKNOWN("", Environment.UNKNOWN, ""), WINDOWS32("eclipsec.exe", Environment.WINDOWS, "Windows_x32"),
		WINDOWS64("eclipsec.exe", Environment.WINDOWS, "Windows_x64"),
		LINUX32("eclipse", Environment.LINUX, "Linux_x32"), LINUX64("eclipse", Environment.LINUX, "Linux_x64");

		private final String launcherFile;
		private final Environment environment;
		private final String buildName;

		public enum Environment {
			UNKNOWN, WINDOWS, LINUX;
		}

		private PackageType(String launcherFile, Environment environment, String buildName) {
			this.launcherFile = launcherFile;
			this.environment = environment;
			this.buildName = buildName;
		}

		public String getLauncherFile() {
			return launcherFile;
		}

		public Environment getEnvironment() {
			return environment;
		}

		public String getBuildName() {
			return buildName;
		}
	}

	private static void deleteFileRecursively(File file) {
		if (!file.exists()) {
			return;
		}

		if (file.isDirectory()) {
			for (File subFile : file.listFiles()) {
				deleteFileRecursively(subFile);
			}
		}
		file.delete();
	}

	private static void editEclipseIni(Path ini) throws IOException {
		StringBuilder sb = new StringBuilder();

		String line;
		FileReader reader = new FileReader(ini.toFile());
		BufferedReader br = new BufferedReader(reader);
		while ((line = br.readLine()) != null) {
			if (line.startsWith("-Dosgi.instance.area.default=")) {
				sb.append("-Dosgi.instance.area.default=./eclipse-workspace\n");
				sb.append("-Dorg.osgi.framework.bundle.parent=ext\n");
				sb.append("-Dosgi.framework.extensions=org.eclipse.wst.jsdt.nashorn.extension\n");
			} else {
				sb.append(line);
				sb.append("\n");
			}
		}
		br.close();
		reader.close();

		FileWriter writer = new FileWriter(ini.toFile());
		BufferedWriter bw = new BufferedWriter(writer);
		bw.write(sb.toString());
		bw.close();
		writer.close();
	}

	private static void installPlugin(Path plugin, Path destinationFolder) throws IOException {
		Path destination = destinationFolder.resolve(plugin.getFileName());

		buildSubStep("Install plugin " + plugin.getFileName());

		Files.copy(plugin, destination, StandardCopyOption.REPLACE_EXISTING);
	}

	private static void createDir(Path workspace) throws IOException {
		File file = workspace.toFile();
		if (!file.exists()) {
			file.mkdirs();
		}
	}

	private static void copyRecursively(Path from, Path to) throws IOException {
		File currentFile = from.toFile();

		if (currentFile.isDirectory()) {
			to.toFile().mkdirs();

			for (File file : currentFile.listFiles()) {
				copyRecursively(file.toPath(), to.resolve(file.getName()));
			}
		} else {
			Files.copy(currentFile.toPath(), to, StandardCopyOption.REPLACE_EXISTING);
		}
	}

	private static boolean onWindows() {
		String os = System.getProperty("os.name");
		return os.contains("Windows");
	}

	private static String preparePath(Path path, PackageType packageType) {
		String absolute = path.toString();
		PackageType.Environment environment = packageType.getEnvironment();

		if (onWindows()) {
			if (environment == PackageType.Environment.LINUX) {
				String disk = absolute.substring(0, 1);
				absolute = "/mnt/" + disk.toLowerCase() + absolute.substring(2).replaceAll("\\\\", "/");
			}
		} else {
			System.err.println("ERROR: Not prepared for running on non Windows systems");
		}

		return absolute;
	}

	private static void executeCommandLine(String command, PackageType packageType) throws IOException {
		if (onWindows()) {
			if (packageType.getEnvironment() == PackageType.Environment.LINUX) {
				command = "bash -c \"" + command.replaceAll("\"", "'") + "\"";
			}
		} else {
			System.err.println("ERROR: Not prepared for running on non Windows systems");
		}

		buildSubStep("Execute: " + command);
		Runtime rt = Runtime.getRuntime();
		Process p = rt.exec(command);

		try {
			p.waitFor();
		} catch (Exception e) {
			e.printStackTrace();
		}
	}

	private static void zipDirectory(Path source, Path destination) throws IOException {
		File destinationFile = destination.toFile();
		destinationFile.getParentFile().mkdirs();

		File folderFromZip = source.toFile();
		FileOutputStream fos = new FileOutputStream(destinationFile);
		ZipOutputStream zipOut = new ZipOutputStream(fos);

		for (File file : folderFromZip.listFiles()) {
			zipFile(file, file.getName(), zipOut);
		}
		zipOut.close();
		fos.close();
	}

	private static void zipFile(File fileToZip, String fileName, ZipOutputStream zipOut) throws IOException {
		if (fileToZip.isHidden()) {
			return;
		}
		if (fileToZip.isDirectory()) {
			if (fileName.endsWith("/")) {
				zipOut.putNextEntry(new ZipEntry(fileName));
				zipOut.closeEntry();
			} else {
				zipOut.putNextEntry(new ZipEntry(fileName + "/"));
				zipOut.closeEntry();
			}
			File[] children = fileToZip.listFiles();
			for (File childFile : children) {
				zipFile(childFile, fileName + "/" + childFile.getName(), zipOut);
			}
			return;
		}
		FileInputStream fis = new FileInputStream(fileToZip);
		ZipEntry zipEntry = new ZipEntry(fileName);
		zipOut.putNextEntry(zipEntry);
		byte[] bytes = new byte[1024];
		int length;
		while ((length = fis.read(bytes)) >= 0) {
			zipOut.write(bytes, 0, length);
		}
		fis.close();
	}
}