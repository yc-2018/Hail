package com.aistra.hail.ui.home

import android.os.Bundle
import android.provider.Settings
import android.text.InputType
import android.view.*
import android.widget.EditText
import androidx.appcompat.widget.SearchView
import androidx.core.view.MenuHost
import androidx.core.view.MenuProvider
import androidx.core.view.isVisible
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import com.aistra.hail.HailApp.Companion.app
import com.aistra.hail.R
import com.aistra.hail.app.AppInfo
import com.aistra.hail.app.AppManager
import com.aistra.hail.app.HailApi
import com.aistra.hail.app.HailData
import com.aistra.hail.app.HailData.HomeGroup
import com.aistra.hail.databinding.DialogInputBinding
import com.aistra.hail.databinding.FragmentHomeBinding
import com.aistra.hail.extensions.*
import com.aistra.hail.ui.main.MainFragment
import com.aistra.hail.utils.*
import com.aistra.hail.work.HWork
import com.google.android.material.color.MaterialColors
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.android.material.snackbar.Snackbar
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray

class HomeFragment : MainFragment(), MenuProvider {
    var multiselect: Boolean = false
    val selectedList: MutableList<AppInfo> = mutableListOf()
    private var _binding: FragmentHomeBinding? = null
    private val binding get() = _binding!!
    private var query: String = String()
    private var moveMode: Boolean = false
    private lateinit var listAdapter: HomeListAdapter
    private var touchHelper: ItemTouchHelper? = null

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?
    ): View {
        (requireActivity() as MenuHost).addMenuProvider(this, viewLifecycleOwner, Lifecycle.State.RESUMED)
        _binding = FragmentHomeBinding.inflate(inflater, container, false)
        listAdapter = HomeListAdapter(
            onAppClick = ::onAppClick,
            onAppLongClick = ::onAppLongClick,
            onGroupClick = ::toggleGroup,
            onGroupLongClick = ::onGroupLongClick,
            onGroupColorClick = ::showColorDialog
        )
        val spanCount = resources.getInteger(
            if (HailData.compactIcon) R.integer.home_span_compact else R.integer.home_span
        )
        binding.recyclerView.apply {
            layoutManager = GridLayoutManager(activity, spanCount).apply {
                spanSizeLookup = object : GridLayoutManager.SpanSizeLookup() {
                    override fun getSpanSize(position: Int): Int =
                        if (listAdapter.getItemViewType(position) == HomeListAdapter.TYPE_GROUP) spanCount else 1
                }
            }
            adapter = listAdapter
            applyDefaultInsetter { paddingRelative(isRtl, bottom = isLandscape) }
        }
        binding.actionBar.applyDefaultInsetter { paddingRelative(isRtl, start = !isLandscape, end = true) }
        binding.refresh.apply {
            setOnRefreshListener {
                updateList()
                binding.refresh.isRefreshing = false
            }
            applyDefaultInsetter { marginRelative(isRtl, start = !isLandscape, end = true) }
        }
        binding.addGroup.setOnClickListener { showAddGroupDialog() }
        binding.move.isCheckable = true
        binding.move.setOnClickListener { setMoveMode(!moveMode) }
        touchHelper = ItemTouchHelper(DragCallback()).also { it.attachToRecyclerView(binding.recyclerView) }
        return binding.root
    }

    override fun onResume() {
        super.onResume()
        updateList()
        updateBarTitle()
        activity.appbar.setLiftOnScrollTargetView(binding.recyclerView)
        // The freeze button freezes every application on the Home screen.
        activity.fab.setOnClickListener { setListFrozen(true) }
        activity.fab.setOnLongClickListener {
            setListFrozen(false)
            true
        }
    }

    /* ----------------------------------------------------------------- the list */

    private fun matchesQuery(info: AppInfo): Boolean = query.isEmpty() || (
            (HailData.nineKeySearch && NineKeySearch.search(query, info.packageName, info.name.toString()))
                    || FuzzySearch.search(info.packageName, query)
                    || FuzzySearch.search(info.name.toString(), query)
                    || PinyinSearch.searchPinyinAll(info.name.toString(), query)
            )

    private fun buildItems(): List<HomeItem> {
        val searching = query.isNotEmpty()
        val items = ArrayList<HomeItem>()
        HailData.tags.forEach { group ->
            val apps = HailData.appsOfTag(group.id).filter { matchesQuery(it) }
            if (searching && apps.isEmpty()) return@forEach
            items += HomeItem.Group(group)
            if (searching || !group.collapsed) apps.forEach { items += HomeItem.App(it, group.color) }
        }
        return items
    }

    private fun updateList() {
        val items = buildItems()
        listAdapter.submitItems(items)
        binding.empty.isVisible =
            HailData.checkedList.isEmpty() || (query.isNotEmpty() && items.none { it is HomeItem.App })
        app.setAutoFreezeService()
    }

    private fun updateBarTitle() {
        activity.supportActionBar?.title =
            if (multiselect) getString(R.string.msg_selected, selectedList.size.toString())
            else getString(R.string.app_name)
    }

    /* ---------------------------------------------------------------- move mode */

    private fun setMoveMode(enabled: Boolean) {
        moveMode = enabled
        binding.move.isChecked = enabled
        listAdapter.moveMode = enabled
        HUI.showToast(if (enabled) R.string.msg_move_on else R.string.msg_move_off)
    }

    private inner class DragCallback : ItemTouchHelper.Callback() {
        override fun isLongPressDragEnabled(): Boolean = moveMode
        override fun isItemViewSwipeEnabled(): Boolean = false

        override fun getMovementFlags(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder): Int {
            val position = viewHolder.bindingAdapterPosition
            if (position < 0) return 0
            val type = listAdapter.getItemViewType(position)
            return if (type == HomeListAdapter.TYPE_GROUP)
                makeMovementFlags(ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0)
            else makeMovementFlags(
                ItemTouchHelper.UP or ItemTouchHelper.DOWN or ItemTouchHelper.LEFT or ItemTouchHelper.RIGHT, 0
            )
        }

        override fun onMove(
            recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder, target: RecyclerView.ViewHolder
        ): Boolean = moveItem(viewHolder.bindingAdapterPosition, target.bindingAdapterPosition)

        override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) {}

        override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
            super.onSelectedChanged(viewHolder, actionState)
            if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) viewHolder?.itemView?.alpha = 0.5f
        }

        override fun clearView(recyclerView: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
            super.clearView(recyclerView, viewHolder)
            viewHolder.itemView.alpha = 1f
            persistOrder()
        }
    }

    /**
     * A group header moves together with all of its apps (a whole block); an app item moves one slot
     * at a time so that it can be dragged across groups.
     */
    private fun moveItem(from: Int, to: Int): Boolean {
        val items = listAdapter.mutableItems
        if (from == to || from !in items.indices || to !in items.indices) return false
        val fromItem = items[from]
        if (fromItem is HomeItem.Group) {
            val start = items.indexOfFirst { it is HomeItem.Group && it.group.id == fromItem.group.id }
            if (start < 0) return false
            var end = start
            while (end + 1 < items.size && items[end + 1] is HomeItem.App) end++
            if (to in start..end) return false
            val block = ArrayList(items.subList(start, end + 1))
            repeat(block.size) { items.removeAt(start) }
            var insertAt = if (to > end) to - block.size + 1 else to
            insertAt = snapToGroupBoundary(items, insertAt)
            items.addAll(insertAt, block)
            listAdapter.notifyItemMoved(start, insertAt)
            listAdapter.notifyItemRangeChanged(
                minOf(start, insertAt), block.size + kotlin.math.abs(insertAt - start)
            )
            return true
        }
        if (items[to] is HomeItem.Group) return false
        items.add(to, items.removeAt(from))
        listAdapter.notifyItemMoved(from, to)
        return true
    }

    /** A group block may only be inserted on a group boundary. */
    private fun snapToGroupBoundary(items: List<HomeItem>, index: Int): Int {
        var i = index.coerceIn(0, items.size)
        while (i < items.size && items[i] !is HomeItem.Group) i++
        return i
    }

    /** Persists the group order and the app order (and group membership) produced by dragging. */
    private fun persistOrder() {
        val items = listAdapter.mutableItems
        val groupIds = ArrayList<Int>()
        val appOrder = ArrayList<AppInfo>()
        var currentId = HailData.tags.firstOrNull()?.id ?: 0
        items.forEach {
            when (it) {
                is HomeItem.Group -> {
                    currentId = it.group.id
                    if (it.group.id !in groupIds) groupIds.add(it.group.id)
                }

                is HomeItem.App -> {
                    it.info.tagId = currentId
                    appOrder.add(it.info)
                }
            }
        }
        // Hidden apps (collapsed or filtered out) keep their group and go to the end of the order.
        HailData.checkedList.forEach { if (it !in appOrder) appOrder.add(it) }
        HailData.checkedList.clear()
        HailData.checkedList.addAll(appOrder)
        val orderedGroups = ArrayList<HomeGroup>()
        groupIds.forEach { id -> HailData.tags.find { it.id == id }?.let { g -> orderedGroups.add(g) } }
        HailData.tags.forEach { if (it !in orderedGroups) orderedGroups.add(it) }
        HailData.tags.clear()
        HailData.tags.addAll(orderedGroups)
        HailData.saveApps()
        HailData.saveTags()
        updateList()
    }

    /* ------------------------------------------------------------------- groups */

    private fun toggleGroup(group: HomeGroup) {
        group.collapsed = !group.collapsed
        HailData.saveTags()
        updateList()
    }

    private fun showAddGroupDialog() {
        val dialogBinding = DialogInputBinding.inflate(layoutInflater)
        dialogBinding.inputLayout.setHint(R.string.group_name)
        MaterialAlertDialogBuilder(activity).setTitle(R.string.action_add_group).setView(dialogBinding.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = dialogBinding.editText.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                val id = if (name.hashCode() == 0) 1 else name.hashCode()
                if (HailData.tags.any { it.name == name || it.id == id }) return@setPositiveButton
                HailData.tags.add(HomeGroup(name, id))
                HailData.saveTags()
                updateList()
                binding.recyclerView.post {
                    binding.recyclerView.smoothScrollToPosition(listAdapter.itemCount - 1)
                }
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun onGroupLongClick(group: HomeGroup): Boolean {
        val actions = mutableListOf(R.string.action_group_rename)
        if (HailData.tags.size > 1 && group.id != 0) actions += R.string.action_group_remove
        MaterialAlertDialogBuilder(activity).setTitle(group.name)
            .setItems(actions.map { getString(it) }.toTypedArray()) { _, which ->
                when (actions[which]) {
                    R.string.action_group_rename -> renameGroup(group)
                    R.string.action_group_remove -> removeGroup(group)
                }
            }.setNegativeButton(android.R.string.cancel, null).show()
        return true
    }

    private fun renameGroup(group: HomeGroup) {
        val dialogBinding = DialogInputBinding.inflate(layoutInflater)
        dialogBinding.inputLayout.setHint(R.string.group_name)
        dialogBinding.editText.setText(group.name)
        MaterialAlertDialogBuilder(activity).setTitle(R.string.action_group_rename).setView(dialogBinding.root)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                val name = dialogBinding.editText.text.toString().trim()
                if (name.isEmpty()) return@setPositiveButton
                group.name = name
                HailData.saveTags()
                updateList()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun removeGroup(group: HomeGroup) {
        val fallback = HailData.tags.first { it.id != group.id }
        HailData.checkedList.forEach { if (it.tagId == group.id) it.tagId = fallback.id }
        HailData.tags.remove(group)
        HailData.saveApps()
        HailData.saveTags()
        updateList()
    }

    private fun showColorDialog(group: HomeGroup) {
        val colors = intArrayOf(
            HomeGroup.NO_COLOR, 0xFFEF9A9A.toInt(), 0xFFFFCC80.toInt(), 0xFFFFF59D.toInt(),
            0xFFA5D6A7.toInt(), 0xFF80DEEA.toInt(), 0xFF90CAF9.toInt(), 0xFFCE93D8.toInt()
        )
        val names = intArrayOf(
            R.string.color_default, R.string.color_red, R.string.color_orange, R.string.color_yellow,
            R.string.color_green, R.string.color_teal, R.string.color_blue, R.string.color_purple
        )
        MaterialAlertDialogBuilder(activity).setTitle(R.string.action_group_color)
            .setItems(names.map { getString(it) }.toTypedArray()) { _, which ->
                group.color = colors[which]
                HailData.saveTags()
                updateList()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    /* --------------------------------------------------------------------- apps */

    private fun onAppClick(info: AppInfo) {
        if (moveMode) return
        if (multiselect) {
            if (info in selectedList) selectedList.remove(info) else selectedList.add(info)
            updateList()
            updateBarTitle()
            return
        }
        if (info.applicationInfo == null) {
            Snackbar.make(activity.fab, R.string.app_not_installed, Snackbar.LENGTH_LONG)
                .setAction(R.string.action_remove_home) { removeCheckedApp(info.packageName) }.show()
            return
        }
        launchApp(info.packageName)
    }

    private fun onAppLongClick(info: AppInfo): Boolean {
        if (info.applicationInfo == null && (!multiselect || info !in selectedList)) {
            exportToClipboard(listOf(info))
            return true
        }
        if (info in selectedList) {
            onMultiSelect()
            return true
        }
        val pkg = info.packageName
        val frozen = AppManager.isAppFrozen(pkg)
        val actions = mutableListOf<Pair<Int, () -> Unit>>()
        actions += R.string.action_launch to { launchApp(pkg) }
        if (!frozen) actions += R.string.action_freeze to { setListFrozen(true, listOf(info)) }
        else actions += R.string.action_unfreeze to { setListFrozen(false, listOf(info)) }
        actions += R.string.action_deferred_task to { showDeferredTask(info, frozen) }
        if (!info.whitelisted) actions += R.string.action_whitelist to {
            info.whitelisted = true; HailData.saveApps(); updateList()
        } else actions += R.string.action_remove_whitelist to {
            info.whitelisted = false; HailData.saveApps(); updateList()
        }
        actions += R.string.action_tag_set to { groupDialog(info) }
        actions += R.string.action_add_pin_shortcut to { addPinShortcut(info) }
        actions += R.string.action_export_clipboard to { exportToClipboard(listOf(info)) }
        actions += R.string.action_remove_home to { removeCheckedApp(pkg) }
        if (frozen) actions += R.string.action_unfreeze_remove_home to {
            setListFrozen(false, listOf(info), false)
            if (!AppManager.isAppFrozen(pkg)) removeCheckedApp(pkg)
        }
        MaterialAlertDialogBuilder(activity).setTitle(info.name)
            .setItems(actions.map { getString(it.first) }.toTypedArray()) { _, which -> actions[which].second() }
            .setNeutralButton(R.string.action_details) { _, _ ->
                HUI.startActivity(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, HPackages.packageUri(pkg))
            }.setNegativeButton(android.R.string.cancel, null).show()
        return true
    }

    private fun showDeferredTask(info: AppInfo, frozen: Boolean) {
        val values = resources.getIntArray(R.array.deferred_task_values)
        val entries = arrayOfNulls<String>(values.size)
        values.forEachIndexed { i, v ->
            entries[i] = resources.getQuantityString(R.plurals.deferred_task_entry, v, v)
        }
        val action = getString(if (frozen) R.string.action_unfreeze else R.string.action_freeze)
        MaterialAlertDialogBuilder(activity).setTitle(R.string.action_deferred_task)
            .setItems(entries) { _, i ->
                HWork.setDeferredFrozen(info.packageName, !frozen, values[i].toLong())
                Snackbar.make(
                    activity.fab, resources.getQuantityString(
                        R.plurals.msg_deferred_task, values[i], values[i], action, info.name
                    ), Snackbar.LENGTH_INDEFINITE
                ).setAction(R.string.action_undo) { HWork.cancelWork(info.packageName) }.show()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    /** Single-choice dialog that moves an application to another group. */
    private fun groupDialog(info: AppInfo) {
        MaterialAlertDialogBuilder(activity).setTitle(R.string.action_tag_set)
            .setItems(HailData.tags.map { it.name }.toTypedArray()) { _, index ->
                info.tagId = HailData.tags[index].id
                HailData.saveApps()
                updateList()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun addPinShortcut(info: AppInfo) {
        val pkg = info.packageName
        HShortcuts.addPinShortcut(
            info, pkg, info.name, HailApi.getIntentForPackage(HailApi.ACTION_LAUNCH, pkg)
        )
    }

    /* -------------------------------------------------------------- multiselect */

    private fun deselect(update: Boolean = true) {
        selectedList.clear()
        if (!update) return
        updateList()
        updateBarTitle()
    }

    private fun onMultiSelect() {
        val actions = mutableListOf<Pair<Int, () -> Unit>>()
        actions += R.string.action_freeze to { setListFrozen(true, selectedList, false); deselect() }
        actions += R.string.action_unfreeze to { setListFrozen(false, selectedList, false); deselect() }
        actions += R.string.action_tag_set to { multiGroupDialog() }
        actions += R.string.action_export_clipboard to { exportToClipboard(selectedList); deselect() }
        actions += R.string.action_remove_home to {
            selectedList.forEach { removeCheckedApp(it.packageName, false) }
            HailData.saveApps()
            deselect()
        }
        actions += R.string.action_unfreeze_remove_home to {
            setListFrozen(false, selectedList, false)
            selectedList.forEach {
                if (!AppManager.isAppFrozen(it.packageName)) removeCheckedApp(it.packageName, false)
            }
            HailData.saveApps()
            deselect()
        }
        MaterialAlertDialogBuilder(activity).setTitle(
            getString(R.string.msg_selected, selectedList.size.toString())
        ).setItems(actions.map { getString(it.first) }.toTypedArray()) { _, which -> actions[which].second() }
            .setNegativeButton(R.string.action_deselect) { _, _ -> deselect() }
            .setNeutralButton(R.string.action_select_all) { _, _ ->
                selectedList.addAll(getVisibleApps().filterNot { it in selectedList })
                updateList()
                updateBarTitle()
                onMultiSelect()
            }.show()
    }

    private fun multiGroupDialog() {
        MaterialAlertDialogBuilder(activity).setTitle(R.string.action_tag_set)
            .setItems(HailData.tags.map { it.name }.toTypedArray()) { _, index ->
                val id = HailData.tags[index].id
                selectedList.forEach { it.tagId = id }
                HailData.saveApps()
                deselect()
            }.setNegativeButton(android.R.string.cancel, null).show()
    }

    private fun getVisibleApps(): List<AppInfo> =
        buildItems().filterIsInstance<HomeItem.App>().map { it.info }

    /* ------------------------------------------------------------------ helpers */

    private fun launchApp(packageName: String) {
        if (AppManager.isAppFrozen(packageName) && AppManager.setAppFrozen(packageName, false)) {
            updateList()
        }
        if (HailData.workingMode == HailData.MODE_ISLAND_HIDE) {
            HIsland.ensureLaunchIntentExists(packageName)
        }
        app.packageManager.getLaunchIntentForPackage(packageName)?.let {
            HShortcuts.addDynamicShortcut(packageName)
            startActivity(it)
        } ?: HUI.showToast(R.string.activity_not_found)
    }

    private fun setListFrozen(
        frozen: Boolean, list: List<AppInfo> = HailData.checkedList, updateCurrentList: Boolean = true
    ) {
        if (HailData.workingMode == HailData.MODE_DEFAULT) {
            MaterialAlertDialogBuilder(activity).setMessage(R.string.msg_guide)
                .setPositiveButton(android.R.string.ok, null).show()
            return
        } else if (HailData.workingMode == HailData.MODE_SHIZUKU_HIDE) {
            runCatching { HShizuku.isRoot }.onSuccess {
                if (!it) {
                    MaterialAlertDialogBuilder(activity).setMessage(R.string.shizuku_hide_adb)
                        .setPositiveButton(android.R.string.ok, null).show()
                    return
                }
            }
        }
        val filtered = list.filter { AppManager.isAppFrozen(it.packageName) != frozen }
        when (val result = AppManager.setListFrozen(frozen, *filtered.toTypedArray())) {
            null -> HUI.showToast(R.string.permission_denied)
            else -> {
                if (updateCurrentList) updateList()
                HUI.showToast(if (frozen) R.string.msg_freeze else R.string.msg_unfreeze, result)
            }
        }
    }

    private fun exportToClipboard(list: List<AppInfo>) {
        if (list.isEmpty()) return
        HUI.copyText(if (list.size > 1) JSONArray().run {
            list.forEach { put(it.packageName) }
            toString()
        } else list[0].packageName)
        HUI.showToast(R.string.msg_exported, if (list.size > 1) list.size.toString() else list[0].name)
    }

    private fun importFromClipboard() = runCatching {
        val str = HUI.pasteText() ?: throw IllegalArgumentException()
        val json = if (str.contains("[")) JSONArray(
            str.substring(str.indexOf("[")..str.indexOf("]", str.indexOf("[")))
        ) else JSONArray().put(str)
        var i = 0
        val defaultTagId = HailData.tags.firstOrNull()?.id ?: 0
        for (index in 0 until json.length()) {
            val pkg = json.getString(index)
            if (HPackages.getApplicationInfoOrNull(pkg) != null && !HailData.isChecked(pkg)) {
                HailData.addCheckedApp(pkg, defaultTagId, false)
                i++
            }
        }
        if (i > 0) {
            HailData.saveApps()
            updateList()
        }
        HUI.showToast(getString(R.string.msg_imported, i.toString()))
    }

    private suspend fun importFrozenApp() = withContext(Dispatchers.IO) {
        val defaultTagId = HailData.tags.firstOrNull()?.id ?: 0
        HPackages.getInstalledApplications().map { it.packageName }
            .filter { AppManager.isAppFrozen(it) && !HailData.isChecked(it) }
            .onEach { HailData.addCheckedApp(it, defaultTagId, false) }.size
    }

    private fun removeCheckedApp(packageName: String, saveApps: Boolean = true) {
        HailData.removeCheckedApp(packageName, saveApps)
        if (saveApps) updateList()
    }

    /* --------------------------------------------------------------------- menu */

    private fun MenuItem.updateIcon() = icon?.setTint(
        MaterialColors.getColor(
            activity.findViewById(R.id.toolbar),
            if (multiselect) androidx.appcompat.R.attr.colorPrimary
            else com.google.android.material.R.attr.colorOnSurface
        )
    )

    override fun onCreateMenu(menu: Menu, inflater: MenuInflater) {
        inflater.inflate(R.menu.menu_home, menu)
        val searchView = menu.findItem(R.id.action_search).actionView as SearchView
        if (HailData.nineKeySearch) {
            val editText = searchView.findViewById<EditText>(androidx.appcompat.R.id.search_src_text)
            editText.inputType = InputType.TYPE_CLASS_PHONE
        }
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            private var inited = false
            override fun onQueryTextChange(newText: String): Boolean {
                if (inited) {
                    query = newText
                    updateList()
                } else inited = true
                return true
            }

            override fun onQueryTextSubmit(query: String): Boolean = true
        })
        menu.findItem(R.id.action_multiselect).updateIcon()
    }

    override fun onMenuItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            R.id.action_multiselect -> {
                multiselect = !multiselect
                item.updateIcon()
                if (multiselect) {
                    updateBarTitle()
                    HUI.showToast(R.string.tap_to_select)
                } else deselect()
            }

            R.id.action_freeze_current -> setListFrozen(true, getVisibleApps().filterNot { it.whitelisted })
            R.id.action_unfreeze_current -> setListFrozen(false, getVisibleApps())
            R.id.action_freeze_all -> setListFrozen(true)
            R.id.action_unfreeze_all -> setListFrozen(false)
            R.id.action_freeze_non_whitelisted -> setListFrozen(true, HailData.checkedList.filterNot { it.whitelisted })
            R.id.action_import_clipboard -> importFromClipboard()
            R.id.action_import_frozen -> lifecycleScope.launch {
                val size = importFrozenApp()
                if (size > 0) {
                    HailData.saveApps()
                    updateList()
                }
                HUI.showToast(getString(R.string.msg_imported, size.toString()))
            }

            R.id.action_export_current -> exportToClipboard(getVisibleApps())
            R.id.action_export_all -> exportToClipboard(HailData.checkedList)
        }
        return false
    }

    override fun onDestroyView() {
        activity.fab.setOnClickListener(null)
        activity.fab.setOnLongClickListener(null)
        listAdapter.onDestroy()
        super.onDestroyView()
        _binding = null
    }
}
