# SSHWrap - Convenience API for Jsch #

SSHWrap is designed to provide a minimal set of classes that allow you streamlined access to the features of the Jsch Java SSH client library.

## Usage ##

Imagine that you need to create and initialize a bare remote Git repository for a project you're about to push. The following method will do the trick:

    private boolean createRemoteGitRepo( final String dir )
        throws IOException, SSHWrapException
    {
        SSHConnection ssh = new SSHConnection( USER, HOST, PORT ).connect();
    
        final Set<String> cmds = new HashSet<String>();
        cmds.add( "mkdir -p " + dir );
        cmds.add( "git --git-dir=" + dir + " --bare init" );

        final ByteArrayOutputStream cmdOutput = new ByteArrayOutputStream();

        for ( final String cmd : cmds )
        {
            cmdOutput.reset();
            final int result = ssh.execute( cmd, cmdOutput );

            if ( LOGGER.isDebugEnabled() )
            {
                LOGGER.debug( new String( cmdOutput.toByteArray() ) );
            }

            if ( result != 0 )
            {
                return false;
            }
        }

        return true;
    }

