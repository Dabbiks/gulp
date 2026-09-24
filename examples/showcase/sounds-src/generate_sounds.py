import math
import os
import struct
import random

out = r"C:\Users\mikol\Documents\Gulp\examples\showcase\src\main\resources\assets\showcase"
RATE = 22050


def write(path, samples):
    os.makedirs(os.path.dirname(path), exist_ok=True)
    data = b"".join(struct.pack("<h", max(-32767, min(32767, int(s * 32767)))) for s in samples)
    header = b"RIFF" + struct.pack("<I", 36 + len(data)) + b"WAVE"
    header += b"fmt " + struct.pack("<IHHIIHH", 16, 1, 1, RATE, RATE * 2, 2, 16)
    header += b"data" + struct.pack("<I", len(data))
    with open(path, "wb") as f:
        f.write(header + data)


def envelope(i, n, attack=0.01):
    t = i / n
    a = min(1.0, i / (attack * RATE)) if attack > 0 else 1.0
    return a * (1 - t) ** 2


def chirp(seconds, f0, f1, wave=math.sin):
    n = int(seconds * RATE)
    phase = 0.0
    result = []
    for i in range(n):
        f = f0 + (f1 - f0) * i / n
        phase += 2 * math.pi * f / RATE
        result.append(0.5 * wave(phase) * envelope(i, n))
    return result


def square(phase):
    return 1.0 if math.sin(phase) >= 0 else -1.0


def tones(notes, seconds, wave=math.sin, volume=0.4):
    result = []
    for f in notes:
        n = int(seconds * RATE)
        phase = 0.0
        for i in range(n):
            phase += 2 * math.pi * f / RATE
            result.append(volume * wave(phase) * envelope(i, n, 0.005))
    return result


write(os.path.join(out, "sounds", "jump.wav"), chirp(0.18, 300, 900, square))
write(os.path.join(out, "sounds", "coin_a.wav"), tones([988, 1319], 0.07, square, 0.3))
write(os.path.join(out, "sounds", "coin_b.wav"), tones([1047, 1397], 0.07, square, 0.3))

# Music: 8 bars of a simple arpeggio over a bass line, 8 seconds, loops seamlessly.
random.seed(7)
bars = [[220, 262, 330], [175, 220, 262], [196, 247, 294], [165, 208, 247]] * 2
beat = RATE // 4
music = []
for chord in bars:
    for step in range(4):
        f = chord[step % 3] * 2
        bass = chord[0] / 2
        for i in range(beat):
            t = i / RATE
            e = math.exp(-6 * i / beat)
            v = 0.22 * math.sin(2 * math.pi * f * t) * e + 0.18 * math.sin(2 * math.pi * bass * t)
            music.append(v)
write(os.path.join(out, "music", "loop.wav"), music)
print("ok", len(music) / RATE, "s")
