package org.apache.maven.plugins.pdf;

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
import java.io.Reader;
import java.io.StringReader;
import java.io.Writer;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.TimeZone;

import org.apache.maven.doxia.docrenderer.DocumentRenderer;
import org.apache.maven.doxia.docrenderer.DocumentRendererException;
import org.apache.maven.doxia.docrenderer.pdf.PdfRenderer;
import org.apache.maven.doxia.document.DocumentCover;
import org.apache.maven.doxia.document.DocumentMeta;
import org.apache.maven.doxia.document.DocumentModel;
import org.apache.maven.doxia.document.DocumentTOC;
import org.apache.maven.doxia.document.DocumentTOCItem;
import org.apache.maven.doxia.document.io.xpp3.DocumentXpp3Reader;
import org.apache.maven.doxia.document.io.xpp3.DocumentXpp3Writer;
import org.apache.maven.doxia.site.decoration.DecorationModel;
import org.apache.maven.doxia.site.decoration.Menu;
import org.apache.maven.doxia.site.decoration.MenuItem;
import org.apache.maven.doxia.site.decoration.io.xpp3.DecorationXpp3Reader;
import org.apache.maven.doxia.tools.SiteToolException;

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.plugin.logging.Log;
import org.apache.maven.project.MavenProject;
import org.apache.maven.wagon.PathUtils;
import org.codehaus.plexus.interpolation.EnvarBasedValueSource;
import org.codehaus.plexus.interpolation.InterpolationException;
import org.codehaus.plexus.interpolation.Interpolator;
import org.codehaus.plexus.interpolation.MapBasedValueSource;
import org.codehaus.plexus.interpolation.ObjectBasedValueSource;
import org.codehaus.plexus.interpolation.RegexBasedInterpolator;
import org.codehaus.plexus.util.FileUtils;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.WriterFactory;
import org.codehaus.plexus.util.introspection.ReflectionValueExtractor;
import org.codehaus.plexus.util.xml.XmlStreamReader;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * Generates a PDF document for a project.
 *
 * @author ltheussl
 * @version $Id$
 *
 * @goal pdf
 */
public class PdfMojo
    extends AbstractPdfMojo
{
    // ----------------------------------------------------------------------
    // Mojo Parameters
    // ----------------------------------------------------------------------

    /**
     * The Maven Project Object.
     *
     * @parameter expression="${project}"
     * @required
     * @readonly
     */
    private MavenProject project;

    /**
     * Directory containing source for apt, fml and xdoc docs.
     *
     * @parameter expression="${basedir}/src/site"
     * @required
     */
    private File siteDirectory;

    /**
     * Directory containing the generated project sites and report distributions.
     *
     * @parameter alias="workingDirectory" expression="${project.build.directory}/pdf"
     * @required
     */
    private File outputDirectory;

    /**
     * File that contains the DocumentModel of the PDF to generate.
     *
     * @parameter expression="src/site/pdf.xml"
     */
    private File docDescriptor;

    /**
     * Identifies the framework to use for pdf generation: either "fo" (default) or "itext".
     *
     * @parameter expression="${implementation}" default-value="fo"
     * @required
     */
    private String implementation;

    // ----------------------------------------------------------------------
    // Mojo components
    // ----------------------------------------------------------------------

    /**
     * FO Document Renderer.
     *
     * @component role-hint="fo"
     */
    private PdfRenderer foRenderer;

    /**
     * IText Document Renderer.
     *
     * @component role-hint="itext"
     */
    private PdfRenderer itextRenderer;

    /**
     * Document Renderer.
     */
    private DocumentRenderer docRenderer;

    /**
     * Default locale
     */
    private Locale defaultLocale;

    /**
     * Default decoration model
     */
    private DecorationModel defaultDecorationModel;

    // ----------------------------------------------------------------------
    // Public methods
    // ----------------------------------------------------------------------

    /** {@inheritDoc} */
    public void execute()
        throws MojoExecutionException, MojoFailureException
    {
        if ( "fo".equals( implementation ) )
        {
            this.docRenderer = foRenderer;
        }
        else if ( "itext".equals( implementation ) )
        {
            this.docRenderer = itextRenderer;
        }
        else
        {
            throw new MojoFailureException( "Not a valid implementation: " + implementation );
        }

        try
        {
            List localesList = getSiteTool().getAvailableLocales( getLocales() );

            // Default is first in the list
            Locale defaultLocale = (Locale) localesList.get( 0 );

            Locale.setDefault( defaultLocale );

            for ( Iterator iterator = localesList.iterator(); iterator.hasNext(); )
            {
                Locale locale = (Locale) iterator.next();

                File outputDir = getOutputDirectory( locale, defaultLocale );

                File siteDirectoryFile = siteDirectory;

                if ( !locale.getLanguage().equals( defaultLocale.getLanguage() ) )
                {
                    siteDirectoryFile = new File( siteDirectory, locale.getLanguage() );
                }

                docRenderer.render( siteDirectoryFile, outputDir, getDocumentModel() );
            }
        }
        catch ( DocumentRendererException e )
        {
            throw new MojoExecutionException( "Error during document generation", e );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Error during document generation", e );
        }
    }

    // ----------------------------------------------------------------------
    // Private methods
    // ----------------------------------------------------------------------

    /**
     * Constructs a DocumentModel for the current project. The model is either read from
     * a descriptor file, if it exists, or constructed from information in the pom and site.xml.
     *
     * @return DocumentModel.
     * @throws DocumentRendererException if any.
     * @throws IOException if any.
     * @throws MojoExecutionException if any
     * @see #readAndFilterDocumentDescriptor(MavenProject, File, Log)
     */
    private DocumentModel getDocumentModel()
        throws DocumentRendererException, IOException, MojoExecutionException
    {
        if ( docDescriptor.exists() )
        {
            return readAndFilterDocumentDescriptor( project, docDescriptor, getLog() );
        }

        return generateDefaultDocDescriptor();
    }

    /**
     * Return the output directory for a given Locale and the current default Locale.
     *
     * @param locale a Locale.
     * @param defaultLocale the current default Locale.
     * @return File.
     * @todo can be re-used
     */
    private File getOutputDirectory( Locale locale, Locale defaultLocale )
    {
        if ( locale.getLanguage().equals( defaultLocale.getLanguage() ) )
        {
            return outputDirectory;
        }

        return new File( outputDirectory, locale.getLanguage() );
    }

    /**
     * @return Generate a default document descriptor from the Maven project
     * @throws IOException if any
     * @throws MojoExecutionException if any
     */
    private DocumentModel generateDefaultDocDescriptor()
        throws IOException, MojoExecutionException
    {
        DocumentMeta meta = new DocumentMeta();
        meta.setAuthor( getProjectOrganizationName() );
        meta.setTitle( getProjectName() );
        meta.setDescription( project.getDescription() );

        DocumentCover cover = new DocumentCover();
        cover.setCoverTitle( getProjectName() );
        cover.setCoverVersion( project.getVersion() );
        cover.setCoverType( getI18n().getString( "pdf-plugin", getDefaultLocale(), "toc.type" ) );
        cover.setDate( Integer.toString( Calendar.getInstance().get( Calendar.YEAR ) ) );
        cover.setProjectName( getProjectName() );
        cover.setCompanyName( getProjectOrganizationName() );

        DocumentModel docModel = new DocumentModel();
        docModel.setModelEncoding( getProjectModelEncoding() );
        docModel.setOutputName( project.getArtifactId() );
        docModel.setMeta( meta );
        docModel.setCover( cover );

        // Populate docModel from defaultDecorationModel
        DecorationModel decorationModel = getDefaultDecorationModel();

        if ( decorationModel != null )
        {
            DocumentTOC toc = new DocumentTOC();

            toc.setName( getI18n().getString( "pdf-plugin", getDefaultLocale(), "toc.title" ) );

            for ( Iterator it = decorationModel.getMenus().iterator(); it.hasNext(); )
            {
                Menu menu = (Menu) it.next();

                for ( Iterator it2 = menu.getItems().iterator(); it2.hasNext(); )
                {
                    MenuItem item = (MenuItem) it2.next();

                    DocumentTOCItem documentTOCItem = new DocumentTOCItem();
                    documentTOCItem.setName( item.getName() );
                    documentTOCItem.setRef( item.getHref() );
                    toc.addItem( documentTOCItem );
                }
            }

            docModel.setToc( toc );
        }

        if ( getLog().isDebugEnabled() )
        {
            File outputDir = new File( project.getBuild().getDirectory(), "pdf" );

            if ( outputDir.isFile() )
            {
                throw new IOException( outputDir + " is not a directory!" );
            }

            if ( !outputDir.exists() )
            {
                outputDir.mkdirs();
            }

            File doc = FileUtils.createTempFile( "pdf", ".xml", outputDir );

            getLog().debug( "Generated a default document model: " + doc.getAbsolutePath() );

            DocumentXpp3Writer xpp3 = new DocumentXpp3Writer();
            Writer w = null;
            try
            {
                w = WriterFactory.newPlatformWriter( doc );
                xpp3.write( w, docModel );
            }
            finally
            {
                IOUtil.close( w );
            }
        }

        return docModel;
    }

    private String getProjectOrganizationName()
    {
        return ( project.getOrganization() != null
            && StringUtils.isNotEmpty( project.getOrganization().getName() )
                ? project.getOrganization().getName()
                : System.getProperty( "user.name" ) );
    }

    private String getProjectName()
    {
        return ( StringUtils.isEmpty( project.getName() )
                ? project.getGroupId() + ":" + project.getArtifactId()
                : project.getName() );
    }

    private String getProjectModelEncoding()
    {
        return ( StringUtils.isEmpty( project.getModel().getModelEncoding() )
                ? "UTF-8"
                : project.getModel().getModelEncoding() );
    }

    /**
     * @return the default locale from <code>siteTool</code>.
     */
    private Locale getDefaultLocale()
    {
        if ( this.defaultLocale == null )
        {
            List localesList = getSiteTool().getAvailableLocales( getLocales() );
            this.defaultLocale = (Locale) localesList.get( 0 );
        }

        return this.defaultLocale;
    }

    /**
     * @return the DecorationModel instance from <code>site.xml</code>
     * @throws MojoExecutionException if any
     */
    private DecorationModel getDefaultDecorationModel()
        throws MojoExecutionException
    {
        if ( this.defaultDecorationModel == null )
        {
            Locale locale = getDefaultLocale();

            File descriptorFile =
                getSiteTool()
                             .getSiteDescriptorFromBasedir(
                                                            PathUtils.toRelative( project.getBasedir(),
                                                                                  siteDirectory.getAbsolutePath() ),
                                                            project.getBasedir(), locale );
            DecorationModel decoration = null;
            if ( descriptorFile.exists() )
            {
                Map props = new HashMap();

                XmlStreamReader reader = null;
                try
                {
                    reader = ReaderFactory.newXmlReader( descriptorFile );
                    String siteDescriptorContent = IOUtil.toString( reader );

                    siteDescriptorContent =
                        getSiteTool().getInterpolatedSiteDescriptorContent( props, project, siteDescriptorContent,
                                                                            reader.getEncoding(), reader.getEncoding() );

                    decoration = new DecorationXpp3Reader().read( new StringReader( siteDescriptorContent ) );
                }
                catch ( XmlPullParserException e )
                {
                    throw new MojoExecutionException( "Error parsing site descriptor", e );
                }
                catch ( IOException e )
                {
                    throw new MojoExecutionException( "Error reading site descriptor", e );
                }
                catch ( SiteToolException e )
                {
                    throw new MojoExecutionException( "Error when interpoling site descriptor", e );
                }
                finally
                {
                    IOUtil.close( reader );
                }
            }

            this.defaultDecorationModel = decoration;
        }

        return this.defaultDecorationModel;
    }

    /**
     * Read and filter the <code>docDescriptor</code> file.
     *
     * @param project not null
     * @param docDescriptor not null
     * @param log not null
     * @return a DocumentModel instance
     * @throws DocumentRendererException if any
     * @throws IOException if any
     */
    private DocumentModel readAndFilterDocumentDescriptor( final MavenProject project, File docDescriptor, Log log )
        throws DocumentRendererException, IOException
    {
        Reader reader = null;
        try
        {
            // System properties
            Properties filterProperties = System.getProperties();
            // Project properties
            if ( project != null && project.getProperties() != null )
            {
                filterProperties.putAll( project.getProperties() );
            }

            Interpolator interpolator = new RegexBasedInterpolator();
            interpolator.addValueSource( new MapBasedValueSource( filterProperties ) );
            interpolator.addValueSource( new EnvarBasedValueSource() );
            interpolator.addValueSource( new ObjectBasedValueSource( project )
            {
                /** {@inheritDoc} */
                public Object getValue( String expression )
                {
                    try
                    {
                        return ReflectionValueExtractor.evaluate( expression, project );
                    }
                    catch ( Exception e )
                    {
                        addFeedback( "Failed to extract \'" + expression + "\' from: " + project, e );
                    }

                    return null;
                }
            } );
            final DateBean bean = new DateBean();
            interpolator.addValueSource( new ObjectBasedValueSource( bean ) );

            reader = ReaderFactory.newXmlReader( docDescriptor );

            String interpolatedDoc = interpolator.interpolate( IOUtil.toString( reader ) );

            if ( getLog().isDebugEnabled() )
            {
                getLog().debug(
                                "Interpolated document descriptor (" + docDescriptor.getAbsolutePath() + ")\n"
                                    + interpolatedDoc );
            }

            // No Strict
            return new DocumentXpp3Reader().read( new StringReader( interpolatedDoc ), false );
        }
        catch ( XmlPullParserException e )
        {
            throw new DocumentRendererException( "Error parsing document descriptor", e );
        }
        catch ( InterpolationException e )
        {
            throw new DocumentRendererException( "Error interpolating document descriptor", e );
        }
        finally
        {
            IOUtil.close( reader );
        }
    }

    /**
     * Simple bean to allow date interpolation in the document descriptor, i.e.
     * <pre>
     * ${year}  = 2009
     * ${date}  = 2009-05-17
     * </pre>
     */
    public static class DateBean
    {
        private static final TimeZone UTC_TIME_ZONE = TimeZone.getTimeZone( "UTC" );

        /**
         * @return the current year.
         */
        public String getYear()
        {
            return new SimpleDateFormat( "yyyy", Locale.US ).format( new Date() );
        }

        /**
         * @return the current month.
         */
        public String getMonth()
        {
            return new SimpleDateFormat( "MM", Locale.US ).format( new Date() );
        }

        /**
         * @return the current day.
         */
        public String getDay()
        {
            return new SimpleDateFormat( "dd", Locale.US ).format( new Date() );
        }

        /**
         * @return the current hour.
         */
        public String getHour()
        {
            return new SimpleDateFormat( "HH", Locale.US ).format( new Date() );
        }

        /**
         * @return the current minute.
         */
        public String getMinute()
        {
            return new SimpleDateFormat( "mm", Locale.US ).format( new Date() );
        }

        /**
         * @return the current second.
         */
        public String getSecond()
        {
            return new SimpleDateFormat( "ss", Locale.US ).format( new Date() );
        }

        /**
         * @return the current millisecond.
         */
        public String getMillisecond()
        {
            return new SimpleDateFormat( "SSS", Locale.US ).format( new Date() );
        }

        /**
         * @return the current date using the ISO 8601 format, i.e. <code>yyyy-MM-dd</code>.
         */
        public String getDate()
        {
            return new SimpleDateFormat( "yyyy-MM-dd", Locale.US ).format( new Date() );
        }

        /**
         * @return the current time using the ISO 8601 format and UTC time zone, i.e. <code>HH:mm:ss'Z'</code>.
         */
        public String getTime()
        {
            SimpleDateFormat sdf = new SimpleDateFormat( "HH:mm:ss'Z'", Locale.US );
            sdf.setTimeZone( UTC_TIME_ZONE );
            return sdf.format( new Date() );
        }

        /**
         * @return the current datetime using the ISO 8601 format, i.e. <code>yyyy-MM-dd'T'HH:mm:ss'Z'</code>.
         */
        public String getDateTime()
        {
            SimpleDateFormat sdf = new SimpleDateFormat( "yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US );
            sdf.setTimeZone( UTC_TIME_ZONE );
            return sdf.format( new Date() );
        }
    }
}
