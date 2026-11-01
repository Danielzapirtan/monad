import importlib.util
import pathlib
import sys
import threading
import types
import unittest
from unittest.mock import patch


ROOT = pathlib.Path(__file__).resolve().parents[3]
APP_PATHS = (
    ROOT / "projects" / "diarix" / "app.py",
    ROOT / "projects" / "diarix.cpp" / "app.py",
)


def load_app(path):
    module_name = f"diarix_test_{path.parent.name.replace('.', '_')}"
    spec = importlib.util.spec_from_file_location(module_name, path)
    module = importlib.util.module_from_spec(spec)
    sys.modules[module_name] = module
    if "torch" not in sys.modules:
        sys.modules["torch"] = types.ModuleType("torch")
    spec.loader.exec_module(module)
    return module


class MultiFileDiarizationTests(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        cls.apps = [(path, load_app(path)) for path in APP_PATHS]

    def test_multifile_mlx_uses_native_diarization_only_for_pyannote(self):
        for path, app in self.apps[:1]:
            with self.subTest(app=path.parent.name):
                for method, expected_native in (
                    ("pyannote", True),
                    ("ai_api", False),
                ):
                    with self.subTest(method=method):
                        with (
                            patch.object(
                                app,
                                "transcribe_file",
                                return_value=[{
                                    "start": 0.0,
                                    "end": 1.0,
                                    "text": "hello",
                                    "speaker": "SPEAKER_00",
                                }],
                            ) as transcribe,
                            patch.object(app, "apply_diarization_to_segments") as apply,
                        ):
                            app.transcribe_one_source_file(
                                {"path": "sample.wav", "name": "sample.wav"},
                                "mlx",
                                "en",
                                "metal",
                                "large-v3",
                                True,
                                method,
                                "gemini",
                                "token",
                                None,
                                defer_pyannote=(method == "pyannote"),
                            )

                        self.assertEqual(
                            transcribe.call_args.kwargs["diarize"], expected_native
                        )
                        if method == "pyannote":
                            apply.assert_not_called()
                        else:
                            apply.assert_called_once()

    def test_transcription_continues_while_pyannote_is_running(self):
        for path, app in self.apps:
            with self.subTest(app=path.parent.name):
                diarization_started = threading.Event()
                next_transcription_finished = threading.Event()
                diarized = []

                def transcribe_file(file, *args, **kwargs):
                    if file["path"] == "second.wav":
                        self.assertTrue(diarization_started.wait(5))
                        next_transcription_finished.set()
                    return {
                        "name": file["name"],
                        "id": None,
                        "segments": [{
                            "start": 0.0,
                            "end": 1.0,
                            "text": file["name"],
                        }],
                    }

                def apply_diarization(file_path, segments, *args):
                    diarized.append(file_path)
                    for segment in segments:
                        segment["speaker"] = "SPEAKER_00"
                    if file_path == "first.wav":
                        diarization_started.set()
                        self.assertTrue(next_transcription_finished.wait(5))

                app_state = {
                    "lock": threading.Lock(),
                    "transcriptions": [],
                    "transcript": None,
                    "transcription_status": {"completed": 0},
                }
                source_files = [
                    {"path": "first.wav", "name": "first.wav"},
                    {"path": "second.wav", "name": "second.wav"},
                ]

                with (
                    patch.object(app, "force_sequential", return_value=False),
                    patch.object(app, "uses_gpu_device", return_value=False),
                    patch.object(app, "cpu_worker_count", return_value=2),
                    patch.object(
                        app, "transcribe_one_source_file", side_effect=transcribe_file
                    ),
                    patch.object(
                        app, "apply_diarization_to_segments",
                        side_effect=apply_diarization,
                    ),
                    patch.object(
                        app,
                        "segments_to_transcript",
                        side_effect=lambda segments: segments[0]["text"],
                    ),
                ):
                    results = app.run_multifile_transcriptions(
                        app_state,
                        source_files,
                        "faster" if path.parent.name == "diarix" else "whisper.cpp",
                        "en",
                        "cpu",
                        "small",
                        True,
                        "pyannote",
                        "gemini",
                        "token",
                        None,
                    )

                self.assertEqual(
                    [result["name"] for result in results],
                    ["first.wav", "second.wav"],
                )
                self.assertEqual(diarized, ["first.wav", "second.wav"])
                self.assertEqual(app_state["transcription_status"]["completed"], 2)


if __name__ == "__main__":
    unittest.main()
