/*
 * SPDX-License-Identifier: LGPL-2.1-or-later
 * SPDX-FileCopyrightText: Copyright 2021-2026 Fcitx5 for Android Contributors
 */
package org.fcitx.fcitx5.android.input.picker

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.util.TypedValue
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import org.fcitx.fcitx5.android.R
import org.fcitx.fcitx5.android.core.FcitxKeyMapping
import org.fcitx.fcitx5.android.core.KeySym
import org.fcitx.fcitx5.android.data.RecentlyUsed
import org.fcitx.fcitx5.android.data.theme.Theme
import org.fcitx.fcitx5.android.data.theme.ThemeManager
import org.fcitx.fcitx5.android.input.AutoScaleTextView
import org.fcitx.fcitx5.android.input.font.FontProviders
import org.fcitx.fcitx5.android.input.keyboard.KeyAction
import org.fcitx.fcitx5.android.input.keyboard.KeyAction.SymAction
import org.fcitx.fcitx5.android.input.keyboard.KeyActionListener
import org.fcitx.fcitx5.android.input.keyboard.KeyDef
import org.fcitx.fcitx5.android.input.keyboard.TextKeyView
import org.fcitx.fcitx5.android.input.keyboard.TextKeyboard
import org.fcitx.fcitx5.android.input.popup.PopupAction
import org.fcitx.fcitx5.android.input.popup.PopupActionListener
import org.fcitx.fcitx5.android.utils.alpha
import org.fcitx.fcitx5.android.utils.pressHighlightDrawable
import org.fcitx.fcitx5.android.utils.rippleDrawable
import splitties.dimensions.dp
import splitties.views.dsl.core.Ui

/**
 * 符号 / 表情 / 颜文字面板，Gboard 式布局：
 *
 * ```
 * ┌──────────────────────────────────────────┐
 * │  符号网格（可纵向滚动），每行 N 个           │
 * ├────┬──────────────────────────────┬──────┤
 * │ ⌨  │ 最近 标点 数学 …（可横向拖动）   │  ⌫   │
 * └────┴──────────────────────────────┴──────┘
 * ```
 *
 * **面板独占整个键盘区域**：面板按键是「点一下即时上屏」，不需要空格/回车那一整行。
 * 键盘级操作只有底栏两端两枚键：
 * - **⌨** 返回文字键盘；
 * - **⌫** 退格删除，长按连续删除。
 *
 * **面板之间的切换不在这里**，交由布局按键与宏配置（见 `PickerWindow.Key.layerTargetNames`）。
 * 「返回键盘」在工具栏最左侧还有第二个入口（见 `IdleUi.setBackToKeyboardMode`）。
 *
 * 网格与分组栏都用 [RecyclerView] 复用视图，避免上千个子 View 常驻内存；网格项复用
 * [TextKeyView]，自动继承主题圆角、边框、按压高亮与字体设置，并保留长按弹出（emoji 肤色）。
 *
 * 列数由 [columns] 决定：符号/表情为 6 列，颜文字为 1 列（条目很长）。
 */
@SuppressLint("ViewConstructor")
class SymbolPanelUi(
    override val ctx: Context,
    private val theme: Theme,
    private val catalogType: SymbolCatalogType,
    private val columns: Int,
    private val textSize: Float,
    private val policy: PickerPolicy,
    private val keyActionListener: KeyActionListener,
    private val popupActionListener: PopupActionListener
) : Ui {

    private companion object {
        /** 底栏高度、两端功能键宽度、分组标签最小宽度。 */
        const val BOTTOM_BAR_HEIGHT_DP = 40
        const val UTILITY_KEY_WIDTH_DP = 56
        const val TAB_MIN_WIDTH_DP = 48
        const val GRID_ITEM_HEIGHT_DP = 40
        /** 每个面板保留最近十行记录，颜文字面板也按单列计算。 */
        const val RECENT_ROWS = 10

        /** 功能键文字大小。 */
        const val UTILITY_KEY_TEXT_SIZE = 20f

        const val LABEL_BACK_TO_KEYBOARD = "⌨"
        const val LABEL_BACKSPACE = "⌫"

        val DigitRange = '0'.code..'9'.code
        val FullWidthDigitRange = '０'.code..'９'.code
    }

    /**
     * 当前生效的 catalog。**不缓存在本地**：用户可能在设置里切换自备 catalog，
     * 故每次 [rebuild] 都重新取（[SymbolCatalogs] 内部按「种类 + 所选文件」缓存）。
     */
    private var catalog: SymbolCatalog = SymbolCatalog(emptyList(), multiLine = false)

    private val recentlyUsed = RecentlyUsed(catalogType.recentKey, columns * RECENT_ROWS)

    private var groups: List<SymbolGroup> = emptyList()
    private var selectedIndex = 0

    private val gridItemHeight = ctx.dp(GRID_ITEM_HEIGHT_DP)

    private val keyAppearance = KeyDef.Appearance.Text(
        displayText = "",
        textSize = textSize,
        variant = KeyDef.Appearance.Variant.Normal,
        border = KeyDef.Appearance.Border.Default
    )

    private val gridAdapter = object : RecyclerView.Adapter<GridHolder>() {

        var items: List<String> = emptyList()
            set(value) {
                field = value
                @Suppress("NotifyDataSetChanged")
                notifyDataSetChanged()
            }

        override fun getItemCount() = items.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): GridHolder {
            val keyView = TextKeyView(ctx, theme, keyAppearance)
            keyView.id = View.generateViewId()
            keyView.layoutParams = RecyclerView.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, gridItemHeight
            )
            return GridHolder(keyView)
        }

        override fun onBindViewHolder(holder: GridHolder, position: Int) {
            holder.bind(items[position])
        }
    }

    private inner class GridHolder(private val keyView: TextKeyView) :
        RecyclerView.ViewHolder(keyView) {

        fun bind(raw: String) {
            val text = policy.transform(raw)
            keyView.mainText.text = text
            keyView.mainText.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                FontProviders.getFontSize("key_main_font", textSize)
            )
            keyView.mainText.setFontTypeFace("key_main_font")
            // 面板靠滚动翻页，不用滑动切页手势
            keyView.swipeEnabled = false
            keyView.onGestureListener = null
            keyView.setOnClickListener { commit(raw) }
            keyView.setOnLongClickListener {
                val popup = policy.popup(raw)
                if (popup == null) {
                    false
                } else {
                    // 弹出键盘需要按键在屏幕上的实际位置
                    keyView.updateBounds()
                    popupActionListener.onPopupAction(
                        PopupAction.ShowKeyboardAction(keyView.id, popup, keyView.bounds)
                    )
                    true
                }
            }
        }
    }

    private val sidebarAdapter = object : RecyclerView.Adapter<SidebarHolder>() {

        override fun getItemCount() = groups.size

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): SidebarHolder {
            val label = AutoScaleTextView(ctx).apply {
                gravity = Gravity.CENTER
                maxLines = 1
                setTextSize(TypedValue.COMPLEX_UNIT_SP, 13f)
                minimumWidth = ctx.dp(TAB_MIN_WIDTH_DP)
                val h = ctx.dp(12)
                setPadding(h, 0, h, 0)
                layoutParams = RecyclerView.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.MATCH_PARENT
                )
                isClickable = true
                isFocusable = true
                contentDescription = null
            }
            return SidebarHolder(label)
        }

        override fun onBindViewHolder(holder: SidebarHolder, position: Int) {
            holder.bind(position)
        }
    }

    private inner class SidebarHolder(private val label: AutoScaleTextView) :
        RecyclerView.ViewHolder(label) {

        fun bind(position: Int) {
            val active = position == selectedIndex
            label.text = groups[position].name
            label.setTextColor(
                if (active) theme.accentKeyTextColor else theme.keyTextColor.alpha(0.65f)
            )
            label.background = if (active) {
                ColorDrawable(theme.genericActiveBackgroundColor)
            } else if (ThemeManager.prefs.keyRippleEffect.getValue()) {
                rippleDrawable(theme.keyPressHighlightColor)
            } else {
                pressHighlightDrawable(theme.keyPressHighlightColor)
            }
            label.setOnClickListener { selectGroup(position) }
        }
    }

    private val sidebarRecycler = RecyclerView(ctx).apply {
        id = View.generateViewId()
        // 分组横向排列，较多时可左右拖动
        layoutManager = LinearLayoutManager(ctx, LinearLayoutManager.HORIZONTAL, false)
        adapter = sidebarAdapter
        itemAnimator = null
        overScrollMode = View.OVER_SCROLL_NEVER
    }

    private val gridRecycler = RecyclerView(ctx).apply {
        id = View.generateViewId()
        layoutManager = GridLayoutManager(ctx, columns)
        adapter = gridAdapter
        itemAnimator = null
        overScrollMode = View.OVER_SCROLL_NEVER
    }

    /**
     * 构造底栏两端的一个功能键。
     *
     * 复用 [TextKeyView]，因此自动继承主题的按键背景、圆角、边框、按压高亮与字体设置。
     */
    private fun createUtilityKey(
        label: String,
        descriptionRes: Int,
        action: KeyAction,
        repeat: Boolean = false
    ): TextKeyView {
        val appearance = KeyDef.Appearance.Text(
            displayText = label,
            textSize = UTILITY_KEY_TEXT_SIZE,
            variant = KeyDef.Appearance.Variant.Alternative,
            border = KeyDef.Appearance.Border.Default
        )
        return TextKeyView(ctx, theme, appearance).apply {
            id = View.generateViewId()
            mainText.text = label
            mainText.setTextSize(
                TypedValue.COMPLEX_UNIT_SP,
                FontProviders.getFontSize("key_main_font", UTILITY_KEY_TEXT_SIZE)
            )
            mainText.setFontTypeFace("key_main_font")
            contentDescription = ctx.getString(descriptionRes)
            // 功能键不参与滑动切页/长按弹窗等键盘手势
            swipeEnabled = false
            onGestureListener = null
            val fire = { keyActionListener.onKeyAction(action, KeyActionListener.Source.Keyboard) }
            setOnClickListener { fire() }
            if (repeat) {
                // 退格长按连续删除，与键盘上的退格键一致
                repeatEnabled = true
                onRepeatListener = { fire() }
            }
        }
    }

    /** 底栏：⌨ · 分组栏（占满剩余宽度）· ⌫ */
    private val bottomBar = LinearLayout(ctx).apply {
        orientation = LinearLayout.HORIZONTAL
        val keyLp = { LinearLayout.LayoutParams(ctx.dp(UTILITY_KEY_WIDTH_DP), ViewGroup.LayoutParams.MATCH_PARENT) }
        addView(
            createUtilityKey(
                label = LABEL_BACK_TO_KEYBOARD,
                descriptionRes = R.string.back_to_keyboard,
                action = KeyAction.LayoutSwitchAction(TextKeyboard.Name)
            ),
            keyLp()
        )
        addView(
            sidebarRecycler,
            LinearLayout.LayoutParams(0, ViewGroup.LayoutParams.MATCH_PARENT, 1f)
        )
        addView(
            createUtilityKey(
                label = LABEL_BACKSPACE,
                descriptionRes = R.string.backspace,
                action = SymAction(KeySym(FcitxKeyMapping.FcitxKey_BackSpace)),
                repeat = true
            ),
            keyLp()
        )
    }

    /** 上：符号网格（占满剩余高度）；下：底栏。 */
    override val root: View = LinearLayout(ctx).apply {
        orientation = LinearLayout.VERTICAL
        setBackgroundColor(theme.keyboardColor)
        addView(
            gridRecycler,
            LinearLayout.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, 0, 1f)
        )
        addView(
            bottomBar,
            LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ctx.dp(BOTTOM_BAR_HEIGHT_DP)
            )
        )
    }

    init {
        rebuild()
    }

    /**
     * 重新构建分组列表（语言、过滤偏好、所选 catalog 或最近使用变化后调用）。
     */
    fun rebuild() {
        // 每次都重新取：用户可能刚在设置里换了自备 catalog
        catalog = SymbolCatalogs.get(catalogType)
        val list = ArrayList<SymbolGroup>(catalog.groups.size + 1)
        val recent = recentlyUsed.items.filter(policy::filter)
        if (recent.isNotEmpty()) {
            list.add(SymbolGroup(ctx.getString(R.string.symbol_panel_recent), recent))
        }
        catalog.groups.forEach { group ->
            val filtered = group.symbols.filter(policy::filter)
            if (filtered.isNotEmpty()) {
                list.add(SymbolGroup(group.name, filtered))
            }
        }
        groups = list
        if (selectedIndex >= groups.size) selectedIndex = 0
        @Suppress("NotifyDataSetChanged")
        sidebarAdapter.notifyDataSetChanged()
        applySelectedGroup()
    }

    /**
     * 面板将要显示时调用：重建分组并刷新网格。
     *
     * 以下四件事只能在这里对齐，故每次都重建（而不是"无变化就跳过"）：
     * - 「最近使用」可能在面板未显示时被写入；
     * - 过滤偏好（如「隐藏不支持的 emoji」）可能已改变；
     * - **所选的自备 catalog 可能已在设置里切换**；
     * - 字体配置可能已改变——[GridHolder.bind] 会按当前配置重设字号，
     *   而 `notifyDataSetChanged` 会让所有可见项重新绑定。
     */
    fun refresh() {
        rebuild()
    }

    private fun selectGroup(index: Int) {
        selectedIndex = index
        @Suppress("NotifyDataSetChanged")
        sidebarAdapter.notifyDataSetChanged()
        // 点到边缘半露的标签时，把它完整滑进视野
        sidebarRecycler.smoothScrollToPosition(index)
        applySelectedGroup()
    }

    private fun applySelectedGroup() {
        val group = groups.getOrNull(selectedIndex)
        gridAdapter.items = group?.symbols ?: emptyList()
        if (group != null) {
            gridRecycler.scrollToPosition(0)
        }
    }

    fun scrollToTop() {
        gridRecycler.scrollToPosition(0)
        sidebarRecycler.scrollToPosition(selectedIndex.coerceAtLeast(0))
    }

    private fun commit(raw: String) {
        val text = policy.transform(raw)
        keyActionListener.onKeyAction(KeyAction.CommitAction(text), KeyActionListener.Source.Keyboard)
        insertRecent(text)
    }

    private fun insertRecent(text: String) {
        // 单个数字（半角/全角）不进"最近使用"，否则面板一打开就被数字占满
        if (text.length == 1) {
            val code = text[0].code
            if (code in DigitRange || code in FullWidthDigitRange) return
        }
        recentlyUsed.insert(text)
    }
}
