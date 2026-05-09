import java.util.*;

public class RegexToDFA {

    // special symbol we use for concatenation
    private static final char CONCAT = '.';

    // NFA state class
    private static class NFAState {
        int id; // state number
        Map<Character, Set<NFAState>> trans = new HashMap<>(); // transitions on characters
        Set<NFAState> eps = new HashSet<>(); // epsilon transitions (no input)

        NFAState(int id) { this.id = id; }

        // add a normal transition
        void t(char s, NFAState n) { 
            trans.computeIfAbsent(s, k -> new HashSet<>()).add(n);
        }

        // add an epsilon transition
        void e(NFAState n) { eps.add(n); }
    }

    // fragment = small piece of NFA with start and end
    private static class Frag {
        NFAState s, e;
        Frag(NFAState s, NFAState e) { this.s = s; this.e = e; }
    }

    // whole NFA object
    private static class NFA {
        NFAState s, a; // start and accept states
        Set<Character> alpha; // alphabet
        NFA(NFAState s, NFAState a, Set<Character> alpha) {
            this.s = s; this.a = a; this.alpha = alpha;
        }
    }

    // build DFA from regex (with optional logging of steps)
    public static DFA buildFromRegex(String regex, boolean logSteps) {
        if (regex == null || regex.trim().isEmpty()) throw new IllegalArgumentException("Regex cannot be empty.");
        String normalized = regex.replaceAll("\\s+", "");
        if (normalized.isEmpty()) throw new IllegalArgumentException("Regex cannot be empty.");
        
        // Step 1: add concat operator
        String withConcat = addConcatOperator(normalized);
        if (logSteps) System.out.println("Step 1 (Add Concat): " + normalized + " -> " + withConcat);
        
        // Step 2: convert to postfix
        String postfix = toPostfix(withConcat);
        if (logSteps) System.out.println("Step 2 (Postfix): " + withConcat + " -> " + postfix);
        
        // Step 3: build NFA
        NFA nfa = buildNFA(postfix);
        if (logSteps) System.out.println("Step 3: Built NFA from postfix expression.");
        
        // Step 4: convert NFA to DFA
        return convertNFAToDFA(nfa, logSteps);
    }

    // simpler version without logging
    public static DFA buildFromRegex(String regex) {
        return buildFromRegex(regex, false);
    }

    // insert explicit concat operator between symbols
    private static String addConcatOperator(String regex) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < regex.length(); i++) {
            char current = regex.charAt(i);
            out.append(current);
            if (i + 1 < regex.length() && needsConcat(current, regex.charAt(i + 1))) out.append(CONCAT);
        }
        return out.toString();
    }

    // check if concat is needed between two characters
    private static boolean needsConcat(char left, char right) {
        return (isLiteral(left) || left == ')' || left == '*' || left == '+') && (isLiteral(right) || right == '(');
    }

    // convert regex to postfix using shunting-yard algorithm
    private static String toPostfix(String regex) {
        StringBuilder out = new StringBuilder();
        Deque<Character> ops = new ArrayDeque<>();
        for (char t : regex.toCharArray()) {
            if (isLiteral(t)) out.append(t);
            else if (t == '(') ops.push(t);
            else if (t == ')') {
                while (!ops.isEmpty() && ops.peek() != '(') out.append(ops.pop());
                if (ops.isEmpty() || ops.pop() != '(') throw new IllegalArgumentException("Mismatched parentheses in regex.");
            } else if (isOperator(t)) {
                while (!ops.isEmpty() && ops.peek() != '(' && precedence(ops.peek()) >= precedence(t)) out.append(ops.pop());
                ops.push(t);
            } else {
                throw new IllegalArgumentException("Unsupported symbol in regex: " + t);
            }
        }
        while (!ops.isEmpty()) {
            if (ops.peek() == '(') throw new IllegalArgumentException("Mismatched parentheses in regex.");
            out.append(ops.pop());
        }
        return out.toString();
    }

    // build NFA from postfix regex
    private static NFA buildNFA(String postfix) {
        Deque<Frag> st = new ArrayDeque<>();
        Set<Character> alpha = new HashSet<>();
        int id = 0;
        for (char tok : postfix.toCharArray()) {
            if (isLiteral(tok)) {
                // create two states and connect them
                NFAState a = new NFAState(id++), b = new NFAState(id++);
                a.t(tok, b);
                st.push(new Frag(a, b));
                alpha.add(tok);
            } else if (tok == CONCAT) {
                // concatenation
                Frag r = st.pop(), l = st.pop();
                l.e.e(r.s);
                st.push(new Frag(l.s, r.e));
            } else if (tok == '|' || tok == 'U') {
                // union
                Frag r = st.pop(), l = st.pop();
                NFAState s = new NFAState(id++), e = new NFAState(id++);
                s.e(l.s); s.e(r.s); l.e.e(e); r.e.e(e);
                st.push(new Frag(s, e));
            } else if (tok == '*') {
                // star
                Frag f = st.pop();
                NFAState s = new NFAState(id++), e = new NFAState(id++);
                s.e(f.s); s.e(e); f.e.e(f.s); f.e.e(e);
                st.push(new Frag(s, e));
            } else if (tok == '+') {
                // plus
                Frag f = st.pop();
                NFAState s = new NFAState(id++), e = new NFAState(id++);
                s.e(f.s); f.e.e(f.s); f.e.e(e);
                st.push(new Frag(s, e));
            } else {
                throw new IllegalArgumentException("Unsupported regex token: " + tok);
            }
        }
        if (st.size() != 1) throw new IllegalArgumentException("Invalid regex: unresolved expression.");
        Frag r = st.pop();
        return new NFA(r.s, r.e, alpha);
    }

    // convert NFA to DFA using subset construction
    private static DFA convertNFAToDFA(NFA nfa, boolean logSteps) {
        if (nfa == null || nfa.s == null || nfa.alpha == null) {
            throw new IllegalArgumentException("Invalid NFA provided for DFA conversion.");
        }
        
        DFA dfa = new DFA();
        Map<Set<NFAState>, State> map = new HashMap<>();
        Queue<Set<NFAState>> q = new ArrayDeque<>();
        
        // start state = epsilon closure of NFA start
        Set<NFAState> start = epsilonClosure(Collections.singleton(nfa.s));
        State s0 = new State(0, containsAccept(start, nfa.a));
        map.put(start, s0);
        q.add(start);
        dfa.addState(s0);
        
        if (logSteps) {
            System.out.println("Step 4: NFA to DFA Subset Construction:");
            System.out.println("  epsilon-closure(NFA start state " + nfa.s.id + ") = " + formatStateSet(start) + " -> DFA q0");
        }
        
        int id = 1;
        while (!q.isEmpty()) {
            Set<NFAState> cur = q.poll();
            State ds = map.get(cur);
            for (char sym : nfa.alpha) {
                // move on symbol
                Set<NFAState> mv = move(cur, sym);
                if (mv.isEmpty()) continue;
                Set<NFAState> nxt = epsilonClosure(mv);
                
                State ns = map.get(nxt);
                if (ns == null) {
                    ns = new State(id++, containsAccept(nxt, nfa.a));
                    map.put(nxt, ns);
                    q.add(nxt);
                    dfa.addState(ns);
                }
                ds.addTransition(sym, ns);
                
                if (logSteps) {
                    System.out.println("  - From DFA q" + ds.id + " " + formatStateSet(cur) + " on '" + sym + "':");
                    System.out.println("      move = " + formatStateSet(mv));
                    System.out.println("      epsilon-closure(move) = " + formatStateSet(nxt) + " -> DFA q" + ns.id);
                }
            }
        }
        return dfa;
    }

    // helper: format set of states for printing
    private static String formatStateSet(Set<NFAState> set) {
        StringBuilder sb = new StringBuilder("{");
        List<Integer> ids = new ArrayList<>();
        for (NFAState s : set) ids.add(s.id);
        Collections.sort(ids);
        for (int i = 0; i < ids.size(); i++) {
            sb.append(ids.get(i));
            if (i < ids.size() - 1) sb.append(", ");
        }
        sb.append("}");
        return sb.toString();
    }

    // epsilon closure of a set of states
       // epsilon closure of a set of states
    private static Set<NFAState> epsilonClosure(Set<NFAState> states) {
        Set<NFAState> c = new HashSet<>(states); // closure set
        Deque<NFAState> st = new ArrayDeque<>(states); // stack for DFS
        while (!st.isEmpty()) {
            NFAState cur = st.pop();
            // follow epsilon transitions
            for (NFAState n : cur.eps) {
                if (c.add(n)) st.push(n);
            }
        }
        return c;
    }

    // move function: from a set of states on a symbol
    private static Set<NFAState> move(Set<NFAState> states, char symbol) {
        Set<NFAState> r = new HashSet<>();
        for (NFAState st : states) {
            Set<NFAState> t = st.trans.get(symbol);
            if (t != null) r.addAll(t);
        }
        return r;
    }

    // check if a set of states contains the accept state
    private static boolean containsAccept(Set<NFAState> states, NFAState accept) {
        return states.contains(accept);
    }

    // check if character is an operator
    private static boolean isOperator(char c) {
        return c == '|' || c == '*' || c == '+' || c == CONCAT || c == 'U';
    }

    // operator precedence
    private static int precedence(char operator) {
        if (operator == '*' || operator == '+') return 3;
        if (operator == CONCAT) return 2;
        if (operator == '|' || operator == 'U') return 1;
        return -1;
    }

    // check if character is a literal (symbol in regex)
    private static boolean isLiteral(char c) {
        return Character.isLetterOrDigit(c) && c != 'U';
    }

    // describe NFA states and transitions
    public static List<String> getNFADescription(String regex) {
        if (regex == null || regex.trim().isEmpty()) throw new IllegalArgumentException("Regex cannot be empty.");
        String normalized = regex.replaceAll("\\s+", "");
        if (normalized.isEmpty()) throw new IllegalArgumentException("Regex cannot be empty.");
        
        // build NFA
        NFA nfa = buildNFA(toPostfix(addConcatOperator(normalized)));
        
        List<String> lines = new ArrayList<>();
        Set<NFAState> visited = new HashSet<>();
        Queue<NFAState> q = new ArrayDeque<>();
        List<NFAState> allStates = new ArrayList<>();
        
        // BFS to collect all states
        q.add(nfa.s);
        visited.add(nfa.s);
        
        while (!q.isEmpty()) {
            NFAState cur = q.poll();
            allStates.add(cur);
            
            // follow normal transitions
            for (Set<NFAState> dests : cur.trans.values()) {
                for (NFAState d : dests) {
                    if (visited.add(d)) q.add(d);
                }
            }
            // follow epsilon transitions
            for (NFAState d : cur.eps) {
                if (visited.add(d)) q.add(d);
            }
        }
        
        // sort states by id
        allStates.sort(Comparator.comparingInt(s -> s.id));
        
        // print description
        for (NFAState s : allStates) {
            boolean isStart = (s == nfa.s);
            boolean isAccept = (s == nfa.a);
            lines.add("NFA State " + s.id + (isStart ? " (start)" : "") + (isAccept ? " (accept)" : ""));
            for (Map.Entry<Character, Set<NFAState>> entry : s.trans.entrySet()) {
                for (NFAState dest : entry.getValue()) {
                    lines.add("  --" + entry.getKey() + "--> State " + dest.id);
                }
            }
            for (NFAState dest : s.eps) {
                lines.add("  --ε--> State " + dest.id);
            }
        }
        return lines;
    }

    // describe DFA states and transitions
    public static List<String> describeDFA(DFA dfa) {
        List<String> lines = new ArrayList<>();
        for (State state : dfa.states) {
            lines.add("State q" + state.id + (state.isAccept ? " (accept)" : ""));
            for (Map.Entry<Character, State> entry : state.transitions.entrySet()) {
                lines.add("  --" + entry.getKey() + "--> q" + entry.getValue().id);
            }
        }
        return lines;
    }
}
