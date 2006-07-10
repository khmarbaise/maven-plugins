package org.apache.maven.plugins.help;

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

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.settings.Settings;
import org.apache.maven.settings.io.xpp3.SettingsXpp3Writer;

import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.io.StringWriter;

/**
 * Print out the calculated settings for this project, given any profile enhancement and 
 *  the inheritance of the global settings into the user-level settings.
 *  
 * @goal effective-settings
 * @requiresProject false
 */
public class EffectiveSettingsMojo
    extends AbstractMojo
{

    /**
     * The system settings for Maven. This is the instance resulting from 
     * merging global- and user-level settings files.
     * 
     * @parameter expression="${settings}"
     * @readonly
     * @required
     */
    private Settings settings;

    /**
     * If specified write the effective settings file out to this path.
     * 
     * @parameter expression="${output}"
     */
    private String output;

    /**
     * @see org.apache.maven.plugin.AbstractMojo#execute() 
     */
    public void execute()
        throws MojoExecutionException
    {
        StringWriter sWriter = new StringWriter();

        SettingsXpp3Writer settingsWriter = new SettingsXpp3Writer();

        try
        {
            settingsWriter.write( sWriter, settings );
        }
        catch ( IOException e )
        {
            throw new MojoExecutionException( "Cannot serialize Settings to XML.", e );
        }

        if ( output != null && output.trim().length() > 0 )
        {
            FileWriter fWriter = null;
            try
            {
                File outFile = new File( output ).getAbsoluteFile();

                File dir = outFile.getParentFile();

                if ( !dir.exists() )
                {
                    dir.mkdirs();
                }

                getLog().info( "Writing effective-settings to: " + outFile );

                fWriter = new FileWriter( outFile );

                fWriter.write( sWriter.toString() );
            }
            catch ( IOException e )
            {
                throw new MojoExecutionException( "Cannot write effective-settings to output: " + output, e );
            }
            finally
            {
                if ( fWriter != null )
                {
                    try
                    {
                        fWriter.close();
                    }
                    catch ( IOException e )
                    {
                        getLog().debug( "Cannot close FileWriter to output location: " + output, e );
                    }
                }
            }
        }
        else
        {
            StringBuffer message = new StringBuffer();

            message.append( "\nEffective settings:\n\n" );
            message.append( sWriter.toString() );
            message.append( "\n\n" );

            getLog().info( message );
        }
    }

    public final void setOutput( String output )
    {
        this.output = output;
    }

    public final void setSettings( Settings settings )
    {
        this.settings = settings;
    }

}
