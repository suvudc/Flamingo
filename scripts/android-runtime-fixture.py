"""Synthetic audio and original timed words; used only by the disposable CI emulator."""
from pathlib import Path
import wave

root = Path('runtime-diagnostics')
root.mkdir(exist_ok=True)
with wave.open(str(root / 'MusicRuntime.wav'), 'wb') as audio:
    audio.setnchannels(1)
    audio.setsampwidth(2)
    audio.setframerate(8000)
    audio.writeframes(b'\0\0' * 8000 * 64)
lines = []
for start in range(0, 64, 4):
    lines.append(f'<p begin="{start}s" end="{start + 4}s"><span begin="{start}s" end="{start + 1.5}s">Offline </span><span begin="{start + 1.5}s" end="{start + 3}s">lyrics </span><span begin="{start + 3}s" end="{start + 4}s">test {start // 4 + 1}</span></p>')
(root / 'MusicRuntime.ttml').write_text('<tt xmlns="http://www.w3.org/ns/ttml"><body><div>' + ''.join(lines) + '</div></body></tt>')
