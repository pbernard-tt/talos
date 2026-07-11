"""The AgentAdapter contract (Section 7.1 of the implementation plan, transcribed verbatim, plus
the Phase 11 addition noted on AgentSessionRequest below)."""

from abc import ABC, abstractmethod
from dataclasses import dataclass, field
from enum import Enum
from typing import AsyncIterator

from talos_agent_adapter_spec.container import ContainerConfig


class AgentEventType(str, Enum):
    LOG = "log"  # raw stdout/stderr line
    TOOL_USE = "tool_use"  # structured action (command, file edit) when parseable
    STATUS = "status"  # adapter-level status change
    ERROR = "error"


@dataclass
class ProviderCapabilities:
    supports_streaming: bool
    supports_subscription_auth: bool
    supports_api_key_auth: bool
    supports_headless_mode: bool
    supports_diff_output: bool
    supports_approval_hooks: bool
    default_timeout_seconds: int = 1800


@dataclass
class AgentSessionRequest:
    run_id: str
    workspace_path: str  # absolute path to the worktree
    prompt: str  # fully assembled prompt (see 7.3)
    env: dict[str, str]  # ONLY approved injected variables
    auth_mode: str  # "api_key" | "subscription_local"
    provider_home: str  # isolated HOME dir holding provider credentials
    timeout_seconds: int
    # Phase 11 (Section 8: "per-run Docker containers using the workers/ images"): when set, the
    # adapter spawns its command inside this container instead of as a direct local subprocess.
    # None only for adapter-level unit tests that don't need real isolation.
    container: ContainerConfig | None = None


@dataclass
class AgentEvent:
    type: AgentEventType
    message: str
    timestamp: str  # ISO-8601 UTC
    metadata: dict = field(default_factory=dict)


@dataclass
class AgentResult:
    exit_code: int
    success: bool
    summary: str | None
    raw_output_path: str  # artifact file with the full transcript
    # Phase 14 (Section 16): normalized usage metadata, adapter-reported when the underlying CLI/API
    # exposes it. All four are None when unavailable -- callers must degrade gracefully rather than
    # treat missing usage as an error. total_cost_usd is the provider's own reported price; Talos
    # never estimates one (Section 13: subscription-mode runs must not get a fabricated dollar cost,
    # enforced at the API layer regardless of what an adapter reports here).
    input_tokens: int | None = None
    output_tokens: int | None = None
    total_cost_usd: float | None = None
    model: str | None = None


class AgentAdapter(ABC):
    key: str  # "custom-shell" | "claude-code" | "opencode" | "codex-cli" | ...

    @abstractmethod
    def capabilities(self) -> ProviderCapabilities: ...

    @abstractmethod
    async def start(self, request: AgentSessionRequest) -> None: ...

    @abstractmethod
    def events(self) -> AsyncIterator[AgentEvent]: ...

    @abstractmethod
    async def stop(self) -> None:
        """SIGTERM the process group; SIGKILL survivors after 10 seconds."""

    @abstractmethod
    async def result(self) -> AgentResult: ...
