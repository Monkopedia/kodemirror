/*
 * Copyright 2026 Jason Monk
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.monkopedia.kodemirror.samples.showcase

object SampleDocs {

    val javascript = """
        // Fibonacci sequence
        function fibonacci(n) {
            if (n <= 1) return n;
            return fibonacci(n - 1) + fibonacci(n - 2);
        }

        // Print first 10 numbers
        for (let i = 0; i < 10; i++) {
            console.log(`fib(${'$'}{i}) = ${'$'}{fibonacci(i)}`);
        }
    """.trimIndent()

    val python = """
        import math

        def sieve_of_eratosthenes(limit):
            # Find all primes up to limit.
            is_prime = [True] * (limit + 1)
            is_prime[0] = is_prime[1] = False
            for i in range(2, int(math.sqrt(limit)) + 1):
                if is_prime[i]:
                    for j in range(i * i, limit + 1, i):
                        is_prime[j] = False
            return [i for i in range(limit + 1) if is_prime[i]]

        primes = sieve_of_eratosthenes(100)
        print(f"Found {len(primes)} primes: {primes}")
    """.trimIndent()

    val rust = """
        use std::collections::HashMap;

        fn word_count(text: &str) -> HashMap<&str, usize> {
            let mut counts = HashMap::new();
            for word in text.split_whitespace() {
                *counts.entry(word).or_insert(0) += 1;
            }
            counts
        }

        fn main() {
            let text = "hello world hello rust world";
            let counts = word_count(text);
            for (word, count) in &counts {
                println!("{word}: {count}");
            }
        }
    """.trimIndent()

    val html = """
        <!DOCTYPE html>
        <html lang="en">
        <head>
            <meta charset="UTF-8">
            <title>Hello KodeMirror</title>
            <style>
                body { font-family: sans-serif; margin: 2rem; }
                .highlight { color: #e06c75; font-weight: bold; }
            </style>
        </head>
        <body>
            <h1>Hello, <span class="highlight">World</span>!</h1>
            <script>
                document.querySelector('h1').addEventListener('click', () => {
                    alert('Clicked!');
                });
            </script>
        </body>
        </html>
    """.trimIndent()

    val css = """
        :root {
            --primary: #61afef;
            --bg: #282c34;
            --fg: #abb2bf;
        }

        body {
            background-color: var(--bg);
            color: var(--fg);
            font-family: 'Segoe UI', sans-serif;
            line-height: 1.6;
        }

        .container {
            max-width: 800px;
            margin: 0 auto;
            padding: 2rem;
        }

        a { color: var(--primary); text-decoration: none; }
        a:hover { text-decoration: underline; }
    """.trimIndent()

    val json = """
        {
            "name": "kodemirror",
            "version": "1.0.0",
            "description": "Kotlin Multiplatform code editor",
            "features": [
                "syntax-highlighting",
                "code-folding",
                "autocompletion",
                "linting"
            ],
            "platforms": {
                "jvm": true,
                "js": true,
                "wasmJs": true
            }
        }
    """.trimIndent()

    val yaml = """
        apiVersion: apps/v1
        kind: Deployment
        metadata:
          name: kodemirror-app
          labels:
            app: kodemirror
        spec:
          replicas: 3
          selector:
            matchLabels:
              app: kodemirror
          template:
            metadata:
              labels:
                app: kodemirror
            spec:
              containers:
                - name: editor
                  image: kodemirror:latest
                  ports:
                    - containerPort: 8080
    """.trimIndent()

    val markdown = """
        # KodeMirror

        A **Kotlin Multiplatform** code editor built on Compose.

        ## Features

        - Syntax highlighting for 20+ languages
        - Configurable themes (One Dark, GitHub Light, Dracula)
        - Code folding and bracket matching
        - Autocompletion and linting

        ```kotlin
        fun main() {
            println("Hello from KodeMirror!")
        }
        ```

        > Built with love using Compose Multiplatform.
    """.trimIndent()

    val go = """
        package main

        import (
            "fmt"
            "strings"
        )

        func reverseWords(s string) string {
            words := strings.Fields(s)
            for i, j := 0, len(words)-1; i < j; i, j = i+1, j-1 {
                words[i], words[j] = words[j], words[i]
            }
            return strings.Join(words, " ")
        }

        func main() {
            result := reverseWords("hello world from go")
            fmt.Println(result)
        }
    """.trimIndent()

    val java = """
        import java.util.stream.IntStream;

        public class FizzBuzz {
            public static void main(String[] args) {
                IntStream.rangeClosed(1, 30).forEach(i -> {
                    String result = "";
                    if (i % 3 == 0) result += "Fizz";
                    if (i % 5 == 0) result += "Buzz";
                    System.out.println(result.isEmpty() ? i : result);
                });
            }
        }
    """.trimIndent()

    val bidi = """
        // English comment
        let greeting = "Hello World";

        // تعليق بالعربية
        let تحية = "مرحباً بالعالم";

        // Mixed: English and عربي together
        console.log(greeting + " / " + تحية);
    """.trimIndent()

    val mergeOriginal = """
        function greet(name) {
            console.log("Hello, " + name);
        }

        function add(a, b) {
            return a + b;
        }

        greet("World");
        console.log(add(2, 3));
    """.trimIndent()

    val mergeModified = """
        function greet(name, greeting = "Hello") {
            console.log(greeting + ", " + name + "!");
        }

        function add(a, b) {
            return a + b;
        }

        function multiply(a, b) {
            return a * b;
        }

        greet("World");
        greet("KodeMirror", "Welcome");
        console.log(add(2, 3));
        console.log(multiply(4, 5));
    """.trimIndent()

    val collabInitial = """
        // Collaborative document
        function hello() {
            return "Hello from editor";
        }
    """.trimIndent()

    val largeDocument: String by lazy {
        buildString {
            repeat(10_000) { i ->
                appendLine("// Line ${i + 1}: The quick brown fox jumps over the lazy dog")
            }
        }
    }
}
