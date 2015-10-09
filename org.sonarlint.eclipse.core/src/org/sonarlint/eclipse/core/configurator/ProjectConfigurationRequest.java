/*
 * SonarLint for Eclipse
 * Copyright (C) 2015 SonarSource
 * sonarlint@sonarsource.com
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
 * You should have received a copy of the GNU Lesser General Public
 * License along with this program; if not, write to the Free Software
 * Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02
 */
package org.sonarlint.eclipse.core.configurator;

import java.util.Properties;
import org.eclipse.core.resources.IProject;

public class ProjectConfigurationRequest {

  private final IProject project;
  private final Properties sonarProjectProperties;

  public ProjectConfigurationRequest(IProject eclipseProject, Properties sonarProjectProperties) {
    this.project = eclipseProject;
    this.sonarProjectProperties = sonarProjectProperties;
  }

  public IProject getProject() {
    return project;
  }

  public Properties getSonarProjectProperties() {
    return sonarProjectProperties;
  }

}