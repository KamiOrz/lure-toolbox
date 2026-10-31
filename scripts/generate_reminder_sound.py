"""Generate the local, click-free outdoor cadence cue using only Python stdlib."""
import math
from pathlib import Path
import struct
import wave

RATE = 44100
HEADROOM = 0.97

def pulse(frequency, seconds):
    length = round(RATE * seconds)
    samples = []
    for i in range(length):
        t = i / RATE
        # Brief ramps avoid edge clicks; harmonics improve small-speaker audibility.
        envelope = min(1.0, i / (RATE * 0.005), (length - 1 - i) / (RATE * 0.012))
        value = math.sin(2 * math.pi * frequency * t)
        value += 0.22 * math.sin(4 * math.pi * frequency * t)
        samples.append(value * max(0.0, envelope))
    return samples

# Give a sleeping audio route time to wake before the first audible pulse.
samples = [0.0] * round(RATE * 0.10) + pulse(1300, 0.18) + [0.0] * round(RATE * 0.055) + pulse(1800, 0.22)
scale = HEADROOM * 32767 / max(abs(value) for value in samples)
pcm = [round(value * scale) for value in samples]
assert max(abs(value) for value in pcm) < 32767
assert len(pcm) / RATE < 1  # A one-second reminder cycle cannot overlap itself.
output = Path(__file__).resolve().parents[1] / 'app/src/main/res/raw/cadence.wav'
with wave.open(str(output), 'wb') as audio:
    audio.setparams((1, 2, RATE, 0, 'NONE', 'not compressed'))
    audio.writeframes(struct.pack('<' + 'h' * len(pcm), *pcm))
rms = math.sqrt(sum(value * value for value in pcm) / len(pcm)) / 32768
print(f'{output.name}: {len(pcm) / RATE:.3f}s, peak {max(abs(v) for v in pcm) / 32768:.3f}, RMS {rms:.3f}')
