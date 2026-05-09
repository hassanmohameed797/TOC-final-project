# 🔤 RegEx → NFA/DFA Converter

A Java implementation of the classic compiler pipeline that transforms a **regular expression** into a **Non-deterministic Finite Automaton (NFA)** and then into a **Deterministic Finite Automaton (DFA)** — a core concept in Theory of Computation.

---

## 📌 What It Does

This project walks through the full automata construction pipeline:

1. **Parse** a regular expression
2. **Build an NFA** using Thompson's Construction algorithm
3. **Convert the NFA to a DFA** using the Subset Construction algorithm
4. **Simulate** the resulting automaton to test whether strings are accepted or rejected

---

## 🧠 Concepts Covered

| Concept | Description |
|---|---|
| Regular Expressions | Pattern language for describing sets of strings |
| Thompson's Construction | Builds an ε-NFA from a regex |
| Subset Construction | Converts NFA to an equivalent DFA |
| ε-closure | Set of states reachable via epsilon transitions |
| String Acceptance | Simulates DFA to decide if input is in the language |

---

## 📂 Project Structure

```
TOC-final-project/
├── TOCproject/
│   ├── *.java        # All source files
└── README.md
```

---

## 💡 Example Usage

```
Input regex:  (a|b)*abb
Input string: aabb

→ NFA constructed via Thompson's Construction
→ NFA converted to DFA via Subset Construction
→ Result: ACCEPTED ✅
```

---

## 📚 Background

This project is a final project for a **Theory of Computation** course. It demonstrates how theoretical automata concepts translate directly into working software — the same pipeline used under the hood by real-world lexers and compilers (like `lex`/`flex`).

---

## 🛠️ Built With

- **Java** — core implementation language
- No external libraries — pure standard Java
