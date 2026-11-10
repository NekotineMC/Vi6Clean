# minecraft-rcon MCP server

Lets Claude Code drive the Vi6 dev server: run commands over RCON and read its `latest.log`. Registered in the repository's `.mcp.json`.

Tools:

- `test_connection` — log in over RCON and say "Rcon test connection" in chat.
- `execute_command` — run a command as the console and return its reply (`command`, with or without the leading slash; `announce`, default `true`, first broadcasts `[Claude] /<command>` in chat).
- `read_logs` — numbered log lines; `fromLine` (1-based), `grep` (case-insensitive regex) and `lastNLines` narrow it down.
- `log_size` — the log's line count, a bookmark for `read_logs fromLine`.

## Setup

1. Node 20 or later. In this folder: `npm install`.
2. Create `config.local.json` here (gitignored, it holds the RCON password):

   ```json
   {
     "host": "127.0.0.1",
     "port": 25575,
     "password": "<rcon.password from the dev server's server.properties>",
     "logFile": "<dev server folder>/logs/latest.log"
   }
   ```

   The server needs `enable-rcon=true` in its `server.properties`. Environment variables `MC_RCON_HOST`, `MC_RCON_PORT`, `MC_RCON_PASSWORD` and `MC_LOG_FILE` override the file.
3. Start a new Claude Code session in the repository and approve the `minecraft-rcon` project server (or add `"enabledMcpjsonServers": ["minecraft-rcon"]` to `.claude/settings.local.json`).
