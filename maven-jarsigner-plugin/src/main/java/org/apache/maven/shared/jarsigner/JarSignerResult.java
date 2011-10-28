package org.apache.maven.shared.jarsigner;

import org.codehaus.plexus.util.cli.CommandLineException;
import org.codehaus.plexus.util.cli.Commandline;

/**
 * Describes the result of a JarSigner invocation.
 *
 * @author tchemit <chemit@codelutin.com>
 * @version $Id$
 * @since 1.0
 */
public interface JarSignerResult
{

    /**
     * Gets the command line used.
     *
     * @return The command line used
     */
    Commandline getCommandline();

    /**
     * Gets the exception that possibly occurred during the execution of the command line.
     *
     * @return The exception that prevented to invoke Jarsigner or <code>null</code> if the command line was successfully
     *         processed by the operating system.
     */
    CommandLineException getExecutionException();

    /**
     * Gets the exit code from the JarSigner invocation. A non-zero value indicates a build failure. <strong>Note:</strong>
     * This value is undefined if {@link #getExecutionException()} reports an exception.
     *
     * @return The exit code from the Jarsigner invocation.
     */
    int getExitCode();
}
