import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

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

    // Minmization algorithm (Hopcroft's / Partitioning)
    public void minimize() {
        if (states.isEmpty()) return;

        Set<Character> alphabet = new HashSet<>();
        for (State s : states) {
            alphabet.addAll(s.transitions.keySet());
        }

        List<Set<State>> P = new ArrayList<>();
        Set<State> acceptStates = new HashSet<>();
        Set<State> nonAcceptStates = new HashSet<>();
        for (State s : states) {
            if (s.isAccept) acceptStates.add(s);
            else nonAcceptStates.add(s);
        }
        if (!acceptStates.isEmpty()) P.add(acceptStates);
        if (!nonAcceptStates.isEmpty()) P.add(nonAcceptStates);

        boolean changed = true;
        while (changed) {
            changed = false;
            List<Set<State>> newP = new ArrayList<>();
            for (Set<State> group : P) {
                Map<List<Integer>, Set<State>> splitMap = new HashMap<>();
                for (State s : group) {
                    List<Integer> signature = new ArrayList<>();
                    for (char c : alphabet) {
                        State target = s.transitions.get(c);
                        int targetGroupIdx = -1;
                        if (target != null) {
                            for (int i = 0; i < P.size(); i++) {
                                if (P.get(i).contains(target)) {
                                    targetGroupIdx = i;
                                    break;
                                }
                            }
                        }
                        signature.add(targetGroupIdx);
                    }
                    splitMap.computeIfAbsent(signature, k -> new HashSet<>()).add(s);
                }
                newP.addAll(splitMap.values());
                if (splitMap.size() > 1) {
                    changed = true;
                }
            }
            P = newP;
        }

        List<State> newStates = new ArrayList<>();
        State newStart = null;
        Map<Set<State>, State> groupToNewState = new HashMap<>();

        int id = 0;
        for (Set<State> group : P) {
            State rep = group.iterator().next();
            State ns = new State(id++, rep.isAccept);
            groupToNewState.put(group, ns);
            newStates.add(ns);
            if (group.contains(startState)) {
                newStart = ns;
            }
        }

        for (Set<State> group : P) {
            State ns = groupToNewState.get(group);
            State rep = group.iterator().next();
            for (Map.Entry<Character, State> edge : rep.transitions.entrySet()) {
                State target = edge.getValue();
                for (Set<State> g : P) {
                    if (g.contains(target)) {
                        ns.addTransition(edge.getKey(), groupToNewState.get(g));
                        break;
                    }
                }
            }
        }

        this.states = newStates;
        this.startState = newStart;
    }
}