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


import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.util.Set;

public interface SSHConfiguration
{

    /**
     * Locate the configuration for a specific host request.
     *
     * @param hostName
     *            the name the user has supplied to the SSH tool. This may be a
     *            real host name, or it may just be a "Host" block in the
     *            configuration file.
     * @return r configuration for the requested name. Never null.
     */
    Host lookup( final String hostName );

    Set<File> getIdentities();

    InputStream getKnownHosts()
        throws IOException;

}
