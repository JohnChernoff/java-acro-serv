package org.chernovia.acro;

import com.fasterxml.jackson.databind.JsonNode;
import org.chernovia.lib.zugserv.*;
import org.chernovia.lib.zugserv.enums.ZugAuthSource;
import java.util.*;
import java.util.logging.Level;

public class AcroServ extends ZugManager {
    public static AcroBase acroBase;
    public static List<AcroTopic> topics;
    public static final String DEF_LETTER_FILE = "deflet";

    ZugUser admin = new ZugUser(null,new ZugUser.UniqueName("admin", ZugAuthSource.local));

    public static void loadWords() throws Exception {
        AcroBot.loadWords("res/wordlist_by_letter_and_pos.json");
        AcroBot.loadPhrasalVerbs("res/verbs.json");
        AcroBot.loadCorporaWordList("res/nouns.json", "nouns", "noun");
        AcroBot.loadCorporaWordList("res/adjs.json", "adjs", "adj");
        AcroBot.loadCorporaWordList("res/adverbs.json", "adverbs", "adv");
        log("Loaded acrobot word list");
    }

    //args: port db_uri, db_usr, db_pwd, db_name, debug, hosts...
    public static void main(final String[] args) {
        try {
            loadWords();
            acroBase = new AcroBase(args[1], args[2], args[3], args[4]);
            log("Connected to " + args[1]);
            topics = acroBase.loadTopics(); log("Loaded topics");
        } catch (Exception e) { log(Level.SEVERE, e.getMessage()); }
        List<String> hosts = new ArrayList<>(Arrays.asList(args).subList(6, args.length));
        log("Hosts: " + hosts);
        AcroServ serv = new AcroServ(ZugServ.ServType.WEBSOCK_JAVALIN, "acrosrv", Integer.parseInt(args[0]),hosts);
        ZugManager.setLoggingLevel(args[5].equals("debug") ? Level.FINE : Level.INFO);
        serv.getServ().startSrv();
        serv.startPings(20000);
    }

    public AcroServ(ZugServ.ServType type, String ep, int port, List<String> hosts) {
        super(type, port, ep, hosts, null);
        addOrGetArea(new AcroGame("Guest Grotto",admin,this,DEF_LETTER_FILE,30,60,30,true,true));
        addOrGetArea(new AcroGame("Guest Grotto (Rapid)",admin,this,DEF_LETTER_FILE,20,16,6,true,true));
        addOrGetArea(new AcroGame("Classical Cove",admin,this,DEF_LETTER_FILE,30,60,30,false,true));
        addOrGetArea(new AcroGame("Classical Cove (Rapid)",admin,this,DEF_LETTER_FILE,20,16,6,false,true));
        for (ZugArea area : getAreas()) area.startArea(admin,null);
    }

    @Override
    public Optional<ZugUser> handleCreateUser(Connection conn, ZugUser.UniqueName uName, JsonNode dataNode) {
        return Optional.of(new AcroUser(conn, uName));
    }

    @Override
    public Optional<ZugArea> handleCreateArea(ZugUser user, String title, JsonNode dataNode) {
        return Optional.empty();
    }

    @Override
    public Optional<Occupant> handleCreateOccupant(ZugUser user, ZugArea area, JsonNode dataNode) {
        return Optional.of(new AcroPlayer(user, area));
    }

    @Override
    public void areaJoined(ZugArea area, Occupant occupant) {
        super.areaJoined(area, occupant);
        if (area instanceof AcroGame game) game.unIdle();
        area.spam(occupant.getName() + " joins the game!");
        area.tell(occupant,"Welcome to the " + area.getTitle() + "!");
        //area.tell(occupant, ZugServMsgType.phase,area.pm().toJSON()); //TODO: figure out why this is necessary
    }

    Optional<AcroPlayer> getPlayer(ZugUser user, JsonNode dataNode) {
        return getOccupant(user,dataNode).map(occupant -> (AcroPlayer) occupant);
    }

    @Override
    public void handleUnsupportedMsg(Connection conn, String type, JsonNode dataNode, ZugUser user) {
        log(Level.FINE,"Unsupported message: " + type + " from " + user.getName() + ", data: " + dataNode.textValue());
        if (equalsType(type,AcroMsg.newAcro)) {
            getPlayer(user,dataNode).ifPresent(player ->
                    player.getGame().registerAcro(player,getTxtNode(dataNode,AcroField.acro).orElse("")));
        } else if (equalsType(type,AcroMsg.newVote)) {
            getPlayer(user,dataNode).ifPresent(player ->
                    player.getGame().registerVote(player,getTxtNode(dataNode,AcroField.vote).orElse("")));
        } else if (equalsType(type,AcroMsg.newTopic)) {
            getPlayer(user,dataNode).ifPresent(player ->
                    player.getGame().newTopic(player,getTxtNode(dataNode,AcroField.topic).orElse("")));
        }
    }
}
