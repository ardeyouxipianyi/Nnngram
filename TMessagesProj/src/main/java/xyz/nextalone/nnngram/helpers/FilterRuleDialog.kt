/*
 * Copyright (C) 2019-2026 qwq233 <qwq233@qwq2333.top>
 * https://github.com/qwq233/Nullgram
 *
 * This program is free software; you can redistribute it and/or
 * modify it under the terms of the GNU General Public License
 * as published by the Free Software Foundation; either version 2
 * of the License, or (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License along with this software.
 *  If not, see
 * <https://www.gnu.org/licenses/>
 */
package xyz.nextalone.nnngram.helpers

import android.content.Context
import android.util.TypedValue
import android.widget.CheckBox
import android.widget.LinearLayout
import android.widget.RadioButton
import android.widget.RadioGroup
import android.widget.TextView
import org.telegram.messenger.AndroidUtilities
import org.telegram.messenger.LocaleController
import org.telegram.messenger.R
import org.telegram.ui.ActionBar.AlertDialog
import org.telegram.ui.ActionBar.BaseFragment
import org.telegram.ui.ActionBar.Theme
import org.telegram.ui.Components.BulletinFactory
import org.telegram.ui.Components.EditTextBoldCursor
import org.telegram.ui.Components.LayoutHelper
import java.util.UUID
import java.util.regex.Pattern

/**
 * 消息过滤规则编辑器 (新增 / 编辑 / 从消息预填).
 *
 * 规则管理器与聊天页「过滤此类消息」菜单共用同一份表单和校验, 避免两处逻辑漂移:
 *  - [editing] 非空          编辑已有规则, 按 id 原地替换
 *  - [editing] 空 + [template] 非空  用模板预填后新建 (来自消息菜单)
 *  - 两者都空                空白新建
 *
 * 保存由本类统一落盘 ([MessageFilterRules.save], 内部会 invalidate + 广播),
 * 再回调 [Callback.onSaved] 让调用方刷新自己的列表 / 弹提示.
 */
object FilterRuleDialog {

    /** 保存成功回调. 用 fun interface 是为了让 Java 调用点能直接写 lambda. */
    fun interface Callback {
        fun onSaved(rule: MessageFilterRules.Rule)
    }

    @JvmStatic
    @JvmOverloads
    fun show(
        fragment: BaseFragment,
        editing: MessageFilterRules.Rule? = null,
        template: MessageFilterRules.Rule? = null,
        callback: Callback? = null,
    ) {
        val context: Context = fragment.parentActivity ?: return
        // BaseFragment 只暴露 getResourceProvider() (BaseActivity 那个 protected 字段是它自己的)
        val resourcesProvider = fragment.resourceProvider
        // 编辑时以已有规则为准, 新建时用模板预填
        val initial = editing ?: template

        val container = LinearLayout(context).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(AndroidUtilities.dp(24f), 0, AndroidUtilities.dp(24f), 0)
        }

        fun addLabel(text: String) {
            container.addView(TextView(context).apply {
                setText(text)
                setTextSize(TypedValue.COMPLEX_UNIT_DIP, 13f)
                setTextColor(Theme.getColor(Theme.key_dialogTextGray2, resourcesProvider))
                setPadding(0, AndroidUtilities.dp(12f), 0, AndroidUtilities.dp(4f))
            })
        }

        addLabel(LocaleController.getString("FilterRulesMatchType", R.string.FilterRulesMatchType))
        val matchGroup = RadioGroup(context)
        val matchLabels = mapOf(
            MessageFilterRules.MATCH_CONTAINS to LocaleController.getString("FilterRulesMatchContains", R.string.FilterRulesMatchContains),
            MessageFilterRules.MATCH_REGEX to LocaleController.getString("FilterRulesMatchRegex", R.string.FilterRulesMatchRegex),
            MessageFilterRules.MATCH_SENDER_ID to LocaleController.getString("FilterRulesMatchSenderId", R.string.FilterRulesMatchSenderId),
            MessageFilterRules.MATCH_VIA_BOT to LocaleController.getString("FilterRulesMatchViaBot", R.string.FilterRulesMatchViaBot),
        )
        matchLabels.forEach { (type, label) ->
            matchGroup.addView(RadioButton(context).apply {
                id = type + 1000
                text = label
                isChecked = (initial?.matchType ?: MessageFilterRules.MATCH_CONTAINS) == type
            })
        }
        container.addView(matchGroup)

        addLabel(LocaleController.getString("FilterRulesMatchValue", R.string.FilterRulesMatchValue))
        val valueEdit = EditTextBoldCursor(context).apply {
            background = null
            setTextSize(TypedValue.COMPLEX_UNIT_DIP, 16f)
            setTextColor(Theme.getColor(Theme.key_dialogTextBlack, resourcesProvider))
            setCursorColor(Theme.getColor(Theme.key_dialogTextBlack))
            setCursorSize(AndroidUtilities.dp(20f))
            setCursorWidth(1.5f)
            setLineColors(
                Theme.getColor(Theme.key_windowBackgroundWhiteInputField),
                Theme.getColor(Theme.key_windowBackgroundWhiteInputFieldActivated),
                Theme.getColor(Theme.key_text_RedRegular),
            )
            setText(initial?.matchValue ?: "")
            setHintText(LocaleController.getString("FilterRulesMatchValueHint", R.string.FilterRulesMatchValueHint))
        }
        container.addView(valueEdit, LayoutHelper.createLinear(LayoutHelper.MATCH_PARENT, 40))

        val caseInsensitiveBox = CheckBox(context).apply {
            text = LocaleController.getString("FilterRulesCaseInsensitive", R.string.FilterRulesCaseInsensitive)
            isChecked = initial?.caseInsensitive ?: false
        }
        container.addView(caseInsensitiveBox)

        addLabel(LocaleController.getString("FilterRulesAction", R.string.FilterRulesAction))
        val actionGroup = RadioGroup(context)
        val actionLabels = mapOf(
            MessageFilterRules.ACTION_HIDE to LocaleController.getString("FilterRulesActionHide", R.string.FilterRulesActionHide),
            MessageFilterRules.ACTION_COLLAPSE to LocaleController.getString("FilterRulesActionCollapse", R.string.FilterRulesActionCollapse),
            MessageFilterRules.ACTION_SPOILER to LocaleController.getString("FilterRulesActionSpoiler", R.string.FilterRulesActionSpoiler),
        )
        actionLabels.forEach { (act, label) ->
            actionGroup.addView(RadioButton(context).apply {
                id = act + 2000
                text = label
                isChecked = (initial?.action ?: MessageFilterRules.ACTION_HIDE) == act
            })
        }
        container.addView(actionGroup)

        AlertDialog.Builder(context, resourcesProvider).apply {
            setTitle(
                if (editing == null)
                    LocaleController.getString("FilterRulesAdd", R.string.FilterRulesAdd)
                else LocaleController.getString("FilterRulesEdit", R.string.FilterRulesEdit)
            )
            setView(container)
            setPositiveButton(LocaleController.getString("Save", R.string.Save)) { _, _ ->
                val value = valueEdit.text?.toString()?.trim().orEmpty()
                if (value.isEmpty()) return@setPositiveButton
                val matchType = matchGroup.checkedRadioButtonId.takeIf { it != -1 }?.let { it - 1000 }
                    ?: MessageFilterRules.MATCH_CONTAINS
                val action = actionGroup.checkedRadioButtonId.takeIf { it != -1 }?.let { it - 2000 }
                    ?: MessageFilterRules.ACTION_HIDE
                if (matchType == MessageFilterRules.MATCH_REGEX) {
                    runCatching { Pattern.compile(value) }.onFailure {
                        BulletinFactory.of(fragment)
                            .createErrorBulletin(LocaleController.getString("InvalidPattern", R.string.InvalidPattern))
                            .show()
                        return@setPositiveButton
                    }
                }
                if (matchType == MessageFilterRules.MATCH_SENDER_ID && value.toLongOrNull() == null) {
                    BulletinFactory.of(fragment)
                        .createErrorBulletin(LocaleController.getString("FilterRulesSenderIdInvalid", R.string.FilterRulesSenderIdInvalid))
                        .show()
                    return@setPositiveButton
                }
                // via bot: "*" / 空 (任意) / 数字 bot id
                if (matchType == MessageFilterRules.MATCH_VIA_BOT
                    && value != "*" && value.toLongOrNull() == null
                ) {
                    BulletinFactory.of(fragment)
                        .createErrorBulletin(LocaleController.getString("FilterRulesViaBotInvalid", R.string.FilterRulesViaBotInvalid))
                        .show()
                    return@setPositiveButton
                }
                val newRule = MessageFilterRules.Rule(
                    id = editing?.id ?: UUID.randomUUID().toString(),
                    matchType = matchType,
                    matchValue = value,
                    action = action,
                    enabled = editing?.enabled ?: true,
                    caseInsensitive = caseInsensitiveBox.isChecked,
                )
                // 按 id 定位而不是下标: 调用方传进来的可能是过期快照
                val rules = MessageFilterRules.loadRaw().toMutableList()
                val idx = if (editing == null) -1 else rules.indexOfFirst { it.id == editing.id }
                if (idx >= 0) rules[idx] = newRule else rules.add(newRule)
                MessageFilterRules.save(rules)
                callback?.onSaved(newRule)
            }
            setNegativeButton(LocaleController.getString("Cancel", R.string.Cancel), null)
        }.show()
    }
}
