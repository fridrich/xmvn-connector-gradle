/*-
 * Copyright (c) 2015 Red Hat, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package org.fedoraproject.xmvn.connector.gradle;

import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import javax.inject.Inject;

import org.gradle.api.DefaultTask;
import org.gradle.api.Project;
import org.gradle.api.artifacts.ModuleDependency;
import org.gradle.api.artifacts.PublishArtifact;
import org.gradle.api.artifacts.PublishException;
import org.gradle.api.component.SoftwareComponent;
import org.gradle.api.internal.component.SoftwareComponentInternal;
import org.gradle.api.internal.component.Usage;
import org.gradle.api.tasks.TaskAction;

import org.fedoraproject.xmvn.artifact.Artifact;
import org.fedoraproject.xmvn.artifact.DefaultArtifact;
import org.fedoraproject.xmvn.connector.gradle.GradleResolver.LazyLocatorProvider;
import org.fedoraproject.xmvn.deployer.Deployer;
import org.fedoraproject.xmvn.deployer.DeploymentRequest;
import org.fedoraproject.xmvn.deployer.DeploymentResult;

/**
 * @author Mikolaj Izdebski
 */
class XMvnInstallTask
    extends DefaultTask
{
    static class LazyDeployerProvider
    {
        static final Deployer deployer = LazyLocatorProvider.locator.getService( Deployer.class );
    }

    @Inject
    public XMvnInstallTask()
    {
        // There must exist constructor annotated with @Inject
    }

    private Deployer getDeployer()
    {
        return LazyDeployerProvider.deployer;
    }

    private Artifact getPublishArtifact( Project project, PublishArtifact gradleArtifact )
    {
        String groupId = project.getGroup().toString();
        String artifactId = gradleArtifact.getName();
        String extension = gradleArtifact.getExtension();
        String classifier = gradleArtifact.getClassifier();
        String version = project.getVersion().toString();

        return new DefaultArtifact( groupId, artifactId, extension, classifier, version );
    }

    private List<Artifact> getDependencyArtifacts( ModuleDependency dependency )
    {
        String groupId = dependency.getGroup();
        String version = dependency.getVersion();

        if ( dependency.getArtifacts().isEmpty() )
        {
            String artifactId = dependency.getName();
            return Collections.singletonList( new DefaultArtifact( groupId, artifactId, version ) );
        }

        return dependency.getArtifacts().stream().map( dependencyArtifact -> {
            String artifactId = dependencyArtifact.getName();
            String extension = dependencyArtifact.getExtension();
            String classifier = dependencyArtifact.getClassifier();
            return new DefaultArtifact( groupId, artifactId, extension, classifier, version );
        } ).collect( Collectors.toList() );
    }

    private List<Artifact> getExclusionArtifacts( ModuleDependency dependency )
    {
        return dependency.getExcludeRules().stream().map( exclusionRule -> {
            String groupId = exclusionRule.getGroup();
            String artifactId = exclusionRule.getModule();
            return new DefaultArtifact( groupId, artifactId );
        } ).collect( Collectors.toList() );
    }

    private void deploy( Project project, PublishArtifact gradleArtifact, Set<ModuleDependency> dependencies )
    {
        DeploymentRequest request = new DeploymentRequest();

        Artifact publishArtifact = getPublishArtifact( project, gradleArtifact );
        publishArtifact = publishArtifact.setPath( gradleArtifact.getFile().toPath() );
        request.setArtifact( publishArtifact );

        if ( gradleArtifact.getType() != null )
        {
            request.addProperty( "type", gradleArtifact.getType() );
        }

        for ( ModuleDependency dependency : dependencies )
        {
            List<Artifact> dependencyArtifacts = getDependencyArtifacts( dependency );
            List<Artifact> exclusionArtifacts = getExclusionArtifacts( dependency );

            for ( Artifact dependencyArtifact : dependencyArtifacts )
            {
                request.addDependency( dependencyArtifact, exclusionArtifacts );
            }
        }

        DeploymentResult result = getDeployer().deploy( request );

        if ( result.getException() != null )
        {
            throw new PublishException( "XMVn installation failed", result.getException() );
        }
    }

    private void deployProject( Project project )
    {
        for ( SoftwareComponent component : project.getComponents() )
        {
            SoftwareComponentInternal internalComponent = (SoftwareComponentInternal) component;

            for ( Usage usage : internalComponent.getUsages() )
            {
                Set<ModuleDependency> dependencies = usage.getDependencies();

                for ( PublishArtifact artifact : usage.getArtifacts() )
                {
                    deploy( project, artifact, dependencies );
                }
            }
        }
    }

    @TaskAction
    protected void upload()
    {
        deployProject( getProject() );
    }
}
