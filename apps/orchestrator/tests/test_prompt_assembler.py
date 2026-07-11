from talos_orchestrator.prompt_assembler import assemble_prompt


def test_assemble_prompt_uses_fixed_section_order_and_truncates_context(tmp_path):
    (tmp_path / "README.md").write_text("z" * 8_100)
    prompt = assemble_prompt(
        {"title": "Add hello", "description": "Expose /hello."},
        {"slug": "demo"},
        {"rules": {"forbidden": [".env", "secrets/**"]}, "context": {"docs": ["README.md"]}},
        str(tmp_path),
    )
    assert prompt.index("isolated branch") < prompt.index("Project context") < prompt.index("Task title") < prompt.index("Make the necessary")
    assert "Do not modify files matching: .env, secrets/**" in prompt
    assert prompt.count("z") == 8_000


def test_assemble_prompt_ignores_context_outside_workspace(tmp_path):
    prompt = assemble_prompt(
        {"title": "Task", "description": "Description"}, {"slug": "demo"},
        {"context": {"docs": ["../outside.md"]}}, str(tmp_path),
    )
    assert "Project context" not in prompt


def test_assemble_prompt_injects_memory_between_context_and_task(tmp_path):
    (tmp_path / "README.md").write_text("project docs")
    prompt = assemble_prompt(
        {"title": "Task", "description": "Description"},
        {"slug": "demo"},
        {"context": {"docs": ["README.md"]}},
        str(tmp_path),
        [{"title": "Prior run", "sourceRef": "run-1", "content": "Use the existing service layer."}],
    )

    assert prompt.index("Project context") < prompt.index("Relevant project memory") < prompt.index("Task title")
    assert "Use the existing service layer." in prompt
