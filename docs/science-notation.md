# Science notation

Recall stores science notation as ordinary Unicode text wrapped in lightweight markers:

- `[[chem:2Fe₂O₃(s) + 3CO(g) → 4Fe(s) + 3CO₂(g)]]`
- `[[math:x = (−b ± √(b² − 4ac)) / 2a]]`

The markers are visible while editing but hidden everywhere cards are read. The enclosed expression is rendered as one left-to-right directional unit, so surrounding Arabic cannot reorder its coefficients, element symbols, brackets, operators, states, variables, or units.

Imported marked expressions are normalized conservatively. Recall converts common ASCII arrows and comparisons, common symbol commands, explicit `_` subscripts and `^` powers, and chemical digits such as `H2O` to `H₂O`. Text outside a marker is never rewritten. Existing unmarked equations and measurements are detected and directionally isolated without changing the stored card.

Malformed or empty supported markers fail AI import with the affected card number. During manual editing malformed markup remains visible, so content is never silently discarded.

The AI prompt requires Unicode notation, one marker around the complete expression, balanced equations, correct phase labels, and no LaTeX, HTML, MathML, dollar delimiters, or ASCII arrows. This keeps the renderer deterministic and fully offline.
