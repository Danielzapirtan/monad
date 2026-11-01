# MONAD

MONAD is a collection of local Flask apps for media transcription and document
conversion. The apps are independent; the default launcher starts only
`diarix.cpp`.

## Apps

| App | Purpose | Local URL |
| --- | --- | --- |
| `diarix.cpp` | Media editing and transcription using whisper.cpp | <http://127.0.0.1:5029> |
| `diarix` | Media editing, transcription, and optional diarization | <http://127.0.0.1:5030> |
| `morphix` | Document conversion and splitting | <http://127.0.0.1:5034> |

`index.html` links to these local apps and the external Convix app.

## Start diarix.cpp

Run from the repository root:

```bash
./lau.sh
```

`lau.sh` delegates to `laucpp.sh`, which prepares the whisper.cpp checkout,
builds the CLI when needed, downloads the large-v3 model when missing, installs
the app dependencies, and starts `diarix.cpp`. The build and model download can
take significant time and disk space. `test.sh` delegates to `testcpp.sh` and
expects that setup to be complete.

The default whisper.cpp checkout and model locations can be overridden with
`WHISPER_CPP_DIR`, `WHISPER_CPP_BIN`, and `WHISPER_CPP_MODEL_DIR`.

## Start the other apps

`diarix` and `morphix` are not started by `lau.sh`. To run either app, install
its own requirements and launch it from its project directory. For example:

```bash
cd projects/morphix
python3 -m venv .venv
source .venv/bin/activate
pip install -r requirements.txt
python app.py
```

Use the corresponding directory and requirements file for `diarix`. Media
features require `ffmpeg`; optional AI features may require app-specific API
keys.

## Repository utilities

- `updatecpp.sh` updates the MONAD checkout at `$HOME/MONAD` (override with
  `MONAD_DIR`).
- `instal` installs the repository's zsh aliases.

These are local-use apps, not production services; do not expose them to
untrusted networks without adding appropriate safeguards.
