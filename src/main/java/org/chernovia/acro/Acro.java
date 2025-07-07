package org.chernovia.acro;

import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chernovia.lib.zugserv.JSONifier;
import org.chernovia.lib.zugserv.ZugUtils;
import java.util.ArrayList;
import java.util.List;

public class Acro implements JSONifier {

    enum Scope {scoring};
    AcroPlayer author;
    int round;
    String txt;
    String topic;
    List<AcroPlayer> votes = new ArrayList<>();
    long time;
    String id = "";
    boolean speedy = false;
    boolean winner = false;

    public Acro(AcroPlayer author, String txt, String topic, int round, long time) {
        this.author = author;
        this.txt = txt;
        this.round = round;
        this.time = time;
    }

    public float getTimeInSeconds() {
        return time/1000f;
    }

    @Override
    public ObjectNode toJSON(List<String> scopes) {
        ObjectNode json = ZugUtils.newJSON()
                .put(AcroField.acroId,id)
                .put(AcroField.round, round)
                .put(AcroField.acroTxt, txt)
                .put(AcroField.time, getTimeInSeconds());
        if (hasScope(scopes, Scope.scoring, true)) {
            json.set(AcroField.author,author.toJSON());
            ArrayNode arrayNode = ZugUtils.newJSONArray();
            for (AcroPlayer vote : votes) arrayNode.add(vote.toJSON());
            json.set(AcroField.votes, arrayNode);
            json.put(AcroField.speedy,speedy);
            json.put(AcroField.winner,winner);
        }
        return json;
    }
}
