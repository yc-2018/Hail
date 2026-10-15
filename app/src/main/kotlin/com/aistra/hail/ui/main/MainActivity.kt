package com.aistra.hail.ui.main

import android.content.res.ColorStateList
import android.graphics.Color
import android.os.Bundle
import android.os.SystemClock
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.graphics.drawable.StateListDrawable
import androidx.appcompat.app.AppCompatActivity
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricManager.Authenticators.BIOMETRIC_STRONG
import androidx.biometric.BiometricManager.Authenticators.DEVICE_CREDENTIAL
import androidx.biometric.BiometricPrompt
import androidx.core.content.ContextCompat
import androidx.core.graphics.ColorUtils
import androidx.core.view.MenuCompat
import androidx.core.view.WindowCompat
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
import com.aistra.hail.utils.HPolicy
import com.aistra.hail.views.NavPillDrawable
import com.aistra.hail.utils.HUI
import com.google.android.material.appbar.AppBarLayout
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.floatingactionbutton.FloatingActionButton
import com.google.android.material.navigation.NavigationBarItemView
import com.google.android.material.navigation.NavigationBarView
import com.google.android.material.shape.MaterialShapeDrawable
import com.google.android.material.shape.RelativeCornerSize
import com.google.android.material.shape.ShapeAppearanceModel

class MainActivity : AppCompatActivity(), NavController.OnDestinationChangedListener {
    lateinit var fab: FloatingActionButton
    lateinit var appbar: AppBarLayout

    /**
     * Freezes (`true`) or unfreezes (`false`) the Home list. Home registers it while it is on
     * screen, so the freeze button knows there is something to act on.
     */
    var freezeAction: ((Boolean) -> Unit)? = null

    private var navView: NavigationBarView? = null
    private var onHome = true
    private var homeShownAt = 0L

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
        // Portrait: the freeze button sits at the start of the bottom bar, standing in for the Home
        // tab (activity_main). Landscape: it floats over the content (app_bar_main) and the rail
        // keeps a Home item of its own.
        this@MainActivity.fab = root.findViewById(R.id.fab)
        appbar = appBarMain.appBarLayout

        val navHostFragment = supportFragmentManager.findFragmentById(R.id.nav_host_fragment) as NavHostFragment
        val navController = navHostFragment.navController
        val appBarConfiguration = AppBarConfiguration.Builder(
            R.id.nav_home, R.id.nav_apps, R.id.nav_settings, R.id.nav_about
        ).build()
        setupActionBarWithNavController(navController, appBarConfiguration)
        bottomNav?.setupWithNavController(navController)
        navRail?.setupWithNavController(navController)
        navView = bottomNav ?: navRail
        // The freeze button is the Home tab now, so the bar's own entry stays hidden. Keeping it in
        // the menu leaves navigation and the checked states to NavigationUI: selecting it from the
        // button goes Home the same way a tab would, and no other tab stays highlighted there.
        bottomNav?.menu?.findItem(R.id.nav_home)?.isVisible = false
        bottomNav?.let { shapeBar(it) }
        setupFreezeButton()

        val isRtl = isRtl
        val isLandscape = isLandscape
        appBarMain.appBarLayout.applyDefaultInsetter {
            paddingRelative(isRtl, start = !isLandscape, end = true, top = true)
        }
        // The floating bar keeps its distance from the screen edges with margins, not padding, so
        // the pill itself stays the height of its items.
        bottomNav?.applyDefaultInsetter { marginRelative(isRtl, end = true, bottom = true) }
        navRail?.applyDefaultInsetter { paddingRelative(isRtl, start = true, top = true, bottom = true) }
        // Portrait: the button is centered on the bar, which already rises with the gesture bar, so
        // it only needs the side inset. Landscape: it floats on its own.
        this@MainActivity.fab.applyDefaultInsetter {
            marginRelative(isRtl, start = !isLandscape, end = isLandscape, bottom = isLandscape)
        }
        navController.addOnDestinationChangedListener(this@MainActivity)
    }

    /** Rounds the bottom bar into a floating pill, filled with a faintly translucent surface. */
    private fun shapeBar(nav: NavigationBarView) {
        nav.background = MaterialShapeDrawable(
            ShapeAppearanceModel.builder().setAllCornerSizes(RelativeCornerSize(0.5f)).build()
        ).apply { fillColor = ColorStateList.valueOf(barColor(nav)) }
        // A shadow would show through the translucent fill, so the bar stays flat.
        nav.elevation = 0f
        highlightItems(nav)
    }

    /**
     * Wraps the selected tab's highlight around its icon *and* label.
     *
     * The bar's own active indicator is sized around the icon and always centers on it, so it has no
     * way to reach the label below; drawing the pill as the item's background keeps it centered on
     * the two together. The indicator itself stays enabled, just invisible, because the item's ripple
     * hangs off it.
     */
    private fun highlightItems(nav: NavigationBarView) {
        nav.itemActiveIndicatorColor = ColorStateList.valueOf(Color.TRANSPARENT)
        val color = MaterialColors.getColor(nav, com.google.android.material.R.attr.colorSecondaryContainer)
        val width = resources.getDimension(R.dimen.nav_highlight_width)
        val height = resources.getDimension(R.dimen.nav_highlight_height)
        collectItems(nav).forEach { item ->
            // An item draws its own background first, so the pill lands under the icon and label.
            item.background = StateListDrawable().apply {
                addState(intArrayOf(android.R.attr.state_checked), NavPillDrawable(color, width, height))
            }
        }
    }

    /** Every navigation item the bar holds, however it nests them. */
    private fun collectItems(group: ViewGroup): List<View> {
        val items = ArrayList<View>()
        for (i in 0 until group.childCount) {
            when (val child = group.getChildAt(i)) {
                is NavigationBarItemView -> items += child
                is ViewGroup -> items += collectItems(child)
            }
        }
        return items
    }

    /**
     * The freeze button doubles as the Home tab: on Home a tap freezes every listed application and
     * a long press thaws them, anywhere else it just goes back to Home.
     */
    private fun setupFreezeButton() {
        if (!isLandscape) {
            // Flat like the bar it belongs to: the FAB style keeps a shadow of its own even with
            // app:elevation at zero, so the state list animator behind it has to go.
            fab.stateListAnimator = null
            fab.elevation = 0f
        }
        fab.setOnClickListener {
            val freeze = freezeAction
            if (onHome && freeze != null && !justCameHome()) {
                it.spin()
                freeze(true)
            } else navView?.selectedItemId = R.id.nav_home
        }
        fab.setOnLongClickListener {
            val freeze = freezeAction
            // Unhandled off Home, which leaves the plain click to take us there on release.
            if (!onHome || freeze == null) return@setOnLongClickListener false
            it.spin()
            freeze(false)
            true
        }
    }

    /**
     * Whether Home has only just opened. Freezing everything is not something to do by accident, and
     * tapping the button twice in a row — once to come back Home, once more because the screen was
     * still catching up — should not be enough to set it off.
     */
    private fun justCameHome() = SystemClock.uptimeMillis() - homeShownAt < FREEZE_GRACE_MS

    /**
     * Portrait: off Home the button wears the bar's own surface, reading as the unselected Home tab;
     * on Home it fills with the primary color, as a freeze button should. Landscape: the rail has
     * its own Home item, so the button only shows up on Home.
     */
    private fun updateFreezeButton() {
        if (isLandscape) {
            if (onHome) fab.show() else fab.hide()
            return
        }
        // Off Home it shares the bar's translucent fill; on Home it turns into a solid button.
        val fill = if (onHome) MaterialColors.getColor(fab, androidx.appcompat.R.attr.colorPrimary)
        else barColor(fab)
        val onFill = MaterialColors.getColor(
            fab,
            if (onHome) com.google.android.material.R.attr.colorOnPrimary
            else com.google.android.material.R.attr.colorOnSurfaceVariant
        )
        fab.backgroundTintList = ColorStateList.valueOf(fill)
        fab.imageTintList = ColorStateList.valueOf(onFill)
        fab.contentDescription = getString(if (onHome) R.string.action_freeze_all else R.string.title_home)
    }

    /** Bottom bar fill: the themed nav surface, a touch translucent as the design asks. */
    private fun barColor(view: View) = ColorUtils.setAlphaComponent(
        MaterialColors.getColor(view, com.google.android.material.R.attr.colorSurfaceContainer), BAR_ALPHA
    )

    private companion object {
        /** Out of 255: see-through enough for content to show below, opaque enough to read. */
        const val BAR_ALPHA = 230

        /** How long after landing on Home the button still refuses to freeze, in milliseconds. */
        const val FREEZE_GRACE_MS = 500L
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
        onHome = destination.id == R.id.nav_home
        if (onHome) homeShownAt = SystemClock.uptimeMillis()
        updateFreezeButton()
    }
}