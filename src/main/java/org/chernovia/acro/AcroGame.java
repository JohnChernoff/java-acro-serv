package org.chernovia.acro;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chernovia.lib.zugserv.*;
import org.chernovia.lib.zugserv.enums.ZugAuthSource;
import org.chernovia.lib.zugserv.enums.ZugScope;
import org.chernovia.lib.zugserv.enums.ZugServMsgType;
import java.util.*;
import java.util.concurrent.ThreadLocalRandom;

//TODO: topics

public class AcroGame extends ZugArea {
    enum AcroOption { acroTime, voteTime, acroBaseDisplayTime, acroDisplayTime, topicTime, summaryTime, skipTime,
        victoryPoints, minLetters, maxLetters, voteBonus, speedBonus, adult, maxGameIdle, maxPlayerIdle, numTopics, winnerChooseTopic}
    enum AcroPhase { paused,waiting,composing,voting,scoring,topicSelect,summarizing,finished,skipping }
    //enum AcroScope {}
    int roundIdle = 0;
    int round = 0;
    int numAI = 1;
    List<String> currentAcro = new ArrayList<>();
    private List<AcroLetter> letters;
    private final List<String> topics = new ArrayList<>();
    String currentTopic = AcroField.noTopic;
    AcroPlayer topicChooser = null;

    public AcroGame(String t, ZugUser c, AreaListener l, String letterFile, int winPoints, int acroTime, int voteTime, boolean allowGuests, boolean adult) {
        super(t, c, l,new AreaConfig(allowGuests,false,false,true,true));
        setMaxOccupants(30);
        setOptionsManager(new OptionsManager(
                OptionsManager.createOption(AcroOption.acroTime,acroTime,15,360,1,"Throw Time","Throw Time (in seconds)"),
                OptionsManager.createOption(AcroOption.voteTime,voteTime,15,60,1,"Vote Time","Voting Time (in seconds)"),
                OptionsManager.createOption(AcroOption.acroBaseDisplayTime,2000,1000,24000,250,"Base Acro Display Time","Base Acro Display Time (in millis)"),
                OptionsManager.createOption(AcroOption.acroDisplayTime,2000,1000,8000,250,"Acro Display Time","Individual Acro Display Time (in millis)"),
                OptionsManager.createOption(AcroOption.topicTime,12,5,60,1,"Topic Time","Topic Selection Time (in seconds)"),
                OptionsManager.createOption(AcroOption.summaryTime,30,15,60,1,"Summary Time","Game Summary Time (in seconds)"),
                OptionsManager.createOption(AcroOption.skipTime,2000,250,12000,250,"Skip Time","Phase Skip Time (in millis)"),
                OptionsManager.createOption(AcroOption.victoryPoints,winPoints,3,60,1,"Victory Points","Points required to win"),
                OptionsManager.createOption(AcroOption.minLetters,3,2,12,1,"Min Letters","Minimum Acro Letters"),
                OptionsManager.createOption(AcroOption.maxLetters,7,3,24,1,"Max Letters","Maximum Acro Letters"),
                OptionsManager.createOption(AcroOption.voteBonus,1,0,4,1,"Vote Bonus","Winning Acro Vote Bonus"),
                OptionsManager.createOption(AcroOption.speedBonus,2,0,8,1,"Speed Bonus","Fastest Acro Bonus"),
                OptionsManager.createOption(AcroOption.maxPlayerIdle,5,0,24,1,"Max Player Idle","Maximum Rounds of Player Idling"),
                OptionsManager.createOption(AcroOption.maxGameIdle,3,0,24,1,"Max Game Idle","Maximum Rounds of Game Idling"),
                OptionsManager.createOption(AcroOption.adult,adult,"Adult Themes","Adult Topics/Acros"),
                OptionsManager.createOption(AcroOption.winnerChooseTopic,true,"Winner Topics","Winner chooses next topic"),
                OptionsManager.createOption(AcroOption.numTopics,4,2,8,1,"Num Topics","Number of Topics to Select")
        ));
        loadLetters(letterFile);
    }

    protected void loadLetters(String abcFile) {
        letters = AcroLetter.loadABC(abcFile);
        if (letters == null) {
            spam("Can't find Letter File: " + abcFile + ", using default (" + AcroServ.DEF_LETTER_FILE + ") " + "instead.");
            letters = AcroLetter.loadABC(AcroServ.DEF_LETTER_FILE + AcroLetter.LETTEXT);
        }
        else spam("Loaded Letter File: " + abcFile);
    }

    @Override
    public String getName() {
        return "Acro Area";
    }

    @Override
    public void run() {
        for (int i=0; i<numAI; i++) {
            addOccupant(new AcroPlayer(
                    new AcroUser(null,new ZugUser.UniqueName(generateBotName(), ZugAuthSource.bot)),this));
        }
        newGame();
    }

    public String getCurrentAcro() {
        return currentAcro.stream()
                .reduce("", (partialString, element) -> partialString + element);
    }

    public void registerAcro(AcroPlayer player, String acro) {
        if (pm().getPhase() == AcroPhase.composing) {
            player.idle = 0;
            String[] words = acro.toUpperCase().split("\\s+");
            if (words.length != currentAcro.size()) {
                tell(player,AcroMsg.badAcro); return;
            }
            for (int i = 0; i < words.length; i++) {
                if (!words[i].startsWith(currentAcro.get(i))) {
                    tell(player,AcroMsg.badAcro); return;
                }
            }
            player.currentAcro = new Acro(player,acro,currentTopic,round,System.currentTimeMillis() - pm().getPhaseStamp());
            tell(player,AcroMsg.acroConfirmed,player.currentAcro.toJSON());
        } else tell(player,"Bad phase: " + pm().getPhase());
    }

    public void registerVote(AcroPlayer player, String vote) {
        if (pm().getPhase() == AcroPhase.voting) {
            getCurrentAcros().stream().filter(acro -> Objects.equals(acro.id, vote)).findFirst().ifPresent(acro -> {
                    if (acro.author == player) {
                        tell(player,"You cannot vote for yourself."); //TODO - make message?
                    } else {
                        acro.votes.add(player);
                        player.idle = 0;
                        tell(player,AcroMsg.voteConfirmed,acro.toJSON());
                    }
                }
            );
        }
    }

    private void makeAcro(int numlets) {
        currentAcro.clear();
        int t = letters.stream().map(l -> l.prob).reduce(0, Integer::sum);
        for (int n=0; n<numlets; n++) {
            int v = ThreadLocalRandom.current().nextInt(0, t);
            int probSum = 0;
            for (AcroLetter letter : letters) {
                probSum += letter.prob;
                if (probSum > v) {
                    currentAcro.add(letter.c.toUpperCase());
                    break;
                }
            }
        }
    }

    private java.util.concurrent.CompletableFuture<Boolean> newTopics() { //TODO: categories?
        currentTopic = AcroField.noTopic;
        try {
            topics.clear(); //ZugManager.log("Available Topics: " + AcroServ.topics.toString());
            List<AcroTopic> availableTopics = new ArrayList<>(AcroServ.topics.stream().filter(topic ->
                    topic.maxLetters >= currentAcro.size() && topic.minLetters <= currentAcro.size() &&
                            (om().getBool(AcroOption.adult) || !Objects.equals(topic.category, "adult"))).toList());
            Collections.shuffle(availableTopics);
            ArrayNode arrayNode = ZugUtils.newJSONArray();
            while (topics.size() < om().getInt(AcroOption.numTopics)) { //ZugManager.log("Available Topics: " + availableTopics);
                topics.add(availableTopics.removeFirst().topic);
                arrayNode.add(topics.getLast());
            }
            if (topicChooser == null) {
                return pm().newPhase(AcroPhase.skipping, om().getInt(AcroOption.skipTime));
            } else return pm().newPhase(AcroPhase.topicSelect, om().getInt(AcroOption.topicTime) * 1000,
                    topicChooser.toJSON().set(AcroField.topics,arrayNode));
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    void newGame() {
        ZugManager.log("New Game: " + getTitle());
        initGame();
        spam(AcroMsg.newGame);
        startNextRound();
    }

    void newTopic(AcroPlayer player, String t) {
        if (pm().getPhase() == AcroPhase.topicSelect) {
            if (player == topicChooser) {
                if (topics.contains(t)) {
                    currentTopic = t;
                    pm().interruptPhase();
                }
                else err(player.getAcroUser(),"Invalid topic");
            } else err(player.getAcroUser(),"You can't choose a topic");
        } else err(player.getAcroUser(),"Bad phase: " + pm().getPhase());
    }

    void unIdle() {
        roundIdle = 0;
        if (pm().isPhase(AcroPhase.waiting)) pm().interruptPhase();
    }

    public boolean isIdle() { return roundIdle >=  om().getInt(AcroOption.maxGameIdle); }

    void initGame() {
        for (AcroPlayer player : getPlayers()) {
            if (!player.history.isEmpty()) player.histories.add(player.history);
            player.history.clear();
            player.points = 0;
            player.idle = 0;
        }
    }

    private void startNextRound() {
        round++;
        if (isIdle()) {
            spam("Bah, nobody's playing - going idle.");
            pm().newPhase(AcroPhase.waiting, 999999999).thenRun(this::nextRound);
        } else nextRound();
    }

    private void checkIdle() {
        getPlayers().forEach(player -> {
            player.currentAcro = null;
            if (player.idle++ > om().getInt(AcroOption.maxPlayerIdle)) {
                spam(player.getName() + " has been ejected for idleness.");
                kick(player);
            }
        });
    }

    private void nextRound() {
        topicChooser = null;
        checkIdle();
        makeAcro(ThreadLocalRandom.current().nextInt(om().getInt(AcroOption.minLetters),om().getInt(AcroOption.maxLetters)+1));
        spam("Round " + round + ": enter your acros!  You have " + om().getInt(AcroOption.acroTime) + " seconds.");
        pm().newPhase(AcroPhase.composing, om().getInt(AcroOption.acroTime) * 1000, ZugUtils.newJSON()
                        .put(AcroField.acro, getCurrentAcro())
                        .put(AcroField.topic, currentTopic)
                ).thenRun(() -> { //spam("Generating bot acros...");
                    for (AcroPlayer bot : getBots()) registerAcro(bot,AcroBot.generateStructuredAcro(getCurrentAcro()));
                    spam("Round " + round + ": enter your votes! You have " + om().getInt(AcroOption.voteTime) + " seconds.");
                    initVoting();
                    if (getCurrentAcros().isEmpty()) {
                        roundIdle++;
                        pm().newPhase(AcroPhase.skipping,om().getInt(AcroOption.skipTime)).thenRun(this::startNextRound);
                    } else pm().newPhase(AcroPhase.voting,om().getInt(AcroOption.voteTime) * 1000,acrosToJSON(true))
                            .thenRun(() -> {
                                tally();
                                int scoreTime = (getCurrentAcros().size() * om().getInt(AcroOption.acroDisplayTime)) + om().getInt(AcroOption.acroBaseDisplayTime);
                                pm().newPhase(AcroPhase.scoring,scoreTime, acrosToJSON(false))
                                        .thenRun(() -> {
                                            spam(ZugServMsgType.updateOccupants,toJSON(ZugScope.occupants_all));
                                            List<AcroPlayer> winners = getWinners();
                                            if (winners.isEmpty()) {
                                                spam("Next round in " + om().getInt(AcroOption.topicTime) + " seconds");
                                                newTopics().thenRun(this::startNextRound);
                                            } else {
                                                endGame(winners);
                                            }
                                        });
                            });
                });
    }

    public void endGame(List<AcroPlayer> winners) {
        for (AcroPlayer winner : winners) spam(winner.getName() + " wins!");
        ZugManager.log("Updating players for: " + getTitle());
        updatePlayers(winners);
        ZugManager.log("Summary time for: " + getTitle());
        pm().newPhase(AcroPhase.summarizing,om().getInt(AcroOption.summaryTime)).thenRun(this::newGame);
    }

    public void updatePlayers(List<AcroPlayer> winners) {
        try {
            for (AcroPlayer player : getPlayers()) {
                ZugManager.log("Updating: " + player.getName());
                AcroUser user = player.getAcroUser();
                ZugManager.log("User: " + user);
                user.points += player.points;
                user.games++;
                user.acros += player.history.size();
                if (winners.contains(player)) user.wins++;
                AcroServ.acroBase.updateUser(user);
            }
        } catch (Exception e) { e.printStackTrace();
            ZugManager.log("updatePlayers error: " + e.getMessage());
        }
    }

    public void initVoting() {
        List<Acro> acros = getCurrentAcros();
        Collections.shuffle(acros); //spam("Initializing voting, " + acros.size() + " acros...");
        int i = 0; for (Acro acro : acros) acro.id = String.valueOf(++i); //spam("Voting initialized");
    }

    public void tally() {
        getCurrentAcros().stream()
                .filter(a -> !a.votes.isEmpty())
                .min((o1, o2) -> (int) (o1.time - o2.time)).ifPresent(acro -> acro.speedy = true);
        int winVotes = getCurrentAcros().stream()
                .filter(a -> !a.votes.isEmpty())
                .max(Comparator.comparingInt(o -> o.votes.size())).map(acro -> acro.votes.size()).orElse(0);
        if (winVotes > 0) getCurrentAcros().stream().filter(a -> a.votes.size() == winVotes)
                .min((o1, o2) -> (int) (o1.time - o2.time)).ifPresent(acro -> acro.winner = true);
        for (Acro acro : getCurrentAcros()) {
            acro.author.points += acro.votes.size();
            if (acro.speedy) acro.author.points += om().getInt(AcroOption.speedBonus);
            if (acro.winner) {
                acro.author.points += currentAcro.size();
                for (AcroPlayer voter : acro.votes) voter.points += om().getInt(AcroOption.voteBonus);
                if (om().getBool(AcroOption.winnerChooseTopic)) topicChooser = acro.author; //TODO: else pick random player?
            }
            if (!acro.author.isBot()) AcroServ.acroBase.updateAcro(acro);
        }
    }

    private ObjectNode acrosToJSON(boolean hideAuthor) {
        ObjectNode acroNode = ZugUtils.newJSON();
        ArrayNode acroArray = ZugUtils.newJSONArray();
        for (Acro acro : getCurrentAcros()) acroArray.add(hideAuthor ? acro.toJSON() : acro.toJSON(Acro.Scope.scoring));
        return acroNode.set(AcroField.acros, acroArray);
    }

    public List<AcroPlayer> getWinners() {
        return new ArrayList<>(getPlayers().stream().filter(p -> p.points >= om().getInt(AcroOption.victoryPoints)).toList());
    }

    List<Acro> getCurrentAcros() {
        return new ArrayList<>(getPlayers().stream().filter(p -> p.currentAcro != null).map(p2 -> p2.currentAcro).toList());
    }

    List<AcroPlayer> getPlayers() {
        return new ArrayList<>(getOccupants().stream().map(x -> (AcroPlayer) x).toList());
    }

    List<AcroPlayer> getBots() {
        return new ArrayList<>(getPlayers().stream().filter(Occupant::isBot).toList());
    }

    @Override
    public ObjectNode toJSON(List<String> scopes) {
        return super.toJSON(scopes)
                .put(AcroField.round, round)
                .put(AcroField.acro,getCurrentAcro());
    }
}
