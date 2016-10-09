/*
 * Copyright (C) 2016 Nicolai Ehemann (en@enlightened.de).
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package de.enlightened.maven.plugin.sqlenumgen;

import com.google.common.jimfs.Jimfs;
import static de.enlightened.maven.plugin.sqlenumgen.SqlEnumGeneratorMojo.VELOCITY_TEMPLATE;
import de.enlightened.maven.plugin.sqlenumgen.configuration.Configuration;
import de.enlightened.maven.plugin.sqlenumgen.configuration.DatabaseCfg;
import de.enlightened.maven.plugin.sqlenumgen.configuration.EnumCfg;
import de.enlightened.maven.plugin.sqlenumgen.util.Column;
import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.FileSystem;
import java.nio.file.FileSystems;
import java.nio.file.Files;
import java.nio.file.Path;
import java.sql.DriverManager;
import java.sql.SQLException;
import org.apache.commons.collections4.map.LinkedMap;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.apache.maven.plugin.testing.stubs.MavenProjectStub;
import org.apache.maven.project.MavenProject;
import org.apache.velocity.Template;
import org.apache.velocity.app.VelocityEngine;
import org.junit.Before;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.ExpectedException;
import org.junit.runner.RunWith;
import static org.powermock.api.mockito.PowerMockito.mock;
import static org.powermock.api.mockito.PowerMockito.when;
import org.powermock.modules.junit4.PowerMockRunner;
import org.powermock.reflect.Whitebox;

/**
 *
 * @author Nicolai Ehemann
 */
@RunWith(PowerMockRunner.class)
public class SqlEnumGeneratorMojoTest extends AbstractMojoTestCase {

  public static final String JDBC_URL_CLOSE_DELAY = ";DB_CLOSE_DELAY=-1";
  public static final String JDBC_URL = "jdbc:h2:mem:test";
  private final static String EXPECTED_ENUM_PATH = "src/test/resources/expectedEnum.java";

  @SuppressWarnings("checkstyle:constantname")
  private final static FileSystem fileSystem = Jimfs.newFileSystem();

  private SqlEnumGeneratorMojo mojo;

  private Configuration configuration;

  @Rule
  public final ExpectedException exception = ExpectedException.none();

  @Before
  public void setUp() throws Exception {
    super.setUp();
    Class.forName("org.h2.Driver");
  }

  @Test
  public void testExecute() throws MojoFailureException, SQLException, IOException, IllegalAccessException {
    setupInMemoryMojo();

    DriverManager.getConnection(JDBC_URL + ";INIT=RUNSCRIPT FROM './src/test/resources/test.init.sql'" + JDBC_URL_CLOSE_DELAY);

    configuration.getJdbc().setUrl(JDBC_URL + JDBC_URL_CLOSE_DELAY);
    mojo.setConfiguration(configuration);

    final DatabaseCfg database = new DatabaseCfg();
    final EnumCfg enm = new EnumCfg();
    enm.setName("testEnum");
    enm.setTable("TEST");
    database.addEnum(enm);
    configuration.getGenerator().setDatabase(database);

    mojo.execute();

    final Path generatedEnumPath = SqlEnumGeneratorMojoTest.fileSystem.getPath(
        SqlEnumGeneratorMojo.DEFAULT_OUTPUT_DIRECTORY,
        SqlEnumGeneratorMojo.DEFAULT_PACKAGE.replace(".", "/"),
        "testEnum.java")
        .toAbsolutePath();
    final String generatedEnumClass = new String(Files.readAllBytes(generatedEnumPath));
    final Path expectedEnumPath = FileSystems.getDefault()
        .getPath(EXPECTED_ENUM_PATH).toAbsolutePath();
    final String expectedEnumClass = new String(Files.readAllBytes(expectedEnumPath));

    final int beforeVersionPos = expectedEnumClass.indexOf("sqlenumgen version") + 18;
    final int afterVersionPos = beforeVersionPos + SqlEnumGeneratorMojo.CONFIGURATION.PROJECT_VERSION.length() + 2;
    final int beforeDatePos = expectedEnumClass.indexOf("date") + 7;
    final int afterDatePos = beforeDatePos + 26;
    assertEquals("Generated enum class",
        expectedEnumClass.substring(0, beforeVersionPos)
            + expectedEnumClass.substring(afterVersionPos, beforeDatePos)
            + expectedEnumClass.substring(afterDatePos),
        generatedEnumClass.substring(0, beforeVersionPos)
            + generatedEnumClass.substring(afterVersionPos, beforeDatePos)
            + generatedEnumClass.substring(afterDatePos));
    assertEquals("Generated enum sqlenumgen version",
        SqlEnumGeneratorMojo.CONFIGURATION.PROJECT_VERSION,
        generatedEnumClass.substring(beforeVersionPos + 1, afterVersionPos - 1));
    assertTrue("Generated enum date",
        generatedEnumClass.substring(beforeDatePos + 1, afterDatePos - 1)
            .matches("[0-9]{4}-[0-9]{2}-[0-9]{2}T[0-9]{2}:[0-9]{2}:[0-9]{2}.[0-9]{3}Z"));
  }

  @Test
  public void testExecuteFail() throws Exception {
    setupInMemoryMojo();
    mojo.setConfiguration(configuration);

    final DatabaseCfg database = new DatabaseCfg();
    final EnumCfg enm = new EnumCfg();
    enm.setName("testEnum");
    enm.setTable("TEST");
    database.addEnum(enm);
    configuration.getGenerator().setDatabase(database);

    exception.expect(MojoFailureException.class);
    mojo.execute();
  }

  @Test
  public void testcompleteEnumCfgFromColumns1() throws Exception {
    mojo = new SqlEnumGeneratorMojo();

    final EnumCfg enumCfgIn = new EnumCfg();
    enumCfgIn.setName("TestEnum");
    final LinkedMap<String, Column> columns = new LinkedMap<>();
    columns.put("name", new Column("name", "VARCHAR"));
    final EnumCfg actualEnumCfg = (EnumCfg) Whitebox.invokeMethod(mojo, "completeEnumCfgFromColumns", enumCfgIn, columns);

    final EnumCfg expectedEnumCfg = new EnumCfg();
    expectedEnumCfg.setName("TestEnum");
    expectedEnumCfg.setNameColumn("name");
    expectedEnumCfg.setIdColumn("name");

    assertEquals("enumCfg is returned", expectedEnumCfg, actualEnumCfg);
  }

  @Test
  public void testcompleteEnumCfgFromColumns2() throws Exception {
    mojo = new SqlEnumGeneratorMojo();

    final EnumCfg enumCfgIn = new EnumCfg();
    enumCfgIn.setName("TestEnum");
    final LinkedMap<String, Column> columns = new LinkedMap<>();
    columns.put("id", new Column("id", "INTEGER"));
    columns.put("name", new Column("name", "VARCHAR"));
    final EnumCfg actualEnumCfg = (EnumCfg) Whitebox.invokeMethod(mojo, "completeEnumCfgFromColumns", enumCfgIn, columns);

    final EnumCfg expectedEnumCfg = new EnumCfg();
    expectedEnumCfg.setName("TestEnum");
    expectedEnumCfg.setNameColumn("name");
    expectedEnumCfg.setIdColumn("id");

    assertEquals("enumCfg is returned", expectedEnumCfg, actualEnumCfg);
  }

  @Test
  public void testcompleteEnumCfgFromColumns3() throws Exception {
    mojo = new SqlEnumGeneratorMojo();

    final EnumCfg enumCfgIn = new EnumCfg();
    enumCfgIn.setName("TestEnum");
    enumCfgIn.setIdColumn("id");
    final LinkedMap<String, Column> columns = new LinkedMap<>();
    columns.put("id", new Column("id", "INTEGER"));
    columns.put("name", new Column("name", "VARCHAR"));
    final EnumCfg actualEnumCfg = (EnumCfg) Whitebox.invokeMethod(mojo, "completeEnumCfgFromColumns", enumCfgIn, columns);

    final EnumCfg expectedEnumCfg = new EnumCfg();
    expectedEnumCfg.setName("TestEnum");
    expectedEnumCfg.setNameColumn("name");
    expectedEnumCfg.setIdColumn("id");

    assertEquals("enumCfg is returned", expectedEnumCfg, actualEnumCfg);
  }

  @Test
  public void testcompleteEnumCfgFromColumns4() throws Exception {
    mojo = new SqlEnumGeneratorMojo();

    final EnumCfg enumCfgIn = new EnumCfg();
    enumCfgIn.setName("TestEnum");
    enumCfgIn.setIdColumn("id");
    final LinkedMap<String, Column> columns = new LinkedMap<>();
    columns.put("id", new Column("id", "VARCHAR"));
    columns.put("name", new Column("name", "VARCHAR"));
    final EnumCfg actualEnumCfg = (EnumCfg) Whitebox.invokeMethod(mojo, "completeEnumCfgFromColumns", enumCfgIn, columns);

    final EnumCfg expectedEnumCfg = new EnumCfg();
    expectedEnumCfg.setName("TestEnum");
    expectedEnumCfg.setNameColumn("id");
    expectedEnumCfg.setIdColumn("id");

    assertEquals("enumCfg is returned", expectedEnumCfg, actualEnumCfg);
  }

  @Test
  public void testcompleteEnumCfgFromColumnsFailNoStringColumn1() throws Exception {
    mojo = new SqlEnumGeneratorMojo();

    final EnumCfg enumCfgIn = new EnumCfg();
    enumCfgIn.setName("TestEnum");
    final LinkedMap<String, Column> columns = new LinkedMap<>();
    columns.put("name", new Column("name", "INTEGER"));

    exception.expect(MojoFailureException.class);
    exception.expectMessage("Only column for enum TestEnum must have String representation (for enum name).");
    Whitebox.invokeMethod(mojo, "completeEnumCfgFromColumns", enumCfgIn, columns);
  }

  @Test
  public void testcompleteEnumCfgFromColumnsFailNoStringColumn2() throws Exception {
    mojo = new SqlEnumGeneratorMojo();

    final EnumCfg enumCfgIn = new EnumCfg();
    enumCfgIn.setName("TestEnum");
    final LinkedMap<String, Column> columns = new LinkedMap<>();
    columns.put("id", new Column("name", "INTEGER"));
    columns.put("name", new Column("name", "INTEGER"));

    exception.expect(MojoFailureException.class);
    exception.expectMessage("Enum TestEnum must have at least one column with String representation (for enum name).");
    Whitebox.invokeMethod(mojo, "completeEnumCfgFromColumns", enumCfgIn, columns);
  }

  @Test
  public void testcompleteEnumCfgFromColumnsFailNoColumns() throws Exception {
    mojo = new SqlEnumGeneratorMojo();

    final EnumCfg enumCfgIn = new EnumCfg();
    enumCfgIn.setName("TestEnum");
    final LinkedMap<String, Column> columns = new LinkedMap<>();

    exception.expect(MojoFailureException.class);
    exception.expectMessage("No columns found for enum TestEnum.");
    Whitebox.invokeMethod(mojo, "completeEnumCfgFromColumns", enumCfgIn, columns);
  }

  @Test
  public void testLoadTemplate() throws Exception {
    final VelocityEngine velocityEngine = mock(VelocityEngine.class);
    final Template expectedTemplate = mock(Template.class);
    when(velocityEngine.getTemplate(VELOCITY_TEMPLATE)).thenReturn(expectedTemplate);
    mojo = new SqlEnumGeneratorMojo(velocityEngine);

    final Template actualTemplate = (Template) Whitebox.invokeMethod(mojo, "loadTemplate");

    assertSame("template is returned", expectedTemplate, actualTemplate);
  }

  @Test
  public void testLoadTemplateFail() throws Exception {
    final VelocityEngine velocityEngine = mock(VelocityEngine.class);
    when(velocityEngine.getTemplate(VELOCITY_TEMPLATE)).thenThrow(new NullPointerException());
    mojo = new SqlEnumGeneratorMojo(velocityEngine);

    exception.expect(RuntimeException.class);
    Whitebox.invokeMethod(mojo, "loadTemplate");
  }

  private void setupInMemoryMojo() throws IllegalAccessException {
    mojo = new SqlEnumGeneratorMojo(SqlEnumGeneratorMojoTest.fileSystem);
    configuration = new Configuration();
    configuration.getGenerator().getTarget().setDirectory(SqlEnumGeneratorMojo.DEFAULT_OUTPUT_DIRECTORY);
    configuration.getGenerator().getTarget().setPackage(SqlEnumGeneratorMojo.DEFAULT_PACKAGE);

    final MavenProject project = new MavenProjectStub();
    setVariableValueToObject(mojo, "project", project);
}
}
