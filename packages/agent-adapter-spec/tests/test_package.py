# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: Apache-2.0

import talos_agent_adapter_spec


def test_package_imports():
    assert talos_agent_adapter_spec is not None
