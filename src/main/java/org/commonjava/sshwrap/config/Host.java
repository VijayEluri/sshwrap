package org.commonjava.sshwrap.config;

import java.io.File;
import java.util.HashSet;
import java.util.Set;

/**
 * Configuration of one "Host" block in the configuration file.
 * <p>
 * If returned from {@link OpenSshConfig#lookup(String)} some or all of the properties may not be populated. The
 * properties which are not populated should be defaulted by the caller.
 * <p>
 * When returned from {@link OpenSshConfig#lookup(String)} any wildcard entries which appear later in the configuration
 * file will have been already merged into this block.
 */
public class Host
{
    private boolean patternsApplied;

    private String hostName;

    private int port;

    private File identityFile;

    private String user;

    private String preferredAuthentications;

    private Boolean batchMode;

    private String strictHostKeyChecking;

    private Set<LocalForward> localForwards = new HashSet<LocalForward>();

    private Set<RemoteForward> remoteForwards = new HashSet<RemoteForward>();

    /**
     * @return the value StrictHostKeyChecking property, the valid values are "yes" (unknown hosts are not accepted),
     *         "no" (unknown hosts are always accepted), and "ask" (user should be asked before accepting the host)
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
     * @return path of the private key file to use for authentication; null if the caller should use default
     *         authentication strategies.
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
     * @return the preferred authentication methods, separated by commas if more than one authentication method is
     *         preferred.
     */
    public String getPreferredAuthentications()
    {
        return preferredAuthentications;
    }

    /**
     * @return true if batch (non-interactive) mode is preferred for this host connection.
     */
    public boolean isBatchMode()
    {
        return batchMode != null && batchMode.booleanValue();
    }

    public boolean isPatternsApplied()
    {
        return patternsApplied;
    }

    public Boolean getBatchMode()
    {
        return batchMode;
    }

    public Set<LocalForward> getLocalForwards()
    {
        return localForwards;
    }

    public Set<RemoteForward> getRemoteForwards()
    {
        return remoteForwards;
    }

    public void setPatternsApplied( final boolean patternsApplied )
    {
        this.patternsApplied = patternsApplied;
    }

    public void setHostName( final String hostName )
    {
        this.hostName = hostName;
    }

    public void setPort( final int port )
    {
        this.port = port;
    }

    public void setIdentityFile( final File identityFile )
    {
        this.identityFile = identityFile;
    }

    public void setUser( final String user )
    {
        this.user = user;
    }

    public void setPreferredAuthentications( final String preferredAuthentications )
    {
        this.preferredAuthentications = preferredAuthentications;
    }

    public void setBatchMode( final Boolean batchMode )
    {
        this.batchMode = batchMode;
    }

    public void setStrictHostKeyChecking( final String strictHostKeyChecking )
    {
        this.strictHostKeyChecking = strictHostKeyChecking;
    }

    public void setLocalForwards( final Set<LocalForward> localForwards )
    {
        this.localForwards = localForwards;
    }

    public void setRemoteForwards( final Set<RemoteForward> remoteForwards )
    {
        this.remoteForwards = remoteForwards;
    }

    public void addLocalForward( final LocalForward lf )
    {
        this.localForwards.add( lf );
    }

    public void addRemoteForward( final RemoteForward rf )
    {
        this.remoteForwards.add( rf );
    }

}