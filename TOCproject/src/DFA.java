import java.util.ArrayList;
import java.util.List;

public class DFA {

    State startState;
    List<State> states = new ArrayList<>();

    void addState(State s) {
        states.add(s);
        if (startState == null) startState = s;
    }

    boolean simulate(String input) {
        State current = startState;
        for (char c : input.toCharArray()) {
            if (!current.transitions.containsKey(c)) return false;
            current = current.transitions.get(c);
        }
        return current.isAccept;
    }
}