# Agent instructions

Source: https://github.com/ciembor/agent-rules-books/blob/main/clean-code/clean-code.nano.md

# OBEY Clean Code by Robert C. Martin

## When to use

Use when you need a small always-on bias toward readable, low-surprise code.

## Primary bias to correct

Working code is not automatically clean code.

## Decision rules

- Preserve behavior, write for the next reader, and leave touched code cleaner within scope.
- Write for local reasoning and use precise names with one term per concept.
- Split boolean flags, mixed abstraction levels, and hidden side effects out of functions.
- Separate commands from queries and keep parameters small and meaningful.
- Keep the happy path readable; make invalid states, errors, and cleanup explicit instead of implicit.
- Use comments only for rationale or contracts, not to explain confusing code.
- When touching code, remove the smell most likely to make the next change risky or unclear.

## Trigger rules

- When a function both mutates and answers, split it.
- When a comment explains the flow, simplify the code first.
- When async, concurrency, or framework quirks spread the change, reduce shared mutable state and add the right boundary instead of more branching.

## Final checklist

- Local reasoning preserved?
- Clear names?
- Clear mutation boundaries?
- One smell removed?


## Pull request descriptions

Include screenshots of the game in PR descriptions when relevant to the changes. For changes to existing visuals, include before-and-after screenshots where practical. Briefly caption screenshots to identify what they demonstrate. Screenshots are optional for changes without a relevant visual effect. Use accessible image attachments or links, not local filesystem paths.
