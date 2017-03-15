/*
 * SonarLint for Eclipse
 * Copyright (C) 2015-2017 SonarSource SA
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
 * You should have received a copy of the GNU Lesser General Public License
 * along with this program; if not, write to the Free Software Foundation,
 * Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA.
 */
package org.sonarlint.eclipse.core.internal.tracking;

import java.io.File;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.sonarlint.eclipse.core.SonarLintLogger;
import org.sonarlint.eclipse.core.internal.SonarLintCorePlugin;
import org.sonarlint.eclipse.core.internal.jobs.AsyncServerMarkerUpdaterJob;
import org.sonarlint.eclipse.core.resource.ISonarLintFile;
import org.sonarlint.eclipse.core.resource.ISonarLintIssuable;
import org.sonarlint.eclipse.core.resource.ISonarLintProject;
import org.sonarsource.sonarlint.core.client.api.connected.ConnectedSonarLintEngine;
import org.sonarsource.sonarlint.core.client.api.connected.ServerConfiguration;
import org.sonarsource.sonarlint.core.client.api.connected.ServerIssue;
import org.sonarsource.sonarlint.core.client.api.exceptions.DownloadException;

public class ServerIssueUpdater {

  public static final String PATH_SEPARATOR_PATTERN = Pattern.quote(File.separator);

  private final IssueTrackerRegistry issueTrackerRegistry;

  public ServerIssueUpdater(IssueTrackerRegistry issueTrackerRegistry) {
    this.issueTrackerRegistry = issueTrackerRegistry;
  }

  public void updateAsync(ServerConfiguration serverConfiguration, ConnectedSonarLintEngine engine, ISonarLintProject project, String localModuleKey,
    String serverModuleKey,
    Collection<ISonarLintIssuable> resources) {
    new IssueUpdateJob(serverConfiguration, engine, project, localModuleKey, serverModuleKey, resources).schedule();
  }

  private class IssueUpdateJob extends Job {
    private final ServerConfiguration serverConfiguration;
    private final ConnectedSonarLintEngine engine;
    private final String localModuleKey;
    private final String serverModuleKey;
    private final Collection<ISonarLintIssuable> resources;
    private final ISonarLintProject project;

    private IssueUpdateJob(ServerConfiguration serverConfiguration, ConnectedSonarLintEngine engine, ISonarLintProject project, String localModuleKey,
      String serverModuleKey, Collection<ISonarLintIssuable> resources) {
      super("Fetch server issues for " + project.getName());
      setPriority(DECORATE);
      this.serverConfiguration = serverConfiguration;
      this.engine = engine;
      this.project = project;
      this.localModuleKey = localModuleKey;
      this.serverModuleKey = serverModuleKey;
      this.resources = resources;
    }

    @Override
    protected IStatus run(IProgressMonitor monitor) {
      Map<ISonarLintIssuable, Collection<Trackable>> trackedIssues = new HashMap<>();
      try {
        for (ISonarLintIssuable resource : resources) {
          if (monitor.isCanceled()) {
            return Status.CANCEL_STATUS;
          }
          if (resource instanceof ISonarLintFile) {
            String relativePath = ((ISonarLintFile) resource).getProjectRelativePath();
            IssueTracker issueTracker = issueTrackerRegistry.getOrCreate(project, localModuleKey);
            List<ServerIssue> serverIssues = fetchServerIssues(serverConfiguration, engine, serverModuleKey, (ISonarLintFile) resource);
            Collection<Trackable> serverIssuesTrackable = serverIssues.stream().map(ServerIssueTrackable::new).collect(Collectors.toList());
            Collection<Trackable> tracked = issueTracker.matchAndTrackServerIssues(relativePath, serverIssuesTrackable);
            issueTracker.updateCache(relativePath, tracked);
            trackedIssues.put(resource, tracked);
          }
        }
        new AsyncServerMarkerUpdaterJob(project, trackedIssues).schedule();
        return Status.OK_STATUS;
      } catch (Throwable t) {
        // note: without catching Throwable, any exceptions raised in the thread will not be visible
        SonarLintLogger.get().error("error while fetching and matching server issues", t);
        return new Status(IStatus.ERROR, SonarLintCorePlugin.PLUGIN_ID, t.getMessage());
      }
    }

  }

  public static List<ServerIssue> fetchServerIssues(ServerConfiguration serverConfiguration, ConnectedSonarLintEngine engine, String moduleKey, ISonarLintFile resource) {
    String fileKey = resource.getProjectRelativePath();

    try {
      SonarLintLogger.get().debug("fetchServerIssues moduleKey=" + moduleKey + ", filepath=" + fileKey);
      return engine.downloadServerIssues(serverConfiguration, moduleKey, fileKey);
    } catch (DownloadException e) {
      SonarLintLogger.get().info(e.getMessage());
      return engine.getServerIssues(moduleKey, fileKey);
    }
  }

  /**
   * Convert relative path to SonarQube file key
   *
   * @param relativePath relative path string in the local OS
   * @return SonarQube file key
   */
  public static String toFileKey(IResource resource) {
    String relativePath = resource.getProjectRelativePath().toString();
    if (File.separatorChar != '/') {
      return relativePath.replaceAll(PATH_SEPARATOR_PATTERN, "/");
    }
    return relativePath;
  }
}
