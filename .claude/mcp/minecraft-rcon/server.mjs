// MCP server for the Vi6 dev server: run commands over RCON and read its log.
// Machine-specific settings live in config.local.json next to this file (gitignored; see README.md).
// Environment variables override it: MC_RCON_HOST, MC_RCON_PORT, MC_RCON_PASSWORD, MC_LOG_FILE.
import { existsSync, readFileSync } from "node:fs";
import { readFile } from "node:fs/promises";
import { McpServer } from "@modelcontextprotocol/sdk/server/mcp.js";
import { StdioServerTransport } from "@modelcontextprotocol/sdk/server/stdio.js";
import rconModule from "rcon-srcds";
import { z } from "zod";

const Rcon = rconModule.default ?? rconModule;

const localConfigPath = new URL("./config.local.json", import.meta.url);
const local = existsSync(localConfigPath) ? JSON.parse(readFileSync(localConfigPath, "utf8")) : {};

const host = process.env.MC_RCON_HOST || local.host || "127.0.0.1";
const port = Number(process.env.MC_RCON_PORT || local.port || 25575);
const password = process.env.MC_RCON_PASSWORD || local.password;
const logFile = process.env.MC_LOG_FILE || local.logFile;

// Minecraft colour/format codes (§x) are noise in tool output
const stripFormatting = (text) => text.replace(/§./g, "");

async function withRcon(fn) {
	if (!password) {
		throw new Error("MC_RCON_PASSWORD is not set: add it to .claude/mcp/minecraft-rcon/config.local.json (see README.md)");
	}
	const server = new Rcon({ host, port, encoding: "utf8", timeout: 5000 });
	try {
		await server.authenticate(password);
		return await fn(server);
	} catch (e) {
		throw new Error(`RCON ${host}:${port} failed: ${e instanceof Error ? e.message : String(e)}`);
	} finally {
		try {
			server.disconnect();
		} catch {
			// already closed
		}
	}
}

async function readLogLines() {
	if (!logFile) {
		throw new Error("MC_LOG_FILE is not set: add it to .claude/mcp/minecraft-rcon/config.local.json (see README.md)");
	}
	const content = await readFile(logFile, "utf8");
	const lines = content.split(/\r?\n/);
	if (lines.at(-1) === "") {
		lines.pop();
	}
	return lines;
}

const text = (value) => ({ content: [{ type: "text", text: value }] });

const server = new McpServer({ name: "minecraft-rcon", version: "1.0.0" });

server.registerTool(
	"test_connection",
	{
		title: "Test RCON connection",
		description: "Connect to the dev Minecraft server over RCON and announce the test in chat.",
		inputSchema: {},
	},
	async () => {
		await withRcon((s) => s.execute("say Rcon test connection"));
		return text(`Success: authenticated to ${host}:${port}`);
	},
);

server.registerTool(
	"execute_command",
	{
		title: "Run a server command",
		description:
			"Run a Minecraft server command over RCON (as the console) and return its reply. " +
			"Give the command without the leading slash, e.g. 'bot add 3 any'.",
		inputSchema: {
			command: z.string().min(1).describe("Command to run, without the leading slash"),
			announce: z
				.boolean()
				.default(true)
				.describe("Broadcast the command in chat first (say …) so players can follow along"),
		},
	},
	async ({ command, announce }) => {
		const cmd = command.replace(/^\//, "");
		const reply = await withRcon(async (s) => {
			if (announce) {
				await s.execute(`say [Claude] /${cmd}`);
			}
			return s.execute(cmd);
		});
		const out = stripFormatting(String(reply ?? "")).trim();
		return text(out === "" ? "(no reply)" : out);
	},
);

server.registerTool(
	"read_logs",
	{
		title: "Read the server log",
		description:
			"Read the dev server's latest.log. Use fromLine (1-based, from log_size) to read only what was " +
			"logged since a point, and/or lastNLines to keep only the end. With neither, returns the whole log.",
		inputSchema: {
			lastNLines: z.number().int().positive().optional().describe("Return only this many lines from the end"),
			fromLine: z
				.number()
				.int()
				.positive()
				.optional()
				.describe("Start at this 1-based line number (e.g. a log_size taken earlier, plus 1)"),
			grep: z
				.string()
				.optional()
				.describe("Keep only lines matching this case-insensitive regular expression"),
		},
	},
	async ({ lastNLines, fromLine, grep }) => {
		let lines = (await readLogLines()).map((line, i) => ({ n: i + 1, line }));
		if (fromLine !== undefined) {
			lines = lines.slice(fromLine - 1);
		}
		if (grep !== undefined) {
			const re = new RegExp(grep, "i");
			lines = lines.filter(({ line }) => re.test(line));
		}
		if (lastNLines !== undefined) {
			lines = lines.slice(-lastNLines);
		}
		if (lines.length === 0) {
			return text("(no matching lines)");
		}
		return text(lines.map(({ n, line }) => `${n}: ${line}`).join("\n"));
	},
);

server.registerTool(
	"log_size",
	{
		title: "Count log lines",
		description: "Return the number of lines in the dev server's latest.log (a bookmark for read_logs fromLine).",
		inputSchema: {},
	},
	async () => text(String((await readLogLines()).length)),
);

await server.connect(new StdioServerTransport());
