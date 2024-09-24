/*
 * Copyright (c) Meta Platforms, Inc. and affiliates.
 *
 * This source code is licensed under the MIT license found in the
 * LICENSE file in the root directory of this source tree.
 */

package com.meta.spatial.samples.mruksample

import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import android.widget.ArrayAdapter
import android.widget.Button
import android.widget.Spinner
import com.meta.spatial.castinputforward.CastInputForwardFeature
import com.meta.spatial.core.Entity
import com.meta.spatial.core.Pose
import com.meta.spatial.core.Quaternion
import com.meta.spatial.core.SpatialFeature
import com.meta.spatial.core.Vector3
import com.meta.spatial.mruk.AnchorMeshSpawner
import com.meta.spatial.mruk.AnchorProceduralMesh
import com.meta.spatial.mruk.AnchorProceduralMeshConfig
import com.meta.spatial.mruk.MRUKAnchor
import com.meta.spatial.mruk.MRUKFeature
import com.meta.spatial.mruk.MRUKLabel
import com.meta.spatial.mruk.MRUKLoadDeviceResult
import com.meta.spatial.mruk.MRUKSystem
import com.meta.spatial.okhttp3.OkHttpAssetFetcher
import com.meta.spatial.physics.PhysicsFeature
import com.meta.spatial.runtime.NetworkedAssetLoader
import com.meta.spatial.toolkit.AppSystemActivity
import com.meta.spatial.toolkit.Grabbable
import com.meta.spatial.toolkit.Material
import com.meta.spatial.toolkit.PanelRegistration
import com.meta.spatial.toolkit.PlayerBodyAttachmentSystem
import com.meta.spatial.toolkit.Transform
import com.meta.spatial.toolkit.Visible
import com.meta.spatial.toolkit.createPanelEntity
import com.meta.spatial.vr.LocomotionSystem
import com.meta.spatial.vr.VRFeature
import java.io.File

// default activity
class MrukSampleActivity : AppSystemActivity() {
  public lateinit var mrukSystem: MRUKSystem
  private lateinit var meshSpawner: AnchorMeshSpawner
  private lateinit var procMeshSpawner: AnchorProceduralMesh
  private var globalMeshSpawner: AnchorProceduralMesh? = null

  private var sceneDataLoaded = false
  public var uiPositionInitialized = false
  public var showUiPanel = false
  private var showColliders = false

  override fun registerFeatures(): List<SpatialFeature> {
    var features =
        mutableListOf<SpatialFeature>(
            VRFeature(this), PhysicsFeature(spatial), MRUKFeature(this, systemManager))
    if (BuildConfig.DEBUG) {
      features.add(CastInputForwardFeature(this))
    }
    return features
  }

  override fun onCreate(savedInstanceState: Bundle?) {
    super.onCreate(savedInstanceState)

    systemManager.findSystem<LocomotionSystem>().enableLocomotion(false)
    mrukSystem = systemManager.findSystem<MRUKSystem>()
    systemManager.registerSystem(UIPositionSystem(this))
    systemManager.registerSystem(MrukInputSystem(this))

    scene.enablePassthrough(true)

    meshSpawner =
        AnchorMeshSpawner(
            mrukSystem,
            mutableMapOf(
                MRUKLabel.TABLE to AnchorMeshSpawner.AnchorMeshGroup(listOf("Furniture/Table.glb")),
                MRUKLabel.COUCH to AnchorMeshSpawner.AnchorMeshGroup(listOf("Furniture/Couch.glb")),
                MRUKLabel.WINDOW_FRAME to
                    AnchorMeshSpawner.AnchorMeshGroup(listOf("Furniture/Window.glb")),
                MRUKLabel.DOOR_FRAME to
                    AnchorMeshSpawner.AnchorMeshGroup(listOf("Furniture/Door.glb")),
                MRUKLabel.OTHER to
                    AnchorMeshSpawner.AnchorMeshGroup(listOf("Furniture/BoxCardBoard.glb")),
                MRUKLabel.STORAGE to
                    AnchorMeshSpawner.AnchorMeshGroup(listOf("Furniture/Storage.glb")),
                MRUKLabel.BED to AnchorMeshSpawner.AnchorMeshGroup(listOf("Furniture/TwinBed.glb")),
                MRUKLabel.SCREEN to
                    AnchorMeshSpawner.AnchorMeshGroup(listOf("Furniture/ComputerScreen.glb")),
                MRUKLabel.LAMP to AnchorMeshSpawner.AnchorMeshGroup(listOf("Furniture/Lamp.glb")),
                MRUKLabel.PLANT to
                    AnchorMeshSpawner.AnchorMeshGroup(
                        listOf(
                            "Furniture/Plant1.glb",
                            "Furniture/Plant2.glb",
                            "Furniture/Plant3.glb",
                            "Furniture/Plant4.glb")),
                MRUKLabel.WALL_ART to
                    AnchorMeshSpawner.AnchorMeshGroup(listOf("Furniture/WallArt.glb")),
            ))

    val floorMaterial =
        Material().apply { baseTextureAndroidResourceId = R.drawable.carpet_texture }
    val wallMaterial = Material().apply { baseTextureAndroidResourceId = R.drawable.wall1 }
    procMeshSpawner =
        AnchorProceduralMesh(
            mrukSystem,
            mapOf(
                MRUKLabel.TABLE to AnchorProceduralMeshConfig(null, true),
                MRUKLabel.SCREEN to AnchorProceduralMeshConfig(null, true),
                MRUKLabel.LAMP to AnchorProceduralMeshConfig(null, true),
                MRUKLabel.OTHER to AnchorProceduralMeshConfig(null, true),
                MRUKLabel.COUCH to AnchorProceduralMeshConfig(null, true),
                MRUKLabel.PLANT to AnchorProceduralMeshConfig(null, true),
                MRUKLabel.STORAGE to AnchorProceduralMeshConfig(null, true),
                MRUKLabel.BED to AnchorProceduralMeshConfig(null, true),
                MRUKLabel.FLOOR to AnchorProceduralMeshConfig(floorMaterial, true),
                MRUKLabel.WALL_FACE to AnchorProceduralMeshConfig(wallMaterial, true),
                MRUKLabel.CEILING to AnchorProceduralMeshConfig(wallMaterial, true)))

    NetworkedAssetLoader.init(
        File(applicationContext.getCacheDir().canonicalPath), OkHttpAssetFetcher())

    // Attach listeners
    mrukSystem.addOnRoomAddedListener({ room ->
      Log.d("MRUK", "Activity: Room added: ${room.anchor}")
    })

    mrukSystem.addOnRoomUpdatedListener({ room ->
      Log.d("MRUK", "Activity: Room updated: ${room.anchor}")
    })

    mrukSystem.addOnRoomRemovedListener({ room ->
      Log.d("MRUK", "Activity: Room removed: ${room.anchor}")
    })

    mrukSystem.addOnAnchorAddedListener({ room, anchor ->
      val anchorUuid = anchor.getComponent<MRUKAnchor>().uuid
      Log.d("MRUK", "Activity: Anchor ${anchorUuid} added to room ${room.anchor}")
    })

    mrukSystem.addOnAnchorUpdatedListener({ room, anchor ->
      val anchorUuid = anchor.getComponent<MRUKAnchor>().uuid
      Log.d("MRUK", "Activity: Anchor ${anchorUuid} updated to room ${room.anchor}")
    })

    mrukSystem.addOnAnchorRemovedListener({ room, anchor ->
      val anchorUuid = anchor.getComponent<MRUKAnchor>().uuid
      Log.d("MRUK", "Activity: Anchor ${anchorUuid} removed from room ${room.anchor}")
    })

    if (checkSelfPermission(PERMISSION_USE_SCENE) != PackageManager.PERMISSION_GRANTED) {
      Log.i(TAG, "Scene permission has not been granted, requesting " + PERMISSION_USE_SCENE)
      requestPermissions(arrayOf(PERMISSION_USE_SCENE), REQUEST_CODE_PERMISSION_USE_SCENE)
    } else {
      loadScene(true)
    }
  }

  override fun onRequestPermissionsResult(
      requestCode: Int,
      permissions: Array<out String>,
      grantResults: IntArray
  ) {
    if (requestCode == REQUEST_CODE_PERMISSION_USE_SCENE &&
        permissions.size == 1 &&
        permissions[0] == PERMISSION_USE_SCENE) {
      val granted = grantResults[0] == PackageManager.PERMISSION_GRANTED
      if (granted) {
        Log.i(TAG, "Use scene permission has been granted")
      } else {
        Log.i(TAG, "Use scene permission was DENIED!")
      }
      loadScene(granted)
    }
  }

  override fun onSceneReady() {
    super.onSceneReady()

    scene.setLightingEnvironment(
        ambientColor = Vector3(0.2f),
        sunColor = Vector3(1.0f, 1.0f, 1.0f),
        sunDirection = -Vector3(1.0f, 3.0f, -2.0f),
        environmentIntensity = 0.3f)

    Entity.createPanelEntity(
        R.integer.ui_mruk_id,
        R.layout.ui_mruk,
        Transform.build {
          move(0.0f, 1.0f, -2.0f)
          rotateY(180.0f)
        },
        Visible(showUiPanel),
        Grabbable())
  }

  override fun onDestroy() {
    meshSpawner.destroy()
    procMeshSpawner.destroy()
    globalMeshSpawner?.destroy()
    super.onDestroy()
  }

  private fun loadScene(scenePermissionsGranted: Boolean) {
    if (!sceneDataLoaded) {
      sceneDataLoaded = true

      if (scenePermissionsGranted) {
        loadSceneFromDevice()
      } else {
        loadFallbackScene()
      }
    }
  }

  private fun loadFallbackScene() {
    Log.i(TAG, "Loading fallback scene from JSON")
    val file = applicationContext.assets.open("MeshBedroom3.json")
    val text = file.bufferedReader().use { it.readText() }
    mrukSystem.loadSceneFromJsonString(text)
  }

  private fun loadSceneFromDevice() {
    Log.i(TAG, "Loading scene from device")
    var future = mrukSystem.loadSceneFromDevice()

    future.whenComplete { result: MRUKLoadDeviceResult, _ ->
      if (result != MRUKLoadDeviceResult.SUCCESS) {
        Log.e(TAG, "Error loading scene from device: ${result}")
        loadFallbackScene()
      }
    }
  }

  public fun updateUiPanelPosition(): Boolean {
    var head = getHmd()
    if (head == null) {
      return false
    }
    var headPose = head.tryGetComponent<Transform>()?.transform
    if (headPose == null || headPose == Pose()) {
      return false
    }
    var forward = headPose.q * Vector3(0f, 0f, 1f)
    forward.y = 0f
    headPose.q = Quaternion.lookRotation(forward)
    val uiEntity = Entity(R.integer.ui_mruk_id)
    // Rotate it to face away from the hmd
    headPose.q *= Quaternion(-20f, 180f, 0f)
    // Bring it away from the hmd
    headPose.t -= headPose.q * Vector3(0f, 0f, 0.8f)
    uiEntity.setComponent(Transform(headPose))
    return true
  }

  private fun getHmd(): Entity? {
    return systemManager
        .tryFindSystem<PlayerBodyAttachmentSystem>()
        ?.tryGetLocalPlayerAvatarBody()
        ?.head
  }

  private fun toggleGlobalMesh() {
    if (globalMeshSpawner == null) {
      val wallMaterial = Material().apply { baseTextureAndroidResourceId = R.drawable.wall1 }
      globalMeshSpawner =
          AnchorProceduralMesh(
              mrukSystem,
              mapOf(MRUKLabel.GLOBAL_MESH to AnchorProceduralMeshConfig(wallMaterial, false)))
    } else {
      globalMeshSpawner?.destroy()
      globalMeshSpawner = null
    }
  }

  private fun toggleShowColliders() {
    showColliders = !showColliders
    spatial.enablePhysicsDebugLines(showColliders)
  }

  override fun registerPanels(): List<PanelRegistration> {
    return listOf(
        PanelRegistration(R.layout.ui_mruk) {
          config {
            height = 0.5f
            width = 0.5f
            fractionOfScreen = 1f
            layoutDpi = 500
          }
          panel {
            requireNotNull(rootView)

            val jsonFileSpinner =
                requireNotNull(rootView?.findViewById<Spinner>(R.id.json_file_spinner))
            ArrayAdapter.createFromResource(
                    rootView?.context!!,
                    R.array.json_rooms_array,
                    android.R.layout.simple_spinner_item)
                .also { adapter ->
                  adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item)
                  jsonFileSpinner.adapter = adapter
                }

            val loadSceneFromJSONButton = rootView?.findViewById<Button>(R.id.load_scene_from_json)
            loadSceneFromJSONButton?.setOnClickListener {
              // Get selection from spinner
              jsonFileSpinner.selectedItem?.let {
                val file = applicationContext.assets.open("${it}.json")
                val text = file.bufferedReader().use { it.readText() }
                mrukSystem.loadSceneFromJsonString(text)
              }
            }

            val clearSceneButton = rootView?.findViewById<Button>(R.id.clear_scene)
            clearSceneButton?.setOnClickListener { mrukSystem.clearRooms() }

            val loadSceneFromDeviceButton =
                rootView?.findViewById<Button>(R.id.load_scene_from_device)
            loadSceneFromDeviceButton?.setOnClickListener { mrukSystem.loadSceneFromDevice() }

            val showGlobalMeshButton = rootView?.findViewById<Button>(R.id.show_global_mesh)
            showGlobalMeshButton?.setOnClickListener { toggleGlobalMesh() }

            val showCollidersButton = rootView?.findViewById<Button>(R.id.show_colliders)
            showCollidersButton?.setOnClickListener { toggleShowColliders() }

            val launchSceneCaptureButton = rootView?.findViewById<Button>(R.id.launch_scene_capture)
            launchSceneCaptureButton?.setOnClickListener { mrukSystem.requestSceneCapture() }
          }
        })
  }

  companion object {
    const val TAG = "MrukSampleActivity"
    const val PERMISSION_USE_SCENE: String = "com.oculus.permission.USE_SCENE"
    const val REQUEST_CODE_PERMISSION_USE_SCENE: Int = 1
  }
}
