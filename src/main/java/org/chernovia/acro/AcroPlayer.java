package org.chernovia.acro;

import com.fasterxml.jackson.databind.node.ObjectNode;
import org.chernovia.lib.zugserv.Occupant;
import org.chernovia.lib.zugserv.ZugArea;
import org.chernovia.lib.zugserv.ZugUser;
import java.util.ArrayList;
import java.util.List;

public class AcroPlayer extends Occupant {

    int points = 0;

    int idle = 0;

    Acro currentAcro;

    List<List<Acro>> histories = new ArrayList<>();

    List<Acro> history = new ArrayList<>();

    public AcroPlayer(ZugUser u, ZugArea area) {
        super(u, area);
    }

    public AcroGame getGame() {
        return (AcroGame) getArea();
    }

    public ObjectNode toJSON(List<String> scopes) {
        return super.toJSON(scopes).put(AcroField.points, points);
    }

}
