from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected source exactly once, found {count}")
    path.write_text(text.replace(old, new, 1))


shell = Path("app/src/main/java/com/snaploop/app/ui/SnapLoopMainShell.kt")
replace_once(shell, "import androidx.compose.material.icons.filled.CalendarMonth\n", "import androidx.compose.material.icons.filled.CalendarMonth\nimport androidx.compose.material.icons.filled.Cake\nimport androidx.compose.material.icons.filled.Celebration\nimport androidx.compose.material.icons.filled.Flight\n", "Shell category icon imports")
replace_once(
    shell,
    '''private fun shellCategoryIcon(category: EventCategory): ImageVector = when (category) {
    EventCategory.trip -> Icons.Filled.LocationOn
    EventCategory.wedding -> Icons.Filled.Image
    EventCategory.party -> Icons.Filled.Groups
    EventCategory.birthday -> Icons.Filled.MoreHoriz
    EventCategory.conference -> Icons.Filled.Groups
    EventCategory.family -> Icons.Filled.Groups
    EventCategory.sports -> Icons.Filled.MoreHoriz
    EventCategory.other -> Icons.Filled.PhotoLibrary
}''',
    '''private fun shellCategoryIcon(category: EventCategory): ImageVector = when (category) {
    EventCategory.trip -> Icons.Filled.Flight
    EventCategory.wedding -> Icons.Filled.Image
    EventCategory.party -> Icons.Filled.Celebration
    EventCategory.birthday -> Icons.Filled.Cake
    EventCategory.conference -> Icons.Filled.Groups
    EventCategory.family -> Icons.Filled.Groups
    EventCategory.sports -> Icons.Filled.MoreHoriz
    EventCategory.other -> Icons.Filled.PhotoLibrary
}''',
    "Shell category icons",
)


dashboard = Path("app/src/main/java/com/snaploop/app/ui/ParityEventDashboard.kt")
replace_once(dashboard, "import androidx.compose.material.icons.filled.CalendarMonth\n", "import androidx.compose.material.icons.filled.CalendarMonth\nimport androidx.compose.material.icons.filled.Cake\nimport androidx.compose.material.icons.filled.Celebration\nimport androidx.compose.material.icons.filled.Flight\n", "Dashboard category icon imports")
replace_once(
    dashboard,
    '''private fun parityCategoryIcon(category: EventCategory): ImageVector = when (category) {
    EventCategory.trip -> Icons.Filled.LocationOn
    EventCategory.wedding -> Icons.Filled.Image
    EventCategory.party -> Icons.Filled.Groups
    EventCategory.birthday -> Icons.Filled.MoreHoriz
    EventCategory.conference -> Icons.Filled.Groups
    EventCategory.family -> Icons.Filled.Groups
    EventCategory.sports -> Icons.Filled.MoreHoriz
    EventCategory.other -> Icons.Filled.PhotoLibrary
}''',
    '''private fun parityCategoryIcon(category: EventCategory): ImageVector = when (category) {
    EventCategory.trip -> Icons.Filled.Flight
    EventCategory.wedding -> Icons.Filled.Image
    EventCategory.party -> Icons.Filled.Celebration
    EventCategory.birthday -> Icons.Filled.Cake
    EventCategory.conference -> Icons.Filled.Groups
    EventCategory.family -> Icons.Filled.Groups
    EventCategory.sports -> Icons.Filled.MoreHoriz
    EventCategory.other -> Icons.Filled.PhotoLibrary
}''',
    "Dashboard category icons",
)


test_path = Path("app/src/test/java/com/snaploop/app/ui/ReportedCategoryIconParityTest.kt")
test_path.write_text('''package com.snaploop.app.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportedCategoryIconParityTest {
    private fun source(name: String): String {
        val candidates = listOf(
            File("src/main/java/com/snaploop/app/ui/$name"),
            File("app/src/main/java/com/snaploop/app/ui/$name"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("$name was not found from the unit-test working directory")
    }

    @Test fun `home uses meaningful trip and birthday icons instead of placeholders`() {
        val source = source("SnapLoopMainShell.kt")
        assertTrue(source.contains("EventCategory.trip -> Icons.Filled.Flight"))
        assertTrue(source.contains("EventCategory.birthday -> Icons.Filled.Cake"))
        assertFalse(source.contains("EventCategory.birthday -> Icons.Filled.MoreHoriz"))
    }

    @Test fun `event hero uses same trip birthday and party icon language`() {
        val source = source("ParityEventDashboard.kt")
        assertTrue(source.contains("EventCategory.trip -> Icons.Filled.Flight"))
        assertTrue(source.contains("EventCategory.birthday -> Icons.Filled.Cake"))
        assertTrue(source.contains("EventCategory.party -> Icons.Filled.Celebration"))
    }
}
''')
