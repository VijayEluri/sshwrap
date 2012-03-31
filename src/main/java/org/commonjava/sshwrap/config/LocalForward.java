package org.commonjava.sshwrap.config;

public final class LocalForward
{
    private final String localAddress;

    private final int localPort;

    private final String remoteAddress;

    private final int remotePort;

    public LocalForward( final int localPort, final String remoteAddress, final int remotePort )
    {
        this.localAddress = null;
        this.localPort = localPort;
        this.remoteAddress = remoteAddress;
        this.remotePort = remotePort;
    }

    public LocalForward( final String localAddress, final int localPort, final String remoteAddress,
                         final int remotePort )
    {
        this.localAddress = localAddress;
        this.localPort = localPort;
        this.remoteAddress = remoteAddress;
        this.remotePort = remotePort;
    }

    public String getLocalAddress()
    {
        return localAddress;
    }

    public int getLocalPort()
    {
        return localPort;
    }

    public String getRemoteAddress()
    {
        return remoteAddress;
    }

    public int getRemotePort()
    {
        return remotePort;
    }
}