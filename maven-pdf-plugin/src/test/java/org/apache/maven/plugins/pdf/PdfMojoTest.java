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

import org.apache.maven.plugin.testing.AbstractMojoTestCase;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.ReaderFactory;
import org.codehaus.plexus.util.StringUtils;
import org.codehaus.plexus.util.cli.CommandLineUtils;

import java.io.File;
import java.io.Reader;

/**
 * @author ltheussl
 * @version $Id$
 */
public class PdfMojoTest
    extends AbstractMojoTestCase
{
    /** {@inheritDoc} */
    protected void setUp() throws Exception
    {
        // required for mojo lookups to work
        super.setUp();
    }

    /**
     * Tests the basic functioning of the pdf generation using the FO implementation.
     *
     * @throws Exception if any.
     */
    public void testPdfMojo() throws Exception
    {
        File testPom = new File( getBasedir(), "/target/test-classes/unit/pdf/pom.xml" );
        assertTrue( "testPom does not exist!", testPom.exists() );

        PdfMojo mojo = (PdfMojo) lookupMojo( "pdf", testPom );
        assertNotNull( "pdf mojo not found!", mojo );

        File pdfFile = new File( getBasedir(), "/target/test-output/pdf/fo/maven-pdf-plugin-doc.pdf" );
        if ( pdfFile.exists() )
        {
            pdfFile.delete();
        }

        mojo.execute();

        assertTrue( "FO: Pdf file not created!", pdfFile.exists() );
        assertTrue( "FO: Pdf file has no content!", pdfFile.length() > 0 );
    }

    /**
     * Tests the basic functioning of the pdf generation with iText.
     *
     * @throws Exception if any.
     */
    public void testITextImpl() throws Exception
    {
        File testPom = new File( getBasedir(), "/target/test-classes/unit/pdf/iText_pom.xml" );
        assertTrue( "testPom does not exist!", testPom.exists() );

        PdfMojo mojo = (PdfMojo) lookupMojo( "pdf", testPom );
        assertNotNull( "pdf mojo not found!", mojo );

        File pdfFile = new File( getBasedir(), "/target/test-output/pdf/itext/maven-pdf-plugin-doc.pdf" );
        if ( pdfFile.exists() )
        {
            pdfFile.delete();
        }

        mojo.execute();

        assertTrue( "iText: Pdf file not created!", pdfFile.exists() );
        assertTrue( "iText: Pdf file has no content!", pdfFile.length() > 0 );
     }

    /**
     * @throws Exception if any.
     */
    public void testPdfFilterMojo() throws Exception
    {
        File testPom = new File( getBasedir(), "/target/test-classes/unit/pdf/pom_filtering.xml" );
        assertTrue( "testPom does not exist!", testPom.exists() );

        PdfMojo mojo = (PdfMojo) lookupMojo( "pdf", testPom );
        assertNotNull( "pdf mojo not found!", mojo );

        File pdfFile = new File( getBasedir(), "/target/test-output/pdf/filtering/maven-pdf-plugin-doc-1.0-SNAPSHOT.pdf" );
        if ( pdfFile.exists() )
        {
            pdfFile.delete();
        }

        mojo.execute();

        assertTrue( "FO: Pdf file not created!", pdfFile.exists() );
        assertTrue( "FO: Pdf file has no content!", pdfFile.length() > 0 );

        File foFile = new File( getBasedir(), "/target/test-output/pdf/filtering/maven-pdf-plugin-doc-1.0-SNAPSHOT.fo" );
        assertTrue( "FO: Fo file not created!", foFile.exists() );
        assertTrue( "FO: Fo file has no content!", foFile.length() > 0 );

        Reader reader = null;
        String foContent;
        try
        {
            reader = ReaderFactory.newXmlReader( foFile );
            foContent = IOUtil.toString( reader );
        }
        finally
        {
            IOUtil.close( reader );
        }
        // ${pom.name}
        assertTrue( foContent.indexOf( "Test filtering" ) > 0 );
        assertTrue( foContent.indexOf( "1.0-SNAPSHOT" ) > 0 );
        // env ${M2_HOME}
        String m2Home = CommandLineUtils.getSystemEnvVars().getProperty( "M2_HOME" );
        if ( StringUtils.isNotEmpty( m2Home ) )
        {
            assertTrue( foContent.indexOf( m2Home ) > 0 );
        }
        // ${project.developers[0].email}
        assertTrue( foContent.indexOf( "vsiveton@apache.org ltheussl@apache.org" ) > 0 );
        // ${date}
        // TODO: this might fail on NewYear's eve! :)
        assertTrue( foContent.indexOf( new DateBean().getDate() ) > 0 );
    }

    /**
     * Tests the basic functioning of the pdf generation using the FO implementation.
     *
     * @throws Exception if any.
     */
    public void testPdfMojoNoDocDesriptor() throws Exception
    {
        File testPom = new File( getBasedir(), "/target/test-classes/unit/pdf/no_docdescriptor_pom.xml" );
        assertTrue( "testPom does not exist!", testPom.exists() );

        PdfMojo mojo = (PdfMojo) lookupMojo( "pdf", testPom );
        assertNotNull( "pdf mojo not found!", mojo );

        File pdfFile = new File( getBasedir(), "/target/test-output/pdf/no/unnamed.pdf" );
        if ( pdfFile.exists() )
        {
            pdfFile.delete();
        }

        mojo.execute();

        assertTrue( "FO: Pdf file not created!", pdfFile.exists() );
        assertTrue( "FO: Pdf file has no content!", pdfFile.length() > 0 );
    }
}