package com.aistra.hail.ui.home

import android.annotation.SuppressLint
import android.content.res.Configuration
import android.graphics.drawable.GradientDrawable
import android.util.TypedValue
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.aistra.hail.R
import com.aistra.hail.app.AppInfo
import com.aistra.hail.app.HailData
import com.aistra.hail.app.HailData.HomeGroup
import com.aistra.hail.utils.AppIconCache
import com.aistra.hail.utils.HPackages.myUserId
import com.google.android.material.color.MaterialColors
import kotlinx.coroutines.Job

/** A row of the Home screen: either a group header or a single application cell. */
sealed class HomeItem {
    class Group(val group: HomeGroup) : HomeItem()
    class App(val info: AppInfo, val color: Int, val selected: Boolean = false) : HomeItem()
}

/**
 * Renders the Home screen as a flat grid list: group headers span the whole row and applications
 * occupy a single column. The flat model keeps drag & drop (including cross-group moves) simple.
 */
class HomeListAdapter(
    private val onAppClick: (AppInfo) -> Unit,
    private val onAppLongClick: (AppInfo) -> Boolean,
    private val onGroupClick: (HomeGroup) -> Unit,
    private val onGroupLongClick: (HomeGroup) -> Boolean,
    private val onGroupColorClick: (HomeGroup) -> Unit,
    private val onStartDrag: (RecyclerView.ViewHolder) -> Unit
) : RecyclerView.Adapter<RecyclerView.ViewHolder>() {

    companion object {
        const val TYPE_GROUP = 0
        const val TYPE_APP = 1
    }

    /** Mutable so the Home fragment can reorder items while dragging. */
    val mutableItems = ArrayList<HomeItem>()
    private var loadIconJob: Job? = null

    var moveMode: Boolean = false
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    fun submitItems(items: List<HomeItem>) {
        mutableItems.clear()
        mutableItems.addAll(items)
        notifyDataSetChanged()
    }

    fun onDestroy() {
        if (loadIconJob?.isActive == true) loadIconJob?.cancel()
    }

    override fun getItemViewType(position: Int): Int =
        if (mutableItems[position] is HomeItem.Group) TYPE_GROUP else TYPE_APP

    override fun getItemCount(): Int = mutableItems.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder =
        if (viewType == TYPE_GROUP) GroupViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_group_header, parent, false)
        ) else AppViewHolder(
            LayoutInflater.from(parent.context).inflate(R.layout.item_home, parent, false)
        )

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        when (val item = mutableItems[position]) {
            is HomeItem.Group -> (holder as GroupViewHolder).bind(item.group)
            is HomeItem.App -> (holder as AppViewHolder).bind(item)
        }
    }

    private fun itemAt(position: Int): HomeItem? =
        if (position in mutableItems.indices) mutableItems[position] else null

    /** Group colors are drawn translucent so they stay soft in light mode and readable in dark mode. */
    private fun View.tinted(color: Int): Int {
        val night = (resources.configuration.uiMode and Configuration.UI_MODE_NIGHT_MASK) ==
                Configuration.UI_MODE_NIGHT_YES
        val alpha = if (night) 0x66 else 0x4D
        return (color and 0x00FFFFFF) or (alpha shl 24)
    }

    private fun View.roundedRect(color: Int): GradientDrawable = GradientDrawable().apply {
        cornerRadius = resources.getDimension(R.dimen.padding_small)
        setColor(color)
    }

    /** Starts a drag as soon as the handle is touched, no long press needed. */
    @SuppressLint("ClickableViewAccessibility")
    private fun View.startDragOnTouch(holder: RecyclerView.ViewHolder) = setOnTouchListener { _, event ->
        if (event.actionMasked == MotionEvent.ACTION_DOWN) onStartDrag(holder)
        false
    }

    inner class GroupViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val drag: ImageView = view.findViewById(R.id.group_drag)
        private val expand: ImageView = view.findViewById(R.id.group_expand)
        private val name: TextView = view.findViewById(R.id.group_name)
        private val color: ImageView = view.findViewById(R.id.group_color)
        private val more: ImageView = view.findViewById(R.id.group_more)

        private val boundGroup: HomeGroup?
            get() = (itemAt(bindingAdapterPosition) as? HomeItem.Group)?.group

        init {
            view.setOnClickListener { boundGroup?.let(onGroupClick) }
            view.setOnLongClickListener { boundGroup?.let(onGroupLongClick) ?: false }
            color.setOnClickListener { boundGroup?.let(onGroupColorClick) }
            more.setOnClickListener { boundGroup?.let(onGroupLongClick) }
            drag.startDragOnTouch(this)
        }

        fun bind(group: HomeGroup) {
            name.text = group.name
            expand.rotation = if (group.collapsed) 0f else 180f
            drag.visibility = if (moveMode) View.VISIBLE else View.GONE
            val custom = group.color != HomeGroup.NO_COLOR
            itemView.background = itemView.roundedRect(
                if (custom) itemView.tinted(group.color) else MaterialColors.getColor(
                    itemView, com.google.android.material.R.attr.colorSurfaceContainerHigh, 0x33888888
                )
            )
            // The palette icon keeps its normal tint; the chosen color shows as a swatch behind it.
            color.clearColorFilter()
            color.background = if (custom) GradientDrawable().apply {
                shape = GradientDrawable.OVAL
                setColor(itemView.tinted(group.color))
                setStroke(itemView.resources.getDimensionPixelSize(R.dimen.padding_extra_small) / 2, group.color)
            } else null
        }
    }

    inner class AppViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val background: View = view.findViewById(R.id.cell_background)
        private val icon: ImageView = view.findViewById(R.id.app_icon)
        private val name: TextView = view.findViewById(R.id.app_name)
        private val drag: ImageView = view.findViewById(R.id.app_drag)

        private val boundInfo: AppInfo?
            get() = (itemAt(bindingAdapterPosition) as? HomeItem.App)?.info

        init {
            view.setOnClickListener { boundInfo?.let(onAppClick) }
            view.setOnLongClickListener { boundInfo?.let(onAppLongClick) ?: false }
            drag.startDragOnTouch(this)
        }

        fun bind(item: HomeItem.App) {
            val info = item.info
            drag.visibility = if (moveMode) View.VISIBLE else View.GONE
            val primary = MaterialColors.getColor(itemView, androidx.appcompat.R.attr.colorPrimary)
            background.background = when {
                // Selected cells get a primary-colored border plus a light primary wash so the
                // selection is obvious on top of any group color.
                item.selected -> itemView.roundedRect(itemView.tinted(primary)).apply {
                    setStroke(itemView.resources.getDimensionPixelSize(R.dimen.selection_stroke), primary)
                }

                item.color != HomeGroup.NO_COLOR -> itemView.roundedRect(itemView.tinted(item.color))
                else -> null
            }

            icon.run {
                info.applicationInfo?.let {
                    loadIconJob = AppIconCache.loadIconBitmapAsync(
                        context, it, myUserId, this, HailData.grayscaleIcon && info.state == AppInfo.State.FROZEN
                    )
                } ?: run {
                    setImageDrawable(context.packageManager.defaultActivityIcon)
                    colorFilter = null
                }
            }
            name.run {
                text = buildString {
                    if (!HailData.grayscaleIcon && info.state == AppInfo.State.FROZEN) append("❄️")
                    if (info.whitelisted) append("🔒")
                    append(info.name)
                }
                isEnabled = !HailData.grayscaleIcon || info.state != AppInfo.State.FROZEN
                when {
                    item.selected -> setTextColor(primary)
                    info.state == AppInfo.State.NOT_FOUND -> setTextColor(
                        MaterialColors.getColor(this, androidx.appcompat.R.attr.colorError)
                    )

                    else -> setTextAppearance(
                        com.google.android.material.R.style.TextAppearance_Material3_BodyMedium
                    )
                }
                setTextSize(TypedValue.COMPLEX_UNIT_SP, HailData.homeFontSize)
            }
        }
    }
}
