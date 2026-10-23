package com.ngocdevv.nativecook.blocks.forms.dialslider.dial

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.CustomAccessibilityAction
import androidx.compose.ui.semantics.ProgressBarRangeInfo
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.customActions
import androidx.compose.ui.semantics.disabled
import androidx.compose.ui.semantics.progressBarRangeInfo
import androidx.compose.ui.semantics.setProgress
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.unit.dp
import com.ngocdevv.nativecook.blocks.forms.dialslider.core.DialRange
import com.ngocdevv.nativecook.blocks.forms.dialslider.core.PresetValueConfig
import com.ngocdevv.nativecook.blocks.forms.dialslider.core.PresetValueUpdateInput
import com.ngocdevv.nativecook.blocks.forms.dialslider.core.clampDialValue
import com.ngocdevv.nativecook.blocks.forms.dialslider.core.createPresetValueChange
import com.ngocdevv.nativecook.blocks.forms.dialslider.core.dedupePresetsById
import com.ngocdevv.nativecook.blocks.forms.dialslider.core.getPresetInitialValue
import com.ngocdevv.nativecook.blocks.forms.dialslider.core.getPresetRange
import com.ngocdevv.nativecook.blocks.forms.dialslider.core.jsRound
import com.ngocdevv.nativecook.blocks.forms.dialslider.core.reconcilePresetValues
import com.ngocdevv.nativecook.blocks.forms.dialslider.core.resolvePresetValues
import com.ngocdevv.nativecook.blocks.forms.dialslider.core.resolveSelectedPresetId
import kotlin.math.max
import kotlin.math.roundToInt
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/** Latest JS-style "refs" that gesture and effect callbacks read without recomposing. */
private class DialSliderRefs(
    var selectedId: String,
    var values: Map<String, Double>,
    var presets: List<DialPreset>,
    var valuesAreControlled: Boolean,
) {
    var revision: Int = 0
    var selectedConfigSignature: String = ""
}

private fun resolvePresetSelection(presets: List<DialPreset>, preferredId: String?): String =
    resolveSelectedPresetId(presets, preferredId, { it.id }, { it.disabled })

/**
 * Photo-style adjustment control. One preset renders a centered tool; multiple
 * presets render a strip with an independent value for each tool.
 *
 * @param presets Adjustment tools to render. One item renders a centered tool;
 * multiple items render the horizontally scrollable preset strip.
 * @param initialPresetId Initial selection for uncontrolled use. Ignored when
 * [selectedPresetId] is set.
 * @param selectedPresetId Controlled selected preset identifier.
 * @param values Controlled values map. Missing or invalid entries use preset defaults.
 * @param defaultValues Initial values for uncontrolled use. Later changes are
 * intentionally ignored.
 * @param onPresetChange Called after a user request or an uncontrolled fallback
 * changes selection.
 * @param onValueChange Called for each committed integer dial value.
 * @param onValuesChange Called with the complete proposed values map whenever a
 * value changes.
 * @param accessibilityLabel Overrides the active preset label on the adjustable ruler.
 */
@Composable
public fun DialSlider(
    presets: List<DialPreset>,
    modifier: Modifier = Modifier,
    initialPresetId: String? = null,
    selectedPresetId: String? = null,
    values: Map<String, Double>? = null,
    defaultValues: Map<String, Double>? = null,
    onPresetChange: ((presetId: String, value: Double) -> Unit)? = null,
    onValueChange: ((presetId: String, value: Double, values: Map<String, Double>) -> Unit)? = null,
    onValuesChange: ((values: Map<String, Double>) -> Unit)? = null,
    accentColor: Color = DialColors.Positive,
    adjustedColor: Color = DialColors.Adjusted,
    backgroundColor: Color = DialColors.Background,
    accessibilityLabel: String? = null,
    accessibilityHint: String =
        "Swipe horizontally or use increment and decrement actions to adjust.",
    testTag: String? = null,
) {
    val controlledValues = values
    val resolvedPresets = remember(presets) { dedupePresetsById(presets) { it.id } }
    val multiPreset = resolvedPresets.size > 1
    val selectionIsControlled = selectedPresetId != null
    val valuesAreControlled = controlledValues != null
    val firstSelectedId = resolvePresetSelection(
        resolvedPresets,
        if (selectionIsControlled) selectedPresetId else initialPresetId,
    )

    var uncontrolledSelectedId by remember { mutableStateOf(firstSelectedId) }
    var uncontrolledValues by remember {
        mutableStateOf(resolvePresetValues(resolvedPresets, defaultValues))
    }
    var displayValue by remember {
        mutableDoubleStateOf(
            resolvePresetValues(
                resolvedPresets,
                if (valuesAreControlled) controlledValues else defaultValues,
            )[firstSelectedId] ?: 0.0,
        )
    }
    var viewportWidthPx by remember { mutableIntStateOf(0) }
    var isInteracting by remember { mutableStateOf(false) }

    val presetValueConfigs = remember(resolvedPresets) {
        resolvedPresets.map { preset ->
            val (min, max) = getPresetRange(preset)
            PresetValueConfig(
                id = preset.id,
                min = min,
                max = max,
                initialValue = getPresetInitialValue(preset),
            )
        }
    }
    val reconciledUncontrolledValues = remember(presetValueConfigs, uncontrolledValues) {
        reconcilePresetValues(presetValueConfigs, uncontrolledValues).values
    }
    val resolvedControlledValues = remember(controlledValues, resolvedPresets) {
        resolvePresetValues(resolvedPresets, controlledValues)
    }
    val currentValues = if (valuesAreControlled) {
        resolvedControlledValues
    } else {
        reconciledUncontrolledValues
    }
    val selectedId = resolvePresetSelection(
        resolvedPresets,
        if (selectionIsControlled) selectedPresetId else uncontrolledSelectedId,
    )

    val refs = remember {
        DialSliderRefs(
            selectedId = selectedId,
            values = currentValues,
            presets = resolvedPresets,
            valuesAreControlled = valuesAreControlled,
        )
    }
    val latestOnValueChange by rememberUpdatedState(onValueChange)
    val latestOnValuesChange by rememberUpdatedState(onValuesChange)
    val latestOnPresetChange by rememberUpdatedState(onPresetChange)
    SideEffect {
        refs.selectedId = selectedId
        refs.values = currentValues
        refs.presets = resolvedPresets
        refs.valuesAreControlled = valuesAreControlled
    }

    var interactionRevision by remember { mutableIntStateOf(0) }
    val presetRowAlpha = remember { Animatable(1f) }
    val valueBadgeAlpha = remember { Animatable(0f) }
    var hideBadgeJob by remember { mutableStateOf<Job?>(null) }
    val scrollState = rememberScrollState()
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val selectedPreset = resolvedPresets.firstOrNull { it.id == selectedId }
        ?: resolvedPresets.firstOrNull()
    val selectedRange = selectedPreset?.let { getPresetRange(it) }
        ?: DialRange(0.0, 0.0)
    val selectedValue = selectedPreset?.let { preset ->
        currentValues[preset.id] ?: getPresetInitialValue(preset)
    } ?: 0.0

    fun advanceRevision(): Int {
        val next = refs.revision + 1
        refs.revision = next
        interactionRevision = next
        return next
    }

    fun scrollToPreset(index: Int, animated: Boolean = true) {
        if (!multiPreset) return
        val target = with(density) {
            (index * (DialConfig.ITEM_SIZE + DialConfig.ITEM_GAP)).dp.toPx()
        }.roundToInt()
        scope.launch {
            if (animated) scrollState.animateScrollTo(target) else scrollState.scrollTo(target)
        }
    }

    fun handleValueChange(nextValue: Double, callbackRevision: Int) {
        val presetId = refs.selectedId
        val preset = refs.presets.firstOrNull { it.id == presetId }
        if (preset == null || preset.disabled) return

        val (min, max) = getPresetRange(preset)
        val next = clampDialValue(jsRound(nextValue), min, max)
        val change = createPresetValueChange(
            PresetValueUpdateInput(
                callbackRevision = callbackRevision,
                currentRevision = refs.revision,
                presetId = presetId,
                nextValue = next,
                values = refs.values,
            ),
        ) ?: return

        displayValue = change.value
        if (!change.changed) return

        refs.values = change.values
        if (!refs.valuesAreControlled) {
            uncontrolledValues = change.values
        }
        latestOnValueChange?.invoke(change.presetId, change.value, change.values)
        latestOnValuesChange?.invoke(change.values)
    }

    fun handleInteractionStart() {
        hideBadgeJob?.cancel()
        hideBadgeJob = null
        isInteracting = true
        scope.launch {
            presetRowAlpha.animateTo(
                if (multiPreset) DialConfig.PRESET_DIM_OPACITY else 1f,
                tween(durationMillis = DialConfig.PRESET_DIM_OUT_MS),
            )
        }
        scope.launch {
            valueBadgeAlpha.animateTo(1f, tween(durationMillis = DialConfig.VALUE_BADGE_IN_MS))
        }
    }

    fun handleInteractionEnd() {
        isInteracting = false
        scope.launch {
            presetRowAlpha.animateTo(1f, tween(durationMillis = DialConfig.PRESET_DIM_IN_MS))
        }
        hideBadgeJob?.cancel()
        hideBadgeJob = scope.launch {
            delay(DialConfig.VALUE_BADGE_DELAY_MS)
            valueBadgeAlpha.animateTo(0f, tween(durationMillis = DialConfig.VALUE_BADGE_OUT_MS))
            hideBadgeJob = null
        }
    }

    // Center the selected preset once the viewport has a size.
    LaunchedEffect(viewportWidthPx, multiPreset) {
        if (!multiPreset || viewportWidthPx <= 0) return@LaunchedEffect
        val index = refs.presets.indexOfFirst { it.id == refs.selectedId }
        if (index < 0) return@LaunchedEffect
        withFrameNanos {}
        scrollToPreset(index, animated = false)
    }

    // Reconcile dynamic preset ranges and additions in uncontrolled mode.
    LaunchedEffect(resolvedPresets, presetValueConfigs, uncontrolledValues, valuesAreControlled) {
        if (valuesAreControlled) return@LaunchedEffect
        val (nextValues, changed) = reconcilePresetValues(presetValueConfigs, uncontrolledValues)
        if (!changed) return@LaunchedEffect

        val previousValues = uncontrolledValues
        refs.values = nextValues
        uncontrolledValues = nextValues
        latestOnValuesChange?.invoke(nextValues)

        val activePreset = resolvedPresets.firstOrNull { it.id == refs.selectedId && !it.disabled }
        if (activePreset != null && previousValues[activePreset.id] != nextValues[activePreset.id]) {
            latestOnValueChange?.invoke(
                activePreset.id,
                nextValues.getValue(activePreset.id),
                nextValues,
            )
        }
    }

    // Repair an uncontrolled selection when presets are removed or disabled.
    LaunchedEffect(selectedId, selectionIsControlled, uncontrolledSelectedId) {
        if (selectionIsControlled || selectedId == uncontrolledSelectedId) return@LaunchedEffect
        refs.selectedId = selectedId
        uncontrolledSelectedId = selectedId
        val next = refs.values[selectedId] ?: 0.0
        latestOnPresetChange?.invoke(selectedId, next)
    }

    // Synchronize preset/config changes and controlled value corrections.
    LaunchedEffect(
        selectedPreset?.id,
        selectedRange.min,
        selectedRange.max,
        selectedPreset?.disabled,
        selectedValue,
        valuesAreControlled,
        displayValue,
    ) {
        val preset = selectedPreset ?: return@LaunchedEffect
        val nextConfigSignature = listOf(
            preset.id,
            selectedRange.min,
            selectedRange.max,
            if (preset.disabled) 1 else 0,
        ).joinToString(":")
        val configChanged = nextConfigSignature != refs.selectedConfigSignature
        val controlledValueChanged = valuesAreControlled && selectedValue != displayValue
        if (!configChanged && !controlledValueChanged) return@LaunchedEffect

        if (configChanged) advanceRevision()
        refs.selectedConfigSignature = nextConfigSignature
        displayValue = selectedValue

        if (configChanged) {
            val index = resolvedPresets.indexOfFirst { it.id == preset.id }
            if (index >= 0) scrollToPreset(index, animated = false)
        }
    }

    fun selectPreset(preset: DialPreset, index: Int) {
        if (preset.disabled || preset.id == refs.selectedId) return

        val next = refs.values[preset.id] ?: getPresetInitialValue(preset)
        val (min, max) = getPresetRange(preset)
        advanceRevision()
        latestOnPresetChange?.invoke(preset.id, next)
        if (selectionIsControlled) return

        refs.selectedConfigSignature = listOf(preset.id, min, max, 0).joinToString(":")
        refs.selectedId = preset.id
        uncontrolledSelectedId = preset.id
        displayValue = next
        scrollToPreset(index)
    }

    fun adjustBy(delta: Double) {
        val preset = refs.presets.firstOrNull { it.id == refs.selectedId }
        if (preset == null || preset.disabled) return

        val (min, max) = getPresetRange(preset)
        val current = refs.values[preset.id] ?: getPresetInitialValue(preset)
        val next = clampDialValue(current + delta, min, max)
        if (next != current) handleValueChange(next, refs.revision)
    }

    if (selectedPreset == null) return

    val formattedValue = selectedPreset.formatValue?.invoke(displayValue)
        ?: formatDialValue(displayValue)
    val contentPadding = if (multiPreset) {
        with(density) {
            max(0f, (viewportWidthPx - DialConfig.ITEM_SIZE.dp.toPx()) / 2f).toDp()
        }
    } else {
        0.dp
    }
    val requestedStep = selectedPreset.step
    val accessibilityStep = if (requestedStep != null && requestedStep.isFinite() && requestedStep > 0) {
        requestedStep
    } else {
        1.0
    }
    val rulerLabel = accessibilityLabel ?: selectedPreset.label

    Column(
        modifier = modifier
            .then(if (testTag != null) Modifier.testTag(testTag) else Modifier)
            .fillMaxWidth()
            .background(backgroundColor)
            .padding(top = 8.dp, bottom = 6.dp),
    ) {
        DialPresetViewport(
            presets = resolvedPresets,
            values = currentValues,
            selectedPreset = selectedPreset,
            selectedRange = selectedRange,
            displayValue = displayValue,
            formattedValue = formattedValue,
            multiPreset = multiPreset,
            isInteracting = isInteracting,
            contentPadding = contentPadding,
            accentColor = accentColor,
            adjustedColor = adjustedColor,
            presetRowAlpha = { presetRowAlpha.value },
            valueBadgeAlpha = { valueBadgeAlpha.value },
            scrollState = scrollState,
            onViewportWidthChanged = { width -> viewportWidthPx = width },
            onSelectPreset = ::selectPreset,
        )

        DialRuler(
            value = displayValue,
            minValue = selectedRange.min,
            maxValue = selectedRange.max,
            enabled = !selectedPreset.disabled,
            interactionRevision = interactionRevision,
            fadeColor = backgroundColor,
            onValueChange = ::handleValueChange,
            onInteractionStart = ::handleInteractionStart,
            onInteractionEnd = ::handleInteractionEnd,
            modifier = Modifier
                .padding(top = 4.dp)
                .clearAndSetSemantics {
                    contentDescription = if (accessibilityHint.isBlank()) {
                        rulerLabel
                    } else {
                        "$rulerLabel. $accessibilityHint"
                    }
                    stateDescription = formattedValue
                    progressBarRangeInfo = ProgressBarRangeInfo(
                        current = selectedValue.toFloat(),
                        range = selectedRange.min.toFloat()..selectedRange.max.toFloat(),
                    )
                    if (selectedPreset.disabled) disabled()
                    customActions = listOf(
                        CustomAccessibilityAction("Increment") {
                            adjustBy(accessibilityStep)
                            true
                        },
                        CustomAccessibilityAction("Decrement") {
                            adjustBy(-accessibilityStep)
                            true
                        },
                    )
                    setProgress { target ->
                        if (selectedPreset.disabled) return@setProgress false
                        val current = refs.values[selectedPreset.id]
                            ?: getPresetInitialValue(selectedPreset)
                        adjustBy(jsRound(target.toDouble()) - current)
                        true
                    }
                },
        )
    }
}
