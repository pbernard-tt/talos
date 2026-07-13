# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

import talos_runner_supervisor


def test_package_imports():
    assert talos_runner_supervisor is not None
