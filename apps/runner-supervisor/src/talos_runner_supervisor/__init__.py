# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

def main() -> None:
    import uvicorn

    uvicorn.run("talos_runner_supervisor.app:app", host="0.0.0.0", port=8081)
