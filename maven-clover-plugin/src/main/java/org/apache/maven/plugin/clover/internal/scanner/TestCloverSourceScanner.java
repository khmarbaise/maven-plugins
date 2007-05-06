/*
 * Copyright 2007 The Apache Software Foundation.
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
package org.apache.maven.plugin.clover.internal.scanner;

import org.apache.maven.plugin.clover.internal.CloverConfiguration;

import java.util.List;

/**
 * Computes the list of test source files to instrument.
 *
 * @version $Id: $
 */
public class TestCloverSourceScanner extends AbstractCloverSourceScanner
{
    public TestCloverSourceScanner(CloverConfiguration configuration)
    {
        super( configuration );
    }

    protected List getSourceRoots()
    {
        return getConfiguration().getProject().getTestCompileSourceRoots();
    }

    protected String getSourceDirectory()
    {
        return getConfiguration().getProject().getBuild().getTestSourceDirectory();
    }
}