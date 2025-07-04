package org.chernovia.acro;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chernovia.lib.zugserv.ZugFields;
import org.chernovia.lib.zugserv.ZugUtils;
import java.sql.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.logging.Level;
import static org.chernovia.lib.zugserv.ZugHandler.log;

public class AcroBase {
    private record Credentials(String uri, String usr, String pwd, String db) {}
    private final Connection conn;
    int maxAttempts = 3;
    public boolean recordGuests = false;

    public AcroBase(String uri, String usr, String pwd, String db) {
        conn = connect(new Credentials(uri, usr, pwd, db));
    }

    private Connection connect(final Credentials credentials) {
        try {
            if (conn != null && !conn.isClosed()) conn.close();
            String connStr = "jdbc:mysql://" + credentials.uri +
                    "/" + credentials.db + "?autoReconnect=TRUE";
            //"?user=" + credentials.usr + "&password=" + credentials.pwd;
            return DriverManager.getConnection(connStr,credentials.usr,credentials.pwd);
        } catch (SQLException ex) {
            log(Level.WARNING,"DataBase connection error: " + ex);
            return null;
        }
    }

    public List<AcroTopic> loadTopics() {
        List<AcroTopic> topics = new ArrayList<>();
        try {
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM topics");
            ResultSet rs = ps.executeQuery();
            while (rs.next()) topics.add(new AcroTopic(
                    rs.getInt(AcroField.id),
                    rs.getString(AcroField.topic),
                    rs.getString(AcroField.category),
                    rs.getInt(AcroField.min_letters),
                    rs.getInt(AcroField.max_letters)
            ));
        } catch (SQLException e) {
            log(Level.WARNING,"SQL Exception: " + e);
        } return topics;
    }

    public Optional<ResultSet> getOrCreateUser(AcroUser user) {
        if (!recordGuests && user.isGuest()) return Optional.empty();
        return getOrCreateUser(user,0);
    }
    private Optional<ResultSet> getOrCreateUser(AcroUser user, int attempts) {
        ResultSet rs;
        try {
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM players WHERE name = ? AND source = ?");
            ps.setString(1,user.getName());
            ps.setString(2,user.getSource().name());
            rs = ps.executeQuery();
            if (rs.next()) return Optional.of(rs);
            else {
                log("Adding user: " + user.getName() + "@" + user.getSource().name());
                ps = conn.prepareStatement("INSERT INTO players (name,source) VALUES (?,?)");
                ps.setString(1,user.getName());
                ps.setString(2,user.getSource().name());
                ps.execute();
                if (attempts < maxAttempts) return getOrCreateUser(user,attempts + 1);
                else {
                    log(Level.WARNING, "Max user creation attempts exceeded"); return Optional.empty();
                }
            }
        } catch (SQLException e) {
            log(Level.WARNING,"SQL Exception: " + e); return Optional.empty();
        }
    }

    public void updateUser(AcroUser user) {
        if (!recordGuests && user.isGuest()) return;
        try {
            PreparedStatement ps;
            ps = conn.prepareStatement("UPDATE players SET points = ?, acros = ?, games = ?, wins = ? WHERE id = ?");
            ps.setInt(1,user.points);
            ps.setInt(2,user.acros);
            ps.setInt(3,user.games);
            ps.setInt(4,user.wins);
            ps.setInt(5,user.id);
            ps.execute();
        } catch (SQLException e) {
            log(Level.WARNING,"SQL Exception: " + e);
        }
    }

    public void updateAcro(Acro acro) {
        if (!recordGuests && acro.author.getUser().isGuest()) return;
        try {
            PreparedStatement ps;
            ps = conn.prepareStatement("INSERT INTO acros (txt,votes,time,date,winner,topic,speedy,author) VALUES (?,?,?,?,?,?,?,?)");
            ps.setString(1,acro.txt);
            ps.setInt(2,acro.votes.size());
            ps.setFloat(3,acro.time / 1000f);
            ps.setDate(4,new Date(System.currentTimeMillis()));
            ps.setBoolean(5,acro.winner);
            ps.setString(6,acro.topic);
            ps.setBoolean(7,acro.speedy);
            ps.setInt(8,acro.author.getAcroUser().id);
            ps.execute();
        } catch (SQLException e) {
            log(Level.WARNING,"SQL Exception: " + e);
        }
    }

    public Optional<ObjectNode> getTop(int n, String crit) {
        try {
            PreparedStatement ps = conn.prepareStatement("SELECT * FROM players ORDER BY ? DESC LIMIT ?");
            ps.setString(1,crit);
            ps.setInt(2,n);
            ResultSet rs = ps.executeQuery();
            if (!rs.next()) return Optional.empty();
            ArrayNode users = ZugUtils.newJSONArray();
            do {
                users.add(ZugUtils.newJSON()
                        .put(ZugFields.NAME,rs.getString("name") + "@" + rs.getString("source"))
                        .put(crit,rs.getInt(crit)));
            } while (rs.next());
            return Optional.of(ZugUtils.newJSON().set(ZugFields.USERS,users));
        } catch (SQLException e) {
            log(Level.WARNING,"SQL Exception: " + e);
        }
        return Optional.empty();
    }



}
