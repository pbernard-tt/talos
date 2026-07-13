# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

import talos_orchestrator


def test_package_imports():
    assert talos_orchestrator is not None
