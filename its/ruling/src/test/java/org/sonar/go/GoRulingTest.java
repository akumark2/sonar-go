/*
 * SonarQube Go Plugin
 * Copyright (C) 2018-2018 SonarSource SA
 * mailto:info AT sonarsource DOT com
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU Lesser General Public
 * License as published by the Free Software Foundation; either
 * version 3 of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonar.go;

import com.sonar.orchestrator.Orchestrator;
import com.sonar.orchestrator.OrchestratorBuilder;
import com.sonar.orchestrator.build.SonarScanner;
import com.sonar.orchestrator.locator.FileLocation;
import java.io.File;
import java.nio.file.Files;
import java.util.Collections;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.sonarsource.analyzer.commons.ProfileGenerator;

import static org.assertj.core.api.Assertions.assertThat;

public class GoRulingTest {

  private static Orchestrator orchestrator;

  @BeforeAll
  static void setUp() {
    OrchestratorBuilder orchestratorBuilder = Orchestrator.builderEnv()
      .setOrchestratorProperty("litsVersion", "0.6")
      .addPlugin("lits");
    String isQA = System.getenv("SONARSOURCE_QA");
    if ("true".equals(isQA)) {
      // when we run QA we want to test artifact published on repox
      // version is computed in top-level build.gradle and set as system property in build.gradle for ruling test
      orchestratorBuilder.addMavenPlugin("org.sonarsource.go", "sonar-go-plugin", "goVersion");
    } else {
      orchestratorBuilder.addPlugin(FileLocation.byWildcardMavenFilename(
        new File("../../sonar-go-plugin/build/libs"), "sonar-go-plugin-*-all.jar"));
    }
    orchestrator = orchestratorBuilder.build();
    orchestrator.start();
    ProfileGenerator.RulesConfiguration rulesConfiguration = new ProfileGenerator.RulesConfiguration();
    File profile = ProfileGenerator.generateProfile(GoRulingTest.orchestrator.getServer().getUrl(), "go", "go", rulesConfiguration, Collections.emptySet());
    orchestrator.getServer().restoreProfile(FileLocation.of(profile));
  }

  @Test
  public void test() throws Exception {
    orchestrator.getServer().provisionProject("project", "project");
    orchestrator.getServer().associateProjectToQualityProfile("project", "go", "rules");

    File litsDifferencesFile = FileLocation.of("build/differences").getFile();
    SonarScanner build = SonarScanner.create(FileLocation.of("src/test/ruling-test-sources").getFile())
      .setProjectKey("project")
      .setProjectName("project")
      .setProjectVersion("1")
      .setLanguage("go")
      .setSourceDirs("./")
      .setSourceEncoding("utf-8")
      .setProperty("sonar.inclusions", "**/*.go")
      .setProperty("sonar.tests", ".")
      .setProperty("sonar.test.inclusions", "**/*_test.go")
      .setProperty("sonar.analysis.mode", "preview")
      .setProperty("dump.old", FileLocation.of("src/test/expected").getFile().getAbsolutePath())
      .setProperty("dump.new", FileLocation.of("build/actual").getFile().getAbsolutePath())
      .setProperty("lits.differences", litsDifferencesFile.getAbsolutePath())
      .setProperty("sonar.cpd.skip", "true")
      .setEnvironmentVariable("SONAR_RUNNER_OPTS", "-Xmx1024m");

    orchestrator.executeBuild(build);

    String litsDifference = new String(Files.readAllBytes(litsDifferencesFile.toPath()));
    assertThat(litsDifference).isEmpty();
  }

  @AfterAll
  static void tearDown() {
    orchestrator.stop();
  }
}
