# SPDX-FileCopyrightText: 2026 Vulkan Technologies
# SPDX-License-Identifier: AGPL-3.0-or-later

import pytest

from talos_runner_supervisor.execute import _volume_subpath


def test_volume_subpath_strips_the_mounted_root():
    assert _volume_subpath("/var/talos/workspaces", "/var/talos/workspaces/demo/runs/r1/worktree") == \
        "demo/runs/r1/worktree"


def test_volume_subpath_raises_if_path_is_not_under_root():
    with pytest.raises(ValueError):
        _volume_subpath("/var/talos/workspaces", "/var/talos/provider-homes/claude-code")
