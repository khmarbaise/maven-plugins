/*
 * Copyright 2001-2006 The Apache Software Foundation.
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
package org.apache.maven.plugin.clover;

import com.cenqua.clover.reporters.html.HtmlReporter;
import com.cenqua.clover.CloverMerge;

import org.apache.maven.artifact.handler.ArtifactHandler;
import org.apache.maven.project.MavenProject;
import org.apache.maven.reporting.AbstractMavenReport;
import org.apache.maven.reporting.MavenReportException;
import org.apache.maven.model.Plugin;
import org.apache.tools.ant.Project;
import org.codehaus.doxia.sink.Sink;
import org.codehaus.doxia.site.renderer.SiteRenderer;

import java.io.File;
import java.util.*;

/**
 * Generate a <a href="http://cenqua.com/clover">Clover</a> report. The generated report is an external report
 * generated by Clover itself. If the project generating the report is a top level project and if the
 * <code>aggregate</code> configuration element is set to true then an aggregated report will also be created.
 *
 * @goal clover
 * @execute phase="test" lifecycle="clover"
 * @aggregator
 *
 * @author <a href="mailto:vmassol@apache.org">Vincent Massol</a>
 * @version $Id$
 */
public class CloverReportMojo extends AbstractMavenReport
{
    // TODO: Need some way to share config elements and code between report mojos and main build
    // mojos. See http://jira.codehaus.org/browse/MNG-1886

    /**
     * The location of the <a href="http://cenqua.com/clover/doc/adv/database.html">Clover database</a>.
     * 
     * @parameter expression="${project.build.directory}/clover/clover.db"
     * @required
     */
    private String cloverDatabase;

    /**
     * The location of the merged clover database to create when running a report in a multimodule build.
     *
     * @parameter expression="${project.build.directory}/clover/cloverMerge.db"
     * @required
     */
    private String cloverMergeDatabase;

    /**
     * The directory where the Clover report will be generated.
     * 
     * @parameter expression="${project.reporting.outputDirectory}/clover"
     * @required
     */
    private File outputDirectory;

    /**
     * When the Clover Flush Policy is set to "interval" or threaded this value is the minimum 
     * period between flush operations (in milliseconds).
     *
     * @parameter default-value="500"
     */
    protected int flushInterval;

    /**
     * If true we'll wait 2*flushInterval to ensure coverage data is flushed to the Clover 
     * database before running any query on it. 
     * 
     * Note: The only use case where you would want to turn this off is if you're running your 
     * tests in a separate JVM. In that case the coverage data will be flushed by default upon
     * the JVM shutdown and there would be no need to wait for the data to be flushed. As we
     * can't control whether users want to fork their tests or not, we're offering this parameter
     * to them.  
     * 
     * @parameter default-value="true"
     */
    protected boolean waitForFlush;

    /**
     * @component
     */
    private SiteRenderer siteRenderer;

    /**
     * The Maven project. 
     * 
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * The projects in the reactor for aggregation report.
     *
     * @parameter expression="${reactorProjects}"
     * @readonly
     */
    private List reactorProjects;

    /**
     * Whether to build an aggregated report at the root in addition to building individual reports or not.
     *
     * @parameter expression="${aggregate}" default-value="true"
     */
    protected boolean aggregate;

    /**
     * @see org.apache.maven.reporting.AbstractMavenReport#executeReport(java.util.Locale)
     */
    public void executeReport( Locale locale ) throws MavenReportException
    {
        // Only generate module reports for non root projects
        if ( !getProject().isExecutionRoot() )
        {
            AbstractCloverMojo.waitForFlush( this.waitForFlush, this.flushInterval );
            createCloverHtmlReport();
        }

        // If we're in the top level module, then create an extra report by aggregating the generated clover
        // databases.
        if ( this.aggregate && getProject().isExecutionRoot() )
        {
            // Ensure the merged database output directory exists
            new File( this.cloverMergeDatabase ).getParentFile().mkdirs();

            // Merge the databases
            mergeCloverDatabases();

            // Generate the merged report
            createMasterCloverHtmlReport();
        }
    }

    private List getChildrenCloverDatabases()
    {
        // Ideally we'd need to find out where each module stores its Clover database. However that's not
        // currently possible in m2 (see http://jira.codehaus.org/browse/MNG-2180). Thus we'll assume for now
        // that all modules use the cloverDatabase configuration from the top level module.

        // Find out the location of the clover DB relative to the root module.
        // Note: This is a pretty buggy algorithm and we really need a proper solution (see MNG-2180)
        String relativeCloverDatabasePath =
            this.cloverDatabase.substring(getProject().getBasedir().getPath().length());

        List dbFiles = new ArrayList();
        for ( Iterator projects = this.reactorProjects.iterator(); projects.hasNext(); )
        {
            MavenProject project = (MavenProject) projects.next();

            File cloverDb = new File(project.getBasedir(), relativeCloverDatabasePath);
            if (cloverDb.exists())
            {
                dbFiles.add(cloverDb.getPath());
            }
            else
            {
                getLog().warn("Skipping [" + cloverDb.getPath() + "] as it doesn't exist.");
            }
        }

        return dbFiles;
    }

    /**
     * @todo handle multiple source roots. At the moment only the first source root is instrumented
     */
    private void createCloverHtmlReport() throws MavenReportException
    {
        String[] cliArgs = new String[] {
            "-t", "Maven Clover report",
            "-p", (String) this.project.getCompileSourceRoots().get( 0 ),
            "-i", this.cloverDatabase,
            "-o", this.outputDirectory.getPath() };

        int result = HtmlReporter.mainImpl( cliArgs );
        if ( result != 0 )
        {
            throw new MavenReportException( "Clover has failed to create the HTML report" );
        }

    }

    private void createMasterCloverHtmlReport() throws MavenReportException
    {
        String[] args = new String[] {
            "-t", "Maven Aggregated Clover report",
            "-i", this.cloverMergeDatabase,
            "-o", this.outputDirectory.getPath() };

        int reportResult = HtmlReporter.mainImpl( args );
        if ( reportResult != 0 )
        {
            throw new MavenReportException( "Clover has failed to create the merged HTML report" );
        }
    }

    private void mergeCloverDatabases() throws MavenReportException
    {
        List dbFiles = getChildrenCloverDatabases();

        String[] args = new String[dbFiles.size() + 2];
        args[0] = "-i";
        args[1] = this.cloverMergeDatabase;

        int i = 2;
        for ( Iterator dbs = dbFiles.iterator(); dbs.hasNext(); )
        {
            args[i] = (String) dbs.next();
            i++;
        }

        int mergeResult = CloverMerge.mainImpl( args );
        if ( mergeResult != 0 )
        {
            throw new MavenReportException( "Clover has failed to merge the module databases" );
        }
    }

    public String getOutputName()
    {
        return "clover/index";
    }

    /**
     * @see org.apache.maven.reporting.MavenReport#getDescription(java.util.Locale)
     */
    public String getDescription( Locale locale )
    {
        return getBundle( locale ).getString( "report.clover.description" );
    }

    private static ResourceBundle getBundle( Locale locale )
    {
        return ResourceBundle.getBundle( "clover-report", locale, CloverReportMojo.class.getClassLoader() );
    }

    /**
     * @see org.apache.maven.reporting.AbstractMavenReport#getOutputDirectory()
     */
    protected String getOutputDirectory()
    {
        return this.outputDirectory.getAbsoluteFile().toString();
    }

    /**
     * @see org.apache.maven.reporting.AbstractMavenReport#getSiteRenderer()
     */
    protected SiteRenderer getSiteRenderer()
    {
        return this.siteRenderer;
    }

    /**
     * @see org.apache.maven.reporting.AbstractMavenReport#getProject()
     */
    protected MavenProject getProject()
    {
        return this.project;
    }

    /**
     * @see org.apache.maven.reporting.MavenReport#getName(java.util.Locale)
     */
    public String getName( Locale locale )
    {
        return getBundle( locale ).getString( "report.clover.name" );
    }

    /**
     * @see org.apache.maven.reporting.MavenReport#generate(org.codehaus.doxia.sink.Sink, java.util.Locale)
     */
    public void generate( Sink sink, Locale locale )
        throws MavenReportException
    {
        executeReport( locale );
    }

    /**
     * Always return true as we're using the report generated by Clover rather than creating our own report.
     * @return true
     */
    public boolean isExternalReport()
    {
        return true;
    }

    /**
     * Only execute reports for Java projects.
     *
     * @return true if the current project is Java project and false otherwise
     * @see org.apache.maven.reporting.AbstractMavenReport#canGenerateReport()
     */
    public boolean canGenerateReport()
    {
        boolean canGenerate = false;

        if ( this.aggregate && getProject().isExecutionRoot() )
        {
            // Check if we have at least one project which is a java project
            for ( Iterator projects = this.reactorProjects.iterator(); projects.hasNext(); )
            {
                MavenProject project = (MavenProject) projects.next();
                if ( isJavaProject(project) )
                {
                    canGenerate = true;
                    break;
                }
            }
        }
        else if ( isJavaProject(getProject()) )
        {
            canGenerate = true;
        }

        return canGenerate;
    }

    private boolean isJavaProject(MavenProject project)
    {
        ArtifactHandler artifactHandler = project.getArtifact().getArtifactHandler();
        return "java".equals( artifactHandler.getLanguage() );
    }
}
