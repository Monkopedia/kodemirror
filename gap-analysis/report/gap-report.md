# Kodemirror Gap Analysis Report

Generated: 2026-03-22

## Summary

| Category | Pass | Fail | Timeout | Skip | Total |
|----------|------|------|---------|------|-------|
| Editing | 4 | 2 | 0 | 0 | 6 |
| Features | 2 | 2 | 0 | 0 | 4 |
| Navigation | 3 | 8 | 0 | 0 | 11 |
| Selection | 2 | 7 | 0 | 0 | 9 |
| Typing | 4 | 2 | 0 | 0 | 6 |
| Visual Comparison | 3 | 0 | 0 | 0 | 3 |
| **Total** | **18** | **21** | **0** | **0** | **39** |

## Failures

### Editing > Delete key

- **Status:** fail
- **Duration:** 1074ms
- **Error:**
  ```
  Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m
  
  Expected: [32m228[39m
  Received: [31m229[39m
  
    49 |     if (km) {
    50 |       const kmAfter = await km.getState();
  > 51 |       expect(kmAfter.docInfo.length).toBe(kmBefore!.docInfo.length - 1);
       |                                      ^
    52 |       expect(kmAfter.cursor.pos).toBe(kmBefore!.cursor.pos);
    53 |     }
    54 |   });
      at /home/jmonk/git/kodemirror/gap-anal...
  ```

### Editing > Ctrl+Y - redo

- **Status:** fail
- **Duration:** 2104ms
- **Error:**
  ```
  Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoContain[2m([22m[32mexpected[39m[2m) // indexOf[22m
  
  Expected substring: [32m"REDO_TEST"[39m
  Received string:    [31m"// Fibonacci sequence[39m
  [31mfunction fibonacci(n) {[39m
  [31m    if (n <= 1) return n;[39m
  [31m    return fibonacci(n - 1) + fibonacci(n - 2);[39m
  [31m}·[39m
  [31m// Print first 10 numbers[39m
  [31mfor (let i = 0; i < 10; i++) {[39m
  [31m    console.log(`fib(${i}) = ${fibonacci(i)}`);[39m
  [31m}RREREDREDOR...
  ```

### Features > initial cursor position

- **Status:** fail
- **Duration:** 1056ms
- **Error:**
  ```
  Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m
  
  Expected: [32m0[39m
  Received: [31m229[39m
  
    52 |     if (km) {
    53 |       const kmCursor = await km.getCursor();
  > 54 |       expect(kmCursor.pos).toBe(0);
       |                            ^
    55 |       expect(kmCursor.line).toBe(cm6Cursor.line);
    56 |       expect(kmCursor.col).toBe(cm6Cursor.col);
    57 |     }
      at /home/jmonk/git/kodemirror/gap-analysis/tests/featu...
  ```

### Features > bracket matching - cursor next to bracket

- **Status:** fail
- **Duration:** 2148ms
- **Error:**
  ```
  Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m
  
  Expected: [32m19[39m
  Received: [31m16[39m
  
    78 |     if (km) {
    79 |       const kmState = await km.getState();
  > 80 |       expect(kmState.cursor.col).toBe(cm6State.cursor.col);
       |                                  ^
    81 |       expect(kmState.cursor.line).toBe(cm6State.cursor.line);
    82 |     }
    83 |   });
      at /home/jmonk/git/kodemirror/gap-analysis/tests/feature...
  ```

### Navigation > arrow keys - right

- **Status:** fail
- **Duration:** 1207ms
- **Error:**
  ```
  Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m
  
  Expected: [32m1[39m
  Received: [31m0[39m
  
    19 |     if (km) {
    20 |       const kmCursor = await km.getCursor();
  > 21 |       expect(kmCursor.pos).toBe(cm6Cursor.pos);
       |                            ^
    22 |       expect(kmCursor.col).toBe(cm6Cursor.col);
    23 |     }
    24 |   });
      at /home/jmonk/git/kodemirror/gap-analysis/tests/navigation.spec.ts:21:28
  ```

### Navigation > arrow keys - down

- **Status:** fail
- **Duration:** 1087ms
- **Error:**
  ```
  Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m
  
  Expected: [32m2[39m
  Received: [31m10[39m
  
    59 |     if (km) {
    60 |       const kmCursor = await km.getCursor();
  > 61 |       expect(kmCursor.line).toBe(cm6Cursor.line);
       |                             ^
    62 |       expect(kmCursor.col).toBe(cm6Cursor.col);
    63 |     }
    64 |   });
      at /home/jmonk/git/kodemirror/gap-analysis/tests/navigation.spec.ts:61:29
  ```

### Navigation > arrow keys - up

- **Status:** fail
- **Duration:** 1161ms
- **Error:**
  ```
  Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m
  
  Expected: [32m9[39m
  Received: [31m10[39m
  
    75 |     if (km) {
    76 |       const kmCursor = await km.getCursor();
  > 77 |       expect(kmCursor.line).toBe(cm6Cursor.line);
       |                             ^
    78 |       expect(kmCursor.col).toBe(cm6Cursor.col);
    79 |     }
    80 |   });
      at /home/jmonk/git/kodemirror/gap-analysis/tests/navigation.spec.ts:77:29
  ```

### Navigation > Home key

- **Status:** fail
- **Duration:** 1174ms
- **Error:**
  ```
  Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m
  
  Expected: [32m0[39m
  Received: [31m4[39m
  
     96 |     if (km) {
     97 |       const kmCursor = await km.getCursor();
  >  98 |       expect(kmCursor.col).toBe(cm6Cursor.col);
        |                            ^
     99 |     }
    100 |   });
    101 |
      at /home/jmonk/git/kodemirror/gap-analysis/tests/navigation.spec.ts:98:28
  ```

### Navigation > Ctrl+Home - go to document start

- **Status:** fail
- **Duration:** 1036ms
- **Error:**
  ```
  Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m
  
  Expected: [32m0[39m
  Received: [31m229[39m
  
    128 |     if (km) {
    129 |       const kmCursor = await km.getCursor();
  > 130 |       expect(kmCursor.pos).toBe(0);
        |                            ^
    131 |     }
    132 |   });
    133 |
      at /home/jmonk/git/kodemirror/gap-analysis/tests/navigation.spec.ts:130:28
  ```

### Navigation > Ctrl+Right - word movement forward

- **Status:** fail
- **Duration:** 1063ms
- **Error:**
  ```
  Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m
  
  Expected: [32m2[39m
  Received: [31m0[39m
  
    159 |     if (km) {
    160 |       const kmCursor = await km.getCursor();
  > 161 |       expect(kmCursor.pos).toBe(cm6Cursor.pos);
        |                            ^
    162 |     }
    163 |   });
    164 |
      at /home/jmonk/git/kodemirror/gap-analysis/tests/navigation.spec.ts:161:28
  ```

### Navigation > Ctrl+Left - word movement backward

- **Status:** fail
- **Duration:** 1160ms
- **Error:**
  ```
  Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m
  
  Expected: [32m13[39m
  Received: [31m21[39m
  
    176 |     if (km) {
    177 |       const kmCursor = await km.getCursor();
  > 178 |       expect(kmCursor.pos).toBe(cm6Cursor.pos);
        |                            ^
    179 |     }
    180 |   });
    181 |
      at /home/jmonk/git/kodemirror/gap-analysis/tests/navigation.spec.ts:178:28
  ```

### Navigation > column memory across lines

- **Status:** fail
- **Duration:** 1180ms
- **Error:**
  ```
  Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m
  
  Expected: [32m21[39m
  Received: [31m25[39m
  
    196 |     if (km) {
    197 |       const kmCursor = await km.getCursor();
  > 198 |       expect(kmCursor.col).toBe(cm6Cursor.col);
        |                            ^
    199 |     }
    200 |   });
    201 | });
      at /home/jmonk/git/kodemirror/gap-analysis/tests/navigation.spec.ts:198:28
  ```

### Selection > Shift+Right - extend selection right

- **Status:** fail
- **Duration:** 1104ms
- **Error:**
  ```
  Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m
  
  Expected: [32mfalse[39m
  Received: [31mtrue[39m
  
    18 |     if (km) {
    19 |       const kmSel = await km.getSelection();
  > 20 |       expect(kmSel.empty).toBe(false);
       |                           ^
    21 |       expect(kmSel.anchor).toBe(cm6Sel.anchor);
    22 |       expect(kmSel.head).toBe(cm6Sel.head);
    23 |     }
      at /home/jmonk/git/kodemirror/gap-analysis/tests/sele...
  ```

### Selection > Shift+Left - extend selection left

- **Status:** fail
- **Duration:** 2245ms
- **Error:**
  ```
  Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m
  
  Expected: [32m4[39m
  Received: [31m5[39m
  
    39 |       const kmSel = await km.getSelection();
    40 |       expect(kmSel.anchor).toBe(cm6Sel.anchor);
  > 41 |       expect(kmSel.head).toBe(cm6Sel.head);
       |                          ^
    42 |     }
    43 |   });
    44 |
      at /home/jmonk/git/kodemirror/gap-analysis/tests/selection.spec.ts:41:26
  ```

### Selection > Shift+Down - extend selection down

- **Status:** fail
- **Duration:** 1185ms
- **Error:**
  ```
  Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m
  
  Expected: [32m22[39m
  Received: [31m0[39m
  
    53 |       const kmSel = await km.getSelection();
    54 |       expect(kmSel.anchor).toBe(cm6Sel.anchor);
  > 55 |       expect(kmSel.head).toBe(cm6Sel.head);
       |                          ^
    56 |     }
    57 |   });
    58 |
      at /home/jmonk/git/kodemirror/gap-analysis/tests/selection.spec.ts:55:26
  ```

### Selection > Ctrl+Shift+Right - select word right

- **Status:** fail
- **Duration:** 1149ms
- **Error:**
  ```
  Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m
  
  Expected: [32m2[39m
  Received: [31m0[39m
  
    67 |       const kmSel = await km.getSelection();
    68 |       expect(kmSel.anchor).toBe(cm6Sel.anchor);
  > 69 |       expect(kmSel.head).toBe(cm6Sel.head);
       |                          ^
    70 |     }
    71 |   });
    72 |
      at /home/jmonk/git/kodemirror/gap-analysis/tests/selection.spec.ts:69:26
  ```

### Selection > Ctrl+Shift+Left - select word left

- **Status:** fail
- **Duration:** 1218ms
- **Error:**
  ```
  Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m
  
  Expected: [32m13[39m
  Received: [31m21[39m
  
    84 |       const kmSel = await km.getSelection();
    85 |       expect(kmSel.anchor).toBe(cm6Sel.anchor);
  > 86 |       expect(kmSel.head).toBe(cm6Sel.head);
       |                          ^
    87 |     }
    88 |   });
    89 |
      at /home/jmonk/git/kodemirror/gap-analysis/tests/selection.spec.ts:86:26
  ```

### Selection > Shift+Home - select to line start

- **Status:** fail
- **Duration:** 2250ms
- **Error:**
  ```
  Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m
  
  Expected: [32m0[39m
  Received: [31m10[39m
  
    103 |       const kmSel = await km.getSelection();
    104 |       expect(kmSel.anchor).toBe(cm6Sel.anchor);
  > 105 |       expect(kmSel.head).toBe(cm6Sel.head);
        |                          ^
    106 |     }
    107 |   });
    108 |
      at /home/jmonk/git/kodemirror/gap-analysis/tests/selection.spec.ts:105:26
  ```

### Selection > typing replaces selection

- **Status:** fail
- **Duration:** 2184ms
- **Error:**
  ```
  Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoContain[2m([22m[32mexpected[39m[2m) // indexOf[22m
  
  Expected substring: [32m"replaced"[39m
  Received string:    [31m"// Fibonacci sequence[39m
  [31mfunction fibonacci(n) {[39m
  [31m    if (n <= 1) return n;[39m
  [31m    return fibonacci(n - 1) + fibonacci(n - 2);[39m
  [31m}·[39m
  [31m// Print first 10 numbers[39m
  [31mfor (let i = 0; i < 10; i++) {[39m
  [31m    console.log(`fib(${i}) = ${fibonacci(i)}`);[39m
  [31m}rrerepreplaa...
  ```

### Typing > special characters - brackets

- **Status:** fail
- **Duration:** 2091ms
- **Error:**
  ```
  Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m
  
  Expected: [32mtrue[39m
  Received: [31mfalse[39m
  
    61 |       expect(kmState.doc.includes("(")).toBe(true);
    62 |       const kmHasClose = kmState.doc.includes("()");
  > 63 |       expect(kmHasClose).toBe(cm6HasClose);
       |                          ^
    64 |     }
    65 |   });
    66 |
      at /home/jmonk/git/kodemirror/gap-analysis/tests/typing.spec.ts:63:26
  ```

### Typing > Enter key and auto-indent

- **Status:** fail
- **Duration:** 2147ms
- **Error:**
  ```
  Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m
  
  Expected: [32m2[39m
  Received: [31m0[39m
  
    104 |     if (km) {
    105 |       const kmState = await km.getState();
  > 106 |       expect(kmState.cursor.col).toBe(cm6State.cursor.col);
        |                                  ^
    107 |     }
    108 |   });
    109 |
      at /home/jmonk/git/kodemirror/gap-analysis/tests/typing.spec.ts:106:34
  ```

## Suggested TODO Items

Add these to `docs/TODO.md` for tracking and resolution:

### 1. Fix gap: Editing - Delete key
- Gap analysis test `Delete key` in category `Editing` failed.
- Error: Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m

### 2. Fix gap: Editing - Ctrl+Y - redo
- Gap analysis test `Ctrl+Y - redo` in category `Editing` failed.
- Error: Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoContain[2m([22m[32mexpected[39m[2m) // indexOf[22m

### 3. Fix gap: Features - initial cursor position
- Gap analysis test `initial cursor position` in category `Features` failed.
- Error: Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m

### 4. Fix gap: Features - bracket matching - cursor next to bracket
- Gap analysis test `bracket matching - cursor next to bracket` in category `Features` failed.
- Error: Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m

### 5. Fix gap: Navigation - arrow keys - right
- Gap analysis test `arrow keys - right` in category `Navigation` failed.
- Error: Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m

### 6. Fix gap: Navigation - arrow keys - down
- Gap analysis test `arrow keys - down` in category `Navigation` failed.
- Error: Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m

### 7. Fix gap: Navigation - arrow keys - up
- Gap analysis test `arrow keys - up` in category `Navigation` failed.
- Error: Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m

### 8. Fix gap: Navigation - Home key
- Gap analysis test `Home key` in category `Navigation` failed.
- Error: Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m

### 9. Fix gap: Navigation - Ctrl+Home - go to document start
- Gap analysis test `Ctrl+Home - go to document start` in category `Navigation` failed.
- Error: Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m

### 10. Fix gap: Navigation - Ctrl+Right - word movement forward
- Gap analysis test `Ctrl+Right - word movement forward` in category `Navigation` failed.
- Error: Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m

### 11. Fix gap: Navigation - Ctrl+Left - word movement backward
- Gap analysis test `Ctrl+Left - word movement backward` in category `Navigation` failed.
- Error: Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m

### 12. Fix gap: Navigation - column memory across lines
- Gap analysis test `column memory across lines` in category `Navigation` failed.
- Error: Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m

### 13. Fix gap: Selection - Shift+Right - extend selection right
- Gap analysis test `Shift+Right - extend selection right` in category `Selection` failed.
- Error: Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m

### 14. Fix gap: Selection - Shift+Left - extend selection left
- Gap analysis test `Shift+Left - extend selection left` in category `Selection` failed.
- Error: Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m

### 15. Fix gap: Selection - Shift+Down - extend selection down
- Gap analysis test `Shift+Down - extend selection down` in category `Selection` failed.
- Error: Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m

### 16. Fix gap: Selection - Ctrl+Shift+Right - select word right
- Gap analysis test `Ctrl+Shift+Right - select word right` in category `Selection` failed.
- Error: Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m

### 17. Fix gap: Selection - Ctrl+Shift+Left - select word left
- Gap analysis test `Ctrl+Shift+Left - select word left` in category `Selection` failed.
- Error: Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m

### 18. Fix gap: Selection - Shift+Home - select to line start
- Gap analysis test `Shift+Home - select to line start` in category `Selection` failed.
- Error: Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m

### 19. Fix gap: Selection - typing replaces selection
- Gap analysis test `typing replaces selection` in category `Selection` failed.
- Error: Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoContain[2m([22m[32mexpected[39m[2m) // indexOf[22m

### 20. Fix gap: Typing - special characters - brackets
- Gap analysis test `special characters - brackets` in category `Typing` failed.
- Error: Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m

### 21. Fix gap: Typing - Enter key and auto-indent
- Gap analysis test `Enter key and auto-indent` in category `Typing` failed.
- Error: Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m

## Passing Tests

- [x] Editing > Backspace (1206ms)
- [x] Editing > Ctrl+Backspace - delete word backward (1136ms)
- [x] Editing > Ctrl+Z - undo (1151ms)
- [x] Editing > bracket auto-delete (1246ms)
- [x] Features > Ctrl+F opens search (1119ms)
- [x] Features > initial document matches (1005ms)
- [x] Navigation > arrow keys - left (1172ms)
- [x] Navigation > End key (1119ms)
- [x] Navigation > Ctrl+End - go to document end (1094ms)
- [x] Selection > Shift+End - select to line end (1174ms)
- [x] Selection > Ctrl+A - select all (992ms)
- [x] Typing > single character (1168ms)
- [x] Typing > word input (1061ms)
- [x] Typing > special characters - quotes (1175ms)
- [x] Typing > Tab key (1078ms)
- [x] Visual Comparison > initial render - side by side (1014ms)
- [x] Visual Comparison > after typing - visual state (2113ms)
- [x] Visual Comparison > with selection - visual state (1065ms)
