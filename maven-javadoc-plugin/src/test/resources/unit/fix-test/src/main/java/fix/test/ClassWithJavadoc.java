package fix.test;

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

/**
 * Some Javadoc.
 */
public class ClassWithJavadoc
{
    public static final String MY_STRING_CONSTANT = "value";

    public static final int MY_INT_CONSTANT = 1;

    public static final String EOL = System.getProperty( "line.separator" );

    private static final String MY_PRIVATE_CONSTANT = "";

    public ClassWithJavadoc()
    {
        // nop
    }

    public static void main( String[] args )
    {
        System.out.println( "Sample Application." );
    }

    /**
     * @param str
     */
    public String methodWithMissingParameters( String str, boolean b, int i )
    {
        return null;
    }

    /**
     * @param str
     * @throws UnsupportedOperationException if any
     */
    public String methodWithMissingParameters2( String str, boolean b, int i )
        throws UnsupportedOperationException
    {
        return null;
    }

    /**
     * @param str
     */
    public void methodWithWrongJavadocParameters( String aString )
    {
    }

    /**
     * @param aString
     *      a string
     * @param anotherString
     *      with
     *      multi
     *      line
     *      comments
     * @return a
     *      String
     * @throws UnsupportedOperationException
     *      if any
     */
    public String methodWithMultiLinesJavadoc( String aString, String anotherString )
        throws UnsupportedOperationException
    {
        return null;
    }
}
