import java.util.*;

public class RegexToDFA {

    private static final char CONCAT = '.';
    private static class NFAState {
        int id;
        Map<Character, Set<NFAState>> trans = new HashMap<>();
        Set<NFAState> eps = new HashSet<>();

        NFAState(int id) { this.id = id; }
        void t(char s, NFAState n) { trans.computeIfAbsent(s, k -> new HashSet<>()).add(n); }
        void e(NFAState n) { eps.add(n); }
    }

    private static class Frag {
        NFAState s, e;
        Frag(NFAState s, NFAState e) { this.s = s; this.e = e; }
    }

    private static class NFA {
        NFAState s, a;
        Set<Character> alpha;
        NFA(NFAState s, NFAState a, Set<Character> alpha) { this.s = s; this.a = a; this.alpha = alpha; }
    }

    public static DFA buildFromRegex(String regex) {
        if (regex == null || regex.trim().isEmpty()) throw new IllegalArgumentException("Regex cannot be empty.");
        String normalized = regex.replaceAll("\\s+", "");
        if (normalized.isEmpty()) throw new IllegalArgumentException("Regex cannot be empty.");
        return convertNFAToDFA(buildNFA(toPostfix(addConcatOperator(normalized))));
    }

    private static String addConcatOperator(String regex) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < regex.length(); i++) {
            char current = regex.charAt(i);
            out.append(current);
            if (i + 1 < regex.length() && needsConcat(current, regex.charAt(i + 1))) out.append(CONCAT);
        }
        return out.toString();
    }

    private static boolean needsConcat(char left, char right) {
        return (isLiteral(left) || left == ')' || left == '*') && (isLiteral(right) || right == '(');
    }

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

    private static NFA buildNFA(String postfix) {
        Deque<Frag> st = new ArrayDeque<>();
        Set<Character> alpha = new HashSet<>();
        int id = 0;
        for (char tok : postfix.toCharArray()) {
            if (isLiteral(tok)) {
                NFAState a = new NFAState(id++), b = new NFAState(id++);
                a.t(tok, b);
                st.push(new Frag(a, b));
                alpha.add(tok);
            } else if (tok == CONCAT) {
                if (st.size() < 2) throw new IllegalArgumentException("Invalid regex: malformed concatenation.");
                Frag r = st.pop(), l = st.pop();
                l.e.e(r.s);
                st.push(new Frag(l.s, r.e));
            } else if (tok == '|' || tok == 'U') {
                if (st.size() < 2) throw new IllegalArgumentException("Invalid regex: malformed union.");
                Frag r = st.pop(), l = st.pop();
                NFAState s = new NFAState(id++), e = new NFAState(id++);
                s.e(l.s); s.e(r.s); l.e.e(e); r.e.e(e);
                st.push(new Frag(s, e));
            } else if (tok == '*') {
                if (st.isEmpty()) throw new IllegalArgumentException("Invalid regex: malformed Kleene star.");
                Frag f = st.pop();
                NFAState s = new NFAState(id++), e = new NFAState(id++);
                s.e(f.s); s.e(e); f.e.e(f.s); f.e.e(e);
                st.push(new Frag(s, e));
            } else {
                throw new IllegalArgumentException("Unsupported regex token: " + tok);
            }
        }
        if (st.size() != 1) throw new IllegalArgumentException("Invalid regex: unresolved expression.");
        Frag r = st.pop();
        return new NFA(r.s, r.e, alpha);
    }

    private static DFA convertNFAToDFA(NFA nfa) {
        DFA dfa = new DFA();
        Map<Set<NFAState>, State> map = new HashMap<>();
        Queue<Set<NFAState>> q = new ArrayDeque<>();
        Set<NFAState> start = epsilonClosure(Collections.singleton(nfa.s));
        State s0 = new State(0, containsAccept(start, nfa.a));
        map.put(start, s0);
        q.add(start);
        dfa.addState(s0);
        int id = 1;
        while (!q.isEmpty()) {
            Set<NFAState> cur = q.poll();
            State ds = map.get(cur);
            for (char sym : nfa.alpha) {
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
            }
        }
        return dfa;
    }

    private static Set<NFAState> epsilonClosure(Set<NFAState> states) {
        Set<NFAState> c = new HashSet<>(states);
        Deque<NFAState> st = new ArrayDeque<>(states);
        while (!st.isEmpty()) {
            NFAState cur = st.pop();
            for (NFAState n : cur.eps) if (c.add(n)) st.push(n);
        }
        return c;
    }

    private static Set<NFAState> move(Set<NFAState> states, char symbol) {
        Set<NFAState> r = new HashSet<>();
        for (NFAState st : states) {
            Set<NFAState> t = st.trans.get(symbol);
            if (t != null) r.addAll(t);
        }
        return r;
    }

    private static boolean containsAccept(Set<NFAState> states, NFAState accept) {
        return states.contains(accept);
    }

    private static boolean isOperator(char c) {
        return c == '|' || c == '*' || c == CONCAT || c == 'U';
    }

    private static int precedence(char operator) {
        if (operator == '*') return 3;
        if (operator == CONCAT) return 2;
        if (operator == '|' || operator == 'U') return 1;
        return -1;
    }

    private static boolean isLiteral(char c) {
        return Character.isLetterOrDigit(c) && c != 'U';
    }

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