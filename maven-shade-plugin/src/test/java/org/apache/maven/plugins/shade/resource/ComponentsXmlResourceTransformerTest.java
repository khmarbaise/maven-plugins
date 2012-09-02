package org.apache.maven.plugins.shade.resource;

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

import junit.framework.TestCase;

import org.apache.maven.plugins.shade.relocation.Relocator;
import org.codehaus.plexus.util.IOUtil;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.XMLAssert;
import org.custommonkey.xmlunit.XMLUnit;

import java.util.Collections;

/**
 * Test for {@link ComponentsXmlResourceTransformer}.
 *
 * @author Brett Porter
 * @version $Id$
 */
public class ComponentsXmlResourceTransformerTest
    extends TestCase
{
    private ComponentsXmlResourceTransformer transformer;

    public void setUp()
    {
        this.transformer = new ComponentsXmlResourceTransformer();
    }

    public void testConfigurationMerging()
        throws Exception
    {

        XMLUnit.setNormalizeWhitespace( true );

        transformer.processResource( "components-1.xml", getClass().getResourceAsStream( "/components-1.xml" ),
                                     Collections.<Relocator> emptyList() );
        transformer.processResource( "components-1.xml", getClass().getResourceAsStream( "/components-2.xml" ),
                                     Collections.<Relocator> emptyList() );
        Diff diff = XMLUnit.compareXML(
            IOUtil.toString( getClass().getResourceAsStream( "/components-expected.xml" ), "UTF-8" ),
            IOUtil.toString( transformer.getTransformedResource(), "UTF-8" ) );
        //assertEquals( IOUtil.toString( getClass().getResourceAsStream( "/components-expected.xml" ), "UTF-8" ),
        //              IOUtil.toString( transformer.getTransformedResource(), "UTF-8" ).replaceAll("\r\n", "\n") );
        XMLAssert.assertXMLIdentical( diff, true );
    }
}