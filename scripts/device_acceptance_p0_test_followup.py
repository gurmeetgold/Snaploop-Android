from pathlib import Path

ROOT = Path('.')

def rw(path, transform):
    p = ROOT / path
    text = p.read_text()
    new = transform(text)
    if new == text:
        raise RuntimeError(f'No changes made to {path}')
    p.write_text(new)

rw(
    'app/src/test/java/com/snaploop/app/ui/EventDateRangeFormatterTest.kt',
    lambda s: s
        .replace('"Aug 24, 2026"', '"Aug 24"')
        .replace('"Aug 24–28, 2026"', '"Aug 24 – Aug 28"')
        .replace('"Aug 26 – Sep 10, 2026"', '"Aug 26 – Sep 10"')
        .replace('"Dec 31, 2026 – Jan 2, 2027"', '"Dec 31 – Jan 2"')
        .replace('single date keeps complete date', 'single date uses compact no-year date')
        .replace('same month keeps both days and year without duplicate month', 'same month keeps both compact endpoints without year')
        .replace('different months keep complete range and year', 'different months keep both compact endpoints without year')
        .replace('different years retain both years', 'different years intentionally omit years on compact Event cards'),
)

rw(
    'app/src/test/java/com/snaploop/app/ui/GuidedFacePoseTrackerTest.kt',
    lambda s: s
        .replace('observation(yaw = 32f)).readyToCapture)', 'observation(yaw = 42f)).readyToCapture)')
        .replace('observation(yaw = 24f, centerY = 0.72f)', 'observation(yaw = 42f, centerY = 0.72f)'),
)

rw(
    'app/src/test/java/com/snaploop/app/ui/ReportedLayoutParityTest.kt',
    lambda s: s
        .replace('fun `event dashboard back is icon only`()', 'fun `event dashboard back uses consistent labeled back affordance`()')
        .replace('assertTrue(source.contains("Icon(Icons.Filled.ChevronLeft, contentDescription = \\"Back\\")"))', 'assertTrue(source.contains("Text(\\"‹ Back\\""))')
        .replace('assertFalse(source.contains("Text(\\"Events\\")"))', 'assertFalse(source.contains("contentDescription = \\"Back\\")"))')
        .replace('assertTrue(source.contains("fontSize = 16.sp"))', 'assertTrue(source.contains("fontSize = 14.sp"))')
        .replace('assertTrue(source.contains("fontSize = 11.sp"))', 'assertTrue(source.contains("fontSize = 10.sp"))'),
)

print('Approved compact UI regression expectations updated')
