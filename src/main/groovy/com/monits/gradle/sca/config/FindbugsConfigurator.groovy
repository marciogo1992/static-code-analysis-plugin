/*
 * Copyright 2010-2016 Monits S.A.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use this
 * file except in compliance with the License. You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed under
 * the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF
 * ANY KIND, either express or implied. See the License for the specific language governing
 * permissions and limitations under the License.
 */
package com.monits.gradle.sca.config

import com.monits.gradle.sca.ClasspathAware
import com.monits.gradle.sca.StaticCodeAnalysisExtension
import com.monits.gradle.sca.ToolVersions
import com.monits.gradle.sca.task.DownloadTask
import org.gradle.api.Project
import org.gradle.api.Task
import org.gradle.api.plugins.quality.FindBugs
import org.gradle.api.reporting.ReportingExtension
import org.gradle.api.tasks.SourceSet
import org.gradle.api.tasks.compile.JavaCompile
import org.gradle.util.GUtil

/**
 * A configurator for Findbugs tasks
*/
class FindbugsConfigurator implements AnalysisConfigurator, ClasspathAware {
    private static final String FINDBUGS = 'findbugs'

    @Override
    void applyConfig(Project project, StaticCodeAnalysisExtension extension) {
        project.plugins.apply FINDBUGS

        project.dependencies {
            findbugsPlugins('com.monits:findbugs-plugin:' + ToolVersions.monitsFindbugsVersion) {
                transitive = false
            }
            findbugsPlugins 'com.mebigfatguy.fb-contrib:fb-contrib:' + ToolVersions.fbContribVersion
        }

        boolean remoteLocation = isRemoteLocation(extension.getFindbugsExclude())
        File filterSource
        String downloadTaskName = 'downloadFindbugsExcludeFilter'
        if (remoteLocation) {
            filterSource = makeDownloadFileTask(project, extension.getFindbugsExclude(),
                    'excludeFilter.xml', downloadTaskName, FINDBUGS)
        } else {
            filterSource = new File(extension.getFindbugsExclude())
        }

        project.findbugs {
            toolVersion = ToolVersions.findbugsVersion
            effort = 'max'
            ignoreFailures = extension.getIgnoreErrors()
            excludeFilter = filterSource
        }

        // Create a phony findbugs task that just executes all real findbugs tasks
        Task findbugsRootTask = project.tasks.findByName(FINDBUGS) ?: project.task(FINDBUGS)
        project.sourceSets.all { SourceSet sourceSet ->
            Task findbugsTask = getOrCreateTask(project, sourceSet.getTaskName(FINDBUGS, null)) {
                // most defaults are good enough
                if (remoteLocation) {
                    dependsOn project.tasks.findByName(downloadTaskName)
                }

                reports.xml {
                    destination = new File(project.extensions.getByType(ReportingExtension).file(FINDBUGS),
                            "findbugs-${sourceSet.name}.xml")
                    withMessages = true
                }
            }

            findbugsRootTask.dependsOn findbugsTask
        }

        project.tasks.check.dependsOn findbugsRootTask
    }

    @SuppressWarnings('UnnecessaryGetter')
    @Override
    void applyAndroidConfig(Project project, StaticCodeAnalysisExtension extension) {
        project.plugins.apply FINDBUGS

        project.dependencies {
            findbugsPlugins('com.monits:findbugs-plugin:' + ToolVersions.monitsFindbugsVersion) {
                transitive = false
            }
            findbugsPlugins 'com.mebigfatguy.fb-contrib:fb-contrib:' + ToolVersions.fbContribVersion
        }

        boolean remoteLocation = isRemoteLocation(extension.getFindbugsExclude())
        File filterSource
        String downloadTaskName = 'downloadFindbugsExcludeFilter'
        if (remoteLocation) {
            filterSource = makeDownloadFileTask(project, extension.getFindbugsExclude(),
                    'excludeFilter.xml', downloadTaskName, FINDBUGS)
        } else {
            filterSource = new File(extension.getFindbugsExclude())
        }

        project.findbugs {
            toolVersion = ToolVersions.findbugsVersion
            effort = 'max'
            ignoreFailures = extension.getIgnoreErrors()
            excludeFilter = filterSource
        }

        // Create a phony findbugs task that just executes all real findbugs tasks
        Task findbugsRootTask = project.tasks.findByName(FINDBUGS) ?: project.task(FINDBUGS)
        project.android.sourceSets.all { sourceSet ->
            Task findbugsTask = getOrCreateTask(project, getTaskName(sourceSet.name)) {
                /*
                 * Android doesn't expose name of the task compiling the sourceset, and names vary
                 * widely from version to version of the plugin, plus needs to take flavors into account.
                 * This is inefficient, but safer and simpler.
                */
                dependsOn project.tasks.withType(JavaCompile)

                if (remoteLocation) {
                    dependsOn project.tasks.findByName(downloadTaskName)
                }

                // TODO : Get classes just for the given sourceset, the rest should be in the classpath
                classes = getProjectClassTree(project)

                source sourceSet.java.srcDirs
                exclude '**/gen/**'

                reports.xml {
                    destination = new File(project.extensions.getByType(ReportingExtension).file(FINDBUGS),
                            "findbugs-${sourceSet.name}.xml")
                    withMessages = true
                }
            }

            setupAndroidClasspathAwareTask(findbugsTask, project)
            findbugsRootTask.dependsOn findbugsTask
        }

        project.tasks.check.dependsOn findbugsRootTask
    }

    private static boolean isRemoteLocation(String path) {
        path.startsWith('http://') || path.startsWith('https://')
    }

    private File makeDownloadFileTask(Project project, String remotePath, String destination,
                                      String taskName, String plugin) {
        GString destPath = "${project.rootDir}/config/${plugin}/"
        File destFile = project.file(destPath + destination)

        if (!project.tasks.findByName(taskName)) {
            project.task(taskName, type:DownloadTask) {
                directory = project.file(destPath)
                downloadedFile = destFile
                resourceUri = remotePath
            }
        }

        destFile
    }

    private static String getTaskName(final String sourceSetName) {
        GUtil.toLowerCamelCase(String.format('%s %s', FINDBUGS, sourceSetName))
    }

    private static Task getOrCreateTask(final Project project, final String taskName, final Closure closure) {
        Task findbugsTask;
        if (project.tasks.findByName(taskName)) {
            findbugsTask = project.tasks.findByName(taskName)
        } else {
            findbugsTask = project.task(taskName, type:FindBugs)
        }

        findbugsTask.configure closure
    }
}
