/**
 * RiskBadge — renders nothing for NORMAL risk (quiet by default); only
 * HIGH risk gets a visible warning-triangle pill. Never make a normal task
 * look alarming — only genuinely high-risk work should stand out.
 *
 * @startingPoint section="Badges" subtitle="High-risk task/change flag" viewport="140x32"
 */
export interface RiskBadgeProps {
  level?: "NORMAL" | "HIGH";
}
