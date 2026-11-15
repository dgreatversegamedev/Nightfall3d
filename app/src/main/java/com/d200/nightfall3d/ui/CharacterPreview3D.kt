package com.d200.nightfall3d.ui

import android.util.Log
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import com.d200.nightfall3d.ui.theme.NfColors
import com.google.android.filament.IndirectLight
import io.github.sceneview.Scene
import io.github.sceneview.environment.Environment
import io.github.sceneview.math.Position
import io.github.sceneview.math.Rotation
import io.github.sceneview.node.ModelNode
import io.github.sceneview.rememberCameraNode
import io.github.sceneview.rememberEngine
import io.github.sceneview.rememberMaterialLoader
import io.github.sceneview.rememberModelLoader
import io.github.sceneview.rememberNodes

// =====================================================================
// CharacterPreview3D.kt
// Shows a real character GLB standing in idle, for the Character screen and the Main Menu.
// Drag left / right on it to turn him around.
//
// The character is loaded and placed the SAME way as in the arena (feet on the floor at 0).
// There is no background colour any more: the menu behind it shows through.
//
// NOTE: CharacterSilhouette below has the SAME name as the one in Theme.kt. Files in this package
// (MainMenuScreen.kt) now pick this one, so the main menu shows the real 3D character you picked,
// without editing MainMenuScreen.kt. The old drawn silhouette is still in Theme.kt, just not used here.
// =====================================================================

private const val PREVIEW_HEIGHT = 1.8f      // character size in meters (same as the game)
private const val PREVIEW_CAM_DIST = 2.8f    // smaller = he looks bigger
private const val PREVIEW_CAM_Y = 0.95f      // camera height
private const val PREVIEW_LOOK_Y = 0.9f      // height of the point the camera looks at
private const val PREVIEW_START_YAW = -90f   // he starts facing you. If you see his back, change to 90f. For side view use 0f.

/** The character the player picked. The Character screen sets it, the main menu shows it. */
object SelectedCharacter {
    var glb by mutableStateOf("char_blue.glb")
}

private class PreviewState {
    var model: ModelNode? = null
    var yaw = PREVIEW_START_YAW
    var t = 0f
    var last = 0L
    var idle = -1
}

@Composable
fun CharacterPreview3D(glb: String, modifier: Modifier = Modifier) {
    val engine = rememberEngine()
    val modelLoader = rememberModelLoader(engine)
    val materialLoader = rememberMaterialLoader(engine)
    val cameraNode = rememberCameraNode(engine)
    val state = remember { PreviewState() }

    // Light only. No skybox, so there is no background colour.
    val environment = remember(engine) {
        Environment(
            indirectLight = IndirectLight.Builder()
                .irradiance(1, floatArrayOf(1f, 1f, 1f))
                .intensity(30_000f)
                .build(engine)
        )
    }
    val nodes = rememberNodes { }

    LaunchedEffect(glb) {
        try {
            withFrameNanos { }
            val m = ModelNode(
                modelInstance = modelLoader.createModelInstance(glb),
                autoAnimate = false,
                scaleToUnits = PREVIEW_HEIGHT,
                centerOrigin = Position(0f, -1f, 0f)   // feet on the ground
            )
            state.model = m
            nodes.add(m)
        } catch (e: Throwable) {
            Log.e("CharPreview", "Could not load $glb", e)
        }
    }

    Box(modifier) {
        Scene(
            modifier = Modifier.fillMaxSize(),
            isOpaque = false,                           // transparent background
            engine = engine,
            modelLoader = modelLoader,
            materialLoader = materialLoader,
            cameraNode = cameraNode,
            childNodes = nodes,
            environment = environment,
            onFrame = { nanos ->
                val dt = if (state.last == 0L) 0f else ((nanos - state.last) / 1_000_000_000f).coerceIn(0f, 0.05f)
                state.last = nanos
                cameraNode.position = Position(0f, PREVIEW_CAM_Y, PREVIEW_CAM_DIST)
                cameraNode.lookAt(Position(0f, PREVIEW_LOOK_Y, 0f))
                val m = state.model
                if (m != null) {
                    // same as the arena: position = where his feet stand
                    m.position = Position(0f, 0f, 0f)
                    m.rotation = Rotation(y = state.yaw)
                    val a = m.modelInstance.animator
                    if (a.animationCount > 0) {
                        if (state.idle < 0) {
                            state.idle = (0 until a.animationCount).firstOrNull { a.getAnimationName(it) == "idle" } ?: 0
                        }
                        val d = a.getAnimationDuration(state.idle)
                        if (d > 0f) state.t = (state.t + dt) % d
                        a.applyAnimation(state.idle, state.t)
                        a.updateBoneMatrices()
                    }
                }
            }
        )
        // drag left / right = turn him around
        Box(
            Modifier
                .fillMaxSize()
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, dx ->
                        change.consume()
                        state.yaw += dx * 0.5f
                    }
                }
        )
    }
}

/** Same name and inputs as the drawn silhouette in Theme.kt, but shows the real picked character. */
@Composable
fun CharacterSilhouette(modifier: Modifier = Modifier, accent: Color = NfColors.Accent) {
    CharacterPreview3D(SelectedCharacter.glb, modifier)
}
