package org.apache.maven.plugin.gpg;

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

import java.io.File;
import java.io.IOException;

import org.apache.maven.plugin.AbstractMojo;
import org.apache.maven.plugin.MojoExecutionException;
import org.apache.maven.plugin.MojoFailureException;
import org.apache.maven.project.MavenProject;

/**
 * @author Benjamin Bentmann
 */
public abstract class AbstractGpgMojo
    extends AbstractMojo
{

    /**
     * The directory from which gpg will load keyrings. If not specified, gpg will use the value configured for its
     * installation, e.g. <code>~/.gnupg</code> or <code>%APPDATA%/gnupg</code>.
     * 
     * @parameter expression="${gpg.homedir}"
     * @since 1.0
     */
    private File homedir;

    /**
     * The passphrase to use when signing.
     * 
     * @parameter expression="${gpg.passphrase}"
     */
    private String passphrase;

    /**
     * The "name" of the key to sign with. Passed to gpg as <code>--local-user</code>.
     * 
     * @parameter expression="${gpg.keyname}"
     */
    private String keyname;

    /**
     * Passes <code>--use-agent</code> or <code>--no-use-agent</code> to gpg. If using an agent, the passphrase is
     * optional as the agent will provide it.
     * 
     * @parameter expression="${gpg.useagent}" default-value="false"
     */
    private boolean useAgent;

    /**
     * @parameter default-value="${settings.interactiveMode}"
     * @readonly
     */
    private boolean interactive;

    GpgSigner newSigner( MavenProject project )
        throws MojoExecutionException, MojoFailureException
    {
        GpgSigner signer = new GpgSigner();

        signer.setInteractive( interactive );
        signer.setKeyName( keyname );
        signer.setUseAgent( useAgent );
        signer.setHomeDirectory( homedir );

        signer.setPassPhrase( passphrase );
        if ( null == passphrase && !useAgent )
        {
            if ( !interactive )
            {
                throw new MojoFailureException( "Cannot obtain passphrase in batch mode" );
            }
            try
            {
                signer.setPassPhrase( signer.getPassphrase( project ) );
            }
            catch ( IOException e )
            {
                throw new MojoExecutionException( "Exception reading passphrase", e );
            }
        }

        return signer;
    }

}
