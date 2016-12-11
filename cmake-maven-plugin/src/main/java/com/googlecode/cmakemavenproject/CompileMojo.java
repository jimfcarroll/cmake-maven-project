package com.googlecode.cmakemavenproject;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this file except
 * in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under the License
 * is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express
 * or implied. See the License for the specific language governing permissions and limitations under
 * the License.
 */
import static com.googlecode.cmakemavenproject.Mojos.VALID_CLASSIFIERS;
import edu.umd.cs.findbugs.annotations.SuppressFBWarnings;
import java.io.File;
import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.plugins.annotations.LifecyclePhase;
import org.apache.maven.plugins.annotations.Mojo;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.project.MavenProject;

/**
 * Goal which compiles project files generated by CMake.
 *
 * @author Gili Tzabari
 */
@Mojo(name = "compile", defaultPhase = LifecyclePhase.COMPILE)
public class CompileMojo
	extends AbstractMojo
{
	@Parameter(property = "classifier", readonly = true, required = true)
	private String classifier;
	/**
	 * The build configuration (e.g. "Win32|Debug", "x64|Release").
	 */
	@SuppressFBWarnings("UWF_UNWRITTEN_FIELD")
	@Parameter
	private String config;
	/**
	 * The target to build.
	 */
	@Parameter
	private String target;
	/**
	 * The environment variables.
	 */
	@SuppressFBWarnings("UWF_UNWRITTEN_FIELD")
	@Parameter
	private Map<String, String> environmentVariables;
	/**
	 * Extra command-line options to pass to cmake.
	 */
	@SuppressFBWarnings("UWF_UNWRITTEN_FIELD")
	@Parameter
	private List<String> options;
	/**
	 * The directory containing the project file.
	 */
	@SuppressFBWarnings("UWF_UNWRITTEN_FIELD")
	@Parameter(required = true)
	private File projectDirectory;
	@SuppressFBWarnings(
		{
			"UWF_UNWRITTEN_FIELD", "NP_UNWRITTEN_FIELD"
		})
	@Parameter(property = "project", required = true, readonly = true)
	private MavenProject project;

	@Parameter(property = "download.cmake", defaultValue = "true")
	private boolean downloadBinaries;

	@Parameter(property = "cmake.root.dir", defaultValue = "/usr")
	private String cmakeRootDir;

	@Parameter(property = "cmake.child.dir")
	private String cmakeChildDir;

	@Override
	@SuppressFBWarnings("NP_UNWRITTEN_FIELD")
	public void execute()
		throws MojoExecutionException, MojoFailureException
	{
		if (cmakeChildDir == null)
		{
			switch (classifier)
			{
				case "windows-i386":
				case "windows-amd64":
				{
					cmakeChildDir = "bin/cmake.exe";
					break;
				}
				case "linux-i386":
				case "linux-amd64":
				case "linux-arm":
				case "mac-amd64":
				{
					cmakeChildDir = "bin/cmake";
					break;
				}
				default:
				{
					throw new MojoExecutionException("\"classifier\" must be one of " + VALID_CLASSIFIERS +
						"\nActual: " + classifier);
				}
			}
		}
		try
		{
			if (!projectDirectory.exists())
				throw new MojoExecutionException(projectDirectory.getAbsolutePath() + " does not exist");
			if (!projectDirectory.isDirectory())
				throw new MojoExecutionException(projectDirectory.getAbsolutePath() + " must be a directory");

			// We assume that someone else has extracted the cmake binaries beforehand (e.g. the
			// "generate" mojo).
			File cmakeFile = downloadBinaries ? new File(project.getBuild().getDirectory(),
				"dependency/cmake/bin/cmake") : new File(cmakeRootDir + "/" + cmakeChildDir);
			if (!downloadBinaries)
			{
				getLog().info("Configured to use native CMake");
			}

			ProcessBuilder processBuilder = new ProcessBuilder(cmakeFile.getAbsolutePath(),
				"--build", projectDirectory.getPath());
			if (target != null)
				Collections.addAll(processBuilder.command(), "--target", target);
			if (config != null)
				Collections.addAll(processBuilder.command(), "--config", config);
			if (options != null)
				processBuilder.command().addAll(options);

			Map<String, String> env = processBuilder.environment();

			if (environmentVariables != null)
			{
				for (Entry<String, String> entry: environmentVariables.entrySet())
				{
					if (entry.getValue() == null)
					{
						// Linux does not support null values: https://github.com/cmake-maven-project/cmake-maven-project/issues/11
						continue;
					}
					env.put(entry.getKey(), entry.getValue());
				}
			}
			Log log = getLog();
			if (log.isDebugEnabled())
			{
				log.debug("projectDirectory: " + projectDirectory);
				log.debug("target: " + target);
				log.debug("config: " + config);
				log.debug("environment: " + processBuilder.environment());
				log.debug("command-line: " + processBuilder.command());
			}
			int returnCode = Mojos.waitFor(processBuilder);
			if (returnCode != 0)
				throw new MojoExecutionException("Return code: " + returnCode);
		}
		catch (InterruptedException | IOException e)
		{
			throw new MojoExecutionException("", e);
		}
	}
}
