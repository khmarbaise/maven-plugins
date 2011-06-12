package org.apache.maven.plugin.changes;

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

/**
 * Cross-IMS types of issues.
 */
public enum IssueType
{
    ADD( "add" ), FIX( "fix" ), UPDATE( "update" );

    private String configurationKey;

    private IssueType( String configurationKey )
    {
        this.configurationKey = configurationKey;
    }

    public String configurationKey()
    {
        return configurationKey;
    }

    public String modelRepresentation()
    {
        return name().toLowerCase();
    }

    public static IssueType lookupByKey( String key )
    {
        return IssueType.valueOf( key.toUpperCase() );
    }
}
