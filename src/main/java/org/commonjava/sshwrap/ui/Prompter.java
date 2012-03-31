package org.commonjava.sshwrap.ui;

import com.jcraft.jsch.UIKeyboardInteractive;
import com.jcraft.jsch.UserInfo;

public interface Prompter
    extends UserInfo, UIKeyboardInteractive
{

}
