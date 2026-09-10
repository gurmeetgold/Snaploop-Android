from pathlib import Path

path = Path('app/src/main/java/com/snaploop/app/ui/ParityPhotoGallery.kt')
text = path.read_text()

replacements = {
    'Modifier.padding(horizontal = 16.dp, top = 4.dp)': 'Modifier.padding(start = 16.dp, top = 4.dp, end = 16.dp)',
    'Modifier.fillMaxWidth().padding(horizontal = 16.dp, top = 10.dp)': 'Modifier.fillMaxWidth().padding(start = 16.dp, top = 10.dp, end = 16.dp)',
}

for old, new in replacements.items():
    count = text.count(old)
    if count != 1:
        raise SystemExit(f'Expected exactly one occurrence of {old!r}, found {count}')
    text = text.replace(old, new, 1)

path.write_text(text)
print('Fixed Compose padding overload usage in ParityPhotoGallery.kt')
