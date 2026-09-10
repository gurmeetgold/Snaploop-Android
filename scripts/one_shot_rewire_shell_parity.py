from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text()
    count = text.count(old)
    if count != 1:
        raise SystemExit(f"{label}: expected source exactly once, found {count}")
    path.write_text(text.replace(old, new, 1))


create = Path("app/src/main/java/com/snaploop/app/ui/ParityCreateEventDialog.kt")
replace_once(create, "import androidx.compose.material.icons.filled.Category\n", "", "Remove generic Create category import")
replace_once(create, "import androidx.compose.material.icons.filled.LocationOn\n", "import androidx.compose.material.icons.filled.Navigation\n", "Create location navigation icon")
replace_once(create, 'CreateFieldLabel("Type", Icons.Filled.Category)', 'CreateFieldLabel("Type", eventCategoryIcon(category))', "Create selected category label")
replace_once(create, 'CreateFieldLabel("Location", Icons.Filled.LocationOn)', 'CreateFieldLabel("Location", Icons.Filled.Navigation)', "Create location label")
replace_once(
    create,
    '''                                Text(
                                    createCategoryName(category),
                                    modifier = Modifier.weight(1f),
                                    textAlign = TextAlign.Start,
                                    fontWeight = FontWeight.SemiBold,
                                )''',
    '''                                Icon(
                                    eventCategoryIcon(category),
                                    contentDescription = null,
                                    modifier = Modifier.size(20.dp),
                                )
                                Text(
                                    createCategoryName(category),
                                    modifier = Modifier.weight(1f).padding(start = 8.dp),
                                    textAlign = TextAlign.Start,
                                    fontWeight = FontWeight.SemiBold,
                                )''',
    "Create selected category row icon",
)
replace_once(
    create,
    '''                                DropdownMenuItem(
                                    text = { Text(createCategoryName(item)) },
                                    onClick = {''',
    '''                                DropdownMenuItem(
                                    text = { Text(createCategoryName(item)) },
                                    leadingIcon = {
                                        Icon(eventCategoryIcon(item), contentDescription = null)
                                    },
                                    onClick = {''',
    "Create dropdown category icons",
)

edit = Path("app/src/main/java/com/snaploop/app/ui/ParityEditEventDialog.kt")
replace_once(edit, "import androidx.compose.material.icons.filled.Category\n", "", "Remove generic Edit category import")
replace_once(edit, "import androidx.compose.material.icons.filled.LocationOn\n", "import androidx.compose.material.icons.filled.Navigation\n", "Edit location navigation icon")
replace_once(edit, 'EditFieldLabel("Type", Icons.Filled.Category)', 'EditFieldLabel("Type", eventCategoryIcon(category))', "Edit selected category label")
replace_once(edit, 'EditFieldLabel("Location", Icons.Filled.LocationOn)', 'EditFieldLabel("Location", Icons.Filled.Navigation)', "Edit location label")
replace_once(
    edit,
    '''                        TextButton(
                            onClick = { categoryMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !controlsBusy,
                        ) {
                            Text(
                                editCategoryName(category),
                                color = SnapColors.Coral,
                                modifier = Modifier.weight(1f),
                                textAlign = TextAlign.Start,
                            )
                        }''',
    '''                        TextButton(
                            onClick = { categoryMenu = true },
                            modifier = Modifier.fillMaxWidth(),
                            enabled = !controlsBusy,
                        ) {
                            Icon(
                                eventCategoryIcon(category),
                                contentDescription = null,
                                tint = SnapColors.Coral,
                                modifier = Modifier.size(20.dp),
                            )
                            Text(
                                editCategoryName(category),
                                color = SnapColors.Coral,
                                modifier = Modifier.weight(1f).padding(start = 8.dp),
                                textAlign = TextAlign.Start,
                            )
                        }''',
    "Edit selected category row icon",
)
replace_once(
    edit,
    '''                                DropdownMenuItem(
                                    text = { Text(editCategoryName(item)) },
                                    onClick = {''',
    '''                                DropdownMenuItem(
                                    text = { Text(editCategoryName(item)) },
                                    leadingIcon = {
                                        Icon(eventCategoryIcon(item), contentDescription = null)
                                    },
                                    onClick = {''',
    "Edit dropdown category icons",
)

you = Path("app/src/main/java/com/snaploop/app/ui/ParityYouScreen.kt")
replace_once(you, "import androidx.compose.material.icons.filled.CheckCircle\n", "import androidx.compose.material.icons.filled.AutoAwesome\nimport androidx.compose.material.icons.filled.Badge\nimport androidx.compose.material.icons.filled.CenterFocusStrong\nimport androidx.compose.material.icons.filled.CheckCircle\n", "You parity icon imports 1")
replace_once(you, "import androidx.compose.material.icons.filled.Edit\nimport androidx.compose.material.icons.filled.Face\n", "", "Remove generic You edit face icons")
replace_once(you, "import androidx.compose.material.icons.filled.PrivacyTip\nimport androidx.compose.material.icons.filled.RestartAlt\n", "import androidx.compose.material.icons.filled.Shield\n", "You parity icon imports 2")
replace_once(you, "icon = Icons.Filled.Edit,", "icon = Icons.Filled.Badge,", "You edit-name icon")
replace_once(you, "icon = Icons.Filled.Face,", "icon = Icons.Filled.CenterFocusStrong,", "You face setup icon")
replace_once(you, "icon = Icons.Filled.PrivacyTip,", "icon = Icons.Filled.Shield,", "You privacy icon")
replace_once(you, "icon = Icons.Filled.RestartAlt,", "icon = Icons.Filled.AutoAwesome,", "You replay icon")


test_path = Path("app/src/test/java/com/snaploop/app/ui/CreateEditYouVisualParityTest.kt")
test_path.write_text('''package com.snaploop.app.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CreateEditYouVisualParityTest {
    private fun source(name: String): String {
        val candidates = listOf(
            File("src/main/java/com/snaploop/app/ui/$name"),
            File("app/src/main/java/com/snaploop/app/ui/$name"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("$name was not found from the unit-test working directory")
    }

    @Test fun `create event uses selected category icon and navigation location icon`() {
        val source = source("ParityCreateEventDialog.kt")
        assertTrue(source.contains("CreateFieldLabel(\\"Type\\", eventCategoryIcon(category))"))
        assertTrue(source.contains("leadingIcon = {"))
        assertTrue(source.contains("Icons.Filled.Navigation"))
        assertFalse(source.contains("Icons.Filled.Category"))
    }

    @Test fun `edit event uses selected category icon and navigation location icon`() {
        val source = source("ParityEditEventDialog.kt")
        assertTrue(source.contains("EditFieldLabel(\\"Type\\", eventCategoryIcon(category))"))
        assertTrue(source.contains("eventCategoryIcon(category)"))
        assertTrue(source.contains("Icons.Filled.Navigation"))
        assertFalse(source.contains("Icons.Filled.Category"))
    }

    @Test fun `you uses semantic icons closer to pinned iOS`() {
        val source = source("ParityYouScreen.kt")
        assertTrue(source.contains("Icons.Filled.Badge"))
        assertTrue(source.contains("Icons.Filled.CenterFocusStrong"))
        assertTrue(source.contains("Icons.Filled.Shield"))
        assertTrue(source.contains("Icons.Filled.AutoAwesome"))
        assertFalse(source.contains("Icons.Filled.RestartAlt"))
    }
}
''')
