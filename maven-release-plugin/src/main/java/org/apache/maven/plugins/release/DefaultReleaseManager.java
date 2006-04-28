package org.apache.maven.plugins.release;

/*
 * Copyright 2005-2006 The Apache Software Foundation.
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

import org.apache.maven.plugins.release.config.ReleaseConfiguration;
import org.apache.maven.plugins.release.config.ReleaseConfigurationStore;
import org.apache.maven.plugins.release.config.ReleaseConfigurationStoreException;
import org.apache.maven.plugins.release.phase.ReleasePhase;
import org.codehaus.plexus.logging.AbstractLogEnabled;

import java.util.List;
import java.util.Map;

/**
 * Implementation of the release manager.
 *
 * @author <a href="mailto:brett@apache.org">Brett Porter</a>
 */
public class DefaultReleaseManager
    extends AbstractLogEnabled
    implements ReleaseManager
{
    /**
     * Whether to only step through and state the tasks, or to execute them.
     */
    private boolean dryRun;

    /**
     * Whether to resume a previous release, if it was in progress. Defaults to true.
     */
    private boolean resume = true;

    /**
     * The phases of release to run, and in what order.
     */
    private List phases;

    /**
     * The available phases.
     */
    private Map releasePhases;

    /**
     * The configuration storage.
     */
    private ReleaseConfigurationStore configStore;

    public void prepare( ReleaseConfiguration releaseConfiguration )
        throws ReleaseExecutionException
    {
        ReleaseConfiguration config;
        if ( resume )
        {
            try
            {
                config = configStore.read( releaseConfiguration );
            }
            catch ( ReleaseConfigurationStoreException e )
            {
                throw new ReleaseExecutionException( "Error reading stored configuration: " + e.getMessage(), e );
            }
        }
        else
        {
            config = releaseConfiguration;
        }

        // Later, it would be a good idea to introduce a proper workflow tool so that the release can be made up of a
        // more flexible set of steps.

        String completedPhase = config.getCompletedPhase();
        int index = phases.indexOf( completedPhase );

        // start from next phase
        for ( int i = index + 1; i < phases.size(); i++ )
        {
            String name = (String) phases.get( i );

            ReleasePhase phase = (ReleasePhase) releasePhases.get( name );

            if ( phase == null )
            {
                throw new ReleaseExecutionException( "Unable to find phase '" + name + "' to execute" );
            }

            if ( dryRun )
            {
                phase.simulate( config );
            }
            else
            {
                phase.execute( config );
            }

            config.setCompletedPhase( name );
            try
            {
                configStore.write( config );
            }
            catch ( ReleaseConfigurationStoreException e )
            {
                // TODO: rollback?
                throw new ReleaseExecutionException( "Error writing release properties after completing phase", e );
            }
        }
    }

    public void perform()
    {
        //To change body of implemented methods use File | Settings | File Templates. TODO

        clean();
    }

    public void clean()
    {
        //To change body of implemented methods use File | Settings | File Templates. TODO
    }

    void setConfigStore( ReleaseConfigurationStore configStore )
    {
        this.configStore = configStore;
    }
}
