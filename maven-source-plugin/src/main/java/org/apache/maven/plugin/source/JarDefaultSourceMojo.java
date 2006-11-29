package org.apache.maven.plugin.source;

/*
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 */

import org.apache.maven.plugin.MojoExecutionException;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.archiver.jar.JarArchiver;

import java.io.File;
import java.io.IOException;

/**
 * This plugin bundles all the sources into a jar archive.
 *
 * @author <a href="mailto:trygvis@inamo.no">Trygve Laugst&oslash;l</a>
 * @version $Id: AbstractJarSourceMojo.java 389062 2006-03-27 08:19:25Z aramirez $
 * @goal jar
 * @phase package
 * @execute phase="generate-sources"
 */
public class JarDefaultSourceMojo
    extends AbstractJarSourceMojo
{
    /**
     * @see org.apache.maven.plugin.AbstractMojo#execute()
     */
    public void execute()
        throws MojoExecutionException
    {
        if ( "pom".equals( packaging ) )
        {
            getLog().info( "NOT adding sources to attached artifacts for packaging: \'" + packaging + "\'." );
        }
        else
        {
            // Do not attach source JAR for artifacts with classifier. This is because Maven2 only supports a single
            // classifier per artifact. This is a limitation. See http://jira.codehaus.org/browse/MSOURCES-10.
            if ( getProject().getArtifact().getClassifier() != null )
            {
                getLog().warn( "NOT adding sources to artifacts with classifier as Maven only supports one classifier "
                    + "per artifact. Current artifact [" + getProject().getArtifact().getId() + "] has a ["
                    + getProject().getArtifact().getClassifier() + "] classifier.");
            }
            else
            {
                File outputFile = new File( outputDirectory, finalName + "-sources.jar" );

                File[] sourceDirectories = getDefaultSources();

                try
                {
                    createJar( outputFile, sourceDirectories, createArchiver() );
                }
                catch ( IOException e )
                {
                    throw new MojoExecutionException( "Error creating source archive: " + e.getMessage(), e );
                }
                catch ( ArchiverException e )
                {
                    throw new MojoExecutionException( "Error creating source archive: " + e.getMessage(), e );
                }

                attachArtifact( outputFile, "sources" );
            }
        }
    }

}
