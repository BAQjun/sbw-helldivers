# Project and version control

- Work on D:. The mod source repository is D:/Project/sbw_helldiver.
- GitHub: https://github.com/BAQjun/sbw-helldivers (private). main is the development branch; immutable release tags use v<mod_version>.
- Models and the active user-facing delivery are under D:/Codex/projects/2026-09-19/new-chat/outputs/当前交付. Follow the artwork workspace AGENTS.md for modeling, textures, animation and sound requirements.
- Runtime resources live in src/main/resources; accepted Blockbench sources live in art/models. After changing accepted models, synchronize with tools/sync_model_sources.py --delivery <delivery-directory> and verify --check before committing.
- Git LFS manages *.bbmodel and *.geo.json. Never replace a real source file with an LFS pointer in the working tree.
- Keep generated builds, caches, test saves and old install jars out of Git. Current binaries and map ZIPs are GitHub Release assets.
- Historical working materials removed during cleanup are recoverable from Release workspace-archive-2026-10-17, using tools/restore_workspace.py. Do not assume old work/ or archive/ paths still exist locally.
- Build with JDK 17. The existing build.ps1 uses the retained D: Gradle dependency cache. Run checks appropriate to actual changes and record their results with each release.
- Do not move published tags or force-push shared history. Never commit credentials or personal game data.
- The Actions workflow is a template in ci/github-actions-build.yml because the current OAuth token lacks workflow scope; do not repeatedly push it to .github/workflows without that permission.
