package org.apache.maven.plugin.assembly.filter;

/*
 * Copyright 2001-2005 The Apache Software Foundation.
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

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.util.Collections;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.plexus.archiver.AbstractArchiveFinalizer;
import org.codehaus.plexus.archiver.ArchiveFileFilter;
import org.codehaus.plexus.archiver.ArchiveFilterException;
import org.codehaus.plexus.archiver.Archiver;
import org.codehaus.plexus.archiver.ArchiverException;
import org.codehaus.plexus.components.io.fileselectors.FileInfo;
import org.codehaus.plexus.components.io.fileselectors.FileSelector;
import org.codehaus.plexus.util.IOUtil;
import org.codehaus.plexus.util.xml.Xpp3Dom;
import org.codehaus.plexus.util.xml.Xpp3DomBuilder;
import org.codehaus.plexus.util.xml.Xpp3DomWriter;
import org.codehaus.plexus.util.xml.pull.XmlPullParserException;

/**
 * Components XML file filter.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 */
public class ComponentsXmlArchiverFileFilter
    extends AbstractArchiveFinalizer
    implements ArchiveFileFilter, FileSelector
{
    // [jdcasey] Switched visibility to protected to allow testing. Also, because this class isn't final, it should allow
    // some minimal access to the components accumulated for extending classes.
    protected Map components;

    private boolean excludeOverride = false;

    public static final String COMPONENTS_XML_PATH = "META-INF/plexus/components.xml";

    public boolean include( InputStream dataStream, String entryName )
        throws ArchiveFilterException
    {
        try
        {
            return isIncluded( dataStream, entryName );
        }
        catch ( XmlPullParserException e )
        {
            throw new ArchiveFilterException( "Error reading components from stream: " + e.getMessage(), e );
        }
        catch ( IOException e )
        {
            throw new ArchiveFilterException( "Error reading components from stream: " + e.getMessage(), e );
        }
    }

    protected void addComponentsXml( Reader componentsReader )
        throws XmlPullParserException, IOException
    {
        Xpp3Dom newDom = Xpp3DomBuilder.build( componentsReader );

        if ( newDom != null )
        {
            newDom = newDom.getChild( "components" );
        }

        if ( newDom != null )
        {
            Xpp3Dom[] children = newDom.getChildren();
            for ( int i = 0; i < children.length; i++ )
            {
                Xpp3Dom component = children[i];

                if ( components == null )
                {
                    components = new LinkedHashMap();
                }

                String role = component.getChild( "role" ).getValue();
                Xpp3Dom child = component.getChild( "role-hint" );
                String roleHint = child != null ? child.getValue() : "";
                components.put( role + roleHint, component );
            }
        }
    }

//    public void addComponentsXml( File componentsXml )
//        throws IOException, XmlPullParserException
//    {
//        FileReader fileReader = null;
//        try
//        {
//            fileReader = new FileReader( componentsXml );
//
//            addComponentsXml( fileReader );
//        }
//        finally
//        {
//            IOUtil.close( fileReader );
//        }
//    }

    private void addToArchive( Archiver archiver )
        throws IOException, ArchiverException
    {
        if ( components != null )
        {
            File f = File.createTempFile( "maven-assembly-plugin", "tmp" );
            f.deleteOnExit();

            FileWriter fileWriter = new FileWriter( f );
            try
            {
                Xpp3Dom dom = new Xpp3Dom( "component-set" );
                Xpp3Dom componentDom = new Xpp3Dom( "components" );
                dom.addChild( componentDom );

                for ( Iterator i = components.values().iterator(); i.hasNext(); )
                {
                    Xpp3Dom component = (Xpp3Dom) i.next();
                    componentDom.addChild( component );
                }

                Xpp3DomWriter.write( fileWriter, dom );
            }
            finally
            {
                IOUtil.close( fileWriter );
            }

            excludeOverride = true;

            archiver.addFile( f, COMPONENTS_XML_PATH );

            excludeOverride = false;
        }
    }

    public void finalizeArchiveCreation( Archiver archiver )
        throws ArchiverException
    {
        try
        {
            addToArchive( archiver );
        }
        catch ( IOException e )
        {
            throw new ArchiverException( "Error finalizing component-set for archive. Reason: " + e.getMessage(), e );
        }
    }

    public List getVirtualFiles()
    {
        if ( ( components != null ) && !components.isEmpty() )
        {
            return Collections.singletonList( COMPONENTS_XML_PATH );
        }

        return null;
    }

    private boolean isIncluded( InputStream dataStream, String entryName )
        throws XmlPullParserException, IOException
    {
        if ( excludeOverride )
        {
            return true;
        }

        String entry = entryName;

        if ( entry.startsWith( "/" ) )
        {
            entry = entry.substring( 1 );
        }

        if ( ComponentsXmlArchiverFileFilter.COMPONENTS_XML_PATH.equals( entry ) )
        {
            addComponentsXml( new InputStreamReader( dataStream ) );

            return false;
        }

        return true;
    }

    public boolean isSelected( FileInfo fileInfo )
        throws IOException
    {
        try
        {
            return isIncluded( fileInfo.getContents(), fileInfo.getName() );
        }
        catch ( XmlPullParserException e )
        {
            IOException error = new IOException( "Error finalizing component-set for archive. Reason: " + e.getMessage() );
            error.initCause( e );

            throw error;
        }
    }

}
