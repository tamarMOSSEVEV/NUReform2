package com.example.nureform.ui.home

import androidx.annotation.DrawableRes
import androidx.annotation.ColorRes

data class ActionCard(
    val id: String,
    val title: String,
    @DrawableRes val icon: Int,
    @ColorRes val tintColor: Int,
    val action: ActionCardType
)

enum class ActionCardType {
    ADD_NURSES,
    NURSES_DETAILS,
    NURSES_REQUESTS,
    DELETE_NURSE,
    WEEKLY_SHIFTS,
    CHOOSE_SHIFTS,
    MY_SCHEDULE,
    ALL_NURSES_SCHEDULE,
    RUN_SCHEDULING
}

