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

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.HashSet;
import java.util.Set;

import org.commonjava.sshwrap.config.DefaultSSHConfiguration;
import org.commonjava.sshwrap.config.Host;
import org.commonjava.sshwrap.config.LocalForward;
import org.commonjava.sshwrap.config.RemoteForward;
import org.commonjava.sshwrap.config.SSHConfiguration;
import org.commonjava.sshwrap.ui.Prompter;

import com.jcraft.jsch.Channel;
import com.jcraft.jsch.ChannelExec;
import com.jcraft.jsch.JSch;
import com.jcraft.jsch.JSchException;
import com.jcraft.jsch.Session;
import com.jcraft.jsch.UserInfo;

public class SSHConnection
{

    private final SSHConfiguration config;

    private final JSch jsch;

    private transient Session session;

    private final Host host;

    private final UserInfo userInfo;

    private SSHConnection( final Host host, final SSHConfiguration config, final UserInfo userInfo )
        throws SSHWrapException
    {
        this.host = host;
        this.config = config;
        this.userInfo = userInfo;
        jsch = new JSch();

        connect();
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

    private void connect()
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
            session = jsch.getSession( host.getUser(), host.getHostName(), host.getPort() );
            if ( userInfo != null )
            {
                session.setUserInfo( userInfo );
            }

            session.connect();

            for ( final LocalForward lf : host.getLocalForwards() )
            {
                if ( lf.getLocalAddress() == null )
                {
                    session.setPortForwardingL( lf.getLocalPort(), lf.getRemoteAddress(), lf.getRemotePort() );
                }
                else
                {
                    session.setPortForwardingL( lf.getLocalAddress(), lf.getLocalPort(), lf.getRemoteAddress(),
                                                lf.getRemotePort() );
                }
            }

            for ( final RemoteForward rf : host.getRemoteForwards() )
            {
                if ( rf.getLocalAddress() == null )
                {
                    session.setPortForwardingR( rf.getLocalPort(), rf.getRemoteAddress(), rf.getRemotePort() );
                }
                else
                {
                    session.setPortForwardingR( rf.getLocalAddress(), rf.getLocalPort(), rf.getRemoteAddress(),
                                                rf.getRemotePort() );
                }
            }

        }
        catch ( final JSchException e )
        {
            throw new SSHWrapException( "Failed to initialize/connect SSH session for %s@%s:%s\nReason: %s", e,
                                        host.getUser(), host.getHostName(), host.getPort(), e.getMessage() );
        }
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
                    Thread.currentThread()
                          .interrupt();
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

        return Byte.MIN_VALUE;
    }

    public SSHConnection disconnect()
    {
        session.disconnect();
        session = null;

        return this;
    }

    public static final class Builder
    {
        private SSHConfiguration config;

        private String user = System.getProperty( "user.name" );

        private final String host;

        private int port;

        private final Prompter prompter;

        private final Set<LocalForward> localForwards = new HashSet<LocalForward>();

        private final Set<RemoteForward> remoteForwards = new HashSet<RemoteForward>();

        public Builder( final String host, final Prompter prompter )
        {
            this.host = host;
            this.prompter = prompter;
        }

        public Builder withLocalForward( final LocalForward lf )
        {
            localForwards.add( lf );
            return this;
        }

        public Builder withLocalForward( final String localAddress, final int localPort, final String remoteAddress,
                                         final int remotePort )
        {
            localForwards.add( new LocalForward( localAddress, localPort, remoteAddress, remotePort ) );
            return this;
        }

        public Builder withLocalForward( final int localPort, final String remoteAddress, final int remotePort )
        {
            localForwards.add( new LocalForward( localPort, remoteAddress, remotePort ) );
            return this;
        }

        public Builder withRemoteForward( final RemoteForward lf )
        {
            remoteForwards.add( lf );
            return this;
        }

        public Builder withRemoteForward( final String localAddress, final int localPort, final String remoteAddress,
                                          final int remotePort )
        {
            remoteForwards.add( new RemoteForward( localAddress, localPort, remoteAddress, remotePort ) );
            return this;
        }

        public Builder withRemoteForward( final int localPort, final String remoteAddress, final int remotePort )
        {
            remoteForwards.add( new RemoteForward( localPort, remoteAddress, remotePort ) );
            return this;
        }

        public Builder withUser( final String user )
        {
            this.user = user;
            return this;
        }

        public Builder withPort( final int port )
        {
            this.port = port;
            return this;
        }

        public Builder withConfig( final SSHConfiguration config )
        {
            this.config = config;
            return this;
        }

        public SSHConnection create()
            throws SSHWrapException
        {
            if ( config == null )
            {
                config = new DefaultSSHConfiguration();
            }

            final Host h = config.lookup( host );
            if ( user != null )
            {
                h.setUser( user );
            }

            if ( port > 0 )
            {
                h.setPort( port );
            }

            for ( final LocalForward lf : localForwards )
            {
                h.addLocalForward( lf );
            }

            for ( final RemoteForward rf : remoteForwards )
            {
                h.addRemoteForward( rf );
            }

            return new SSHConnection( h, config, prompter );
        }
    }

}
