# Collaborative Editing

The `:collab` module provides the client-side infrastructure for real-time
collaborative editing using operational transformation (OT).

## How It Works

Collaborative editing in Kodemirror follows a central-authority model:

1. A **server** (the authority) maintains the canonical document and a
   version counter.
2. Each **client** tracks its own version and sends local changes to the
   server.
3. The server applies changes, increments the version, and broadcasts
   updates to all clients.
4. Clients receive remote updates and **rebase** any unconfirmed local
   changes on top of them.

The `collab()` extension handles the rebasing automatically. Your job is
to wire up the transport layer (WebSocket, HTTP polling, etc.).

## Setup

Add the `:collab` dependency and install the extension:

```kotlin
val session = rememberEditorSession(
    doc = initialDoc,
    extensions = basicSetup + collab(CollabConfig(startVersion = serverVersion))
)
```

### CollabConfig

| Property | Type | Default | Description |
|----------|------|---------|-------------|
| `startVersion` | `Int` | `0` | Document version when the editor was initialized |
| `clientID` | `String?` | `null` | Unique client identifier (auto-generated if null) |
| `sharedEffects` | `((Transaction) -> List<StateEffect<*>>)?` | `null` | Extract effects to share with other clients |

## Sending Local Changes

After each transaction, call `sendableUpdates()` to get pending changes:

```kotlin
val updates = sendableUpdates(session.state)
if (updates.isNotEmpty()) {
    val version = getSyncedVersion(session.state)
    sendToServer(version, updates)
}
```

Each `SendableUpdate` contains the `ChangeSet`, the `clientID`, any
shared `effects`, and the originating `Transaction`.

## Receiving Remote Changes

When the server broadcasts updates from other clients:

```kotlin
fun onServerUpdate(updates: List<Update>) {
    val spec = receiveUpdates(session.state, updates)
    session.dispatch(spec)
}
```

`receiveUpdates()` returns a `TransactionSpec` that, when dispatched,
applies the remote changes and rebases any unconfirmed local changes
on top of them.

## Querying State

```kotlin
val version = getSyncedVersion(session.state)  // Current synced version
val id = getClientID(session.state)            // This client's ID
```

## Server-Side Rebasing

If your server rejects updates because they were based on a stale
version, use `rebaseUpdates()` on the server to transform them:

```kotlin
val rebased = rebaseUpdates(rejectedUpdates, acceptedSinceVersion)
```

This is useful when clients submit updates concurrently and the server
needs to reconcile the order.

## Sharing Custom Effects

Some state effects (like cursor positions, annotations, or markers)
should be shared across clients. Use `sharedEffects` in the config:

```kotlin
val markEffect = StateEffect.define<MarkSpec>()

val session = rememberEditorSession(
    doc = doc,
    extensions = basicSetup + collab(CollabConfig(
        startVersion = version,
        sharedEffects = { tr ->
            tr.effects.filter { it.isType(markEffect) }
        }
    ))
)
```

Shared effects are included in `SendableUpdate.effects` and delivered
to other clients via `Update.effects`.

## Undo/Redo Integration

The `collab()` extension works with the `history()` extension. Each
client's undo history is independent — undoing reverts only that
client's own changes, even when interleaved with remote edits.

```kotlin
val session = rememberEditorSession(
    doc = doc,
    extensions = basicSetup + collab(config) + history()
)
// Undo only reverts this client's changes
```

## Example: Polling Architecture

A minimal polling-based collaboration client:

```kotlin
class CollabClient(
    private val session: EditorSession,
    private val serverUrl: String
) {
    suspend fun pushPull() {
        // 1. Push local changes
        val updates = sendableUpdates(session.state)
        if (updates.isNotEmpty()) {
            pushToServer(serverUrl, updates, getSyncedVersion(session.state))
        }

        // 2. Pull remote changes
        val version = getSyncedVersion(session.state)
        val remote = pullFromServer(serverUrl, version)
        if (remote.isNotEmpty()) {
            session.dispatch(receiveUpdates(session.state, remote))
        }
    }
}
```

For production use, consider a WebSocket-based transport for lower
latency.

## API Reference

| Function | Description |
|----------|-------------|
| `collab(config)` | Install collaborative editing extension |
| `sendableUpdates(state)` | Get pending local updates to send |
| `receiveUpdates(state, updates)` | Build a `TransactionSpec` from remote updates |
| `getSyncedVersion(state)` | Get the current synced version number |
| `getClientID(state)` | Get this client's ID |
| `rebaseUpdates(updates, over)` | Rebase out-of-date updates (server-side) |

---

*Based on the [CodeMirror Collaborative Editing](https://codemirror.net/examples/collab/)
approach.*
