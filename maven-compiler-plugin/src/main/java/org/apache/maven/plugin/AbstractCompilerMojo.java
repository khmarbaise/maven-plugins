package org.apache.maven.plugin;

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

import org.apache.maven.execution.MavenSession;
import org.apache.maven.plugins.annotations.Component;
import org.apache.maven.plugins.annotations.Parameter;
import org.apache.maven.shared.incremental.IncrementalBuildHelper;
import org.apache.maven.shared.utils.ReaderFactory;
import org.apache.maven.shared.utils.StringUtils;
import org.apache.maven.toolchain.Toolchain;
import org.apache.maven.toolchain.ToolchainManager;
import org.codehaus.plexus.compiler.Compiler;
import org.codehaus.plexus.compiler.CompilerConfiguration;
import org.codehaus.plexus.compiler.CompilerError;
import org.codehaus.plexus.compiler.CompilerException;
import org.codehaus.plexus.compiler.CompilerMessage;
import org.codehaus.plexus.compiler.CompilerNotImplementedException;
import org.codehaus.plexus.compiler.CompilerOutputStyle;
import org.codehaus.plexus.compiler.CompilerResult;
import org.codehaus.plexus.compiler.manager.CompilerManager;
import org.codehaus.plexus.compiler.manager.NoSuchCompilerException;
import org.codehaus.plexus.compiler.util.scan.InclusionScanException;
import org.codehaus.plexus.compiler.util.scan.SourceInclusionScanner;
import org.codehaus.plexus.compiler.util.scan.mapping.SingleTargetSourceMapping;
import org.codehaus.plexus.compiler.util.scan.mapping.SourceMapping;
import org.codehaus.plexus.compiler.util.scan.mapping.SuffixMapping;

import java.io.File;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Date;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * TODO: At least one step could be optimized, currently the plugin will do two
 * scans of all the source code if the compiler has to have the entire set of
 * sources. This is currently the case for at least the C# compiler and most
 * likely all the other .NET compilers too.
 *
 * @author others
 * @author <a href="mailto:trygvis@inamo.no">Trygve Laugst&oslash;l</a>
 * @version $Id$
 * @since 2.0
 */
public abstract class AbstractCompilerMojo
    extends AbstractMojo
{
    // ----------------------------------------------------------------------
    // Configurables
    // ----------------------------------------------------------------------

    /**
     * Indicates whether the build will continue even if there are compilation errors.
     *
     * @since 2.0.2
     */
    @Parameter ( property = "maven.compiler.failOnError", defaultValue = "true" )
    private boolean failOnError = true;

    /**
     * Set to <code>true</code> to include debugging information in the compiled class files.
     */
    @Parameter ( property = "maven.compiler.debug", defaultValue = "true" )
    private boolean debug = true;

    /**
     * Set to <code>true</code> to show messages about what the compiler is doing.
     */
    @Parameter ( property = "maven.compiler.verbose", defaultValue = "false" )
    private boolean verbose;

    /**
     * Sets whether to show source locations where deprecated APIs are used.
     */
    @Parameter ( property = "maven.compiler.showDeprecation", defaultValue = "false" )
    private boolean showDeprecation;

    /**
     * Set to <code>true</code> to optimize the compiled code using the compiler's optimization methods.
     */
    @Parameter ( property = "maven.compiler.optimize", defaultValue = "false" )
    private boolean optimize;

    /**
     * Set to <code>true</code> to show compilation warnings.
     */
    @Parameter ( property = "maven.compiler.showWarnings", defaultValue = "false" )
    private boolean showWarnings;

    /**
     * The -source argument for the Java compiler.
     */
    @Parameter ( property = "maven.compiler.source", defaultValue = "1.5" )
    protected String source;

    /**
     * The -target argument for the Java compiler.
     */
    @Parameter ( property = "maven.compiler.target", defaultValue = "1.5" )
    protected String target;

    /**
     * The -encoding argument for the Java compiler.
     *
     * @since 2.1
     */
    @Parameter ( property = "encoding", defaultValue = "${project.build.sourceEncoding}" )
    private String encoding;

    /**
     * Sets the granularity in milliseconds of the last modification
     * date for testing whether a source needs recompilation.
     */
    @Parameter ( property = "lastModGranularityMs", defaultValue = "0" )
    private int staleMillis;

    /**
     * The compiler id of the compiler to use. See this
     * <a href="non-javac-compilers.html">guide</a> for more information.
     */
    @Parameter ( property = "maven.compiler.compilerId", defaultValue = "javac" )
    private String compilerId;

    /**
     * Version of the compiler to use, ex. "1.3", "1.5", if {@link #fork} is set to <code>true</code>.
     */
    @Parameter ( property = "maven.compiler.compilerVersion" )
    private String compilerVersion;

    /**
     * Allows running the compiler in a separate process.
     * If <code>false</code> it uses the built in compiler, while if <code>true</code> it will use an executable.
     */
    @Parameter ( property = "maven.compiler.fork", defaultValue = "false" )
    private boolean fork;

    /**
     * Initial size, in megabytes, of the memory allocation pool, ex. "64", "64m"
     * if {@link #fork} is set to <code>true</code>.
     *
     * @since 2.0.1
     */
    @Parameter ( property = "maven.compiler.meminitial" )
    private String meminitial;

    /**
     * Sets the maximum size, in megabytes, of the memory allocation pool, ex. "128", "128m"
     * if {@link #fork} is set to <code>true</code>.
     *
     * @since 2.0.1
     */
    @Parameter ( property = "maven.compiler.maxmem" )
    private String maxmem;

    /**
     * Sets the executable of the compiler to use when {@link #fork} is <code>true</code>.
     */
    @Parameter ( property = "maven.compiler.executable" )
    private String executable;

    /**
     * <p>
     * Sets whether annotation processing is performed or not. Only applies to JDK 1.6+
     * If not set, both compilation and annotation processing are performed at the same time.
     * </p>
     * <p>Allowed values are:</p>
     * <ul>
     * <li><code>none</code> - no annotation processing is performed.</li>
     * <li><code>only</code> - only annotation processing is done, no compilation.</li>
     * </ul>
     *
     * @since 2.2
     */
    @Parameter
    private String proc;

    /**
     * <p>
     * Names of annotation processors to run. Only applies to JDK 1.6+
     * If not set, the default annotation processors discovery process applies.
     * </p>
     *
     * @since 2.2
     */
    @Parameter
    private String[] annotationProcessors;

    /**
     * <p>
     * Sets the arguments to be passed to the compiler (prepending a dash) if {@link #fork} is set to <code>true</code>.
     * </p>
     * <p>
     * This is because the list of valid arguments passed to a Java compiler
     * varies based on the compiler version.
     * </p>
     * <p>
     * To pass <code>-Xmaxerrs 1000 -Xlint -Xlint:-path -Averbose=true</code> you should include the following:
     * </p>
     * <pre>
     * &lt;compilerArguments&gt;
     *   &lt;Xmaxerrs&gt;1000&lt;/Xmaxerrs&gt;
     *   &lt;Xlint/&gt;
     *   &lt;Xlint:-path/&gt;
     *   &lt;Averbose&gt;true&lt;/Averbose&gt;
     * &lt;/compilerArguments&gt;
     * </pre>
     *
     * @since 2.0.1
     */
    @Parameter
    protected Map<String, String> compilerArguments;

    /**
     * <p>
     * Sets the unformatted single argument string to be passed to the compiler if {@link #fork} is set to <code>true</code>.
     * To pass multiple arguments such as <code>-Xmaxerrs 1000</code> (which are actually two arguments) you have to use {@link #compilerArguments}.
     * </p>
     * <p>
     * This is because the list of valid arguments passed to a Java compiler
     * varies based on the compiler version.
     * </p>
     */
    @Parameter
    protected String compilerArgument;

    /**
     * Sets the name of the output file when compiling a set of
     * sources to a single file.
     * <p/>
     * expression="${project.build.finalName}"
     */
    @Parameter
    private String outputFileName;

    /**
     * Keyword list to be appended to the <code>-g</code> command-line switch. Legal values are none or a
     * comma-separated list of the following keywords: <code>lines</code>, <code>vars</code>, and <code>source</code>.
     * If debug level is not specified, by default, nothing will be appended to <code>-g</code>.
     * If debug is not turned on, this attribute will be ignored.
     *
     * @since 2.1
     */
    @Parameter ( property = "maven.compiler.debuglevel" )
    private String debuglevel;

    /**
     *
     */
    @Component
    private ToolchainManager toolchainManager;

    // ----------------------------------------------------------------------
    // Read-only parameters
    // ----------------------------------------------------------------------

    /**
     * The directory to run the compiler from if fork is true.
     */
    @Parameter ( defaultValue = "${basedir}", required = true, readonly = true )
    private File basedir;

    /**
     * The target directory of the compiler if fork is true.
     */
    @Parameter ( defaultValue = "${project.build.directory}", required = true, readonly = true )
    private File buildDirectory;

    /**
     * Plexus compiler manager.
     */
    @Component
    private CompilerManager compilerManager;

    /**
     * The current build session instance. This is used for toolchain manager API calls.
     */
    @Component
    private MavenSession session;

    /**
     * Strategy to re use javacc class created:
     * <ul>
     * <li><code>reuseCreated</code> (default): will reuse already created but in case of multi-threaded builds,
     * each thread will have its own instance</li>
     * <li><code>reuseSame</code>: the same Javacc class will be used for each compilation even for multi-threaded build</li>
     * <li><code>alwaysNew</code>: a new Javacc class will be created for each compilation</li>
     * </ul>
     * Note this parameter value depends on the os/jdk you are using, but the default value should work on most of env.
     *
     * @since 2.5
     */
    @Parameter ( defaultValue = "${reuseCreated}", property = "maven.compiler.compilerReuseStrategy" )
    private String compilerReuseStrategy = "reuseCreated";

    /**
     * @since 2.5
     */
    @Parameter ( defaultValue = "false", property = "maven.compiler.skipMultiThreadWarning" )
    private boolean skipMultiThreadWarning;

    /**
     * compiler can now use javax.tools if available in your current jdk, you can disable this feature
     * using -Dmaven.compiler.forceJavacCompilerUse=true or in the plugin configuration
     *
     * @since 2.6
     */
    @Parameter ( defaultValue = "false", property = "maven.compiler.forceJavacCompilerUse" )
    private boolean forceJavacCompilerUse;

    /**
     * @since 2.6 needed for storing the status for the incremental build support.
     */
    @Parameter ( property = "mojoExecution" )
    private MojoExecution mojoExecution;

    /**
     * We need this to determine the start timestamp of the build.
     *
     * @since 2.6
     */
    @Component
    protected MavenSession mavenSession;

    protected abstract SourceInclusionScanner getSourceInclusionScanner( int staleMillis );

    protected abstract SourceInclusionScanner getSourceInclusionScanner( String inputFileEnding );

    protected abstract List<String> getClasspathElements();

    protected abstract List<String> getCompileSourceRoots();

    protected abstract File getOutputDirectory();

    protected abstract String getSource();

    protected abstract String getTarget();

    protected abstract String getCompilerArgument();

    protected abstract Map<String, String> getCompilerArguments();

    protected abstract File getGeneratedSourcesDirectory();

    public void execute()
        throws MojoExecutionException, CompilationFailureException
    {
        // ----------------------------------------------------------------------
        // Look up the compiler. This is done before other code than can
        // cause the mojo to return before the lookup is done possibly resulting
        // in misconfigured POMs still building.
        // ----------------------------------------------------------------------

        Compiler compiler;

        getLog().debug( "Using compiler '" + compilerId + "'." );

        try
        {
            compiler = compilerManager.getCompiler( compilerId );
        }
        catch ( NoSuchCompilerException e )
        {
            throw new MojoExecutionException( "No such compiler '" + e.getCompilerId() + "'." );
        }

        //-----------toolchains start here ----------------------------------
        //use the compilerId as identifier for toolchains as well.
        Toolchain tc = getToolchain();
        if ( tc != null )
        {
            getLog().info( "Toolchain in compiler-plugin: " + tc );
            if ( executable != null )
            {
                getLog().warn( "Toolchains are ignored, 'executable' parameter is set to " + executable );
            }
            else
            {
                fork = true;
                //TODO somehow shaky dependency between compilerId and tool executable.
                executable = tc.findTool( compilerId );
            }
        }
        // ----------------------------------------------------------------------
        //
        // ----------------------------------------------------------------------

        List<String> compileSourceRoots = removeEmptyCompileSourceRoots( getCompileSourceRoots() );

        if ( compileSourceRoots.isEmpty() )
        {
            getLog().info( "No sources to compile" );

            return;
        }

        if ( getLog().isDebugEnabled() )
        {
            getLog().debug( "Source directories: " + compileSourceRoots.toString().replace( ',', '\n' ) );
            getLog().debug( "Classpath: " + getClasspathElements().toString().replace( ',', '\n' ) );
            getLog().debug( "Output directory: " + getOutputDirectory() );
        }

        // ----------------------------------------------------------------------
        // Create the compiler configuration
        // ----------------------------------------------------------------------

        CompilerConfiguration compilerConfiguration = new CompilerConfiguration();

        compilerConfiguration.setOutputLocation( getOutputDirectory().getAbsolutePath() );

        compilerConfiguration.setClasspathEntries( getClasspathElements() );

        compilerConfiguration.setSourceLocations( compileSourceRoots );

        compilerConfiguration.setOptimize( optimize );

        compilerConfiguration.setDebug( debug );

        if ( debug && StringUtils.isNotEmpty( debuglevel ) )
        {
            String[] split = StringUtils.split( debuglevel, "," );
            for ( int i = 0; i < split.length; i++ )
            {
                if ( !( split[i].equalsIgnoreCase( "none" ) || split[i].equalsIgnoreCase( "lines" )
                    || split[i].equalsIgnoreCase( "vars" ) || split[i].equalsIgnoreCase( "source" ) ) )
                {
                    throw new IllegalArgumentException( "The specified debug level: '" + split[i] + "' is unsupported. "
                                                            + "Legal values are 'none', 'lines', 'vars', and 'source'." );
                }
            }
            compilerConfiguration.setDebugLevel( debuglevel );
        }

        compilerConfiguration.setVerbose( verbose );

        compilerConfiguration.setShowWarnings( showWarnings );

        compilerConfiguration.setShowDeprecation( showDeprecation );

        compilerConfiguration.setSourceVersion( getSource() );

        compilerConfiguration.setTargetVersion( getTarget() );

        compilerConfiguration.setProc( proc );

        compilerConfiguration.setGeneratedSourcesDirectory( getGeneratedSourcesDirectory() );

        compilerConfiguration.setAnnotationProcessors( annotationProcessors );

        compilerConfiguration.setSourceEncoding( encoding );

        Map<String, String> effectiveCompilerArguments = getCompilerArguments();

        String effectiveCompilerArgument = getCompilerArgument();

        if ( ( effectiveCompilerArguments != null ) || ( effectiveCompilerArgument != null ) )
        {
            LinkedHashMap<String, String> cplrArgsCopy = new LinkedHashMap<String, String>();
            if ( effectiveCompilerArguments != null )
            {
                for ( Map.Entry<String, String> me : effectiveCompilerArguments.entrySet() )
                {
                    String key = me.getKey();
                    String value = me.getValue();
                    if ( !key.startsWith( "-" ) )
                    {
                        key = "-" + key;
                    }

                    if ( key.startsWith( "-A" ) && StringUtils.isNotEmpty( value ) )
                    {
                        cplrArgsCopy.put( key + "=" + value, null );
                    }
                    else
                    {
                        cplrArgsCopy.put( key, value );
                    }
                }
            }
            if ( !StringUtils.isEmpty( effectiveCompilerArgument ) )
            {
                cplrArgsCopy.put( effectiveCompilerArgument, null );
            }
            compilerConfiguration.setCustomCompilerArguments( cplrArgsCopy );
        }

        compilerConfiguration.setFork( fork );

        if ( fork )
        {
            if ( !StringUtils.isEmpty( meminitial ) )
            {
                String value = getMemoryValue( meminitial );

                if ( value != null )
                {
                    compilerConfiguration.setMeminitial( value );
                }
                else
                {
                    getLog().info( "Invalid value for meminitial '" + meminitial + "'. Ignoring this option." );
                }
            }

            if ( !StringUtils.isEmpty( maxmem ) )
            {
                String value = getMemoryValue( maxmem );

                if ( value != null )
                {
                    compilerConfiguration.setMaxmem( value );
                }
                else
                {
                    getLog().info( "Invalid value for maxmem '" + maxmem + "'. Ignoring this option." );
                }
            }
        }

        compilerConfiguration.setExecutable( executable );

        compilerConfiguration.setWorkingDirectory( basedir );

        compilerConfiguration.setCompilerVersion( compilerVersion );

        compilerConfiguration.setBuildDirectory( buildDirectory );

        compilerConfiguration.setOutputFileName( outputFileName );

        if ( CompilerConfiguration.CompilerReuseStrategy.AlwaysNew.getStrategy().equals( this.compilerReuseStrategy ) )
        {
            compilerConfiguration.setCompilerReuseStrategy( CompilerConfiguration.CompilerReuseStrategy.AlwaysNew );
        }
        else if ( CompilerConfiguration.CompilerReuseStrategy.ReuseSame.getStrategy().equals(
            this.compilerReuseStrategy ) )
        {
            if ( getRequestThreadCount() > 1 )
            {
                if ( !skipMultiThreadWarning )
                {
                    StringBuilder sb = new StringBuilder(
                        "You are in a multi-thread build and compilerReuseStrategy is set to reuseSame. This can cause issues in some environments (os/jdk)! Consider using reuseCreated strategy." );
                    sb.append( System.getProperty( "line.separator" ) );
                    sb.append(
                        "If your env is fine with reuseSame, you can skip this warning with the configuration field skipMultiThreadWarning or -Dmaven.compiler.skipMultiThreadWarning=true" );
                    getLog().warn( sb.toString() );
                }
            }
            compilerConfiguration.setCompilerReuseStrategy( CompilerConfiguration.CompilerReuseStrategy.ReuseSame );
        }
        else
        {

            compilerConfiguration.setCompilerReuseStrategy( CompilerConfiguration.CompilerReuseStrategy.ReuseCreated );
        }

        getLog().debug( "CompilerReuseStrategy: " + compilerConfiguration.getCompilerReuseStrategy().getStrategy() );

        compilerConfiguration.setForceJavacCompilerUse( forceJavacCompilerUse );

        boolean canUpdateTarget;

        IncrementalBuildHelper incrementalBuildHelper = new IncrementalBuildHelper( mojoExecution, mavenSession );

        try
        {
            canUpdateTarget = compiler.canUpdateTarget( compilerConfiguration );
            Set<File> sources = getCompileSources( compiler, compilerConfiguration );

            if ( ( compiler.getCompilerOutputStyle().equals( CompilerOutputStyle.ONE_OUTPUT_FILE_FOR_ALL_INPUT_FILES )
                && !canUpdateTarget ) || isDependencyChanged() || isSourceChanged( compilerConfiguration, compiler )
                || incrementalBuildHelper.inputFileTreeChanged( sources ) )
            {
                getLog().info( "Changes detected - recompiling the module!" );

                compilerConfiguration.setSourceFiles( sources );
            }
            else
            {
                getLog().info( "Nothing to compile - all classes are up to date" );

                return;
            }
        }
        catch ( CompilerException e )
        {
            throw new MojoExecutionException( "Error while computing stale sources.", e );
        }

        // ----------------------------------------------------------------------
        // Dump configuration
        // ----------------------------------------------------------------------

        if ( getLog().isDebugEnabled() )
        {
            getLog().debug( "Classpath:" );

            for ( String s : getClasspathElements() )
            {
                getLog().debug( " " + s );
            }

            getLog().debug( "Source roots:" );

            for ( String root : getCompileSourceRoots() )
            {
                getLog().debug( " " + root );
            }

            try
            {
                if ( fork )
                {
                    if ( compilerConfiguration.getExecutable() != null )
                    {
                        getLog().debug( "Excutable: " );
                        getLog().debug( " " + compilerConfiguration.getExecutable() );
                    }
                }

                String[] cl = compiler.createCommandLine( compilerConfiguration );
                if ( cl != null && cl.length > 0 )
                {
                    StringBuilder sb = new StringBuilder();
                    sb.append( cl[0] );
                    for ( int i = 1; i < cl.length; i++ )
                    {
                        sb.append( " " );
                        sb.append( cl[i] );
                    }
                    getLog().debug( "Command line options:" );
                    getLog().debug( sb );
                }
            }
            catch ( CompilerException ce )
            {
                getLog().debug( ce );
            }
        }

        // ----------------------------------------------------------------------
        // Compile!
        // ----------------------------------------------------------------------

        if ( StringUtils.isEmpty( compilerConfiguration.getSourceEncoding() ) )
        {
            getLog().warn( "File encoding has not been set, using platform encoding " + ReaderFactory.FILE_ENCODING
                               + ", i.e. build is platform dependent!" );
        }

        CompilerResult compilerResult;

        incrementalBuildHelper.beforeRebuildExecution( getOutputDirectory() );

        try
        {
            try
            {
                compilerResult = compiler.performCompile( compilerConfiguration );
            }
            catch ( CompilerNotImplementedException cnie )
            {
                List<CompilerError> messages = compiler.compile( compilerConfiguration );
                compilerResult = new CompilerResult().compilerMessages( makeCompilerMessages( messages ) );
            }
        }
        catch ( Exception e )
        {
            // TODO: don't catch Exception
            throw new MojoExecutionException( "Fatal error compiling", e );
        }

        // now scan the same directory again and create a diff
        incrementalBuildHelper.afterRebuildExecution();

        List<CompilerMessage> warnings = new ArrayList<CompilerMessage>();
        List<CompilerMessage> errors = new ArrayList<CompilerMessage>();


            for ( CompilerMessage message : compilerResult.getCompilerMessages() )
            {
                if ( message.isError() || message.getKind() == CompilerMessage.Kind.ERROR )
                {
                    errors.add( message );
                }
                else
                {
                    warnings.add( message );
                }
            }


        if ( failOnError && !errors.isEmpty() )
        {
            if ( !warnings.isEmpty() )
            {
                getLog().info( "-------------------------------------------------------------" );
                getLog().warn( "COMPILATION WARNING : " );
                getLog().info( "-------------------------------------------------------------" );
                for ( CompilerMessage warning : warnings )
                {
                    getLog().warn( warning.toString() );
                }
                getLog().info( warnings.size() + ( ( warnings.size() > 1 ) ? " warnings " : " warning" ) );
                getLog().info( "-------------------------------------------------------------" );
            }

            getLog().info( "-------------------------------------------------------------" );
            getLog().error( "COMPILATION ERROR : " );
            getLog().info( "-------------------------------------------------------------" );

            for ( CompilerMessage error : errors )
            {
                getLog().error( error.toString() );
            }
            getLog().info( errors.size() + ( ( errors.size() > 1 ) ? " errors " : " error" ) );
            getLog().info( "-------------------------------------------------------------" );

            throw new CompilationFailureException( errors );
        }
        else
        {
            for ( CompilerMessage message : compilerResult.getCompilerMessages() )
            {
                getLog().warn( message.toString() );
            }
        }
    }

    protected List<CompilerMessage> makeCompilerMessages( List<CompilerError> compilerErrors )
    {
        if ( compilerErrors == null )
        {
            return Collections.emptyList();
        }
        List<CompilerMessage> messages = new ArrayList<CompilerMessage>( compilerErrors.size() );
        for ( CompilerError compilerError : compilerErrors )
        {
            messages.add(
                new CompilerMessage( compilerError.getFile(), compilerError.getKind(), compilerError.getStartLine(),
                                     compilerError.getStartColumn(), compilerError.getEndLine(),
                                     compilerError.getEndColumn(), compilerError.getMessage() ) );
        }

        return messages;
    }

    /**
     * @return all source files for the compiler
     */
    private Set<File> getCompileSources( Compiler compiler, CompilerConfiguration compilerConfiguration )
        throws MojoExecutionException, CompilerException
    {
        String inputFileEnding = compiler.getInputFileEnding( compilerConfiguration );
        SourceInclusionScanner scanner = getSourceInclusionScanner( inputFileEnding );

        SourceMapping mapping = getSourceMapping( compilerConfiguration, compiler );

        scanner.addSourceMapping( mapping );

        Set<File> compileSources = new HashSet<File>();

        for ( String sourceRoot : getCompileSourceRoots() )
        {
            File rootFile = new File( sourceRoot );

            if ( !rootFile.isDirectory() )
            {
                continue;
            }

            try
            {
                compileSources.addAll( scanner.getIncludedSources( rootFile, null ) );
            }
            catch ( InclusionScanException e )
            {
                throw new MojoExecutionException(
                    "Error scanning source root: \'" + sourceRoot + "\' for stale files to recompile.", e );
            }
        }

        return compileSources;
    }

    /**
     * @param compilerConfiguration
     * @param compiler
     * @return <code>true</code> if at least a single source file is newer than it's class file
     */
    private boolean isSourceChanged( CompilerConfiguration compilerConfiguration, Compiler compiler )
        throws CompilerException, MojoExecutionException
    {
        Set<File> staleSources =
            computeStaleSources( compilerConfiguration, compiler, getSourceInclusionScanner( staleMillis ) );

        return staleSources != null && staleSources.size() > 0;
    }


    /**
     * try to get thread count if a Maven 3 build, using reflection as the plugin must not be maven3 api dependant
     *
     * @return number of thread for this build or 1 if not multi-thread build
     */
    protected int getRequestThreadCount()
    {
        try
        {
            Method getRequestMethod = session.getClass().getMethod( "getRequest" );
            Object mavenExecutionRequest = getRequestMethod.invoke( this.session );
            Method getThreadCountMethod = mavenExecutionRequest.getClass().getMethod( "getThreadCount" );
            String threadCount = (String) getThreadCountMethod.invoke( mavenExecutionRequest );
            return Integer.valueOf( threadCount );
        }
        catch ( Exception e )
        {
            getLog().debug( "unable to get threadCount for the current build: " + e.getMessage() );
        }
        return 1;
    }

    protected Date getBuildStartTime()
    {
        try
        {
            Method getRequestMethod = session.getClass().getMethod( "getRequest" );
            Object mavenExecutionRequest = getRequestMethod.invoke( session );
            Method getStartTimeMethod = mavenExecutionRequest.getClass().getMethod( "getStartTime" );
            Date buildStartTime = (Date) getStartTimeMethod.invoke( mavenExecutionRequest );
            return buildStartTime;
        }
        catch ( Exception e )
        {
            getLog().debug( "unable to get start time for the current build: " + e.getMessage() );
        }

        return new Date();
    }


    private String getMemoryValue( String setting )
    {
        String value = null;

        // Allow '128' or '128m'
        if ( isDigits( setting ) )
        {
            value = setting + "m";
        }
        else
        {
            if ( ( isDigits( setting.substring( 0, setting.length() - 1 ) ) ) && ( setting.toLowerCase().endsWith(
                "m" ) ) )
            {
                value = setting;
            }
        }
        return value;
    }

    //TODO remove the part with ToolchainManager lookup once we depend on
    //3.0.9 (have it as prerequisite). Define as regular component field then.
    private Toolchain getToolchain()
    {
        Toolchain tc = null;
        if ( toolchainManager != null )
        {
            tc = toolchainManager.getToolchainFromBuildContext( "jdk", session );
        }
        return tc;
    }

    private boolean isDigits( String string )
    {
        for ( int i = 0; i < string.length(); i++ )
        {
            if ( !Character.isDigit( string.charAt( i ) ) )
            {
                return false;
            }
        }
        return true;
    }

    private Set<File> computeStaleSources( CompilerConfiguration compilerConfiguration, Compiler compiler,
                                           SourceInclusionScanner scanner )
        throws MojoExecutionException, CompilerException
    {
        SourceMapping mapping = getSourceMapping( compilerConfiguration, compiler );

        File outputDirectory;
        CompilerOutputStyle outputStyle = compiler.getCompilerOutputStyle();
        if ( outputStyle == CompilerOutputStyle.ONE_OUTPUT_FILE_FOR_ALL_INPUT_FILES )
        {
            outputDirectory = buildDirectory;
        }
        else
        {
            outputDirectory = getOutputDirectory();
        }

        scanner.addSourceMapping( mapping );

        Set<File> staleSources = new HashSet<File>();

        for ( String sourceRoot : getCompileSourceRoots() )
        {
            File rootFile = new File( sourceRoot );

            if ( !rootFile.isDirectory() )
            {
                continue;
            }

            try
            {
                staleSources.addAll( scanner.getIncludedSources( rootFile, outputDirectory ) );
            }
            catch ( InclusionScanException e )
            {
                throw new MojoExecutionException(
                    "Error scanning source root: \'" + sourceRoot + "\' for stale files to recompile.", e );
            }
        }

        return staleSources;
    }

    private SourceMapping getSourceMapping( CompilerConfiguration compilerConfiguration, Compiler compiler )
        throws CompilerException, MojoExecutionException
    {
        CompilerOutputStyle outputStyle = compiler.getCompilerOutputStyle();

        SourceMapping mapping;
        if ( outputStyle == CompilerOutputStyle.ONE_OUTPUT_FILE_PER_INPUT_FILE )
        {
            mapping = new SuffixMapping( compiler.getInputFileEnding( compilerConfiguration ),
                                         compiler.getOutputFileEnding( compilerConfiguration ) );
        }
        else if ( outputStyle == CompilerOutputStyle.ONE_OUTPUT_FILE_FOR_ALL_INPUT_FILES )
        {
            mapping = new SingleTargetSourceMapping( compiler.getInputFileEnding( compilerConfiguration ),
                                                     compiler.getOutputFile( compilerConfiguration ) );

        }
        else
        {
            throw new MojoExecutionException( "Unknown compiler output style: '" + outputStyle + "'." );
        }
        return mapping;
    }

    /**
     * @todo also in ant plugin. This should be resolved at some point so that it does not need to
     * be calculated continuously - or should the plugins accept empty source roots as is?
     */
    private static List<String> removeEmptyCompileSourceRoots( List<String> compileSourceRootsList )
    {
        List<String> newCompileSourceRootsList = new ArrayList<String>();
        if ( compileSourceRootsList != null )
        {
            // copy as I may be modifying it
            for ( String srcDir : compileSourceRootsList )
            {
                if ( !newCompileSourceRootsList.contains( srcDir ) && new File( srcDir ).exists() )
                {
                    newCompileSourceRootsList.add( srcDir );
                }
            }
        }
        return newCompileSourceRootsList;
    }

    /**
     * We just compare the timestamps of all local dependency files (inter-module dependency classpath)
     * and the own generated classes
     * and if we got a file which is >= the buid-started timestamp, then we catched a file which got
     * changed during this build.
     *
     * @return <code>true</code> if at least one single dependency has changed.
     */
    protected boolean isDependencyChanged()
    {
        if ( mavenSession == null )
        {
            // we just cannot determine it, so don't do anything beside logging
            getLog().info( "Cannot determine build start date, skipping incremental build detection." );
            return false;
        }

        Date buildStartTime = getBuildStartTime();

        for ( String classPathElement : getClasspathElements() )
        {
            // ProjectArtifacts are artifacts which are available in the local project
            // that's the only ones we are interested in now.
            File artifactPath = new File( classPathElement );
            if ( artifactPath.isDirectory() )
            {
                if ( hasNewFile( artifactPath, buildStartTime ) )
                {
                    return true;
                }
            }
        }

        // obviously there was no new file detected.
        return false;
    }

    private boolean hasNewFile( File classPathEntry, Date buildStartTime )
    {
        if ( !classPathEntry.exists() )
        {
            return false;
        }

        if ( classPathEntry.isFile() )
        {
            return classPathEntry.lastModified() >= buildStartTime.getTime();
        }

        File[] children = classPathEntry.listFiles();

        for ( File child : children )
        {
            if ( hasNewFile( child, buildStartTime ) )
            {
                return true;
            }
        }

        return false;
    }
}
