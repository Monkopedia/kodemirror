# Kodemirror Gap Analysis Report

Generated: 2026-03-22

## Summary

| Category | Pass | Fail | Timeout | Skip | Total |
|----------|------|------|---------|------|-------|
| Editing | 5 | 1 | 0 | 0 | 6 |
| Features | 4 | 0 | 0 | 0 | 4 |
| Navigation | 10 | 1 | 0 | 0 | 11 |
| Selection | 8 | 1 | 0 | 0 | 9 |
| Typing | 3 | 3 | 0 | 0 | 6 |
| Visual Comparison | 3 | 0 | 0 | 0 | 3 |
| **Total** | **33** | **6** | **0** | **0** | **39** |

## Failures

### Editing > Ctrl+Y - redo

- **Status:** fail
- **Duration:** 2641ms
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
  [31m}REEDEDOEDO_...
  ```

### Navigation > column memory across lines

- **Status:** fail
- **Duration:** 2096ms
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

### Selection > typing replaces selection

- **Status:** fail
- **Duration:** 2221ms
- **Error:**
  ```
  Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoContain[2m([22m[32mexpected[39m[2m) // indexOf[22m
  
  Expected substring: [32m"replaced"[39m
  Received string:    [31m"reepepleplaeplaceplaceeplaced Fibonacci sequence[39m
  [31mfunction fibonacci(n) {[39m
  [31m    if (n <= 1) return n;[39m
  [31m    return fibonacci(n - 1) + fibonacci(n - 2);[39m
  [31m}·[39m
  [31m// Print first 10 numbers[39m
  [31mfor (let i = 0; i < 10; i++) {[39m
  [31m    console.log(`fib(${i}) = ${fibonacci(i)}...
  ```

### Typing > special characters - brackets

- **Status:** fail
- **Duration:** 2610ms
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
- **Duration:** 2701ms
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

### Typing > Tab key

- **Status:** fail
- **Duration:** 1658ms
- **Error:**
  ```
  Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m
  
  Expected: [32m0[39m
  Received: [31m1[39m
  
    124 |     if (km) {
    125 |       const kmCursor = await km.getCursor();
  > 126 |       expect(kmCursor.col).toBe(cm6Col);
        |                            ^
    127 |     }
    128 |   });
    129 | });
      at /home/jmonk/git/kodemirror/gap-analysis/tests/typing.spec.ts:126:28
  ```

## Suggested TODO Items

Add these to `docs/TODO.md` for tracking and resolution:

### 1. Fix gap: Editing - Ctrl+Y - redo
- Gap analysis test `Ctrl+Y - redo` in category `Editing` failed.
- Error: Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoContain[2m([22m[32mexpected[39m[2m) // indexOf[22m

### 2. Fix gap: Navigation - column memory across lines
- Gap analysis test `column memory across lines` in category `Navigation` failed.
- Error: Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m

### 3. Fix gap: Selection - typing replaces selection
- Gap analysis test `typing replaces selection` in category `Selection` failed.
- Error: Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoContain[2m([22m[32mexpected[39m[2m) // indexOf[22m

### 4. Fix gap: Typing - special characters - brackets
- Gap analysis test `special characters - brackets` in category `Typing` failed.
- Error: Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m

### 5. Fix gap: Typing - Enter key and auto-indent
- Gap analysis test `Enter key and auto-indent` in category `Typing` failed.
- Error: Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m

### 6. Fix gap: Typing - Tab key
- Gap analysis test `Tab key` in category `Typing` failed.
- Error: Error: [2mexpect([22m[31mreceived[39m[2m).[22mtoBe[2m([22m[32mexpected[39m[2m) // Object.is equality[22m

## Passing Tests

- [x] Editing > Backspace (1195ms)
- [x] Editing > Delete key (1030ms)
- [x] Editing > Ctrl+Backspace - delete word backward (1088ms)
- [x] Editing > Ctrl+Z - undo (1594ms)
- [x] Editing > bracket auto-delete (1698ms)
- [x] Features > Ctrl+F opens search (1584ms)
- [x] Features > initial document matches (997ms)
- [x] Features > initial cursor position (1002ms)
- [x] Features > bracket matching - cursor next to bracket (1852ms)
- [x] Navigation > arrow keys - right (1016ms)
- [x] Navigation > arrow keys - left (1047ms)
- [x] Navigation > arrow keys - down (1033ms)
- [x] Navigation > arrow keys - up (1496ms)
- [x] Navigation > Home key (1135ms)
- [x] Navigation > End key (1038ms)
- [x] Navigation > Ctrl+Home - go to document start (1522ms)
- [x] Navigation > Ctrl+End - go to document end (1032ms)
- [x] Navigation > Ctrl+Right - word movement forward (1031ms)
- [x] Navigation > Ctrl+Left - word movement backward (1090ms)
- [x] Selection > Shift+Right - extend selection right (1062ms)
- [x] Selection > Shift+Left - extend selection left (1156ms)
- [x] Selection > Shift+Down - extend selection down (1009ms)
- [x] Selection > Ctrl+Shift+Right - select word right (1036ms)
- [x] Selection > Ctrl+Shift+Left - select word left (1060ms)
- [x] Selection > Shift+Home - select to line start (1205ms)
- [x] Selection > Shift+End - select to line end (1047ms)
- [x] Selection > Ctrl+A - select all (1030ms)
- [x] Typing > single character (1619ms)
- [x] Typing > word input (1594ms)
- [x] Typing > special characters - quotes (1655ms)
- [x] Visual Comparison > initial render - side by side (1044ms)
- [x] Visual Comparison > after typing - visual state (1673ms)
- [x] Visual Comparison > with selection - visual state (1070ms)
