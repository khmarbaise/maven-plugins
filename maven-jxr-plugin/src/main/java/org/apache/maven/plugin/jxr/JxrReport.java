package org.apache.maven.plugin.jxr;

/*
 * Copyright 2004-2006 The Apache Software Foundation.
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

import org.apache.maven.model.ReportPlugin;
import org.apache.maven.project.MavenProject;

import java.io.File;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;

/**
 * Creates an html-based, cross referenced version of Java source code
 * for a project.
 *
 * @author <a href="mailto:bellingard.NO-SPAM@gmail.com">Fabrice Bellingard</a>
 * @goal jxr
 */
public class JxrReport
    extends AbstractJxrReport
{
    /**
     * Source directories of the project.
     *
     * @parameter expression="${project.compileSourceRoots}"
     * @required
     * @readonly
     */
    private List sourceDirs;

    /**
     * Folder where the Xref files will be copied to.
     *
     * @parameter expression="${project.reporting.outputDirectory}/xref"
     */
    private String destDir;

    /**
     * Folder where Javadoc is generated for this project.
     *
     * @parameter expression="${project.reporting.outputDirectory}/apidocs"
     */
    private File javadocDir;

    /**
     * Link the Javadoc from the Source XRef. Defaults to true and will link
     * automatically if javadoc plugin is being used.
     *
     * @parameter expression="${linkJavadoc}" default-value="true"
     */
    private boolean linkJavadoc;

    /**
     * @see org.apache.maven.plugin.jxr.AbstractJxrReport#getDestinationDirectory()
     */
    protected String getDestinationDirectory()
    {
        return destDir;
    }

    /**
     * @see org.apache.maven.plugin.jxr.AbstractJxrReport#getSourceRoots()
     */
    protected List getSourceRoots()
    {
        return this.sourceDirs;
    }

    /**
     * @see org.apache.maven.plugin.jxr.AbstractJxrReport#getSourceRoots(org.apache.maven.project.MavenProject)
     */
    protected List getSourceRoots( MavenProject project )
    {
        return project.getCompileSourceRoots();
    }

    /**
     * Cf. overriden method documentation.
     *
     * @see org.apache.maven.reporting.MavenReport#getDescription(java.util.Locale)
     */
    public String getDescription( Locale locale )
    {
        return getBundle( locale ).getString( "report.xref.main.description" );
    }

    /**
     * Cf. overriden method documentation.
     *
     * @see org.apache.maven.reporting.MavenReport#getName(java.util.Locale)
     */
    public String getName( Locale locale )
    {
        return getBundle( locale ).getString( "report.xref.main.name" );
    }

    /**
     * Cf. overriden method documentation.
     *
     * @see org.apache.maven.reporting.MavenReport#getOutputName()
     */
    public String getOutputName()
    {
        return "xref/index";
    }

    /**
     * @see org.apache.maven.plugin.jxr.AbstractJxrReport#getJavadocLocation()
     */
    protected String getJavadocLocation()
    {
        String location = null;
        if ( linkJavadoc )
        {
            // We don't need to do the whole translation thing like normal, because JXR does it internally.
            // It probably shouldn't.
            if ( javadocDir.exists() )
            {
                // XRef was already generated by manual execution of a lifecycle binding
                location = javadocDir.getAbsolutePath();
            }
            else
            {
                // Not yet generated - check if the report is on its way
                for ( Iterator reports = getProject().getReportPlugins().iterator(); reports.hasNext(); )
                {
                    ReportPlugin report = (ReportPlugin) reports.next();

                    String artifactId = report.getArtifactId();
                    if ( "maven-javadoc-plugin".equals( artifactId ) )
                    {
                        location = javadocDir.getAbsolutePath();
                    }
                }
            }

            if ( location == null )
            {
                getLog().warn( "Unable to locate Javadoc to link to - DISABLED" );
            }
        }
        return location;
    }

    /**
     * @see org.apache.maven.reporting.AbstractMavenReport#setReportOutputDirectory(java.io.File)
     */
    public void setReportOutputDirectory( File reportOutputDirectory )
    {
        if ( ( reportOutputDirectory != null ) && ( !reportOutputDirectory.getAbsolutePath().endsWith( "xref" ) ) )
        {
            this.destDir = new File( reportOutputDirectory, "xref" ).getAbsolutePath();
        }
        else
        {
            this.destDir = reportOutputDirectory.getAbsolutePath();
        }
    }
}
