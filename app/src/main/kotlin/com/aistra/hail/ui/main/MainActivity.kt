package com.aistra.hail.ui.main

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.view.Menu
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.view.MenuCompat
import androidx.core.view.WindowCompat
import androidx.core.view.doOnLayout
import androidx.core.view.updateLayoutParams
import androidx.core.view.isVisible
import androidx.navigation.NavController
import androidx.navigation.NavDestination
import androidx.navigation.fragment.NavHostFragment
import androidx.navigation.ui.AppBarConfiguration
import androidx.navigation.ui.setupActionBarWithNavController
import androidx.navigation.ui.setupWithNavController
import com.aistra.hail.R
import com.aistra.hail.app.HailData
import com.aistra.hail.databinding.ActivityMainBinding
import com.aistra.hail.extensions.*
import com.aistra.hail.utils.HLog
import com.aistra.hail.views.CradleDrawable
import com.aistra.hail.utils.HPolicy
import com.aistra.hail.utils.HUI
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.bottomappbar.BottomAppBarTopEdgeTreatment
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.shape.ShapeAppearanceModel
import com.google.android.material.shape.MaterialShapeDrawable

class MainActivity : AppCompatActivity(), NavController.OnDestinationChangedListener {
    lateinit var fab: FloatingActionButton
    lateinit var appbar: AppBarLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val binding = initView()
        if (!HailData.biometricLogin || BiometricManager.from(this)
                .canAuthenticate(BIOMETRIC_STRONG or DEVICE_CREDENTIAL) != BiometricManager.BIOMETRIC_SUCCESS
        ) return
        binding.root.isVisible = false
        val biometricPrompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    super.onAuthenticationError(errorCode, errString)
                    HUI.showToast(errString)
                    finishAndRemoveTask()
                }

                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    super.onAuthenticationSucceeded(result)
                    binding.root.isVisible = true
                }
            })
        val promptInfo = BiometricPrompt.PromptInfo.Builder().setTitle(getString(R.string.action_biometric))
            .setSubtitle(getString(R.string.msg_biometric)).setNegativeButtonText(getString(android.R.string.cancel))
            .build()
        biometricPrompt.authenticate(promptInfo)
    }

    private fun initView() = ActivityMainBinding.inflate(layoutInflater).apply {
        setContentView(root)
        setSupportActionBar(appBarMain.toolbar)
        // Portrait: FAB lives in activity_main (centered on the bottom bar). Landscape: in app_bar_main.
        this@MainActivity.fab = root.findViewById(R.id.fab)
        appbar = appBarMain.appBarLayout

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        navController.addOnDestinationChangedListener(this@MainActivity)
        val appBarConfiguration = AppBarConfiguration.Builder(
            R.id.nav_home, R.id.nav_apps, R.id.nav_settings, R.id.nav_about
        ).build()
        setupActionBarWithNavController(navController, appBarConfiguration)
        bottomNav?.setupWithNavController(navController)
        navRail?.setupWithNavController(navController)

        val isRtl = isRtl
        val isLandscape = isLandscape
        appBarMain.appBarLayout.applyDefaultInsetter {
            paddingRelative(isRtl, start = !isLandscape, end = true, top = true)
        }
        bottomNav?.applyDefaultInsetter { paddingRelative(isRtl, start = true, end = true, bottom = true) }
        navRail?.applyDefaultInsetter { paddingRelative(isRtl, start = true, top = true, bottom = true) }
        // Rises with the gesture bar like the nav bar does; landscape also keeps an end margin.
        this@MainActivity.fab.applyDefaultInsetter { marginRelative(isRtl, end = isLandscape, bottom = true) }
        bottomNav?.let { nav -> navCradle?.let { applyCradle(nav, it) } }
    }

    /**
     * Paints [cradle] with the nav background color and carves a notch around the FAB.
     *
     * The overlay starts at the FAB's top edge and ends at the bar's bottom, so the circle center
     * is exactly one FAB radius below the overlay's top. The notch radius adds a small gap.
     */
    private fun applyCradle(nav: View, cradle: View) {
        val radius = resources.getDimension(R.dimen.fab_size) / 2
        val offset = resources.getDimension(R.dimen.fab_bottom_offset)
        val gap = resources.getDimension(R.dimen.fab_cradle_margin)
        nav.doOnLayout {
            runCatching {
                val barHeight = nav.height - nav.paddingBottom
                // The FAB's bottom edge relative to the bar's top edge; the crest rises to just
                // under the button so the button looks seated in the bulge.
                val fabBottomAboveTop = barHeight - offset
                val crest = (radius * 2 - fabBottomAboveTop + gap).coerceAtLeast(0f)
                // Grow the overlay upwards by the crest so the curve has room to be drawn.
                cradle.updateLayoutParams<ViewGroup.MarginLayoutParams> {
                    height = nav.height + crest.toInt()
                }
                cradle.background = CradleDrawable(
                    MaterialColors.getColor(nav, com.google.android.material.R.attr.colorSurfaceContainer),
                    crest,
                    radius * CRADLE_WIDTH_FACTOR,
                    crest
                )
                nav.setBackgroundColor(Color.TRANSPARENT)
            }.onFailure { HLog.e(it) }
        }
    }

    private companion object {
        /** Bulge half width as a multiple of the FAB radius: wide and gentle, like a dock. */
        const val CRADLE_WIDTH_FACTOR = 2.6f
    }

    override fun onCreateOptionsMenu(menu: Menu?): Boolean {
        menu?.let { MenuCompat.setGroupDividerEnabled(it, true) }
        return super.onCreateOptionsMenu(menu)
    }

    fun ownerRemoveDialog() {
        MaterialAlertDialogBuilder(this).setTitle(R.string.title_remove_owner).setMessage(R.string.msg_remove_owner)
            .setPositiveButton(R.string.action_continue) { _, _ ->
                HPolicy.setOrganizationName()
                HPolicy.removeDeviceOwner()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    /* override fun onStop() {
        super.onStop()
        if (HailData.biometricLogin) finishAndRemoveTask()
    } */

    override fun onDestinationChanged(
        controller: NavController, destination: NavDestination, arguments: Bundle?
    ) {
        fab.tag = destination.id == R.id.nav_home
        if (fab.tag == true) fab.show() else fab.hide()
    }
}