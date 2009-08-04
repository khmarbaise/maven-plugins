package org.apache.maven.plugins.repository;

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

import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.logging.Log;
import org.codehaus.plexus.components.interactivity.InputHandler;

import java.io.File;
import java.io.FilenameFilter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.Comparator;
import java.util.Iterator;
import java.util.List;
import java.util.StringTokenizer;

final class BundleUtils
{
    private BundleUtils()
    {
    }
    
    public static List selectProjectFiles( final File dir, final InputHandler inputHandler, final String finalName,
                                           final File pom, final Log log, final boolean batchMode )
        throws MojoExecutionException
    {
        File[] projectFiles = dir.listFiles( new FilenameFilter()
        {
            public boolean accept( File dir, String name )
            {
                return new File( dir, name ).isFile() && name.startsWith( finalName );
            }
        } );
        
        List result = new ArrayList();
        
        if ( projectFiles == null )
        {
            return result;
        }
        
        for ( int i = 0; i < projectFiles.length; i++ )
        {
            if ( projectFiles[i].getName().endsWith( ".pom" ) )
            {
                if ( !projectFiles[i].equals( pom ) )
                {
                    log.info( "Detected POM file will be excluded:\n" + projectFiles[i]
                                                                                     + "\n\nInstead, the bundle will include the POM from:\n" + pom );
                }
            }
            else if ( projectFiles[i].getName().endsWith( "-bundle.jar" ) )
            {
                log.warn( "Skipping project file which collides with repository bundle filename:\n" + projectFiles[i] );
            }
            else
            {
                result.add( projectFiles[i] );
            }
        }
        
        if ( result.isEmpty() )
        {
            return result;
        }
        
        Collections.sort( result, new Comparator()
        {
            public int compare( Object first, Object second )
            {
                String f = ((File) first).getName();
                String s = ((File) second).getName();
                
                if ( f.length() == s.length() )
                {
                    return f.compareTo( s );
                }
                
                return f.length() < s.length() ? -1 : 1;
            }
        } );
        
        result = reviseFileList( result, inputHandler, log, batchMode );
        
        return result;
    }

    public static List reviseFileList( List input, InputHandler inputHandler, Log log, boolean batchMode )
        throws MojoExecutionException
    {
        List result = new ArrayList( input );
        
        if ( batchMode )
        {
            return result;
        }
        
        while( true )
        {
            StringBuffer message = new StringBuffer();
            message.append( "The following files are marked for inclusion in the repository bundle:\n" );
            message.append( "\n0.) Done" );
            
            int i = 1;
            for ( Iterator it = result.iterator(); it.hasNext(); )
            {
                File f = (File) it.next();
                message.append( "\n" ).append( (i++) ).append( ".) " ).append( f.getName() );
            }
            
            message.append( "\n\nPlease select the number(s) for any files you wish to exclude, " +
                    "or '0' when you're done.\nSeparate the numbers for multiple files with a " +
                    "comma (',').\n\nSelection: " );
            
            log.info( message );
            String response = null;
            try
            {
                response = inputHandler.readLine();
            }
            catch ( IOException e )
            {
                throw new MojoExecutionException( "Project file selection failed with an I/O exception: " + e.getMessage(), e );
            }
            
            if ( response == null || "0".equals( response ) )
            {
                break;
            }
            
            StringTokenizer st = new StringTokenizer( response, "," );
            
            if ( st.countTokens() > 0 )
            {
                int[] idxs = new int[st.countTokens()];
                for ( int j = 0; j < idxs.length; j++ )
                {
                    idxs[j] = Integer.parseInt( st.nextToken().trim() );
                }
                
                Arrays.sort( idxs );
                
                for( int k = idxs.length - 1; k > -1; k-- )
                {
                    if ( idxs[k] < 1 || idxs[k] > result.size() )
                    {
                        log.warn( "NOT removing: " + idxs[k] + "; no such file." );
                        continue;
                    }
                    
                    File removed = (File) result.remove( idxs[k] -1 );
                    log.info( "Removed: " + removed.getName() );
                }
            }
            else
            {
                break;
            }
        }
        
        return result;
    }

}
