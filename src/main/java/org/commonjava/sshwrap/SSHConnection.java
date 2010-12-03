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

package org.commonjava.sshwrap;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

public class SSHConnection
{

    private final SSHConfiguration config;

    private final JSch jsch;

    private transient Session session;

    private final String username;

    private final String host;

    private final int port;

    private UserInfo userInfo;

    public SSHConnection( final String username, final String host, final int port )
    {
        this.username = username;
        this.host = host;
        this.port = port;
        config = new DefaultSSHConfiguration();
        jsch = new JSch();
    }

    public SSHConnection( final String username, final String host )
    {
        this.username = username;
        this.host = host;
        port = 22;

        config = new DefaultSSHConfiguration();
        jsch = new JSch();
    }

    public SSHConnection withUserInfo( final UserInfo userInfo )
    {
        if ( isConnected() )
        {
            throw new IllegalStateException( "Cannot set user-info on connected session!" );
        }

        this.userInfo = userInfo;
        if ( session != null )
        {

            session.setUserInfo( userInfo );
        }

        return this;
    }

    public boolean isConnected()
    {
        return session != null && session.isConnected();
    }

    private void checkConnected()
        throws SSHWrapException
    {
        if ( !isConnected() )
        {
            throw new SSHWrapException( "You must call connect() before attempting this operation!" );
        }
    }

    public SSHConnection connect()
        throws SSHWrapException
    {
        try
        {
            jsch.setKnownHosts( config.getKnownHosts() );
        }
        catch ( final JSchException e )
        {
            throw new SSHWrapException( "Failed to initialize known hosts: %s", e, e.getMessage() );
        }
        catch ( final IOException e )
        {
            throw new SSHWrapException( "Failed to initialize known hosts: %s", e, e.getMessage() );
        }

        for ( final File identityFile : config.getIdentities() )
        {
            try
            {
                jsch.addIdentity( identityFile.getAbsolutePath() );
            }
            catch ( final JSchException e )
            {
                throw new SSHWrapException( "Failed to load key: %s.\nReason: %s", e, identityFile, e.getMessage() );
            }
        }

        try
        {
            session = jsch.getSession( username, host, port );
            if ( userInfo != null )
            {
                session.setUserInfo( userInfo );
            }

            session.connect();
        }
        catch ( final JSchException e )
        {
            throw new SSHWrapException( "Failed to initialize/connect SSH session for %s@%s:%s\nReason: %s", e,
                                        username, host, port, e.getMessage() );
        }

        return this;
    }

    public Channel openChannel( final ChannelType type )
        throws SSHWrapException
    {
        checkConnected();

        try
        {
            final Channel channel = session.openChannel( type.channelName() );

            return channel;
        }
        catch ( final JSchException e )
        {
            throw new SSHWrapException( "Failed to open channel: %s\nReason: %s", e, type, e.getMessage() );
        }
    }

    public int execute( final String command, final OutputStream cmdOutput )
        throws IOException, SSHWrapException
    {
        ChannelExec channel = null;
        try
        {
            channel = (ChannelExec) openChannel( ChannelType.exec );
            ( channel ).setCommand( command );

            final InputStream in = channel.getInputStream();

            try
            {
                channel.connect();
            }
            catch ( final JSchException e )
            {
                throw new SSHWrapException( "Failed to connect channel: %s", e, e.getMessage() );
            }

            final byte[] tmp = new byte[1024];
            while ( true )
            {
                while ( in.available() > 0 )
                {
                    final int i = in.read( tmp );
                    if ( i < 0 )
                    {
                        break;
                    }

                    cmdOutput.write( tmp, 0, i );
                }

                if ( channel.isClosed() )
                {
                    return channel.getExitStatus();
                }

                try
                {
                    Thread.sleep( 500 );
                }
                catch ( final InterruptedException ee )
                {
                    Thread.currentThread().interrupt();
                    break;
                }
            }
        }
        finally
        {
            if ( channel != null )
            {
                channel.disconnect();
            }
        }

        return Integer.MIN_VALUE;
    }

    public SSHConnection disconnect()
    {
        session.disconnect();
        session = null;

        return this;
    }

}
