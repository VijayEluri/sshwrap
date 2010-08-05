/*
 *  Copyright (c) 2010 Red Hat, Inc.
 *  
 *  This program is licensed to you under Version 3 only of the GNU
 *  General Public License as published by the Free Software 
 *  Foundation. This program is distributed in the hope that it will be 
 *  useful, but WITHOUT ANY WARRANTY; without even the implied 
 *  warranty of MERCHANTABILITY or FITNESS FOR A PARTICULAR 
 *  PURPOSE.
 *  
 *  See the GNU General Public License Version 3 for more details.
 *  You should have received a copy of the GNU General Public License 
 *  Version 3 along with this program. 
 *  
 *  If not, see http://www.gnu.org/licenses/.
 */

package org.commonjava.sshwrap;


public enum ChannelType
{

    session,
    shell,
    exec,
    x11,
    auth_agent( "auth-agent@openssh.com" ),
    direct_tcpip,
    forwarded_tcpip,
    sftp,
    subsystem;

    private String realName;

    private ChannelType( final String realName )
    {
        this.realName = realName;
    }

    private ChannelType()
    {
        realName = name().replace( '_', '-' );
    }

    public String channelName()
    {
        return realName;
    }
}
