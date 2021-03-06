package tornadofx.tests

import javafx.stage.Stage
import org.junit.Test
import org.testfx.api.FxToolkit
import tornadofx.*
import kotlin.reflect.KProperty1
import kotlin.test.*

class ComponentTests {

    val primaryStage: Stage = FxToolkit.registerPrimaryStage()

    @Test
    fun testParam2() {

        val mainFragment = MainFragment()

        try {
            mainFragment.subFragmentNoParam.booleanParam
            fail("IllegalStateException should have been thrown")
        } catch (e: IllegalStateException) {
            // param not set
        }

        assertTrue(mainFragment.subFragmentNoParam.booleanParamWithDefault,
                "parameter value should match default value")

        assertFalse(mainFragment.subFragmentWithParam.booleanParam,
                "parameter value should match parameter passed in")

        assertNull(mainFragment.subFragmentWithParam.nullableBooleanParam,
                "nullable parameter value should match parameter passed in")

    }

    class MainFragment : Fragment() {

        var subFragmentNoParam: SubFragment by singleAssign()

        var subFragmentWithParam: SubFragment by singleAssign()

        override val root = vbox {
            add(SubFragment::class) {
                subFragmentNoParam = this
            }
            add(SubFragment::class, mapOf(
                    SubFragment::booleanParam to false,
                    SubFragment::nullableBooleanParam to null
            )) {
                subFragmentWithParam = this
            }
        }

    }

    class SubFragment : Fragment() {
        val booleanParam: Boolean by param()
        val booleanParamWithDefault: Boolean by param(defaultValue = true)
        val nullableBooleanParam: Boolean? by nullableParam()

        override val root = vbox()
    }

}