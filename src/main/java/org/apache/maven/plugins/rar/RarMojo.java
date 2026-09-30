/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */
package org.apache.maven.plugins.rar;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;

import org.apache.maven.api.Dependency;
import org.apache.maven.api.PathScope;
import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.di.Inject;
import org.apache.maven.api.plugin.Log;
import org.apache.maven.api.plugin.MojoException;
import org.apache.maven.api.plugin.annotations.Mojo;
import org.apache.maven.api.plugin.annotations.Parameter;
import org.apache.maven.api.services.DependencyResolver;
import org.apache.maven.api.services.ProjectManager;
import org.apache.maven.shared.archiver.MavenArchiveConfiguration;
import org.apache.maven.shared.archiver.MavenArchiver;
import org.apache.maven.shared.filtering.MavenFilteringException;
import org.apache.maven.shared.filtering.MavenResourcesExecution;
import org.apache.maven.shared.filtering.MavenResourcesFiltering;
import org.apache.maven.shared.filtering.Resource;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.jar.JarArchiver;
import org.codehaus.plexus.archiver.util.DefaultFileSet;

/**
 * Builds J2EE Resource Adapter Archive (RAR) files.
 *
 * @author <a href="stephane.nicoll@gmail.com">Stephane Nicoll</a>
 * @version $Id$
 */
@Mojo(name = "rar", defaultPhase = "package")
public class RarMojo implements org.apache.maven.api.plugin.Mojo {
    private static final String RA_XML_URI = "META-INF/ra.xml";

    /**
     * Single directory for extra files to include in the RAR.
     */
    @Parameter(defaultValue = "${project.basedir}/src/main/rar", required = true)
    private Path rarSourceDirectory;

    /**
     * The location of the ra.xml file to be used within the rar file.
     */
    @Parameter(defaultValue = "${project.basedir}/src/main/rar/META-INF/ra.xml")
    private Path raXmlFile;

    /**
     * Specify if the generated jar file of this project should be
     * included in the rar file ; default is true.
     */
    @Parameter(defaultValue = "true")
    private boolean includeJar;

    /**
     * The location of the manifest file to be used within the rar file.
     */
    @Parameter(defaultValue = "${project.basedir}/src/main/rar/META-INF/MANIFEST.MF")
    private Path manifestFile;

    /**
     * Directory that resources are copied to during the build.
     */
    @Parameter(defaultValue = "${project.build.directory}/${project.build.finalName}", required = true)
    private String workDirectory;

    /**
     * The directory for the generated RAR.
     */
    @Parameter(defaultValue = "${project.build.directory}", required = true)
    private Path outputDirectory;

    /**
     * The name of the RAR file to generate.
     */
    @Parameter(defaultValue = "${project.build.finalName}", required = true, readonly = true)
    private String finalName;

    /**
     * Classifier to add to the artifact generated. If given, the artifact will be attached.
     *
     * If this is not given, it will merely be written to the output directory
     * according to the finalName.
     *
     * @since 2.4
     */
    @Parameter(property = "maven.rar.classifier", defaultValue = "")
    private String classifier;

    /**
     * The archive configuration to use.
     * See <a href="https://maven.apache.org/shared/maven-archiver/index.html">Maven Archiver Reference</a>.
     */
    @Parameter
    private MavenArchiveConfiguration archive = new MavenArchiveConfiguration();

    /**
     * Allow filtering of {@link rarSourceDirectory}.
     *
     * @since 2.3
     */
    @Parameter(property = "maven.rar.filterRarSourceDirectory", defaultValue = "false")
    private boolean filterRarSourceDirectory;

    /**
     * @since 2.3
     */
    @Inject
    protected Session session;

    /**
     * @since 2.3
     */
    @Parameter(property = "encoding", defaultValue = "${project.build.sourceEncoding}")
    protected String encoding;

    /**
     * Whether to escape backslashes and colons in windows-style paths.
     *
     * @since 2.3
     */
    @Parameter(property = "maven.resources.escapeWindowsPaths", defaultValue = "true")
    protected boolean escapeWindowsPaths;

    /**
     * Expression preceded with the String won't be interpolated
     * \${foo} will be replaced with ${foo}
     *
     * @since 2.3
     */
    @Parameter(property = "maven.resources.escapeString")
    protected String escapeString;

    /**
     * Overwrite existing files even if the destination files are newer.
     *
     * @since 2.3
     */
    @Parameter(property = "maven.resources.overwrite", defaultValue = "false")
    private boolean overwrite;

    /**
     * Copy any empty directories included in the Resources.
     *
     * @since 2.3
     */
    @Parameter(property = "maven.resources.includeEmptyDirs", defaultValue = "false")
    protected boolean includeEmptyDirs;

    /**
     * Stop searching endToken at the end of line.
     *
     * @since 2.3
     */
    @Parameter(property = "maven.resources.supportMultiLineFiltering", defaultValue = "false")
    private boolean supportMultiLineFiltering;

    /**
     * @since 2.3
     */
    @Parameter(defaultValue = "true")
    protected boolean useDefaultDelimiters;

    /**
     * <p>
     * Set of delimiters for expressions to filter within the resources. These delimiters are specified in the
     * form 'beginToken*endToken'. If no '*' is given, the delimiter is assumed to be the same for start and end.
     * </p><p>
     * So, the default filtering delimiters might be specified as:
     * </p>
     * <pre>
     * &lt;delimiters&gt;
     *   &lt;delimiter&gt;${*}&lt;/delimiter&gt;
     *   &lt;delimiter&gt;@&lt;/delimiter&gt;
     * &lt;/delimiters&gt;
     * </pre>
     * <p>
     * Since the '@' delimiter is the same on both ends, we don't need to specify '@*@' (though we can).
     * </p>
     *
     * @since 2.3
     */
    @Parameter
    protected LinkedHashSet<String> delimiters;

    /**
     * <p>
     * The list of extra filter properties files to be used along with System properties,
     * project properties, and filter properties files specified in the POM build/filters section,
     * which should be used for the filtering during the current mojo execution.
     * </p>
     * <p>
     * Normally, these will be configured from a plugin's execution section, to provide a different
     * set of filters for a particular execution. For instance, starting in Maven 2.2.0, you have the
     * option of configuring executions with the id's <code>default-resources</code> and
     * <code>default-testResources</code> to supply different configurations for the two
     * different types of resources. By supplying <code>extraFilters</code> configurations, you
     * can separate which filters are used for which type of resource.
     * </p>
     *
     * @since 2.3
     */
    @Parameter
    protected List<String> filters;

    /**
     * Additional file extensions to not apply filtering (already defined are : jpg, jpeg, gif, bmp, png)
     *
     * @since 2.3
     */
    @Parameter
    protected List<String> nonFilteredFileExtensions;

    /**
     * Extra resource to include in rar archive.
     *
     * @since 2.3
     */
    @Parameter
    protected List<RarResource> rarResources;

    /**
     * Whether to warn if the <code>ra.xml</code> file is missing. Set to <code>false</code>
     * if you want you RAR built without a <code>ra.xml</code> file.
     * This may be useful if you are building against JCA 1.6 or later.
     *
     * @since 2.3
     */
    @Parameter(property = "maven.rar.warnOnMissingRaXml", defaultValue = "true")
    protected boolean warnOnMissingRaXml;

    /**
     * To skip execution of the rar mojo.
     *
     * @since 2.4
     */
    @Parameter(property = "maven.rar.skip")
    private boolean skip;

    /**
     * The maven project.
     */
    @Inject
    private Project project;

    /**
     * Timestamp for reproducible output archive entries, either formatted as ISO 8601
     * <code>yyyy-MM-dd'T'HH:mm:ssXXX</code> or as an int representing seconds since the epoch (like
     * <a href="https://reproducible-builds.org/docs/source-date-epoch/">SOURCE_DATE_EPOCH</a>).
     *
     * @since 3.0.0
     */
    @Parameter(defaultValue = "${project.build.outputTimestamp}")
    private String outputTimestamp;

    /**
     * Resolves the runtime dependencies of the project.
     */
    @Inject
    private DependencyResolver dependencyResolver;

    /**
     * @since 2.3
     */
    @Inject
    protected MavenResourcesFiltering mavenResourcesFiltering;

    @Inject
    private Log log;

    private Path buildDir;

    /**
     * {@inheritDoc}
     */
    @Override
    public void execute() throws MojoException {

        if (skip) {
            log.info("Skipping rar generation.");
            return;
        }

        // Check if jar file is there and if requested, copy it
        try {
            if (includeJar) {
                Path generatedJarFile = outputDirectory.resolve(finalName + ".jar");
                if (Files.exists(generatedJarFile)) {
                    log.info("Including generated jar file[" + generatedJarFile.getFileName() + "]");
                    copyFileToDirectory(generatedJarFile, getBuildDir());
                }
            }
        } catch (IOException e) {
            throw new MojoException("Error copying generated Jar file", e);
        }

        // Copy dependencies
        try {
            Map<Dependency, Path> runtimeDependencies = dependencyResolver
                    .resolve(session, project, PathScope.MAIN_RUNTIME)
                    .getDependencies();
            for (Map.Entry<Dependency, Path> entry : runtimeDependencies.entrySet()) {
                Dependency artifact = entry.getKey();
                // A type which contributes to neither the class-path nor the module-path
                // (for example "pom" or "rar") is not a library and must not be copied.
                if (!artifact.isOptional() && !artifact.getType().getPathTypes().isEmpty()) {
                    log.info("Copying artifact[" + artifact.getGroupId() + ", " + artifact.getArtifactId() + ", "
                            + artifact.getScope().id() + "]");
                    copyFileToDirectory(entry.getValue(), getBuildDir());
                }
            }
        } catch (IOException e) {
            throw new MojoException("Error copying RAR dependencies", e);
        }

        resourceHandling();

        // Include custom manifest if necessary
        try {
            includeCustomRaXmlFile();
        } catch (IOException e) {
            throw new MojoException("Error copying ra.xml file", e);
        }

        // Check if connector deployment descriptor is there
        Path ddFile = getBuildDir().resolve(RA_XML_URI);
        if (Files.notExists(ddFile) && warnOnMissingRaXml) {
            log.warn("Connector deployment descriptor: " + ddFile.toAbsolutePath() + " does not exist.");
        }

        String rarClassifier = (classifier == null || classifier.isBlank()) ? null : classifier;
        Path rarFile = getRarFile(outputDirectory, finalName, rarClassifier);
        MavenArchiver archiver = new MavenArchiver();
        archiver.setArchiver(new JarArchiver());
        archiver.setCreatedBy("Maven RAR Plugin", "org.apache.maven.plugins", "maven-rar-plugin");
        archiver.setOutputFile(rarFile.toFile());

        // configure for Reproducible Builds based on outputTimestamp value
        archiver.configureReproducibleBuild(outputTimestamp);

        try {
            // Include custom manifest if necessary
            includeCustomManifestFile();

            Archiver jarArchiver = archiver.getArchiver();
            jarArchiver.addFileSet(DefaultFileSet.fileSet(getBuildDir().toFile())
                    .prefixed("")
                    .includeExclude(null, null)
                    .includeEmptyDirs(jarArchiver.getIncludeEmptyDirs()));
            archiver.createArchive(session, project, archive);
        } catch (IOException e) {
            throw new MojoException("Error assembling RAR", e);
        }

        if (rarClassifier != null) {
            session.getService(ProjectManager.class)
                    .attachArtifact(
                            project,
                            session.createProducedArtifact(
                                    project.getGroupId(),
                                    project.getArtifactId(),
                                    project.getVersion(),
                                    rarClassifier,
                                    null,
                                    "rar"),
                            rarFile);
        } else {
            session.getService(ProjectManager.class)
                    .attachArtifact(project, project.getMainArtifact().orElseThrow(), rarFile);
        }
    }

    private void resourceHandling() throws MojoException {
        Resource resource = new Resource();
        resource.setDirectory(rarSourceDirectory.toAbsolutePath().toString());
        resource.setTargetPath(getBuildDir().toAbsolutePath().toString());
        resource.setFiltering(filterRarSourceDirectory);

        List<Resource> resources = new ArrayList<>();
        resources.add(resource);

        if (rarResources != null && !rarResources.isEmpty()) {
            resources.addAll(rarResources);
        }

        MavenResourcesExecution mavenResourcesExecution =
                new MavenResourcesExecution(resources, getBuildDir(), project, encoding, filters, List.of(), session);

        mavenResourcesExecution.setEscapeWindowsPaths(escapeWindowsPaths);

        // never include project build filters in this call, since we've already accounted for the POM build filters
        // above, in getCombinedFiltersList().
        mavenResourcesExecution.setInjectProjectBuildFilters(false);

        mavenResourcesExecution.setEscapeString(escapeString);
        mavenResourcesExecution.setOverwrite(overwrite);
        mavenResourcesExecution.setIncludeEmptyDirs(includeEmptyDirs);
        mavenResourcesExecution.setSupportMultiLineFiltering(supportMultiLineFiltering);
        mavenResourcesExecution.setDelimiters(delimiters, useDefaultDelimiters);

        if (nonFilteredFileExtensions != null) {
            mavenResourcesExecution.setNonFilteredFileExtensions(nonFilteredFileExtensions);
        }
        try {
            mavenResourcesFiltering.filterResources(mavenResourcesExecution);
        } catch (MavenFilteringException e) {
            throw new MojoException("Error copying RAR resources", e);
        }
    }

    /**
     * @return the buildDir
     */
    protected Path getBuildDir() {
        if (buildDir == null) {
            buildDir = Path.of(workDirectory);
        }
        return buildDir;
    }

    /**
     * @param basedir the base directory
     * @param finalName the final name
     * @param classifier the classifier
     * @return the resulting file which contains classifier
     */
    protected static Path getRarFile(Path basedir, String finalName, String classifier) {
        if (classifier == null) {
            classifier = "";
        } else if (!classifier.trim().isEmpty() && !classifier.startsWith("-")) {
            classifier = "-" + classifier;
        }

        return basedir.resolve(finalName + classifier + ".rar");
    }

    private void includeCustomManifestFile() throws IOException {
        Path customManifestFile = manifestFile;
        if (Files.notExists(customManifestFile)) {
            log.info("Could not find manifest file: " + manifestFile + " - Generating one");
        } else {
            log.info("Including custom manifest file[" + customManifestFile + "]");
            archive.setManifestFile(customManifestFile);
            copyFileToDirectory(customManifestFile, getBuildDir().resolve("META-INF"));
        }
    }

    private void includeCustomRaXmlFile() throws IOException {
        if (raXmlFile == null) {
            return;
        }
        if (Files.exists(raXmlFile)) {
            log.info("Using ra.xml " + raXmlFile);
            copyFileToDirectory(raXmlFile, getBuildDir().resolve("META-INF"));
        }
    }

    private static void copyFileToDirectory(Path source, Path directory) throws IOException {
        Files.createDirectories(directory);
        Files.copy(
                source,
                directory.resolve(source.getFileName()),
                StandardCopyOption.REPLACE_EXISTING,
                StandardCopyOption.COPY_ATTRIBUTES);
    }
}
