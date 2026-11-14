VTab lets server operators hide a player from the tab list with one command, without changing anything else about that player.

## What it does

- `/vtab` hides you from the tab list. Run it again to show yourself.
- `/vtab <player>` does the same for another online player.
- When someone is hidden, everyone sees the usual "left the game" message. When they are shown again, everyone sees "joined the game".
- While hidden, that player's joins and leaves produce no chat message. They also stay off the tab list when they reconnect.
- The hidden state is saved in the world (`vtab_hidden.txt`) and survives server restarts.
- Command feedback is available in English, Polish and German, based on the language of whoever runs the command.

The hidden player is not removed from the world. They keep their skin, stay visible in-game and can play normally. Only the tab list entry and the join/leave chat messages are affected.

## Good to know

- **Server-side only.** Players don't need to install the mod and can join with a vanilla client. Install it on the server only.
- **Requires Fabric API.**
- **Operators only.** Both command forms need permission level 2 (gamemaster).
- A hidden player also sees their own fake "left the game" message.
- Matching is done by player name, so if a hidden player renames their account while offline, their first join afterwards may show a join message.
- Join/leave lines in the server console and logs are not removed, only the chat messages.

## Compatibility

- Minecraft 26.3
- Fabric Loader 0.20.1 or newer
- NeoForge 26.3.0.102 or newer
- Java 25

## License

VTab is released under the MIT License. You can use it freely on any server or in any modpack. You can mention the author; I’d appreciate that.
