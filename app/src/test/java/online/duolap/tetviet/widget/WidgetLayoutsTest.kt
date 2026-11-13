package online.duolap.tetviet.widget

import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Layout của widget màn hình chính chỉ được dùng các view RemoteViews hỗ trợ.
 * View khác (vd. androidx.appcompat.widget.LinearLayoutCompat) khiến launcher báo "Không thể tải tiện ích".
 */
class WidgetLayoutsTest {

    private val allowedTags = setOf(
        "FrameLayout", "LinearLayout", "RelativeLayout", "GridLayout",
        "AnalogClock", "Button", "Chronometer", "ImageButton", "ImageView", "ProgressBar", "TextView", "TextClock",
        "ViewFlipper", "ListView", "GridView", "StackView", "AdapterViewFlipper",
        "CheckBox", "Switch", "RadioButton", "RadioGroup", "ViewStub", "merge", "include"
    )

    @Test
    fun widgetLayoutsUseOnlyRemoteViewsSupportedViews() {
        val layoutDir = File("src/main/res/layout")
        val widgetLayouts = layoutDir.listFiles { file -> file.name.startsWith("widget_") && file.extension == "xml" }
            .orEmpty()
        assertTrue("Không tìm thấy layout widget trong ${layoutDir.absolutePath}", widgetLayouts.isNotEmpty())

        val tagRegex = Regex("<([A-Za-z][\\w.]*)")
        val problems = widgetLayouts.flatMap { file ->
            tagRegex.findAll(file.readText())
                .map { it.groupValues[1] }
                .filter { it != "xml" && it !in allowedTags }
                .distinct()
                .map { "${file.name}: <$it>" }
                .toList()
        }
        assertTrue("View không được RemoteViews hỗ trợ:\n${problems.joinToString("\n")}", problems.isEmpty())
    }
}
