package org.commonjava.sshwrap.ui;

import java.awt.Container;
import java.awt.GridBagConstraints;
import java.awt.GridBagLayout;
import java.awt.Insets;

import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JPanel;
import javax.swing.JPasswordField;
import javax.swing.JTextField;

public class SwingPrompter
    implements Prompter
{

    private String password;

    private String passphrase;

    @Override
    public String getPassphrase()
    {
        return passphrase;
    }

    @Override
    public String getPassword()
    {
        return password;
    }

    @Override
    public boolean promptPassphrase( final String prompt )
    {
        if ( passphrase == null )
        {
            passphrase = JOptionPane.showInputDialog( prompt );
        }
        return true;
    }

    @Override
    public boolean promptPassword( final String prompt )
    {
        if ( password == null )
        {
            password = JOptionPane.showInputDialog( prompt );
        }
        return true;
    }

    @Override
    public boolean promptYesNo( final String prompt )
    {
        // return true;
        return JOptionPane.showConfirmDialog( null, prompt ) == JOptionPane.OK_OPTION;
    }

    @Override
    public void showMessage( final String message )
    {
        JOptionPane.showMessageDialog( null, message );
    }

    @Override
    public String[] promptKeyboardInteractive( final String destination, final String name, final String instruction,
                                               final String[] prompt, final boolean[] echo )
    {
        final Container panel = new JPanel();
        panel.setLayout( new GridBagLayout() );

        final GridBagConstraints gbc =
            new GridBagConstraints( 0, 0, 1, 1, 1, 1, GridBagConstraints.NORTHWEST, GridBagConstraints.NONE,
                                    new Insets( 0, 0, 0, 0 ), 0, 0 );

        gbc.weightx = 1.0;
        gbc.gridwidth = GridBagConstraints.REMAINDER;
        gbc.gridx = 0;
        panel.add( new JLabel( instruction ), gbc );
        gbc.gridy++;

        gbc.gridwidth = GridBagConstraints.RELATIVE;

        final JTextField[] texts = new JTextField[prompt.length];
        for ( int i = 0; i < prompt.length; i++ )
        {
            gbc.fill = GridBagConstraints.NONE;
            gbc.gridx = 0;
            gbc.weightx = 1;
            panel.add( new JLabel( prompt[i] ), gbc );

            gbc.gridx = 1;
            gbc.fill = GridBagConstraints.HORIZONTAL;
            gbc.weighty = 1;
            if ( echo[i] )
            {
                texts[i] = new JTextField( 20 );
            }
            else
            {
                texts[i] = new JPasswordField( 20 );
            }
            panel.add( texts[i], gbc );
            gbc.gridy++;
        }

        final int answer =
            JOptionPane.showConfirmDialog( null, panel, destination + ": " + name, JOptionPane.OK_CANCEL_OPTION,
                                           JOptionPane.QUESTION_MESSAGE );

        if ( answer == JOptionPane.OK_OPTION )
        {
            final String[] response = new String[prompt.length];
            for ( int i = 0; i < prompt.length; i++ )
            {
                response[i] = texts[i].getText();
            }
            return response;
        }
        else
        {
            return null; // cancel
        }
    }
}
