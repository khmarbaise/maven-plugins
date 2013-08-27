package org.apache.maven.plugin.reactor;

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

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import junit.framework.TestCase;

import org.apache.maven.model.Dependency;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.scm.ScmException;
import org.apache.maven.scm.ScmFile;
import org.apache.maven.scm.ScmFileSet;
import org.apache.maven.scm.ScmFileStatus;
import org.apache.maven.scm.ScmResult;
import org.apache.maven.scm.command.status.StatusScmResult;
import org.apache.maven.scm.repository.ScmRepository;
import org.apache.maven.shared.invoker.Invoker;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.StringUtils;

public class MakeMojoTest
    extends TestCase
{
    MavenProject dataAccess = new MavenProject();

    MavenProject businessLogic = new MavenProject();

    MavenProject ui = new MavenProject();

    List configuredProjects;

    String ps = File.separator;

    File baseDir;

    public void setUp()
        throws Exception
    {
        File tempDir = new File( System.getProperty( "java.io.tmpdir" ) );
        baseDir = File.createTempFile( "makeMojoTest", "", tempDir );
        baseDir.delete();
        configureProject( dataAccess, "dataAccess", "reactortest", "1.0" );
        configureProject( businessLogic, "businessLogic", "reactortest", "1.0" );
        configureProject( ui, "ui", "reactortest", "1.0" );

        configuredProjects = Arrays.asList( dataAccess, businessLogic, ui );

        // ui depends on businessLogic
        // businessLogic depends on dataAccess
        createDependency( businessLogic, ui );
        createDependency( dataAccess, businessLogic );
    }

    public void tearDown()
        throws Exception
    {
        FileUtils.deleteDirectory( baseDir );
    }

    void configureProject( MavenProject p, String artifactId, String groupId, String version )
        throws IOException
    {
        p.setArtifactId( artifactId );
        p.setGroupId( groupId );
        p.setVersion( version );
        File file = new File( baseDir, artifactId + "/pom.xml" );
        file.getParentFile().mkdirs();
        file.createNewFile();
        p.setFile( file.getAbsoluteFile() );
    }

    void createDependency( MavenProject provider, MavenProject consumer )
    {
        Dependency d = new Dependency();
        d.setArtifactId( provider.getArtifactId() );
        d.setGroupId( provider.getGroupId() );
        d.setVersion( provider.getVersion() );
        consumer.getDependencies().add( d );
    }

    public void testMake()
        throws MojoExecutionException, MojoFailureException
    {
        MakeMojo m = new MakeMojo();
        m.collectedProjects = configuredProjects;
        m.artifactList = "reactortest:businessLogic";
        m.baseDir = baseDir;
        m.goals = "install";
        FakeInvoker fi = new FakeInvoker();
        m.simpleInvoker = fi;
        m.execute();
        assertEquals( "dataAccess/pom.xml,businessLogic/pom.xml", fi.getIncludes() );
    }

    public void testMakeDependents()
        throws MojoExecutionException, MojoFailureException
    {
        MakeDependentsMojo m = new MakeDependentsMojo();
        m.collectedProjects = configuredProjects;
        m.artifactList = "reactortest:businessLogic";
        m.baseDir = baseDir;
        m.goals = "install";
        FakeInvoker fi = new FakeInvoker();
        m.simpleInvoker = fi;
        m.execute();
        assertEquals( "businessLogic/pom.xml,ui/pom.xml", fi.getIncludes() );
    }

    public void testMakeScmChanges()
        throws Exception
    {
        MakeScmChanges m = new MakeScmChanges();
        m.collectedProjects = configuredProjects;
        m.baseDir = baseDir;
        m.goals = "install";
        FakeInvoker fi = new FakeInvoker();
        m.simpleInvoker = fi;

        ScmFile sf = new ScmFile( businessLogic.getFile().getAbsolutePath(), ScmFileStatus.MODIFIED );
        m.scmManager = new FakeScmManager( Arrays.asList( sf ) );
        m.scmConnection = "";

        m.execute();
        assertEquals( "businessLogic/pom.xml,ui/pom.xml", fi.getIncludes() );
    }

    public void testMakeResume()
        throws Exception
    {
        MakeMojo m = new MakeMojo();
        m.collectedProjects = configuredProjects;
        m.artifactList = "reactortest:ui";
        m.continueFromFolder = new File( baseDir, "businessLogic" );
        m.baseDir = baseDir;
        m.goals = "install";
        FakeInvoker fi = new FakeInvoker();
        m.simpleInvoker = fi;
        m.execute();
        assertEquals( "businessLogic/pom.xml,ui/pom.xml", fi.getIncludes() );
    }

    class FakeInvoker
        extends SimpleInvoker
    {
        String[] reactorIncludes;

        List goalList;

        void runReactor( String[] reactorIncludes, List goalList, Invoker invoker, boolean printOnly, Log log )
            throws InvokerExecutionException
        {
            this.reactorIncludes = reactorIncludes;
            this.goalList = goalList;
        }

        String getIncludes()
        {
            String includes = StringUtils.join( reactorIncludes, "," );
            includes = includes.replaceAll( "\\\\", "/" );
            return includes;
        }
    }

    class FakeScmManager
        extends NoopScmManager
    {
        List changedFiles;

        public FakeScmManager( List changedFiles )
        {
            this.changedFiles = changedFiles;
        }

        public StatusScmResult status( ScmRepository repository, ScmFileSet fileSet )
            throws ScmException
        {
            return new StatusScmResult( changedFiles, new ScmResult( null, null, null, true ) );
        }
    }

}
