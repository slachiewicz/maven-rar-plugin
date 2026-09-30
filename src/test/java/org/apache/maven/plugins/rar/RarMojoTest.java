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
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Enumeration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

import org.apache.maven.api.Dependency;
import org.apache.maven.api.DependencyScope;
import org.apache.maven.api.JavaPathType;
import org.apache.maven.api.PathScope;
import org.apache.maven.api.Project;
import org.apache.maven.api.Session;
import org.apache.maven.api.Type;
import org.apache.maven.api.model.Build;
import org.apache.maven.api.model.Model;
import org.apache.maven.api.services.DependencyResolver;
import org.apache.maven.api.services.DependencyResolverResult;
import org.apache.maven.testing.plugin.Basedir;
import org.apache.maven.testing.plugin.InjectMojo;
import org.apache.maven.testing.plugin.MojoTest;
import org.apache.maven.testing.plugin.stubs.ProducedArtifactStub;
import org.apache.maven.testing.plugin.stubs.ProjectStub;
import org.junit.jupiter.api.Test;

import static org.apache.maven.testing.plugin.MojoExtension.getBasedir;
import static org.apache.maven.testing.plugin.MojoExtension.setVariableValueToObject;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * @author <a href="mailto:aramirez@apache.org">Allan Ramirez</a>
 */
@MojoTest
class RarMojoTest {

    @Test
    @Basedir("${basedir}")
    @InjectMojo(goal = "rar", pom = "classpath:/unit/basic-rar-test/plugin-config.xml")
    void rarTestEnvironment(RarMojo mojo) {
        assertNotNull(mojo);
    }

    @Test
    @Basedir("${basedir}")
    @InjectMojo(goal = "rar", pom = "classpath:/unit/basic-rar-test/plugin-config.xml")
    void basicRar(RarMojo mojo) throws Exception {
        Path outputDir = prepare(mojo, "basic-rar-test", true);

        // include the project jar to the rar
        Path projectJar = Path.of(getBasedir(), "src/test/resources/unit/basic-rar-test/target/test-rar.jar");
        Files.createDirectories(outputDir);
        Files.copy(projectJar, outputDir.resolve(projectJar.getFileName()));

        mojo.execute();

        // check the working directory
        assertEquals(
                Set.of("maven-artifact01-1.0-SNAPSHOT.jar", "maven-artifact02-1.0-SNAPSHOT.jar", "test-rar.jar"),
                listNames(mojo.getBuildDir()));

        // check the generated rar file
        assertEquals(
                Set.of(
                        "META-INF/maven/org.apache.maven.test/maven-rar-test/pom.properties",
                        "META-INF/maven/org.apache.maven.test/maven-rar-test/pom.xml",
                        "META-INF/maven/org.apache.maven.test/maven-rar-test/",
                        "META-INF/maven/org.apache.maven.test/",
                        "META-INF/maven/",
                        "META-INF/MANIFEST.MF",
                        "META-INF/",
                        "maven-artifact01-1.0-SNAPSHOT.jar",
                        "maven-artifact02-1.0-SNAPSHOT.jar",
                        "test-rar.jar"),
                entriesOf(outputDir.resolve("test-rar.rar")));
    }

    @Test
    @Basedir("${basedir}")
    @InjectMojo(goal = "rar", pom = "classpath:/unit/basic-rar-with-descriptor/plugin-config.xml")
    void basicRarWithDescriptor(RarMojo mojo) throws Exception {
        Path outputDir = prepare(mojo, "basic-rar-with-descriptor", true);

        mojo.execute();

        assertEquals(
                Set.of("ra.xml", "maven-artifact01-1.0-SNAPSHOT.jar", "maven-artifact02-1.0-SNAPSHOT.jar", "META-INF"),
                listNames(mojo.getBuildDir()));

        assertEquals(
                Set.of(
                        "META-INF/maven/org.apache.maven.test/maven-rar-test/pom.properties",
                        "META-INF/maven/org.apache.maven.test/maven-rar-test/pom.xml",
                        "META-INF/maven/org.apache.maven.test/maven-rar-test/",
                        "META-INF/maven/org.apache.maven.test/",
                        "META-INF/maven/",
                        "META-INF/MANIFEST.MF",
                        "META-INF/ra.xml",
                        "META-INF/",
                        "maven-artifact01-1.0-SNAPSHOT.jar",
                        "maven-artifact02-1.0-SNAPSHOT.jar"),
                entriesOf(outputDir.resolve("test-rar-with-descriptor.rar")));
    }

    @Test
    @Basedir("${basedir}")
    @InjectMojo(goal = "rar", pom = "classpath:/unit/basic-rar-with-manifest/plugin-config.xml")
    void basicRarWithManifest(RarMojo mojo) throws Exception {
        Path outputDir = prepare(mojo, "basic-rar-with-manifest", true);

        mojo.execute();

        assertEquals(
                Set.of(
                        "ra.xml",
                        "maven-artifact01-1.0-SNAPSHOT.jar",
                        "maven-artifact02-1.0-SNAPSHOT.jar",
                        "META-INF",
                        "MANIFEST.MF"),
                listNames(mojo.getBuildDir()));

        assertEquals(
                Set.of(
                        "META-INF/maven/org.apache.maven.test/maven-rar-test/pom.properties",
                        "META-INF/maven/org.apache.maven.test/maven-rar-test/pom.xml",
                        "META-INF/maven/org.apache.maven.test/maven-rar-test/",
                        "META-INF/maven/org.apache.maven.test/",
                        "META-INF/maven/",
                        "META-INF/MANIFEST.MF",
                        "META-INF/ra.xml",
                        "META-INF/",
                        "maven-artifact01-1.0-SNAPSHOT.jar",
                        "maven-artifact02-1.0-SNAPSHOT.jar"),
                entriesOf(outputDir.resolve("test-rar-with-manifest.rar")));
    }

    /**
     * Optional dependencies and dependencies which are not libraries (for example a {@code pom} or a {@code rar})
     * must not be copied into the archive.
     */
    @Test
    @Basedir("${basedir}")
    @InjectMojo(goal = "rar", pom = "classpath:/unit/basic-rar-with-descriptor/plugin-config.xml")
    void skipsOptionalAndNonLibraryDependencies(RarMojo mojo) throws Exception {
        prepare(mojo, "basic-rar-with-descriptor", false);
        Map<Dependency, Path> dependencies = new LinkedHashMap<>();
        dependencies.put(dependency("maven-artifact01", false, true), remoteJar("maven-artifact01"));
        dependencies.put(dependency("maven-artifact02", true, true), remoteJar("maven-artifact02"));
        dependencies.put(dependency("maven-rar-dep", false, false), remoteJar("maven-artifact02"));
        resolve(mojo, dependencies);

        mojo.execute();

        List<String> jars = listNames(mojo.getBuildDir()).stream()
                .filter(n -> n.endsWith(".jar"))
                .collect(Collectors.toList());
        assertEquals(List.of("maven-artifact01-1.0-SNAPSHOT.jar"), jars);
    }

    /**
     * Configures the mojo the way the former {@code plugin-config.xml} files did, since the test harness does not
     * interpolate {@code ${basedir}} in the plugin configuration.
     */
    private Path prepare(RarMojo mojo, String unit, boolean withDependencies) throws Exception {
        String finalName;
        boolean includeJar;
        String workDir;
        switch (unit) {
            case "basic-rar-test":
                finalName = "test-rar";
                includeJar = true;
                workDir = "rar-test";
                break;
            case "basic-rar-with-descriptor":
                finalName = "test-rar-with-descriptor";
                includeJar = false;
                workDir = "rar-test-with-descriptor";
                break;
            default:
                finalName = "test-rar-with-manifest";
                includeJar = false;
                workDir = "rar-test-with-manifest";
                break;
        }
        Path basedir = Path.of(getBasedir());
        Path unitDir = basedir.resolve("src/test/resources/unit").resolve(unit);
        Path outputDir = basedir.resolve("target/unit").resolve(unit).resolve("target");
        deleteRecursively(outputDir);

        setVariableValueToObject(mojo, "rarSourceDirectory", unitDir.resolve("src/main/rar"));
        setVariableValueToObject(mojo, "raXmlFile", unitDir.resolve("src/main/rar/META-INF/ra.xml"));
        setVariableValueToObject(mojo, "manifestFile", unitDir.resolve("src/main/rar/META-INF/MANIFEST.MF"));
        setVariableValueToObject(mojo, "includeJar", includeJar);
        setVariableValueToObject(
                mojo, "workDirectory", outputDir.resolve(workDir).toString());
        setVariableValueToObject(mojo, "outputDirectory", outputDir);
        setVariableValueToObject(mojo, "finalName", finalName);

        Model model = Model.newBuilder()
                .groupId("org.apache.maven.test")
                .artifactId("maven-rar-test")
                .version("1.0-SNAPSHOT")
                .packaging("rar")
                .build(Build.newBuilder().directory(outputDir.toString()).build())
                .build();
        ProjectStub project = new ProjectStub();
        project.setModel(model);
        project.setBasedir(basedir);
        project.setPomPath(unitDir.resolve("plugin-config.xml"));
        project.setMainArtifact(
                new ProducedArtifactStub("org.apache.maven.test", "maven-rar-test", "", "1.0-SNAPSHOT", "rar"));
        setVariableValueToObject(mojo, "project", project);

        if (withDependencies) {
            Map<Dependency, Path> dependencies = new LinkedHashMap<>();
            dependencies.put(dependency("maven-artifact01", false, true), remoteJar("maven-artifact01"));
            dependencies.put(dependency("maven-artifact02", false, true), remoteJar("maven-artifact02"));
            resolve(mojo, dependencies);
        }
        return outputDir;
    }

    private static void deleteRecursively(Path directory) throws IOException {
        if (Files.notExists(directory)) {
            return;
        }
        try (Stream<Path> files = Files.walk(directory)) {
            for (Path file : files.sorted(Comparator.reverseOrder()).collect(Collectors.toList())) {
                Files.delete(file);
            }
        }
    }

    /** Makes the mojo see the given runtime dependencies as the result of the dependency resolution. */
    private static void resolve(RarMojo mojo, Map<Dependency, Path> dependencies) throws Exception {
        DependencyResolverResult result = mock(DependencyResolverResult.class);
        when(result.getDependencies()).thenReturn(dependencies);
        DependencyResolver resolver = mock(DependencyResolver.class);
        when(resolver.resolve(any(Session.class), any(Project.class), eq(PathScope.MAIN_RUNTIME)))
                .thenReturn(result);
        setVariableValueToObject(mojo, "dependencyResolver", resolver);
    }

    private static Path remoteJar(String artifactId) {
        return Path.of(
                getBasedir(),
                "src/test/remote-repo/org/apache/maven/test/" + artifactId + "/1.0-SNAPSHOT/" + artifactId
                        + "-1.0-SNAPSHOT.jar");
    }

    private static Dependency dependency(String artifactId, boolean optional, boolean library) {
        Type type = mock(Type.class);
        when(type.getPathTypes()).thenReturn(library ? Set.of(JavaPathType.CLASSES) : Set.of());
        Dependency dependency = mock(Dependency.class);
        when(dependency.getGroupId()).thenReturn("org.apache.maven.test");
        when(dependency.getArtifactId()).thenReturn(artifactId);
        when(dependency.getType()).thenReturn(type);
        when(dependency.getScope()).thenReturn(DependencyScope.COMPILE);
        when(dependency.isOptional()).thenReturn(optional);
        return dependency;
    }

    /** Names of all files and directories below the given directory, recursively. */
    private static Set<String> listNames(Path directory) throws IOException {
        assertTrue(Files.isDirectory(directory));
        try (Stream<Path> files = Files.walk(directory)) {
            return files.filter(p -> !p.equals(directory))
                    .map(p -> p.getFileName().toString())
                    .collect(Collectors.toSet());
        }
    }

    private static Set<String> entriesOf(Path rarFile) throws IOException {
        assertTrue(Files.exists(rarFile));
        List<String> names = new ArrayList<>();
        try (ZipFile rar = new ZipFile(rarFile.toFile())) {
            Enumeration<? extends ZipEntry> entries = rar.entries();
            assertTrue(entries.hasMoreElements());
            while (entries.hasMoreElements()) {
                names.add(entries.nextElement().getName());
            }
        }
        return Set.copyOf(names);
    }
}
