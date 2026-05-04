import java.util.HashMap;
import java.util.Map;

public class State {
    int id;
    boolean isAccept;
    Map<Character, State> transitions = new HashMap<>();

    public State(int id, boolean isAccept) {
        this.id = id;
        this.isAccept = isAccept;
    }

    public void addTransition(char input, State next) {
        transitions.put(input, next);
    }
}