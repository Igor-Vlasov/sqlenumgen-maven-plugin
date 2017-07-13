/*
 * Copyright (C) 2016-2017 Nicolai Ehemann (en@enlightened.de).
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
package de.enlightened.maven.plugin.sqlenumgen.configuration;

/**
 *
 * @author Nicolai Ehemann
 */
public class Configuration {

  /**
   * JDBC connection specification
   * @since 0.0.1
   */
  private JDBCCfg jdbc;

  /**
   * Configuration of the enum generator
   * @since 0.0.1
   */
  private GeneratorCfg generator;

  public Configuration() {
    this.jdbc = new JDBCCfg();
    this.generator = new GeneratorCfg();
  }

  public final JDBCCfg getJdbc() {
    return this.jdbc;
  }

  public final void setJdbc(final JDBCCfg jdbc) {
    this.jdbc = jdbc;
  }

  public final GeneratorCfg getGenerator() {
    return this.generator;
  }

  public final void setGenerator(final GeneratorCfg generator) {
    this.generator = generator;
  }
}
