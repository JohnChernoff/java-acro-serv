package org.chernovia.acro;

import org.chernovia.lib.zugserv.Connection;
import org.chernovia.lib.zugserv.ZugUser;

public class AcroUser extends ZugUser {
    public AcroUser(Connection c, UniqueName uName) {
        super(c, uName);
    }
}
