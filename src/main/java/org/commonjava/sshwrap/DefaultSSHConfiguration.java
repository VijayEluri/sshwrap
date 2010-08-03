/*
 * Copyright (C) 2008-2009, Google Inc.
 * and other copyright owners as documented in the project's IP log.
 *
 * This program and the accompanying materials are made available
 * under the terms of the Eclipse Distribution License v1.0 which
 * accompanies this distribution, is reproduced below, and is
 * available at http://www.eclipse.org/org/documents/edl-v10.php
 *
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or
 * without modification, are permitted provided that the following
 * conditions are met:
 *
 * - Redistributions of source code must retain the above copyright
 *   notice, this list of conditions and the following disclaimer.
 *
 * - Redistributions in binary form must reproduce the above
 *   copyright notice, this list of conditions and the following
 *   disclaimer in the documentation and/or other materials provided
 *   with the distribution.
 *
 * - Neither the name of the Eclipse Foundation, Inc. nor the
 *   names of its contributors may be used to endorse or promote
 *   products derived from this software without specific prior
 *   written permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND
 * CONTRIBUTORS "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES,
 * INCLUDING, BUT NOT LIMITED TO, THE IMPLIED WARRANTIES
 * OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE
 * ARE DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT OWNER OR
 * CONTRIBUTORS BE LIABLE FOR ANY DIRECT, INDIRECT, INCIDENTAL,
 * SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES (INCLUDING, BUT
 * NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR SERVICES;
 * LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF
 * ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */

package org.commonjava.sshwrap;

import org.apache.commons.io.IOUtils;
import org.apache.commons.lang.StringUtils;

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
import java.util.Collections;
import java.util.HashSet;
import java.util.Iterator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Forked from git://egit.eclipse.org/jgit.git@94207f0a43a44261b8170d3cdba3028059775d9d
 * 
 * Simple configuration parser for the OpenSSH ~/.ssh/config file.
 * <p>
 * Since JSch does not (currently) have the ability to parse an OpenSSH
 * configuration file this is a simple parser to read that file and make the
 * critical options available to {@link SshSessionFactory}.
 */
public class DefaultSSHConfiguration
    implements SSHConfiguration
{
    /** IANA assigned port number for SSH. */
    static final int SSH_PORT = 22;

    private final File sshDir;

    private final Set<File> privateKeys;

    private final File configFile;

    private final File knownHosts;

    /** Cached entries read out of the configuration file. */
    private Map<String, Host> hosts;

    private byte[] knownHostsBuffer;

    /**
     * Obtain the user's configuration data.
     * <p>
     * The configuration file is always returned to the caller, even if no file
     * exists in the user's home directory at the time the call was made. Lookup
     * requests are cached and are automatically updated if the user modifies
     * the configuration file since the last time it was cached.
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
     * The configuration file is always returned to the caller, even if no file
     * exists in the user's home directory at the time the call was made. Lookup
     * requests are cached and are automatically updated if the user modifies
     * the configuration file since the last time it was cached.
     * </p>
     * 
     * @param sshDir The base directory where all SSH configurations are housed.
     */
    public DefaultSSHConfiguration( final File sshDir )
    {
        this.sshDir = sshDir;
        configFile = new File( sshDir, "config" );
        knownHosts = new File( sshDir, "known_hosts" );

        hosts = parseHosts();
        privateKeys = initPrivateKeys();
    }

    public Set<File> getIdentities()
    {
        return privateKeys;
    }

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
     * @see org.commonjava.sshwrap.SSHConfiguration#lookup(java.lang.String)
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

        if ( h.patternsApplied )
        {
            return h;
        }

        if ( h.hostName == null )
        {
            h.hostName = hostName;
        }

        if ( h.user == null )
        {
            h.user = userName();
        }

        if ( h.port == 0 )
        {
            h.port = SSH_PORT;
        }

        h.patternsApplied = true;

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
                    if ( c.hostName == null )
                    {
                        c.hostName = dequote( argValue );
                    }
                }
            }
            else if ( StringUtils.equalsIgnoreCase( "User", keyword ) )
            {
                for ( final Host c : current )
                {
                    if ( c.user == null )
                    {
                        c.user = dequote( argValue );
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
                        if ( c.port == 0 )
                        {
                            c.port = port;
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
                    if ( c.identityFile == null )
                    {
                        c.identityFile = toFile( dequote( argValue ) );
                    }
                }
            }
            else if ( StringUtils.equalsIgnoreCase( "PreferredAuthentications", keyword ) )
            {
                for ( final Host c : current )
                {
                    if ( c.preferredAuthentications == null )
                    {
                        c.preferredAuthentications = nows( dequote( argValue ) );
                    }
                }
            }
            else if ( StringUtils.equalsIgnoreCase( "BatchMode", keyword ) )
            {
                for ( final Host c : current )
                {
                    if ( c.batchMode == null )
                    {
                        c.batchMode = yesno( dequote( argValue ) );
                    }
                }
            }
            else if ( StringUtils.equalsIgnoreCase( "StrictHostKeyChecking", keyword ) )
            {
                final String value = dequote( argValue );
                for ( final Host c : current )
                {
                    if ( c.strictHostKeyChecking == null )
                    {
                        c.strictHostKeyChecking = value;
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

    private Set<File> initPrivateKeys()
    {
        final Set<File> privateKeys = new HashSet<File>();
        privateKeys.add( new File( sshDir, "identity" ) );
        privateKeys.add( new File( sshDir, "id_rsa" ) );
        privateKeys.add( new File( sshDir, "id_dsa" ) );

        for ( final Iterator<File> it = privateKeys.iterator(); it.hasNext(); )
        {
            final File file = it.next();
            if ( !file.canRead() )
            {
                it.remove();
            }
        }

        return privateKeys;
    }

    static String userName()
    {
        return AccessController.doPrivileged( new PrivilegedAction<String>()
        {
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
            public String run()
            {
                return System.getProperty( "user.home" );
            }
        } );
    }

    /**
     * Configuration of one "Host" block in the configuration file.
     * <p>
     * If returned from {@link OpenSshConfig#lookup(String)} some or all of the
     * properties may not be populated. The properties which are not populated
     * should be defaulted by the caller.
     * <p>
     * When returned from {@link OpenSshConfig#lookup(String)} any wildcard
     * entries which appear later in the configuration file will have been
     * already merged into this block.
     */
    public static class Host
    {
        boolean patternsApplied;

        String hostName;

        int port;

        File identityFile;

        String user;

        String preferredAuthentications;

        Boolean batchMode;

        String strictHostKeyChecking;

        void copyFrom( final Host src )
        {
            if ( hostName == null )
            {
                hostName = src.hostName;
            }
            if ( port == 0 )
            {
                port = src.port;
            }
            if ( identityFile == null )
            {
                identityFile = src.identityFile;
            }
            if ( user == null )
            {
                user = src.user;
            }
            if ( preferredAuthentications == null )
            {
                preferredAuthentications = src.preferredAuthentications;
            }
            if ( batchMode == null )
            {
                batchMode = src.batchMode;
            }
            if ( strictHostKeyChecking == null )
            {
                strictHostKeyChecking = src.strictHostKeyChecking;
            }
        }

        /**
         * @return the value StrictHostKeyChecking property, the valid values
         *         are "yes" (unknown hosts are not accepted), "no" (unknown
         *         hosts are always accepted), and "ask" (user should be asked
         *         before accepting the host)
         */
        public String getStrictHostKeyChecking()
        {
            return strictHostKeyChecking;
        }

        /**
         * @return the real IP address or host name to connect to; never null.
         */
        public String getHostName()
        {
            return hostName;
        }

        /**
         * @return the real port number to connect to; never 0.
         */
        public int getPort()
        {
            return port;
        }

        /**
         * @return path of the private key file to use for authentication; null
         *         if the caller should use default authentication strategies.
         */
        public File getIdentityFile()
        {
            return identityFile;
        }

        /**
         * @return the real user name to connect as; never null.
         */
        public String getUser()
        {
            return user;
        }

        /**
         * @return the preferred authentication methods, separated by commas if
         *         more than one authentication method is preferred.
         */
        public String getPreferredAuthentications()
        {
            return preferredAuthentications;
        }

        /**
         * @return true if batch (non-interactive) mode is preferred for this
         *         host connection.
         */
        public boolean isBatchMode()
        {
            return batchMode != null && batchMode.booleanValue();
        }
    }
}
