package tornadofx

import javafx.beans.DefaultProperty
import javafx.beans.binding.Bindings.createObjectBinding
import javafx.collections.ListChangeListener
import javafx.collections.ObservableList
import javafx.css.PseudoClass
import javafx.event.EventTarget
import javafx.geometry.Orientation
import javafx.geometry.Orientation.HORIZONTAL
import javafx.geometry.Orientation.VERTICAL
import javafx.geometry.Pos
import javafx.scene.Node
import javafx.scene.control.Label
import javafx.scene.control.PasswordField
import javafx.scene.layout.HBox
import javafx.scene.layout.Pane
import javafx.scene.layout.Priority.SOMETIMES
import javafx.scene.layout.VBox
import java.util.concurrent.Callable

fun EventTarget.form(op: (Form.() -> Unit)? = null) = opcr(this, Form(), op)

fun EventTarget.fieldset(text: String? = null, icon: Node? = null, labelPosition: Orientation? = null, wrapWidth: Double? = null, op: (Fieldset.() -> Unit)? = null): Fieldset {
    val fieldset = Fieldset(text ?: "")
    if (wrapWidth != null) fieldset.wrapWidth = wrapWidth
    if (labelPosition != null) fieldset.labelPosition = labelPosition
    if (icon != null) fieldset.icon = icon
    return opcr(this, fieldset, op)
}

open class Form : VBox() {
    init {
        addClass(Stylesheet.form)
    }

    internal val labelContainerWidth: Double
        get() = fieldsets.flatMap { it.fields }.map { it.labelContainer }.map { f -> f.prefWidth(-1.0) }.max() ?: 0.0

    val fieldsets: List<Fieldset>
        get() = children.filterIsInstance<Fieldset>()

    override fun getUserAgentStylesheet() =
            Form::class.java.getResource("form.css").toExternalForm()!!
}

@DefaultProperty("children")
class Fieldset(text: String? = null, labelPosition: Orientation = HORIZONTAL) : VBox() {
    var text by property<String>()
    fun textProperty() = getProperty(Fieldset::text)

    var inputGrow by property(SOMETIMES)
    fun inputGrowProperty() = getProperty(Fieldset::inputGrow)

    var labelPosition by property<Orientation>()
    fun labelPositionProperty() = getProperty(Fieldset::labelPosition)

    var wrapWidth by property<Double>()
    fun wrapWidthProperty() = getProperty(Fieldset::wrapWidth)

    var icon by property<Node>()
    fun iconProperty() = getProperty(Fieldset::icon)

    var legend by property<Label?>()
    fun legendProperty() = getProperty(Fieldset::legend)

    fun buttonbar(alignment: Pos ? = null, op: (HBox.() -> Unit)? = null): Field {
        val field = Field("", true)
        children.add(field)
        if (alignment != null) field.inputContainer.alignment = alignment
        op?.invoke(field.inputContainer)
        return field
    }

    fun field(text: String? = null, forceLabelIndent: Boolean = false, op: (HBox.() -> Unit)? = null): Field {
        val field = Field(text ?: "", forceLabelIndent)
        children.add(field)
        op?.invoke(field.inputContainer)
        return field
    }

    init {
        addClass(Stylesheet.fieldset)

        // Apply pseudo classes when orientation changes
        syncOrientationState()

        // Add legend label when text is populated
        textProperty().addListener { observable, oldValue, newValue -> if (!newValue.isNullOrBlank()) addLegend() }

        // Add legend when icon is populated
        iconProperty().addListener { observable1, oldValue1, newValue -> if (newValue != null) addLegend() }

        // Make sure input children gets the configured HBox.hgrow property
        syncHgrow()

        // Initial values
        this@Fieldset.labelPosition = labelPosition
        if (text != null) this@Fieldset.text = text
    }

    private fun syncHgrow() {
        children.addListener(ListChangeListener { c ->
            while (c.next()) {
                if (c.wasAdded()) {
                    c.addedSubList.asSequence().filterIsInstance<Field>().forEach { added ->

                        // Configure hgrow for current children
                        added.inputContainer.children.forEach { this.configureHgrow(it) }

                        // Add listener to support inputs added later
                        added.inputContainer.children.addListener(ListChangeListener { while (it.next()) if (it.wasAdded()) it.addedSubList.forEach { this.configureHgrow(it) } })
                    }
                }
            }
        })

        // Change HGrow for unconfigured children when inputGrow changes
        inputGrowProperty().addListener { observable, oldValue, newValue ->
            children.asSequence().filterIsInstance<Field>().forEach {
                it.inputContainer.children.forEach { this.configureHgrow(it) }
            }
        }
    }

    private fun syncOrientationState() {
        labelPositionProperty().addListener { observable, oldValue, newValue ->
            if (newValue == HORIZONTAL) {
                pseudoClassStateChanged(VERTICAL_PSEUDOCLASS_STATE, false)
                pseudoClassStateChanged(HORIZONTAL_PSEUDOCLASS_STATE, true)
            } else {
                pseudoClassStateChanged(HORIZONTAL_PSEUDOCLASS_STATE, false)
                pseudoClassStateChanged(VERTICAL_PSEUDOCLASS_STATE, true)
            }
        }

        // Setup listeneres for wrapping
        wrapWidthProperty().addListener { observable, oldValue, newValue ->
            val responsiveOrientation = createObjectBinding<Orientation>(Callable {
                if (width < newValue) VERTICAL else HORIZONTAL
            }, widthProperty())

            if (labelPositionProperty().isBound)
                labelPositionProperty().unbind()

            labelPositionProperty().bind(responsiveOrientation)
        }
    }

    private fun addLegend() {
        if (legend == null) {
            legend = Label()
            legend!!.textProperty().bind(textProperty())
            legend!!.addClass(Stylesheet.legend)
            children.add(0, legend)
        }

        legend!!.graphic = icon
    }

    private fun configureHgrow(input: Node) {
        HBox.setHgrow(input, inputGrow)
    }

    val form: Form get() = parent as Form

    internal val fields: List<Field>
        get() = children.filterIsInstance<Field>()

    companion object {
        private val HORIZONTAL_PSEUDOCLASS_STATE = PseudoClass.getPseudoClass("horizontal")
        private val VERTICAL_PSEUDOCLASS_STATE = PseudoClass.getPseudoClass("vertical")
    }
}

/**
 * Make this Node (presumably an input element) the mnemonicTarget for the field label. When the label
 * of the field is activated, this input element will receive focus.
 */
fun Node.mnemonicTarget() {
    findParentOfType(Field::class)?.apply {
        label.isMnemonicParsing = true
        label.labelFor = this@mnemonicTarget
    }
}

@DefaultProperty("inputs")
class Field(text: String? = null, val forceLabelIndent: Boolean = false) : Pane() {
    var text by property(text)
    fun textProperty() = getProperty(Field::text)

    val label = Label()
    val labelContainer = HBox(label).apply { addClass(Stylesheet.labelContainer) }
    val inputContainer = HBox().apply { addClass(Stylesheet.inputContainer) }
    var inputs: ObservableList<Node>? = null

    init {
        isFocusTraversable = false
        addClass(Stylesheet.field)
        label.textProperty().bind(textProperty())
        inputs = inputContainer.children
        children.addAll(labelContainer, inputContainer)
    }

    val fieldset: Fieldset get() = parent as Fieldset

    override fun computePrefHeight(width: Double): Double {
        val labelHasContent = forceLabelIndent || text.isNotBlank()

        val labelHeight = if (labelHasContent) labelContainer.prefHeight(-1.0) else 0.0
        val inputHeight = inputContainer.prefHeight(-1.0)

        val insets = insets

        if (fieldset.labelPosition == HORIZONTAL)
            return Math.max(labelHeight, inputHeight) + insets.top + insets.bottom

        return labelHeight + inputHeight + insets.top + insets.bottom
    }

    override fun computePrefWidth(height: Double): Double {
        val fieldset = fieldset
        val labelHasContent = forceLabelIndent || text.isNotBlank()

        val labelWidth = if (labelHasContent) fieldset.form.labelContainerWidth else 0.0
        val inputWidth = inputContainer.prefWidth(height)

        val insets = insets

        if (fieldset.labelPosition == HORIZONTAL)
            return Math.max(labelWidth, inputWidth) + insets.left + insets.right

        return labelWidth + inputWidth + insets.left + insets.right
    }

    override fun computeMinHeight(width: Double) = computePrefHeight(width)

    override fun layoutChildren() {
        val fieldset = fieldset
        val labelHasContent = forceLabelIndent || text.isNotBlank()

        val insets = insets
        val contentX = insets.left
        val contentY = insets.top
        val contentWidth = width - insets.left - insets.right
        val contentHeight = height - insets.top - insets.bottom

        val labelWidth = Math.min(contentWidth, fieldset.form.labelContainerWidth)

        if (fieldset.labelPosition == HORIZONTAL) {
            if (labelHasContent) {
                labelContainer.resizeRelocate(contentX, contentY, labelWidth, contentHeight)

                val inputX = contentX + labelWidth
                val inputWidth = contentWidth - labelWidth

                inputContainer.resizeRelocate(inputX, contentY, inputWidth, contentHeight)
            } else {
                inputContainer.resizeRelocate(contentX, contentY, contentWidth, contentHeight)
            }
        } else {
            if (labelHasContent) {
                val labelPrefHeight = labelContainer.prefHeight(-1.0)
                val labelHeight = Math.min(labelPrefHeight, contentHeight)

                labelContainer.resizeRelocate(contentX, contentY, Math.min(labelWidth, contentWidth), labelHeight)

                val restHeight = contentHeight - labelHeight

                inputContainer.resizeRelocate(contentX, contentY + labelHeight, contentWidth, restHeight)
            } else {
                inputContainer.resizeRelocate(contentX, contentY, contentWidth, contentHeight)
            }
        }
    }

}
