/**
 * AgentBadge — identifies which adapter/provider executed or will execute a
 * run (Claude Code, Codex CLI, OpenCode, OpenHands, Custom Shell). Each
 * provider gets a stable initial + color so it stays recognizable across the
 * whole product without becoming a rainbow of unrelated hues.
 *
 * @startingPoint section="Badges" subtitle="Agent/provider identity chip" viewport="160x40"
 */
export interface AgentBadgeProps {
  agentKey?: "claude-code" | "codex-cli" | "opencode" | "openhands" | "custom-shell";
}
