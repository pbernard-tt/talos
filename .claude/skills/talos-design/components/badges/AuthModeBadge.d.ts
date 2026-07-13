/**
 * AuthModeBadge — discloses whether a run used a personal subscription
 * login (never expose this mode to other users) or an API key (preferred
 * for productized/automated work). This distinction is a governance
 * requirement in the plan, not a cosmetic detail — never hide it.
 *
 * @startingPoint section="Badges" subtitle="api_key vs subscription_local disclosure" viewport="160x32"
 */
export interface AuthModeBadgeProps {
  mode?: "api_key" | "subscription_local" | "n/a";
}
