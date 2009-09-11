package org.apache.maven.plugin.resources.remote.it;

import org.apache.maven.it.VerificationException;
import org.apache.maven.it.Verifier;
import org.apache.maven.plugin.resources.remote.it.support.TestUtils;
import org.codehaus.plexus.util.FileUtils;

import java.io.File;
import java.io.IOException;
import java.net.URISyntaxException;

public class IT_GenerateFromOverride
    extends AbstractIT
{
    
    public void test()
        throws IOException, URISyntaxException, VerificationException
    {
        File dir = TestUtils.getTestDir( "generate-from-override" );
        Verifier verifier = new Verifier( dir.getAbsolutePath() );
        
        verifier.executeGoal( "generate-resources" );
        verifier.verifyErrorFreeLog();
        verifier.resetStreams();
        
        File output = new File( dir, "target/maven-shared-archive-resources/DEPENDENCIES" );
        String content = FileUtils.fileRead( output );
        
        assertTrue( content.indexOf( "Override:" ) > -1 );
    }

}
