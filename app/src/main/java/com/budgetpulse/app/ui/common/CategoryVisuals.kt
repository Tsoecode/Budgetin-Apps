package com.budgetpulse.app.ui.common

import com.budgetpulse.app.R
import androidx.annotation.DrawableRes
import androidx.compose.ui.graphics.Color
import com.budgetpulse.app.domain.model.Category

fun Category.color(): Color = Color(colorArgb)

@DrawableRes
fun Category.icon(): Int = when (this) {
    Category.FOOD -> R.drawable.ic_restaurant
    Category.TRANSPORT -> R.drawable.ic_directions_car
    Category.SHOPPING -> R.drawable.ic_shopping_cart
    Category.BILLS -> R.drawable.ic_receipt
    Category.ENTERTAINMENT -> R.drawable.ic_movie
    Category.HEALTH -> R.drawable.ic_local_hospital
    Category.EDUCATION -> R.drawable.ic_school
    Category.TRAVEL -> R.drawable.ic_flight
    Category.OTHER_EXPENSE -> R.drawable.ic_more_horiz
    Category.SALARY -> R.drawable.ic_payments
    Category.BONUS -> R.drawable.ic_card_giftcard
    Category.OTHER_INCOME -> R.drawable.ic_trending_up
}
