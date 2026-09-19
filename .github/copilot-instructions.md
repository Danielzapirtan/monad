# Copilot instructions for MONAD

## Repository shape

This repo is a launcher plus a small set of self-contained Flask apps in `projects/`. The current checkout contains the app directories `diarix` and `morphix`; treat each app as independent rather than as a shared monorepo service.

- Root scripts (`lau.sh`, `test.sh`) orchestrate startup and environment setup.
- `index.html` is just a landing page for local app URLs.
- Each app usually has its own `app.py`, `requirements.txt`, and optional project README.
- UI code is typically embedded directly in `app.py`; there is no shared frontend framework or common app server.

## Build, test, and lint commands

There is no formal Python test suite or lint runner in this repo. The practical validation path is startup-level verification via the launcher and individual app check scripts.

Run the full stack from the repo root:

```bash
./lau.sh
```

Recreate the environment from scratch, including clearing virtualenvs and reinstalling dependencies:

```bash
./lau.sh --cold
```

Validate a single app by name:

```bash
bash ./test.sh diarix
bash ./test.sh morphix
```

Manual app startup pattern:

```bash
cd projects/diarix
python3.13 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python app.py
```

Use the same pattern for `projects/morphix` with its own `requirements.txt`.

## High-level architecture

### Root launcher model

`lau.sh` does the cross-app coordination work:

- discovers app directories under `projects/`
- exports `DEMO` and `VER` for the current platform/Python version
- kills stale processes already bound to the app ports
- creates or reuses a root `.venv`
- runs `test.sh` for each app to install dependencies and confirm startup
- waits for external connectivity before declaring the stack ready

This means startup order, dependency installation, port cleanup, and environment setup are centralized at the repo root instead of being managed per app.

### App-level architecture

Each app under `projects/` is functionally standalone:

- a Flask app in `app.py`
- app-local Python dependencies in `requirements.txt`
- embedded HTML/CSS/JS in the same file when the app is a single-file UI
- optional README/spec notes describing the app's behavior and environment variables

There is no shared backend library or cross-app service layer. When making code changes, keep them in the target app directory unless the root launcher script itself needs to change.

### Operational conventions

`test.sh` is the repo's startup verification tool. It:

- changes into `./projects/$APP`
- installs that app's requirements with `python$VER -m pip install -r requirements.txt`
- runs that app in the background with `python$VER app.py &`
- waits briefly and confirms the process exists at `/proc/$pid`

That startup check is the primary validation signal in this codebase; there is no CI suite to substitute for it.

## Key conventions

- Keep app changes local to the relevant `projects/<app>/` directory.
- Do not introduce a repo-wide Python package or shared service layer unless the app docs explicitly require it.
- If a change adds a dependency, update that app's `requirements.txt` rather than a root dependency file.
- Prefer the existing app-local structure: `app.py` + embedded front-end logic + project-specific requirements, unless a given app README documents a different layout.
- Treat these apps as local/single-user tools rather than production services; they are not protected by auth, rate limiting, or hardened deployment defaults.
- Check app-specific README/spec files before wiring in secret environment variables such as `ANTHROPIC_API_KEY`, `GEMINI_API_KEY`, or project-specific key names.
- The repo has stale references in older docs; verify the actual `projects/` directory before assuming app names or port assumptions from older README snippets.

## Notes for future sessions

When modifying code, validate with the repo's startup pattern (`./lau.sh` or `bash ./test.sh <app>`) rather than adding unrelated frameworks or a broad test harness that does not match this project.
