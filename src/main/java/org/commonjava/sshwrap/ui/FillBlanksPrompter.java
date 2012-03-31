package org.commonjava.sshwrap.ui;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class FillBlanksPrompter
    implements Prompter
{
    
    private String passphrase;
    private String password;
    private Map<String, String> textAnswers = new HashMap<String, String>();
    private Map<String, Boolean> yesNoAnswers = new HashMap<String, Boolean>();
    private final Prompter delegate;
    
    public FillBlanksPrompter( Prompter delegate )
    {
        this.delegate = delegate;
    }
    
    public FillBlanksPrompter setAnswer( String prompt, String answer )
    {
        textAnswers.put( prompt, answer );
        return this;
    }
    
    public FillBlanksPrompter setAnswer( String prompt, Boolean answer )
    {
        yesNoAnswers.put( prompt, answer );
        return this;
    }
    
    public FillBlanksPrompter setPassphrase( String passphrase )
    {
        this.passphrase = passphrase;
        return this;
    }

    public FillBlanksPrompter setPassword( String password )
    {
        this.password = password;
        return this;
    }

    @Override
    public String getPassphrase()
    {
        return passphrase == null ? delegate.getPassphrase() : passphrase;
    }

    @Override
    public String getPassword()
    {
        return password == null ? delegate.getPassword() : password;
    }

    @Override
    public boolean promptPassword( String message )
    {
        return password == null ? delegate.promptPassword( message ) : true;
    }

    @Override
    public boolean promptPassphrase( String message )
    {
        return passphrase == null ? delegate.promptPassphrase( message ) : true;
    }

    @Override
    public boolean promptYesNo( String message )
    {
        Boolean answer = yesNoAnswers.get( message );
        if ( answer == null )
        {
            answer = delegate.promptYesNo( message );
        }
        return answer;
    }

    @Override
    public void showMessage( String message )
    {
        delegate.showMessage( message );
    }

    @Override
    public String[] promptKeyboardInteractive( String destination, String name, String instruction, String[] prompt,
                                               boolean[] echo )
    {
        String[] answers = new String[prompt.length];
        List<String> toDelegate = new ArrayList<String>();
        for ( int i = 0; i < prompt.length; i++ )
        {
            String p = prompt[i];
            String ans = textAnswers.get( p );
            if ( ans != null )
            {
                answers[i] = ans;
            }
            else
            {
                toDelegate.add( p );
                answers[i] = null;
            }
        }
        
        String[] delegated = delegate.promptKeyboardInteractive( destination, name, instruction, toDelegate.toArray( new String[]{} ), echo );
        int currentAnswer = 0;
        for ( int i = 0; i < delegated.length; i++ )
        {
            String ans = delegated[i];
            while( currentAnswer < answers.length && answers[currentAnswer] != null )
            {
                currentAnswer++;
            }
            
            if ( currentAnswer < answers.length )
            {
                answers[currentAnswer] = ans;
                currentAnswer++;
            }
        }
        
        return answers;
    }

}
