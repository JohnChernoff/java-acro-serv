package org.chernovia.acro;

import org.chernovia.lib.zugserv.Connection;
import org.chernovia.lib.zugserv.ZugUser;
import java.sql.SQLException;
import java.util.logging.Level;
import static org.chernovia.lib.zugserv.ZugHandler.log;

public class AcroUser extends ZugUser {

    int id = -1, games = 0, acros = 0, points = 0, wins = 0, admin_lvl = 0;

    public AcroUser(Connection c, UniqueName uName) {
        super(c, uName);
        AcroServ.acroBase.getOrCreateUser(this).ifPresent(rs -> {
            try {
                id = rs.getInt(AcroField.id);
                points = rs.getInt(AcroField.points);
                acros = rs.getInt(AcroField.acros);
                games = rs.getInt(AcroField.games);
                wins = rs.getInt(AcroField.wins);
                admin_lvl = rs.getInt(AcroField.admin_lvl);
            }
            catch (SQLException e) {
                log(Level.WARNING,"SQL Exception: " + e);
            }
        });
    }
}
