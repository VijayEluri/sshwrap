/*
 * Copyright (c) 2010 Red Hat, Inc.
 * 
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published
 * by the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 * 
 * This program is distributed in the hope that it will be useful, but
 * WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see 
 * <http://www.gnu.org/licenses>.
 */

package org.commonjava.sshwrap.config;

import java.io.BufferedReader;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

/**
 * Forked from git://egit.eclipse.org/jgit.git@94207f0a43a44261b8170d3cdba3028059775d9d Simple configuration parser for
 * the OpenSSH ~/.ssh/config file.
 * <p>
 * Since JSch does not (currently) have the ability to parse an OpenSSH configuration file this is a simple parser to
 * read that file and make the critical options available to {@link SshSessionFactory}.
 */
public class DefaultSSHConfiguration
    implements SSHConfiguration
{
    /** IANA assigned port number for SSH. */
    static final int SSH_PORT = 22;

    private final Set<File> privateKeys;

    private final File configFile;

    private final File knownHosts;

    /** Cached entries read out of the configuration file. */
    private Map<String, Host> hosts;

    private byte[] knownHostsBuffer;

    /**
     * Obtain the user's configuration data.
     * <p>
     * The configuration file is always returned to the caller, even if no file exists in the user's home directory at
     * the time the call was made. Lookup requests are cached and are automatically updated if the user modifies the
     * configuration file since the last time it was cached.
     * </p>
     * <p>
     * Uses ${user.home}/.ssh/config as the configuration file.
     * </p>
     */
    public DefaultSSHConfiguration()
    {
        this( new File( userHome(), ".ssh" ).getAbsoluteFile() );
    }

    /**
     * Obtain the user's configuration data.
     * <p>
     * The configuration file is always returned to the caller, even if no file exists in the user's home directory at
     * the time the call was made. Lookup requests are cached and are automatically updated if the user modifies the
     * configuration file since the last time it was cached.
     * </p>
     * 
     * @param sshDir The base directory where all SSH configurations are housed.
     */
    public DefaultSSHConfiguration( final File sshDir )
    {
        configFile = new File( sshDir, "config" );
        knownHosts = new File( sshDir, "known_hosts" );

        hosts = parseHosts();
        privateKeys = initPrivateKeys( sshDir );
    }

    /**
     * Obtain the user's configuration data.
     * <p>
     * The configuration file is always returned to the caller, even if no file exists in the user's home directory at
     * the time the call was made. Lookup requests are cached and are automatically updated if the user modifies the
     * configuration file since the last time it was cached.
     * </p>
     * 
     * @param sshDir The base directory where all SSH configurations are housed.
     */
    public DefaultSSHConfiguration( final File config, final File knownHosts, final File... identities )
    {
        this.configFile = config;
        this.knownHosts = knownHosts;
        this.privateKeys = new HashSet<File>( Arrays.asList( identities ) );

        hosts = parseHosts();
    }

    @Override
    public Set<File> getIdentities()
    {
        return privateKeys;
    }

    @Override
    public synchronized InputStream getKnownHosts()
        throws IOException
    {
        if ( knownHostsBuffer == null )
        {
            if ( knownHosts != null && knownHosts.exists() && knownHosts.canRead() )
            {
                final ByteArrayOutputStream baos = new ByteArrayOutputStream();
                FileInputStream fis = null;
                try
                {
                    fis = new FileInputStream( knownHosts );
                    IOUtils.copy( fis, baos );
                }
                finally
                {
                    IOUtils.closeQuietly( fis );
                }

                knownHostsBuffer = baos.toByteArray();
            }
            else
            {
                knownHostsBuffer = new byte[0];
            }
        }

        return new ByteArrayInputStream( knownHostsBuffer );
    }

    /**
     * {@inheritDoc}
     * 
     * @see org.commonjava.sshwrap.config.SSHConfiguration#lookup(java.lang.String)
     */
    @Override
    public Host lookup( final String hostName )
    {
        boolean isNew = false;
        Host h = hosts.get( hostName );
        if ( h == null )
        {
            isNew = true;
            h = new Host();
        }

        if ( h.isPatternsApplied() )
        {
            return h;
        }

        if ( h.getHostName() == null )
        {
            h.setHostName( hostName );
        }

        if ( h.getUser() == null )
        {
            h.setUser( userName() );
        }

        if ( h.getPort() < 1 )
        {
            h.setPort( SSH_PORT );
        }

        h.setPatternsApplied( true );

        if ( isNew )
        {
            hosts.put( hostName, h );
        }

        return h;
    }

    private synchronized Map<String, Host> parseHosts()
    {
        if ( configFile.exists() && configFile.canRead() )
        {
            FileInputStream in = null;
            try
            {
                in = new FileInputStream( configFile );
                hosts = parse( in );
            }
            catch ( final IOException err )
            {
                hosts = Collections.emptyMap();
            }
            finally
            {
                IOUtils.closeQuietly( in );
            }
        }

        return hosts;
    }

    private Map<String, Host> parse( final InputStream in )
        throws IOException
    {
        final Map<String, Host> m = new LinkedHashMap<String, Host>();
        final BufferedReader br = new BufferedReader( new InputStreamReader( in ) );
        final List<Host> current = new ArrayList<Host>( 4 );
        String line;

        while ( ( line = br.readLine() ) != null )
        {
            line = line.trim();
            if ( line.length() == 0 || line.startsWith( "#" ) )
            {
                continue;
            }

            final String[] parts = line.split( "[ \t]*[= \t]", 2 );
            final String keyword = parts[0].trim();
            final String argValue = parts[1].trim();

            if ( StringUtils.equalsIgnoreCase( "Host", keyword ) )
            {
                current.clear();
                for ( final String pattern : argValue.split( "[ \t]" ) )
                {
                    final String name = dequote( pattern );
                    Host c = m.get( name );
                    if ( c == null )
                    {
                        c = new Host();
                        m.put( name, c );
                    }
                    current.add( c );
                }
                continue;
            }

            if ( current.isEmpty() )
            {
                // We received an option outside of a Host block. We
                // don't know who this should match against, so skip.
                //
                continue;
            }

            if ( StringUtils.equalsIgnoreCase( "HostName", keyword ) )
            {
                for ( final Host c : current )
                {
                    if ( c.getHostName() == null )
                    {
                        c.setHostName( dequote( argValue ) );
                    }
                }
            }
            else if ( StringUtils.equalsIgnoreCase( "User", keyword ) )
            {
                for ( final Host c : current )
                {
                    if ( c.getUser() == null )
                    {
                        c.setUser( dequote( argValue ) );
                    }
                }
            }
            else if ( StringUtils.equalsIgnoreCase( "Port", keyword ) )
            {
                try
                {
                    final int port = Integer.parseInt( dequote( argValue ) );
                    for ( final Host c : current )
                    {
                        if ( c.getPort() < 1 )
                        {
                            c.setPort( port );
                        }
                    }
                }
                catch ( final NumberFormatException nfe )
                {
                    // Bad port number. Don't set it.
                }
            }
            else if ( StringUtils.equalsIgnoreCase( "IdentityFile", keyword ) )
            {
                for ( final Host c : current )
                {
                    if ( c.getIdentityFile() == null )
                    {
                        c.setIdentityFile( toFile( dequote( argValue ) ) );
                    }
                }
            }
            else if ( StringUtils.equalsIgnoreCase( "PreferredAuthentications", keyword ) )
            {
                for ( final Host c : current )
                {
                    if ( c.getPreferredAuthentications() == null )
                    {
                        c.setPreferredAuthentications( nows( dequote( argValue ) ) );
                    }
                }
            }
            else if ( StringUtils.equalsIgnoreCase( "BatchMode", keyword ) )
            {
                for ( final Host c : current )
                {
                    if ( c.getBatchMode() == null )
                    {
                        c.setBatchMode( yesno( dequote( argValue ) ) );
                    }
                }
            }
            else if ( StringUtils.equalsIgnoreCase( "StrictHostKeyChecking", keyword ) )
            {
                final String value = dequote( argValue );
                for ( final Host c : current )
                {
                    if ( c.getStrictHostKeyChecking() == null )
                    {
                        c.setStrictHostKeyChecking( value );
                    }
                }
            }
            else if ( StringUtils.equalsIgnoreCase( "LocalForward", keyword ) )
            {
                final String[] argParts = argValue.split( ":" );
                LocalForward lf = null;
                if ( argParts.length > 3 )
                {
                    lf =
                        new LocalForward( argParts[0], Integer.parseInt( argParts[1] ), argParts[2],
                                          Integer.parseInt( argParts[3] ) );
                }
                else if ( argParts.length > 2 )
                {
                    lf =
                        new LocalForward( Integer.parseInt( argParts[0] ), argParts[1], Integer.parseInt( argParts[2] ) );
                }

                if ( lf != null )
                {
                    for ( final Host host : current )
                    {
                        host.addLocalForward( lf );
                    }
                }
            }
            else if ( StringUtils.equalsIgnoreCase( "RemoteForward", keyword ) )
            {
                final String[] argParts = argValue.split( ":" );
                RemoteForward rf = null;
                if ( argParts.length > 3 )
                {
                    rf =
                        new RemoteForward( argParts[0], Integer.parseInt( argParts[1] ), argParts[2],
                                           Integer.parseInt( argParts[3] ) );
                }
                else if ( argParts.length > 2 )
                {
                    rf =
                        new RemoteForward( Integer.parseInt( argParts[0] ), argParts[1], Integer.parseInt( argParts[2] ) );
                }

                if ( rf != null )
                {
                    for ( final Host host : current )
                    {
                        host.addRemoteForward( rf );
                    }
                }
            }
        }

        return m;
    }

    private static String dequote( final String value )
    {
        if ( value.startsWith( "\"" ) && value.endsWith( "\"" ) )
        {
            return value.substring( 1, value.length() - 1 );
        }
        return value;
    }

    private static String nows( final String value )
    {
        final StringBuilder b = new StringBuilder();
        for ( int i = 0; i < value.length(); i++ )
        {
            if ( !Character.isSpaceChar( value.charAt( i ) ) )
            {
                b.append( value.charAt( i ) );
            }
        }
        return b.toString();
    }

    private static Boolean yesno( final String value )
    {
        if ( StringUtils.equalsIgnoreCase( "yes", value ) )
        {
            return Boolean.TRUE;
        }
        return Boolean.FALSE;
    }

    private File toFile( final String path )
    {
        if ( path.startsWith( "~/" ) )
        {
            return new File( userHome(), path.substring( 2 ) );
        }
        final File ret = new File( path );
        if ( ret.isAbsolute() )
        {
            return ret;
        }
        return new File( userHome(), path );
    }

    private Set<File> initPrivateKeys( final File sshDir )
    {
        final Set<File> privateKeys = new HashSet<File>();
        privateKeys.add( new File( sshDir, "identity" ) );
        privateKeys.add( new File( sshDir, "id_rsa" ) );
        privateKeys.add( new File( sshDir, "id_dsa" ) );

        validatePrivateKeys();

        return privateKeys;
    }

    private void validatePrivateKeys()
    {
        for ( final Iterator<File> it = privateKeys.iterator(); it.hasNext(); )
        {
            final File file = it.next();
            if ( !file.canRead() )
            {
                it.remove();
            }
        }
    }

    static String userName()
    {
        return AccessController.doPrivileged( new PrivilegedAction<String>()
        {
            @Override
            public String run()
            {
                return System.getProperty( "user.name" );
            }
        } );
    }

    static String userHome()
    {
        return AccessController.doPrivileged( new PrivilegedAction<String>()
        {
            @Override
            public String run()
            {
                return System.getProperty( "user.home" );
            }
        } );
    }
}
