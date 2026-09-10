from pathlib import Path


def replace_once(path: Path, old: str, new: str, label: str) -> None:
    text = path.read_text()
    if text.count(old) != 1:
        raise SystemExit(f"{label}: expected source exactly once, found {text.count(old)}")
    path.write_text(text.replace(old, new, 1))


shell = Path("app/src/main/java/com/snaploop/app/ui/SnapLoopMainShell.kt")
replace_once(
    shell,
    '''private fun shellEventRange(event: SnapEvent): String {
    val zone = shellEventZone(event)
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
    val start = event.startsAt.atZone(zone).toLocalDate().format(formatter)
    val end = event.endsAt.atZone(zone).toLocalDate().format(formatter)
    return if (start == end) start else "$start – $end"
}''',
    '''private fun shellEventRange(event: SnapEvent): String {
    val zone = shellEventZone(event)
    return EventDateRangeFormatter.format(
        event.startsAt.atZone(zone).toLocalDate(),
        event.endsAt.atZone(zone).toLocalDate(),
    )
}''',
    "Home event range formatter",
)
replace_once(
    shell,
    '''                Text(title, color = Color.White, fontSize = 17.sp, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Color.White.copy(alpha = 0.90f), fontSize = 12.sp)''',
    '''                Text(
                    title,
                    color = Color.White,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 2,
                )
                Text(
                    subtitle,
                    color = Color.White.copy(alpha = 0.90f),
                    fontSize = 11.sp,
                    maxLines = 2,
                )''',
    "Home action card typography",
)


dashboard = Path("app/src/main/java/com/snaploop/app/ui/ParityEventDashboard.kt")
replace_once(
    dashboard,
    '''            TextButton(onClick = onBack) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = null)
                Text("Events")
            }''',
    '''            androidx.compose.material3.IconButton(onClick = onBack) {
                Icon(Icons.Filled.ChevronLeft, contentDescription = "Back")
            }''',
    "Event dashboard icon-only back",
)
replace_once(
    dashboard,
    '''            Row(
                modifier = Modifier.padding(top = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(17.dp))
                Text(
                    parityEventRange(event),
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                )
                role?.let {
                    Surface(color = Color.White.copy(alpha = 0.18f), shape = CircleShape) {
                        Row(
                            Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Icon(parityRoleIcon(it), contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                            Text(
                                parityRoleLabel(it),
                                color = Color.White,
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Black,
                                modifier = Modifier.padding(start = 4.dp),
                            )
                        }
                    }
                }
            }''',
    '''            Row(
                modifier = Modifier.padding(top = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(Icons.Filled.CalendarMonth, contentDescription = null, tint = Color.White.copy(alpha = 0.92f), modifier = Modifier.size(17.dp))
                Text(
                    parityEventRange(event),
                    color = Color.White.copy(alpha = 0.92f),
                    fontSize = 12.sp,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1,
                )
            }
            role?.let {
                Surface(
                    color = Color.White.copy(alpha = 0.18f),
                    shape = CircleShape,
                    modifier = Modifier.padding(top = 7.dp),
                ) {
                    Row(
                        Modifier.padding(horizontal = 9.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(parityRoleIcon(it), contentDescription = null, tint = Color.White, modifier = Modifier.size(13.dp))
                        Text(
                            parityRoleLabel(it),
                            color = Color.White,
                            fontSize = 10.sp,
                            fontWeight = FontWeight.Black,
                            modifier = Modifier.padding(start = 4.dp),
                            maxLines = 1,
                        )
                    }
                }
            }''',
    "Event hero role layout",
)
replace_once(
    dashboard,
    '''                    subtitle = "$photosOfMe found of you",''',
    '''                    subtitle = if (photosOfMe == 1) "1 photo of you" else "$photosOfMe photos of you",''',
    "My Photos count copy",
)
replace_once(
    dashboard,
    '''private fun parityEventRange(event: SnapEvent): String {
    val zone = runCatching {
        ZoneId.of(event.photoWindowTimeZoneId ?: ZoneId.systemDefault().id)
    }.getOrDefault(ZoneId.systemDefault())
    val formatter = DateTimeFormatter.ofPattern("MMM d, yyyy", Locale.getDefault())
    val start = event.startsAt.atZone(zone).toLocalDate().format(formatter)
    val end = event.endsAt.atZone(zone).toLocalDate().format(formatter)
    return if (start == end) start else "$start – $end"
}''',
    '''private fun parityEventRange(event: SnapEvent): String {
    val zone = runCatching {
        ZoneId.of(event.photoWindowTimeZoneId ?: ZoneId.systemDefault().id)
    }.getOrDefault(ZoneId.systemDefault())
    return EventDateRangeFormatter.format(
        event.startsAt.atZone(zone).toLocalDate(),
        event.endsAt.atZone(zone).toLocalDate(),
    )
}''',
    "Dashboard event range formatter",
)


gallery = Path("app/src/main/java/com/snaploop/app/ui/ParityPhotoGallery.kt")
replace_once(
    gallery,
    '''                TextButton(onClick = ::endSelection, enabled = !bulkBusy) {
                    Text("Cancel", fontWeight = FontWeight.Bold)
                }''',
    '''                TextButton(onClick = ::endSelection, enabled = !bulkBusy) {
                    Text(
                        "Cancel",
                        fontWeight = FontWeight.Bold,
                        fontSize = 13.sp,
                        maxLines = 1,
                        softWrap = false,
                    )
                }''',
    "Gallery Cancel wrapping",
)
replace_once(
    gallery,
    '''                    } else {
                        "photos found of you"
                    },''',
    '''                    } else {
                        if (count == 1) "photo of you found in this Event" else "photos of you found in this Event"
                    },''',
    "Event My Photos count copy",
)


test_path = Path("app/src/test/java/com/snaploop/app/ui/ReportedLayoutParityTest.kt")
test_path.write_text('''package com.snaploop.app.ui

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReportedLayoutParityTest {
    private fun source(name: String): String {
        val candidates = listOf(
            File("src/main/java/com/snaploop/app/ui/$name"),
            File("app/src/main/java/com/snaploop/app/ui/$name"),
        )
        return candidates.firstOrNull { it.isFile }?.readText()
            ?: error("$name was not found from the unit-test working directory")
    }

    @Test fun `event dashboard back is icon only`() {
        val source = source("ParityEventDashboard.kt")
        assertTrue(source.contains("Icon(Icons.Filled.ChevronLeft, contentDescription = \\"Back\\")"))
        assertFalse(source.contains("Text(\\"Events\\")"))
    }

    @Test fun `event hero role is not squeezed beside the full date`() {
        val source = source("ParityEventDashboard.kt")
        assertTrue(source.contains("modifier = Modifier.padding(top = 7.dp)"))
        assertTrue(source.contains("EventDateRangeFormatter.format("))
    }

    @Test fun `gallery cancel stays one line and event count copy matches iOS semantics`() {
        val source = source("ParityPhotoGallery.kt")
        assertTrue(source.contains("softWrap = false"))
        assertTrue(source.contains("photos of you found in this Event"))
    }

    @Test fun `home action cards cap title and subtitle lines`() {
        val source = source("SnapLoopMainShell.kt")
        assertTrue(source.contains("fontSize = 16.sp"))
        assertTrue(source.contains("fontSize = 11.sp"))
        assertTrue(source.contains("EventDateRangeFormatter.format("))
    }
}
''')
