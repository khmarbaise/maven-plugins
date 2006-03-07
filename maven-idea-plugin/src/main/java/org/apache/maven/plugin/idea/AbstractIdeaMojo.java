package org.apache.maven.plugin.idea;

/*
 * Copyright 2005-2006 The Apache Software Foundation.
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

import org.apache.maven.artifact.Artifact;
import org.apache.maven.artifact.factory.ArtifactFactory;
import org.apache.maven.artifact.metadata.ArtifactMetadataSource;
import org.apache.maven.artifact.repository.ArtifactRepository;
import org.apache.maven.artifact.resolver.ArtifactNotFoundException;
import org.apache.maven.artifact.resolver.ArtifactResolutionException;
import org.apache.maven.artifact.resolver.ArtifactResolutionResult;
import org.apache.maven.artifact.resolver.ArtifactResolver;
import org.apache.maven.artifact.versioning.InvalidVersionSpecificationException;
import org.apache.maven.artifact.versioning.VersionRange;
import org.apache.maven.model.Dependency;
import org.apache.maven.model.DependencyManagement;
import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.project.ProjectBuildingException;
import org.apache.maven.project.artifact.InvalidDependencyVersionException;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.xml.Xpp3Dom;

import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Map;
import java.util.Set;

/**
 * @author Edwin Punzalan
 */
public abstract class AbstractIdeaMojo
    extends AbstractMojo
{
    /**
     * The Maven Project.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    protected MavenProject project;

    /* holder for the log object only */
    protected Log log;

    /**
     * Whether to update the existing project files or overwrite them.
     *
     * @parameter expression="${overwrite}" default-value="false"
     */
    protected boolean overwrite;

    /**
     * @parameter expression="${component.org.apache.maven.artifact.factory.ArtifactFactory}"
     * @required
     * @readonly
     */
    protected ArtifactFactory artifactFactory;

    /**
     * @parameter expression="${localRepository}"
     * @required
     * @readonly
     */
    protected ArtifactRepository localRepo;

    /**
     * @parameter expression="${component.org.apache.maven.artifact.resolver.ArtifactResolver}"
     */
    protected ArtifactResolver artifactResolver;

    /**
     * @parameter expression="${component.org.apache.maven.artifact.metadata.ArtifactMetadataSource}"
     */
    protected ArtifactMetadataSource artifactMetadataSource;

    public void initParam( MavenProject project, ArtifactFactory artifactFactory, ArtifactRepository localRepo,
                           ArtifactResolver artifactResolver, ArtifactMetadataSource artifactMetadataSource, Log log,
                           boolean overwrite )
    {
        this.project = project;

        this.log = log;

        this.artifactFactory = artifactFactory;

        this.localRepo = localRepo;

        this.artifactResolver = artifactResolver;

        this.artifactMetadataSource = artifactMetadataSource;

        this.overwrite = overwrite;
    }

    /**
     * Finds element from the module element.
     *
     * @param module Xpp3Dom element
     * @param name   Name attribute to find
     * @return component  Returns the Xpp3Dom element found.
     */
    protected Xpp3Dom findComponent( Xpp3Dom module, String name )
    {
        Xpp3Dom[] components = module.getChildren( "component" );
        for ( int i = 0; i < components.length; i++ )
        {
            if ( name.equals( components[i].getAttribute( "name" ) ) )
            {
                return components[i];
            }
        }

        Xpp3Dom component = createElement( module, "component" );
        component.setAttribute( "name", name );
        return component;
    }

    /**
     * Finds an element from Xpp3Dom component.
     *
     * @param component Xpp3Dom component
     * @param name      Name of the element to find.
     * @return the element
     */
    protected Xpp3Dom findElement( Xpp3Dom component, String name )
    {
        Xpp3Dom element = component.getChild( name );

        if ( element == null )
        {
            element = createElement( component, name );
        }
        return element;
    }

    /**
     * Creates an Xpp3Dom element.
     *
     * @param module Xpp3Dom element
     * @param name   Name of the element
     * @return component Xpp3Dom element
     */
    protected Xpp3Dom createElement( Xpp3Dom module, String name )
    {
        Xpp3Dom component = new Xpp3Dom( name );
        module.addChild( component );
        return component;
    }

    /**
     * Translate the absolutePath into its relative path.
     *
     * @param basedir      The basedir of the project.
     * @param absolutePath The absolute path that must be translated to relative path.
     * @return relative  Relative path of the parameter absolute path.
     */
    protected String toRelative( File basedir, String absolutePath )
    {
        String relative;

        if ( absolutePath.startsWith( basedir.getAbsolutePath() ) )
        {
            relative = absolutePath.substring( basedir.getAbsolutePath().length() + 1 );
        }
        else
        {
            relative = absolutePath;
        }

        relative = StringUtils.replace( relative, "\\", "/" );

        return relative;
    }

    /**
     * Remove elements from content (Xpp3Dom).
     *
     * @param content Xpp3Dom element
     * @param name    Name of the element to be removed
     */
    protected void removeOldElements( Xpp3Dom content, String name )
    {
        Xpp3Dom[] children = content.getChildren();
        for ( int i = children.length - 1; i >= 0; i-- )
        {
            Xpp3Dom child = children[i];
            if ( child.getName().equals( name ) )
            {
                content.removeChild( i );
            }
        }
    }

    protected void doDependencyResolution( MavenProject project, ArtifactFactory artifactFactory,
                                           ArtifactResolver artifactResolver, ArtifactRepository localRepo,
                                           ArtifactMetadataSource artifactMetadataSource )
        throws InvalidDependencyVersionException, ProjectBuildingException
    {
        if ( project.getDependencies() != null )
        {
            Map managedVersions =
                createManagedVersionMap( artifactFactory, project.getId(), project.getDependencyManagement() );

            try
            {
                ArtifactResolutionResult result = artifactResolver.resolveTransitively( getProjectArtifacts(),
                                                                                        project.getArtifact(),
                                                                                        managedVersions, localRepo,
                                                                                        project.getRemoteArtifactRepositories(),
                                                                                        artifactMetadataSource );

                project.setArtifacts( result.getArtifacts() );
            }
            catch ( ArtifactNotFoundException e )
            {
                getLog().debug( e.getMessage(), e );

                StringBuffer msg = new StringBuffer();
                msg.append( "An error occurred during dependency resolution.\n\n" );
                msg.append( "    Failed to retrieve " + e.getDownloadUrl() + "\n" );
                msg.append( "from the following repositories:" );
                for( Iterator repositories = e.getRemoteRepositories().iterator(); repositories.hasNext(); )
                {
                    ArtifactRepository repository = (ArtifactRepository) repositories.next();
                    msg.append( "\n    " + repository.getId() + "(" + repository.getUrl() + ")" );
                }
                msg.append( "\nCaused by: " + e.getMessage() );

                getLog().warn( msg );
            }
            catch ( ArtifactResolutionException e )
            {
                getLog().debug( e.getMessage(), e );

                StringBuffer msg = new StringBuffer();
                msg.append( "An error occurred during dependency resolution of the following artifact:\n\n" );
                msg.append( "    " + e.getGroupId() + ":" + e.getArtifactId() + e.getVersion() + "\n\n" );
                msg.append( "Caused by: " + e.getMessage() );

                getLog().warn( msg );
            }
        }
    }

    private Set getProjectArtifacts()
    {
        Set artifacts = new HashSet();

        for ( Iterator dependencies = project.getDependencies().iterator(); dependencies.hasNext(); )
        {
            Dependency dep = (Dependency) dependencies.next();

            String groupId = dep.getGroupId();
            String artifactId = dep.getArtifactId();
            VersionRange versionRange = VersionRange.createFromVersion( dep.getVersion() );
            String type = dep.getType();
            if ( type == null )
            {
                type = "jar";
            }
            String classifier = dep.getClassifier();
            boolean optional = dep.isOptional();
            String scope = dep.getScope();
            if ( scope == null )
            {
                scope = Artifact.SCOPE_COMPILE;
            }

            Artifact artifact = artifactFactory.createDependencyArtifact( groupId, artifactId, versionRange, type, classifier, scope, optional ) ;

            if ( scope.equalsIgnoreCase( Artifact.SCOPE_SYSTEM ) )
            {
                artifact.setFile( new File( dep.getSystemPath() ) );
            }

            artifacts.add( artifact );
        }

        return artifacts;
    }

    private Map createManagedVersionMap( ArtifactFactory artifactFactory, String projectId,
                                         DependencyManagement dependencyManagement )
        throws ProjectBuildingException
    {
        Map map;
        if ( dependencyManagement != null && dependencyManagement.getDependencies() != null )
        {
            map = new HashMap();
            for ( Iterator i = dependencyManagement.getDependencies().iterator(); i.hasNext(); )
            {
                Dependency d = (Dependency) i.next();

                try
                {
                    VersionRange versionRange = VersionRange.createFromVersionSpec( d.getVersion() );
                    Artifact artifact = artifactFactory.createDependencyArtifact( d.getGroupId(), d.getArtifactId(),
                                                                                  versionRange, d.getType(),
                                                                                  d.getClassifier(), d.getScope(),
                                                                                  d.isOptional() );
                    map.put( d.getManagementKey(), artifact );
                }
                catch ( InvalidVersionSpecificationException e )
                {
                    throw new ProjectBuildingException( projectId, "Unable to parse version '" + d.getVersion() +
                        "' for dependency '" + d.getManagementKey() + "': " + e.getMessage(), e );
                }
            }
        }
        else
        {
            map = Collections.EMPTY_MAP;
        }
        return map;
    }

    public Log getLog()
    {
        if ( log == null )
        {
            log = super.getLog();
        }

        return log;
    }

    protected Reader getXmlReader( String file )
    {
        File altFile = new File( project.getBasedir(), "src/main/idea/" + file );
        if ( altFile.exists() )
        {
            try
            {
                return new FileReader( altFile );
            }
            catch ( FileNotFoundException e )
            {
                // this shouldn't happen, since we just verified it exists,
                // but we'll print the error out in the off change
                getLog().error( "File not found even though we just verified it exists. Failing.", e );
                throw new RuntimeException( e );
            }
        }
        else
        {
            return new InputStreamReader( getClass().getResourceAsStream( "/templates/default/" + file ) );
        }
    }
}
