package org.apache.maven.report.projectinfo.dependencies;

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

import org.apache.maven.shared.jar.classes.JarClasses;

import java.util.List;

public class JarDependencyDetails
{
    private JarClasses jarClasses;

    private boolean isSealed;

    private List entries;

    public JarDependencyDetails( JarClasses classes, boolean sealed, List entries )
    {
        jarClasses = classes;
        isSealed = sealed;
        this.entries = entries;
    }

    public boolean isDebugPresent()
    {
        return jarClasses.isDebugPresent();
    }

    public boolean isSealed()
    {
        return isSealed;
    }

    public String getJdkRevision()
    {
        return jarClasses.getJdkRevision();
    }

    public long getPackageSize()
    {
        return jarClasses.getPackages().size();
    }

    public long getClassSize()
    {
        return jarClasses.getClassNames().size();
    }

    public long getEntries()
    {
        return entries.size();
    }
}
