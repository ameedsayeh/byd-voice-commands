"""Recognizer check: synthesize command phrases with several macOS voices, make a road-noise copy
(15 dB SNR) and a harsh one (5 dB SNR + cheap band-limited mic), transcribe them with the app's
Moonshine Tiny model, and write a TSV that `gradle :voicecore:nluEval --args="score <tsv>"` scores
by the command that would actually fire.

usage: python asr_bench.py <workdir>
Needs: pip install moonshine-voice numpy   (macOS, for `say` / `afconvert`)
"""
import subprocess, sys, wave
from pathlib import Path
import numpy as np

PHRASES = [
    "turn on the air conditioning", "turn off the AC", "set the temperature to twenty two degrees",
    "set the temperature to nineteen point five", "make it a bit warmer", "fan speed four",
    "set the fan to level six", "air to my feet", "air to face and windshield", "defrost the windshield",
    "turn on recirculation", "turn on the driver seat heating", "heat the passenger seat on high",
    "strong passenger ventilation", "turn off the seat ventilation", "turn on the steering wheel heating",
    "heat the mirrors", "fold the mirrors now", "lock the car", "unlock the doors", "open the trunk",
    "close the trunk", "open the driver window halfway", "open the rear right window thirty percent",
    "close all windows", "open the sunroof a quarter", "close the sunroof", "open the sun shade",
    "turn on the hazard lights", "turn on the ambient light", "set the ambient light to blue",
    "turn on the cabin light", "turn off the daytime running lights", "turn on auto high beam",
    "show me the rear camera", "show the front camera", "switch to sport mode", "eco mode",
    "strong energy recovery", "what is the temperature outside", "what is the temperature inside",
    "turn on the head up display", "turn off the screen", "dark theme", "raise the wipers",
    "fan speed seven", "open the passenger window seventy percent", "open the roof three quarters",
]
VOICES = ["Samantha", "Daniel", "Karen", "Moira", "Rishi", "Tara", "Aman", "Fred"]
# Same list as Lexicon.recognizerTerms (the command dictionary the app gives Moonshine).
TERMS = ["BYD", "air conditioning", "AC", "temperature", "ventilation", "recirculation", "windshield",
         "defrost", "steering wheel", "passenger", "driver", "mirrors", "trunk", "sunroof", "sun shade",
         "hazard lights", "ambient light", "cabin light", "daytime running lights", "high beam",
         "camera", "sport mode", "eco mode", "energy recovery", "head up display", "wipers", "fan speed"]
MODEL = Path(__file__).resolve().parent.parent / "app/src/main/assets/moonshine/tiny-streaming-en"
RATE = 16000


def read_wav(p):
    with wave.open(str(p)) as w:
        return np.frombuffer(w.readframes(w.getnframes()), dtype=np.int16).astype(np.float32) / 32768


def write_wav(p, x):
    with wave.open(str(p), "wb") as w:
        w.setnchannels(1); w.setsampwidth(2); w.setframerate(RATE)
        w.writeframes((np.clip(x, -1, 1) * 32767).astype(np.int16).tobytes())


def noisy(x, rng, snr_db, cheap_mic):
    """Road rumble + fan hiss at the given SNR; optionally a cheap mic (300-3400 Hz, 6 dB quieter)."""
    brown = np.cumsum(rng.standard_normal(len(x))); brown -= np.convolve(brown, np.ones(400) / 400, "same")
    noise = brown / (np.std(brown) + 1e-9) + 0.3 * rng.standard_normal(len(x))
    speech = x[np.abs(x) > 0.01]
    noise *= np.sqrt(np.mean(speech ** 2) / 10 ** (snr_db / 10) / np.mean(noise ** 2))
    y = x + noise
    if not cheap_mic:
        return y
    f = np.fft.rfft(y); hz = np.fft.rfftfreq(len(y), 1 / RATE)
    f[(hz < 300) | (hz > 3400)] *= 0.05
    return np.fft.irfft(f, len(y)) * 0.5


def synth(work):
    rng = np.random.default_rng(7)
    items = []
    for i, text in enumerate(PHRASES):
        for v in (VOICES[i % len(VOICES)], VOICES[(i + 3) % len(VOICES)]):
            key = f"{i:02d}-{v}"
            clean = work / f"{key}-clean.wav"
            if not clean.exists():
                aiff = work / f"{key}.aiff"
                subprocess.run(["say", "-v", v, "-o", str(aiff), text], check=True)
                subprocess.run(["afconvert", "-f", "WAVE", "-d", "LEI16@16000", "-c", "1", str(aiff), str(clean)], check=True)
                aiff.unlink()
                x = np.concatenate([np.zeros(RATE // 2), read_wav(clean), np.zeros(RATE)])
                write_wav(clean, x)
                write_wav(work / f"{key}-road.wav", noisy(x, rng, 15, False))
                write_wav(work / f"{key}-car.wav", noisy(x, rng, 5, True))
            items.append((key, text))
    return items


def moonshine(terms):
    """Streams audio in 0.1 s chunks like the app does, with its options."""
    from moonshine_voice.transcriber import Transcriber
    from moonshine_voice.moonshine_api import ModelArch
    t = Transcriber(MODEL, ModelArch.TINY_STREAMING, options={"vad_threshold": "0.3"})
    if terms: t.set_keyterms(terms)

    def run(x):
        s = t.create_stream(); s.start()
        for o in range(0, len(x), 1600): s.add_audio(x[o:o + 1600].tolist(), RATE)
        return " ".join(l.text for l in s.stop().lines if l.text).strip()
    return run


def main():
    work = Path(sys.argv[1]); work.mkdir(parents=True, exist_ok=True)
    items = synth(work)
    engines = {"tiny": moonshine(None), "tiny + command terms": moonshine(TERMS)}
    rows = []
    for cond in ("clean", "road", "car"):
        for key, text in items:
            x = read_wav(work / f"{key}-{cond}.wav")
            for name, run in engines.items():
                rows.append("\t".join([key, text, f"{cond:5s} {name}", run(x).replace("\t", " ")]))
    (work / "results.tsv").write_text("\n".join(rows) + "\n")
    print(f"wrote {work / 'results.tsv'} ({len(rows)} rows)")


if __name__ == "__main__":
    main()
